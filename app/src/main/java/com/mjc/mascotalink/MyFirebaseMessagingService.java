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

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

import com.mjc.mascotalink.PaseosActivity;
import com.mjc.mascotalink.SolicitudesActivity;
import com.mjc.mascotalink.SolicitudDetalleActivity;
import com.mjc.mascotalink.PaseoEnCursoActivity;
import com.mjc.mascotalink.ConfirmarPagoActivity;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";
    public static final String CHANNEL_ID_PAYMENTS = "payment_channel";
    public static final String CHANNEL_NAME_PAYMENTS = "Confirmaciones de Pago";
    public static final String CHANNEL_DESCRIPTION_PAYMENTS = "Notificaciones sobre confirmaciones de pagos de paseos.";

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
            // Handle data payload messages here.
            handleDataMessage(remoteMessage);
        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
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
        String title = remoteMessage.getData().get("title");
        String message = remoteMessage.getData().get("message");
        sendNotification(title, message, remoteMessage.getData());
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     */
    private void sendNotification(String title, String messageBody, java.util.Map<String, String> data) {
        Intent intent = null;
        String clickAction = data.get("click_action");

        if (clickAction != null) {
            switch (clickAction) {
                case "OPEN_WALKS_ACTIVITY":
                    intent = new Intent(this, PaseosActivity.class);
                    break;
                case "OPEN_REQUESTS_ACTIVITY":
                    intent = new Intent(this, SolicitudesActivity.class);
                    break;
                case "OPEN_REQUEST_DETAILS":
                    intent = new Intent(this, SolicitudDetalleActivity.class);
                    if (data.containsKey("reservaId")) {
                        intent.putExtra("id_reserva", data.get("reservaId"));
                    }
                    break;
                case "OPEN_PAYMENT_CONFIRMATION":
                    intent = new Intent(this, ConfirmarPagoActivity.class);
                    if (data.containsKey("reservaId")) {
                        intent.putExtra("reserva_id", data.get("reservaId"));
                    }
                    break;
                case "OPEN_CURRENT_WALK_ACTIVITY":
                    intent = new Intent(this, PaseoEnCursoActivity.class);
                    if (data.containsKey("reservaId")) {
                        intent.putExtra("id_reserva", data.get("reservaId"));
                    }
                    break;
                default:
                    intent = new Intent(this, MainActivity.class);
                    break;
            }
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        String channelId = CHANNEL_ID_PAYMENTS;
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.walki_logo_secundario)
                        .setContentTitle(title != null ? title : "Walki")
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel(notificationManager, channelId, CHANNEL_NAME_PAYMENTS, CHANNEL_DESCRIPTION_PAYMENTS);

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());
    }

    private void createNotificationChannel(NotificationManager notificationManager, String channelId, String channelName, String channelDescription) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(channelDescription);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void sendRegistrationToServer(String token) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            Map<String, Object> tokenMap = new HashMap<>();
            tokenMap.put("fcmToken", token);

            db.collection("usuarios").document(userId)
                    .update(tokenMap)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token successfully updated for user: " + userId))
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating FCM token for user: " + userId, e));
        } else {
            Log.d(TAG, "No user logged in, cannot save FCM token to Firestore.");
        }
    }
}