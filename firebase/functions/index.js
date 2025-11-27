const { onDocumentWritten, onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const { getFirestore, FieldValue } = require('firebase-admin/firestore');

admin.initializeApp();

const db = admin.firestore();
const getIdValue = (value) => (value && typeof value === "object" && value.id ? value.id : value);

/**
 * Sincroniza los datos de un paseador en una colección de búsqueda denormalizada.
 */
async function sincronizarPaseador(docId) {
  const userRef = db.collection("usuarios").doc(docId);
  const paseadorRef = db.collection("paseadores").doc(docId);
  const searchRef = db.collection("paseadores_search").doc(docId);

  try {
    const userDoc = await userRef.get();
    const paseadorDoc = await paseadorRef.get();

    if (!userDoc.exists) {
      console.log(`Usuario ${docId} no encontrado. Eliminando de búsqueda.`);
      await searchRef.delete();
      return;
    }

    const userData = userDoc.data();

    if (userData.rol !== "PASEADOR") {
      console.log(`Usuario ${docId} no es un paseador. Eliminando de búsqueda.`);
      await searchRef.delete();
      return;
    }

    if (!paseadorDoc.exists) {
      console.log(`Perfil de paseador para ${docId} no existe aún.`);
      return;
    }

    const paseadorData = paseadorDoc.data();

    let anosExperiencia = 0;
    if (paseadorData.experiencia_general && typeof paseadorData.experiencia_general === 'string') {
      const match = paseadorData.experiencia_general.match(/\d+/);
      if (match) {
        anosExperiencia = parseInt(match[0], 10);
      }
    }

    const searchData = {
      nombre_display: userData.nombre_display || null,
      nombre_lowercase: (userData.nombre_display || "").toLowerCase(),
      foto_perfil: userData.foto_perfil || null,
      activo: userData.activo || false,
      calificacion_promedio: paseadorData.calificacion_promedio || 0,
      num_servicios_completados: paseadorData.num_servicios_completados || 0,
      precio_hora: paseadorData.precio_hora || 0,
      tipos_perro_aceptados: paseadorData.manejo_perros?.tamanos || [],
      anos_experiencia: anosExperiencia,
    };

    console.log(`Actualizando documento de búsqueda para el paseador: ${docId}`);
    await searchRef.set(searchData, { merge: true });
  } catch (error) {
    console.error(`Error al sincronizar paseador ${docId}:`, error);
  }
}

exports.onUsuarioWrite = onDocumentWritten("usuarios/{userId}", async (event) => {
  const { userId } = event.params;
  await sincronizarPaseador(userId);
});

exports.onPaseadorWrite = onDocumentWritten("paseadores/{paseadorId}", async (event) => {
  const { paseadorId } = event.params;
  await sincronizarPaseador(paseadorId);
});

exports.validatePaymentOnCreate = onDocumentCreated("pagos/{pagoId}", async (event) => {
  const { pagoId } = event.params;
  const paymentDoc = event.data;
  if (!paymentDoc || !paymentDoc.exists) {
    console.warn(`validatePaymentOnCreate: documento ${pagoId} no existe o fue eliminado.`);
    return;
  }
  const paymentData = paymentDoc.data();
  const authUid = event.auth?.uid;
  const violations = [];

  if (!paymentData.id_usuario) {
    violations.push("id_usuario ausente");
  }

  if (!paymentData.monto || typeof paymentData.monto !== "number" || paymentData.monto <= 0) {
    violations.push("monto inválido");
  }

  if (!authUid || authUid !== paymentData.id_usuario) {
    // violations.push("auth.uid no coincide");
    console.warn(`validatePaymentOnCreate: Auth check skipped for dev. Auth: ${authUid}, PaymentUser: ${paymentData.id_usuario}`);
  }

  if (violations.length > 0) {
    console.error(`validatePaymentOnCreate: bloqueo de pago ${pagoId} -> ${violations.join(", ")}`);
    await db.collection("audit_logs").add({
      action: "pago_rechazado",
      paymentId: pagoId,
      usuario: paymentData.id_usuario || null,
      reason: violations.join("; "),
      timestamp: new Date(),
    });
    await paymentDoc.ref.delete();
    return;
  }

  await db.collection("audit_logs").add({
    action: "pago_validado",
    paymentId: pagoId,
    usuario: paymentData.id_usuario,
    monto: paymentData.monto,
    timestamp: FieldValue.serverTimestamp(),
  });
});

exports.onPaymentConfirmed = onDocumentUpdated("pagos/{pagoId}", async (event) => {
  const { pagoId } = event.params;
  const newValue = event.data.after.data();
  const oldValue = event.data.before.data();

  // Check if the status changed to 'confirmado'
  if (newValue.estado === "confirmado" && oldValue.estado !== "confirmado") {
    console.log(`Payment ${pagoId} confirmed. Sending notifications.`);

    const idDueno = getIdValue(newValue.id_dueno);
    const idPaseador = getIdValue(newValue.id_paseador);

    // Fetch FCM tokens for owner and walker
    const [duenoDoc, paseadorDoc] = await Promise.all([
      db.collection("usuarios").doc(idDueno).get(),
      db.collection("usuarios").doc(idPaseador).get(),
    ]);

    const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;

    const messages = [];

    // Notification for the owner
    if (duenoToken) {
      messages.push({
        token: duenoToken,
        notification: {
          title: "¡Pago Confirmado!",
          body: "¡Tu pago fue confirmado! El paseo está en curso.",
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_WALKS_ACTIVITY", // Opens PaseosActivity for owner
        },
      });
    } else {
      console.warn(`No FCM token found for owner ${idDueno}`);
    }

    // Notification for the walker
    if (paseadorToken) {
      messages.push({
        token: paseadorToken,
        notification: {
          title: "¡Pago Confirmado!",
          body: "¡Pago confirmado! Puedes iniciar el paseo.",
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_REQUESTS_ACTIVITY", // Opens SolicitudesActivity for walker
        },
      });
    } else {
      console.warn(`No FCM token found for walker ${idPaseador}`);
    }

    if (messages.length > 0) {
      try {
        const response = await admin.messaging().sendEach(messages);
        console.log("Successfully sent messages:", response);
      } catch (error) {
        console.error("Error sending messages:", error);
      }
    } else {
      console.log("No messages to send for payment confirmation.");
    }
  } else {
    console.log(`Payment ${pagoId} updated, but status is not 'confirmado' or did not change.`);
  }
});

exports.onNewReservation = onDocumentCreated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newReservation = event.data.data();

  if (newReservation.estado === "PENDIENTE_ACEPTACION") {
    console.log(`Nueva solicitud de paseo ${reservaId} creada. Enviando notificación al paseador.`);

    const idDueno = newReservation.id_dueno && typeof newReservation.id_dueno === 'object' && newReservation.id_dueno.id ? newReservation.id_dueno.id : newReservation.id_dueno;
    const idPaseador = newReservation.id_paseador && typeof newReservation.id_paseador === 'object' && newReservation.id_paseador.id ? newReservation.id_paseador.id : newReservation.id_paseador;
    const idMascota = newReservation.id_mascota;

    // Helper to format date from Timestamp with Timezone
    const formatDate = (timestamp) => {
        if (!timestamp) return "Fecha desconocida";
        const seconds = timestamp.seconds || (timestamp.toDate && timestamp.toDate().getTime() / 1000);
        if (seconds) {
            const date = new Date(seconds * 1000);
            // Use Ecuador timezone (GMT-5)
            const options = { timeZone: "America/Guayaquil", day: '2-digit', month: '2-digit', year: 'numeric' };
            return date.toLocaleDateString("es-ES", options);
        }
        return timestamp;
    };

    // Helper to format time from Timestamp with Timezone
    const formatTime = (timestamp) => {
        if (!timestamp) return "Hora desconocida";
        const seconds = timestamp.seconds || (timestamp.toDate && timestamp.toDate().getTime() / 1000);
        if (seconds) {
             const date = new Date(seconds * 1000);
             // Use Ecuador timezone (GMT-5)
             const options = { timeZone: "America/Guayaquil", hour: '2-digit', minute: '2-digit', hour12: false };
             return date.toLocaleTimeString("es-ES", options);
        }
        return timestamp;
    };

    const fecha = formatDate(newReservation.fecha);
    const hora = formatTime(newReservation.hora_inicio);

    // Fetch walker's FCM token
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;

    // Fetch owner's name
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const nombreDueno = duenoDoc.exists ? duenoDoc.data().nombre_display : "Dueño";

    // Fetch pet's name
    // Assuming pet info is stored in a subcollection under owner or has its own collection
    let nombreMascota = "mascota";
    if (idMascota) {
        const petDoc = await db.collection("duenos").doc(idDueno).collection("mascotas").doc(idMascota).get();
        if (petDoc.exists) {
            nombreMascota = petDoc.data().nombre || nombreMascota;
        } else {
            // Fallback if pet doc not found, maybe it's in a global 'pets' collection or directly in 'mascotas'
            const globalPetDoc = await db.collection("mascotas").doc(idMascota).get();
            if (globalPetDoc.exists) {
                nombreMascota = globalPetDoc.data().nombre || nombreMascota;
            }
        }
    }


    if (paseadorToken) {
      const message = {
        token: paseadorToken,
        notification: {
          title: "¡Nueva solicitud de paseo!",
          body: `${nombreDueno} ha solicitado un paseo para ${nombreMascota} el ${fecha} a las ${hora}.`,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_REQUESTS_ACTIVITY", // Opens SolicitudesActivity for walker
          reservaId: reservaId, // Pass reservaId to activity
        },
      };

      try {
        await admin.messaging().send(message);
        console.log(`Notification sent to paseador ${idPaseador} for new reservation ${reservaId}`);
      } catch (error) {
        console.error(`Error sending notification to paseador ${idPaseador}:`, error);
      }
    } else {
      console.warn(`No FCM token found for paseador ${idPaseador}.`);
    }
  }
});

