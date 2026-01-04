const { onSchedule } = require("firebase-functions/v2/scheduler");
const { db, admin } = require('../config/firebase');
const { getIdValue, formatearNombresMascotas } = require('../utils/general');
const { formatTimeEcuador } = require('../utils/formatters');
const { obtenerNombreMascota, sendEachAndUpdate } = require('../utils/firestore-helpers');

exports.checkWalkReminders = onSchedule("every 60 minutes", async (event) => {
  console.log("Running scheduled job to check for walk reminders.");
  const now = admin.firestore.Timestamp.now();
  const oneHourFromNow = admin.firestore.Timestamp.fromMillis(now.toMillis() + (60 * 60 * 1000)); // 1 hour from now

  const reservationsSnapshot = await db.collection("reservas")
    .where("estado", "in", ["ACEPTADO", "CONFIRMADO"])
    .where("reminderSent", "==", false)
    .where("hora_inicio", ">=", now)
    .where("hora_inicio", "<=", oneHourFromNow)
    .get();

  const usersToFetchTokens = new Set();
  const petsToFetch = new Map(); // idDueno -> Set<idMascota>

  for (const doc of reservationsSnapshot.docs) {
    const reserva = doc.data();
    const reservaId = doc.id;

    if (!reserva.hora_inicio || !reserva.hora_inicio.toDate) {
      console.warn(`Reserva ${reservaId} has invalid hora_inicio.`);
      continue;
    }

    console.log(`Found upcoming walk ${reservaId} for reminder.`);

    const idDueno = getIdValue(reserva.id_dueno);
    const idPaseador = getIdValue(reserva.id_paseador);
    const idMascota = getIdValue(reserva.id_mascota);

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

      let nombresMascotas;
      const mascotasNombres = reserva.mascotas_nombres;
      if (mascotasNombres && mascotasNombres.length > 0) {
        nombresMascotas = formatearNombresMascotas(mascotasNombres);
      } else {
        nombresMascotas = pet ? pet.nombre : "tu mascota";
      }

      // Reminder for Owner
      if (dueno && dueno.fcmToken) {
        messagesToSend.push({
          token: dueno.fcmToken,
          notification: {
            title: "Recordatorio de paseo",
            body: `Tu paseo con ${nombrePaseador} para ${nombresMascotas} es en 1 hora.`,
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
            body: `Tienes un paseo programado con ${nombreDueno} para ${nombresMascotas} en 1 hora.`,
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
  }

  if (reservationsToUpdate.length > 0) {
    await Promise.all(reservationsToUpdate);
    console.log("Updated reminderSent status for processed reservations.");
  }
});

exports.transitionToInCourse = onSchedule("every 1 minutes", async (event) => {
  console.log("Running scheduled job to transition CONFIRMADO reservations to LISTO_PARA_INICIAR.");
  const now = admin.firestore.Timestamp.now();

  const fifteenMinutesFromNowMillis = now.toMillis() + (15 * 60 * 1000);
  const fifteenMinutesFromNow = admin.firestore.Timestamp.fromMillis(fifteenMinutesFromNowMillis);
  const twentyFourHoursAgoMillis = now.toMillis() - (24 * 60 * 60 * 1000);
  const twentyFourHoursAgo = admin.firestore.Timestamp.fromMillis(twentyFourHoursAgoMillis);

  try {
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "CONFIRMADO")
      .where("hora_inicio", "<=", fifteenMinutesFromNow)
      .where("hora_inicio", ">=", twentyFourHoursAgo)
      .get();

    if (reservationsSnapshot.empty) {
      console.log("No CONFIRMADO reservations found pending transition.");
      return;
    }

    console.log(`Found ${reservationsSnapshot.size} reservations to transition.`);

    const batches = [];
    let currentBatch = db.batch();
    let operationCounter = 0;
    let processedCount = 0;

    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();
      const millisUntilStart = reserva.hora_inicio.toMillis() - now.toMillis();

      if (millisUntilStart > (15 * 60 * 1000)) continue;
      if (reserva.estado !== "CONFIRMADO") continue;

      currentBatch.update(doc.ref, {
        estado: "LISTO_PARA_INICIAR",
        hasTransitionedToReady: true,
        actualizado_por_sistema: true,
        last_updated: now
      });

      operationCounter++;
      processedCount++;

      if (operationCounter >= 499) {
        batches.push(currentBatch.commit());
        currentBatch = db.batch();
        operationCounter = 0;
      }
    }

    if (operationCounter > 0) {
      batches.push(currentBatch.commit());
    }

    await Promise.all(batches);
    console.log(`Successfully transitioned ${processedCount} reservations to LISTO_PARA_INICIAR.`);

  } catch (error) {
    console.error("Error transitioning reservations to LISTO_PARA_INICIAR:", error);
  }
});

exports.sendReminder15MinBefore = onSchedule("every 1 minutes", async (event) => {
  // Disabled as per original code logic, delegated to notifyWalkReadyWindow
  console.log("sendReminder15MinBefore: disabled (use notifyWalkReadyWindow).");
  return;
});

exports.sendReminder5MinBefore = onSchedule("every 5 minutes", async (event) => {
  console.log("Running scheduled job to send 5-minute reminders.");
  const now = admin.firestore.Timestamp.now();
  const windowStartMillis = now.toMillis();
  const windowEndMillis = now.toMillis() + (10 * 60 * 1000);
  const windowStart = admin.firestore.Timestamp.fromMillis(windowStartMillis);
  const windowEnd = admin.firestore.Timestamp.fromMillis(windowEndMillis);

  try {
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "CONFIRMADO")
      .where("hora_inicio", ">=", windowStart)
      .where("hora_inicio", "<=", windowEnd)
      .get();

    if (reservationsSnapshot.empty) {
      console.log("No reservations found for 5-minute reminders.");
      return;
    }

    console.log(`Found ${reservationsSnapshot.size} reservations for 5-minute reminders.`);

    const entries = [];

    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();

      if (reserva.reminder5MinSent) continue;

      const esGrupo = reserva.es_grupo;
      const grupoReservaId = reserva.grupo_reserva_id;
      let cantidadDias = 1;

      if (esGrupo && grupoReservaId) {
        if (reserva.es_primer_dia_grupo !== undefined) {
          if (!reserva.es_primer_dia_grupo) {
            await doc.ref.update({ reminder5MinSent: true });
            continue;
          }
        } else {
          // FALLBACK
          const grupoSnapshot = await db.collection("reservas")
            .where("grupo_reserva_id", "==", grupoReservaId)
            .get();

          const fechas = grupoSnapshot.docs.map(d => ({
            id: d.id,
            fecha: d.data().fecha
          })).sort((a, b) => {
            const aTime = a.fecha?.toMillis() || 0;
            const bTime = b.fecha?.toMillis() || 0;
            return aTime - bTime;
          });

          if (fechas.length > 0 && fechas[0].id !== doc.id) {
            await doc.ref.update({ reminder5MinSent: true });
            continue;
          }
        }

        if (reserva.cantidad_dias_grupo !== undefined) {
          cantidadDias = reserva.cantidad_dias_grupo;
        } else {
           const grupoSnapshot = await db.collection("reservas")
            .where("grupo_reserva_id", "==", grupoReservaId)
            .get();
          cantidadDias = grupoSnapshot.size;
        }
      }

      const paseadorRef = reserva.id_paseador;
      let paseadorId = null;
      if (paseadorRef) {
        if (typeof paseadorRef === 'object' && paseadorRef.id) {
          paseadorId = paseadorRef.id;
        } else if (typeof paseadorRef === 'string') {
          paseadorId = paseadorRef;
        }
      }

      if (!paseadorId) continue;

      const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
      if (!paseadorDoc.exists) continue;

      const fcmToken = paseadorDoc.data().fcmToken;
      if (!fcmToken) continue;

      const horaFormateada = formatTimeEcuador(reserva.hora_inicio);

      let notificationBody;
      if (cantidadDias > 1) {
        notificationBody = `Tu paseo comienza en 5 minutos (${horaFormateada}). Primer día de ${cantidadDias} días. Es hora de prepararte.`;
      } else {
        notificationBody = `Tu paseo comienza en 5 minutos (${horaFormateada}). Es hora de prepararte.`;
      }

      const message = {
        token: fcmToken,
        notification: {
          title: "¡Paseo Próximo!",
          body: notificationBody
        },
        data: {
          tipo: "recordatorio_paseo",
          reservaId: doc.id,
          click_action: "OPEN_CURRENT_WALK_ACTIVITY"
        },
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channelId: "paseos_channel",
            tag: "reserva_" + doc.id + "_reminder5"
          }
        }
      };

      entries.push({
        docId: doc.id,
        ref: doc.ref,
        update: { reminder5MinSent: true },
        message
      });

      const idDueno = getIdValue(reserva.id_dueno);
      if (idDueno) {
        const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
        if (duenoDoc.exists) {
          const duenoToken = duenoDoc.data().fcmToken;
          if (duenoToken) {
            let nombresMascotas = "tus mascotas";
            const mascotasNombres = reserva.mascotas_nombres;
            if (mascotasNombres && mascotasNombres.length > 0) {
              nombresMascotas = formatearNombresMascotas(mascotasNombres);
            }

            let duenoNotificationBody;
            if (cantidadDias > 1) {
              duenoNotificationBody = `El paseo de ${nombresMascotas} comienza en 5 minutos (${horaFormateada}). Primer día de ${cantidadDias} días.`;
            } else {
              duenoNotificationBody = `El paseo de ${nombresMascotas} comienza en 5 minutos (${horaFormateada}).`;
            }

            const messageDueno = {
              token: duenoToken,
              notification: {
                title: "¡Paseo Próximo!",
                body: duenoNotificationBody
              },
              data: {
                tipo: "recordatorio_paseo_dueno",
                reservaId: doc.id,
                click_action: "OPEN_CURRENT_WALK_OWNER"
              },
              android: {
                priority: "high",
                notification: {
                  sound: "default",
                  channelId: "paseos_channel",
                  tag: "reserva_" + doc.id + "_reminder5_owner"
                }
              }
            };

            entries.push({
              docId: doc.id,
              ref: doc.ref,
              update: { reminder5MinSent: true },
              message: messageDueno
            });
          }
        }
      }
    }

    if (entries.length > 0) {
      const result = await sendEachAndUpdate(entries, "sendReminder5MinBefore");
      console.log(`sendReminder5MinBefore: sent=${result.success} failed=${result.failure} updated=${result.updated}`);
    } else {
      console.log("No 5-minute reminders to send.");
    }

    console.log("Completed 5-minute reminder job.");
  } catch (error) {
    console.error("Error in 5-minute reminder job:", error);
  }
});

