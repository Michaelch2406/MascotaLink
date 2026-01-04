const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { db } = require('../config/firebase');

/**
 * Trigger automático para desnormalizar nuevas reseñas de dueños
 */
exports.desnormalizarResenasDuenos = onDocumentCreated(
  "resenas_duenos/{resenaId}",
  async (event) => {
      const resenaData = event.data.data();

      try {
          const autorId = resenaData.paseadorId;
          if (!autorId) return;

          const autorDoc = await db.collection('usuarios').doc(autorId).get();
          if (!autorDoc.exists) return;

          const autorData = autorDoc.data();

          await event.data.ref.update({
              autorId: autorId,
              autorNombre: autorData.nombre_display || 'Paseador',
              autorFotoUrl: autorData.foto_perfil || null,
              autorRol: 'paseador'
          });

          console.log(`✓ Reseña de dueño ${event.params.resenaId} desnormalizada`);
      } catch (error) {
          console.error("Error desnormalizando reseña de dueño:", error);
      }
  }
);

/**
 * Trigger automático para desnormalizar nuevas reseñas de paseadores
 */
exports.desnormalizarResenasPaseadores = onDocumentCreated(
  "resenas_paseadores/{resenaId}",
  async (event) => {
      const resenaData = event.data.data();

      try {
          const autorId = resenaData.duenoId;
          if (!autorId) return;

          const autorDoc = await db.collection('usuarios').doc(autorId).get();
          if (!autorDoc.exists) return;

          const autorData = autorDoc.data();

          await event.data.ref.update({
              autorId: autorId,
              autorNombre: autorData.nombre_display || 'Usuario de Walki',
              autorFotoUrl: autorData.foto_perfil || null,
              autorRol: 'dueno'
          });

          console.log(`✓ Reseña de paseador ${event.params.resenaId} desnormalizada`);
      } catch (error) {
          console.error("Error desnormalizando reseña de paseador:", error);
      }
  }
);
