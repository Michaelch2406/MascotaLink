const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { db, FieldValue } = require('../config/firebase');
const { getModel, initializeGemini } = require('../config/gemini');
const { logDebug, calculateDistance } = require('../utils/general');
const { getCachedRecommendations, saveCachedRecommendations } = require('../utils/firestore-helpers');

const CACHE_TTL = 15 * 60 * 1000;

/**
 * Cloud Function to recommend walkers based on user and pet criteria using Gemini AI.
 * This is a callable function, invoked directly from the Android app.
 */
const recomendarPaseadores = onCall(async (request) => {
  logDebug(` DEBUG: Función recomendarPaseadores invocada`);

  // Inicializar Gemini AI si aún no está inicializado
  const model = initializeGemini();
  logDebug(` DEBUG: Gemini inicializado. Model existe: ${model ? 'YES' : 'NO'}`);

  if (!model) {
    console.error("Gemini AI model is not initialized. Check GEMINI_API_KEY.");
    return { error: "Gemini AI no está disponible. Contacta al soporte." };
  }

  const { userData, petData, userLocation } = request.data;
  const userId = request.auth?.uid;
  logDebug(` DEBUG: User ID: ${userId}, Datos recibidos: userData=${!!userData}, petData=${!!petData}, userLocation=${!!userLocation}`);

  if (!userId) {
    throw new HttpsError('unauthenticated', 'El usuario no está autenticado.');
  }

  // Validar rol de usuario - solo dueños pueden solicitar recomendaciones
  const userDoc = await db.collection('usuarios').doc(userId).get();
  if (!userDoc.exists) {
    throw new HttpsError('not-found', 'No se encontró tu perfil de usuario.');
  }

  const userRole = userDoc.data().role || userDoc.data().rol;
  if (userRole?.toUpperCase() !== 'DUEÑO') {
    throw new HttpsError('permission-denied',
      'Solo los dueños de mascotas pueden solicitar recomendaciones con IA.');
  }
  console.log(` Validación de rol exitosa: ${userRole}`);

  // Rate limiting - máximo 10 recomendaciones por hora
  const rateLimitRef = db.collection('rate_limiting_ia').doc(userId);
  const rateLimitDoc = await rateLimitRef.get();
  const now = Date.now();
  const ONE_HOUR = 60 * 60 * 1000;
  const MAX_CALLS_PER_HOUR = 10;

  if (rateLimitDoc.exists) {
    const { count, resetTime } = rateLimitDoc.data();
    if (now < resetTime && count >= MAX_CALLS_PER_HOUR) {
      const minutesRemaining = Math.ceil((resetTime - now) / 60000);
      throw new HttpsError('resource-exhausted',
        `Has alcanzado el límite de ${MAX_CALLS_PER_HOUR} recomendaciones por hora. Intenta en ${minutesRemaining} minutos.`);
    }

    // Resetear contador si pasó la hora
    if (now >= resetTime) {
      await rateLimitRef.set({
        count: 1,
        resetTime: now + ONE_HOUR,
        lastCall: now
      });
      console.log(` Rate limit reseteado para usuario ${userId}`);
    } else {
      // Incrementar contador
      await rateLimitRef.update({
        count: FieldValue.increment(1),
        lastCall: now
      });
      console.log(` Rate limit: ${count + 1}/${MAX_CALLS_PER_HOUR} llamadas`);
    }
  } else {
    // Primera llamada del usuario
    await rateLimitRef.set({
      count: 1,
      resetTime: now + ONE_HOUR,
      lastCall: now
    });
    console.log(` Rate limit inicializado para usuario ${userId}`);
  }

  if (!userData || !petData || !userLocation) {
    throw new HttpsError(
      'invalid-argument',
      'Faltan datos de usuario, mascota o ubicación.'
    );
  }

  // 🆕 MEJORA #3: Validar campos mínimos requeridos
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

  console.log(` Validación exitosa - Recomendación para usuario ${userId}, mascota ${petData.nombre} (${petData.tamano})`);
  logDebug(` DEBUG: Iniciando lógica de recomendación...`);

  // Verificar cache de Firestore antes de llamar a Gemini
  const petId = petData.id || petData.nombre;
  const cachedRecommendations = await getCachedRecommendations(userId, petId);

  if (cachedRecommendations) {
    console.log(`💾 Retornando recomendaciones desde cache de Firestore`);
    return {
      recommendations: cachedRecommendations,
      message: "Recomendaciones obtenidas (cache)",
      cached: true
    };
  }

  logDebug(` DEBUG: Cache no encontrado, continuando con búsqueda...`);

  const userLat = userLocation.latitude;
  const userLng = userLocation.longitude;
  const radiusKm = 10; // Search within 10 km radius
  logDebug(` DEBUG: Ubicación usuario: ${userLat}, ${userLng} - Radio: ${radiusKm}km`);

  let potentialWalkers = [];

  logDebug(` DEBUG: Iniciando bloque try para búsqueda de paseadores...`);
  try {
    logDebug(` DEBUG: Ejecutando query a paseadores_search...`);
    // Fetch potential walkers from paseadores_search collection (denormalizado)
    const searchSnapshot = await db.collection("paseadores_search")
      .where("activo", "==", true)
      .orderBy("calificacion_promedio", "desc")
      .limit(50) // Máximo 50 candidatos iniciales para optimizar
      .get();

    logDebug(` DEBUG: Query completada. Documentos encontrados: ${searchSnapshot.size}`);

    const walkerIds = [];
    const walkerDataMap = {};

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
        logDebug(`  ⏭️ Saltando paseador ${walkerId}: verificacion_estado = "${verificacionEstado}"`);
      }
    }

    logDebug(` DEBUG: Total de IDs de paseadores verificados/aprobados: ${walkerIds.length}`);

    if (walkerIds.length === 0) {
      console.log(` No se encontraron paseadores activos y verificados`);
      return { recommendations: [], message: "No se encontraron paseadores activos y verificados." };
    }

    logDebug(` DEBUG: Obteniendo datos completos de ${walkerIds.length} paseadores...`);

    // Fetch full paseador profile data and user display name
    const fullWalkerPromises = walkerIds.map(id => db.collection("paseadores").doc(id).get());
    const userDisplayPromises = walkerIds.map(id => db.collection("usuarios").doc(id).get());

    logDebug(` DEBUG: Ejecutando queries paralelas a paseadores y usuarios...`);

    const [fullWalkerDocs, userDisplayDocs] = await Promise.all([
      Promise.all(fullWalkerPromises),
      Promise.all(userDisplayPromises)
    ]);

    logDebug(` DEBUG: Queries completadas. fullWalkerDocs: ${fullWalkerDocs.length}, userDisplayDocs: ${userDisplayDocs.length}`);

    const paseadoresForAI = [];

    logDebug(` DEBUG: Procesando ${walkerIds.length} paseadores y calculando distancias...`);

    for (let i = 0; i < walkerIds.length; i++) {
      const walkerId = walkerIds[i];
      const searchData = walkerDataMap[walkerId];
      const fullWalkerData = fullWalkerDocs[i].exists ? fullWalkerDocs[i].data() : {};
      const userData = userDisplayDocs[i].exists ? userDisplayDocs[i].data() : {};

      logDebug(`   Procesando paseador ${i + 1}/${walkerIds.length}: ${walkerId}`);

      // Buscar ubicación en varios lugares posibles
      let walkerLocation = null;

      if (userData.ubicacion_actual) {
        walkerLocation = userData.ubicacion_actual;
        logDebug(`    📍 Ubicación encontrada en usuarios.ubicacion_actual`);
      } else if (fullWalkerData.ubicacion_principal?.geopoint) {
        walkerLocation = fullWalkerData.ubicacion_principal.geopoint;
        logDebug(`    📍 Ubicación encontrada en paseadores.ubicacion_principal.geopoint`);
      } else if (userData.ubicacion) {
        walkerLocation = userData.ubicacion;
        logDebug(`    📍 Ubicación encontrada en usuarios.ubicacion`);
      }

      if (!walkerLocation || !walkerLocation.latitude || !walkerLocation.longitude) {
        logDebug(`     Sin ubicación válida en ningún campo, saltando`);
        continue;
      }

      const distance = calculateDistance(userLat, userLng, walkerLocation.latitude, walkerLocation.longitude);
      logDebug(`    📍 Distancia: ${distance.toFixed(2)}km (radio máx: ${radiusKm}km)`);

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
            // Campos adicionales optimizados (solo los que Gemini realmente usa)
            top_resenas: (searchData.top_resenas || []).slice(0, 2), // Solo 2 reseñas en vez de todas
            zonas_principales: (searchData.zonas_principales || []).slice(0, 2), // Solo 2 zonas
            // Campos para enriquecer la recomendación en el cliente
            foto_perfil: userData.foto_perfil || fullWalkerData.foto_perfil || '',
            ubicacion: userData.ubicacion_actual || userData.ubicacion || fullWalkerData.ubicacion_principal?.geopoint || '',
            total_resenas: searchData.total_resenas || 0,
            especialidad: fullWalkerData.especialidad || ''
          });
          logDebug(`     Agregado a lista de candidatos`);
        } else {
          logDebug(`     Fuera de rango (>${radiusKm}km)`);
        }
    }

    logDebug(` DEBUG: Total de paseadores dentro del radio: ${paseadoresForAI.length}`);

    if (paseadoresForAI.length === 0) {
      console.log(` No se encontraron paseadores cerca de la ubicación`);
      return { recommendations: [], message: "No se encontraron paseadores aptos cerca de tu ubicación." };
    }

    // 🆕 MEJORA #4: Pre-scoring híbrido (distancia + calificación + experiencia)
    logDebug(` DEBUG: Calculando pre-score para ${paseadoresForAI.length} candidatos...`);

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

    logDebug(` DEBUG: Pre-scoring completado`);

    // 🆕 MEJORA C: Personalización basada en historial del usuario
    logDebug(" DEBUG: 🎯 Analizando historial de usuario para personalización...");

    try {
      const userId = request.auth.uid;
      logDebug(` DEBUG: Consultando historial de reservas para usuario ${userId}...`);
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

        console.log(`   Historial: ${paseadoresPrevios.size} paseadores únicos, ${caracteristicasPreferidas.count} reservas analizadas`);
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
      console.error(" Error en personalización (continuando sin ella):", error.message);
      // No fallar la función, solo log del error
    }

    // Ordenar por pre_score descendente (mejores primero)
    paseadoresForAI.sort((a, b) => b.pre_score - a.pre_score);

    // 🔥 OPTIMIZACIÓN: Pre-filtrar por compatibilidad de tamaño ANTES de enviar a Gemini
    const tamanoMascota = petData.tamano;
    const candidatosCompatibles = paseadoresForAI.filter(paseador => {
      const aceptaTamano = paseador.tipos_perro_aceptados.some(tipo =>
        tipo.toLowerCase() === tamanoMascota.toLowerCase() // Comparación exacta: "Mediano" === "Mediano"
      );
      if (!aceptaTamano) {
        console.log(`  🚫 Filtrado: ${paseador.nombre} no acepta perros ${tamanoMascota}`);
      }
      return aceptaTamano;
    });

    logDebug(` DEBUG: Candidatos compatibles con ${tamanoMascota}: ${candidatosCompatibles.length} de ${paseadoresForAI.length}`);

    // Si no hay candidatos compatibles, devolver mensaje específico
    if (candidatosCompatibles.length === 0) {
      console.log(` No hay paseadores que acepten perros ${tamanoMascota}`);
      return {
        recommendations: [],
        message: `No encontramos paseadores que acepten perros de tamaño ${tamanoMascota} en tu área. Intenta expandir tu búsqueda.`
      };
    }

    // 🔥 OPTIMIZACIÓN: Limitar a máximo 8 candidatos (reducido para ahorrar tokens/créditos)
    const candidatosParaIA = candidatosCompatibles.slice(0, 8);

    logDebug(` DEBUG:  Top 3 candidatos por pre-score:`);
    candidatosParaIA.slice(0, 3).forEach((p, i) => {
      logDebug(`  ${i+1}. ${p.nombre} - Score: ${p.pre_score.toFixed(1)} (${p.calificacion_promedio}⭐, ${p.distancia_km}km, ${p.anos_experiencia}años exp)`);
    });
    logDebug(` DEBUG: Enviando ${candidatosParaIA.length} candidatos a Gemini AI (de ${paseadoresForAI.length} totales)`);

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
        ],
        foto_perfil: candidato.foto_perfil || '',
        ubicacion: candidato.ubicacion || '',
        anos_experiencia: candidato.anos_experiencia || 0,
        calificacion_promedio: candidato.calificacion_promedio || 0,
        total_resenas: candidato.total_resenas || 0,
        precio_hora: candidato.precio_hora || 0,
        especialidad: candidato.especialidad || '',
        tipos_perro_aceptados: candidato.tipos_perro_aceptados || [],
        distancia_km: candidato.distancia_km || 0,
        num_servicios_completados: candidato.num_servicios_completados || 0
      };

      // Guardar en cache
      await saveCachedRecommendations(userId, petId, [directRecommendation]);

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
        is_fallback: true,
        foto_perfil: mejorCandidato.foto_perfil || '',
        ubicacion: mejorCandidato.ubicacion || '',
        anos_experiencia: mejorCandidato.anos_experiencia || 0,
        calificacion_promedio: mejorCandidato.calificacion_promedio || 0,
        total_resenas: mejorCandidato.total_resenas || 0,
        precio_hora: mejorCandidato.precio_hora || 0,
        especialidad: mejorCandidato.especialidad || '',
        tipos_perro_aceptados: mejorCandidato.tipos_perro_aceptados || [],
        distancia_km: mejorCandidato.distancia_km || 0,
        num_servicios_completados: mejorCandidato.num_servicios_completados || 0
      };

      // Guardar en cache
      await saveCachedRecommendations(userId, petId, [fallbackRecommendation]);

      return {
        recommendations: [fallbackRecommendation],
        message: "Mostrando la mejor opción disponible (match limitado)"
      };
    }

    // Construct the prompt for Gemini AI (optimizado para reducir tokens)
    logDebug(` DEBUG: Construyendo prompt para Gemini AI...`);

    // Simplificar datos de candidatos (solo lo necesario para Gemini)
    const candidatosSimplificados = candidatosParaIA.map(p => ({
      id: p.id,
      nombre: p.nombre,
      cal: p.calificacion_promedio, // Nombre corto
      serv: p.num_servicios_completados,
      precio: p.precio_hora,
      exp: p.anos_experiencia,
      tipos: p.tipos_perro_aceptados,
      dist: p.distancia_km,
      score: Math.round(p.pre_score) // Pre-score ya calculado
    }));

    const prompt = `Experto en matching de paseadores. Analiza y recomienda.

MASCOTA DEL USUARIO: ${petData.nombre}, Tamaño: ${petData.tamano}, Raza: ${petData.raza || 'sin especificar'}

CRITERIOS ESTRICTOS:
1. Tamaño DEBE coincidir: Paseador acepta ${petData.tamano} (40%)
2. Reputación: Calificación + número de servicios (25%)
3. Distancia: <2km es excelente (20%)
4. Experiencia: años de experiencia (10%)
5. Precio competitivo (5%)

REGLAS OBLIGATORIAS:
- RECHAZAR si los "tipos" del paseador NO incluyen "${petData.tamano}"
- RECHAZAR si match_score < 50
- Máximo 2 recomendaciones
- En empate: priorizar por menor distancia
- IMPORTANTE: Solo mencionar DATOS REALES del paseador (nombre, calificación, distancia, experiencia)
- NO inventar especialidades, características o datos no presentes

CANDIDATOS DISPONIBLES:
${JSON.stringify(candidatosSimplificados)}

SALIDA (JSON VÁLIDO):
[
  {
    "id": "walker_id",
    "nombre": "Nombre del paseador",
    "razon_ia": "Experiencia comprobada con ${petData.tamano}s, calificación X.X/5 con X servicios completados. Ubicado a Xkm. Una excelente opción para ${petData.nombre}.",
    "match_score": 75,
    "tags": ["📍 Xkm", "⭐ X.X/5", "🐕 ${petData.tamano}"]
  }
]

IMPORTANTE: Devuelve SOLO JSON válido. Si no hay matches: []`;

    logDebug(` DEBUG: ⚡ Enviando prompt a Gemini AI (longitud: ${prompt.length} caracteres)...`);
    const result = await model.generateContent(prompt);
    logDebug(` DEBUG:  Respuesta de Gemini recibida`);

    const response = result.response;
    const text = response.text();

    logDebug(` DEBUG: Respuesta de Gemini AI (raw, primeros 500 chars): ${text.substring(0, 500)}`);

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

    logDebug(` DEBUG: Recomendaciones parseadas de Gemini AI:`, JSON.stringify(recommendations, null, 2));

    // 🆕 ENRIQUECIMIENTO: Agregar datos completos del paseador a cada recomendación
    recommendations = recommendations.map(rec => {
      const paseadorCompleto = candidatosParaIA.find(p => p.id === rec.id);
      if (paseadorCompleto) {
        return {
          ...rec,
          foto_perfil: paseadorCompleto.foto_perfil || '',
          ubicacion: paseadorCompleto.ubicacion || '',
          zonas_principales: paseadorCompleto.zonas_principales || [],
          anos_experiencia: paseadorCompleto.anos_experiencia || 0,
          calificacion_promedio: paseadorCompleto.calificacion_promedio || 0,
          total_resenas: paseadorCompleto.total_resenas || 0,
          precio_hora: paseadorCompleto.precio_hora || 0,
          especialidad: paseadorCompleto.especialidad || '',
          tipos_perro_aceptados: paseadorCompleto.tipos_perro_aceptados || [],
          distancia_km: paseadorCompleto.distancia_km || 0,
          num_servicios_completados: paseadorCompleto.num_servicios_completados || 0
        };
      }
      return rec;
    });

    // 🆕 OPTIMIZACIÓN: Si Gemini no devuelve matches, usar fallback con el mejor candidato
    if (!recommendations || recommendations.length === 0) {
      console.log(` Gemini no devolvió matches. Activando fallback con mejor candidato disponible...`);

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
          is_fallback: true, // Marcar como fallback
          foto_perfil: mejorCandidato.foto_perfil || '',
          ubicacion: mejorCandidato.ubicacion || '',
          zonas_principales: mejorCandidato.zonas_principales || [],
          anos_experiencia: mejorCandidato.anos_experiencia || 0,
          calificacion_promedio: mejorCandidato.calificacion_promedio || 0,
          total_resenas: mejorCandidato.total_resenas || 0,
          precio_hora: mejorCandidato.precio_hora || 0,
          especialidad: mejorCandidato.especialidad || '',
          tipos_perro_aceptados: mejorCandidato.tipos_perro_aceptados || [],
          distancia_km: mejorCandidato.distancia_km || 0,
          num_servicios_completados: mejorCandidato.num_servicios_completados || 0
        };

        recommendations = [fallbackRecommendation];
        console.log(` Fallback activado: ${mejorCandidato.nombre} (score: ${mejorCandidato.pre_score.toFixed(1)})`);
      } else {
        console.log(` No hay candidatos disponibles para fallback`);
        return { recommendations: [], message: "No se encontraron paseadores aptos cerca de tu ubicación." };
      }
    }

    // Guardar en cache de Firestore antes de retornar
    await saveCachedRecommendations(userId, petId, recommendations);
    logDebug(` DEBUG: 💾 Recomendaciones guardadas en cache (válido por ${CACHE_TTL / 1000}s)`);

    logDebug(` DEBUG: ✅✅✅ FUNCIÓN COMPLETADA EXITOSAMENTE - Retornando ${recommendations.length} recomendaciones`);
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

module.exports = { recomendarPaseadores };
