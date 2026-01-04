const { onDocumentWritten, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { db, admin } = require('../config/firebase');
const { sincronizarPaseador } = require('../utils/sync-helpers');
const { calculateDistance } = require('../utils/general');

exports.onUsuarioWrite = onDocumentWritten("usuarios/{userId}", async (event) => {
  const { userId } = event.params;
  await sincronizarPaseador(userId);
});

exports.onPaseadorWrite = onDocumentWritten("paseadores/{paseadorId}", async (event) => {
  const { paseadorId } = event.params;
  await sincronizarPaseador(paseadorId);
});

/**
 * Notifica a dueños cuando un paseador verificado está disponible cerca de su ubicación.
 * Se ejecuta cuando un paseador actualiza su ubicación o disponibilidad.
 */
exports.notifyNearbyWalkerAvailable = onDocumentUpdated("paseadores/{paseadorId}", async (event) => {
  const beforeData = event.data.before.data();
  const afterData = event.data.after.data();
  const paseadorId = event.params.paseadorId;

  // Solo procesar si el paseador está verificado y acepta solicitudes
  const estadoVerificacion = afterData.verificacion_estado || "";
  if (estadoVerificacion.toLowerCase() !== "aprobado" && estadoVerificacion.toLowerCase() !== "verificado") {
    return null;
  }

  // Verificar si cambió la ubicación o el estado de disponibilidad
  const ubicacionAntes = beforeData.ubicacion_actual;
  const ubicacionDespues = afterData.ubicacion_actual;
  const aceptaSolicitudesAntes = beforeData.acepta_solicitudes;
  const aceptaSolicitudesDespues = afterData.acepta_solicitudes;

  // Solo notificar si:
  // 1. El paseador acaba de activar "acepta_solicitudes"
  // 2. O si actualizó su ubicación mientras acepta solicitudes
  const acabaDeActivar = !aceptaSolicitudesAntes && aceptaSolicitudesDespues;
  const ubicacionCambio = ubicacionDespues &&
    (!ubicacionAntes ||
     ubicacionAntes.latitude !== ubicacionDespues.latitude ||
     ubicacionAntes.longitude !== ubicacionDespues.longitude);

  if (!acabaDeActivar && !(aceptaSolicitudesDespues && ubicacionCambio)) {
    return null;
  }

  if (!ubicacionDespues || !ubicacionDespues.latitude || !ubicacionDespues.longitude) {
    console.log(`Paseador ${paseadorId} no tiene ubicación válida`);
    return null;
  }

  const paseadorLat = ubicacionDespues.latitude;
  const paseadorLng = ubicacionDespues.longitude;
  const radioKm = 3; // Radio de búsqueda en kilómetros

  console.log(`Buscando dueños cerca de paseador ${paseadorId} en radio de ${radioKm}km`);

  try {
    const paseadorNombre = afterData.nombre_display || afterData.nombre || "Un paseador";
    const calificacion = afterData.calificacion_promedio || 0;
    const calificacionStr = calificacion > 0 ? ` (${calificacion.toFixed(1)}★)` : "";

    const duenosSnapshot = await db.collection("duenos")
      .where("notificaciones_paseador_cerca", "==", true)
      .get();

    if (duenosSnapshot.empty) {
      console.log("No hay dueños con notificaciones de paseador cerca activadas");
      return null;
    }

    const notificaciones = [];
    const ahora = Date.now();
    const COOLDOWN_MS = 4 * 60 * 60 * 1000; 

    for (const duenoDoc of duenosSnapshot.docs) {
      const duenoData = duenoDoc.data();
      const duenoId = duenoDoc.id;

      if (duenoId === paseadorId) continue;

      const ubicacionDueno = duenoData.ubicacion || duenoData.direccion_ubicacion;
      if (!ubicacionDueno || !ubicacionDueno.latitude || !ubicacionDueno.longitude) {
        continue;
      }

      const distancia = calculateDistance(
        paseadorLat, paseadorLng,
        ubicacionDueno.latitude, ubicacionDueno.longitude
      );

      if (distancia > radioKm) {
        continue;
      }

      const ultimaNotificacion = duenoData.ultima_notificacion_paseador_cerca || {};
      const ultimaDelPaseador = ultimaNotificacion[paseadorId];
      if (ultimaDelPaseador && (ahora - ultimaDelPaseador) < COOLDOWN_MS) {
        continue;
      }

      const usuarioDoc = await db.collection("usuarios").doc(duenoId).get();
      if (!usuarioDoc.exists) continue;

      const fcmToken = usuarioDoc.data().fcmToken;
      if (!fcmToken) continue;

      const distanciaStr = distancia < 1
        ? `a ${Math.round(distancia * 1000)}m`
        : `a ${distancia.toFixed(1)}km`;

      notificaciones.push({
        token: fcmToken,
        notification: {
          title: "Paseador disponible cerca",
          body: `${paseadorNombre}${calificacionStr} está disponible ${distanciaStr} de ti`,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          tipo: "paseador_cerca",
          paseador_id: paseadorId,
          click_action: "OPEN_WALKER_PROFILE", 
        },
        duenoId: duenoId,
      });
    }

    if (notificaciones.length === 0) {
      console.log("No hay dueños cercanos para notificar");
      return null;
    }

    console.log(`Enviando ${notificaciones.length} notificaciones de paseador cerca`);

    const response = await admin.messaging().sendEach(
      notificaciones.map(n => ({
        token: n.token,
        notification: n.notification,
        data: n.data,
        android: n.android
      }))
    );

    const batch = db.batch();
    for (let i = 0; i < response.responses.length; i++) {
      if (response.responses[i].success) {
        const duenoRef = db.collection("duenos").doc(notificaciones[i].duenoId);
        batch.update(duenoRef, {
          [`ultima_notificacion_paseador_cerca.${paseadorId}`]: ahora,
        });
      }
    }
    await batch.commit();

    return null;

  } catch (error) {
    console.error("Error en notifyNearbyWalkerAvailable:", error);
    return null;
  }
});