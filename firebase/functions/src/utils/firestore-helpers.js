const { admin, db } = require('../config/firebase');

/**
 * Helper to get pet name from Firestore
 * Tries owner's subcollection first, then global mascotas collection
 * @param {string} duenoId - Owner's ID
 * @param {string} mascotaId - Pet's ID
 * @returns {Promise<string>} Pet name or default "tu mascota"
 */
async function obtenerNombreMascota(duenoId, mascotaId) {
  if (!mascotaId || !duenoId) return "tu mascota";

  try {
    // Try owner's pets subcollection first
    const petDoc = await db.collection("duenos").doc(duenoId)
      .collection("mascotas").doc(mascotaId).get();

    if (petDoc.exists) {
      return petDoc.data().nombre || "tu mascota";
    }

    // Fallback to global pets collection
    const globalPetDoc = await db.collection("mascotas").doc(mascotaId).get();
    if (globalPetDoc.exists) {
      return globalPetDoc.data().nombre || "tu mascota";
    }
  } catch (error) {
    console.error(`Error obteniendo nombre de mascota ${mascotaId}:`, error);
  }

  return "tu mascota";
}

async function commitBatchedUpdates(updateEntries, { batchSize = 450 } = {}) {
  if (!updateEntries || updateEntries.length === 0) return 0;

  const merged = new Map(); // ref.path -> { ref, data }
  for (const entry of updateEntries) {
    if (!entry || !entry.ref || !entry.data) continue;
    const key = entry.ref.path;
    const existing = merged.get(key);
    if (existing) {
      Object.assign(existing.data, entry.data);
    } else {
      merged.set(key, { ref: entry.ref, data: { ...entry.data } });
    }
  }

  const uniqueUpdates = Array.from(merged.values()).filter(u => u.data && Object.keys(u.data).length > 0);
  if (uniqueUpdates.length === 0) return 0;

  const commits = [];
  let batch = db.batch();
  let counter = 0;

  for (const { ref, data } of uniqueUpdates) {
    batch.update(ref, data);
    counter++;
    if (counter >= batchSize) {
      commits.push(batch.commit());
      batch = db.batch();
      counter = 0;
    }
  }

  if (counter > 0) {
    commits.push(batch.commit());
  }

  await Promise.all(commits);
  return uniqueUpdates.length;
}

async function sendEachAndUpdate(entries, logLabel) {
  if (!entries || entries.length === 0) return { total: 0, success: 0, failure: 0, updated: 0 };

  let response;
  try {
    response = await admin.messaging().sendEach(entries.map(e => e.message));
  } catch (error) {
    console.error(`${logLabel}: sendEach failed`, error);
    return { total: entries.length, success: 0, failure: entries.length, updated: 0 };
  }

  const updates = [];
  let success = 0;
  let failure = 0;

  for (let i = 0; i < response.responses.length; i++) {
    const r = response.responses[i];
    const entry = entries[i];
    if (r.success) {
      success++;
      if (entry.update && entry.ref) {
        updates.push({ ref: entry.ref, data: entry.update });
      }
    } else {
      failure++;
      console.error(`${logLabel}: send failed`, {
        docId: entry?.docId,
        error: r.error?.message || r.error
      });
    }
  }

  const updated = await commitBatchedUpdates(updates);
  return { total: entries.length, success, failure, updated };
}

// Cache de recomendaciones usando Firestore (persistente, no se pierde en cold starts)
const CACHE_TTL = 15 * 60 * 1000; // 15 minutos para ahorrar créditos de Gemini

/**
 * Obtiene recomendaciones desde cache de Firestore
 */
async function getCachedRecommendations(userId, petId) {
  try {
    const cacheRef = db.collection('recommendation_cache').doc(userId);
    const cacheDoc = await cacheRef.get();

    if (!cacheDoc.exists) {
      return null;
    }

    const cached = cacheDoc.data();
    const now = Date.now();

    // Verificar si el cache expiró
    if (now - cached.timestamp > CACHE_TTL) {
      await cacheRef.delete();
      return null;
    }

    // Verificar que sea para la misma mascota
    if (cached.petId !== petId) {
      return null;
    }

    return cached.recommendations;
  } catch (error) {
    console.error('Error leyendo cache:', error);
    return null;
  }
}

/**
 * Guarda recomendaciones en cache de Firestore
 */
async function saveCachedRecommendations(userId, petId, recommendations) {
  try {
    const cacheRef = db.collection('recommendation_cache').doc(userId);
    await cacheRef.set({
      recommendations: recommendations,
      timestamp: Date.now(),
      petId: petId
    });
  } catch (error) {
    console.error('Error guardando cache:', error);
  }
}

module.exports = {
  obtenerNombreMascota,
  commitBatchedUpdates,
  sendEachAndUpdate,
  getCachedRecommendations,
  saveCachedRecommendations
};
