package com.mjc.mascotalink.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.ChatActivity;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad centralizada para abrir o crear chats evitando duplicación de lógica.
 */
public final class ChatHelper {

    private static final String TAG = "ChatHelper";

    private ChatHelper() {}

    public static void openOrCreateChat(Activity activity,
                                        FirebaseFirestore db,
                                        String currentUserId,
                                        String otherUserId) {
        if (activity == null || db == null) return;
        if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(otherUserId)) {
            Toast.makeText(activity, "Falta información del usuario para chatear", Toast.LENGTH_SHORT).show();
            return;
        }

        String chatId = generarChatId(currentUserId, otherUserId);
        DocumentReference chatRef = db.collection("chats").document(chatId);

        chatRef.get().addOnSuccessListener(doc -> {
            if (doc != null && doc.exists()) {
                lanzarChat(activity, chatId, otherUserId);
            } else {
                crearChat(activity, db, chatRef, chatId, currentUserId, otherUserId);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "No se pudo abrir chat", e);
            Toast.makeText(activity, "No se pudo abrir el chat", Toast.LENGTH_SHORT).show();
        });
    }

    private static void crearChat(Activity activity,
                                  FirebaseFirestore db,
                                  DocumentReference chatRef,
                                  String chatId,
                                  String currentUserId,
                                  String otherUserId) {
        Map<String, Object> chat = new HashMap<>();
        chat.put("participantes", java.util.Arrays.asList(currentUserId, otherUserId));
        chat.put("fecha_creacion", FieldValue.serverTimestamp());
        chat.put("ultimo_mensaje", "");
        chat.put("ultimo_timestamp", FieldValue.serverTimestamp());

        Map<String, Object> mensajesNoLeidos = new HashMap<>();
        mensajesNoLeidos.put(currentUserId, 0);
        mensajesNoLeidos.put(otherUserId, 0);
        chat.put("mensajes_no_leidos", mensajesNoLeidos);

        Map<String, Object> estadoUsuarios = new HashMap<>();
        estadoUsuarios.put(currentUserId, "offline");
        estadoUsuarios.put(otherUserId, "offline");
        chat.put("estado_usuarios", estadoUsuarios);

        chatRef.set(chat).addOnSuccessListener(unused -> lanzarChat(activity, chatId, otherUserId))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "No se pudo crear chat", e);
                    Toast.makeText(activity, "No se pudo crear la conversación", Toast.LENGTH_SHORT).show();
                });
    }

    private static void lanzarChat(Activity activity, String chatId, String otherUserId) {
        Intent intent = new Intent(activity, ChatActivity.class);
        intent.putExtra("chat_id", chatId);
        intent.putExtra("id_otro_usuario", otherUserId);
        activity.startActivity(intent);
    }

    private static String generarChatId(String u1, String u2) {
        return u1.compareTo(u2) < 0 ? u1 + "_" + u2 : u2 + "_" + u1;
    }
}
