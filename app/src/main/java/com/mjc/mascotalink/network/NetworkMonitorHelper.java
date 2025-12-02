package com.mjc.mascotalink.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Helper class para monitorear cambios de red y gestionar reconexiones de WebSocket
 * de manera robusta. Implementa throttling y verificación de red.
 *
 * Uso:
 * 1. Crear instancia en onCreate: networkMonitor = new NetworkMonitorHelper(context, socketManager, callback)
 * 2. Registrar: networkMonitor.register()
 * 3. Limpiar: networkMonitor.unregister() en onDestroy
 */
public class NetworkMonitorHelper {

    private static final String TAG = "NetworkMonitorHelper";
    private static final long MIN_RECONNECT_INTERVAL = 5000; // 5 segundos mínimo entre reconexiones
    private static final long NETWORK_VERIFICATION_DELAY = 2000; // 2 segundos para verificar pérdida de red

    private final Context context;
    private final SocketManager socketManager;
    private final NetworkCallback callback;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Control de reconexiones
    private boolean isReconnecting = false;
    private long lastReconnectTime = 0;

    // Control de room WebSocket (chat o paseo)
    private String currentRoomId = null;
    private RoomType roomType = RoomType.CHAT;

    /**
     * Tipo de sala WebSocket
     */
    public enum RoomType {
        CHAT,    // Para ChatActivity (usa joinChat)
        PASEO    // Para PaseoEnCursoActivity (usa joinPaseo)
    }

    /**
     * Callback para notificar a la actividad sobre cambios de red
     */
    public interface NetworkCallback {
        /**
         * Llamado cuando se detecta pérdida de red REAL (después de verificación)
         */
        void onNetworkLost();

        /**
         * Llamado cuando se detecta red disponible
         */
        void onNetworkAvailable();

        /**
         * Llamado después de una reconexión exitosa
         */
        void onReconnected();
    }

    /**
     * Constructor
     *
     * @param context Contexto de la aplicación
     * @param socketManager Instancia de SocketManager
     * @param callback Callback para notificar eventos de red
     */
    public NetworkMonitorHelper(Context context, SocketManager socketManager, NetworkCallback callback) {
        this.context = context.getApplicationContext();
        this.socketManager = socketManager;
        this.callback = callback;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Configura el room actual para reconexión automática
     *
     * @param roomId ID del chat o paseo
     * @param type Tipo de room (CHAT o PASEO)
     */
    public void setCurrentRoom(String roomId, RoomType type) {
        this.currentRoomId = roomId;
        this.roomType = type;
        Log.d(TAG, "Room configurado: " + roomId + " (tipo: " + type + ")");
    }

    /**
     * Registra el monitor de red. Llamar en onCreate o onResume.
     */
    public void register() {
        if (connectivityManager == null) {
            Log.e(TAG, "ConnectivityManager no disponible");
            return;
        }

        if (networkCallback != null) {
            Log.w(TAG, "NetworkCallback ya registrado, ignorando...");
            return;
        }

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "🌐 Red disponible: " + network);

                // Notificar a la actividad
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onNetworkAvailable());
                }

                // Esperar 3 segundos para que la red se estabilice antes de reconectar
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    reconnectWebSocket();
                }, 3000);
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.w(TAG, "🌐 Red perdida");

                // Esperar 2 segundos para ver si hay otra red disponible
                // (puede ser solo cambio de red, no pérdida total)
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (connectivityManager != null) {
                        Network activeNetwork = connectivityManager.getActiveNetwork();
                        if (activeNetwork == null) {
                            // Realmente no hay red
                            Log.w(TAG, "❌ Sin red confirmado");
                            if (callback != null) {
                                new Handler(Looper.getMainLooper()).post(() -> callback.onNetworkLost());
                            }
                        } else {
                            // Hay otra red disponible (fue cambio de red)
                            Log.d(TAG, "✅ Cambio de red detectado, hay red disponible");
                        }
                    }
                }, NETWORK_VERIFICATION_DELAY);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                boolean isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                // Solo loggear si cambia de no-internet a internet
                if (hasInternet && isValidated) {
                    Log.d(TAG, "🌐 Red con internet validado disponible");
                }
                // NO reconectar aquí para evitar loops - solo en onAvailable
            }
        };

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            Log.d(TAG, "✅ NetworkCallback registrado");
        } catch (Exception e) {
            Log.e(TAG, "Error registrando NetworkCallback", e);
        }
    }

    /**
     * Desregistra el monitor de red. Llamar en onDestroy.
     */
    public void unregister() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                networkCallback = null;
                Log.d(TAG, "🔇 NetworkCallback desregistrado");
            } catch (Exception e) {
                Log.e(TAG, "Error desregistrando NetworkCallback", e);
            }
        }
    }

    /**
     * Reconecta el WebSocket con throttling para evitar loops infinitos
     */
    private void reconnectWebSocket() {
        // Evitar reconexiones múltiples simultáneas
        if (isReconnecting) {
            Log.d(TAG, "⏸️ Reconexión ya en progreso, ignorando...");
            return;
        }

        // Throttling: mínimo 5 segundos entre reconexiones
        long now = System.currentTimeMillis();
        if (now - lastReconnectTime < MIN_RECONNECT_INTERVAL) {
            Log.d(TAG, "⏸️ Muy pronto para reconectar, esperando...");
            return;
        }

        if (!socketManager.isConnected()) {
            isReconnecting = true;
            lastReconnectTime = now;

            Log.d(TAG, "🔄 Reconectando SocketManager...");
            socketManager.connect();

            // Esperar a que se conecte y luego re-unirse al room UNA SOLA VEZ
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (currentRoomId != null && socketManager.isConnected()) {
                    // Unirse según el tipo de room
                    if (roomType == RoomType.CHAT) {
                        socketManager.joinChat(currentRoomId);
                        Log.d(TAG, "✅ Re-unido al chat tras cambio de red: " + currentRoomId);
                    } else if (roomType == RoomType.PASEO) {
                        socketManager.joinPaseo(currentRoomId);
                        Log.d(TAG, "✅ Re-unido al paseo tras cambio de red: " + currentRoomId);
                    }

                    // Notificar a la actividad
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onReconnected());
                    }
                }
                isReconnecting = false;
            }, 2000);
        } else {
            Log.d(TAG, "✅ Socket ya está conectado, no se requiere reconexión");
        }
    }

    /**
     * Verifica si hay red disponible
     *
     * @return true si hay red disponible, false en caso contrario
     */
    public boolean isNetworkAvailable() {
        if (connectivityManager == null) {
            return false;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null &&
               (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
    }

    /**
     * Fuerza una reconexión manual (útil para reintentos)
     */
    public void forceReconnect() {
        Log.d(TAG, "🔄 Reconexión forzada solicitada");
        // Resetear el throttling para permitir reconexión inmediata
        lastReconnectTime = 0;
        isReconnecting = false;
        reconnectWebSocket();
    }
}
