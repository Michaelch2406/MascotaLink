const { onDocumentWritten, onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onRequest, HttpsError } = require("firebase-functions/v2/https");
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
  model = genAI.getGenerativeModel({
    model: "gemini-2.5-flash-lite",
    generationConfig: {
      temperature: 0.1,  // Baja temperatura para respuestas más consistentes (0 = determinista, 2 = creativo)
      topP: 0.95,
      topK: 40,
      maxOutputTokens: 2048,
    }
  });
  console.log("✅ Gemini AI initialized successfully with model: gemini-2.5-flash-lite (temperature: 0.1)");
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

// 🆕 MEJORA #6: Cache de recomendaciones en memoria
// Evita llamar a Gemini múltiples veces para el mismo usuario en poco tiempo
const recommendationCache = new Map(); // userId -> { recommendations, timestamp, petId }
const CACHE_TTL = 15 * 60 * 1000; // 🔥 OPTIMIZADO: 15 minutos (antes 5) para ahorrar créditos

/**
 * Limpia entradas expiradas del cache (ejecuta cada 10 minutos)
 */
function cleanExpiredCache() {
  const now = Date.now();
  let cleaned = 0;

  for (const [userId, data] of recommendationCache.entries()) {
    if (now - data.timestamp > CACHE_TTL) {
      recommendationCache.delete(userId);
      cleaned++;
    }
  }

  if (cleaned > 0) {
    console.log(`🧹 Cache limpiado: ${cleaned} entradas expiradas eliminadas`);
  }
}

// Ejecutar limpieza cada 10 minutos
setInterval(cleanExpiredCache, 10 * 60 * 1000);

/**
 * Cloud Function to recommend walkers based on user and pet criteria using Gemini AI.
 * This is a callable function, invoked directly from the Android app.
 */
