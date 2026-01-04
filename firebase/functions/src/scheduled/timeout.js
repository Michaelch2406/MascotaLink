const { onSchedule } = require("firebase-functions/v2/scheduler");
const { db, admin } = require('../config/firebase');
const { getIdValue } = require('../utils/general');

/**
 * HU-P04: Revisa cada 5 minutos las reservas pendientes que han expirado.
 * Regla de negocio: "15 minutos de vida máxima para cualquier solicitud pendiente"
 * desde el momento de su creación, sin importar la hora programada del paseo.
 */
exports.checkReservationTimeout = onSchedule("every 5 minutes", async (event) => {
  console.log("Running scheduled job to check for expired reservations (15 min timeout).");
  
  const now = admin.firestore.Timestamp.now();
  // 15 minutos en milisegundos
  const timeoutMillis = 15 * 60 * 1000; 
  // Calcular timestamp límite: ahora - 15 min
  const timeoutThreshold = admin.firestore.Timestamp.fromMillis(now.toMillis() - timeoutMillis);

  try {
    const expiredReservations = await db.collection("reservas")
      .where("estado", "==", "PENDIENTE_ACEPTACION")
      .where("fecha_creacion", "<=", timeoutThreshold)
      .get();

    if (expiredReservations.empty) {
      console.log("No expired reservations found.");
      return;
    }

    console.log(`Found ${expiredReservations.size} expired reservations.`);

    const batch = db.batch();
    const notifications = [];

    for (const doc of expiredReservations.docs) {
      const reserva = doc.data();
      const reservaId = doc.id;
      
      // 1. Actualizar estado a RECHAZADO_TIMEOUT
      batch.update(doc.ref, { 
        estado: "RECHAZADO_TIMEOUT",
        motivo_rechazo: "Tiempo de espera agotado (15 min)",
        fecha_respuesta: now
      });

      // 2. Preparar notificaciones
      const idDueno = getIdValue(reserva.id_dueno);
      const idPaseador = getIdValue(reserva.id_paseador);

      // Obtener tokens
      const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
      const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();

      const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;
      const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;

      // Notificar al dueño
      if (duenoToken) {
        notifications.push({
          token: duenoToken,
          notification: {
            title: "Solicitud expirada",
            body: "El paseador no respondió a tiempo (15 min). Tu solicitud ha caducado.",
          },
          android: { notification: { icon: 'walki_logo_secundario' } },
          data: { click_action: "OPEN_WALKS_ACTIVITY", reservaId: reservaId }
        });
      }

      // Notificar al paseador
      if (paseadorToken) {
        notifications.push({
          token: paseadorToken,
          notification: {
            title: "Solicitud perdida",
            body: "Se agotaron los 15 min para responder. La solicitud ha sido cancelada.",
          },
          android: { notification: { icon: 'walki_logo_secundario' } },
          data: { click_action: "OPEN_REQUESTS_ACTIVITY", reservaId: reservaId }
        });
      }
    }

    // 3. Ejecutar batch y enviar notificaciones
    await batch.commit();
    console.log(`Successfully expired ${expiredReservations.size} reservations.`);

    if (notifications.length > 0) {
      const response = await admin.messaging().sendEach(notifications);
      console.log(`Sent expiration notifications: ${response.successCount} success, ${response.failureCount} failure`);
    }

  } catch (error) {
    console.error("Error in checkReservationTimeout:", error);
  }
});