exports.notifyOverdueWalks = onSchedule("every 5 minutes", async (event) => {
  console.log("Running scheduled job to notify overdue walks.");
  const now = admin.firestore.Timestamp.now();
  const twentyFourHoursAgoMillis = now.toMillis() - (24 * 60 * 60 * 1000);
  const twentyFourHoursAgo = admin.firestore.Timestamp.fromMillis(twentyFourHoursAgoMillis);

  try {
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "LISTO_PARA_INICIAR")
      .where("hora_inicio", "<=", now)
      .where("hora_inicio", ">=", twentyFourHoursAgo)
      .get();

    if (reservationsSnapshot.empty) {
      console.log("No overdue LISTO_PARA_INICIAR walks found.");
      return;
    }

    console.log(`Found ${reservationsSnapshot.size} overdue walks.`);

    const entries = [];

    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();

      if (reserva.overdueNotificationSent) continue;

      const paseadorRef = reserva.id_paseador;
      let paseadorId = null;
      if (paseadorRef) {
        if (typeof paseadorRef === 'object' && paseadorRef.id) {
          paseadorId = paseadorRef.id;
        } else if (typeof paseadorRef === 'string') {
          paseadorId = paseadorRef;
        }
      }

      if (!paseadorId) continue;

      const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
      if (!paseadorDoc.exists) continue;

      const fcmToken = paseadorDoc.data().fcmToken;
      if (!fcmToken) continue;

      const horaFormateada = formatTimeEcuador(reserva.hora_inicio);

      const message = {
        token: fcmToken,
        notification: {
          title: "Paseo Programado Pendiente",
          body: `El paseo programado para las ${horaFormateada} aún no ha comenzado. Toca para iniciar.`
        },
        data: {
          tipo: "paseo_retrasado",
          reservaId: doc.id,
          click_action: "OPEN_CURRENT_WALK_ACTIVITY"
        },
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channelId: "paseos_channel",
            tag: "reserva_" + doc.id + "_overdue"
          }
        }
      };

      entries.push({
        docId: doc.id,
        ref: doc.ref,
        update: { overdueNotificationSent: true },
        message
      });
    }

    if (entries.length > 0) {
      const result = await sendEachAndUpdate(entries, "notifyOverdueWalks");
      console.log(`notifyOverdueWalks: sent=${result.success} failed=${result.failure} updated=${result.updated}`);
    } else {
      console.log("No overdue notifications to send.");
    }
  } catch (error) {
    console.error("Error in overdue walks notification job:", error);
  }
});

