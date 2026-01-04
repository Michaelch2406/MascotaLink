const { onSchedule } = require("firebase-functions/v2/scheduler");
const { db, admin } = require('../config/firebase');

/**
 * Limpia mensajes cuya fecha_eliminacion ya expiró (7 días) en todas las conversaciones.
 * Corre diario a las 19:00 hora de Ciudad de México.
 */
exports.cleanupOldMessages = onSchedule("0 19 * * *", {
  timeZone: "America/Guayaquil",
}, async () => {
  const now = admin.firestore.Timestamp.now();
  const batchSize = 450;
  let totalDeleted = 0;

  const runOnce = async () => {
    const snap = await db.collectionGroup("mensajes")
      .where("fecha_eliminacion", "<=", now)
      .limit(batchSize)
      .get();
    if (snap.empty) return false;
    const batch = db.batch();
    snap.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    totalDeleted += snap.size;
    return snap.size === batchSize;
  };

  let shouldContinue = true;
  while (shouldContinue) {
    shouldContinue = await runOnce();
  }

  console.log(`cleanupOldMessages: eliminados ${totalDeleted} mensajes.`);
  return null;
});