exports.recomendarPaseadores = onCall(async (request) => {
  console.log(`🔍 DEBUG: Función recomendarPaseadores invocada`);

  // Inicializar Gemini AI si aún no está inicializado
  initializeGemini();
  console.log(`🔍 DEBUG: Gemini inicializado. Model existe: ${model ? 'YES' : 'NO'}`);

  if (!model) {
    console.error("Gemini AI model is not initialized. Check GEMINI_API_KEY.");
    return { error: "Gemini AI no está disponible. Contacta al soporte." };
  }

  const { userData, petData, userLocation } = request.data;
  const userId = request.auth?.uid;
  console.log(`🔍 DEBUG: User ID: ${userId}, Datos recibidos: userData=${!!userData}, petData=${!!petData}, userLocation=${!!userLocation}`);

  if (!userId) {
    throw new HttpsError('unauthenticated', 'El usuario no está autenticado.');
  }

  if (!userData || !petData || !userLocation) {
    throw new HttpsError(
      'invalid-argument',
      'Faltan datos de usuario, mascota o ubicación.'
    );
  }

  // 🆕 MEJORA #3: Validar campos mínimos requeridos
  // Verificar que los objetos tengan los campos críticos para la IA
  const requiredPetFields = {
    nombre: 'Nombre de mascota',
    tamano: 'Tamaño de mascota (Pequeño/Mediano/Grande)'
  };

  const requiredUserFields = {
    nombre_display: 'Nombre del dueño'
  };

  const requiredLocationFields = {
    latitude: 'Latitud de ubicación',
    longitude: 'Longitud de ubicación'
  };

  // Validar petData
  for (const [field, label] of Object.entries(requiredPetFields)) {
    if (!petData[field]) {
      throw new HttpsError(
        'invalid-argument',
        `Falta información de tu mascota: ${label}. Por favor completa el perfil de tu mascota.`
      );
    }
  }

  // Validar userData
  for (const [field, label] of Object.entries(requiredUserFields)) {
    if (!userData[field]) {
      throw new HttpsError(
        'invalid-argument',
        `Falta información de tu perfil: ${label}. Por favor completa tu perfil.`
      );
    }
  }

  // Validar userLocation
  for (const [field, label] of Object.entries(requiredLocationFields)) {
    if (userLocation[field] === undefined || userLocation[field] === null) {
      throw new HttpsError(
        'invalid-argument',
        `Falta ${label}. Por favor activa los permisos de ubicación.`
      );
    }
  }

  console.log(`✅ Validación exitosa - Recomendación para usuario ${userId}, mascota ${petData.nombre} (${petData.tamano})`);
  console.log(`🔍 DEBUG: Iniciando lógica de recomendación...`);

  // 🆕 MEJORA #6: Verificar cache antes de llamar a Gemini
  console.log(`🔍 DEBUG: Verificando cache...`);
  const cacheKey = userId;
  const petId = petData.id || petData.nombre; // Identificador de mascota
  const cached = recommendationCache.get(cacheKey);
  console.log(`🔍 DEBUG: Cache key: ${cacheKey}, Pet ID: ${petId}, Cache hit: ${cached ? 'YES' : 'NO'}`);

  if (cached && (Date.now() - cached.timestamp < CACHE_TTL)) {
    // Verificar que sea para la misma mascota
    if (cached.petId === petId) {
      console.log(`💾 Retornando recomendaciones desde cache (${Math.floor((CACHE_TTL - (Date.now() - cached.timestamp)) / 1000)}s restantes)`);
      return {
        recommendations: cached.recommendations,
        message: "Recomendaciones obtenidas (cache)",
        cached: true
      };
    } else {
      console.log(`🔄 Cache invalidado: cambió la mascota (${cached.petId} → ${petId})`);
      recommendationCache.delete(cacheKey);
    }
  }

  console.log(`🔍 DEBUG: Cache no encontrado, continuando con búsqueda...`);

  const userLat = userLocation.latitude;
  const userLng = userLocation.longitude;
  const radiusKm = 10; // Search within 10 km radius
  console.log(`🔍 DEBUG: Ubicación usuario: ${userLat}, ${userLng} - Radio: ${radiusKm}km`);

  let potentialWalkers = [];

  console.log(`🔍 DEBUG: Iniciando bloque try para búsqueda de paseadores...`);
  try {
    console.log(`🔍 DEBUG: Ejecutando query a paseadores_search...`);
    // 1. Fetch potential walkers from paseadores_search collection
    // This collection is denormalized and contains searchable walker data.
    const searchSnapshot = await db.collection("paseadores_search")
      .where("activo", "==", true)
      // Buscar tanto "APROBADO" como "aprobado" (hay inconsistencia en la BD)
      .get();

    console.log(`🔍 DEBUG: Query completada. Documentos encontrados: ${searchSnapshot.size}`);

    const walkerIds = [];
    const walkerDataMap = {}; // To store full walker data

    for (const doc of searchSnapshot.docs) {
      const walkerSearchData = doc.data();
      const walkerId = doc.id;
      const verificacionEstado = walkerSearchData.verificacion_estado || "";

      // Filtrar solo paseadores verificados/aprobados (case-insensitive)
      const estadoLower = verificacionEstado.toLowerCase();
      if (estadoLower === "aprobado" || estadoLower === "verificado") {
        walkerIds.push(walkerId);
        walkerDataMap[walkerId] = walkerSearchData;
      } else {
        console.log(`  ⏭️ Saltando paseador ${walkerId}: verificacion_estado = "${verificacionEstado}"`);
      }
    }

    console.log(`🔍 DEBUG: Total de IDs de paseadores verificados/aprobados: ${walkerIds.length}`);

    if (walkerIds.length === 0) {
      console.log(`⚠️ No se encontraron paseadores activos y verificados`);
      return { recommendations: [], message: "No se encontraron paseadores activos y verificados." };
    }

    console.log(`🔍 DEBUG: Obteniendo datos completos de ${walkerIds.length} paseadores...`);
    console.log(`🔍 DEBUG: IDs a consultar: ${walkerIds.join(', ')}`);

    // 2. Fetch full paseador profile data and user display name for prompt
    const fullWalkerPromises = walkerIds.map(id => db.collection("paseadores").doc(id).get());
    const userDisplayPromises = walkerIds.map(id => db.collection("usuarios").doc(id).get());

    console.log(`🔍 DEBUG: Ejecutando queries paralelas a paseadores y usuarios...`);

    const [fullWalkerDocs, userDisplayDocs] = await Promise.all([
      Promise.all(fullWalkerPromises),
      Promise.all(userDisplayPromises)
    ]);

    console.log(`🔍 DEBUG: Queries completadas. fullWalkerDocs: ${fullWalkerDocs.length}, userDisplayDocs: ${userDisplayDocs.length}`);

    const paseadoresForAI = [];

    console.log(`🔍 DEBUG: Procesando ${walkerIds.length} paseadores y calculando distancias...`);

    for (let i = 0; i < walkerIds.length; i++) {
      const walkerId = walkerIds[i];
      const searchData = walkerDataMap[walkerId];
      const fullWalkerData = fullWalkerDocs[i].exists ? fullWalkerDocs[i].data() : {};
      const userData = userDisplayDocs[i].exists ? userDisplayDocs[i].data() : {};

      console.log(`  🔍 Procesando paseador ${i + 1}/${walkerIds.length}: ${walkerId}`);

      // Buscar ubicación en varios lugares posibles
      let walkerLocation = null;

      // Intento 1: ubicacion_actual en usuarios (campo principal)
      if (userData.ubicacion_actual) {
        walkerLocation = userData.ubicacion_actual;
        console.log(`    📍 Ubicación encontrada en usuarios.ubicacion_actual`);
      }
      // Intento 2: ubicacion_principal.geopoint en paseadores
      else if (fullWalkerData.ubicacion_principal?.geopoint) {
        walkerLocation = fullWalkerData.ubicacion_principal.geopoint;
        console.log(`    📍 Ubicación encontrada en paseadores.ubicacion_principal.geopoint`);
      }
      // Intento 3: ubicacion directa en usuarios
      else if (userData.ubicacion) {
        walkerLocation = userData.ubicacion;
        console.log(`    📍 Ubicación encontrada en usuarios.ubicacion`);
      }

      if (!walkerLocation || !walkerLocation.latitude || !walkerLocation.longitude) {
        console.log(`    ⚠️ Sin ubicación válida en ningún campo, saltando`);
        continue;
      }

      // Filter by distance if location is available
      const distance = calculateDistance(userLat, userLng, walkerLocation.latitude, walkerLocation.longitude);
      console.log(`    📍 Distancia: ${distance.toFixed(2)}km (radio máx: ${radiusKm}km)`);

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
          console.log(`    ✅ Agregado a lista de candidatos`);
        } else {
          console.log(`    ❌ Fuera de rango (>${radiusKm}km)`);
        }
    }

    console.log(`🔍 DEBUG: Total de paseadores dentro del radio: ${paseadoresForAI.length}`);

    if (paseadoresForAI.length === 0) {
      console.log(`⚠️ No se encontraron paseadores cerca de la ubicación`);
      return { recommendations: [], message: "No se encontraron paseadores aptos cerca de tu ubicación." };
    }

    // 🆕 MEJORA #4: Pre-scoring híbrido (distancia + calificación + experiencia)
    // En lugar de solo ordenar por distancia, calculamos un score que combina múltiples factores
    console.log(`🔍 DEBUG: Calculando pre-score para ${paseadoresForAI.length} candidatos...`);

    paseadoresForAI.forEach(paseador => {
      // Componentes del score (0-100):
      const scoreDistancia = Math.max(0, 100 - (paseador.distancia_km * 10)); // 10km = 0 pts, 0km = 100 pts
      const scoreCalificacion = (paseador.calificacion_promedio / 5) * 100; // 5 estrellas = 100 pts
      const scoreExperiencia = Math.min(100, paseador.anos_experiencia * 20); // 5+ años = 100 pts
      const scoreServicios = Math.min(100, paseador.num_servicios_completados * 2); // 50+ servicios = 100 pts

      // Ponderación: Calificación (35%) + Distancia (30%) + Servicios (20%) + Experiencia (15%)
      paseador.pre_score =
        (scoreCalificacion * 0.35) +
        (scoreDistancia * 0.30) +
        (scoreServicios * 0.20) +
        (scoreExperiencia * 0.15);

      // Bonus: Si tiene reseñas positivas recientes, +5 puntos
      if (paseador.top_resenas && paseador.top_resenas.length > 0) {
        const promedioResenas = paseador.top_resenas.reduce((sum, r) => sum + r.calificacion, 0) / paseador.top_resenas.length;
        if (promedioResenas >= 4.5) {
          paseador.pre_score += 5;
        }
      }
    });

    console.log(`🔍 DEBUG: Pre-scoring completado`);

    // 🆕 MEJORA C: Personalización basada en historial del usuario
    // Consultar reservas anteriores para identificar preferencias
    console.log("🔍 DEBUG: 🎯 Analizando historial de usuario para personalización...");

    try {
      const userId = request.auth.uid;
      console.log(`🔍 DEBUG: Consultando historial de reservas para usuario ${userId}...`);
      const historialSnapshot = await db.collection("reservas")
        .where("dueno_id", "==", userId)
        .where("estado", "==", "completada")
        .orderBy("fecha_creacion", "desc")
        .limit(10) // Últimas 10 reservas completadas
        .get();

      if (!historialSnapshot.empty) {
        // Extraer paseadores con los que tuvo buena experiencia
        const paseadoresPrevios = new Set();
        const caracteristicasPreferidas = {
          experienciaPromedio: 0,
          precioPromedio: 0,
          distanciaPromedio: 0,
          count: 0
        };

        for (const reservaDoc of historialSnapshot.docs) {
          const reserva = reservaDoc.data();
          const paseadorId = reserva.paseador_id;

          // Agregar a la lista de paseadores previos
          paseadoresPrevios.add(paseadorId);

          // Acumular características para calcular promedios
          const paseador = paseadoresForAI.find(p => p.id === paseadorId);
          if (paseador) {
            caracteristicasPreferidas.experienciaPromedio += paseador.anos_experiencia || 0;
            caracteristicasPreferidas.precioPromedio += paseador.precio_hora || 0;
            caracteristicasPreferidas.distanciaPromedio += paseador.distancia_km || 0;
            caracteristicasPreferidas.count++;
          }
        }

        // Calcular promedios
        if (caracteristicasPreferidas.count > 0) {
          caracteristicasPreferidas.experienciaPromedio /= caracteristicasPreferidas.count;
          caracteristicasPreferidas.precioPromedio /= caracteristicasPreferidas.count;
          caracteristicasPreferidas.distanciaPromedio /= caracteristicasPreferidas.count;
        }

        console.log(`  📊 Historial: ${paseadoresPrevios.size} paseadores únicos, ${caracteristicasPreferidas.count} reservas analizadas`);
        console.log(`  📈 Preferencias detectadas: ${caracteristicasPreferidas.experienciaPromedio.toFixed(1)} años exp, $${caracteristicasPreferidas.precioPromedio.toFixed(0)}/h, ${caracteristicasPreferidas.distanciaPromedio.toFixed(1)}km`);

        // Aplicar boosts al pre_score
        paseadoresForAI.forEach(paseador => {
          let personalBoost = 0;

          // 🎯 BOOST 1: Ha usado este paseador antes (+15 puntos)
          if (paseadoresPrevios.has(paseador.id)) {
            personalBoost += 15;
            console.log(`  ✨ Boost historial: +15 pts para ${paseador.nombre} (ya lo usó antes)`);
          }

          // 🎯 BOOST 2: Experiencia similar a sus preferencias (+10 puntos máx)
          if (caracteristicasPreferidas.count > 0) {
            const diffExperiencia = Math.abs(paseador.anos_experiencia - caracteristicasPreferidas.experienciaPromedio);
            if (diffExperiencia <= 2) { // Si está dentro de ±2 años
              const boostExp = Math.max(0, 10 - (diffExperiencia * 3));
              personalBoost += boostExp;
            }

            // 🎯 BOOST 3: Precio similar a sus preferencias (+10 puntos máx)
            const diffPrecio = Math.abs(paseador.precio_hora - caracteristicasPreferidas.precioPromedio);
            const porcPrecio = diffPrecio / caracteristicasPreferidas.precioPromedio;
            if (porcPrecio <= 0.3) { // Si está dentro de ±30%
              const boostPrecio = Math.max(0, 10 - (porcPrecio * 30));
              personalBoost += boostPrecio;
            }

            // 🎯 BOOST 4: Distancia similar a sus preferencias (+5 puntos máx)
            const diffDistancia = Math.abs(paseador.distancia_km - caracteristicasPreferidas.distanciaPromedio);
            if (diffDistancia <= 2) { // Si está dentro de ±2 km
              const boostDist = Math.max(0, 5 - (diffDistancia * 2));
              personalBoost += boostDist;
            }
          }

          // Aplicar boost total
          if (personalBoost > 0) {
            paseador.pre_score += personalBoost;
            paseador.personal_boost = personalBoost; // Para debugging
          }
        });

        // Log de top candidatos después de personalización
        const topPersonalizados = [...paseadoresForAI]
          .filter(p => p.personal_boost)
          .sort((a, b) => b.personal_boost - a.personal_boost)
          .slice(0, 3);

        if (topPersonalizados.length > 0) {
          console.log(`  🏆 Top 3 con mayor boost personal:`);
          topPersonalizados.forEach((p, i) => {
            console.log(`    ${i+1}. ${p.nombre} - Boost: +${p.personal_boost.toFixed(1)} pts`);
          });
        }
      } else {
        console.log("  ℹ️ Usuario nuevo - no hay historial para personalizar");
      }
    } catch (error) {
      console.error("⚠️ Error en personalización (continuando sin ella):", error.message);
      // No fallar la función, solo log del error
    }

    // Ordenar por pre_score descendente (mejores primero)
    paseadoresForAI.sort((a, b) => b.pre_score - a.pre_score);

    // 🔥 OPTIMIZACIÓN: Pre-filtrar por compatibilidad de tamaño ANTES de enviar a Gemini
    const tamanoMascota = petData.tamano; // "Pequeño", "Mediano", "Grande", "Gigante"
    const candidatosCompatibles = paseadoresForAI.filter(paseador => {
      const aceptaTamano = paseador.tipos_perro_aceptados.some(tipo =>
        tipo.toLowerCase().includes(tamanoMascota.toLowerCase().replace('s', '')) // "Grandes" includes "Grande"
      );
      if (!aceptaTamano) {
        console.log(`  🚫 Filtrado: ${paseador.nombre} no acepta perros ${tamanoMascota}`);
      }
      return aceptaTamano;
    });

    console.log(`🔍 DEBUG: Candidatos compatibles con ${tamanoMascota}: ${candidatosCompatibles.length} de ${paseadoresForAI.length}`);

    // Si no hay candidatos compatibles, devolver mensaje específico
    if (candidatosCompatibles.length === 0) {
      console.log(`⚠️ No hay paseadores que acepten perros ${tamanoMascota}`);
      return {
        recommendations: [],
        message: `No encontramos paseadores que acepten perros de tamaño ${tamanoMascota} en tu área. Intenta expandir tu búsqueda.`
      };
    }

    // 🔥 OPTIMIZACIÓN: Limitar a máximo 8 candidatos (reducido para ahorrar tokens/créditos)
    const candidatosParaIA = candidatosCompatibles.slice(0, 8);

    console.log(`🔍 DEBUG: ✅ Top 3 candidatos por pre-score:`);
    candidatosParaIA.slice(0, 3).forEach((p, i) => {
      console.log(`  ${i+1}. ${p.nombre} - Score: ${p.pre_score.toFixed(1)} (${p.calificacion_promedio}⭐, ${p.distancia_km}km, ${p.anos_experiencia}años exp)`);
    });
    console.log(`🔍 DEBUG: Enviando ${candidatosParaIA.length} candidatos a Gemini AI (de ${paseadoresForAI.length} totales)`);

    // 🔥 OPTIMIZACIÓN: Skip Gemini en casos obvios para ahorrar créditos
    // Caso 1: Solo hay 1 candidato con score decente (>= 60)
    if (candidatosParaIA.length === 1 && candidatosParaIA[0].pre_score >= 60) {
      const candidato = candidatosParaIA[0];
      console.log(`⚡ SKIP GEMINI: Solo 1 candidato con buen score (${candidato.pre_score.toFixed(1)}) - devolviéndolo directo`);

      const directRecommendation = {
        id: candidato.id,
        nombre: candidato.nombre,
        razon_ia: "Mejor opción disponible en tu área",
        match_score: Math.round(candidato.pre_score),
        tags: [
          `📍 ${candidato.distancia_km}km`,
          `⭐ ${candidato.calificacion_promedio.toFixed(1)}/5`,
          `🐕 ${candidato.num_servicios_completados} paseos`
        ]
      };

      // Guardar en cache
      recommendationCache.set(cacheKey, {
        recommendations: [directRecommendation],
        timestamp: Date.now(),
        petId: petId
      });

      return {
        recommendations: [directRecommendation],
        message: "Recomendación generada exitosamente."
      };
    }

    // Caso 2: Todos los candidatos tienen score muy bajo (< 40)
    if (candidatosParaIA.every(p => p.pre_score < 40)) {
      console.log(`⚡ SKIP GEMINI: Todos los candidatos tienen score < 40 - usando solo fallback`);

      const mejorCandidato = candidatosParaIA[0];
      const fallbackRecommendation = {
        id: mejorCandidato.id,
        nombre: mejorCandidato.nombre,
        razon_ia: "Mejor opción disponible (match limitado)",
        match_score: Math.round(mejorCandidato.pre_score),
        tags: [
          `📍 ${mejorCandidato.distancia_km}km`,
          `⭐ ${mejorCandidato.calificacion_promedio.toFixed(1)}/5`,
          `🐕 ${mejorCandidato.num_servicios_completados} paseos`
        ],
        is_fallback: true
      };

      // Guardar en cache
      recommendationCache.set(cacheKey, {
        recommendations: [fallbackRecommendation],
        timestamp: Date.now(),
        petId: petId
      });

      return {
        recommendations: [fallbackRecommendation],
        message: "Mostrando la mejor opción disponible (match limitado)"
      };
    }

    // Construct the prompt for Gemini AI
    console.log(`🔍 DEBUG: Construyendo prompt para Gemini AI...`);
    const prompt = `Eres un asistente experto en matching de paseadores de perros para la app Walki.

**ESQUEMA DE DATOS:**

Mascota (petData):
- nombre: string
- tamano: "Pequeños" | "Medianos" | "Grandes" | "Gigantes"
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
- tipos_perro_aceptados: ["Pequeños", "Medianos", "Grandes"] - DEBE coincidir con tamano
- distancia_km: number - distancia ya calculada (máx 10km)
- verificacion_estado: "verificado" | "pendiente"
- motivacion: string - por qué es paseador
- top_resenas: [{texto: string, calificacion: number}] - últimas 3 reseñas reales
- zonas_principales: [string] - zonas de cobertura
- disponibilidad_general: string - resumen de horarios

**CRITERIOS DE MATCH (ponderación):**

1. **Compatibilidad de tamaño (40% del score)**:
   - El tamano DEBE estar en tipos_perro_aceptados
   - Si NO coincide → match_score = 0 (NO recomendar NUNCA)

2. **Reputación (25%)**:
   - calificacion_promedio >= 4.5 es excelente
   - num_servicios_completados >= 10 es confiable
   - Combinar: paseador 5.0★ con 20 servicios > 4.8★ con 5 servicios
   - Leer top_resenas para validar calidad real

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
- NUNCA recomendar si tamano NO está en tipos_perro_aceptados
- NUNCA recomendar si match_score < 50
- NUNCA recomendar si verificacion_estado != "verificado" y != "APROBADO"
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
    "tags": ["📍 A 2.5 km", "⭐ 4.9/5 (50 paseos)", "🐕 Acepta ${petData.tamano}"]
  }
]

**IMPORTANTE:**
- Devuelve SOLO el array JSON, sin texto adicional ni bloques de código markdown
- Si no hay buenos matches (score >= 50), devuelve array vacío: []
- Los tags deben ser MUY concisos (máx 3 palabras cada uno)`;

    console.log(`🔍 DEBUG: ⚡ Enviando prompt a Gemini AI (longitud: ${prompt.length} caracteres)...`);
    const result = await model.generateContent(prompt);
    console.log(`🔍 DEBUG: ✅ Respuesta de Gemini recibida`);

    const response = result.response;
    const text = response.text();

    console.log(`🔍 DEBUG: Respuesta de Gemini AI (raw, primeros 500 chars): ${text.substring(0, 500)}`);

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
          throw new HttpsError('internal', 'La IA no pudo generar una respuesta válida.');
        }
      } else {
        throw new HttpsError('internal', 'La IA no pudo generar una respuesta válida.');
      }
    }

    if (!Array.isArray(recommendations)) {
      console.error("Gemini AI response is not an array:", recommendations);
      throw new HttpsError('internal', 'La IA no devolvió un formato de recomendaciones válido.');
    }

    // Ensure match_score is a number
    recommendations.forEach(rec => {
      if (typeof rec.match_score !== 'number') {
        rec.match_score = parseInt(rec.match_score) || 0; // Default to 0 if parsing fails
      }
    });

    console.log(`🔍 DEBUG: Recomendaciones parseadas de Gemini AI:`, JSON.stringify(recommendations, null, 2));

    // 🆕 OPTIMIZACIÓN: Si Gemini no devuelve matches, usar fallback con el mejor candidato
    if (!recommendations || recommendations.length === 0) {
      console.log(`⚠️ Gemini no devolvió matches. Activando fallback con mejor candidato disponible...`);

      // Tomar el mejor candidato por pre_score
      const mejorCandidato = candidatosParaIA[0]; // Ya está ordenado por pre_score descendente

      if (mejorCandidato) {
        // Crear recomendación fallback
        const fallbackRecommendation = {
          id: mejorCandidato.id,
          nombre: mejorCandidato.nombre,
          razon_ia: "Mejor opción disponible en tu área",
          match_score: Math.round(mejorCandidato.pre_score), // Usar el pre_score como match_score
          tags: [
            `📍 ${mejorCandidato.distancia_km}km`,
            `⭐ ${mejorCandidato.calificacion_promedio.toFixed(1)}/5`,
            `🐕 ${mejorCandidato.num_servicios_completados} paseos`
          ],
          is_fallback: true // Marcar como fallback
        };

        recommendations = [fallbackRecommendation];
        console.log(`✅ Fallback activado: ${mejorCandidato.nombre} (score: ${mejorCandidato.pre_score.toFixed(1)})`);
      } else {
        console.log(`⚠️ No hay candidatos disponibles para fallback`);
        return { recommendations: [], message: "No se encontraron paseadores aptos cerca de tu ubicación." };
      }
    }

    // 🆕 MEJORA #6: Guardar en cache antes de retornar
    recommendationCache.set(cacheKey, {
      recommendations: recommendations,
      timestamp: Date.now(),
      petId: petId
    });
    console.log(`🔍 DEBUG: 💾 Recomendaciones guardadas en cache (válido por ${CACHE_TTL / 1000}s)`);

    console.log(`🔍 DEBUG: ✅✅✅ FUNCIÓN COMPLETADA EXITOSAMENTE - Retornando ${recommendations.length} recomendaciones`);
    return {
      recommendations: recommendations,
      message: recommendations[0]?.is_fallback
        ? "Mostrando la mejor opción disponible en tu área"
        : "Recomendaciones generadas exitosamente."
    };

  } catch (error) {
    console.error("🔴 ERROR CAPTURADO en bloque try-catch:", error);
    console.error("🔴 Stack trace:", error.stack);
    console.error("🔴 Tipo de error:", error.constructor.name);
    console.error("🔴 Mensaje:", error.message);

    // 🆕 MEJORA #7: Mensajes de error específicos según el tipo de error
    let userMessage = 'Error al procesar la recomendación.';
    let errorCode = 'internal';

    // Detectar tipo de error y dar mensaje específico
    if (error.message?.includes('API key') || error.message?.includes('GEMINI_API_KEY')) {
      userMessage = 'El servicio de recomendaciones IA no está disponible temporalmente. Intenta más tarde.';
      errorCode = 'unavailable';
    } else if (error.message?.includes('quota') || error.message?.includes('limit')) {
      userMessage = 'Hemos alcanzado el límite de recomendaciones por hoy. Intenta mañana o usa la búsqueda manual.';
      errorCode = 'resource-exhausted';
    } else if (error.message?.includes('parse') || error.message?.includes('JSON')) {
      userMessage = 'La IA devolvió una respuesta inválida. Por favor intenta de nuevo.';
      errorCode = 'internal';
    } else if (error.message?.includes('index') || error.message?.includes('Index')) {
      userMessage = 'La base de datos necesita configuración adicional. Contacta al soporte.';
      errorCode = 'failed-precondition';
    } else if (error.message?.includes('permission') || error.message?.includes('denied')) {
      userMessage = 'No tienes permiso para acceder a este servicio. Verifica tu cuenta.';
      errorCode = 'permission-denied';
    } else if (error.message?.includes('network') || error.message?.includes('timeout')) {
      userMessage = 'Problema de conexión. Verifica tu internet e intenta nuevamente.';
      errorCode = 'unavailable';
    }

    throw new HttpsError(errorCode, userMessage, error.message);
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
    const esGrupo = newReservation.es_grupo;
    const grupoReservaId = newReservation.grupo_reserva_id;

    // Si es parte de un grupo, esperar 3 segundos para que se creen todas las reservas
    if (esGrupo && grupoReservaId) {
      console.log(`Reserva ${reservaId} es parte del grupo ${grupoReservaId}. Esperando agrupación...`);
      await new Promise(resolve => setTimeout(resolve, 3000));

      // Verificar si ya se envió notificación para este grupo
      const grupoSnapshot = await db.collection("reservas")
        .where("grupo_reserva_id", "==", grupoReservaId)
        .where("estado", "==", "PENDIENTE_ACEPTACION")
        .get();

      // Ordenar por fecha de creación y obtener la primera
      const reservasGrupo = grupoSnapshot.docs.sort((a, b) => {
        const aTime = a.data().fecha_creacion?.toMillis() || 0;
        const bTime = b.data().fecha_creacion?.toMillis() || 0;
        return aTime - bTime;
      });

      // Solo enviar notificación desde la PRIMERA reserva del grupo
      if (reservasGrupo.length > 0 && reservasGrupo[0].id !== reservaId) {
        console.log(`Notificación ya enviada por otra reserva del grupo ${grupoReservaId}. Saltando.`);
        return;
      }

      console.log(`Esta es la primera reserva del grupo ${grupoReservaId}. Enviando notificación agrupada.`);
    } else {
      console.log(`Nueva solicitud de paseo ${reservaId} creada. Enviando notificación al paseador.`);
    }

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

    // Fetch walker's FCM token
    const paseadorDoc = await db.collection("usuarios").doc(idPaseador).get();
    const paseadorToken = paseadorDoc.exists ? paseadorDoc.data().fcmToken : null;

    // Fetch owner's name
    const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
    const nombreDueno = duenoDoc.exists ? duenoDoc.data().nombre_display : "Dueño";

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

    // Construir mensaje según si es grupo o individual
    let notificationBody;
    if (esGrupo && grupoReservaId) {
      // Para grupos, obtener información de fechas
      const grupoSnapshot = await db.collection("reservas")
        .where("grupo_reserva_id", "==", grupoReservaId)
        .get();

      const cantidadDias = grupoSnapshot.size;
      const fechas = grupoSnapshot.docs.map(doc => doc.data().fecha).sort((a, b) => {
        const aTime = a?.toMillis() || 0;
        const bTime = b?.toMillis() || 0;
        return aTime - bTime;
      });

      const fechaInicio = formatDate(fechas[0]);
      const fechaFin = formatDate(fechas[fechas.length - 1]);
      const hora = formatTime(newReservation.hora_inicio);

      if (cantidadDias > 1) {
        notificationBody = `${nombreDueno} ha solicitado un paseo para ${nombreMascota} - ${cantidadDias} días (${fechaInicio} - ${fechaFin}) a las ${hora}.`;
      } else {
        notificationBody = `${nombreDueno} ha solicitado un paseo para ${nombreMascota} el ${fechaInicio} a las ${hora}.`;
      }
    } else {
      // Reserva individual
      const fecha = formatDate(newReservation.fecha);
      const hora = formatTime(newReservation.hora_inicio);
      notificationBody = `${nombreDueno} ha solicitado un paseo para ${nombreMascota} el ${fecha} a las ${hora}.`;
    }

    if (paseadorToken) {
      const message = {
        token: paseadorToken,
        notification: {
          title: "¡Nueva solicitud de paseo!",
          body: notificationBody,
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
    const esGrupo = newValue.es_grupo;
    const grupoReservaId = newValue.grupo_reserva_id;

    // Si es parte de un grupo, evitar notificaciones duplicadas
    if (esGrupo && grupoReservaId) {
      console.log(`Reserva ${reservaId} del grupo ${grupoReservaId} aceptada. Verificando si es la primera...`);
      await new Promise(resolve => setTimeout(resolve, 2000));

      // Verificar si ya se envió notificación para este grupo
      const grupoSnapshot = await db.collection("reservas")
        .where("grupo_reserva_id", "==", grupoReservaId)
        .where("estado", "==", "ACEPTADO")
        .get();

      const reservasGrupo = grupoSnapshot.docs.sort((a, b) => {
        const aTime = a.data().fecha_respuesta?.toMillis() || 0;
        const bTime = b.data().fecha_respuesta?.toMillis() || 0;
        return aTime - bTime;
      });

      // Solo enviar desde la primera reserva aceptada del grupo
      if (reservasGrupo.length > 0 && reservasGrupo[0].id !== reservaId) {
        console.log(`Notificación de aceptación ya enviada por otra reserva del grupo ${grupoReservaId}. Saltando.`);
        return;
      }

      console.log(`Primera reserva del grupo ${grupoReservaId} aceptada. Enviando notificación agrupada.`);
    } else {
      console.log(`Reserva ${reservaId} aceptada. Enviando notificación al dueño.`);
    }

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

    // Construir mensaje según si es grupo o individual
    let notificationBody;
    if (esGrupo && grupoReservaId) {
      const grupoSnapshot = await db.collection("reservas")
        .where("grupo_reserva_id", "==", grupoReservaId)
        .get();

      const cantidadDias = grupoSnapshot.size;
      if (cantidadDias > 1) {
        notificationBody = `El paseador ${nombrePaseador} ha aceptado tu solicitud para ${nombreMascota} - ${cantidadDias} días.`;
      } else {
        notificationBody = `El paseador ${nombrePaseador} ha aceptado tu solicitud para ${nombreMascota}.`;
      }
    } else {
      notificationBody = `El paseador ${nombrePaseador} ha aceptado tu solicitud para ${nombreMascota}.`;
    }

    if (duenoToken) {
      const message = {
        token: duenoToken,
        notification: {
          title: "¡Paseo aceptado!",
          body: notificationBody,
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
  console.log("Running scheduled job to transition CONFIRMADO reservations to LISTO_PARA_INICIAR.");
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

      // Add to batch - transition to LISTO_PARA_INICIAR (ready to start, waiting for walker)
      currentBatch.update(doc.ref, {
        estado: "LISTO_PARA_INICIAR",
        hasTransitionedToReady: true,
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
    console.log(`Successfully transitioned ${processedCount} reservations to LISTO_PARA_INICIAR.`);

  } catch (error) {
    console.error("Error transitioning reservations to LISTO_PARA_INICIAR:", error);
  }
});

// Scheduled job to send reminders 15 minutes before walk start time
exports.sendReminder15MinBefore = onSchedule("every 5 minutes", async (event) => {
  console.log("Running scheduled job to send 15-minute reminders.");
  const now = admin.firestore.Timestamp.now();

  // Calculate 15 minutes from now
  const fifteenMinutesLaterMillis = now.toMillis() + (15 * 60 * 1000);
  const fifteenMinutesLater = admin.firestore.Timestamp.fromMillis(fifteenMinutesLaterMillis);

  // Look for walks starting in 12-18 minutes (5 min window to catch them)
  const windowStartMillis = now.toMillis() + (12 * 60 * 1000);
  const windowEndMillis = now.toMillis() + (18 * 60 * 1000);
  const windowStart = admin.firestore.Timestamp.fromMillis(windowStartMillis);
  const windowEnd = admin.firestore.Timestamp.fromMillis(windowEndMillis);

  try {
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "CONFIRMADO")
      .where("hora_inicio", ">=", windowStart)
      .where("hora_inicio", "<=", windowEnd)
      .get();

    if (reservationsSnapshot.empty) {
      console.log("No reservations found for 15-minute reminders.");
      return;
    }

    console.log(`Found ${reservationsSnapshot.size} reservations for 15-minute reminders.`);

    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();

      // Skip if already sent
      if (reserva.reminder15MinSent) continue;

      // Si es parte de un grupo, solo enviar recordatorio para el PRIMER día del grupo
      const esGrupo = reserva.es_grupo;
      const grupoReservaId = reserva.grupo_reserva_id;
      let cantidadDias = 1;

      if (esGrupo && grupoReservaId) {
        // Obtener todas las reservas del grupo
        const grupoSnapshot = await db.collection("reservas")
          .where("grupo_reserva_id", "==", grupoReservaId)
          .get();

        cantidadDias = grupoSnapshot.size;

        // Encontrar el primer día (fecha más temprana)
        const fechas = grupoSnapshot.docs.map(d => ({
          id: d.id,
          fecha: d.data().fecha
        })).sort((a, b) => {
          const aTime = a.fecha?.toMillis() || 0;
          const bTime = b.fecha?.toMillis() || 0;
          return aTime - bTime;
        });

        // Si esta NO es la reserva del primer día, saltar
        if (fechas.length > 0 && fechas[0].id !== doc.id) {
          console.log(`Saltando recordatorio para ${doc.id} - no es el primer día del grupo ${grupoReservaId}`);
          await doc.ref.update({ reminder15MinSent: true }); // Marcar como enviado para no procesarlo de nuevo
          continue;
        }

        console.log(`Enviando recordatorio para primer día del grupo ${grupoReservaId} (${cantidadDias} días)`);
      }

      // Get paseador ID
      const paseadorRef = reserva.id_paseador;
      let paseadorId = null;
      if (paseadorRef) {
        if (typeof paseadorRef === 'object' && paseadorRef.id) {
          paseadorId = paseadorRef.id;
        } else if (typeof paseadorRef === 'string') {
          paseadorId = paseadorRef;
        }
      }

      if (!paseadorId) continue;

      // Get paseador's FCM token
      const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
      if (!paseadorDoc.exists) continue;

      const fcmToken = paseadorDoc.data().fcmToken;
      if (!fcmToken) continue;

      // Format time
      const horaInicio = reserva.hora_inicio.toDate();
      const horaFormateada = horaInicio.toLocaleTimeString('es-ES', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      });

      // Construir mensaje según si es grupo
      let notificationBody;
      if (cantidadDias > 1) {
        notificationBody = `Tu paseo comienza en 15 minutos (${horaFormateada}). Primer día de ${cantidadDias} días. Prepárate para salir.`;
      } else {
        notificationBody = `Tu paseo comienza en 15 minutos (${horaFormateada}). Prepárate para salir.`;
      }

      // Send notification
      const message = {
        token: fcmToken,
        notification: {
          title: "Recordatorio de Paseo",
          body: notificationBody
        },
        data: {
          tipo: "recordatorio_paseo",
          reservaId: doc.id,
          click_action: "OPEN_CURRENT_WALK_ACTIVITY"
        },
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channelId: "paseos_channel"
          }
        }
      };

      try {
        await admin.messaging().send(message);
        console.log(`Sent 15-minute reminder for reservation ${doc.id}`);

        // Mark as sent
        await doc.ref.update({ reminder15MinSent: true });
      } catch (error) {
        console.error(`Error sending 15-minute reminder for ${doc.id}:`, error);
      }
    }

    console.log("Completed 15-minute reminder job.");
  } catch (error) {
    console.error("Error in 15-minute reminder job:", error);
  }
});

// Scheduled job to send reminders 5 minutes before walk start time
exports.sendReminder5MinBefore = onSchedule("every 1 minutes", async (event) => {
  console.log("Running scheduled job to send 5-minute reminders.");
  const now = admin.firestore.Timestamp.now();

  // Look for walks starting in 4-6 minutes (2 min window)
  const windowStartMillis = now.toMillis() + (4 * 60 * 1000);
  const windowEndMillis = now.toMillis() + (6 * 60 * 1000);
  const windowStart = admin.firestore.Timestamp.fromMillis(windowStartMillis);
  const windowEnd = admin.firestore.Timestamp.fromMillis(windowEndMillis);

  try {
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "CONFIRMADO")
      .where("hora_inicio", ">=", windowStart)
      .where("hora_inicio", "<=", windowEnd)
      .get();

    if (reservationsSnapshot.empty) {
      console.log("No reservations found for 5-minute reminders.");
      return;
    }

    console.log(`Found ${reservationsSnapshot.size} reservations for 5-minute reminders.`);

    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();

      // Skip if already sent
      if (reserva.reminder5MinSent) continue;

      // Si es parte de un grupo, solo enviar recordatorio para el PRIMER día del grupo
      const esGrupo = reserva.es_grupo;
      const grupoReservaId = reserva.grupo_reserva_id;
      let cantidadDias = 1;

      if (esGrupo && grupoReservaId) {
        const grupoSnapshot = await db.collection("reservas")
          .where("grupo_reserva_id", "==", grupoReservaId)
          .get();

        cantidadDias = grupoSnapshot.size;

        const fechas = grupoSnapshot.docs.map(d => ({
          id: d.id,
          fecha: d.data().fecha
        })).sort((a, b) => {
          const aTime = a.fecha?.toMillis() || 0;
          const bTime = b.fecha?.toMillis() || 0;
          return aTime - bTime;
        });

        if (fechas.length > 0 && fechas[0].id !== doc.id) {
          console.log(`Saltando recordatorio 5min para ${doc.id} - no es el primer día del grupo ${grupoReservaId}`);
          await doc.ref.update({ reminder5MinSent: true });
          continue;
        }

        console.log(`Enviando recordatorio 5min para primer día del grupo ${grupoReservaId} (${cantidadDias} días)`);
      }

      // Get paseador ID
      const paseadorRef = reserva.id_paseador;
      let paseadorId = null;
      if (paseadorRef) {
        if (typeof paseadorRef === 'object' && paseadorRef.id) {
          paseadorId = paseadorRef.id;
        } else if (typeof paseadorRef === 'string') {
          paseadorId = paseadorRef;
        }
      }

      if (!paseadorId) continue;

      // Get paseador's FCM token
      const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
      if (!paseadorDoc.exists) continue;

      const fcmToken = paseadorDoc.data().fcmToken;
      if (!fcmToken) continue;

      // Format time
      const horaInicio = reserva.hora_inicio.toDate();
      const horaFormateada = horaInicio.toLocaleTimeString('es-ES', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      });

      // Construir mensaje según si es grupo
      let notificationBody;
      if (cantidadDias > 1) {
        notificationBody = `Tu paseo comienza en 5 minutos (${horaFormateada}). Primer día de ${cantidadDias} días. Es hora de prepararte.`;
      } else {
        notificationBody = `Tu paseo comienza en 5 minutos (${horaFormateada}). Es hora de prepararte.`;
      }

      // Send notification
      const message = {
        token: fcmToken,
        notification: {
          title: "¡Paseo Próximo!",
          body: notificationBody
        },
        data: {
          tipo: "recordatorio_paseo",
          reservaId: doc.id,
          click_action: "OPEN_CURRENT_WALK_ACTIVITY"
        },
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channelId: "paseos_channel"
          }
        }
      };

      try {
        await admin.messaging().send(message);
        console.log(`Sent 5-minute reminder for reservation ${doc.id}`);

        // Mark as sent
        await doc.ref.update({ reminder5MinSent: true });
      } catch (error) {
        console.error(`Error sending 5-minute reminder for ${doc.id}:`, error);
      }
    }

    console.log("Completed 5-minute reminder job.");
  } catch (error) {
    console.error("Error in 5-minute reminder job:", error);
  }
});

