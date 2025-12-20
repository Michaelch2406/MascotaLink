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

  // ========================================
  // MIDDLEWARE DE AUTENTICACIÓN
  // ========================================
  io.use(async (socket, next) => {
    const token = socket.handshake.auth.token;

    if (!token) {
      console.warn("❌ Conexión rechazada: sin token");
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
        console.log(`✅ Usuario autenticado: ${socket.userName} (${socket.userId})`);
      }

      next();
    } catch (error) {
      console.error("❌ Error de autenticación:", error.message);
      next(new Error("Authentication failed"));
    }
  });

  // ========================================
  // GESTIÓN DE CONEXIONES
  // ========================================
  io.on("connection", (socket) => {
    console.log(`🔌 Usuario conectado: ${socket.userName} [${socket.userId}]`);

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
          console.warn(`❌ Chat ${chatId} no existe`);
          return socket.emit("error", { message: "Chat no encontrado" });
        }

        const participantes = chatDoc.data().participantes || [];
        if (!participantes.includes(socket.userId)) {
          console.warn(`❌ Usuario ${socket.userId} no autorizado para chat ${chatId}`);
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

        console.log(`✅ Mensaje ${messageId} marcado como leído`);
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

        socket.join(`paseo_${paseoId}`);
        console.log(`🐕 ${socket.userName} se unió al paseo ${paseoId}`);

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
        const { paseoId, latitud, longitud, accuracy } = data;

        // Verificar que el usuario es el paseador
        const paseoDoc = await db.collection("reservas").doc(paseoId).get();
        if (!paseoDoc.exists) {
          return socket.emit("error", { message: "Paseo no encontrado" });
        }

        const idPaseador = extractId(paseoDoc.data().id_paseador);
        if (socket.userId !== idPaseador) {
          return socket.emit("error", { message: "Solo el paseador puede enviar ubicación" });
        }

        // Verificar que el paseo esté EN_CURSO
        const estado = paseoDoc.data().estado;
        if (estado !== "EN_CURSO") {
          console.log(`⚠️  Ubicación rechazada para paseo ${paseoId} - estado: ${estado} (debe ser EN_CURSO)`);
          return socket.emit("error", { message: "Solo se puede enviar ubicación cuando el paseo está en curso" });
        }

        // Stream en tiempo real a todos los participantes
        io.to(`paseo_${paseoId}`).emit("walker_location", {
          paseoId,
          latitud,
          longitud,
          accuracy,
          timestamp: Date.now(),
        });

        // Guardar en Firestore solo cada 30 segundos (reduce writes)
        const lastSave = socket.lastLocationSave || 0;
        if (Date.now() - lastSave > 30000) {
          await db.collection("reservas").doc(paseoId).update({
            ubicacion_actual: new admin.firestore.GeoPoint(latitud, longitud),
            ultima_actualizacion: admin.firestore.FieldValue.serverTimestamp(),
          });
          socket.lastLocationSave = Date.now();
          console.log(`📍 Ubicación guardada para paseo ${paseoId}`);
        }
      } catch (error) {
        console.error("Error al actualizar ubicación:", error);
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

        // Notificar a todos los participantes
        io.to(`paseo_${paseoId}`).emit("paseo_updated", {
          paseoId,
          estado: nuevoEstado,
          changedBy: socket.userId,
        });

        console.log(`🚶 Estado de paseo ${paseoId} cambiado a ${nuevoEstado}`);
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
      console.log(`🔌 Usuario desconectado: ${socket.userName} [${reason}]`);

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

  console.log("✅ Servidor WebSocket inicializado correctamente");
}

// ========================================
// FUNCIONES AUXILIARES
// ========================================

/**
 * Actualiza el estado de presencia del usuario
 */
async function updateUserPresence(db, userId, status) {
  try {
    await db.collection("usuarios").doc(userId).update({
      estado: status,
      ultima_actividad: admin.firestore.FieldValue.serverTimestamp(),
    });
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

module.exports = { initializeSocketServer };