exports.notifyWalkReadyWindow = onSchedule("every 1 minutes", async (event) => {
  console.log("Running scheduled job to notify walkers when ready window starts.");
  const now = admin.firestore.Timestamp.now();

  const twentyMinutesFromNowMillis = now.toMillis() + (20 * 60 * 1000);
  const twentyMinutesFromNow = admin.firestore.Timestamp.fromMillis(twentyMinutesFromNowMillis);
  const nowTimestamp = now;

  try {
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "CONFIRMADO")
      .where("hora_inicio", "<=", twentyMinutesFromNow)
      .where("hora_inicio", ">=", nowTimestamp) 
      .get();

    if (reservationsSnapshot.empty) {
      console.log("No walks found entering ready window.");
      return;
    }

    console.log(`Found ${reservationsSnapshot.size} potential walks for ready window.`);

    const entries = [];

    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();

      if (reserva.readyWindowNotificationSent || reserva.reminder15MinSent) continue;

      const esGrupo = reserva.es_grupo;
      const grupoReservaId = reserva.grupo_reserva_id;
      if (esGrupo && grupoReservaId) {
        if (reserva.es_primer_dia_grupo !== undefined) {
          if (!reserva.es_primer_dia_grupo) {
            await doc.ref.update({ readyWindowNotificationSent: true, reminder15MinSent: true });
            continue;
          }
        } else {
          // FALLBACK
          try {
            const grupoSnapshot = await db.collection("reservas")
              .where("grupo_reserva_id", "==", grupoReservaId)
              .get();

            const fechas = grupoSnapshot.docs.map(d => ({
              id: d.id,
              fecha: d.data().fecha
            })).sort((a, b) => {
              const aTime = a.fecha?.toMillis() || 0;
              const bTime = b.fecha?.toMillis() || 0;
              return aTime - bTime;
            });

            if (fechas.length > 0 && fechas[0].id !== doc.id) {
              await doc.ref.update({ readyWindowNotificationSent: true, reminder15MinSent: true });
              continue;
            }
          } catch (err) {
            console.error("Error checking grupo_reserva_id for ready window:", err);
          }
        }
      }

      const paseadorRef = reserva.id_paseador;
      let paseadorId = null;
      if (paseadorRef) {
        if (typeof paseadorRef === 'object' && paseadorRef.id) {
          paseadorId = paseadorRef.id;
        } else if (typeof paseadorRef === 'string') {
          paseadorId = paseadorRef;
        }
      }

      if (!paseadorId) continue;

      const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
      if (!paseadorDoc.exists) continue;

      const fcmToken = paseadorDoc.data().fcmToken;
      if (!fcmToken) continue;

      const idDueno = getIdValue(reserva.id_dueno);
      const idMascota = reserva.id_mascota;

      let nombresMascotas;
      const mascotasNombres = reserva.mascotas_nombres;
      if (mascotasNombres && mascotasNombres.length > 0) {
        nombresMascotas = formatearNombresMascotas(mascotasNombres);
      } else {
        let nombreMascota = "la mascota";
        if (idMascota && idDueno) {
          try {
            const mascotaDoc = await db.collection("duenos").doc(idDueno)
              .collection("mascotas").doc(idMascota).get();
            if (mascotaDoc.exists) {
              nombreMascota = mascotaDoc.data().nombre || nombreMascota;
            }
          } catch (err) {
            console.error("Error fetching mascota:", err);
          }
        }
        nombresMascotas = nombreMascota;
      }

      const horaFormateada = formatTimeEcuador(reserva.hora_inicio);

      const message = {
        token: fcmToken,
        notification: {
          title: "Ya puedes iniciar el paseo",
          body: `Tu paseo con ${nombresMascotas} esta programado para las ${horaFormateada}. Ya puedes iniciar cuando llegues.`
        },
        data: {
          tipo: "ventana_inicio",
          reservaId: doc.id,
          click_action: "OPEN_CURRENT_WALK_ACTIVITY"
        },
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channelId: "paseos_channel",
            tag: "reserva_" + doc.id + "_ready_window"
          }
        }
      };

      entries.push({
        docId: doc.id,
        ref: doc.ref,
        update: { readyWindowNotificationSent: true, reminder15MinSent: true },
        message
      });

      if (idDueno) {
        const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
        if (duenoDoc.exists) {
          const duenoToken = duenoDoc.data().fcmToken;
          if (duenoToken) {
            const messageDueno = {
              token: duenoToken,
              notification: {
                title: "El paseo está por comenzar",
                body: `El paseo de ${nombresMascotas} está programado para las ${horaFormateada}. El paseador ya puede iniciarlo.`
              },
              data: {
                tipo: "ventana_inicio_dueno",
                reservaId: doc.id,
                click_action: "OPEN_CURRENT_WALK_OWNER"
              },
              android: {
                priority: "high",
                notification: {
                  sound: "default",
                  channelId: "paseos_channel",
                  tag: "reserva_" + doc.id + "_ready_window_owner"
                }
              }
            };

            entries.push({
              docId: doc.id,
              ref: doc.ref,
              update: { readyWindowNotificationSent: true, reminder15MinSent: true },
              message: messageDueno
            });
          }
        }
      }
    }

    if (entries.length > 0) {
      const result = await sendEachAndUpdate(entries, "notifyWalkReadyWindow");
      console.log(`notifyWalkReadyWindow: sent=${result.success} failed=${result.failure} updated=${result.updated}`);
    } else {
      console.log("No ready window notifications to send.");
    }

  } catch (error) {
    console.error("Error in ready window notification job:", error);
  }
});

