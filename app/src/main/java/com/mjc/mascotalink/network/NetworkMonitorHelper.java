package com.mjc.mascotalink.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mjc.mascotalink.upload.UploadScheduler;

/**
 * Helper class ROBUSTO para monitorear cambios de red y gestionar reconexiones de WebSocket
 * con retry exponencial, detección de calidad de red, y límites de reintentos.
 *
 * Características:
 * - Retry con backoff exponencial
 * - Detección de tipo de red (WiFi, Cellular, VPN)
 * - Verificación de calidad de conexión
 * - Límite de intentos de reconexión
 * - Ping/Pong para verificar conexión viva
 * - Throttling anti-loop
 *
 * Uso:
 * 1. Crear instancia en onCreate: networkMonitor = new NetworkMonitorHelper(context, socketManager, callback)
 * 2. Registrar: networkMonitor.register()
 * 3. Limpiar: networkMonitor.unregister() en onDestroy
 */
public class NetworkMonitorHelper {

    private static final String TAG = "NetworkMonitorHelper";

    // Configuración de reconexión
    private static final long MIN_RECONNECT_INTERVAL = 5000; // 5s mínimo entre reconexiones
    private static final long NETWORK_VERIFICATION_DELAY = 2000; // 2s para verificar pérdida de red
    private static final long RECONNECT_TIMEOUT = 10000; // 10s timeout para reconexión
    private static final int MAX_RETRY_ATTEMPTS = 5; // Máximo 5 intentos antes de esperar más
    private static final long MAX_BACKOFF_DELAY = 60000; // Máximo 60s de delay

    // Configuración de ping (optimizado para detección rápida)
    private static final long PING_INTERVAL = 15000; // Ping cada 15s para verificar conexión (antes 30s)
    private static final long PING_TIMEOUT = 3000; // Timeout de 3s para pong (antes 5s)

    private final Context context;
    private final SocketManager socketManager;
    private final NetworkCallback callback;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Control de reconexiones con backoff exponencial
    private boolean isReconnecting = false;
    private long lastReconnectTime = 0;
    private int reconnectAttempts = 0;
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private Runnable reconnectRunnable;

    // Control de ping/pong
    private Handler pingHandler = new Handler(Looper.getMainLooper());
    private Runnable pingRunnable;
    private long lastPongTime = 0;

    // Control de room WebSocket (chat o paseo)
    private String currentRoomId = null;
    private RoomType roomType = RoomType.CHAT;

    // Estado de la conexión
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private final SocketManager.OnPongListener pongListener = this::onPongReceived;
    private final SocketManager.OnConnectionListener socketConnectionListener =
        new SocketManager.OnConnectionListener() {
            @Override
            public void onConnected() {
                connectionState = ConnectionState.CONNECTED;
                startPingMonitoring();
            }

            @Override
            public void onDisconnected() {
                connectionState = ConnectionState.DISCONNECTED;
                stopPingMonitoring();
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Socket error: " + message);
            }
        };

    /**
     * Estado de la conexión
     */
    public enum ConnectionState {
        CONNECTED,          // Conectado y verificado
        CONNECTING,         // Intentando conectar
        DISCONNECTED,       // Desconectado
        RECONNECTING,       // Reconectando tras pérdida de red
        FAILED              // Falló tras múltiples intentos
    }

    /**
     * Tipo de sala WebSocket
     */
    public enum RoomType {
        CHAT,    // Para ChatActivity (usa joinChat)
        PASEO    // Para PaseoEnCursoActivity (usa joinPaseo)
    }

    /**
     * Calidad de la conexión de red
     */
    public enum NetworkQuality {
        EXCELLENT,  // WiFi fuerte o 4G/5G
        GOOD,       // WiFi débil o 3G
        POOR,       // 2G o señal muy débil
        UNKNOWN     // No se puede determinar
    }

    /**
     * Tipo de red
     */
    public enum NetworkType {
        WIFI,
        CELLULAR,
        VPN,
        ETHERNET,
        UNKNOWN
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

