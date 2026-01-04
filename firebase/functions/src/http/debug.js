const { onRequest } = require("firebase-functions/v2/https");
const { db, admin, Timestamp } = require('../config/firebase');
const { getIdValue, formatearNombresMascotas } = require('../utils/general');
const { formatTimeEcuador } = require('../utils/formatters');
const { sendEachAndUpdate, obtenerNombreMascota } = require('../utils/firestore-helpers');

// ============================================================================
// FUNCIONES HTTP PARA DESARROLLO LOCAL
// Permiten probar las notificaciones scheduled manualmente vía HTTP
// ============================================================================

exports.debugNotifyReady = onRequest(async (req, res) => {
  console.log("DEBUG: Manually triggering notifyWalkReadyWindow logic.");
  const now = Timestamp.now();
  const twentyMinutesFromNowMillis = now.toMillis() + (20 * 60 * 1000);
  const twentyMinutesFromNow = Timestamp.fromMillis(twentyMinutesFromNowMillis);
  const nowTimestamp = now;

  try {
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "CONFIRMADO")
      .where("hora_inicio", "<=", twentyMinutesFromNow)
      .where("hora_inicio", ">=", nowTimestamp)
      .get();

    if (reservationsSnapshot.empty) {
      res.status(200).send({ success: true, message: "No walks found entering ready window.", sent: 0 });
      return;
    }

    const entries = [];
    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();
      if (reserva.readyWindowNotificationSent || reserva.reminder15MinSent) continue;

      // ... (Lógica de grupo simplificada para debug) ...
      
      const paseadorId = getIdValue(reserva.id_paseador);
      if (!paseadorId) continue;

      const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
      if (!paseadorDoc.exists || !paseadorDoc.data().fcmToken) continue;
      
      const fcmToken = paseadorDoc.data().fcmToken;
      const idDueno = getIdValue(reserva.id_dueno);
      const idMascota = reserva.id_mascota;
      
      let nombreMascota = "la mascota";
      if (idMascota && idDueno) {
         nombreMascota = await obtenerNombreMascota(idDueno, idMascota);
      }

      const horaFormateada = formatTimeEcuador(reserva.hora_inicio);
      const message = {
        token: fcmToken,
        notification: {
          title: "Ya puedes iniciar el paseo",
          body: `Tu paseo con ${nombreMascota} esta programado para las ${horaFormateada}.`
        },
        data: { tipo: "ventana_inicio", reservaId: doc.id, click_action: "OPEN_CURRENT_WALK_ACTIVITY" }
      };

      entries.push({
        docId: doc.id,
        ref: doc.ref,
        update: { readyWindowNotificationSent: true, reminder15MinSent: true },
        message
      });
    }

    const result = await sendEachAndUpdate(entries, "debugNotifyReady");
    res.status(200).send({ success: true, result });
  } catch (error) {
    console.error("Error in debugNotifyReady:", error);
    res.status(500).send({ error: error.message });
  }
});

exports.debugNotifyOverdue = onRequest(async (req, res) => {
  console.log("DEBUG: Manually triggering notifyOverdueWalks logic.");
  const now = Timestamp.now();
  const twentyFourHoursAgoMillis = now.toMillis() - (24 * 60 * 60 * 1000);
  const twentyFourHoursAgo = Timestamp.fromMillis(twentyFourHoursAgoMillis);

  try {
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "LISTO_PARA_INICIAR")
      .where("hora_inicio", "<=", now)
      .where("hora_inicio", ">=", twentyFourHoursAgo)
      .get();

    if (reservationsSnapshot.empty) {
      res.status(200).send({ success: true, message: "No overdue walks found.", sent: 0 });
      return;
    }

    const entries = [];
    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();
      if (reserva.overdueNotificationSent) continue;

      const paseadorId = getIdValue(reserva.id_paseador);
      if (!paseadorId) continue;

      const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
      if (!paseadorDoc.exists || !paseadorDoc.data().fcmToken) continue;

      const message = {
        token: paseadorDoc.data().fcmToken,
        notification: {
          title: "Paseo Programado Pendiente",
          body: `El paseo programado para las ${formatTimeEcuador(reserva.hora_inicio)} aún no ha comenzado.`
        },
        data: { tipo: "paseo_retrasado", reservaId: doc.id, click_action: "OPEN_CURRENT_WALK_ACTIVITY" }
      };

      entries.push({
        docId: doc.id,
        ref: doc.ref,
        update: { overdueNotificationSent: true },
        message
      });
    }

    const result = await sendEachAndUpdate(entries, "debugNotifyOverdue");
    res.status(200).send({ success: true, result });
  } catch (error) {
    res.status(500).send({ error: error.message });
  }
});

