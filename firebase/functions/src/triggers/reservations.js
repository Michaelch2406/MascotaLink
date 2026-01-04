const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { db, admin, FieldValue } = require('../config/firebase');
const { getIdValue } = require('../utils/general');
const { formatearNombresMascotas } = require('../utils/general');
const { obtenerNombreMascota } = require('../utils/firestore-helpers');
const { formatTimeEcuador, formatDateEcuador } = require('../utils/formatters');

exports.onNewReservation = onDocumentCreated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newReservation = event.data.data();

  if (newReservation.estado === "PENDIENTE_ACEPTACION") {
    const esGrupo = newReservation.es_grupo;
    const grupoReservaId = newReservation.grupo_reserva_id;

    // Si es parte de un grupo, verificar si debe enviar notificación
    if (esGrupo && grupoReservaId) {
      console.log(`Reserva ${reservaId} es parte del grupo ${grupoReservaId}.`);

      // OPTIMIZACIÓN: Usar campo es_primer_dia_grupo si existe
      if (newReservation.es_primer_dia_grupo !== undefined) {
        // Usar el campo (0 queries adicionales)
        if (!newReservation.es_primer_dia_grupo) {
          console.log(`⚡ Saltando - no es el primer día del grupo (optimizado)`);
          return;
        }
        console.log(`⚡ Es el primer día del grupo - enviando notificación (optimizado)`);
      } else {
        // FALLBACK: Lógica antigua para compatibilidad con reservas sin el campo
        console.log(`  Usando fallback (reserva sin campo es_primer_dia_grupo)`);
        await new Promise(resolve => setTimeout(resolve, 3000));

        const grupoSnapshot = await db.collection("reservas")
          .where("grupo_reserva_id", "==", grupoReservaId)
          .where("estado", "==", "PENDIENTE_ACEPTACION")
          .get();

        const reservasGrupo = grupoSnapshot.docs.sort((a, b) => {
          const aTime = a.data().fecha_creacion?.toMillis() || 0;
          const bTime = b.data().fecha_creacion?.toMillis() || 0;
          return aTime - bTime;
        });

        if (reservasGrupo.length > 0 && reservasGrupo[0].id !== reservaId) {
          console.log(`Notificación ya enviada por otra reserva del grupo. Saltando.`);
          return;
        }
        console.log(`Esta es la primera reserva del grupo. Enviando notificación.`);
      }
    } else {
      console.log(`Nueva solicitud de paseo ${reservaId} creada. Enviando notificación al paseador.`);
    }

    const idDueno = getIdValue(newReservation.id_dueno);
    const idPaseador = getIdValue(newReservation.id_paseador);
    const idMascota = newReservation.id_mascota;

    // Fetch walker's FCM token
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;

    // Fetch owner's name
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const nombreDueno = duenoDoc.exists ? duenoDoc.data().nombre_display : "Dueño";

    // Fetch pet names (soporta múltiples mascotas)
    let nombresMascotas;
    const mascotasNombres = newReservation.mascotas_nombres;
    if (mascotasNombres && mascotasNombres.length > 0) {
      nombresMascotas = formatearNombresMascotas(mascotasNombres);
    } else {
      const nombreMascota = await obtenerNombreMascota(idDueno, idMascota);
      nombresMascotas = nombreMascota;
    }

    // Construir mensaje según si es grupo o individual
    let notificationBody;
    if (esGrupo && grupoReservaId) {
      // Para grupos, obtener información de fechas
      const grupoSnapshot = await db.collection("reservas")
        .where("grupo_reserva_id", "==", grupoReservaId)
        .get();

      const cantidadDias = grupoSnapshot.size;
      const fechas = grupoSnapshot.docs.map(doc => doc.data().fecha).sort((a, b) => {
        const aTime = a?.toMillis() || 0;
        const bTime = b?.toMillis() || 0;
        return aTime - bTime;
      });

      const fechaInicio = formatDateEcuador(fechas[0]);
      const fechaFin = formatDateEcuador(fechas[fechas.length - 1]);
      const hora = formatTimeEcuador(newReservation.hora_inicio);

      if (cantidadDias > 1) {
        notificationBody = `${nombreDueno} ha solicitado un paseo para ${nombresMascotas} - ${cantidadDias} días (${fechaInicio} - ${fechaFin}) a las ${hora}.`;
      } else {
        notificationBody = `${nombreDueno} ha solicitado un paseo para ${nombresMascotas} el ${fechaInicio} a las ${hora}.`;
      }
    } else {
      // Reserva individual
      const fecha = formatDateEcuador(newReservation.fecha);
      const hora = formatTimeEcuador(newReservation.hora_inicio);
      notificationBody = `${nombreDueno} ha solicitado un paseo para ${nombresMascotas} el ${fecha} a las ${hora}.`;
    }

    if (paseadorToken) {
      const message = {
        token: paseadorToken,
        notification: {
          title: "¡Nueva solicitud de paseo!",
          body: notificationBody,
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
    const esGrupo = newValue.es_grupo;
    const grupoReservaId = newValue.grupo_reserva_id;

    // Si es parte de un grupo, evitar notificaciones duplicadas
    if (esGrupo && grupoReservaId) {
      console.log(`Reserva ${reservaId} del grupo ${grupoReservaId} aceptada.`);

      // OPTIMIZACIÓN: Usar campo es_primer_dia_grupo si existe
      if (newValue.es_primer_dia_grupo !== undefined) {
        if (!newValue.es_primer_dia_grupo) {
          console.log(`⚡ Saltando - no es el primer día del grupo (optimizado)`);
          return;
        }
        console.log(`⚡ Es el primer día - enviando notificación (optimizado)`);
      } else {
        // FALLBACK: Lógica antigua para compatibilidad
        console.log(`  Usando fallback (reserva sin campo es_primer_dia_grupo)`);
        await new Promise(resolve => setTimeout(resolve, 2000));

        const grupoSnapshot = await db.collection("reservas")
          .where("grupo_reserva_id", "==", grupoReservaId)
          .where("estado", "==", "ACEPTADO")
          .get();

        const reservasGrupo = grupoSnapshot.docs.sort((a, b) => {
          const aTime = a.data().fecha_respuesta?.toMillis() || 0;
          const bTime = b.data().fecha_respuesta?.toMillis() || 0;
          return aTime - bTime;
        });

        if (reservasGrupo.length > 0 && reservasGrupo[0].id !== reservaId) {
          console.log(`Notificación de aceptación ya enviada por otra reserva del grupo. Saltando.`);
          return;
        }
        console.log(`Primera reserva del grupo aceptada. Enviando notificación agrupada.`);
      }
    } else {
      console.log(`Reserva ${reservaId} aceptada. Enviando notificación al dueño.`);
    }

    const idDueno = getIdValue(newValue.id_dueno);
    const idPaseador = getIdValue(newValue.id_paseador);
    const idMascota = newValue.id_mascota;

    if (!idDueno) return;
    if (!idPaseador) return;

    // Fetch owner's FCM token
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;

    // Fetch walker's name
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const nombrePaseador = paseadorDoc.exists ? paseadorDoc.data().nombre_display : "Paseador";

    // Fetch pet names (soporta múltiples mascotas)
    let nombresMascotas;
    const mascotasNombres = newValue.mascotas_nombres;
    if (mascotasNombres && mascotasNombres.length > 0) {
      nombresMascotas = formatearNombresMascotas(mascotasNombres);
    } else {
      const nombreMascota = await obtenerNombreMascota(idDueno, idMascota);
      nombresMascotas = nombreMascota;
    }

    // Construir mensaje según si es grupo o individual
    let notificationBody;
    if (esGrupo && grupoReservaId) {
      let cantidadDias = 1;

      // ⚡ OPTIMIZACIÓN: Usar campo denormalizado si existe
      if (newValue.cantidad_dias_grupo !== undefined) {
        cantidadDias = newValue.cantidad_dias_grupo;
      } else {
        const grupoSnapshot = await db.collection("reservas")
          .where("grupo_reserva_id", "==", grupoReservaId)
          .get();
        cantidadDias = grupoSnapshot.size;
      }

      if (cantidadDias > 1) {
        notificationBody = `El paseador ${nombrePaseador} ha aceptado tu solicitud para ${nombresMascotas} - ${cantidadDias} días.`;
      } else {
        notificationBody = `El paseador ${nombrePaseador} ha aceptado tu solicitud para ${nombresMascotas}.`;
      }
    } else {
      notificationBody = `El paseador ${nombrePaseador} ha aceptado tu solicitud para ${nombresMascotas}.`;
    }

    if (duenoToken) {
      const message = {
        token: duenoToken,
        notification: {
          title: "¡Paseo aceptado!",
          body: notificationBody,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
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

    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;

    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const nombrePaseador = paseadorDoc.exists ? paseadorDoc.data().nombre_display : "Paseador";

    let nombresMascotas;
    const mascotasNombres = newValue.mascotas_nombres;
    if (mascotasNombres && mascotasNombres.length > 0) {
      nombresMascotas = formatearNombresMascotas(mascotasNombres);
    } else {
      const nombreMascota = await obtenerNombreMascota(idDueno, idMascota);
      nombresMascotas = nombreMascota;
    }

    if (duenoToken) {
      const message = {
        token: duenoToken,
        notification: {
          title: "¡Tu paseo ha iniciado!",
          body: `El paseo de ${nombresMascotas} con ${nombrePaseador} ha comenzado.`,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_CURRENT_WALK_OWNER", // Opens PaseoEnCursoDuenoActivity for owner
          reservaId: reservaId, // Pass reservaId to activity
        },
      };

      try {
        await admin.messaging().send(message);
        console.log(`Notification sent to dueno ${idDueno} for started walk ${reservaId}`);
      } catch (error) {
        console.error(`Error sending notification to dueno ${idDueno}:`, error);
      }
    }
  }
});

exports.onReservationCancelled = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();

  if (newValue.estado === "CANCELADO") {
    console.log(`Reserva ${reservaId} cancelada. Enviando notificación al paseador.`);

    const idDueno = getIdValue(newValue.id_dueno);
    const idPaseador = getIdValue(newValue.id_paseador);
    const idMascota = newValue.id_mascota;

    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;

    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const nombreDueno = duenoDoc.exists ? duenoDoc.data().nombre_display : "Dueño";

    let nombresMascotas;
    const mascotasNombres = newValue.mascotas_nombres;
    if (mascotasNombres && mascotasNombres.length > 0) {
      nombresMascotas = formatearNombresMascotas(mascotasNombres);
    } else {
      const nombreMascota = await obtenerNombreMascota(idDueno, idMascota);
      nombresMascotas = nombreMascota;
    }

    if (paseadorToken) {
      const message = {
        token: paseadorToken,
        notification: {
          title: "Solicitud cancelada",
          body: `${nombreDueno} ha cancelado la solicitud de paseo para ${nombresMascotas}.`,
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
    }
  }
});

exports.onRequestCancellation = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();
  const oldValue = event.data.before.data();

  if (newValue.estado === "SOLICITUD_CANCELACION" && oldValue.estado !== "SOLICITUD_CANCELACION") {
    console.log(`Solicitud de cancelación detectada para reserva ${reservaId}`);

    const idPaseador = getIdValue(newValue.id_paseador);
    const idDueno = getIdValue(newValue.id_dueno);

    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;
    
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

  // Fetch owner's FCM token
  const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
  const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;

  if (!duenoToken) return;

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
    title = "Nota del paseador";
    body = `${nombrePaseador}: ${newNotas}`;
  }

  const message = {
    token: duenoToken,
    notification: {
      title: title,
      body: body,
    },
    android: {
      notification: {
        icon: 'walki_logo_secundario',
        tag: `walker_note_${idPaseador}_${reservaId}`
      }
    },
    data: {
      click_action: "OPEN_CURRENT_WALK_OWNER",
      reservaId: reservaId,
      walkerName: nombrePaseador,
      note: newNotas
    },
  };

  try {
    await admin.messaging().send(message);
    console.log(`Notification sent to owner ${idDueno} for paseo update.`);
  } catch (error) {
    console.error(`Error sending notification to owner ${idDueno}:`, error);
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

    let shouldBeInWalk = false;
    if (newStatus === "EN_CURSO") {
        shouldBeInWalk = true;
    } else if (newStatus === "COMPLETADO" || newStatus === "CANCELADO") {
        shouldBeInWalk = false;
    } else {
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

        if (currentEnPaseo !== shouldBeInWalk) {
            console.log(`Actualizando paseador ${paseadorId} en_paseo de ${currentEnPaseo} a ${shouldBeInWalk} para reserva ${reservaId}.`);
            
            const updates = {
                en_paseo: shouldBeInWalk,
                last_en_paseo_update: FieldValue.serverTimestamp()
            };

            if (shouldBeInWalk) {
                updates.current_walk_reserva_id = reservaId;
            } else {
                const currentReservaId = userDoc.data().current_walk_reserva_id;
                if (currentReservaId === reservaId) {
                    updates.current_walk_reserva_id = FieldValue.delete();
                }
            }

            await userDocRef.update(updates);
        }

        // ---------------------------------------------------------
        // NUEVA LÓGICA: Incrementar contadores al completar paseo
        // ---------------------------------------------------------
        if (newStatus === "COMPLETADO" && oldStatus !== "COMPLETADO") {
            console.log(`Reserva ${reservaId} completada. Incrementando contadores.`);
            
            // 1. Actualizar Paseador
            const paseadorProfileRef = db.collection("paseadores").doc(paseadorId);
            await paseadorProfileRef.set({
                num_servicios_completados: FieldValue.increment(1)
            }, { merge: true });

            // 2. Actualizar Dueño
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