        /**
         * Llamado cuando cambia el tipo de red (WiFi, Cellular, etc.)
         */
        default void onNetworkTypeChanged(NetworkType type) {}

        /**
         * Llamado cuando cambia la calidad de la red
         */
        default void onNetworkQualityChanged(NetworkQuality quality) {}

        /**
         * Llamado cuando falla la reconexión tras múltiples intentos
         */
        default void onReconnectionFailed(int attempts) {}

        /**
         * Llamado durante reintentos de reconexión
         */
        default void onRetrying(int attempt, long delayMs) {}
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

        socketManager.addOnPongListener(pongListener);
        socketManager.addOnConnectionListener(socketConnectionListener);

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "🌐 Red disponible: " + network);

                // Detectar tipo de red
                NetworkType type = getNetworkType();
                Log.d(TAG, "📡 Tipo de red: " + type);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onNetworkTypeChanged(type));
                }

                                // Reanudar subidas pendientes
                UploadScheduler.retryPendingUploads(context);

// Notificar a la actividad
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onNetworkAvailable());
                }

                // Esperar 3 segundos para que la red se estabilice antes de reconectar
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    reconnectAttempts = 0; // Resetear intentos con red nueva
                    reconnectWebSocket();
                }, 3000);
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.w(TAG, "🌐 Red perdida");
                connectionState = ConnectionState.DISCONNECTED;
                stopPingMonitoring();

                // Esperar 2 segundos para ver si hay otra red disponible
                // (puede ser solo cambio de red, no pérdida total)
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (connectivityManager != null) {
                        Network activeNetwork = connectivityManager.getActiveNetwork();
                        if (activeNetwork == null) {
                            // Realmente no hay red
                            Log.w(TAG, " Sin red confirmado");
                            if (callback != null) {
                                new Handler(Looper.getMainLooper()).post(() -> callback.onNetworkLost());
                            }
                        } else {
                            // Hay otra red disponible (fue cambio de red)
                            Log.d(TAG, " Cambio de red detectado, hay red disponible");
                        }
                    }
                }, NETWORK_VERIFICATION_DELAY);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                boolean isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                // Detectar calidad de la conexión
                NetworkQuality quality = detectNetworkQuality(capabilities);
                Log.d(TAG, "📶 Calidad de red: " + quality);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onNetworkQualityChanged(quality));
                }

                // Solo loggear si cambia de no-internet a internet
                if (hasInternet && isValidated) {
                    Log.d(TAG, "🌐 Red con internet validado disponible");
                }
                // NO reconectar aquí para evitar loops - solo en onAvailable
            }
        };

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            Log.d(TAG, " NetworkCallback registrado");

            // Iniciar monitoreo de ping si hay conexión
            if (socketManager.isConnected()) {
                startPingMonitoring();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registrando NetworkCallback", e);
        }
    }

    /**
     * Desregistra el monitor de red. Llamar en onDestroy.
     */
    public void unregister() {
        socketManager.removeOnPongListener(pongListener);
        socketManager.removeOnConnectionListener(socketConnectionListener);
        stopPingMonitoring();
        cancelPendingReconnects();

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
     * Reconecta el WebSocket con retry exponencial y límite de intentos
     */
    private void reconnectWebSocket() {
        // Evitar reconexiones múltiples simultáneas
        if (isReconnecting) {
            Log.d(TAG, " Reconexión ya en progreso, ignorando...");
            return;
        }

        // Throttling: mínimo 5 segundos entre reconexiones
        long now = System.currentTimeMillis();
        if (now - lastReconnectTime < MIN_RECONNECT_INTERVAL) {
            Log.d(TAG, " Muy pronto para reconectar, esperando...");
            return;
        }

        // Verificar límite de intentos
        if (reconnectAttempts >= MAX_RETRY_ATTEMPTS) {
            long backoffDelay = calculateBackoffDelay(reconnectAttempts);
            Log.w(TAG, " Máximo de intentos alcanzado (" + reconnectAttempts + "), esperando " + backoffDelay + "ms antes de reintentar");

            connectionState = ConnectionState.FAILED;
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onReconnectionFailed(reconnectAttempts));
            }

            // Programar reintento con backoff exponencial
            scheduleReconnect(backoffDelay);
            return;
        }

        if (!socketManager.isConnected()) {
            isReconnecting = true;
            lastReconnectTime = now;
            reconnectAttempts++;
            connectionState = ConnectionState.RECONNECTING;

            Log.d(TAG, "🔄 Reconectando SocketManager (intento " + reconnectAttempts + "/" + MAX_RETRY_ATTEMPTS + ")...");

            if (callback != null && reconnectAttempts > 1) {
                long nextDelay = calculateBackoffDelay(reconnectAttempts);
                new Handler(Looper.getMainLooper()).post(() -> callback.onRetrying(reconnectAttempts, nextDelay));
            }

            socketManager.connect();

            // Timeout para reconexión
            reconnectHandler.postDelayed(() -> {
                if (isReconnecting && !socketManager.isConnected()) {
                    Log.w(TAG, "⏱️ Timeout de reconexión alcanzado");
                    isReconnecting = false;

                    // Reintentar con backoff
                    long backoffDelay = calculateBackoffDelay(reconnectAttempts);
                    scheduleReconnect(backoffDelay);
                }
            }, RECONNECT_TIMEOUT);

            // Esperar a que se conecte y luego re-unirse al room UNA SOLA VEZ
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (currentRoomId != null && socketManager.isConnected()) {
                    // Unirse según el tipo de room
                    if (roomType == RoomType.CHAT) {
                        socketManager.joinChat(currentRoomId);
                        Log.d(TAG, " Re-unido al chat tras cambio de red: " + currentRoomId);
                    } else if (roomType == RoomType.PASEO) {
                        socketManager.joinPaseo(currentRoomId);
                        Log.d(TAG, " Re-unido al paseo tras cambio de red: " + currentRoomId);
                    }

                    // Reconexión exitosa, resetear intentos
                    reconnectAttempts = 0;
                    connectionState = ConnectionState.CONNECTED;

                    // Notificar a la actividad
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onReconnected());
                    }

                    // Iniciar monitoreo de ping
                    startPingMonitoring();
                } else if (!socketManager.isConnected()) {
                    // Falló la reconexión, programar reintento
                    Log.w(TAG, " Falló reconexión, programando reintento...");
                    long backoffDelay = calculateBackoffDelay(reconnectAttempts);
                    scheduleReconnect(backoffDelay);
                }
                isReconnecting = false;
            }, 2000);
        } else {
            Log.d(TAG, " Socket ya está conectado, no se requiere reconexión");
            connectionState = ConnectionState.CONNECTED;
            reconnectAttempts = 0;
        }
    }

    /**
     * Programa un reintento de reconexión con delay
     */
    private void scheduleReconnect(long delayMs) {
        cancelPendingReconnects();

        Log.d(TAG, "⏰ Programando reintento en " + delayMs + "ms");
        reconnectRunnable = () -> {
            Log.d(TAG, "⏰ Ejecutando reintento programado");
            reconnectWebSocket();
        };

        reconnectHandler.postDelayed(reconnectRunnable, delayMs);
    }

    /**
     * Cancela reconexiones pendientes
     */
    private void cancelPendingReconnects() {
        if (reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
            reconnectRunnable = null;
        }
    }

    /**
     * Calcula el delay de backoff exponencial
     */
    private long calculateBackoffDelay(int attempts) {
        // Backoff exponencial: 5s, 10s, 20s, 40s, 60s (máximo)
        long delay = MIN_RECONNECT_INTERVAL * (long) Math.pow(2, attempts - 1);
        return Math.min(delay, MAX_BACKOFF_DELAY);
    }

    /**
     * Inicia el monitoreo de ping/pong
     */
    private void startPingMonitoring() {
        stopPingMonitoring();

        Log.d(TAG, "🏓 Iniciando monitoreo de ping");
        lastPongTime = System.currentTimeMillis();

        pingRunnable = new Runnable() {
            @Override
            public void run() {
                if (socketManager.isConnected()) {
                    Log.v(TAG, "🏓 Enviando ping...");
                    socketManager.ping();

                    // Verificar si hubo pong reciente
                    long timeSinceLastPong = System.currentTimeMillis() - lastPongTime;
                    if (timeSinceLastPong > PING_INTERVAL + PING_TIMEOUT) {
                        Log.w(TAG, " No se recibió pong en " + timeSinceLastPong + "ms, conexión puede estar muerta");
                        // Forzar reconexión
                        forceReconnect();
                    } else {
                        // Programar siguiente ping
                        pingHandler.postDelayed(this, PING_INTERVAL);
                    }
                } else {
                    Log.d(TAG, "Socket desconectado, deteniendo ping");
                    stopPingMonitoring();
                }
            }
        };

        pingHandler.postDelayed(pingRunnable, PING_INTERVAL);
    }

    /**
     * Detiene el monitoreo de ping/pong
     */
    private void stopPingMonitoring() {
        if (pingRunnable != null) {
            pingHandler.removeCallbacks(pingRunnable);
            pingRunnable = null;
            Log.d(TAG, "🏓 Monitoreo de ping detenido");
        }
    }

    /**
     * Notifica que se recibió un pong
     */
    public void onPongReceived() {
        lastPongTime = System.currentTimeMillis();
        Log.v(TAG, "🏓 Pong recibido");
    }

    /**
     * Detecta la calidad de la conexión basada en las capacidades
     */
    private NetworkQuality detectNetworkQuality(NetworkCapabilities capabilities) {
        if (capabilities == null) {
            return NetworkQuality.UNKNOWN;
        }

        // WiFi generalmente es excelente
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            int signalStrength = capabilities.getSignalStrength();
            if (signalStrength > -50) return NetworkQuality.EXCELLENT;
            if (signalStrength > -70) return NetworkQuality.GOOD;
            return NetworkQuality.POOR;
        }

        // Cellular depende del tipo
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            // Verificar ancho de banda si está disponible
            int downstreamKbps = capabilities.getLinkDownstreamBandwidthKbps();
            if (downstreamKbps > 5000) return NetworkQuality.EXCELLENT; // > 5 Mbps
            if (downstreamKbps > 1000) return NetworkQuality.GOOD; // > 1 Mbps
            if (downstreamKbps > 0) return NetworkQuality.POOR;
        }

        return NetworkQuality.UNKNOWN;
    }

    /**
     * Obtiene el tipo de red actual
     */
    public NetworkType getNetworkType() {
        if (connectivityManager == null) {
            return NetworkType.UNKNOWN;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return NetworkType.UNKNOWN;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return NetworkType.UNKNOWN;
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return NetworkType.WIFI;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return NetworkType.CELLULAR;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return NetworkType.VPN;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return NetworkType.ETHERNET;
        }

        return NetworkType.UNKNOWN;
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
        
        // Forzar desconexión real para limpiar estado zombie
        Log.d(TAG, " Forzando desconexión de socket zombie");
        if (socketManager != null) {
            socketManager.disconnect();
        }

        // Resetear el throttling para permitir reconexión inmediata
        lastReconnectTime = 0;
        isReconnecting = false;
        reconnectAttempts = 0;
        cancelPendingReconnects();
        stopPingMonitoring();
        
        // Iniciar nueva conexión limpia
        reconnectWebSocket();
    }

    /**
     * Obtiene el estado actual de la conexión
     */
    public ConnectionState getConnectionState() {
        return connectionState;
    }

    /**
     * Obtiene el número de intentos de reconexión
     */
    public int getReconnectAttempts() {
        return reconnectAttempts;
    }
}
