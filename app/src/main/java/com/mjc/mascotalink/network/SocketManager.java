package com.mjc.mascotalink.network;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * Singleton para gestionar la conexión WebSocket con Socket.IO
 * Se integra con NetworkDetector para manejar IPs dinámicas
 */
public class SocketManager {
    private static final String TAG = "SocketManager";
    private static final int WEBSOCKET_PORT = 3000; // Puerto del servidor WebSocket standalone

    private static SocketManager instance;
    private Socket socket;
    private Context context;
    private boolean isConnected = false;
    private String currentChatId = null;

    // Callbacks registrados
    private Map<String, Emitter.Listener> eventListeners = new HashMap<>();

    private SocketManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized SocketManager getInstance(Context context) {
        if (instance == null) {
            instance = new SocketManager(context);
        }
        return instance;
    }

    /**
     * Conecta al servidor WebSocket usando Firebase Auth token
     */
    public void connect() {
        if (socket != null && socket.connected()) {
            Log.d(TAG, "Socket ya conectado");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "Usuario no autenticado");
            return;
        }

        user.getIdToken(true).addOnSuccessListener(result -> {
            String token = result.getToken();

            // Detectar IP dinámica usando NetworkDetector
            String serverHost = NetworkDetector.detectCurrentHost(context);
            String serverUrl = "http://" + serverHost + ":" + WEBSOCKET_PORT;

            Log.d(TAG, "Conectando a WebSocket: " + serverUrl);

            IO.Options options = new IO.Options();
            options.auth = new HashMap<>();
            ((Map<String, String>) options.auth).put("token", token);
            options.reconnection = true;
            options.reconnectionDelay = 1000;
            options.reconnectionDelayMax = 5000;
            options.reconnectionAttempts = Integer.MAX_VALUE;
            options.timeout = 20000;
            options.transports = new String[]{"websocket", "polling"};

            try {
                socket = IO.socket(serverUrl, options);
                setupBaseEventListeners();
                socket.connect();
            } catch (URISyntaxException e) {
                Log.e(TAG, "URL inválida: " + serverUrl, e);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al obtener token de Firebase", e);
        });
    }

    /**
     * Configura los listeners básicos de conexión
     */
    private void setupBaseEventListeners() {
        socket.on(Socket.EVENT_CONNECT, args -> {
            isConnected = true;
            Log.d(TAG, "✅ Socket conectado");
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            isConnected = false;
            Log.d(TAG, "🔌 Socket desconectado");
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            Log.e(TAG, "❌ Error de conexión: " + (args.length > 0 ? args[0] : "unknown"));
        });

        // Usar string "reconnect" en lugar de constante (no existe en v2.1.0)
        socket.on("reconnect", args -> {
            Log.d(TAG, "🔄 Socket reconectado");
            // Re-unirse al chat si había uno abierto
            if (currentChatId != null) {
                joinChat(currentChatId);
            }
        });

        socket.on("error", args -> {
            if (args.length > 0) {
                try {
                    JSONObject error = (JSONObject) args[0];
                    Log.e(TAG, "Error del servidor: " + error.getString("message"));
                } catch (Exception e) {
                    Log.e(TAG, "Error desconocido del servidor");
                }
            }
        });

        socket.on("pong", args -> {
            Log.v(TAG, "Pong recibido");
        });
    }

