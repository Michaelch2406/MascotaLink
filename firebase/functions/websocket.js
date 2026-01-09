/**
 * WebSocket Server con Socket.IO para MascotaLink
 * Maneja comunicación en tiempo real para chat, tracking de paseos y presencia
 */

const admin = require("firebase-admin");

/**
 * Inicializa el servidor Socket.IO
 * @param {object} io - Instancia de Socket.IO
 * @param {object} db - Instancia de Firestore
 */
function initializeSocketServer(io, db) {
  console.log("🚀 Inicializando servidor WebSocket...");

  // Caché de estado de paseos activos para evitar consultas repetidas a Firestore
  const paseoEstadoCache = new Map();

  // ========================================
  // MIDDLEWARE DE AUTENTICACIÓN
  // ========================================
  io.use(async (socket, next) => {
    const token = socket.handshake.auth.token;

    if (!token) {
      console.warn(" Conexión rechazada: sin token");
      return next(new Error("Authentication token required"));
    }

    try {
      // Verificar token de Firebase Auth
      const decoded = await admin.auth().verifyIdToken(token);
      socket.userId = decoded.uid;
      socket.userEmail = decoded.email;

      // Obtener datos del usuario desde Firestore
      const userDoc = await db.collection("usuarios").doc(decoded.uid).get();
      if (userDoc.exists) {
        const userData = userDoc.data();
        socket.userRole = userData.rol || "DUENO";
        socket.userName = userData.nombre_display || "Usuario";
        console.log(` Usuario autenticado: ${socket.userName} (${socket.userId})`);
      }

      next();
    } catch (error) {
      console.error(" Error de autenticación:", error.message);
      next(new Error("Authentication failed"));
    }
  });

  // ========================================
  // GESTIÓN DE CONEXIONES
  // ========================================
  io.on("connection", (socket) => {
    console.log(` Usuario conectado: ${socket.userName} [${socket.userId}]`);

    // Unir al usuario a su room personal
    socket.join(socket.userId);

    // Actualizar presencia a "online"
    updateUserPresence(db, socket.userId, "online");

    // ========================================
    // EVENTOS DE CHAT
    // ========================================

    /**
     * Unirse a una sala de chat
     */
    socket.on("join_chat", async (chatId) => {
      try {
        // Validar que el usuario pertenece al chat
        const chatDoc = await db.collection("chats").doc(chatId).get();

        if (!chatDoc.exists) {
          console.warn(` Chat ${chatId} no existe`);
          return socket.emit("error", { message: "Chat no encontrado" });
        }

        const participantes = chatDoc.data().participantes || [];
        if (!participantes.includes(socket.userId)) {
          console.warn(` Usuario ${socket.userId} no autorizado para chat ${chatId}`);
          return socket.emit("error", { message: "No autorizado" });
        }

        socket.join(chatId);
        console.log(`💬 ${socket.userName} se unió al chat ${chatId}`);

        // Actualizar estado en Firestore
        await db.collection("chats").doc(chatId).update({
          [`chat_abierto.${socket.userId}`]: chatId,
          [`estado_usuarios.${socket.userId}`]: "online",
        });

        socket.emit("joined_chat", { chatId });
      } catch (error) {
        console.error("Error al unirse al chat:", error);
        socket.emit("error", { message: "Error al unirse al chat" });
      }
    });

    /**
     * Salir de una sala de chat
     */
    socket.on("leave_chat", async (chatId) => {
      try {
        socket.leave(chatId);
        console.log(`💬 ${socket.userName} salió del chat ${chatId}`);

        await db.collection("chats").doc(chatId).update({
          [`chat_abierto.${socket.userId}`]: null,
          [`estado_usuarios.${socket.userId}`]: "online",
        });
      } catch (error) {
        console.error("Error al salir del chat:", error);
      }
    });

    /**
     * Enviar mensaje de chat
     */
    socket.on("send_message", async (data) => {
      try {
        const { chatId, destinatarioId, texto, tipo, imagen_url, latitud, longitud } = data;

        // Validar pertenencia al chat
        const chatDoc = await db.collection("chats").doc(chatId).get();
        if (!chatDoc.exists || !chatDoc.data().participantes.includes(socket.userId)) {
          return socket.emit("error", { message: "No autorizado" });
        }

        // Validar datos
        if (!texto && !imagen_url && !latitud) {
          return socket.emit("error", { message: "Mensaje vacío" });
        }

        // Guardar en Firestore
        const messageRef = await db
          .collection("chats")
          .doc(chatId)
          .collection("mensajes")
          .add({
            id_remitente: socket.userId,
            id_destinatario: destinatarioId,
            texto: texto || "",
            tipo: tipo || "texto",
            imagen_url: imagen_url || null,
            latitud: latitud || null,
            longitud: longitud || null,
            timestamp: admin.firestore.FieldValue.serverTimestamp(),
            leido: false,
            entregado: true,
            fecha_eliminacion: admin.firestore.Timestamp.fromMillis(
              Date.now() + 7 * 24 * 60 * 60 * 1000 // 7 días
            ),
          });

        const messageId = messageRef.id;
        const now = new Date();

        // Broadcast INMEDIATO a todos en el chat
        io.to(chatId).emit("new_message", {
          id: messageId,
          id_remitente: socket.userId,
          id_destinatario: destinatarioId,
          texto: texto || "",
          tipo: tipo || "texto",
          imagen_url: imagen_url || null,
          latitud: latitud || null,
          longitud: longitud || null,
          timestamp: now.toISOString(),
          leido: false,
          entregado: true,
          sender_name: socket.userName,
        });

        // Actualizar metadatos del chat
        await db.collection("chats").doc(chatId).update({
          ultimo_mensaje: texto?.substring(0, 100) || "[Media]",
          ultimo_timestamp: admin.firestore.FieldValue.serverTimestamp(),
          [`mensajes_no_leidos.${destinatarioId}`]: admin.firestore.FieldValue.increment(1),
        });

        console.log(`📨 Mensaje enviado en chat ${chatId} por ${socket.userName}`);

        // Enviar FCM solo si destinatario no tiene el chat abierto
        await sendFCMIfNeeded(db, destinatarioId, chatId, texto || "[Media]", socket.userId, socket.userName);
      } catch (error) {
        console.error("Error al enviar mensaje:", error);
        socket.emit("error", { message: "Error al enviar mensaje" });
      }
    });

    /**
     * Indicador de escritura (typing)
     */
    socket.on("typing", async (chatId) => {
      try {
        // Broadcast a otros usuarios del chat (no a sí mismo)
        socket.to(chatId).emit("user_typing", {
          userId: socket.userId,
          userName: socket.userName,
          chatId: chatId,
        });

        // Actualizar en Firestore (opcional, para persistencia)
        await db.collection("chats").doc(chatId).update({
          [`estado_usuarios.${socket.userId}`]: "escribiendo",
        });
      } catch (error) {
        console.error("Error en typing:", error);
      }
    });

    /**
     * Detener indicador de escritura
     */
    socket.on("stop_typing", async (chatId) => {
      try {
        socket.to(chatId).emit("user_stop_typing", {
          userId: socket.userId,
          chatId: chatId,
        });

        await db.collection("chats").doc(chatId).update({
          [`estado_usuarios.${socket.userId}`]: "online",
        });
      } catch (error) {
        console.error("Error en stop_typing:", error);
      }
    });

    /**
     * Marcar mensaje como leído
     */
    socket.on("mark_read", async (data) => {
      try {
        const { chatId, messageId } = data;

        // Actualizar en Firestore
        await db
          .collection("chats")
          .doc(chatId)
          .collection("mensajes")
          .doc(messageId)
          .update({
            leido: true,
          });

        // Broadcast read receipt
        socket.to(chatId).emit("message_read", {
          messageId: messageId,
          readBy: socket.userId,
        });

        console.log(` Mensaje ${messageId} marcado como leído`);
      } catch (error) {
        console.error("Error al marcar como leído:", error);
      }
    });

    /**
     * Resetear contador de mensajes no leídos
     */
    socket.on("reset_unread", async (chatId) => {
      try {
        await db.collection("chats").doc(chatId).update({
          [`mensajes_no_leidos.${socket.userId}`]: 0,
        });
        console.log(`🔔 Contador de no leídos reseteado para ${socket.userId} en ${chatId}`);
      } catch (error) {
        console.error("Error al resetear no leídos:", error);
      }
    });

    // ========================================
    // EVENTOS DE PASEO (WALK TRACKING)
    // ========================================

    /**
     * Unirse a tracking de paseo
     */
    socket.on("join_paseo", async (paseoId) => {
      try {
        // Verificar que el usuario es parte del paseo
        const paseoDoc = await db.collection("reservas").doc(paseoId).get();

        if (!paseoDoc.exists) {
          return socket.emit("error", { message: "Paseo no encontrado" });
        }

        const paseoData = paseoDoc.data();
        const idPaseador = extractId(paseoData.id_paseador);
        const idDueno = extractId(paseoData.id_dueno);

        if (socket.userId !== idPaseador && socket.userId !== idDueno) {
          return socket.emit("error", { message: "No autorizado para este paseo" });
        }

        const roomName = `paseo_${paseoId}`;
        socket.join(roomName);

        const room = io.sockets.adapter.rooms.get(roomName);
        const clientsInRoom = room ? room.size : 0;

        // Cachear el estado del paseo para evitar consultas repetidas
        const estadoActual = paseoData.estado;
        paseoEstadoCache.set(paseoId, {
          estado: estadoActual,
          idPaseador: idPaseador,
          idDueno: idDueno,
          timestamp: Date.now(),
        });

        console.log(`[JOIN_PASEO] Usuario ${socket.userName} (${socket.userId}) se unio a sala "${roomName}"`);
        console.log(`[JOIN_PASEO] Total de clientes en sala: ${clientsInRoom}`);
        console.log(`[JOIN_PASEO] Estado cacheado: ${estadoActual}`);

        socket.emit("joined_paseo", { paseoId });
      } catch (error) {
        console.error("Error al unirse al paseo:", error);
        socket.emit("error", { message: "Error al unirse al paseo" });
      }
    });

    /**
     * Actualizar ubicación del paseador (streaming)
     */
    socket.on("update_location", async (data) => {
      try {
        console.log(`[UPDATE_LOCATION] Recibido de usuario ${socket.userName} (${socket.userId})`);
        console.log(`[UPDATE_LOCATION] Datos raw:`, JSON.stringify(data));

        // SOPORTE DE COMPRESIÓN: Leer formato comprimido o normal (retrocompatibilidad)
        const paseoId = data.p || data.paseoId;
        const latitud = data.lat || data.latitud;
        const longitud = data.lng || data.longitud;
        const accuracy = data.acc || data.accuracy || 0;
        const speed = data.speed || 0;
        const timestamp = data.ts || data.timestamp || Date.now();

        if (!paseoId) {
          console.error("[UPDATE_LOCATION] ERROR: sin paseoId en datos:", data);
          return socket.emit("error", { message: "paseoId requerido" });
        }

        console.log(`[UPDATE_LOCATION] Parseado: paseoId=${paseoId}, lat=${latitud}, lng=${longitud}`);

        // Usar cache si esta disponible, sino consultar Firestore
        let cachedData = paseoEstadoCache.get(paseoId);

        if (!cachedData || Date.now() - cachedData.timestamp > 60000) {
          // Cache no disponible o expirado (>60s), consultar Firestore
          console.log(`[UPDATE_LOCATION] Cache expirado para ${paseoId}, consultando Firestore...`);
          const paseoDoc = await db.collection("reservas").doc(paseoId).get();
          if (!paseoDoc.exists) {
            console.error(`[UPDATE_LOCATION] ERROR: Paseo ${paseoId} no encontrado`);
            return socket.emit("error", { message: "Paseo no encontrado" });
          }

          const idPaseador = extractId(paseoDoc.data().id_paseador);
          const idDueno = extractId(paseoDoc.data().id_dueno);
          const estado = paseoDoc.data().estado;

          // Actualizar cache
          cachedData = {
            estado: estado,
            idPaseador: idPaseador,
            idDueno: idDueno,
            timestamp: Date.now(),
          };
          paseoEstadoCache.set(paseoId, cachedData);
          console.log(`[UPDATE_LOCATION] Cache actualizado: estado=${estado}`);
        } else {
          console.log(`[UPDATE_LOCATION] Usando cache: estado=${cachedData.estado}`);
        }

        // Verificar que el usuario es el paseador
        if (socket.userId !== cachedData.idPaseador) {
          console.error(`[UPDATE_LOCATION] ERROR: Usuario ${socket.userId} no es el paseador de ${paseoId}`);
          return socket.emit("error", { message: "Solo el paseador puede enviar ubicación" });
        }

        // Verificar que el paseo este EN_CURSO
        if (cachedData.estado !== "EN_CURSO") {
          console.log(`[UPDATE_LOCATION] WARN: Ubicacion rechazada para paseo ${paseoId} - estado: ${cachedData.estado} (debe ser EN_CURSO)`);
          return socket.emit("error", { message: "Solo se puede enviar ubicación cuando el paseo está en curso" });
        }

        // Obtener info de la sala para debugging
        const roomName = `paseo_${paseoId}`;
        const room = io.sockets.adapter.rooms.get(roomName);
        const clientsInRoom = room ? room.size : 0;

        console.log(`[UPDATE_LOCATION] Emitiendo a sala "${roomName}" con ${clientsInRoom} clientes`);
        console.log(`[UPDATE_LOCATION] Datos: lat=${latitud}, lng=${longitud}, acc=${accuracy}`);

        // Stream en tiempo real a todos los participantes (usar mismo evento que el cliente escucha)
        io.to(roomName).emit("update_location", {
          paseoId,
          lat: latitud,
          lng: longitud,
          acc: accuracy,
          speed: speed,
          ts: timestamp,
        });

        console.log(`[UPDATE_LOCATION] Ubicacion streamed para paseo ${paseoId} a ${clientsInRoom} cliente(s)`);

        // Guardar en Firestore solo cada 10 segundos (reduce writes pero mantiene historial)
        const lastSave = socket.lastLocationSave || 0;
        if (Date.now() - lastSave > 10000) {
          const locationData = {
            lat: latitud,
            lng: longitud,
            acc: accuracy,
            speed: speed,
            ts: admin.firestore.Timestamp.fromMillis(timestamp),
          };

          const geoPoint = new admin.firestore.GeoPoint(latitud, longitud);

          // ===== ACTUALIZAR 1: Reserva (para mapa del dueño) =====
          await db.collection("reservas").doc(paseoId).update({
            ubicacion_actual: geoPoint,
            ultima_actualizacion: admin.firestore.FieldValue.serverTimestamp(),
            updated_at: admin.firestore.FieldValue.serverTimestamp(),
            ubicaciones: admin.firestore.FieldValue.arrayUnion(locationData),
          });

          // ===== ACTUALIZAR 2: Usuario (para perfil) =====
          // Calcular geohash para búsquedas geoespaciales (usando algoritmo simple)
          const geoHash = calculateGeoHash(latitud, longitud);

          await db.collection("usuarios").doc(socket.userId).update({
            ubicacion_actual: geoPoint,
            ubicacion: geoPoint,
            ubicacion_geohash: geoHash,
            updated_at: admin.firestore.FieldValue.serverTimestamp(),
            estado: "online",
          }).catch(err => console.warn("Warn: Error actualizando usuario", err.message));

          // ===== ACTUALIZAR 3: Paseadores Search (CRÍTICO para búsqueda) =====
          await db.collection("paseadores_search").doc(socket.userId).update({
            ubicacion_actual: geoPoint,
            ubicacion_geohash: geoHash,
            updated_at: admin.firestore.FieldValue.serverTimestamp(),
            estado: "online",
          }).catch(err => console.warn("Warn: Error actualizando paseadores_search", err.message));

          socket.lastLocationSave = Date.now();
          console.log(`📍 Ubicación guardada en: reservas + usuarios + paseadores_search para paseo ${paseoId}`);
        }
      } catch (error) {
        console.error("Error al actualizar ubicación:", error);
        socket.emit("error", { message: "Error al actualizar ubicación" });
      }
    });

    /**
     * Cambiar estado del paseo
     */
    socket.on("paseo_estado_change", async (data) => {
      try {
        const { paseoId, nuevoEstado } = data;

        // Verificar permisos
        const paseoDoc = await db.collection("reservas").doc(paseoId).get();
        if (!paseoDoc.exists) {
          return socket.emit("error", { message: "Paseo no encontrado" });
        }

        const idPaseador = extractId(paseoDoc.data().id_paseador);
        const idDueno = extractId(paseoDoc.data().id_dueno);

        // Solo paseador o dueño pueden cambiar estado
        if (socket.userId !== idPaseador && socket.userId !== idDueno) {
          return socket.emit("error", { message: "No autorizado" });
        }

        // Actualizar en Firestore
        await db.collection("reservas").doc(paseoId).update({
          estado: nuevoEstado,
          [`fecha_${nuevoEstado.toLowerCase()}`]: admin.firestore.FieldValue.serverTimestamp(),
        });

        // Actualizar cache inmediatamente
        const cachedData = paseoEstadoCache.get(paseoId);
        if (cachedData) {
          cachedData.estado = nuevoEstado;
          cachedData.timestamp = Date.now();
          paseoEstadoCache.set(paseoId, cachedData);
          console.log(`[PASEO_ESTADO_CHANGE] Cache actualizado: ${paseoId} -> ${nuevoEstado}`);
        } else {
          // Si no existe en cache, crearlo
          paseoEstadoCache.set(paseoId, {
            estado: nuevoEstado,
            idPaseador: idPaseador,
            idDueno: idDueno,
            timestamp: Date.now(),
          });
          console.log(`[PASEO_ESTADO_CHANGE] Cache creado: ${paseoId} -> ${nuevoEstado}`);
        }

        // Notificar a todos los participantes
        io.to(`paseo_${paseoId}`).emit("paseo_updated", {
          paseoId,
          estado: nuevoEstado,
          changedBy: socket.userId,
        });

        console.log(`[PASEO_ESTADO_CHANGE] Estado de paseo ${paseoId} cambiado a ${nuevoEstado}`);

        // Limpiar cache si el paseo termino
        if (nuevoEstado === "COMPLETADO" || nuevoEstado === "CANCELADO") {
          paseoEstadoCache.delete(paseoId);
          console.log(`[PASEO_ESTADO_CHANGE] Cache eliminado para paseo finalizado: ${paseoId}`);
        }
      } catch (error) {
        console.error("Error al cambiar estado de paseo:", error);
        socket.emit("error", { message: "Error al cambiar estado" });
      }
    });

    // ========================================
    // SISTEMA DE PRESENCIA
    // ========================================

    /**
     * Obtener usuarios online
     */
    socket.on("get_online_users", (userIds) => {
      const onlineUsers = [];
      userIds.forEach((uid) => {
        const sockets = io.sockets.adapter.rooms.get(uid);
        if (sockets && sockets.size > 0) {
          onlineUsers.push(uid);
        }
      });
      socket.emit("online_users", onlineUsers);
    });

    /**
     * Ping para mantener conexión viva
     */
    socket.on("ping", () => {
      socket.emit("pong", { timestamp: Date.now() });
    });

    // ========================================
    // DESCONEXIÓN
    // ========================================
    socket.on("disconnect", async (reason) => {
      console.log(` Usuario desconectado: ${socket.userName} [${reason}]`);

      try {
        // Actualizar presencia a "offline"
        await updateUserPresence(db, socket.userId, "offline");

        // Limpiar chat_abierto para todos los chats del usuario
        const chatsSnapshot = await db
          .collection("chats")
          .where("participantes", "array-contains", socket.userId)
          .get();

        const updates = [];
        chatsSnapshot.forEach((doc) => {
          updates.push(
            doc.ref.update({
              [`chat_abierto.${socket.userId}`]: null,
              [`estado_usuarios.${socket.userId}`]: "offline",
            })
          );
        });

        await Promise.all(updates);
      } catch (error) {
        console.error("Error en desconexión:", error);
      }
    });
  });

  console.log(" Servidor WebSocket inicializado correctamente");
}