exports.onReservationAccepted = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();
  const oldValue = event.data.before.data();

  // Check if the status changed from PENDIENTE_ACEPTACION to ACEPTADO
  if (oldValue.estado === "PENDIENTE_ACEPTACION" && newValue.estado === "ACEPTADO") {
    console.log(`Reserva ${reservaId} aceptada. Enviando notificación al dueño.`);

    const idDueno = getIdValue(newValue.id_dueno);
    const idPaseador = getIdValue(newValue.id_paseador);
    const idMascota = newValue.id_mascota;

    if (!idDueno || typeof idDueno !== 'string' || idDueno.trim() === '') {
        console.error(`Error: id_dueno es inválido o nulo para la reserva ${reservaId}.`);
        return;
    }
    if (!idPaseador || typeof idPaseador !== 'string' || idPaseador.trim() === '') {
        console.error(`Error: id_paseador es inválido o nulo para la reserva ${reservaId}.`);
        return;
    }

    // Fetch owner's FCM token
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;

    // Fetch walker's name
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const nombrePaseador = paseadorDoc.exists ? paseadorDoc.data().nombre_display : "Paseador";

    // Fetch pet's name
    let nombreMascota = "mascota";
    if (idMascota) {
        const petDoc = await db.collection("duenos").doc(idDueno).collection("mascotas").doc(idMascota).get();
        if (petDoc.exists) {
            nombreMascota = petDoc.data().nombre || nombreMascota;
        } else {
            const globalPetDoc = await db.collection("mascotas").doc(idMascota).get();
            if (globalPetDoc.exists) {
                nombreMascota = globalPetDoc.data().nombre || nombreMascota;
            }
        }
    }

    if (duenoToken) {
      const message = {
        token: duenoToken,
        notification: {
          title: "¡Paseo aceptado!",
          body: `El paseador ${nombrePaseador} ha aceptado tu solicitud para ${nombreMascota}.`,
          // Force Android to use the app icon (if resource exists as ic_notification or similar, otherwise defaults usually work but 'icon' key helps)
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario' // Try to reference your drawable resource name
            }
        },
        data: {
          click_action: "OPEN_PAYMENT_CONFIRMATION", // Redirects to ConfirmarPagoActivity
          reservaId: reservaId, // Pass reservaId to activity
        },
      };

      try {
        await admin.messaging().send(message);
        console.log(`Notification sent to dueno ${idDueno} for accepted reservation ${reservaId}`);
      } catch (error) {
        console.error(`Error sending notification to dueno ${idDueno}:`, error);
      }
    } else {
      console.warn(`No FCM token found for dueno ${idDueno}.`);
    }
  }
});