    /**
     * Desconecta el socket y limpia recursos
     */
    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.off();
            socket = null;
        }
        isConnected = false;
        currentChatId = null;
        eventListeners.clear();
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    // ========================================
    // EVENTOS DE CHAT
    // ========================================

    /**
     * Unirse a una sala de chat
     */
    public void joinChat(String chatId) {
        if (!isConnected) {
            Log.w(TAG, "Socket no conectado, no se puede unir al chat");
            return;
        }
        currentChatId = chatId;
        socket.emit("join_chat", chatId);
        Log.d(TAG, "📥 Uniéndose al chat: " + chatId);
    }

    /**
     * Salir de una sala de chat
     */
    public void leaveChat(String chatId) {
        if (!isConnected) return;

        socket.emit("leave_chat", chatId);
        if (chatId.equals(currentChatId)) {
            currentChatId = null;
        }
        Log.d(TAG, "📤 Saliendo del chat: " + chatId);
    }

    /**
     * Enviar mensaje de texto
     */
    public void sendMessage(String chatId, String destinatarioId, String texto) {
        sendMessage(chatId, destinatarioId, texto, "texto", null, null, null);
    }

    /**
     * Enviar mensaje (texto, imagen o ubicación)
     */
    public void sendMessage(String chatId, String destinatarioId, String texto,
                           String tipo, String imagenUrl, Double latitud, Double longitud) {
        if (!isConnected) {
            Log.w(TAG, "Socket no conectado, no se puede enviar mensaje");
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("chatId", chatId);
            data.put("destinatarioId", destinatarioId);
            data.put("texto", texto != null ? texto : "");
            data.put("tipo", tipo);

            if (imagenUrl != null) data.put("imagen_url", imagenUrl);
            if (latitud != null) data.put("latitud", latitud);
            if (longitud != null) data.put("longitud", longitud);

            socket.emit("send_message", data);
            Log.d(TAG, "📨 Mensaje enviado vía WebSocket");
        } catch (JSONException e) {
            Log.e(TAG, "Error al crear JSON del mensaje", e);
        }
    }

    /**
     * Notificar que el usuario está escribiendo
     */
    public void sendTyping(String chatId) {
        if (!isConnected) return;
        socket.emit("typing", chatId);
    }

    /**
     * Notificar que el usuario dejó de escribir
     */
    public void sendStopTyping(String chatId) {
        if (!isConnected) return;
        socket.emit("stop_typing", chatId);
    }

    /**
     * Marcar mensaje como leído
     */
    public void markMessageRead(String chatId, String messageId) {
        if (!isConnected) return;

        try {
            JSONObject data = new JSONObject();
            data.put("chatId", chatId);
            data.put("messageId", messageId);
            socket.emit("mark_read", data);
        } catch (JSONException e) {
            Log.e(TAG, "Error al marcar como leído", e);
        }
    }

    /**
     * Resetear contador de mensajes no leídos
     */
    public void resetUnreadCount(String chatId) {
        if (!isConnected) return;
        socket.emit("reset_unread", chatId);
    }

    // ========================================
    // EVENTOS DE PASEO (WALK TRACKING)
    // ========================================

    /**
     * Unirse a tracking de paseo
     */
    public void joinPaseo(String paseoId) {
        if (!isConnected) return;
        socket.emit("join_paseo", paseoId);
        Log.d(TAG, "🐕 Uniéndose al paseo: " + paseoId);
    }

    /**
     * Actualizar ubicación del paseador
     */
    public void updateLocation(String paseoId, double latitud, double longitud, float accuracy) {
        if (!isConnected) return;

        try {
            JSONObject data = new JSONObject();
            data.put("paseoId", paseoId);
            data.put("latitud", latitud);
            data.put("longitud", longitud);
            data.put("accuracy", accuracy);
            socket.emit("update_location", data);
        } catch (JSONException e) {
            Log.e(TAG, "Error al actualizar ubicación", e);
        }
    }

    /**
     * Cambiar estado del paseo
     */
    public void updatePaseoEstado(String paseoId, String nuevoEstado) {
        if (!isConnected) return;

        try {
            JSONObject data = new JSONObject();
            data.put("paseoId", paseoId);
            data.put("nuevoEstado", nuevoEstado);
            socket.emit("paseo_estado_change", data);
        } catch (JSONException e) {
            Log.e(TAG, "Error al cambiar estado de paseo", e);
        }
    }

    // ========================================
    // SISTEMA DE PRESENCIA
    // ========================================

    /**
     * Obtener lista de usuarios online
     */
    public void getOnlineUsers(String[] userIds) {
        if (!isConnected) return;

        try {
            org.json.JSONArray array = new org.json.JSONArray();
            for (String uid : userIds) {
                array.put(uid);
            }
            socket.emit("get_online_users", array);
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener usuarios online", e);
        }
    }

    /**
     * Enviar ping para mantener conexión
     */
    public void ping() {
        if (!isConnected) return;
        socket.emit("ping");
    }

    // ========================================
    // REGISTRO DE LISTENERS
    // ========================================

    /**
     * Registrar listener para un evento
     */
    public void on(String event, Emitter.Listener listener) {
        if (socket == null) return;
        socket.on(event, listener);
        eventListeners.put(event, listener);
        Log.d(TAG, "👂 Listener registrado: " + event);
    }

    /**
     * Remover listener de un evento
     */
    public void off(String event) {
        if (socket == null) return;
        socket.off(event);
        eventListeners.remove(event);
        Log.d(TAG, "🔇 Listener removido: " + event);
    }

    /**
     * Remover todos los listeners
     */
    public void offAll() {
        if (socket == null) return;
        for (String event : eventListeners.keySet()) {
            socket.off(event);
        }
        eventListeners.clear();
        Log.d(TAG, "🔇 Todos los listeners removidos");
    }

    // ========================================
    // INTERFACES DE CALLBACK
    // ========================================

    public interface OnMessageListener {
        void onNewMessage(JSONObject message);
    }

    public interface OnTypingListener {
        void onUserTyping(String userId, String userName);
        void onUserStopTyping(String userId);
    }

    public interface OnLocationListener {
        void onLocationUpdate(double lat, double lng, float accuracy, long timestamp);
    }

    public interface OnPaseoUpdateListener {
        void onPaseoStateChanged(String paseoId, String nuevoEstado, String changedBy);
    }

    public interface OnConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String message);
    }
}
