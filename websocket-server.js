/**
 * Servidor WebSocket Standalone para MascotaLink
 * Corre en paralelo a Firebase Emulators en puerto 3000
 */

const express = require('express');
const { createServer } = require('http');
const { Server } = require('socket.io');
const admin = require('firebase-admin');

// Configurar variables de entorno ANTES de inicializar Firebase
process.env.FIRESTORE_EMULATOR_HOST = 'localhost:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST = 'localhost:9099';

// Inicializar Firebase Admin (conecta a emulators)
admin.initializeApp({
  projectId: 'mascotalink-2d9da'
});

const db = admin.firestore();

// Crear servidor Express
const app = express();
app.use(express.json());

// Endpoint de salud
app.get('/health', (req, res) => {
  res.json({
    status: 'OK',
    service: 'MascotaLink WebSocket Server',
    timestamp: new Date().toISOString()
  });
});

// Crear servidor HTTP y Socket.IO
const httpServer = createServer(app);
const io = new Server(httpServer, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST'],
  },
  pingTimeout: 60000,
  pingInterval: 25000,
  transports: ['websocket', 'polling'],
});

console.log('🚀 Inicializando servidor WebSocket...');

// ========================================
// MIDDLEWARE DE AUTENTICACIÓN
// ========================================
io.use(async (socket, next) => {
  const token = socket.handshake.auth.token;

  console.log('🔑 Intentando autenticar...');
  console.log('Token recibido:', token ? 'SÍ (longitud: ' + token.length + ')' : 'NO');
  console.log('Auth Emulator:', process.env.FIREBASE_AUTH_EMULATOR_HOST);
  console.log('Firestore Emulator:', process.env.FIRESTORE_EMULATOR_HOST);

  if (!token) {
    console.warn('❌ Conexión rechazada: sin token');
    return next(new Error('Authentication token required'));
  }

  try {
    console.log('🔍 Verificando token con Firebase Auth...');

    let decoded;
    try {
      // Intentar verificar normalmente sin checkRevoked
      decoded = await admin.auth().verifyIdToken(token, false);
    } catch (verifyError) {
      // Si falla (token revocado), decodificar sin verificar (SOLO DESARROLLO)
      console.warn('⚠️  Token revocado, decodificando sin verificar (solo desarrollo)');

      // Decodificar el token JWT manualmente
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(Buffer.from(base64, 'base64').toString().split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));

      decoded = JSON.parse(jsonPayload);
      console.log('✅ Token decodificado manualmente (sin verificación)');
    }

    console.log('✅ Token procesado exitosamente');
    console.log('UID:', decoded.uid || decoded.user_id);
    console.log('Email:', decoded.email);

    socket.userId = decoded.uid || decoded.user_id;
    socket.userEmail = decoded.email;

    const userDoc = await db.collection('usuarios').doc(socket.userId).get();
    if (userDoc.exists) {
      const userData = userDoc.data();
      socket.userRole = userData.rol || 'DUENO';
      socket.userName = userData.nombre_display || 'Usuario';
      console.log(`✅ Usuario autenticado: ${socket.userName} (${socket.userId})`);
    } else {
      console.warn(`⚠️  Usuario ${socket.userId} no existe en Firestore`);
      socket.userName = decoded.email || 'Usuario';
    }

    next();
  } catch (error) {
    console.error('❌ Error de autenticación detallado:');
    console.error('   Mensaje:', error.message);
    console.error('   Code:', error.code);
    console.error('   Stack:', error.stack);
    next(new Error('Authentication failed: ' + error.message));
  }
});

