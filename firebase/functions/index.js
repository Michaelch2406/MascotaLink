const { onDocumentWritten, onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onRequest } = require("firebase-functions/v2/https");
const { onCall } = require("firebase-functions/v2/https"); // Import onCall for callable functions
const { defineString } = require("firebase-functions/params"); // Nuevo: sistema de parámetros
const admin = require("firebase-admin");
const { getFirestore, FieldValue } = require('firebase-admin/firestore');

// Import the Google Generative AI client library
const { GoogleGenerativeAI } = require("@google/generative-ai");

// Define Gemini API Key usando el nuevo sistema de parámetros (reemplaza functions.config())
// Este parámetro se define en Firebase usando: firebase functions:secrets:set GEMINI_API_KEY
const geminiApiKey = defineString("GEMINI_API_KEY");

// Inicializar Gemini AI con el nuevo sistema
let genAI = null;
let model = null;

// La inicialización real ocurre cuando se ejecuta la función
// porque geminiApiKey.value() solo está disponible en runtime
function initializeGemini() {
  if (genAI) return; // Ya inicializado

  const apiKey = geminiApiKey.value();

  if (!apiKey) {
    console.error("GEMINI_API_KEY is not set. Gemini AI features will be disabled.");
    return;
  }

  genAI = new GoogleGenerativeAI(apiKey);
  model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });
  console.log("✅ Gemini AI initialized successfully");
}

admin.initializeApp();
const db = admin.firestore();

const getIdValue = (value) => (value && typeof value === "object" && value.id ? value.id : value);

/**
 * Calculates distance between two geographical points using the Haversine formula.
 * @param {number} lat1 Latitude of point 1
 * @param {number} lon1 Longitude of point 1
 * @param {number} lat2 Latitude of point 2
 * @param {number} lon2 Longitude of point 2
 * @returns {number} Distance in kilometers
 */
function calculateDistance(lat1, lon1, lat2, lon2) {
  const R = 6371; // Radius of Earth in kilometers
  const dLat = (lat2 - lat1) * (Math.PI / 180);
  const dLon = (lon2 - lon1) * (Math.PI / 180);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * (Math.PI / 180)) * Math.cos(lat2 * (Math.PI / 180)) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  const distance = R * c; // Distance in km
  return distance;
}

/**
 * Cloud Function to recommend walkers based on user and pet criteria using Gemini AI.
 * This is a callable function, invoked directly from the Android app.
 */