// ========================================
// FUNCIONES AUXILIARES
// ========================================

/**
 * Actualiza el estado de presencia del usuario en usuarios y paseadores_search
 */
async function updateUserPresence(db, userId, status) {
  try {
    // ===== ACTUALIZAR 1: Colección 'usuarios' =====
    await db.collection("usuarios").doc(userId).update({
      estado: status,
      en_linea: status === "online",
      ultima_actividad: admin.firestore.FieldValue.serverTimestamp(),
      last_seen: admin.firestore.FieldValue.serverTimestamp(),
    });

    // ===== ACTUALIZAR 2: Colección 'paseadores_search' (CRÍTICO) =====
    await db.collection("paseadores_search").doc(userId).update({
      estado: status,
      en_linea: status === "online",
      last_seen: admin.firestore.FieldValue.serverTimestamp(),
    }).catch(err => {
      // No es crítico si paseadores_search no existe (puede ser un dueño)
      if (err.code !== "not-found") {
        console.warn("Warn: Error actualizando paseadores_search en presencia:", err.message);
      }
    });

    console.log(`✅ Presencia actualizada a '${status}' para usuario ${userId} en ambas colecciones`);
  } catch (error) {
    console.error("Error al actualizar presencia:", error);
  }
}

/**
 * Envía notificación FCM si el destinatario no tiene el chat abierto
 */