exports.notifyDelayedWalks = onSchedule("every 5 minutes", async (event) => {
  console.log("Running scheduled job for delayed walk notifications.");
  const now = admin.firestore.Timestamp.now();

  const delays = [
    { minutes: 30, field: 'delay30MinNotificationSent', message: 'El paseo lleva 30 minutos de retraso. Considera cancelar si no puedes asistir.' },
    { minutes: 20, field: 'delay20MinNotificationSent', message: 'El paseo lleva 20 minutos de retraso. Por favor contacta al dueno si hay algun problema.' },
    { minutes: 10, field: 'delay10MinNotificationSent', message: 'El paseo programado hace 10 minutos aun no ha comenzado.' }
  ];

  try {
    const tenMinutesAgoMillis = now.toMillis() - (10 * 60 * 1000);
    const tenMinutesAgo = admin.firestore.Timestamp.fromMillis(tenMinutesAgoMillis);

    const reservationsSnapshot = await db.collection("reservas")
        .where("estado", "in", ["LISTO_PARA_INICIAR", "CONFIRMADO"])
        .where("hora_inicio", "<=", tenMinutesAgo)
        .get();

    if (reservationsSnapshot.empty) return;

    const entries = [];

    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();
      const horaInicioMillis = reserva.hora_inicio.toMillis();
      const retrasoMillis = now.toMillis() - horaInicioMillis;
      const retrasoMinutos = retrasoMillis / (60 * 1000);

      if (reserva.estado === 'CONFIRMADO') {
        try {
          await doc.ref.update({
            estado: 'LISTO_PARA_INICIAR',
            hasTransitionedToReady: true,
            actualizado_por_sistema: true,
            last_updated: now
          });
          reserva.estado = 'LISTO_PARA_INICIAR';
        } catch (err) {
          console.error(`Error autocorrigiendo estado para ${doc.id}:`, err);
        }
      }

      for (const delay of delays) {
        if (retrasoMinutos >= delay.minutes && !reserva[delay.field]) {
            const paseadorRef = reserva.id_paseador;
            let paseadorId = null;
            if (paseadorRef) {
              if (typeof paseadorRef === 'object' && paseadorRef.id) {
                paseadorId = paseadorRef.id;
              } else if (typeof paseadorRef === 'string') {
                paseadorId = paseadorRef;
              }
            }
    
            if (!paseadorId) continue;

            const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
            if (!paseadorDoc.exists) continue;

            const fcmToken = paseadorDoc.data().fcmToken;
            if (!fcmToken) continue;

            const idDueno = getIdValue(reserva.id_dueno);
            const idMascota = getIdValue(reserva.id_mascota);
            let nombreDueno = "el dueño";

            let nombresMascotas;
            const mascotasNombres = reserva.mascotas_nombres;
            if (mascotasNombres && mascotasNombres.length > 0) {
              nombresMascotas = formatearNombresMascotas(mascotasNombres);
            } else {
              let nombreMascota = "sus mascotas";
              if (idDueno && idMascota) {
                nombreMascota = await obtenerNombreMascota(idDueno, idMascota);
              }
              nombresMascotas = nombreMascota;
            }

            if (idDueno) {
              const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
              if (duenoDoc.exists) {
                nombreDueno = duenoDoc.data().nombre_display || "el dueño";
              }
            }

            const messagePaseador = {
              token: fcmToken,
              notification: {
                title: `Paseo Pendiente de ${nombreDueno}`,
                body: `El paseo con ${nombreDueno} para ${nombresMascotas}, lleva ${delay.minutes} minutos de retraso.`
              },
              data: {
                tipo: "paseo_retrasado",
                reservaId: doc.id,
                delay: delay.minutes.toString(),
                click_action: "OPEN_CURRENT_WALK_ACTIVITY"
              },
              android: {
                priority: "high",
                notification: {
                  sound: "default",
                  channelId: "paseos_channel",
                  tag: "reserva_" + doc.id + "_delay_" + delay.minutes
                }
              }
            };

            entries.push({
              docId: doc.id,
              ref: doc.ref,
              update: { [delay.field]: true },
              message: messagePaseador
            });

            if (delay.minutes === 10 || delay.minutes === 20 || delay.minutes === 30) {
              if (idDueno) {
                const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
                if (duenoDoc.exists) {
                  const duenoToken = duenoDoc.data().fcmToken;
                  if (duenoToken) {
                    const nombrePaseador = paseadorDoc.exists ? paseadorDoc.data().nombre_display || "el paseador" : "el paseador";

                    const messageDueno = {
                      token: duenoToken,
                      notification: {
                        title: `Paseo Pendiente de ${nombrePaseador}`,
                        body: `El paseo de ${nombresMascotas} con el paseador lleva ${delay.minutes} minutos de retraso.`
                      },
                      data: {
                        tipo: "paseo_retrasado_dueno",
                        reservaId: doc.id,
                        delay: delay.minutes.toString(),
                        click_action: "OPEN_CURRENT_WALK_OWNER"
                      },
                      android: {
                        priority: "high",
                        notification: {
                          sound: "default",
                          channelId: "paseos_channel",
                          tag: "reserva_" + doc.id + "_delay_owner_" + delay.minutes
                        }
                      }
                    };
    
                    entries.push({
                      docId: doc.id,
                      ref: doc.ref,
                      update: { [delay.field]: true },
                      message: messageDueno
                    });
                  }
                }
              }
            }
            break; 
        }
      }
    }

    if (entries.length > 0) {
      await sendEachAndUpdate(entries, "notifyDelayedWalks");
    }
  } catch (error) {
    console.error("Error in delayed walk notifications job:", error);
  }
});