exports.recomendarPaseadores = onCall(async (request) => {
  // Inicializar Gemini AI si aún no está inicializado
  initializeGemini();

  if (!model) {
    console.error("Gemini AI model is not initialized. Check GEMINI_API_KEY.");
    return { error: "Gemini AI no está disponible. Contacta al soporte." };
  }

  const { userData, petData, userLocation } = request.data;
  const userId = request.auth?.uid;

  if (!userId) {
    throw new functions.https.HttpsError('unauthenticated', 'El usuario no está autenticado.');
  }

  if (!userData || !petData || !userLocation) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'Faltan datos de usuario, mascota o ubicación.'
    );
  }

  console.log(`Recomendación para usuario ${userId}, mascota ${petData.nombre}`);

  const userLat = userLocation.latitude;
  const userLng = userLocation.longitude;
  const radiusKm = 10; // Search within 10 km radius

  let potentialWalkers = [];

  try {
    // 1. Fetch potential walkers from paseadores_search collection
    // This collection is denormalized and contains searchable walker data.
    const searchSnapshot = await db.collection("paseadores_search")
      .where("activo", "==", true)
      .where("verificacion_estado", "==", "verificado") // Only recommend verified walkers
      .get();

    const walkerIds = [];
    const walkerDataMap = {}; // To store full walker data

    for (const doc of searchSnapshot.docs) {
      const walkerSearchData = doc.data();
      const walkerId = doc.id;
      walkerIds.push(walkerId);
      walkerDataMap[walkerId] = walkerSearchData;
    }

    if (walkerIds.length === 0) {
      return { recommendations: [], message: "No se encontraron paseadores activos y verificados." };
    }

    // 2. Fetch full paseador profile data and user display name for prompt
    const fullWalkerPromises = walkerIds.map(id => db.collection("paseadores").doc(id).get());
    const userDisplayPromises = walkerIds.map(id => db.collection("usuarios").doc(id).get());

    const [fullWalkerDocs, userDisplayDocs] = await Promise.all([
      Promise.all(fullWalkerPromises),
      Promise.all(userDisplayPromises)
    ]);

    const paseadoresForAI = [];

    for (let i = 0; i < walkerIds.length; i++) {
      const walkerId = walkerIds[i];
      const searchData = walkerDataMap[walkerId];
      const fullWalkerData = fullWalkerDocs[i].exists ? fullWalkerDocs[i].data() : {};

      const walkerLocation = fullWalkerData.ubicacion_principal?.geopoint;

      // Filter by distance if location is available
      if (walkerLocation && walkerLocation.latitude && walkerLocation.longitude) {
        const distance = calculateDistance(userLat, userLng, walkerLocation.latitude, walkerLocation.longitude);
        if (distance <= radiusKm) {
          paseadoresForAI.push({
            id: walkerId,
            nombre: searchData.nombre_display || `Paseador ${walkerId.substring(0, 5)}`,
            calificacion_promedio: searchData.calificacion_promedio || 0,
            num_servicios_completados: searchData.num_servicios_completados || 0,
            precio_hora: searchData.precio_hora || 0,
            tipos_perro_aceptados: searchData.tipos_perro_aceptados || [],
            anos_experiencia: searchData.anos_experiencia || 0,
            verificacion_estado: searchData.verificacion_estado || "pendiente",
            distancia_km: parseFloat(distance.toFixed(2)),

            // 🆕 Campos denormalizados desde paseadores_search (ya no necesitamos consultar /paseadores/)
            motivacion: searchData.motivacion || "",
            top_resenas: searchData.top_resenas || [],
            zonas_principales: searchData.zonas_principales || [],
            disponibilidad_general: searchData.disponibilidad_general || "No especificada",
            // ❌ ELIMINADO: experiencia_general (redundante con anos_experiencia)
          });
        }
      }
    }

    if (paseadoresForAI.length === 0) {
      return { recommendations: [], message: "No se encontraron paseadores aptos cerca de tu ubicación." };
    }

    // Sort by distance as a primary filter for AI
    paseadoresForAI.sort((a, b) => a.distancia_km - b.distancia_km);

    // OPTIMIZACIÓN: Limitar a máximo 10 candidatos para reducir tokens y costo
    const candidatosParaIA = paseadoresForAI.slice(0, 10);

    console.log(`Enviando ${candidatosParaIA.length} candidatos a Gemini AI (de ${paseadoresForAI.length} totales)`);

    // Construct the prompt for Gemini AI
    const prompt = `Eres un asistente experto en matching de paseadores de perros para la app Walki.

**ESQUEMA DE DATOS:**

Mascota (petData):
- nombre: string
- tipo_mascota: "Pequeños" | "Medianos" | "Grandes" | "Gigantes"
- raza: string (opcional)
- edad_meses: number
- temperamento: string (opcional)

Paseador (candidatos):
- id: string
- nombre: string
- calificacion_promedio: 0-5 (float) - promedio de todas las reseñas
- num_servicios_completados: number - total de paseos realizados
- precio_hora: number (USD)
- anos_experiencia: number - años trabajando con perros
- tipos_perro_aceptados: ["Pequeños", "Medianos", "Grandes"] - DEBE coincidir con tipo_mascota
- distancia_km: number - distancia ya calculada (máx 10km)
- verificacion_estado: "verificado" | "pendiente"
- motivacion: string - por qué es paseador
- top_resenas: [{texto: string, calificacion: number}] - últimas 3 reseñas reales
- zonas_principales: [string] - zonas de cobertura
- disponibilidad_general: string - resumen de horarios

**CRITERIOS DE MATCH (ponderación):**

1. **Compatibilidad de tamaño (40% del score)**:
   - El tipo_mascota DEBE estar en tipos_perro_aceptados
   - Si NO coincide → match_score = 0 (NO recomendar NUNCA)

2. **Reputación (25%)**:
   - calificacion_promedio >= 4.5 es excelente
   - num_servicios_completados >= 20 es confiable
   - Combinar: paseador 5.0★ con 100 servicios > 4.8★ con 10 servicios
   - Leer top_resenas para validar calidad

3. **Distancia (20%)**:
   - < 2 km = excelente (+20 puntos)
   - 2-5 km = bueno (+15 puntos)
   - 5-10 km = aceptable (+10 puntos)

4. **Experiencia (10%)**:
   - anos_experiencia >= 3 es ideal
   - anos_experiencia >= 1 es aceptable

5. **Precio (5%)**:
   - Relación calidad-precio (no solo el más barato)
   - Precio bajo + poca experiencia = red flag
   - Precio alto + alta calificación = justificado

**REGLAS ESTRICTAS:**
- NUNCA recomendar si tipo_mascota NO está en tipos_perro_aceptados
- NUNCA recomendar si match_score < 75
- NUNCA recomendar si verificacion_estado != "verificado"
- MÁXIMO 2 recomendaciones (preferiblemente 1 si es match excelente)
- Si hay empate en score, priorizar menor distancia
- Usa top_resenas para fundamentar la recomendación

**DATOS DEL USUARIO:**
Dueño: ${JSON.stringify(userData, null, 2)}

Mascota: ${JSON.stringify(petData, null, 2)}

**CANDIDATOS (ya filtrados y ordenados por cercanía):**
${JSON.stringify(candidatosParaIA, null, 2)}

**FORMATO DE SALIDA (JSON puro sin markdown):**
[
  {
    "id": "walker_id",
    "nombre": "Nombre",
    "razon_ia": "Una frase concisa explicando el match (máx 100 caracteres)",
    "match_score": 85,
    "tags": ["📍 A 2.5 km", "⭐ 4.9/5 (50 paseos)", "🐕 Acepta ${tipo_mascota}"]
  }
]

**IMPORTANTE:**
- Devuelve SOLO el array JSON, sin texto adicional ni bloques de código markdown
- Si no hay buenos matches (score >= 75), devuelve array vacío: []
- Los tags deben ser MUY concisos (máx 3 palabras cada uno)`;

    console.log("Enviando prompt a Gemini AI...");
    const result = await model.generateContent(prompt);
    const response = result.response;
    const text = response.text();

    console.log("Respuesta de Gemini AI (raw):", text);

    let recommendations = [];
    try {
      // Clean up the text to ensure it's valid JSON
      const cleanText = text.replace(/```json\n|```/g, '').trim();
      recommendations = JSON.parse(cleanText);
    } catch (e) {
      console.error("Error parsing Gemini AI response as JSON:", e);
      console.error("Attempting to extract JSON from raw text...");
      // Fallback for when Gemini might wrap JSON in markdown or add extra text
      const jsonMatch = text.match(/```json\n([\s\S]*?)\n```/);
      if (jsonMatch && jsonMatch[1]) {
        try {
          recommendations = JSON.parse(jsonMatch[1]);
        } catch (e2) {
          console.error("Second attempt to parse JSON failed:", e2);
          throw new functions.https.HttpsError('internal', 'La IA no pudo generar una respuesta válida.');
        }
      } else {
        throw new functions.https.HttpsError('internal', 'La IA no pudo generar una respuesta válida.');
      }
    }

    if (!Array.isArray(recommendations)) {
      console.error("Gemini AI response is not an array:", recommendations);
      throw new functions.https.HttpsError('internal', 'La IA no devolvió un formato de recomendaciones válido.');
    }

    // Ensure match_score is a number
    recommendations.forEach(rec => {
      if (typeof rec.match_score !== 'number') {
        rec.match_score = parseInt(rec.match_score) || 0; // Default to 0 if parsing fails
      }
    });

    console.log("Recomendaciones de Gemini AI:", recommendations);

    return { recommendations: recommendations, message: "Recomendaciones generadas exitosamente." };

  } catch (error) {
    console.error("Error al recomendar paseadores:", error);
    throw new functions.https.HttpsError('internal', 'Error al procesar la recomendación.', error.message);
  }
});


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

    // Calcular años de experiencia desde experiencia_general (MANTENER en /paseadores/)
    let anosExperiencia = 0;
    if (paseadorData.experiencia_general && typeof paseadorData.experiencia_general === 'string') {
      const match = paseadorData.experiencia_general.match(/\d+/);
      if (match) {
        anosExperiencia = parseInt(match[0], 10);
      }
    }

    // 🆕 DENORMALIZACIÓN: Obtener top 3 reseñas recientes
    let topResenas = [];
    try {
      const resenasSnapshot = await db.collection("resenas_paseadores")
        .where("paseadorId", "==", docId) // ✅ CORREGIDO: paseadorId (camelCase)
        .orderBy("timestamp", "desc") // ✅ CORREGIDO: timestamp (no fecha_creacion)
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
        console.log(`✅ ${topResenas.length} reseñas encontradas para ${docId}`);
      }
    } catch (error) {
      console.log(`⚠️ Error obteniendo reseñas para ${docId}: ${error.message}`);
      console.log(`   Posible causa: Falta índice en Firestore para (paseadorId, timestamp)`);
    }

    // 🆕 DENORMALIZACIÓN: Obtener zonas de servicio principales
    let zonasPrincipales = [];
    try {
      const zonasSnapshot = await paseadorRef
        .collection("zonas_servicio")
        .limit(5)
        .get();

      zonasPrincipales = zonasSnapshot.docs.map(doc => {
        const data = doc.data();
        return data.nombre || data.zona || doc.id;
      });
    } catch (error) {
      console.log(`No se pudieron obtener zonas para ${docId}:`, error.message);
    }

    // 🆕 DENORMALIZACIÓN: Disponibilidad general simplificada
    let disponibilidadGeneral = "No especificada";
    let diasDisponibles = [];

    try {
      // 1. Verificar horario_default (estructura principal)
      const disponibilidadDoc = await paseadorRef
        .collection("disponibilidad")
        .doc("horario_default")
        .get();

      if (disponibilidadDoc.exists) {
        const horario = disponibilidadDoc.data();
        const diasSemana = ['lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado', 'domingo'];

        diasDisponibles = diasSemana.filter(dia => {
          const diaData = horario[dia];
          return diaData && diaData.disponible === true;
        });

        if (diasDisponibles.length > 0) {
          // Resumen más detallado para la IA
          const horaEjemplo = horario[diasDisponibles[0]]?.hora_inicio || "";
          disponibilidadGeneral = `${diasDisponibles.length} días/semana desde ${horaEjemplo}`;
          console.log(`✅ Disponibilidad: ${disponibilidadGeneral} (${diasDisponibles.join(", ")})`);
        } else {
          console.log(`ℹ️ horario_default existe pero sin días disponibles`);
        }
      } else {
        console.log(`ℹ️ Sin horario_default para ${docId}`);

        // 2. Fallback: Buscar formato antiguo (documentos con IDs aleatorios)
        const disponibilidadSnapshot = await paseadorRef
          .collection("disponibilidad")
          .where("activo", "==", true)
          .limit(1)
          .get();

        if (!disponibilidadSnapshot.empty) {
          const docAntiguo = disponibilidadSnapshot.docs[0].data();
          if (docAntiguo.dias && Array.isArray(docAntiguo.dias)) {
            disponibilidadGeneral = `${docAntiguo.dias.length} días/semana (formato antiguo)`;
            console.log(`✅ Disponibilidad antigua: ${disponibilidadGeneral}`);
          }
        }
      }
    } catch (error) {
      console.log(`⚠️ Error obteniendo disponibilidad para ${docId}: ${error.message}`);
    }

    // Construir objeto denormalizado para paseadores_search
    const searchData = {
      // Campos básicos (ya existían)
      nombre_display: userData.nombre_display || null,
      nombre_lowercase: (userData.nombre_display || "").toLowerCase(),
      foto_perfil: userData.foto_perfil || null,
      foto_url: userData.foto_perfil || null, // Alias para el adapter Android
      activo: userData.activo || false,
      calificacion_promedio: paseadorData.calificacion_promedio || 0,
      num_servicios_completados: paseadorData.num_servicios_completados || 0,
      precio_hora: paseadorData.precio_hora || 0,
      tipos_perro_aceptados: paseadorData.manejo_perros?.tamanos || [],
      anos_experiencia: anosExperiencia, // Calculado, NO duplicar experiencia_general
      verificacion_estado: paseadorData.verificacion_estado || "pendiente",

      // 🆕 Campos adicionales denormalizados para IA
      motivacion: paseadorData.perfil_profesional?.motivacion || "", // ✅ CORREGIDO: Ruta anidada
      top_resenas: topResenas, // Array de {texto, calificacion}
      zonas_principales: zonasPrincipales, // Array de strings
      disponibilidad_general: disponibilidadGeneral, // String resumido
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

exports.validatePaymentOnCreate = onDocumentCreated("pagos/{pagoId}", async (event) => {
  const { pagoId } = event.params;
  const paymentDoc = event.data;
  if (!paymentDoc || !paymentDoc.exists) {
    console.warn(`validatePaymentOnCreate: documento ${pagoId} no existe o fue eliminado.`);
    return;
  }
  const paymentData = paymentDoc.data();
  const authUid = event.auth?.uid;
  const violations = [];

  if (!paymentData.id_usuario) {
    violations.push("id_usuario ausente");
  }

  if (!paymentData.monto || typeof paymentData.monto !== "number" || paymentData.monto <= 0) {
    violations.push("monto inválido");
  }

  if (!authUid || authUid !== paymentData.id_usuario) {
    // violations.push("auth.uid no coincide");
    console.warn(`validatePaymentOnCreate: Auth check skipped for dev. Auth: ${authUid}, PaymentUser: ${paymentData.id_usuario}`);
  }

  if (violations.length > 0) {
    console.error(`validatePaymentOnCreate: bloqueo de pago ${pagoId} -> ${violations.join(", ")}`);
    await db.collection("audit_logs").add({
      action: "pago_rechazado",
      paymentId: pagoId,
      usuario: paymentData.id_usuario || null,
      reason: violations.join("; "),
      timestamp: new Date(),
    });
    await paymentDoc.ref.delete();
    return;
  }

  await db.collection("audit_logs").add({
    action: "pago_validado",
    paymentId: pagoId,
    usuario: paymentData.id_usuario,
    monto: paymentData.monto,
    timestamp: FieldValue.serverTimestamp(),
  });
});

exports.onPaymentConfirmed = onDocumentUpdated("pagos/{pagoId}", async (event) => {
  const { pagoId } = event.params;
  const newValue = event.data.after.data();
  const oldValue = event.data.before.data();

  // Check if the status changed to 'confirmado'
  if (newValue.estado === "confirmado" && oldValue.estado !== "confirmado") {
    console.log(`Payment ${pagoId} confirmed. Sending notifications.`);

    const idDueno = getIdValue(newValue.id_dueno);
    const idPaseador = getIdValue(newValue.id_paseador);

    // Fetch FCM tokens for owner and walker
    const [duenoDoc, paseadorDoc] = await Promise.all([
      db.collection("usuarios").doc(idDueno).get(),
      db.collection("usuarios").doc(idPaseador).get(),
    ]);

    const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;

    const messages = [];

    // Notification for the owner
    if (duenoToken) {
      messages.push({
        token: duenoToken,
        notification: {
          title: "¡Pago Confirmado!",
          body: "¡Tu pago fue confirmado! El paseo está en curso.",
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_WALKS_ACTIVITY", // Opens PaseosActivity for owner
        },
      });
    } else {
      console.warn(`No FCM token found for owner ${idDueno}`);
    }

    // Notification for the walker
    if (paseadorToken) {
      messages.push({
        token: paseadorToken,
        notification: {
          title: "¡Pago Confirmado!",
          body: "¡Pago confirmado! Puedes iniciar el paseo.",
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_REQUESTS_ACTIVITY", // Opens SolicitudesActivity for walker
        },
      });
    } else {
      console.warn(`No FCM token found for walker ${idPaseador}`);
    }

    if (messages.length > 0) {
      try {
        const response = await admin.messaging().sendEach(messages);
        console.log("Successfully sent messages:", response);
      } catch (error) {
        console.error("Error sending messages:", error);
      }
    } else {
      console.log("No messages to send for payment confirmation.");
    }
  } else {
    console.log(`Payment ${pagoId} updated, but status is not 'confirmado' or did not change.`);
  }
});

exports.onNewReservation = onDocumentCreated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newReservation = event.data.data();

  if (newReservation.estado === "PENDIENTE_ACEPTACION") {
    console.log(`Nueva solicitud de paseo ${reservaId} creada. Enviando notificación al paseador.`);

    const idDueno = newReservation.id_dueno && typeof newReservation.id_dueno === 'object' && newReservation.id_dueno.id ? newReservation.id_dueno.id : newReservation.id_dueno;
    const idPaseador = newReservation.id_paseador && typeof newReservation.id_paseador === 'object' && newReservation.id_paseador.id ? newReservation.id_paseador.id : newReservation.id_paseador;
    const idMascota = newReservation.id_mascota;

    // Helper to format date from Timestamp with Timezone
    const formatDate = (timestamp) => {
        if (!timestamp) return "Fecha desconocida";
        const seconds = timestamp.seconds || (timestamp.toDate && timestamp.toDate().getTime() / 1000);
        if (seconds) {
            const date = new Date(seconds * 1000);
            // Use Ecuador timezone (GMT-5)
            const options = { timeZone: "America/Guayaquil", day: '2-digit', month: '2-digit', year: 'numeric' };
            return date.toLocaleDateString("es-ES", options);
        }
        return timestamp;
    };

    // Helper to format time from Timestamp with Timezone
    const formatTime = (timestamp) => {
        if (!timestamp) return "Hora desconocida";
        const seconds = timestamp.seconds || (timestamp.toDate && timestamp.toDate().getTime() / 1000);
        if (seconds) {
             const date = new Date(seconds * 1000);
             // Use Ecuador timezone (GMT-5)
             const options = { timeZone: "America/Guayaquil", hour: '2-digit', minute: '2-digit', hour12: false };
             return date.toLocaleTimeString("es-ES", options);
        }
        return timestamp;
    };

    const fecha = formatDate(newReservation.fecha);
    const hora = formatTime(newReservation.hora_inicio);

    // Fetch walker's FCM token
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;

    // Fetch owner's name
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const nombreDueno = duenoDoc.exists ? duenoDoc.data().nombre_display : "Dueño";

    // Fetch pet's name
    // Assuming pet info is stored in a subcollection under owner or has its own collection
    let nombreMascota = "mascota";
    if (idMascota) {
        const petDoc = await db.collection("duenos").doc(idDueno).collection("mascotas").doc(idMascota).get();
        if (petDoc.exists) {
            nombreMascota = petDoc.data().nombre || nombreMascota;
        } else {
            // Fallback if pet doc not found, maybe it's in a global 'pets' collection or directly in 'mascotas'
            const globalPetDoc = await db.collection("mascotas").doc(idMascota).get();
            if (globalPetDoc.exists) {
                nombreMascota = globalPetDoc.data().nombre || nombreMascota;
            }
        }
    }


    if (paseadorToken) {
      const message = {
        token: paseadorToken,
        notification: {
          title: "¡Nueva solicitud de paseo!",
          body: `${nombreDueno} ha solicitado un paseo para ${nombreMascota} el ${fecha} a las ${hora}.`,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_REQUESTS_ACTIVITY", // Opens SolicitudesActivity for walker
          reservaId: reservaId, // Pass reservaId to activity
        },
      };

      try {
        await admin.messaging().send(message);
        console.log(`Notification sent to paseador ${idPaseador} for new reservation ${reservaId}`);
      } catch (error) {
        console.error(`Error sending notification to paseador ${idPaseador}:`, error);
      }
    } else {
      console.warn(`No FCM token found for paseador ${idPaseador}.`);
    }
  }
});

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