exports.debugNotifyDelayed = onRequest(async (req, res) => {
  console.log("DEBUG: Manually triggering notifyDelayedWalks logic.");
  const now = Timestamp.now();
  // 10 minutos de retraso mínimo
  const tenMinutesAgoMillis = now.toMillis() - (10 * 60 * 1000);
  const tenMinutesAgo = Timestamp.fromMillis(tenMinutesAgoMillis);

  const delays = [
    { minutes: 30, field: 'delay30MinNotificationSent' },
    { minutes: 20, field: 'delay20MinNotificationSent' },
    { minutes: 10, field: 'delay10MinNotificationSent' }
  ];

  try {
    const reservationsSnapshot = await db.collection("reservas")
        .where("estado", "in", ["LISTO_PARA_INICIAR", "CONFIRMADO"])
        .where("hora_inicio", "<=", tenMinutesAgo)
        .get();

    if (reservationsSnapshot.empty) {
        res.status(200).send({ success: true, message: "No delayed walks found.", sent: 0 });
        return;
    }

    const entries = [];
    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();
      const retrasoMinutos = (now.toMillis() - reserva.hora_inicio.toMillis()) / 60000;

      for (const delay of delays) {
        if (retrasoMinutos >= delay.minutes && !reserva[delay.field]) {
            const paseadorId = getIdValue(reserva.id_paseador);
            if (!paseadorId) continue;

            const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
            if (!paseadorDoc.exists || !paseadorDoc.data().fcmToken) continue;

            const message = {
              token: paseadorDoc.data().fcmToken,
              notification: {
                title: "Paseo con Retraso",
                body: `El paseo lleva ${delay.minutes} minutos de retraso.`
              },
              data: { tipo: "paseo_retrasado", reservaId: doc.id, delay: delay.minutes.toString() }
            };

            entries.push({
              docId: doc.id,
              ref: doc.ref,
              update: { [delay.field]: true },
              message
            });
            break; // Solo una notificación por ciclo
        }
      }
    }

    const result = await sendEachAndUpdate(entries, "debugNotifyDelayed");
    res.status(200).send({ success: true, result });
  } catch (error) {
    res.status(500).send({ error: error.message });
  }
});

exports.debugNotifyReminder5Min = onRequest(async (req, res) => {
  console.log("DEBUG: Manually triggering sendReminder5MinBefore logic.");
  const now = Timestamp.now();
  const windowStart = now;
  const windowEnd = Timestamp.fromMillis(now.toMillis() + (10 * 60 * 1000));

  try {
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "CONFIRMADO")
      .where("hora_inicio", ">=", windowStart)
      .where("hora_inicio", "<=", windowEnd)
      .get();

    if (reservationsSnapshot.empty) {
      res.status(200).send({ success: true, message: "No walks for 5-min reminder.", sent: 0 });
      return;
    }

    const entries = [];
    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();
      if (reserva.reminder5MinSent) continue;

      const paseadorId = getIdValue(reserva.id_paseador);
      if (!paseadorId) continue;

      const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
      if (!paseadorDoc.exists || !paseadorDoc.data().fcmToken) continue;

      const message = {
        token: paseadorDoc.data().fcmToken,
        notification: {
          title: "¡Paseo Próximo!",
          body: `Tu paseo comienza en 5 minutos.`
        },
        data: { tipo: "recordatorio_paseo", reservaId: doc.id }
      };

      entries.push({
        docId: doc.id,
        ref: doc.ref,
        update: { reminder5MinSent: true },
        message
      });
    }

    const result = await sendEachAndUpdate(entries, "debugNotifyReminder5Min");
    res.status(200).send({ success: true, result });
  } catch (error) {
    res.status(500).send({ error: error.message });
  }
});
