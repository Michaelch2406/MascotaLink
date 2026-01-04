const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { db, admin } = require('../config/firebase');

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
        tag: `chat_${chatId}` // Agrupa mensajes del mismo chat
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