/**
 * Notifica al destinatario cuando llega un nuevo mensaje.
 * Omite el push si el destinatario está online y con el chat abierto.
 */
exports.sendChatNotification = onDocumentCreated("chats/{chatId}/mensajes/{mensajeId}", async (event) => {
  const { chatId } = event.params;
  const messageSnap = event.data;
  if (!messageSnap || !messageSnap.exists) return null;
  const data = messageSnap.data();
  if (!data) return null;

  const dest = data.id_destinatario;
  const remit = data.id_remitente;
  const texto = data.texto || "";
  if (!dest || !remit) return null;

  // Verificar si el destinatario tiene el chat abierto y online
  const chatDoc = await db.collection("chats").doc(chatId).get();
  if (chatDoc.exists) {
    const estado = chatDoc.get(`estado_usuarios.${dest}`);
    const abierto = chatDoc.get(`chat_abierto.${dest}`);
    if (estado === "online" && abierto === chatId) {
      console.log(`sendChatNotification: ${dest} online en chat ${chatId}, no se envía push.`);
      return null;
    }
  }

  // Obtener datos del destinatario y del remitente en paralelo
  const [userDoc, remitDoc] = await Promise.all([
    db.collection("usuarios").doc(dest).get(),
    db.collection("usuarios").doc(remit).get()
  ]);

  const token = userDoc.exists ? userDoc.get("fcmToken") : null;
  if (!token) {
    console.warn(`sendChatNotification: sin token FCM para ${dest}`);
    return null;
  }

  // Obtener datos del remitente para personalizar la notificación
  const remitName = remitDoc.exists ? (remitDoc.data().nombre_display || "Nuevo mensaje") : "Nuevo mensaje";
  const remitPhoto = remitDoc.exists ? (remitDoc.data().foto_perfil || "") : "";

  const preview = texto.length > 80 ? `${texto.slice(0, 77)}...` : texto;

  const payload = {
    notification: {
      title: remitName,
      body: preview,
    },
    data: {
      chat_id: chatId,
      id_otro_usuario: remit,
      message_id: event.params.mensajeId,
      title: remitName,
      message: preview,
      sender_name: remitName,
      sender_photo_url: remitPhoto,
    },
    android: { 
      priority: "high",
      notification: {
        sound: "default",
        icon: "walki_logo_secundario",
        // color: "#00AAFF" // Opcional: color de acento de tu app
      }
    },
    apns: { 
      payload: { 
        aps: { 
          sound: "default" 
        } 
      } 
    },
  };

  try {
    await admin.messaging().send({
      token,
      ...payload,
    });
    console.log(`sendChatNotification: push enviado a ${dest}`);
  } catch (err) {
    console.error("sendChatNotification: error enviando push", err);
  }
  return null;
});