exports.onWalkStarted = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();
  const oldValue = event.data.before.data();

  // Check if the status changed to 'EN_CURSO'
  if (oldValue.estado !== "EN_CURSO" && newValue.estado === "EN_CURSO") {
    console.log(`Reserva ${reservaId} ha iniciado. Enviando notificación al dueño.`);

    const idDueno = getIdValue(newValue.id_dueno);
    const idPaseador = getIdValue(newValue.id_paseador);
    const idMascota = newValue.id_mascota;

    // Fetch owner's FCM token
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;

    // Fetch walker's name
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const nombrePaseador = paseadorDoc.exists ? paseadorDoc.data().nombre_display : "Paseador";

    // Fetch pet's name
    let nombreMascota = "mascota";
    if (idMascota) {
        const petDoc = await db.collection("duenos").doc(idDueno).collection("mascotas").doc(idMascota).get();
        if (petDoc.exists) {
            nombreMascota = petDoc.data().nombre || nombreMascota;
        } else {
            const globalPetDoc = await db.collection("mascotas").doc(idMascota).get();
            if (globalPetDoc.exists) {
                nombreMascota = globalPetDoc.data().nombre || nombreMascota;
            }
        }
    }

    if (duenoToken) {
      const message = {
        token: duenoToken,
        notification: {
          title: "¡Tu paseo ha iniciado!",
          body: `El paseo de ${nombreMascota} con ${nombrePaseador} ha comenzado.`,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_CURRENT_WALK_ACTIVITY", // Opens PaseoEnCursoActivity for owner
          reservaId: reservaId, // Pass reservaId to activity
        },
      };

      try {
        await admin.messaging().send(message);
        console.log(`Notification sent to dueno ${idDueno} for started walk ${reservaId}`);
      } catch (error) {
        console.error(`Error sending notification to dueno ${idDueno}:`, error);
      }
    } else {
      console.warn(`No FCM token found for dueno ${idDueno}.`);
    }
  }
});

