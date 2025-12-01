package com.mjc.mascotalink;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class NotificationReplyReceiver extends BroadcastReceiver {

    private static final String TAG = "NotifReplyReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput == null) {
            return;
        }

        CharSequence replyText = remoteInput.getCharSequence("key_text_reply");
        if (replyText == null) {
            return;
        }

        String chatId = intent.getStringExtra("chat_id");
        String otherUserId = intent.getStringExtra("id_otro_usuario");
        int notificationId = intent.getIntExtra("notification_id", 0);
        
        // Procesar el mensaje
        sendReply(context, chatId, otherUserId, replyText.toString(), notificationId);
    }

    private void sendReply(Context context, String chatId, String otherUserId, String text, int notificationId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String currentUserId = user.getUid();

        Map<String, Object> mensaje = new HashMap<>();
        mensaje.put("id_remitente", currentUserId);
        mensaje.put("id_destinatario", otherUserId);
        mensaje.put("texto", text);
        mensaje.put("timestamp", FieldValue.serverTimestamp());
        mensaje.put("leido", false);
        mensaje.put("entregado", true);
        mensaje.put("tipo", "texto");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 7);
        mensaje.put("fecha_eliminacion", new Timestamp(cal.getTime()));

        db.collection("chats").document(chatId)
                .collection("mensajes")
                .add(mensaje)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "Reply sent: " + docRef.getId());
                    
                    // Update Chat Metadata
                    Map<String, Object> chatUpdate = new HashMap<>();
                    chatUpdate.put("ultimo_mensaje", text);
                    chatUpdate.put("ultimo_timestamp", FieldValue.serverTimestamp());
                    chatUpdate.put("mensajes_no_leidos." + otherUserId, FieldValue.increment(1));
                    
                    db.collection("chats").document(chatId).update(chatUpdate);

                    // Actualizar la notificación para mostrar que se respondió
                    updateNotification(context, notificationId, text);
                    
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending reply", e);
                    Toast.makeText(context, "Error al enviar respuesta", Toast.LENGTH_SHORT).show();
                });
    }
    
    private void updateNotification(Context context, int notificationId, String replyText) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
            for (StatusBarNotification sbn : activeNotifications) {
                if (sbn.getId() == notificationId) {
                    Notification notification = sbn.getNotification();
                    NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
                    
                    if (style != null) {
                        // Añadir el mensaje del usuario actual (Yo)
                        Person me = new Person.Builder().setName("Tú").build();
                        style.addMessage(replyText, System.currentTimeMillis(), me);
                        
                        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, notification.getChannelId())
                                .setSmallIcon(R.drawable.walki_logo_secundario)
                                .setStyle(style)
                                .setOnlyAlertOnce(true) // No sonar/vibrar de nuevo
                                .setAutoCancel(true);

                        // Importante: volver a poner el PendingIntent de contenido original
                        // y las acciones si queremos que sigan disponibles
                        builder.setContentIntent(notification.contentIntent);
                        
                        // Re-add actions if needed (optional, but Reply button might be nice to keep)
                        if (notification.actions != null) {
                            for (Notification.Action action : notification.actions) {
                                // Reconstruir acciones es complejo, para simplificar en MVP
                                // a veces se dejan o se quitan. 
                                // Si no las añadimos, el usuario no podrá responder de nuevo inmediatamente
                                // desde la misma notificación actualizada.
                                // Intentaremos usar NotificationCompat.Action.Builder si fuera necesario,
                                // pero extraerlas de Notification.Action raw es tedioso.
                                // Vamos a simplificar: actualizamos el historial y quitamos el spinner.
                                // Si el usuario quiere responder de nuevo, tendrá que esperar otro mensaje
                                // o abrir la app.
                            }
                        }
                        
                        notificationManager.notify(notificationId, builder.build());
                    } else {
                         // Si no se pudo extraer estilo, cancelar
                         notificationManager.cancel(notificationId);
                    }
                    break;
                }
            }
        } else {
            notificationManager.cancel(notificationId);
        }
    }
}