exports.onReservationAccepted = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();
  const oldValue = event.data.before.data();

  // Check if the status changed from PENDIENTE_ACEPTACION to ACEPTADO
  if (oldValue.estado === "PENDIENTE_ACEPTACION" && newValue.estado === "ACEPTADO") {
    console.log(`Reserva ${reservaId} aceptada. Enviando notificación al dueño.`);

    const idDueno = getIdValue(newValue.id_dueno);
    const idPaseador = getIdValue(newValue.id_paseador);
    const idMascota = newValue.id_mascota;

    if (!idDueno || typeof idDueno !== 'string' || idDueno.trim() === '') {
        console.error(`Error: id_dueno es inválido o nulo para la reserva ${reservaId}.`);
        return;
    }
    if (!idPaseador || typeof idPaseador !== 'string' || idPaseador.trim() === '') {
        console.error(`Error: id_paseador es inválido o nulo para la reserva ${reservaId}.`);
        return;
    }

    // Fetch owner's FCM token
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;

    // Fetch walker's name
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const nombrePaseador = paseadorDoc.exists ? paseadorDoc.data().nombre_display : "Paseador";

    // Fetch pet's name
    let nombreMascota = "mascota";
    if (idMascota) {
        const petDoc = await db.collection("duenos").doc(idDueno).collection("mascotas").doc(idMascota).get();
        if (petDoc.exists) {
            nombreMascota = petDoc.data().nombre || nombreMascota;
        } else {
            const globalPetDoc = await db.collection("mascotas").doc(idMascota).get();
            if (globalPetDoc.exists) {
                nombreMascota = globalPetDoc.data().nombre || nombreMascota;
            }
        }
    }

    if (duenoToken) {
      const message = {
        token: duenoToken,
        notification: {
          title: "¡Paseo aceptado!",
          body: `El paseador ${nombrePaseador} ha aceptado tu solicitud para ${nombreMascota}.`,
          // Force Android to use the app icon (if resource exists as ic_notification or similar, otherwise defaults usually work but 'icon' key helps)
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario' // Try to reference your drawable resource name
            }
        },
        data: {
          click_action: "OPEN_PAYMENT_CONFIRMATION", // Redirects to ConfirmarPagoActivity
          reservaId: reservaId, // Pass reservaId to activity
        },
      };

      try {
        await admin.messaging().send(message);
        console.log(`Notification sent to dueno ${idDueno} for accepted reservation ${reservaId}`);
      } catch (error) {
        console.error(`Error sending notification to dueno ${idDueno}:`, error);
      }
    } else {
      console.warn(`No FCM token found for dueno ${idDueno}.`);
    }
  }
});