// Scheduled job to notify when scheduled time has passed for LISTO_PARA_INICIAR walks
exports.notifyOverdueWalks = onSchedule("every 5 minutes", async (event) => {
  console.log("Running scheduled job to notify overdue walks.");
  const now = admin.firestore.Timestamp.now();

  // Look for walks in LISTO_PARA_INICIAR state where scheduled time has passed
  const fiveMinutesAgoMillis = now.toMillis() - (5 * 60 * 1000);
  const fiveMinutesAgo = admin.firestore.Timestamp.fromMillis(fiveMinutesAgoMillis);

  try {
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "LISTO_PARA_INICIAR")
      .where("hora_inicio", "<=", fiveMinutesAgo)
      .get();

    if (reservationsSnapshot.empty) {
      console.log("No overdue LISTO_PARA_INICIAR walks found.");
      return;
    }

    console.log(`Found ${reservationsSnapshot.size} overdue walks.`);

    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();

      // Skip if already notified about being overdue
      if (reserva.overdueNotificationSent) continue;

      // Get paseador ID
      const paseadorRef = reserva.id_paseador;
      let paseadorId = null;
      if (paseadorRef) {
        if (typeof paseadorRef === 'object' && paseadorRef.id) {
          paseadorId = paseadorRef.id;
        } else if (typeof paseadorRef === 'string') {
          paseadorId = paseadorRef;
        }
      }

      if (!paseadorId) continue;

      // Get paseador's FCM token
      const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
      if (!paseadorDoc.exists) continue;

      const fcmToken = paseadorDoc.data().fcmToken;
      if (!fcmToken) continue;

      // Format time
      const horaInicio = reserva.hora_inicio.toDate();
      const horaFormateada = horaInicio.toLocaleTimeString('es-ES', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      });

      // Send notification
      const message = {
        token: fcmToken,
        notification: {
          title: "Paseo Programado Pendiente",
          body: `El paseo programado para las ${horaFormateada} aún no ha comenzado. Toca para iniciar.`
        },
        data: {
          tipo: "paseo_retrasado",
          reservaId: doc.id,
          click_action: "OPEN_CURRENT_WALK_ACTIVITY"
        },
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channelId: "paseos_channel"
          }
        }
      };

      try {
        await admin.messaging().send(message);
        console.log(`Sent overdue notification for reservation ${doc.id}`);

        // Mark as sent
        await doc.ref.update({ overdueNotificationSent: true });
      } catch (error) {
        console.error(`Error sending overdue notification for ${doc.id}:`, error);
      }
    }

    console.log("Completed overdue walks notification job.");
  } catch (error) {
    console.error("Error in overdue walks notification job:", error);
  }
});

