const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();

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
      tarifa_por_hora: paseadorData.tarifa_por_hora || 0,
      tipos_perro_aceptados: paseadorData.tipos_perro_aceptados || [],
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