exports.onReservationCancelled = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();
  // const oldValue = event.data.before.data(); // Not strictly needed as we only care about new state

  // Check if the status changed to 'CANCELADO'
  if (newValue.estado === "CANCELADO") {
    console.log(`Reserva ${reservaId} cancelada. Enviando notificación al paseador.`);

    const idDueno = getIdValue(newValue.id_dueno);
    const idPaseador = getIdValue(newValue.id_paseador);
    const idMascota = newValue.id_mascota;

    // Fetch walker's FCM token
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;

    // Fetch owner's name
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const nombreDueno = duenoDoc.exists ? duenoDoc.data().nombre_display : "Dueño";

    // Fetch pet's name (same logic as before)
    let nombreMascota = "mascota";
    if (idMascota) {
        const petDoc = await db.collection("duenos").doc(idDueno).collection("mascotas").doc(idMascota).get();
        if (petDoc.exists) {
            nombreMascota = petDoc.data().nombre || nombreMascota;
        } else {
            const globalPetDoc = await db.collection("mascotas").doc(idMascota).get();
            if (globalPetDoc.exists) {
                nombreMascota = globalPetDoc.data().nombre || nombreMascota;
            }
        }
    }

    if (paseadorToken) {
      const message = {
        token: paseadorToken,
        notification: {
          title: "Solicitud cancelada",
          body: `${nombreDueno} ha cancelado la solicitud de paseo para ${nombreMascota}.`,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_REQUESTS_ACTIVITY", // Opens SolicitudesActivity for walker
          reservaId: reservaId, // Pass reservaId to activity
        },
      };

      try {
        await admin.messaging().send(message);
        console.log(`Notification sent to paseador ${idPaseador} for cancelled reservation ${reservaId}`);
      } catch (error) {
        console.error(`Error sending notification to paseador ${idPaseador}:`, error);
      }
    } else {
      console.warn(`No FCM token found for paseador ${idPaseador}.`);
    }
  }
});

exports.onRequestCancellation = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();
  const oldValue = event.data.before.data();

  // Check if status changed to SOLICITUD_CANCELACION
  if (newValue.estado === "SOLICITUD_CANCELACION" && oldValue.estado !== "SOLICITUD_CANCELACION") {
    console.log(`Solicitud de cancelación detectada para reserva ${reservaId}`);

    const idPaseador = getIdValue(newValue.id_paseador);
    const idDueno = getIdValue(newValue.id_dueno);

    // Fetch walker's FCM token
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;
    
    // Fetch owner's name for better context
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const nombreDueno = duenoDoc.exists ? duenoDoc.data().nombre_display : "El dueño";

    if (paseadorToken) {
      const message = {
        token: paseadorToken,
        notification: {
          title: "Solicitud de cancelación",
          body: `${nombreDueno} ha solicitado cancelar el paseo. Por favor revisa y responde.`,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_CURRENT_WALK_ACTIVITY", // Opens PaseoEnCursoActivity for walker
          reservaId: reservaId,
        },
      };

      try {
        await admin.messaging().send(message);
        console.log(`Notification sent to walker ${idPaseador} for cancellation request.`);
      } catch (error) {
        console.error(`Error sending notification to walker ${idPaseador}:`, error);
      }
    } else {
      console.warn(`No FCM token found for walker ${idPaseador}`);
    }
  }
});