// Notifica al paseador cuando llega la ventana de 15 minutos antes del paseo
exports.notifyWalkReadyWindow = onSchedule("every 5 minutes", async (event) => {
  console.log("Running scheduled job to notify walkers when ready window starts.");
  const now = admin.firestore.Timestamp.now();

  // Ventana de 15 minutos antes de la hora programada
  const fifteenMinutesLaterMillis = now.toMillis() + (15 * 60 * 1000);
  const windowStartMillis = fifteenMinutesLaterMillis - (2.5 * 60 * 1000);
  const windowEndMillis = fifteenMinutesLaterMillis + (2.5 * 60 * 1000);
  const windowStart = admin.firestore.Timestamp.fromMillis(windowStartMillis);
  const windowEnd = admin.firestore.Timestamp.fromMillis(windowEndMillis);

  try {
    const reservationsSnapshot = await db.collection("reservas")
      .where("estado", "==", "LISTO_PARA_INICIAR")
      .where("hora_inicio", ">=", windowStart)
      .where("hora_inicio", "<=", windowEnd)
      .get();

    if (reservationsSnapshot.empty) {
      console.log("No walks found entering ready window.");
      return;
    }

    console.log(`Found ${reservationsSnapshot.size} walks entering ready window.`);

    for (const doc of reservationsSnapshot.docs) {
      const reserva = doc.data();

      // Evitar enviar notificacion duplicada
      if (reserva.readyWindowNotificationSent) continue;

      // Obtener ID del paseador
      const paseadorRef = reserva.id_paseador;
      let paseadorId = null;
      if (paseadorRef) {
        if (typeof paseadorRef === 'object' && paseadorRef.id) {
          paseadorId = paseadorRef.id;
        } else if (typeof paseadorRef === 'string') {
          paseadorId = paseadorRef;
        }
      }

      if (!paseadorId) continue;

      // Obtener FCM token del paseador
      const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
      if (!paseadorDoc.exists) continue;

      const fcmToken = paseadorDoc.data().fcmToken;
      if (!fcmToken) continue;

      // Obtener nombre de la mascota
      const idDueno = getIdValue(reserva.id_dueno);
      const idMascota = reserva.id_mascota;
      let nombreMascota = "la mascota";

      if (idMascota && idDueno) {
        try {
          const mascotaDoc = await db.collection("duenos").doc(idDueno)
            .collection("mascotas").doc(idMascota).get();
          if (mascotaDoc.exists) {
            nombreMascota = mascotaDoc.data().nombre || nombreMascota;
          }
        } catch (err) {
          console.error("Error fetching mascota:", err);
        }
      }

      // Formatear hora
      const horaInicio = reserva.hora_inicio.toDate();
      const horaFormateada = horaInicio.toLocaleTimeString('es-ES', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      });

      // Enviar notificacion
      const message = {
        token: fcmToken,
        notification: {
          title: "Ya puedes iniciar el paseo",
          body: `Tu paseo con ${nombreMascota} esta programado para las ${horaFormateada}. Ya puedes iniciar cuando llegues.`
        },
        data: {
          tipo: "ventana_inicio",
          reservaId: doc.id,
          click_action: "OPEN_CURRENT_WALK_ACTIVITY"
        },
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channelId: "paseos_channel"
          }
        }
      };

      try {
        await admin.messaging().send(message);
        console.log(`Sent ready window notification for reservation ${doc.id}`);

        // Marcar como enviada
        await doc.ref.update({ readyWindowNotificationSent: true });
      } catch (error) {
        console.error(`Error sending ready window notification for ${doc.id}:`, error);
      }
    }

    console.log("Completed ready window notification job.");
  } catch (error) {
    console.error("Error in ready window notification job:", error);
  }
});

