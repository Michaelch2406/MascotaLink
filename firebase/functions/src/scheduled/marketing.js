const { onSchedule } = require("firebase-functions/v2/scheduler");
const { db, admin } = require('../config/firebase');
const { calculateDistance } = require('../utils/general');

/**
 * Función programada para notificar a dueños sobre paseadores disponibles en su zona.
 * Se ejecuta cada hora durante horarios de alta demanda (8am, 12pm, 5pm).
 */
exports.notifyNearbyWalkersScheduled = onSchedule({
    schedule: "0 8,12,17 * * *",
    timeZone: "America/Guayaquil"
}, async (event) => {
  console.log("Ejecutando búsqueda programada de paseadores cercanos");

  try {
    // Obtener paseadores verificados y disponibles
    const paseadoresSnapshot = await db.collection("paseadores")
      .where("acepta_solicitudes", "==", true)
      .get();

    if (paseadoresSnapshot.empty) {
      console.log("No hay paseadores disponibles");
      return null;
    }

    // Obtener dueños con notificaciones activadas
    const duenosSnapshot = await db.collection("duenos")
      .where("notificaciones_paseador_cerca", "==", true)
      .get();

    if (duenosSnapshot.empty) {
      console.log("No hay dueños con notificaciones activadas");
      return null;
    }

    const radioKm = 5; 
    const notificacionesPorDueno = new Map();
    const ahora = Date.now();
    const COOLDOWN_MS = 24 * 60 * 60 * 1000; 

    for (const duenoDoc of duenosSnapshot.docs) {
      const duenoData = duenoDoc.data();
      const duenoId = duenoDoc.id;

      const ubicacionDueno = duenoData.ubicacion || duenoData.direccion_ubicacion;
      if (!ubicacionDueno || !ubicacionDueno.latitude || !ubicacionDueno.longitude) {
        continue;
      }

      const ultimaNotificacionGeneral = duenoData.ultima_notificacion_paseadores_zona;
      if (ultimaNotificacionGeneral && (ahora - ultimaNotificacionGeneral) < COOLDOWN_MS) {
        continue;
      }

      let paseadoresCercanos = 0;
      let mejorCalificacion = 0;

      for (const paseadorDoc of paseadoresSnapshot.docs) {
        const paseadorData = paseadorDoc.data();

        // Verificar estado (case insensitive)
        const estado = paseadorData.verificacion_estado || "";
        if (estado.toLowerCase() !== "aprobado" && estado.toLowerCase() !== "verificado") {
            continue;
        }

        if (paseadorDoc.id === duenoId) continue;

        const ubicacionPaseador = paseadorData.ubicacion_actual || paseadorData.ubicacion_principal?.geopoint;
        if (!ubicacionPaseador || !ubicacionPaseador.latitude || !ubicacionPaseador.longitude) {
          continue;
        }

        const distancia = calculateDistance(
          ubicacionDueno.latitude, ubicacionDueno.longitude,
          ubicacionPaseador.latitude, ubicacionPaseador.longitude
        );

        if (distancia <= radioKm) {
          paseadoresCercanos++;
          const cal = paseadorData.calificacion_promedio || 0;
          if (cal > mejorCalificacion) {
            mejorCalificacion = cal;
          }
        }
      }

      if (paseadoresCercanos > 0) {
        notificacionesPorDueno.set(duenoId, {
          cantidad: paseadoresCercanos,
          mejorCalificacion: mejorCalificacion,
        });
      }
    }

    if (notificacionesPorDueno.size === 0) {
      console.log("No hay dueños con paseadores cercanos");
      return null;
    }

    const mensajes = [];
    for (const [duenoId, info] of notificacionesPorDueno) {
      const usuarioDoc = await db.collection("usuarios").doc(duenoId).get();
      if (!usuarioDoc.exists) continue;

      const fcmToken = usuarioDoc.data().fcmToken;
      if (!fcmToken) continue;

      const calStr = info.mejorCalificacion > 0
        ? ` (hasta ${info.mejorCalificacion.toFixed(1)}★)`
        : "";

      mensajes.push({
        token: fcmToken,
        notification: {
          title: "Paseadores disponibles en tu zona",
          body: `Hay ${info.cantidad} paseador${info.cantidad > 1 ? 'es' : ''} disponible${info.cantidad > 1 ? 's' : ''} cerca de ti${calStr}`,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          tipo: "paseadores_zona",
          click_action: "OPEN_SEARCH_ACTIVITY",
        },
        duenoId: duenoId,
      });
    }

    if (mensajes.length === 0) {
      return null;
    }

    const response = await admin.messaging().sendEach(
      mensajes.map(m => ({
        token: m.token,
        notification: m.notification,
        data: m.data,
        android: m.android
      }))
    );

    const batch = db.batch();
    for (let i = 0; i < response.responses.length; i++) {
      if (response.responses[i].success) {
        const duenoRef = db.collection("duenos").doc(mensajes[i].duenoId);
        batch.update(duenoRef, {
          ultima_notificacion_paseadores_zona: ahora,
        });
      }
    }
    await batch.commit();

    console.log(`Notificaciones programadas: ${response.successCount} exitosas, ${response.failureCount} fallidas`);
    return null;

  } catch (error) {
    console.error("Error en notifyNearbyWalkersScheduled:", error);
    return null;
  }
});