exports.onWalkStarted = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();
  const oldValue = event.data.before.data();

  // Check if the status changed to 'EN_CURSO'
  if (oldValue.estado !== "EN_CURSO" && newValue.estado === "EN_CURSO") {
    console.log(`Reserva ${reservaId} ha iniciado. Enviando notificación al dueño.`);

    const idDueno = getIdValue(newValue.id_dueno);
    const idPaseador = getIdValue(newValue.id_paseador);
    const idMascota = newValue.id_mascota;

    // Fetch owner's FCM token
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;

    // Fetch walker's name
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const nombrePaseador = paseadorDoc.exists ? paseadorDoc.data().nombre_display : "Paseador";

    // Fetch pet's name
    let nombreMascota = "mascota";
    if (idMascota) {
        const petDoc = await db.collection("duenos").doc(idDueno).collection("mascotas").doc(idMascota).get();
        if (petDoc.exists) {
            nombreMascota = petDoc.data().nombre || nombreMascota;
        } else {
            const globalPetDoc = await db.collection("mascotas").doc(idMascota).get();
            if (globalPetDoc.exists) {
                nombreMascota = globalPetDoc.data().nombre || nombreMascota;
            }
        }
    }

    if (duenoToken) {
      const message = {
        token: duenoToken,
        notification: {
          title: "¡Tu paseo ha iniciado!",
          body: `El paseo de ${nombreMascota} con ${nombrePaseador} ha comenzado.`,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_CURRENT_WALK_OWNER", // Opens PaseoEnCursoDuenoActivity for owner
          reservaId: reservaId, // Pass reservaId to activity
        },
      };

      try {
        await admin.messaging().send(message);
        console.log(`Notification sent to dueno ${idDueno} for started walk ${reservaId}`);
      } catch (error) {
        console.error(`Error sending notification to dueno ${idDueno}:`, error);
      }
    } else {
      console.warn(`No FCM token found for dueno ${idDueno}.`);
    }
  }
});