// Notificaciones escalonadas para paseos retrasados (10, 20, 30 minutos)
exports.notifyDelayedWalks = onSchedule("every 5 minutes", async (event) => {
  console.log("Running scheduled job for delayed walk notifications.");
  const now = admin.firestore.Timestamp.now();

  const delays = [
    { minutes: 10, field: 'delay10MinNotificationSent', message: 'El paseo programado hace 10 minutos aun no ha comenzado.' },
    { minutes: 20, field: 'delay20MinNotificationSent', message: 'El paseo lleva 20 minutos de retraso. Por favor contacta al dueno si hay algun problema.' },
    { minutes: 30, field: 'delay30MinNotificationSent', message: 'El paseo lleva 30 minutos de retraso. Considera cancelar si no puedes asistir.' }
  ];

  try {
    for (const delay of delays) {
      const delayMillis = delay.minutes * 60 * 1000;
      const targetTimeMillis = now.toMillis() - delayMillis;
      const windowStartMillis = targetTimeMillis - (2.5 * 60 * 1000);
      const windowEndMillis = targetTimeMillis + (2.5 * 60 * 1000);
      const windowStart = admin.firestore.Timestamp.fromMillis(windowStartMillis);
      const windowEnd = admin.firestore.Timestamp.fromMillis(windowEndMillis);

      const reservationsSnapshot = await db.collection("reservas")
        .where("estado", "==", "LISTO_PARA_INICIAR")
        .where("hora_inicio", ">=", windowStart)
        .where("hora_inicio", "<=", windowEnd)
        .get();

      for (const doc of reservationsSnapshot.docs) {
        const reserva = doc.data();

        // Verificar si ya se envio esta notificacion
        if (reserva[delay.field]) continue;

        // Obtener paseador
        const paseadorRef = reserva.id_paseador;
        let paseadorId = null;
        if (paseadorRef) {
          if (typeof paseadorRef === 'object' && paseadorRef.id) {
            paseadorId = paseadorRef.id;
          } else if (typeof paseadorRef === 'string') {
            paseadorId = paseadorRef;
          }
        }

        if (!paseadorId) continue;

        const paseadorDoc = await db.collection("usuarios").doc(paseadorId).get();
        if (!paseadorDoc.exists) continue;

        const fcmToken = paseadorDoc.data().fcmToken;
        if (!fcmToken) continue;

        // Enviar notificacion al paseador
        const messagePaseador = {
          token: fcmToken,
          notification: {
            title: `Paseo Retrasado - ${delay.minutes} minutos`,
            body: delay.message
          },
          data: {
            tipo: "paseo_retrasado",
            reservaId: doc.id,
            delay: delay.minutes.toString(),
            click_action: "OPEN_CURRENT_WALK_ACTIVITY"
          },
          android: {
            priority: "high",
            notification: {
              sound: "default",
              channelId: "paseos_channel"
            }
          }
        };

        try {
          await admin.messaging().send(messagePaseador);
          console.log(`Sent ${delay.minutes}-minute delay notification to walker for ${doc.id}`);
        } catch (error) {
          console.error(`Error sending delay notification to walker:`, error);
        }

        // Si es 10 o 20 minutos, tambien notificar al dueno
        if (delay.minutes === 10 || delay.minutes === 20) {
          const idDueno = getIdValue(reserva.id_dueno);
          if (idDueno) {
            const duenoDoc = await db.collection("usuarios").doc(idDueno).get();
            if (duenoDoc.exists) {
              const duenoToken = duenoDoc.data().fcmToken;
              if (duenoToken) {
                const messageDueno = {
                  token: duenoToken,
                  notification: {
                    title: "Paseo Retrasado",
                    body: `El paseo lleva ${delay.minutes} minutos de retraso. El paseador aun no ha iniciado.`
                  },
                  data: {
                    tipo: "paseo_retrasado_dueno",
                    reservaId: doc.id,
                    delay: delay.minutes.toString(),
                    click_action: "OPEN_CURRENT_WALK_OWNER"
                  },
                  android: {
                    priority: "high",
                    notification: {
                      sound: "default",
                      channelId: "paseos_channel"
                    }
                  }
                };

                try {
                  await admin.messaging().send(messageDueno);
                  console.log(`Sent ${delay.minutes}-minute delay notification to owner for ${doc.id}`);
                } catch (error) {
                  console.error(`Error sending delay notification to owner:`, error);
                }
              }
            }
          }
        }

        // Marcar como enviada
        await doc.ref.update({ [delay.field]: true });
      }
    }

    console.log("Completed delayed walk notifications job.");
  } catch (error) {
    console.error("Error in delayed walk notifications job:", error);
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
    if (newStatus === "EN_CURSO") {
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