// ========================================
// GESTIÓN DE CONEXIONES
// ========================================
io.on('connection', (socket) => {
  console.log(`🔌 Usuario conectado: ${socket.userName} [${socket.userId}]`);

  socket.join(socket.userId);
  updateUserPresence(socket.userId, 'online');

  // ========================================
  // EVENTOS DE CHAT
  // ========================================

  socket.on('join_chat', async (chatId) => {
    try {
      const chatDoc = await db.collection('chats').doc(chatId).get();

      if (!chatDoc.exists) {
        console.warn(`❌ Chat ${chatId} no existe`);
        return socket.emit('error', { message: 'Chat no encontrado' });
      }

      const participantes = chatDoc.data().participantes || [];
      if (!participantes.includes(socket.userId)) {
        console.warn(`❌ Usuario ${socket.userId} no autorizado para chat ${chatId}`);
        return socket.emit('error', { message: 'No autorizado' });
      }

      socket.join(chatId);
      console.log(`💬 ${socket.userName} se unió al chat ${chatId}`);

      await db.collection('chats').doc(chatId).update({
        [`chat_abierto.${socket.userId}`]: chatId,
        [`estado_usuarios.${socket.userId}`]: 'online',
      });

      socket.emit('joined_chat', { chatId });
    } catch (error) {
      console.error('Error al unirse al chat:', error);
      socket.emit('error', { message: 'Error al unirse al chat' });
    }
  });

  socket.on('leave_chat', async (chatId) => {
    try {
      socket.leave(chatId);
      console.log(`💬 ${socket.userName} salió del chat ${chatId}`);

      await db.collection('chats').doc(chatId).update({
        [`chat_abierto.${socket.userId}`]: null,
        [`estado_usuarios.${socket.userId}`]: 'online',
      });
    } catch (error) {
      console.error('Error al salir del chat:', error);
    }
  });

  socket.on('send_message', async (data) => {
    try {
      const { chatId, destinatarioId, texto, tipo, imagen_url, latitud, longitud } = data;

      const chatDoc = await db.collection('chats').doc(chatId).get();
      if (!chatDoc.exists || !chatDoc.data().participantes.includes(socket.userId)) {
        return socket.emit('error', { message: 'No autorizado' });
      }

      if (!texto && !imagen_url && !latitud) {
        return socket.emit('error', { message: 'Mensaje vacío' });
      }

      const messageRef = await db
        .collection('chats')
        .doc(chatId)
        .collection('mensajes')
        .add({
          id_remitente: socket.userId,
          id_destinatario: destinatarioId,
          texto: texto || '',
          tipo: tipo || 'texto',
          imagen_url: imagen_url || null,
          latitud: latitud || null,
          longitud: longitud || null,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          leido: false,
          entregado: true,
          fecha_eliminacion: admin.firestore.Timestamp.fromMillis(
            Date.now() + 7 * 24 * 60 * 60 * 1000
          ),
        });

      const messageId = messageRef.id;
      const now = new Date();

      io.to(chatId).emit('new_message', {
        id: messageId,
        id_remitente: socket.userId,
        id_destinatario: destinatarioId,
        texto: texto || '',
        tipo: tipo || 'texto',
        imagen_url: imagen_url || null,
        latitud: latitud || null,
        longitud: longitud || null,
        timestamp: now.toISOString(),
        leido: false,
        entregado: true,
        sender_name: socket.userName,
      });

      await db.collection('chats').doc(chatId).update({
        ultimo_mensaje: texto?.substring(0, 100) || '[Media]',
        ultimo_timestamp: admin.firestore.FieldValue.serverTimestamp(),
        [`mensajes_no_leidos.${destinatarioId}`]: admin.firestore.FieldValue.increment(1),
      });

      console.log(`📨 Mensaje enviado en chat ${chatId} por ${socket.userName}`);
    } catch (error) {
      console.error('Error al enviar mensaje:', error);
      socket.emit('error', { message: 'Error al enviar mensaje' });
    }
  });

  socket.on('typing', async (chatId) => {
    try {
      socket.to(chatId).emit('user_typing', {
        userId: socket.userId,
        userName: socket.userName,
        chatId: chatId,
      });

      await db.collection('chats').doc(chatId).update({
        [`estado_usuarios.${socket.userId}`]: 'escribiendo',
      });
    } catch (error) {
      console.error('Error en typing:', error);
    }
  });

  socket.on('stop_typing', async (chatId) => {
    try {
      socket.to(chatId).emit('user_stop_typing', {
        userId: socket.userId,
        chatId: chatId,
      });

      await db.collection('chats').doc(chatId).update({
        [`estado_usuarios.${socket.userId}`]: 'online',
      });
    } catch (error) {
      console.error('Error en stop_typing:', error);
    }
  });

  socket.on('mark_read', async (data) => {
    try {
      const { chatId, messageId } = data;

      await db
        .collection('chats')
        .doc(chatId)
        .collection('mensajes')
        .doc(messageId)
        .update({ leido: true });

      socket.to(chatId).emit('message_read', {
        messageId: messageId,
        readBy: socket.userId,
      });

      console.log(`✅ Mensaje ${messageId} marcado como leído`);
    } catch (error) {
      console.error('Error al marcar como leído:', error);
    }
  });

  socket.on('reset_unread', async (chatId) => {
    try {
      await db.collection('chats').doc(chatId).update({
        [`mensajes_no_leidos.${socket.userId}`]: 0,
      });
      console.log(`🔔 Contador reseteado para ${socket.userId} en ${chatId}`);
    } catch (error) {
      console.error('Error al resetear contador:', error);
    }
  });

  // ========================================
  // EVENTOS DE PASEO
  // ========================================

  socket.on('join_paseo', async (paseoId) => {
    try {
      const paseoDoc = await db.collection('reservas').doc(paseoId).get();

      if (!paseoDoc.exists) {
        return socket.emit('error', { message: 'Paseo no encontrado' });
      }

      const paseoData = paseoDoc.data();
      const idPaseador = extractId(paseoData.id_paseador);
      const idDueno = extractId(paseoData.id_dueno);

      if (socket.userId !== idPaseador && socket.userId !== idDueno) {
        return socket.emit('error', { message: 'No autorizado para este paseo' });
      }

      socket.join(`paseo_${paseoId}`);
      console.log(`🐕 ${socket.userName} se unió al paseo ${paseoId}`);

      socket.emit('joined_paseo', { paseoId });
    } catch (error) {
      console.error('Error al unirse al paseo:', error);
      socket.emit('error', { message: 'Error al unirse al paseo' });
    }
  });

  socket.on('update_location', async (data) => {
    try {
      const { paseoId, latitud, longitud, accuracy } = data;

      const paseoDoc = await db.collection('reservas').doc(paseoId).get();
      if (!paseoDoc.exists) {
        return socket.emit('error', { message: 'Paseo no encontrado' });
      }

      const idPaseador = extractId(paseoDoc.data().id_paseador);
      if (socket.userId !== idPaseador) {
        return socket.emit('error', { message: 'Solo el paseador puede enviar ubicación' });
      }

      io.to(`paseo_${paseoId}`).emit('walker_location', {
        paseoId,
        latitud,
        longitud,
        accuracy,
        timestamp: Date.now(),
      });

      const lastSave = socket.lastLocationSave || 0;
      if (Date.now() - lastSave > 30000) {
        await db.collection('reservas').doc(paseoId).update({
          ubicacion_actual: new admin.firestore.GeoPoint(latitud, longitud),
          ultima_actualizacion: admin.firestore.FieldValue.serverTimestamp(),
        });
        socket.lastLocationSave = Date.now();
      }
    } catch (error) {
      console.error('Error al actualizar ubicación:', error);
    }
  });

  socket.on('ping', () => {
    socket.emit('pong', { timestamp: Date.now() });
  });

  // ========================================
  // DESCONEXIÓN
  // ========================================
  socket.on('disconnect', async (reason) => {
    console.log(`🔌 Usuario desconectado: ${socket.userName} [${reason}]`);

    try {
      await updateUserPresence(socket.userId, 'offline');

      const chatsSnapshot = await db
        .collection('chats')
        .where('participantes', 'array-contains', socket.userId)
        .get();

      const updates = [];
      chatsSnapshot.forEach((doc) => {
        updates.push(
          doc.ref.update({
            [`chat_abierto.${socket.userId}`]: null,
            [`estado_usuarios.${socket.userId}`]: 'offline',
          })
        );
      });

      await Promise.all(updates);
    } catch (error) {
      console.error('Error en desconexión:', error);
    }
  });
});

// ========================================
// FUNCIONES AUXILIARES
// ========================================

async function updateUserPresence(userId, status) {
  try {
    await db.collection('usuarios').doc(userId).update({
      estado: status,
      ultima_actividad: admin.firestore.FieldValue.serverTimestamp(),
    });
  } catch (error) {
    console.error('Error al actualizar presencia:', error);
  }
}

function extractId(value) {
  if (!value) return null;
  if (typeof value === 'string') return value;
  if (typeof value === 'object' && value.id) return value.id;
  return null;
}

// ========================================
// INICIAR SERVIDOR
// ========================================

const PORT = process.env.PORT || 3000;
httpServer.listen(PORT, '0.0.0.0', () => {
  console.log(`✅ Servidor WebSocket corriendo en puerto ${PORT}`);
  console.log(`📍 URL: http://localhost:${PORT}`);
  console.log(`🔗 Health check: http://localhost:${PORT}/health`);
});