exports.onReservationCancelled = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();
  // const oldValue = event.data.before.data(); // Not strictly needed as we only care about new state

  // Check if the status changed to 'CANCELADO'
  if (newValue.estado === "CANCELADO") {
    console.log(`Reserva ${reservaId} cancelada. Enviando notificación al paseador.`);

    const idDueno = getIdValue(newValue.id_dueno);
    const idPaseador = getIdValue(newValue.id_paseador);
    const idMascota = newValue.id_mascota;

    // Fetch walker's FCM token
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;

    // Fetch owner's name
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const nombreDueno = duenoDoc.exists ? duenoDoc.data().nombre_display : "Dueño";

    // Fetch pet's name (same logic as before)
    let nombreMascota = "mascota";
    if (idMascota) {
        const petDoc = await db.collection("duenos").doc(idDueno).collection("mascotas").doc(idMascota).get();
        if (petDoc.exists) {
            nombreMascota = petDoc.data().nombre || nombreMascota;
        } else {
            const globalPetDoc = await db.collection("mascotas").doc(idMascota).get();
            if (globalPetDoc.exists) {
                nombreMascota = globalPetDoc.data().nombre || nombreMascota;
            }
        }
    }

    if (paseadorToken) {
      const message = {
        token: paseadorToken,
        notification: {
          title: "Solicitud cancelada",
          body: `${nombreDueno} ha cancelado la solicitud de paseo para ${nombreMascota}.`,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_REQUESTS_ACTIVITY", // Opens SolicitudesActivity for walker
          reservaId: reservaId, // Pass reservaId to activity
        },
      };

      try {
        await admin.messaging().send(message);
        console.log(`Notification sent to paseador ${idPaseador} for cancelled reservation ${reservaId}`);
      } catch (error) {
        console.error(`Error sending notification to paseador ${idPaseador}:`, error);
      }
    } else {
      console.warn(`No FCM token found for paseador ${idPaseador}.`);
    }
  }
});

exports.onRequestCancellation = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();
  const oldValue = event.data.before.data();

  // Check if status changed to SOLICITUD_CANCELACION
  if (newValue.estado === "SOLICITUD_CANCELACION" && oldValue.estado !== "SOLICITUD_CANCELACION") {
    console.log(`Solicitud de cancelación detectada para reserva ${reservaId}`);

    const idPaseador = getIdValue(newValue.id_paseador);
    const idDueno = getIdValue(newValue.id_dueno);

    // Fetch walker's FCM token
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;
    
    // Fetch owner's name for better context
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const nombreDueno = duenoDoc.exists ? duenoDoc.data().nombre_display : "El dueño";

    if (paseadorToken) {
      const message = {
        token: paseadorToken,
        notification: {
          title: "Solicitud de cancelación",
          body: `${nombreDueno} ha solicitado cancelar el paseo. Por favor revisa y responde.`,
        },
        android: {
            notification: {
                icon: 'walki_logo_secundario'
            }
        },
        data: {
          click_action: "OPEN_CURRENT_WALK_ACTIVITY", // Opens PaseoEnCursoActivity for walker
          reservaId: reservaId,
        },
      };

      try {
        await admin.messaging().send(message);
        console.log(`Notification sent to walker ${idPaseador} for cancellation request.`);
      } catch (error) {
        console.error(`Error sending notification to walker ${idPaseador}:`, error);
      }
    } else {
      console.warn(`No FCM token found for walker ${idPaseador}`);
    }
  }
});

exports.onPaseoUpdate = onDocumentUpdated("reservas/{reservaId}", async (event) => {
  const { reservaId } = event.params;
  const newValue = event.data.after.data();
  const oldValue = event.data.before.data();

  // 1. Check for new photos
  const oldFotos = oldValue.fotos_paseo || [];
  const newFotos = newValue.fotos_paseo || [];
  const hasNewPhoto = newFotos.length > oldFotos.length;

  // 2. Check for updated notes
  const oldNotas = oldValue.notas_paseador || "";
  const newNotas = newValue.notas_paseador || "";
  const hasNewNote = newNotas !== oldNotas && newNotas.length > 0;

  if (!hasNewPhoto && !hasNewNote) {
    return; // No relevant changes
  }

  console.log(`Paseo update detected for ${reservaId}: Photo=${hasNewPhoto}, Note=${hasNewNote}`);

  const idDueno = getIdValue(newValue.id_dueno);
  const idPaseador = getIdValue(newValue.id_paseador);
  const idMascota = newValue.id_mascota;

  // Fetch owner's FCM token
  const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
  const duenoToken = duenoDoc.exists ? duenoDoc.data().fcmToken : null;

  if (!duenoToken) {
    console.warn(`No FCM token found for owner ${idDueno}`);
    return;
  }

  // Fetch walker's name
  const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
  const nombrePaseador = paseadorDoc.exists ? paseadorDoc.data().nombre_display : "El paseador";

  let title = "Actualización del paseo";
  let body = `Hay novedades en el paseo de tu mascota.`;

  if (hasNewPhoto && hasNewNote) {
    title = "¡Nueva foto y nota!";
    body = `${nombrePaseador} ha añadido una foto y una nota al paseo.`;
  } else if (hasNewPhoto) {
    title = "¡Nueva foto del paseo!";
    body = `${nombrePaseador} ha añadido una nueva foto.`;
  } else if (hasNewNote) {
    title = "Nueva nota del paseador";
    body = `${nombrePaseador} dice: "${newNotas}"`;
  }

  const message = {
    token: duenoToken,
    notification: {
      title: title,
      body: body,
    },
    android: {
      notification: {
        icon: 'walki_logo_secundario'
      }
    },
    data: {
      click_action: "OPEN_CURRENT_WALK_OWNER",
      reservaId: reservaId,
    },
  };

  try {
    await admin.messaging().send(message);
    console.log(`Notification sent to owner ${idDueno} for paseo update.`);
  } catch (error) {
    console.error(`Error sending notification to owner ${idDueno}:`, error);
  }
});

