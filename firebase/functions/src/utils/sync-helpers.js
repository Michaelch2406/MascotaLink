const { db, FieldValue } = require('../config/firebase');

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

    // Obtener años de experiencia directamente del campo numérico
    let anosExperiencia = 0;
    if (paseadorData.perfil_profesional?.anos_experiencia !== undefined) {
      anosExperiencia = parseInt(paseadorData.perfil_profesional.anos_experiencia, 10) || 0;
      console.log(`📊 Años de experiencia obtenidos directamente para ${docId}: ${anosExperiencia}`);
    } else {
      // Fallback: intentar extraer de experiencia_general (para datos antiguos)
      const experienciaGeneral = paseadorData.perfil_profesional?.experiencia_general || paseadorData.experiencia_general;
      if (experienciaGeneral && typeof experienciaGeneral === 'string') {
        const match = experienciaGeneral.match(/\d+/);
        if (match) {
          anosExperiencia = parseInt(match[0], 10);
        }
      }
      console.log(`📊 Años de experiencia calculados (fallback) para ${docId}: ${anosExperiencia} (de: "${experienciaGeneral}")`);
    }

    // 🆕 DENORMALIZACIÓN: Obtener top 3 reseñas recientes
    let topResenas = [];
    try {
      const resenasSnapshot = await db.collection("resenas_paseadores")
        .where("paseadorId", "==", docId) //  CORREGIDO: paseadorId (camelCase)
        .orderBy("timestamp", "desc") //  CORREGIDO: timestamp (no fecha_creacion)
        .limit(3)
        .get();

      topResenas = resenasSnapshot.docs.map(doc => {
        const data = doc.data();
        return {
          texto: data.comentario || "",
          calificacion: data.calificacion || 0
        };
      });

      if (topResenas.length > 0) {
        console.log(` ${topResenas.length} reseñas encontradas para ${docId}`);
      }
    } catch (error) {
      console.log(` Error obteniendo reseñas para ${docId}: ${error.message}`);
      console.log(`   Posible causa: Falta índice en Firestore para (paseadorId, timestamp)`);
    }

    // 🆕 DENORMALIZACIÓN: Obtener zonas de servicio principales
    let zonasPrincipales = [];
    try {
      const zonasSnapshot = await paseadorRef
        .collection("zonas_servicio")
        .limit(5)
        .get();

      // Función helper para verificar si un string parece ser un ID/geohash
      const esIdOGeohash = (str) => {
        if (!str || typeof str !== 'string') return true;
        // Si tiene menos de 3 caracteres o no tiene espacios y es alfanumérico, probablemente es un ID
        if (str.length < 3) return true;
        // Si no tiene espacios y solo contiene letras/números, probablemente es un ID
        if (!/\s/.test(str) && /^[a-zA-Z0-9]+$/.test(str)) return true;
        return false;
      };

      zonasPrincipales = zonasSnapshot.docs.map(doc => {
        const data = doc.data();
        // Intentar obtener el nombre legible de la zona en orden de prioridad
        let zona = data.nombre || data.zona || data.direccion || data.barrio || data.ciudad || data.sector || null;

        // Verificar que no sea un ID/geohash
        if (zona && esIdOGeohash(zona)) {
          console.log(`⚠️ Zona descartada (parece ID): "${zona}" para paseador ${docId}`);
          return null;
        }

        return zona || "Zona no especificada";
      }).filter(zona => zona && zona !== "Zona no especificada");

      // Si no se encontraron zonas válidas, agregar un valor por defecto
      if (zonasPrincipales.length === 0) {
        zonasPrincipales = ["Zona no especificada"];
      }
    } catch (error) {
      console.log(`No se pudieron obtener zonas para ${docId}:`, error.message);
    }

    // Construir objeto optimizado para búsquedas
    const searchData = {
      nombre_display: userData.nombre_display || userData.nombre_completo || userData.nombre || userData.displayName || userData.display_name || "Usuario",
      foto_perfil: userData.foto_perfil || "",
      calificacion_promedio: paseadorData.calificacion_promedio || 0,
      num_servicios_completados: paseadorData.estadisticas?.paseos_completados || 0,
      precio_hora: paseadorData.tarifas?.precio_hora || 0,
      tipos_perro_aceptados: paseadorData.preferencias?.tipos_perro || [],
      anos_experiencia: anosExperiencia,
      verificacion_estado: paseadorData.verificacion_estado || "pendiente",
      activo: userData.activo !== false, // Por defecto true si no existe
      ubicacion: userData.ubicacion_actual || userData.ubicacion || null,
      top_resenas: topResenas,
      zonas_principales: zonasPrincipales,
      total_resenas: paseadorData.total_resenas || 0,
      updated_at: FieldValue.serverTimestamp()
    };

    await searchRef.set(searchData, { merge: true });
    console.log(`✅ Datos de búsqueda sincronizados para ${docId}`);

  } catch (error) {
    console.error(`Error sincronizando paseador ${docId}:`, error);
  }
}

module.exports = { sincronizarPaseador };