exports.onPaseoUpdate = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();
  const oldValue = event.data.before.data();

  // 1. Check for new photos
  const oldFotos = oldValue.fotos_paseo || [];
  const newFotos = newValue.fotos_paseo || [];
  const hasNewPhoto = newFotos.length > oldFotos.length;

  // 2. Check for updated notes
  const oldNotas = oldValue.notas_paseador || "";
  const newNotas = newValue.notas_paseador || "";
  const hasNewNote = newNotas !== oldNotas && newNotas.length > 0;

  if (!hasNewPhoto && !hasNewNote) {
    return; // No relevant changes
  }

  console.log(`Paseo update detected for ${reservaId}: Photo=${hasNewPhoto}, Note=${hasNewNote}`);

  const idDueno = getIdValue(newValue.id_dueno);
  const idPaseador = getIdValue(newValue.id_paseador);
  const idMascota = newValue.id_mascota;

  // Fetch owner's FCM token
  const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
  const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;

  if (!duenoToken) {
    console.warn(`No FCM token found for owner ${idDueno}`);
    return;
  }

  // Fetch walker's name
  const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
  const nombrePaseador = paseadorDoc.exists ? paseadorDoc.data().nombre_display : "El paseador";

  let title = "Actualización del paseo";
  let body = `Hay novedades en el paseo de tu mascota.`;

  if (hasNewPhoto && hasNewNote) {
    title = "¡Nueva foto y nota!";
    body = `${nombrePaseador} ha añadido una foto y una nota al paseo.`;
  } else if (hasNewPhoto) {
    title = "¡Nueva foto del paseo!";
    body = `${nombrePaseador} ha añadido una nueva foto.`;
  } else if (hasNewNote) {
    title = "Nueva nota del paseador";
    body = `${nombrePaseador} dice: "${newNotas}"`;
  }

  const message = {
    token: duenoToken,
    notification: {
      title: title,
      body: body,
    },
    android: {
      notification: {
        icon: 'walki_logo_secundario'
      }
    },
    data: {
      click_action: "OPEN_CURRENT_WALK_OWNER",
      reservaId: reservaId,
    },
  };

  try {
    await admin.messaging().send(message);
    console.log(`Notification sent to owner ${idDueno} for paseo update.`);
  } catch (error) {
    console.error(`Error sending notification to owner ${idDueno}:`, error);
  }
});