exports.checkWalkReminders = onSchedule("every 60 minutes", async (event) => {
  console.log("Running scheduled job to check for walk reminders.");
  const now = admin.firestore.Timestamp.now();
  const oneHourFromNow = admin.firestore.Timestamp.fromMillis(now.toMillis() + (60 * 60 * 1000)); // 1 hour from now

  const reservationsSnapshot = await db.collection("reservas")
    .where("estado", "in", ["ACEPTADO", "CONFIRMADO"])
    .where("reminderSent", "==", false)
    .get();

  const usersToFetchTokens = new Set();
  const petsToFetch = new Map(); // idDueno -> Set<idMascota>

  for (const doc of reservationsSnapshot.docs) {
    const reserva = doc.data();
    const reservaId = doc.id;
    const idDueno = getIdValue(reserva.id_dueno);
    const idPaseador = getIdValue(reserva.id_paseador);
    const idMascota = getIdValue(reserva.id_mascota);
    
    let walkTimestamp;
    if (reserva.hora_inicio && reserva.hora_inicio.toDate) {
        walkTimestamp = reserva.hora_inicio;
    } else if (reserva.fecha && reserva.fecha.toDate) {
        // Fallback to 'fecha' if 'hora_inicio' is missing (though unlikely given app logic)
        walkTimestamp = reserva.fecha;
    } else {
        console.warn(`Reserva ${reservaId} has invalid date/time format.`);
        continue; 
    }

    if (walkTimestamp.toMillis() > now.toMillis() && walkTimestamp.toMillis() <= oneHourFromNow.toMillis()) {
      console.log(`Found upcoming walk ${reservaId} for reminder.`);
      
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
      const nombreMascota = pet ? pet.nombre : "tu mascota";

      // Reminder for Owner
      if (dueno && dueno.fcmToken) {
        messagesToSend.push({
          token: dueno.fcmToken,
          notification: {
            title: "Recordatorio de paseo",
            body: `Tu paseo con ${nombrePaseador} para ${nombreMascota} es en 1 hora.`,
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
            body: `Tienes un paseo programado con ${nombreDueno} para ${nombreMascota} en 1 hora.`,
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
  } else {
    console.log("No reminder messages to send.");
  }

  if (reservationsToUpdate.length > 0) {
    await Promise.all(reservationsToUpdate);
    console.log("Updated reminderSent status for processed reservations.");
  }
});

exports.transitionToInCourse = onSchedule("every 5 minutes", async (event) => {
  console.log("Running scheduled job to transition CONFIRMADO reservations to EN_CURSO.");
  const now = admin.firestore.Timestamp.now();
  
  // Look back up to 24 hours to catch any missed transitions, not just the last 5 minutes.
  // Using Firestore Timestamp for consistent query comparisons
  const twentyFourHoursAgoMillis = now.toMillis() - (24 * 60 * 60 * 1000);
  const twentyFourHoursAgo = admin.firestore.Timestamp.fromMillis(twentyFourHoursAgoMillis);

  try {
    // Query for reservations that match the criteria
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "CONFIRMADO")
      .where("hora_inicio", "<=", now)
      .where("hora_inicio", ">=", twentyFourHoursAgo) // Optimization: Don't fetch ancient history
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
      
      // Double check logic (redundant with query but safe)
      // Also check if it was already processed to avoid overwriting if query was slightly delayed
      if (reserva.estado !== "CONFIRMADO") continue;

      // Add to batch
      currentBatch.update(doc.ref, {
        estado: "EN_CURSO",
        fecha_inicio_paseo: now, // Set start time to execution time
        hasTransitionedToInCourse: true,
        actualizado_por_sistema: true,
        last_updated: now
      });

      operationCounter++;
      processedCount++;

      // Firestore batch limit is 500
      if (operationCounter >= 499) {
        batches.push(currentBatch.commit());
        currentBatch = db.batch();
        operationCounter = 0;
      }
    }

    // Push remaining operations
    if (operationCounter > 0) {
      batches.push(currentBatch.commit());
    }

    await Promise.all(batches);
    console.log(`Successfully transitioned ${processedCount} reservations to EN_CURSO.`);

  } catch (error) {
    console.error("Error transitioning reservations to EN_CURSO:", error);
  }
});

exports.onReservaStatusChange = onDocumentUpdated("reservas/{reservaId}", async (event) => {
    const { reservaId } = event.params;
    const newValue = event.data.after.data();
    const oldValue = event.data.before.data();
    
    if (!newValue || !oldValue) {
        return;
    }

    const oldStatus = oldValue.estado;
    const newStatus = newValue.estado;

    if (oldStatus === newStatus) {
        return; // No change in status
    }

    // Get paseador ID safely (handle Reference or String)
    const paseadorRef = newValue.id_paseador;
    let paseadorId = null;
    if (paseadorRef) {
        if (typeof paseadorRef === 'object' && paseadorRef.id) {
             paseadorId = paseadorRef.id;
        } else if (typeof paseadorRef === 'string') {
             paseadorId = paseadorRef;
        }
    }

    if (!paseadorId) {
        console.warn(`Reserva ${reservaId} no tiene un id_paseador válido.`);
        return;
    }

    // Determine if the walker should be "in walk" based on the new status
    let shouldBeInWalk = false;
    if (newStatus === "EN_CURSO" || newStatus === "EN_PROGRESO") {
        shouldBeInWalk = true;
    } else if (newStatus === "COMPLETADO" || newStatus === "CANCELADO") {
        shouldBeInWalk = false;
    } else {
        // For other statuses (PENDIENTE, etc.), we generally don't change the status 
        // unless we want to be strict. For now, we only act on explicit start/end states.
        return; 
    }

    const userDocRef = db.collection("usuarios").doc(paseadorId);
    
    try {
        const userDoc = await userDocRef.get();
        if (!userDoc.exists) {
            console.warn(`Paseador ${paseadorId} no encontrado para la reserva ${reservaId}.`);
            return;
        }

        const currentEnPaseo = userDoc.data().en_paseo || false;

        // Update only if the status is different
        if (currentEnPaseo !== shouldBeInWalk) {
            console.log(`Actualizando paseador ${paseadorId} en_paseo de ${currentEnPaseo} a ${shouldBeInWalk} para reserva ${reservaId}.`);
            
            const updates = {
                en_paseo: shouldBeInWalk,
                last_en_paseo_update: FieldValue.serverTimestamp()
            };

            // If starting a walk, track which reservation started it
            if (shouldBeInWalk) {
                updates.current_walk_reserva_id = reservaId;
            } else {
                // If ending a walk, only clear current_walk_reserva_id if it matches this reservation
                // This prevents a race condition where a NEW walk started before this one finished processing
                const currentReservaId = userDoc.data().current_walk_reserva_id;
                if (currentReservaId === reservaId) {
                    updates.current_walk_reserva_id = FieldValue.delete();
                }
            }

            await userDocRef.update(updates);
        } else {
             // Edge case: If already in walk, but for a DIFFERENT reservation, and this one starts...
             // We might want to update current_walk_reserva_id? 
             // For now, keep it simple.
             console.log(`Paseador ${paseadorId} ya tiene el estado en_paseo: ${shouldBeInWalk}. No se necesita actualizar.`);
        }

        // ---------------------------------------------------------
        // NUEVA LÓGICA: Incrementar contadores al completar paseo
        // ---------------------------------------------------------
        if (newStatus === "COMPLETADO" && oldStatus !== "COMPLETADO") {
            console.log(`Reserva ${reservaId} completada. Incrementando contadores.`);
            
            // 1. Actualizar Paseador (num_servicios_completados)
            const paseadorProfileRef = db.collection("paseadores").doc(paseadorId);
            await paseadorProfileRef.set({
                num_servicios_completados: FieldValue.increment(1)
            }, { merge: true });

            // 2. Actualizar Dueño (num_paseos_solicitados)
            // Nota: Extraemos el ID del dueño de manera segura
            const duenoRefRaw = newValue.id_dueno;
            let duenoId = null;
            if (duenoRefRaw) {
                if (typeof duenoRefRaw === 'string') duenoId = duenoRefRaw;
                else if (duenoRefRaw.id) duenoId = duenoRefRaw.id;
            }

            if (duenoId) {
                console.log(`Incrementando paseos para dueño ${duenoId}`);
                const duenoProfileRef = db.collection("duenos").doc(duenoId);
                await duenoProfileRef.set({
                    num_paseos_solicitados: FieldValue.increment(1)
                }, { merge: true });
            }
        }

    } catch (error) {
        console.error(`Error actualizando estado de paseador ${paseadorId}:`, error);
    }
});

// --- 🆕 FUNCIÓN DE MIGRACIÓN: Actualizar paseadores_search con campos denormalizados ---
// Ejecutar UNA VEZ después de desplegar para actualizar paseadores existentes
// URL: https://southamerica-east1-{PROJECT_ID}.cloudfunctions.net/migrarPaseadoresSearch
exports.migrarPaseadoresSearch = onRequest(async (req, res) => {
  try {
    console.log("Iniciando migración de paseadores_search con campos denormalizados...");

    // Obtener todos los usuarios con rol PASEADOR
    const usuariosSnapshot = await db.collection("usuarios")
      .where("rol", "==", "PASEADOR")
      .get();

    if (usuariosSnapshot.empty) {
      res.status(200).send({ success: true, message: "No hay paseadores para migrar", total: 0 });
      return;
    }

    const paseadorIds = usuariosSnapshot.docs.map(doc => doc.id);
    console.log(`Encontrados ${paseadorIds.length} paseadores para migrar`);

    let migrados = 0;
    let errores = 0;

    // Procesar en lotes de 10 para no sobrecargar
    for (const paseadorId of paseadorIds) {
      try {
        await sincronizarPaseador(paseadorId);
        migrados++;
        console.log(`✅ Migrado ${paseadorId} (${migrados}/${paseadorIds.length})`);
      } catch (error) {
        errores++;
        console.error(`❌ Error migrando ${paseadorId}:`, error.message);
      }

      // Pausa de 100ms entre cada paseador para no saturar Firestore
      await new Promise(resolve => setTimeout(resolve, 100));
    }

    const resultado = {
      success: true,
      message: "Migración completada",
      total: paseadorIds.length,
      migrados: migrados,
      errores: errores
    };

    console.log("Migración finalizada:", resultado);
    res.status(200).send(resultado);

  } catch (error) {
    console.error("Error en migración:", error);
    res.status(500).send({ success: false, error: error.message });
  }
});

// --- FUNCIÓN DE MANTENIMIENTO (Ejecutar una vez y borrar si se desea) ---
// Esta función recalcula el total de paseos completados para TODOS los paseadores Y DUEÑOS
exports.recalcularContadores = onRequest(async (req, res) => {
    try {
        console.log("Iniciando recálculo masivo de paseos completados...");

        // 1. Obtener todas las reservas completadas
        const reservasSnapshot = await db.collection("reservas")
            .where("estado", "==", "COMPLETADO")
            .get();

        console.log(`Se encontraron ${reservasSnapshot.size} reservas completadas en total.`);

        const paseadorCounts = {};
        const duenoCounts = {};

        // 2. Contar paseos
        reservasSnapshot.forEach(doc => {
            const data = doc.data();

            // -- Paseador --
            const paseadorRef = data.id_paseador;
            let paseadorId = null;
            if (paseadorRef) {
                if (typeof paseadorRef === 'string') paseadorId = paseadorRef;
                else if (paseadorRef.id) paseadorId = paseadorRef.id;
            }
            if (paseadorId) {
                if (!paseadorCounts[paseadorId]) paseadorCounts[paseadorId] = 0;
                paseadorCounts[paseadorId]++;
            }

            // -- Dueño --
            const duenoRef = data.id_dueno;
            let duenoId = null;
            if (duenoRef) {
                if (typeof duenoRef === 'string') duenoId = duenoRef;
                else if (duenoRef.id) duenoId = duenoRef.id;
            }
            if (duenoId) {
                if (!duenoCounts[duenoId]) duenoCounts[duenoId] = 0;
                duenoCounts[duenoId]++;
            }
        });

        // 3. Actualizar (Batch)
        const updates = [];
        let batch = db.batch();
        let counter = 0;

        // Actualizar Paseadores
        for (const [pid, total] of Object.entries(paseadorCounts)) {
            const ref = db.collection("paseadores").doc(pid);
            batch.set(ref, { num_servicios_completados: total }, { merge: true });
            counter++;
            if (counter >= 400) { updates.push(batch.commit()); batch = db.batch(); counter = 0; }
        }

        // Actualizar Dueños
        for (const [did, total] of Object.entries(duenoCounts)) {
            const ref = db.collection("duenos").doc(did);
            batch.set(ref, { num_paseos_solicitados: total }, { merge: true });
            counter++;
            if (counter >= 400) { updates.push(batch.commit()); batch = db.batch(); counter = 0; }
        }

        if (counter > 0) {
            updates.push(batch.commit());
        }

        await Promise.all(updates);

        res.status(200).send({
            message: "Recálculo completado exitosamente.",
            paseadores: paseadorCounts,
            duenos: duenoCounts
        });

    } catch (error) {
        console.error("Error en recálculo:", error);
        res.status(500).send({ error: error.message });
    }
});

// ========================================
// WEBSOCKET SERVER con Socket.IO
// ========================================

const express = require("express");
const { createServer } = require("http");
const { Server } = require("socket.io");
const cors = require("cors");
const { initializeSocketServer } = require("./websocket");

const app = express();
app.use(cors({ origin: "*" }));

// Endpoint de salud
app.get("/health", (req, res) => {
  res.json({ status: "OK", service: "MascotaLink WebSocket Server" });
});

const httpServer = createServer(app);
const io = new Server(httpServer, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"],
  },
  pingTimeout: 60000,
  pingInterval: 25000,
  transports: ["websocket", "polling"],
});

// Inicializar servidor WebSocket
initializeSocketServer(io, db);

// Exportar como Cloud Function
exports.websocket = onRequest((req, res) => {
  httpServer.emit("request", req, res);
});