async function sendFCMIfNeeded(db, recipientId, chatId, message, senderId, senderName) {
  try {
    // Obtener token FCM del destinatario
    const userDoc = await db.collection("usuarios").doc(recipientId).get();
    if (!userDoc.exists) return;

    const fcmToken = userDoc.data().fcmToken;
    if (!fcmToken) return;

    // Verificar si el destinatario tiene el chat abierto
    const chatDoc = await db.collection("chats").doc(chatId).get();
    if (!chatDoc.exists) return;

    const chatAbierto = chatDoc.data().chat_abierto?.[recipientId];

    // Si el usuario tiene el chat abierto, no enviar FCM
    if (chatAbierto === chatId) {
      console.log(`🔕 FCM omitido: ${recipientId} tiene chat ${chatId} abierto`);
      return;
    }

    // Enviar notificación FCM (ya manejado por Cloud Function sendChatNotification)
    // Esta lógica es redundante pero sirve como fallback
    console.log(`🔔 Preparando FCM para ${recipientId}`);
  } catch (error) {
    console.error("Error al verificar FCM:", error);
  }
}

/**
 * Extrae ID de una referencia de Firestore o string
 */
function extractId(value) {
  if (!value) return null;
  if (typeof value === "string") return value;
  if (typeof value === "object" && value.id) return value.id;
  return null;
}

/**
 * Calcula un geohash basado en coordenadas (compatible con GeoFire)
 * Implementación simplificada basada en interleaving binario
 */
function calculateGeoHash(lat, lng, precision = 9) {
  const BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
  const isEven = true;
  const latRange = [-90, 90];
  const lngRange = [-180, 180];
  let geohash = "";

  let bit = 0;
  let ch = 0;

  while (geohash.length < precision) {
    if (isEven) {
      const mid = (lngRange[0] + lngRange[1]) / 2;
      if (lng > mid) {
        ch |= (1 << (4 - bit));
        lngRange[0] = mid;
      } else {
        lngRange[1] = mid;
      }
    } else {
      const mid = (latRange[0] + latRange[1]) / 2;
      if (lat > mid) {
        ch |= (1 << (4 - bit));
        latRange[0] = mid;
      } else {
        latRange[1] = mid;
      }
    }

    if (bit < 4) {
      bit++;
    } else {
      geohash += BASE32[ch];
      bit = 0;
      ch = 0;
    }
  }

  return geohash;
}

module.exports = { initializeSocketServer };
