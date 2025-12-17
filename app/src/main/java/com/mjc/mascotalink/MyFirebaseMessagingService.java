package com.mjc.mascotalink;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import com.bumptech.glide.Glide;
import android.graphics.Bitmap;
import com.mjc.mascotalink.MyApplication;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject; // Add this import

import dagger.hilt.android.AndroidEntryPoint; // Add this import

import com.mjc.mascotalink.PaseosActivity;
import com.mjc.mascotalink.SolicitudesActivity;
import com.mjc.mascotalink.SolicitudDetalleActivity;
import com.mjc.mascotalink.PaseoEnCursoActivity;
import com.mjc.mascotalink.PaseoEnCursoDuenoActivity; // Add import
import com.mjc.mascotalink.ConfirmarPagoActivity;

@AndroidEntryPoint // Add this annotation
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";
    public static final String CHANNEL_ID_PAYMENTS = "payment_channel";
    public static final String CHANNEL_NAME_PAYMENTS = "Confirmaciones de Pago";
    public static final String CHANNEL_DESCRIPTION_PAYMENTS = "Notificaciones sobre confirmaciones de pagos de paseos.";
    
    public static final String CHANNEL_ID_MESSAGES = "messages_channel";
    public static final String CHANNEL_NAME_MESSAGES = "Mensajes";
    public static final String CHANNEL_DESCRIPTION_MESSAGES = "Notificaciones de mensajes de chat.";

    public static final String CHANNEL_ID_WALKS = "paseos_channel";
    public static final String CHANNEL_NAME_WALKS = "Recordatorios de Paseo";
    public static final String CHANNEL_DESCRIPTION_WALKS = "Alertas sobre inicio, retrasos y recordatorios de paseos.";
    
    private static final String GROUP_KEY_MESSAGES = "com.mjc.mascotalink.MESSAGES";
    private static int messageNotificationId = 1000;

    @Inject // Inject FirebaseAuth
    FirebaseAuth auth;
    @Inject // Inject FirebaseFirestore
    FirebaseFirestore db;

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            // Handle data payload messages here. If data exists, avoid also notifying via the notification payload to prevent duplicates.
            handleDataMessage(remoteMessage);
        }

        // Check if message contains a notification payload.
        else if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            sendNotification(remoteMessage.getNotification().getTitle(), remoteMessage.getNotification().getBody(), remoteMessage.getData());
        }
    }

    /**
     * Called if FCM registration token is updated.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed token: " + token);
        sendRegistrationToServer(token);
    }

    private void handleDataMessage(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        String title = data.get("title");
        String message = data.get("message");

        // Fallback to notification payload if data doesn't have title/message
        if (remoteMessage.getNotification() != null) {
            if (title == null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (message == null) {
                message = remoteMessage.getNotification().getBody();
            }
        }
        
        // Mark message as delivered if it's a chat message
        if (data.containsKey("chat_id") && data.containsKey("message_id")) {
            String chatId = data.get("chat_id");
            String messageId = data.get("message_id");
            if (chatId != null && messageId != null) {
                db.collection("chats").document(chatId) // Use injected db
                        .collection("mensajes").document(messageId)
                        .update("entregado", true)
                        .addOnFailureListener(e -> Log.e(TAG, "Error marking message as delivered", e));
            }
        }

        sendNotification(title, message, data);
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     */
    private void sendNotification(String title, String messageBody, java.util.Map<String, String> data) {
        // Verificar preferencias de notificación antes de mostrar
        com.mjc.mascotalink.utils.NotificacionesPreferences prefs = new com.mjc.mascotalink.utils.NotificacionesPreferences(this);
        if (!prefs.isNotificacionesEnabled()) {
            Log.d(TAG, "Notificaciones deshabilitadas globalmente.");
            return;
        }

        boolean isChatMessage = data != null && data.containsKey("chat_id") && data.containsKey("id_otro_usuario");
        if (isChatMessage && !prefs.isMensajesEnabled()) {
            Log.d(TAG, "Notificaciones de mensajes deshabilitadas.");
            return;
        }

        // Check if the chat is currently open to suppress notification
        if (data != null && data.containsKey("chat_id")) {
            String chatId = data.get("chat_id");
            if (chatId != null && chatId.equals(ChatActivity.currentChatId)) {
                Log.d(TAG, "Suppressing notification for open chat: " + chatId);
                return;
            }
        }

        Intent intent = null;
        String clickAction = data.get("click_action");

        // Manejar notificaciones de chat específicamente
        if (data != null && data.containsKey("chat_id") && data.containsKey("id_otro_usuario")) {
            // Notificación de mensaje de chat - ir directamente a ChatActivity
            intent = new Intent(this, ChatActivity.class);
            intent.putExtra("chat_id", data.get("chat_id"));
            intent.putExtra("id_otro_usuario", data.get("id_otro_usuario"));
        } else if (clickAction != null) {
            switch (clickAction) {
                case "OPEN_WALKS_ACTIVITY":
                    intent = new Intent(this, PaseosActivity.class);
                    break;
                case "OPEN_REQUESTS_ACTIVITY":
                    intent = new Intent(this, SolicitudesActivity.class);
                    break;
                case "OPEN_REQUEST_DETAILS":
                    intent = new Intent(this, SolicitudDetalleActivity.class);
                    break;
                case "OPEN_PAYMENT_CONFIRMATION":
                    intent = new Intent(this, ConfirmarPagoActivity.class);
                    break;
                case "OPEN_CURRENT_WALK_ACTIVITY":
                    intent = new Intent(this, PaseoEnCursoActivity.class);
                    break;
                case "OPEN_CURRENT_WALK_OWNER":
                    intent = new Intent(this, PaseoEnCursoDuenoActivity.class);
                    break;
                default:
                    intent = new Intent(this, MainActivity.class);
                    break;
            }
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        // Pass all data keys to the intent
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }

            // Manually map keys that differ between Cloud Function and Activity expectations
            if (data.containsKey("reservaId")) {
                String reservaId = data.get("reservaId");
                // These activities expect "id_reserva"
                if (intent.getComponent().getClassName().equals(SolicitudDetalleActivity.class.getName()) ||
                    intent.getComponent().getClassName().equals(PaseoEnCursoActivity.class.getName()) ||
                    intent.getComponent().getClassName().equals(PaseoEnCursoDuenoActivity.class.getName())) {
                    intent.putExtra("id_reserva", reservaId);
                }
                // This activity expects "reserva_id"
                if (intent.getComponent().getClassName().equals(ConfirmarPagoActivity.class.getName())) {
                    intent.putExtra("reserva_id", reservaId);
                }
            }
        }
        

        int currentNotificationId;
        String chatId = null;
        if (isChatMessage) {
            chatId = data.get("chat_id");
            currentNotificationId = chatId != null ? chatId.hashCode() : messageNotificationId++;
        } else {
            String reservaId = data != null ? data.get("reservaId") : null;
            String tipo = data != null ? data.get("tipo") : null;
            String delay = data != null ? data.get("delay") : null;

            if (reservaId != null) {
                String key = reservaId + "|" + (tipo != null ? tipo : "") + "|" + (delay != null ? delay : "") + "|" + (clickAction != null ? clickAction : "");
                currentNotificationId = key.hashCode();
            } else {
                currentNotificationId = messageNotificationId++;
            }
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction("NOTIF_" + currentNotificationId);
        intent.setData(Uri.parse("mascotalink://notif/" + currentNotificationId));
        PendingIntent pendingIntent = PendingIntent.getActivity(this, currentNotificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Determinar canal basado en el tipo de notificación
        String channelId = CHANNEL_ID_PAYMENTS; // Default
        String channelName = CHANNEL_NAME_PAYMENTS;
        String channelDescription = CHANNEL_DESCRIPTION_PAYMENTS;

        if (isChatMessage) {
            channelId = CHANNEL_ID_MESSAGES;
            channelName = CHANNEL_NAME_MESSAGES;
            channelDescription = CHANNEL_DESCRIPTION_MESSAGES;
        } else {
            // Verificar si es una notificación de paseo
            boolean isWalkNotification = false;
            if (clickAction != null) {
                if (clickAction.equals("OPEN_WALKS_ACTIVITY") || 
                    clickAction.equals("OPEN_REQUESTS_ACTIVITY") || 
                    clickAction.equals("OPEN_CURRENT_WALK_ACTIVITY") || 
                    clickAction.equals("OPEN_CURRENT_WALK_OWNER")) {
                    isWalkNotification = true;
                }
            }
            if (data != null && (data.containsKey("tipo"))) {
                String tipo = data.get("tipo");
                if ("recordatorio_paseo".equals(tipo) || "paseo_retrasado".equals(tipo) || "ventana_inicio".equals(tipo) || "paseo_retrasado_dueno".equals(tipo)) {
                    isWalkNotification = true;
                }
            }

            if (isWalkNotification) {
                channelId = CHANNEL_ID_WALKS;
                channelName = CHANNEL_NAME_WALKS;
                channelDescription = CHANNEL_DESCRIPTION_WALKS;
            }
        }

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.walki_logo_secundario)
                        .setContentTitle(title != null ? title : "Walki")
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);
        
        /*
        int currentNotificationId;
        String chatId = null;
        if (isChatMessage) {
            chatId = data.get("chat_id");
            // Usar el hashCode del chatId para que sea único por conversación pero constante
            currentNotificationId = chatId != null ? chatId.hashCode() : messageNotificationId++;
        } else {
            currentNotificationId = messageNotificationId++;
        }
        */


        // Si es mensaje de chat, agregar respuesta rápida y agrupar
        if (isChatMessage) {
            String otherUserId = data.get("id_otro_usuario");
            
            // Agrupar notificaciones de mensajes
            notificationBuilder.setGroup(GROUP_KEY_MESSAGES);
            
            // Agregar acción de respuesta rápida
            Intent replyIntent = new Intent(this, NotificationReplyReceiver.class);
            replyIntent.putExtra("chat_id", chatId);
            replyIntent.putExtra("id_otro_usuario", otherUserId);
            replyIntent.putExtra("notification_id", currentNotificationId);
            
            // IMPORTANTE: Debe ser MUTABLE para RemoteInput
            PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                this, 
                currentNotificationId, 
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );
            
            // RemoteInput para respuesta rápida
            androidx.core.app.RemoteInput remoteInput = new androidx.core.app.RemoteInput.Builder("key_text_reply")
                    .setLabel("Responder")
                    .build();
            
            NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
                    R.drawable.ic_arrow_forward,
                    "Responder",
                    replyPendingIntent)
                    .addRemoteInput(remoteInput)
                    .build();
            
            notificationBuilder.addAction(replyAction);
            
            // Estilo de mensajería
            String senderName = data.containsKey("sender_name") ? data.get("sender_name") : (title != null ? title : "Usuario");
            String senderPhotoUrl = data.get("sender_photo_url");
            
            Person.Builder personBuilder = new Person.Builder()
                    .setName(senderName);
            
            if (senderPhotoUrl != null && !senderPhotoUrl.isEmpty()) {
                try {
                    Bitmap bitmap = Glide.with(this)
                            .asBitmap()
                            .load(MyApplication.getFixedUrl(senderPhotoUrl))
                            .submit()
                            .get();
                    personBuilder.setIcon(IconCompat.createWithBitmap(bitmap));
                } catch (Exception e) {
                    Log.e(TAG, "Error downloading sender image", e);
                }
            }
            
            Person senderPerson = personBuilder.build();
            
            NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle("Tú")
                    .addMessage(messageBody, System.currentTimeMillis(), senderPerson);
            
            notificationBuilder.setStyle(messagingStyle);
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (isChatMessage) {
            createNotificationChannel(notificationManager, CHANNEL_ID_MESSAGES, CHANNEL_NAME_MESSAGES, CHANNEL_DESCRIPTION_MESSAGES);
            // Usar ID único para cada mensaje pero agruparlos
            notificationManager.notify(currentNotificationId, notificationBuilder.build());
            
            // Crear notificación de resumen del grupo
            NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
                    .setSmallIcon(R.drawable.walki_logo_secundario)
                    .setGroup(GROUP_KEY_MESSAGES)
                    .setGroupSummary(true)
                    .setAutoCancel(true);
            
            notificationManager.notify(0, summaryBuilder.build());
        } else {
            createNotificationChannel(notificationManager, channelId, channelName, channelDescription);
            notificationManager.notify(currentNotificationId, notificationBuilder.build());
        }
    }

    private void createNotificationChannel(NotificationManager notificationManager, String channelId, String channelName, String channelDescription) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(channelDescription);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void sendRegistrationToServer(String token) {
        FirebaseUser currentUser = auth.getCurrentUser(); // Use injected auth
        if (currentUser != null) {
            String userId = currentUser.getUid();
            // FirebaseFirestore db = FirebaseFirestore.getInstance(); // Use injected db

            Map<String, Object> tokenMap = new HashMap<>();
            tokenMap.put("fcmToken", token);

            db.collection("usuarios").document(userId) // Use injected db
                    .update(tokenMap)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token successfully updated for user: " + userId))
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating FCM token for user: " + userId, e));
        } else {
            Log.d(TAG, "No user logged in, cannot save FCM token to Firestore.");
        }
    }
}