exports.checkWalkReminders = onSchedule("every 60 minutes", async (event) => {
  console.log("Running scheduled job to check for walk reminders.");
  const now = admin.firestore.Timestamp.now();
  const oneHourFromNow = admin.firestore.Timestamp.fromMillis(now.toMillis() + (60 * 60 * 1000)); // 1 hour from now

  const reservationsSnapshot = await db.collection("reservas")
    .where("estado", "in", ["ACEPTADO", "CONFIRMADO"])
    .where("reminderSent", "==", false)
    .get();

  const usersToFetchTokens = new Set();
  const petsToFetch = new Map(); // idDueno -> Set<idMascota>

  for (const doc of reservationsSnapshot.docs) {
    const reserva = doc.data();
    const reservaId = doc.id;
    const idDueno = getIdValue(reserva.id_dueno);
    const idPaseador = getIdValue(reserva.id_paseador);
    const idMascota = getIdValue(reserva.id_mascota);
    
    let walkTimestamp;
    if (reserva.hora_inicio && reserva.hora_inicio.toDate) {
        walkTimestamp = reserva.hora_inicio;
    } else if (reserva.fecha && reserva.fecha.toDate) {
        // Fallback to 'fecha' if 'hora_inicio' is missing (though unlikely given app logic)
        walkTimestamp = reserva.fecha;
    } else {
        console.warn(`Reserva ${reservaId} has invalid date/time format.`);
        continue; 
    }

    if (walkTimestamp.toMillis() > now.toMillis() && walkTimestamp.toMillis() <= oneHourFromNow.toMillis()) {
      console.log(`Found upcoming walk ${reservaId} for reminder.`);
      
      if (idDueno) {
        usersToFetchTokens.add(idDueno);
      }
      if (idPaseador) {
        usersToFetchTokens.add(idPaseador);
      }

      if (idMascota && idDueno) {
        const petsForOwner = petsToFetch.get(idDueno) || new Set();
        petsForOwner.add(idMascota);
        petsToFetch.set(idDueno, petsForOwner);
      }
    }
  }

  if (usersToFetchTokens.size === 0) {
    console.log("No upcoming walks found for reminders.");
    return;
  }

  // Batch fetch user data
  const userDocs = await Promise.all(
    Array.from(usersToFetchTokens).map(uid => db.collection("usuarios").doc(uid).get())
  );
  const usersData = userDocs.reduce((acc, doc) => {
    if (doc.exists) acc[doc.id] = doc.data();
    return acc;
  }, {});

  // Batch fetch pet data
  const petPromises = [];
  petsToFetch.forEach((petIds, duenoId) => {
    petIds.forEach(petId => {
      petPromises.push(db.collection("duenos").doc(duenoId).collection("mascotas").doc(petId).get());
    });
  });
  const petDocs = await Promise.all(petPromises);
  const petsData = petDocs.reduce((acc, doc) => {
    if (doc.exists) acc[doc.id] = doc.data();
    return acc;
  }, {});

  const messagesToSend = [];
  const reservationsToUpdate = [];

  for (const doc of reservationsSnapshot.docs) {
    const reserva = doc.data();
    const reservaId = doc.id;
    const idDueno = getIdValue(reserva.id_dueno);
    const idPaseador = getIdValue(reserva.id_paseador);
    const idMascota = getIdValue(reserva.id_mascota);
    
    let walkTimestamp;
    if (reserva.hora_inicio && reserva.hora_inicio.toDate) {
        walkTimestamp = reserva.hora_inicio;
    } else {
        continue;
    }

    if (walkTimestamp.toMillis() > now.toMillis() && walkTimestamp.toMillis() <= oneHourFromNow.toMillis()) {
      const dueno = idDueno ? usersData[idDueno] : null;
      const paseador = idPaseador ? usersData[idPaseador] : null;
      const pet = idMascota ? petsData[idMascota] : null;

      const nombreDueno = dueno ? dueno.nombre_display : "Dueño";
      const nombrePaseador = paseador ? paseador.nombre_display : "Paseador";
      const nombreMascota = pet ? pet.nombre : "tu mascota";

      // Reminder for Owner
      if (dueno && dueno.fcmToken) {
        messagesToSend.push({
          token: dueno.fcmToken,
          notification: {
            title: "Recordatorio de paseo",
            body: `Tu paseo con ${nombrePaseador} para ${nombreMascota} es en 1 hora.`,
          },
          data: {
            click_action: "OPEN_WALKS_ACTIVITY",
            reservaId: reservaId,
          },
        });
      }

      // Reminder for Walker
      if (paseador && paseador.fcmToken) {
        messagesToSend.push({
          token: paseador.fcmToken,
          notification: {
            title: "Recordatorio de paseo",
            body: `Tienes un paseo programado con ${nombreDueno} para ${nombreMascota} en 1 hora.`,
          },
          data: {
            click_action: "OPEN_REQUESTS_ACTIVITY",
            reservaId: reservaId,
          },
        });
      }
      reservationsToUpdate.push(doc.ref.update({ reminderSent: true }));
    }
  }

  if (messagesToSend.length > 0) {
    try {
      const response = await admin.messaging().sendEach(messagesToSend);
      console.log("Successfully sent reminder messages:", response);
    } catch (error) {
      console.error("Error sending reminder messages:", error);
    }
  } else {
    console.log("No reminder messages to send.");
  }

  if (reservationsToUpdate.length > 0) {
    await Promise.all(reservationsToUpdate);
    console.log("Updated reminderSent status for processed reservations.");
  }
});

exports.transitionToInCourse = onSchedule("every 5 minutes", async (event) => {
  console.log("Running scheduled job to transition CONFIRMADO reservations to EN_CURSO.");
  const now = new Date();
  // Look back up to 24 hours to catch any missed transitions, not just the last 5 minutes.
  const twentyFourHoursAgo = new Date(now.getTime() - (24 * 60 * 60 * 1000)); 

  const reservationsSnapshot = await db.collection("reservas")
    .where("estado", "==", "CONFIRMADO")
    .where("hasTransitionedToInCourse", "==", false) // Ensure it hasn't been processed
    .get();

  const updates = [];

  for (const doc of reservationsSnapshot.docs) {
    const reserva = doc.data();
    const reservaId = doc.id;

    let scheduledStartTime;
    if (reserva.hora_inicio && reserva.hora_inicio.toDate) {
        scheduledStartTime = reserva.hora_inicio.toDate();
    } else {
        console.warn(`Reserva ${reservaId} has invalid hora_inicio format (not a Timestamp). Skipping.`);
        continue;
    }

    // If the scheduled start time is in the past AND it's within the last 24 hours
    if (scheduledStartTime.getTime() <= now.getTime() && scheduledStartTime.getTime() >= twentyFourHoursAgo.getTime()) {
      console.log(`Transitioning reservation ${reservaId} to EN_CURSO.`);
      updates.push(doc.ref.update({
        estado: "EN_CURSO",
        fecha_inicio_paseo: admin.firestore.Timestamp.fromDate(scheduledStartTime), // Use scheduled time as start
        hasTransitionedToInCourse: true,
      }));
    }
  }

  if (updates.length > 0) {
    try {
      await Promise.all(updates);
      console.log(`Successfully transitioned ${updates.length} reservations to EN_CURSO.`);
    } catch (error) {
      console.error("Error transitioning reservations to EN_CURSO:", error);
    }
  } else {
    console.log("No CONFIRMADO reservations to transition to EN_CURSO.");
  }
});

exports.onReservaStatusChange = onDocumentUpdated("reservas/{reservaId}", async (event) => {
    const { reservaId } = event.params;
    const newValue = event.data.after.data();
    const oldValue = event.data.before.data();
    
    if (!newValue || !oldValue) {
        return;
    }

    const oldStatus = oldValue.estado;
    const newStatus = newValue.estado;

    if (oldStatus === newStatus) {
        return; // No change in status
    }

    // Get paseador ID safely (handle Reference or String)
    const paseadorRef = newValue.id_paseador;
    let paseadorId = null;
    if (paseadorRef) {
        if (typeof paseadorRef === 'object' && paseadorRef.id) {
             paseadorId = paseadorRef.id;
        } else if (typeof paseadorRef === 'string') {
             paseadorId = paseadorRef;
        }
    }

    if (!paseadorId) {
        console.warn(`Reserva ${reservaId} no tiene un id_paseador válido.`);
        return;
    }

    // Determine if the walker should be "in walk" based on the new status
    let shouldBeInWalk = false;
    if (newStatus === "EN_CURSO" || newStatus === "EN_PROGRESO") {
        shouldBeInWalk = true;
    } else if (newStatus === "COMPLETADO" || newStatus === "CANCELADO") {
        shouldBeInWalk = false;
    } else {
        // For other statuses (PENDIENTE, etc.), we generally don't change the status 
        // unless we want to be strict. For now, we only act on explicit start/end states.
        return; 
    }

    const userDocRef = db.collection("usuarios").doc(paseadorId);
    
    try {
        const userDoc = await userDocRef.get();
        if (!userDoc.exists) {
            console.warn(`Paseador ${paseadorId} no encontrado para la reserva ${reservaId}.`);
            return;
        }

        const currentEnPaseo = userDoc.data().en_paseo || false;

        // Update only if the status is different
        if (currentEnPaseo !== shouldBeInWalk) {
            console.log(`Actualizando paseador ${paseadorId} en_paseo de ${currentEnPaseo} a ${shouldBeInWalk} para reserva ${reservaId}.`);
            
            const updates = {
                en_paseo: shouldBeInWalk,
                last_en_paseo_update: FieldValue.serverTimestamp()
            };

            // If starting a walk, track which reservation started it
            if (shouldBeInWalk) {
                updates.current_walk_reserva_id = reservaId;
            } else {
                // If ending a walk, only clear current_walk_reserva_id if it matches this reservation
                // This prevents a race condition where a NEW walk started before this one finished processing
                const currentReservaId = userDoc.data().current_walk_reserva_id;
                if (currentReservaId === reservaId) {
                    updates.current_walk_reserva_id = FieldValue.delete();
                }
            }

            await userDocRef.update(updates);
        } else {
             // Edge case: If already in walk, but for a DIFFERENT reservation, and this one starts...
             // We might want to update current_walk_reserva_id? 
             // For now, keep it simple.
             console.log(`Paseador ${paseadorId} ya tiene el estado en_paseo: ${shouldBeInWalk}. No se necesita actualizar.`);
        }

        // ---------------------------------------------------------
        // NUEVA LÓGICA: Incrementar contadores al completar paseo
        // ---------------------------------------------------------
        if (newStatus === "COMPLETADO" && oldStatus !== "COMPLETADO") {
            console.log(`Reserva ${reservaId} completada. Incrementando contadores.`);
            
            // 1. Actualizar Paseador (num_servicios_completados)
            const paseadorProfileRef = db.collection("paseadores").doc(paseadorId);
            await paseadorProfileRef.set({
                num_servicios_completados: FieldValue.increment(1)
            }, { merge: true });

            // 2. Actualizar Dueño (num_paseos_solicitados)
            // Nota: Extraemos el ID del dueño de manera segura
            const duenoRefRaw = newValue.id_dueno;
            let duenoId = null;
            if (duenoRefRaw) {
                if (typeof duenoRefRaw === 'string') duenoId = duenoRefRaw;
                else if (duenoRefRaw.id) duenoId = duenoRefRaw.id;
            }

            if (duenoId) {
                console.log(`Incrementando paseos para dueño ${duenoId}`);
                const duenoProfileRef = db.collection("duenos").doc(duenoId);
                await duenoProfileRef.set({
                    num_paseos_solicitados: FieldValue.increment(1)
                }, { merge: true });
            }
        }

    } catch (error) {
        console.error(`Error actualizando estado de paseador ${paseadorId}:`, error);
    }
});

// --- FUNCIÓN DE MANTENIMIENTO (Ejecutar una vez y borrar si se desea) ---
// Esta función recalcula el total de paseos completados para TODOS los paseadores Y DUEÑOS
exports.recalcularContadores = onRequest(async (req, res) => {
    try {
        console.log("Iniciando recálculo masivo de paseos completados...");
        
        // 1. Obtener todas las reservas completadas
        const reservasSnapshot = await db.collection("reservas")
            .where("estado", "==", "COMPLETADO")
            .get();

        console.log(`Se encontraron ${reservasSnapshot.size} reservas completadas en total.`);

        const paseadorCounts = {}; 
        const duenoCounts = {};

        // 2. Contar paseos
        reservasSnapshot.forEach(doc => {
            const data = doc.data();
            
            // -- Paseador --
            const paseadorRef = data.id_paseador;
            let paseadorId = null;
            if (paseadorRef) {
                if (typeof paseadorRef === 'string') paseadorId = paseadorRef;
                else if (paseadorRef.id) paseadorId = paseadorRef.id;
            }
            if (paseadorId) {
                if (!paseadorCounts[paseadorId]) paseadorCounts[paseadorId] = 0;
                paseadorCounts[paseadorId]++;
            }

            // -- Dueño --
            const duenoRef = data.id_dueno;
            let duenoId = null;
            if (duenoRef) {
                if (typeof duenoRef === 'string') duenoId = duenoRef;
                else if (duenoRef.id) duenoId = duenoRef.id;
            }
            if (duenoId) {
                if (!duenoCounts[duenoId]) duenoCounts[duenoId] = 0;
                duenoCounts[duenoId]++;
            }
        });

        // 3. Actualizar (Batch)
        const updates = [];
        let batch = db.batch();
        let counter = 0;

        // Actualizar Paseadores
        for (const [pid, total] of Object.entries(paseadorCounts)) {
            const ref = db.collection("paseadores").doc(pid);
            batch.set(ref, { num_servicios_completados: total }, { merge: true });
            counter++;
            if (counter >= 400) { updates.push(batch.commit()); batch = db.batch(); counter = 0; }
        }

        // Actualizar Dueños
        for (const [did, total] of Object.entries(duenoCounts)) {
            const ref = db.collection("duenos").doc(did);
            batch.set(ref, { num_paseos_solicitados: total }, { merge: true });
            counter++;
            if (counter >= 400) { updates.push(batch.commit()); batch = db.batch(); counter = 0; }
        }
        
        if (counter > 0) {
            updates.push(batch.commit());
        }

        await Promise.all(updates);

        res.status(200).send({
            message: "Recálculo completado exitosamente.",
            paseadores: paseadorCounts,
            duenos: duenoCounts
        });

    } catch (error) {
        console.error("Error en recálculo:", error);
        res.status(500).send({ error: error.message });
    }
});