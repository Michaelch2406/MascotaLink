package com.mjc.mascotalink.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.mjc.mascotalink.PaseoEnCursoActivity;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.network.SocketManager;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Servicio en primer plano para mantener el rastreo del paseo activo
 * incluso cuando la aplicación se cierra o la pantalla se apaga.
 */
@AndroidEntryPoint
public class LocationService extends Service {

    private static final String TAG = "LocationService";
    public static final String ACTION_START_TRACKING = "com.mjc.mascotalink.action.START_TRACKING";
    public static final String ACTION_STOP_TRACKING = "com.mjc.mascotalink.action.STOP_TRACKING";
    public static final String EXTRA_RESERVA_ID = "reserva_id";

    private static final int NOTIFICATION_ID = 12345;
    private static final String CHANNEL_ID = "location_tracking_channel";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private SocketManager socketManager;
    private String currentReservaId;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // Throttling para Firestore (guardar historial)
    private long lastFirestoreSaveTime = 0;
    private static final long FIRESTORE_SAVE_INTERVAL_MS = 15000; // 15 segundos para historial
    private final SecureRandom secureRandom = new SecureRandom();

    // Throttling para Ubicación Actual (búsqueda)
    private long lastRealtimeUpdateTime = 0;
    private static final long REALTIME_UPDATE_INTERVAL_MS = 5000; // 5 segundos para "ubicacion_actual"

    // ===== THROTTLING WEBSOCKET (AHORRO BATERÍA) =====
    private long lastWebSocketSendTime = 0;
    private static final long WEBSOCKET_SEND_INTERVAL_MS = 10000; // 10 segundos (antes: cada update)
    private static final long WEBSOCKET_SEND_INTERVAL_SLOW_MS = 30000; // 30s cuando detenido

    // ===== WEBSOCKET CONDICIONAL (SOLO SI DUEÑO ESTÁ VIENDO) =====
    private boolean duenoViendoMapa = true; // Asumir true al inicio
    private long lastDuenoCheckTime = 0;
    private static final long DUENO_CHECK_INTERVAL_MS = 30000; // Verificar cada 30s

    // ===== OPTIMIZACIONES DE BATERÍA Y GPS =====

    // Batching de ubicaciones (OPTIMIZADO: 60s → 120s)
    private List<Map<String, Object>> locationBatch = new ArrayList<>();
    private static final int BATCH_SIZE = 10; // Enviar cada 10 ubicaciones (antes: 5)
    private static final long BATCH_TIMEOUT_MS = 120000; // O cada 120 segundos (antes: 60s)
    private long lastBatchSendTime = 0;

    // Detección de movimiento
    private Location lastLocation = null;
    private float currentSpeed = 0f;
    private static final float SPEED_THRESHOLD_MPS = 1.4f; // ~5 km/h
    private static final float STATIONARY_THRESHOLD_METERS = 10f;

    // MODO PAUSA COMPLETA
    private long tiempoSinMovimiento = 0;
    private long ultimoMovimientoDetectado = System.currentTimeMillis();
    private static final long PAUSA_COMPLETA_THRESHOLD_MS = 480000; // 8 minutos
    private long tiempoDetenciónInicio = 0; // Timestamp cuando empezó a estar quieto > 8 minutos
    private long lastStationaryUpdateTime = 0; // Última actualización de ubicacion_actual cuando quieto
    private static final long STATIONARY_UPDATE_INTERVAL_MS = 30000; // 30 segundos - actualizar ubicacion_actual cuando quieto
    private boolean gpsPausado = false;

    // Estado de batería
    private boolean isLowBattery = false;
    private static final int LOW_BATTERY_THRESHOLD = 20; // 20%
    private BroadcastReceiver batteryReceiver;

    // ===== NUEVAS VARIABLES PARA PERSISTENCIA DE DISTANCIA =====
    private double distanciaAcumuladaMetros = 0.0;
    private long lastDistanceSaveTime = 0;
    private static final long DISTANCE_SAVE_INTERVAL_MS = 30000; // 30 segundos

    // Validación de GPS
    private static final float MAX_ACCURACY_METERS = 500f; // Rechazar si accuracy > 500m
    private static final float MAX_JUMP_METERS = 100f; // Rechazar saltos > 100m
    private static final long MIN_TIME_BETWEEN_JUMPS_MS = 2000; // en < 2 segundos

    // Subcollection migration
    private int ubicacionesCount = 0;
    private static final int MAX_UBICACIONES_IN_ARRAY = 500; // Migrar a subcollection después de 500
    private boolean usandoSubcollection = false;

    // Estado de la reserva en tiempo real
    private String currentEstado = null;
    private com.google.firebase.firestore.ListenerRegistration estadoListener = null;
    private com.google.firebase.firestore.ListenerRegistration duenoListener = null;

    // ===== LAZY CONNECTION: Callback para reconexión =====
    private com.mjc.mascotalink.network.SocketManager.OnConnectionListener socketConnectionListener;

    // ===== WEBSOCKET FALLBACK: Si socket no conecta en 5s, forzar guardado a Firestore =====
    private long socketStartConnectTime = 0;
    private static final long WEBSOCKET_CONNECTION_TIMEOUT_MS = 5000; // 5 segundos
    private boolean hasWarnedAboutSlowConnection = false;

    // ===== FIRESTORE BATCH RETRY LOGIC =====
    private List<Map<String, Object>> failedBatch = null;  // Cola de locations fallidas
    private int batchRetryCount = 0;
    private long lastBatchRetryTime = 0;
    private static final int MAX_BATCH_RETRIES = 3;
    private static final long BATCH_RETRY_DELAY_MS = 2000;  // Exponencial: 2s, 4s, 8s

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "========================================");
        Log.d(TAG, ">>> LocationService.onCreate() INICIADO");
        Log.d(TAG, "========================================");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        socketManager = SocketManager.getInstance(this); // Obtener instancia singleton
        db = FirebaseFirestore.getInstance(); // Obtener directamente sin Hilt
        auth = FirebaseAuth.getInstance(); // Obtener directamente sin Hilt

        Log.d(TAG, ">>> SocketManager obtenido: " + (socketManager != null ? "SI" : "NULL"));
        Log.d(TAG, ">>> Firestore obtenido: " + (db != null ? "SI" : "NULL"));
        Log.d(TAG, ">>> FirebaseAuth obtenido: " + (auth != null ? "SI" : "NULL"));

        createNotificationChannel();
        setupBatteryMonitoring();
        setupSocketConnectionListener();

        Log.d(TAG, ">>> LocationService.onCreate() COMPLETADO");
    }

    /**
     * LAZY CONNECTION: Configura listener para reconectar cuando socket esté listo
     */
    private void setupSocketConnectionListener() {
        socketConnectionListener = new com.mjc.mascotalink.network.SocketManager.OnConnectionListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, " Socket conectado - Reintentando unirse al paseo");
                // Si tenemos un paseo activo, reintentar unirse
                if (currentReservaId != null && !currentReservaId.isEmpty()) {
                    socketManager.joinPaseo(currentReservaId);

                    // Si tenemos ubicación reciente, enviar inmediatamente
                    if (lastLocation != null && duenoViendoMapa) {
                        Log.d(TAG, "📡 Enviando ubicación inmediata tras reconexión");
                        socketManager.updateLocation(currentReservaId,
                            lastLocation.getLatitude(),
                            lastLocation.getLongitude(),
                            lastLocation.getAccuracy());
                    }
                }
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, " Socket desconectado");
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, " Error de socket: " + message);
            }
        };

        socketManager.addOnConnectionListener(socketConnectionListener);
    }

    /**
     * Configura monitoreo de batería para optimizar GPS
     */
    private void setupBatteryMonitoring() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int batteryPct = (int) ((level / (float) scale) * 100);

                boolean wasLowBattery = isLowBattery;
                isLowBattery = batteryPct <= LOW_BATTERY_THRESHOLD;

                // Si cambió el estado, ajustar GPS
                if (wasLowBattery != isLowBattery && currentReservaId != null) {
                    Log.d(TAG, "Batería al " + batteryPct + "%, ajustando GPS");
                    adjustLocationUpdates();
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);

        // Obtener nivel inicial
        Intent batteryStatus = registerReceiver(null, filter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = (int) ((level / (float) scale) * 100);
            isLowBattery = batteryPct <= LOW_BATTERY_THRESHOLD;
            Log.d(TAG, "Nivel de batería inicial: " + batteryPct + "%");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, ">>> onStartCommand() llamado");
        Log.d(TAG, ">>> Intent: " + (intent != null ? "PRESENTE" : "NULL"));

        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, ">>> Action: " + action);

            if (ACTION_START_TRACKING.equals(action)) {
                String reservaId = intent.getStringExtra(EXTRA_RESERVA_ID);
                Log.d(TAG, ">>> ReservaId recibido: " + reservaId);

                if (reservaId != null) {
                    Log.d(TAG, ">>> Llamando a startTracking()...");
                    startTracking(reservaId);
                } else {
                    Log.e(TAG, ">>> ERROR: reservaId es NULL");
                }
            } else if (ACTION_STOP_TRACKING.equals(action)) {
                Log.d(TAG, ">>> Llamando a stopTracking()...");
                stopTracking();
            } else {
                Log.w(TAG, ">>> Action desconocido: " + action);
            }
        } else {
            Log.e(TAG, ">>> ERROR: Intent es NULL");
        }

        return START_STICKY;
    }

    private void startTracking(String reservaId) {
        Log.d(TAG, "Iniciando servicio de rastreo para reserva: " + reservaId);
        currentReservaId = reservaId;
        // CRÍTICO: Inicializar currentEstado a EN_CURSO ANTES de requestLocationUpdates()
        // para evitar race condition donde la primera ubicación llega antes que el listener
        // responda de Firestore. LocationService solo se inicia durante paseos EN_CURSO.
        currentEstado = "EN_CURSO";
        Log.d(TAG, "✅ Estado inicial establecido a EN_CURSO para evitar rechazo de ubicaciones");

        // 1. Iniciar notificación Foreground
        startForeground(NOTIFICATION_ID, getNotification(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);

        // 2. Cargar distancia acumulada existente (si se está reanudando)
        cargarDistanciaInicial();

        // 3. Escuchar cambios de estado en tiempo real
        setupEstadoListener();

        // 4. Rastrear tiempo de conexión de socket para fallback
        socketStartConnectTime = System.currentTimeMillis();
        hasWarnedAboutSlowConnection = false;
        Log.d(TAG, "⏱️ Iniciando rastreo de conexión WebSocket (timeout: " + WEBSOCKET_CONNECTION_TIMEOUT_MS + "ms)");

        // 5. Unirse al room del Socket
        socketManager.joinPaseo(currentReservaId);

        // 6. Solicitar actualizaciones de ubicación
        requestLocationUpdates();
    }

    /**
     * Configura listeners en tiempo real para estado y dueño viendo mapa
     * Detiene el tracking automáticamente si el estado cambia a algo diferente de EN_CURSO
     */
    private void setupEstadoListener() {
        if (currentReservaId == null) return;

        // ===== LISTENER 1: Estado de la reserva =====
        if (estadoListener != null) {
            estadoListener.remove();
        }

        estadoListener = db.collection("reservas").document(currentReservaId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error en listener de estado", error);
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String nuevoEstado = documentSnapshot.getString("estado");
                        boolean isFromCache = documentSnapshot.getMetadata().isFromCache();

                        Log.d(TAG, " Listener ejecutado - Estado: " + nuevoEstado + ", FromCache: " + isFromCache);

                        // Actualizar estado actual
                        // CRÍTICO: Solo cambiar estado si es del servidor (!isFromCache) o si es EN_CURSO
                        // Esto evita que el cache desactualizado sobrescriba el estado EN_CURSO inicial
                        if (nuevoEstado != null && !nuevoEstado.equals(currentEstado) && (!isFromCache || "EN_CURSO".equals(nuevoEstado))) {
                            Log.d(TAG, " Estado cambió: " + currentEstado + " → " + nuevoEstado);
                            String estadoAnterior = currentEstado;
                            currentEstado = nuevoEstado;

                            // SOLO detener si:
                            // 1. Los datos vienen del servidor (NO del cache local)
                            // 2. Y el estado cambió de EN_CURSO a otro estado
                            if (!isFromCache && !"EN_CURSO".equals(nuevoEstado) && "EN_CURSO".equals(estadoAnterior)) {
                                Log.w(TAG, " Paseo cambió de EN_CURSO a " + nuevoEstado + ", deteniendo tracking");
                                stopTracking();
                            } else if (isFromCache && !"EN_CURSO".equals(nuevoEstado)) {
                                Log.d(TAG, " Estado del cache es " + nuevoEstado + " pero ignorando hasta recibir datos del servidor");
                            }
                        } else if (currentEstado == null) {
                            // Primera vez que se obtiene el estado
                            currentEstado = nuevoEstado;
                            Log.d(TAG, " Estado inicial: " + currentEstado + ", FromCache: " + isFromCache);
                        }

                        // ===== OPTIMIZACIÓN: Listener para "dueño viendo mapa" =====
                        Boolean viendo = documentSnapshot.getBoolean("dueno_viendo_mapa");
                        boolean estadoAnterior = duenoViendoMapa;
                        duenoViendoMapa = viendo != null ? viendo : true;

                        //  DEBUG: Siempre mostrar el valor leído
                        Log.d(TAG, " dueno_viendo_mapa leído de Firestore: " + viendo + " (será: " + duenoViendoMapa + ")");

                        if (estadoAnterior != duenoViendoMapa) {
                            if (duenoViendoMapa) {
                                Log.i(TAG, " Dueño EMPEZÓ a ver mapa - Activando WebSocket y forzando actualización");
                                // Forzar envío inmediato si tenemos ubicación reciente
                                if (lastLocation != null && socketManager.isConnected()) {
                                    Log.d(TAG, "📡 Forzando envío inmediato de ubicación vía WebSocket");
                                    socketManager.updateLocation(currentReservaId, lastLocation.getLatitude(), lastLocation.getLongitude(), lastLocation.getAccuracy());
                                    // También forzar guardado en Firestore para que el fallback funcione si WS falla
                                    sendLocationBatch();
                                }
                            } else {
                                Log.i(TAG, "🚫 Dueño DEJÓ de ver mapa - Desactivando WebSocket (~10% ahorro batería)");
                            }
                        }
                    }
                });
    }

    /**
     * Carga la distancia acumulada desde Firestore al iniciar el servicio
     * Esto permite reanudar el tracking si la app se cerró
     */
    private void cargarDistanciaInicial() {
        db.collection("reservas").document(currentReservaId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists()) {
                        Object distanciaObj = snapshot.get("distancia_acumulada_metros");
                        if (distanciaObj instanceof Number) {
                            distanciaAcumuladaMetros = ((Number) distanciaObj).doubleValue();
                            Log.d(TAG, "📏 Distancia inicial cargada: " + String.format("%.2f", distanciaAcumuladaMetros / 1000) + " km");
                        }

                        // Cargar contador de ubicaciones para saber si usar subcollection
                        Object ubicacionesObj = snapshot.get("ubicaciones");
                        if (ubicacionesObj instanceof List) {
                            ubicacionesCount = ((List<?>) ubicacionesObj).size();
                            Log.d(TAG, "📍 Ubicaciones existentes: " + ubicacionesCount);
                            if (ubicacionesCount > MAX_UBICACIONES_IN_ARRAY) {
                                usandoSubcollection = true;
                                Log.d(TAG, "🔄 Usando subcollection desde el inicio");
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Error cargando distancia inicial", e));
    }

    private void requestLocationUpdates() {
        LocationRequest locationRequest = buildOptimalLocationRequest();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        processLocation(location);
                    }
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No tienes permisos de ubicación para el servicio.");
            stopSelf();
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    /**
     * Construye un LocationRequest optimizado basado en batería, movimiento y precisión GPS
     */
    private LocationRequest buildOptimalLocationRequest() {
        long interval;
        long minInterval;
        float minDistance;
        int priority;

        // Verificar precisión de última ubicación
        float lastAccuracy = lastLocation != null && lastLocation.hasAccuracy() ?
                             lastLocation.getAccuracy() : 50f;
        boolean precisionBaja = lastAccuracy > 100f;

        // NUEVO: Modo ultra-ahorro cuando dueño NO está viendo el mapa
        if (!duenoViendoMapa && !isLowBattery) {
            // Solo guardar en Firestore cada 30s, sin WebSocket
            interval = 30000; // 30 segundos
            minInterval = 20000; // 20 segundos
            minDistance = 30; // 30 metros
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            Log.d(TAG, "GPS en modo ULTRA-AHORRO (30s, dueño no viendo - ahorro ~40%)");
        } else if (isLowBattery) {
            // Modo ahorro de batería: menos frecuente, menos precisión
            interval = 20000; // 20 segundos (antes 15s)
            minInterval = 15000; // 15 segundos
            minDistance = 25; // 25 metros
            priority = Priority.PRIORITY_LOW_POWER;
            Log.d(TAG, "GPS en modo AHORRO DE BATERÍA (20s, LOW_POWER)");
        } else if (gpsPausado) {
            // NUEVO: Modo pausa - solo verificar cada minuto si hay movimiento
            interval = 60000; // 60 segundos
            minInterval = 45000; // 45 segundos
            minDistance = 50; // 50 metros para detectar movimiento
            priority = Priority.PRIORITY_LOW_POWER;
            Log.d(TAG, "GPS en modo PAUSA (60s, esperando movimiento)");
        } else if (precisionBaja) {
            // Si tenemos baja precisión, reducir frecuencia para ahorrar batería
            interval = 12000; // 12 segundos
            minInterval = 8000; // 8 segundos
            minDistance = 15; // 15 metros
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            Log.d(TAG, "GPS en modo PRECISIÓN BAJA (12s, accuracy: " + (int)lastAccuracy + "m)");
        } else if (currentSpeed < SPEED_THRESHOLD_MPS) {
            // Usuario casi detenido: punto medio entre precisión y batería
            interval = 11000; // 11 segundos
            minInterval = 8000; // 8 segundos
            minDistance = 8; // 8 metros (punto medio entre 12 y 5)
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            Log.d(TAG, "GPS en modo DETENIDO (11s, baja velocidad, 8m resolución)");
        } else if (currentSpeed > 3.0f) {
            // Modo alta velocidad (corriendo/bicicleta) - balance precisión/batería
            interval = 6000; // 6 segundos
            minInterval = 4000; // 4 segundos
            minDistance = 4; // 4 metros (punto medio entre 5 y 3)
            priority = Priority.PRIORITY_HIGH_ACCURACY;
            Log.d(TAG, "GPS en modo ALTA VELOCIDAD (6s, HIGH_ACCURACY - " + String.format("%.1f", currentSpeed * 3.6) + " km/h)");
        } else {
            // Modo normal caminando - balance entre precisión y ahorro de batería
            interval = 8000; // 8 segundos
            minInterval = 5000; // 5 segundos
            minDistance = 5; // 5 metros (punto medio entre 8 y 3)
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            Log.d(TAG, "GPS en modo CAMINANDO (8s, BALANCED, 5m resolución)");
        }

        return new LocationRequest.Builder(priority, interval)
                .setMinUpdateIntervalMillis(minInterval)
                .setMinUpdateDistanceMeters(minDistance)
                .setWaitForAccurateLocation(false)
                .setMaxUpdateDelayMillis(interval * 2) // Permitir batching del sistema
                .build();
    }

    /**
     * Ajusta las actualizaciones de ubicación dinámicamente
     */
    private void adjustLocationUpdates() {
        if (locationCallback == null) return;

        // Remover listener actual
        fusedLocationClient.removeLocationUpdates(locationCallback);

        // Crear nuevo request optimizado
        LocationRequest newRequest = buildOptimalLocationRequest();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Re-solicitar con nuevos parámetros
        fusedLocationClient.requestLocationUpdates(newRequest, locationCallback, Looper.getMainLooper());
        Log.d(TAG, "GPS ajustado dinámicamente");
    }

    private void processLocation(Location location) {
        if (currentReservaId == null || auth.getCurrentUser() == null) return;

        // Verificar estado usando variable local (actualizada en tiempo real por el listener)
        if (!"EN_CURSO".equals(currentEstado)) {
            Log.w(TAG, " Ubicación rechazada - estado: " + currentEstado + " (debe ser EN_CURSO)");
            return;
        }

        double lat = location.getLatitude();
        double lng = location.getLongitude();
        float accuracy = location.getAccuracy();
        long now = System.currentTimeMillis();

        // ===== VALIDACIÓN 1: Rechazar GPS con precisión muy mala =====
        if (accuracy > MAX_ACCURACY_METERS) {
            Log.w(TAG, " Ubicación rechazada: precisión muy baja (" + (int)accuracy + "m > " + (int)MAX_ACCURACY_METERS + "m)");
            return;
        }

        // ===== VALIDACIÓN 2: Detectar saltos anormales (GPS falso o cambio de red) =====
        if (lastLocation != null) {
            float distance = lastLocation.distanceTo(location);
            long timeDelta = location.getTime() - lastLocation.getTime();

            // Detectar saltos > 100m en < 2 segundos, pero PERMITIR salto inicial grande
            // Esto permite que el paseador se conecte desde una ubicación diferente
            if (distance > MAX_JUMP_METERS && timeDelta < MIN_TIME_BETWEEN_JUMPS_MS && timeDelta > 0) {
                Log.w(TAG, " Ubicación rechazada: salto anormal de " + (int)distance + "m en " + timeDelta + "ms");
                return;
            }

            if (timeDelta > 0) {
                currentSpeed = distance / (timeDelta / 1000f); // m/s
            }

            // Filtrar ubicaciones estacionarias con poca precisión
            if (distance < STATIONARY_THRESHOLD_METERS && accuracy > 20) {
                Log.v(TAG, "Ubicación filtrada: muy cercana y baja precisión");
                return;
            }

            // ===== CALCULAR DISTANCIA ACUMULADA =====
            if (distance > 5) { // Solo contar movimientos > 5m para evitar ruido
                distanciaAcumuladaMetros += distance;
                Log.v(TAG, "📏 Distancia acumulada: " + String.format("%.2f", distanciaAcumuladaMetros / 1000) + " km");

                // Detectar movimiento para modo pausa
                ultimoMovimientoDetectado = System.currentTimeMillis();
                tiempoSinMovimiento = 0;
                tiempoDetenciónInicio = 0; // Resetear contador de detención

                // Si estaba pausado, reactivar GPS
                if (gpsPausado) {
                    Log.i(TAG, "▶️ Movimiento detectado - Reactivando GPS desde pausa");
                    reactivarGPSDesdePausa();
                }
            } else {
                // No hay movimiento significativo
                tiempoSinMovimiento = System.currentTimeMillis() - ultimoMovimientoDetectado;

                // ===== NUEVA LÓGICA: Detectar si está quieto > 8 minutos =====
                if (tiempoSinMovimiento > PAUSA_COMPLETA_THRESHOLD_MS) {
                    if (tiempoDetenciónInicio == 0) {
                        // Primera vez que detectamos quieto > 8 minutos
                        tiempoDetenciónInicio = System.currentTimeMillis();
                        Log.w(TAG, "🛑 Paseador QUIETO > 8 minutos - Dejando de marcar ubicaciones en array");
                    }
                }
            }
        } else {
            // Primera ubicación
            ultimoMovimientoDetectado = System.currentTimeMillis();
        }

        lastLocation = location;

        // ===== VERIFICAR SI DEBE ENTRAR EN MODO PAUSA =====
        // COMPLETAMENTE DESHABILITADO: GPS Pause Mode causa pérdida de ubicaciones
        // Los perros se pausan naturalmente para olisquear, jugar, descansar, etc.
        // Activar pausa durante paseos = PÉRDIDA TOTAL DE DATOS
        //
        // CRÍTICO: LocationService debe SIEMPRE guardar ubicaciones sin importar movimiento
        // El único modo de pausa sería si batería < 5% (caso extremo)
        //
        // NO HACER: if (tiempoSinMovimiento > PAUSA_COMPLETA_THRESHOLD_MS) activarModoPausa()
        // RESULTADO: Ubicaciones se guardan CONTINUAMENTE

        if (gpsPausado && isLowBattery == false) {
            // Si estaba pausado pero batería está normal, REACTIVAR
            Log.w(TAG, "🔄 Reactivando GPS - Batería OK, continuando con paseo");
            reactivarGPSDesdePausa();
        }
        // GPS SIEMPRE ACTIVO durante el paseo

        // 1. Enviar por WebSocket (OPTIMIZADO: Throttling + Condicional)
        // Ahorro adicional: No enviar si dueño no está viendo (~5-10% batería)
        // El estado de duenoViendoMapa se actualiza automáticamente por el listener

        long intervaloWebSocket = currentSpeed < SPEED_THRESHOLD_MPS ?
                WEBSOCKET_SEND_INTERVAL_SLOW_MS : // 30s si detenido
                WEBSOCKET_SEND_INTERVAL_MS; // 10s si en movimiento

        // ===== WEBSOCKET FALLBACK: Timeout si socket tarda > 5s en conectar =====
        boolean socketTardando = !socketManager.isConnected() &&
                (now - socketStartConnectTime) > WEBSOCKET_CONNECTION_TIMEOUT_MS;

        if (socketTardando && !hasWarnedAboutSlowConnection) {
            Log.w(TAG, "⚠️ WebSocket tardando > 5s en conectar. Usando FALLBACK directo a Firestore");
            hasWarnedAboutSlowConnection = true;
        }

        if (now - lastWebSocketSendTime > intervaloWebSocket) {
            // SOLO enviar si dueño está viendo Y socket está conectado
            if (duenoViendoMapa && socketManager.isConnected()) {
                Log.d(TAG, "📡 ENVIANDO WebSocket - duenoViendoMapa=" + duenoViendoMapa + ", connected=true, paseoId=" + currentReservaId);
                socketManager.updateLocation(currentReservaId, lat, lng, accuracy);
                lastWebSocketSendTime = now;
                Log.v(TAG, " WebSocket enviado exitosamente (próximo en " + (intervaloWebSocket / 1000) + "s)");
            } else if (!duenoViendoMapa) {
                Log.d(TAG, " WebSocket PAUSADO - duenoViendoMapa=false (ahorro ~10% batería)");
            } else if (socketTardando) {
                Log.w(TAG, " WebSocket DESHABILITADO - Socket tardando > 5s, usando FALLBACK Firestore");
            } else if (!socketManager.isConnected()) {
                Log.d(TAG, " WebSocket pendiente - Socket conectando...");
            }
        } else {
            Log.v(TAG, "⏭️ WebSocket throttled (esperando " +
                    ((intervaloWebSocket - (now - lastWebSocketSendTime)) / 1000) + "s)");
        }

        // 2. Actualizar ubicación en tiempo real del usuario (para búsquedas/mapa general)
        // CAMBIO: Si está quieto > 8 minutos, actualizar cada 30s EN VEZ de cada 5s
        long intervalActualizar = tiempoSinMovimiento > PAUSA_COMPLETA_THRESHOLD_MS ?
                STATIONARY_UPDATE_INTERVAL_MS :  // 30s cuando quieto > 8 min
                REALTIME_UPDATE_INTERVAL_MS;      // 5s cuando en movimiento/normal

        if (now - lastRealtimeUpdateTime > intervalActualizar) {
            updateUserRealtimeLocation(lat, lng, accuracy);
            lastRealtimeUpdateTime = now;
        }

        // 3. Agregar a batch para historial (OPTIMIZACIÓN: no guardar individualmente)
        addLocationToBatch(location);

        // ===== 4. GUARDAR DISTANCIA ACUMULADA (DINÁMICO) =====
        // OPTIMIZACIÓN: 30s cuando en movimiento, 60s cuando parado
        long intervaloDistancia = currentSpeed > SPEED_THRESHOLD_MPS ?
                DISTANCE_SAVE_INTERVAL_MS :          // 30s en movimiento
                DISTANCE_SAVE_INTERVAL_MS * 2;       // 60s parado

        if (now - lastDistanceSaveTime > intervaloDistancia) {
            guardarDistanciaAcumulada();
            lastDistanceSaveTime = now;
        }

        // Ajustar GPS si cambió la velocidad significativamente
        if (Math.abs(currentSpeed - SPEED_THRESHOLD_MPS) < 0.5f) {
            // Cerca del umbral, podría necesitar ajuste
            adjustLocationUpdates();
        }

        // ===== OPTIMIZACIÓN ADICIONAL: Ajustar GPS según precisión =====
        // Si tenemos mala precisión repetidamente, reducir frecuencia
        if (accuracy > 100f) {
            adjustLocationUpdates();
        }
    }

    private void updateUserRealtimeLocation(double lat, double lng, float accuracy) {
        String geohash = GeoFireUtils.getGeoHashForLocation(new GeoLocation(lat, lng));
        com.google.firebase.firestore.GeoPoint geoPoint = new com.google.firebase.firestore.GeoPoint(lat, lng);
        String userId = auth.getCurrentUser().getUid();

        // ===== ACTUALIZAR 1: Colección 'usuarios' =====
        Map<String, Object> userUpdates = new HashMap<>();
        userUpdates.put("ubicacion_actual", geoPoint);
        userUpdates.put("ubicacion", geoPoint);
        userUpdates.put("ubicacion_geohash", geohash);
        userUpdates.put("updated_at", Timestamp.now());
        userUpdates.put("estado", "online");  // Marcar como online

        db.collection("usuarios").document(userId)
                .update(userUpdates)
                .addOnFailureListener(e -> Log.w(TAG, "Error actualizando ubicación usuario", e));

        // ===== ACTUALIZAR 2: Colección 'paseadores_search' (CRÍTICO para BusquedaPaseadoresActivity) =====
        Map<String, Object> searchUpdates = new HashMap<>();
        searchUpdates.put("ubicacion_actual", geoPoint);
        searchUpdates.put("ubicacion_geohash", geohash);
        searchUpdates.put("estado", "online");
        searchUpdates.put("updated_at", Timestamp.now());

        db.collection("paseadores_search").document(userId)
                .update(searchUpdates)
                .addOnFailureListener(e -> Log.w(TAG, "Error actualizando en paseadores_search", e));

        // ===== ACTUALIZAR 3: Reserva (CRÍTICO - Esto es lo que ve el mapa del dueño) =====
        // FIX: Guardar ubicacion_actual cada 5s sin importar el batch
        // Esto asegura que PaseoEnCursoDuenoActivity vea la ubicación actual en tiempo real
        if (currentReservaId != null && !currentReservaId.isEmpty()) {
            Map<String, Object> reservaUpdates = new HashMap<>();
            reservaUpdates.put("ubicacion_actual", geoPoint);
            reservaUpdates.put("ubicacion_actual_paseador", geoPoint);
            reservaUpdates.put("updated_at", Timestamp.now());

            // ===== NUEVA LÓGICA: Guardar timestamp de detención si está quieto > 8 min =====
            // IMPORTANTE: Si nunca se movió desde el inicio, usar ultimoMovimientoDetectado como base
            if (tiempoSinMovimiento > PAUSA_COMPLETA_THRESHOLD_MS) {
                long timestampQuieto = tiempoDetenciónInicio > 0 ? tiempoDetenciónInicio : ultimoMovimientoDetectado;
                reservaUpdates.put("paseador_quieto_desde", new com.google.firebase.Timestamp(timestampQuieto / 1000, 0));
                Log.d(TAG, "🛑 Guardando timestamp de detención en Firestore (quieto desde: " + timestampQuieto + ")");
            } else {
                // Si se movió nuevamente o está en primeros 8 minutos, limpiar el campo
                reservaUpdates.put("paseador_quieto_desde", null);
            }

            db.collection("reservas").document(currentReservaId)
                    .update(reservaUpdates)
                    .addOnSuccessListener(aVoid -> {
                        Log.v(TAG, "✅ ubicacion_actual actualizada en reserva (fallback Firestore para mapa)");
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "⚠️ Error actualizando ubicacion_actual en reserva - El WebSocket debería mantener mapa actualizado", e);
                        // Si falla, el WebSocket debería estar manejando los updates en tiempo real
                    });
        }

        Log.d(TAG, "📍 Ubicación actualizada en: usuarios + paseadores_search + reserva (" + lat + ", " + lng + ")");
    }

    /**
     * Agrega ubicación al batch para escritura eficiente
     * OPTIMIZACIÓN: Reduce escrituras de Firestore de ~240/hora a ~12/hora
     *
     * CAMBIO: Si paseador está quieto > 8 minutos, NO agrega más ubicaciones al array
     * CAMBIO2: Durante los primeros 8 minutos, elimina el filtro de 3 metros para capturar TODAS las ubicaciones
     */
    private void addLocationToBatch(Location location) {
        // ===== NUEVA LÓGICA: Si está quieto > 8 minutos, NO agregar más ubicaciones =====
        if (tiempoSinMovimiento > PAUSA_COMPLETA_THRESHOLD_MS) {
            Log.d(TAG, "⏸️ Paseador QUIETO > 8 minutos - NO agregando más ubicaciones al array");
            // Pero updateLocationInFirestore() aún actualiza ubicacion_actual cada 30s
            return;
        }

        // ===== NUEVA LÓGICA: Durante primeros 8 minutos, CAPTURAR TODAS las ubicaciones SIN filtro =====
        // Esto permite una ruta más precisa durante el paseo activo
        if (tiempoSinMovimiento <= PAUSA_COMPLETA_THRESHOLD_MS) {
            // DURANTE LOS PRIMEROS 8 MINUTOS: No aplicar filtro de 3 metros
            // Agregar todas las ubicaciones para ruta precisa
            Log.v(TAG, "📍 [ACTIVO] Agregando ubicación sin filtro de 3m (primeros 8 minutos)");
        } else {
            // Después de 8 minutos quieto: aplicar filtro (pero ya habría retornado arriba)
            if (!locationBatch.isEmpty()) {
                Map<String, Object> ultimaUbicacion = locationBatch.get(locationBatch.size() - 1);
                double lastLat = (double) ultimaUbicacion.get("lat");
                double lastLng = (double) ultimaUbicacion.get("lng");

                float[] results = new float[1];
                Location.distanceBetween(lastLat, lastLng,
                                        location.getLatitude(), location.getLongitude(),
                                        results);

                if (results[0] < 3.0f) { // Menos de 3 metros de diferencia
                    Log.v(TAG, "📍 Ubicación duplicada ignorada (distancia: " + String.format("%.1f", results[0]) + "m)");
                    return; // No agregar duplicado
                }
            }
        }

        // ===== OPTIMIZACIÓN: Comprimir precisión de coordenadas =====
        // 6 decimales = ~11cm de precisión (suficiente para tracking)
        // Reduce tamaño de datos en ~30-40%
        double latRedondeada = Math.round(location.getLatitude() * 1000000.0) / 1000000.0;
        double lngRedondeada = Math.round(location.getLongitude() * 1000000.0) / 1000000.0;
        float accRedondeada = Math.round(location.getAccuracy() * 10.0f) / 10.0f; // 1 decimal

        Map<String, Object> puntoMap = new HashMap<>();
        puntoMap.put("lat", latRedondeada);
        puntoMap.put("lng", lngRedondeada);
        puntoMap.put("acc", accRedondeada);
        puntoMap.put("ts", Timestamp.now());
        puntoMap.put("speed", Math.round(location.getSpeed() * 100.0f) / 100.0f); // 2 decimales

        locationBatch.add(puntoMap);

        long now = System.currentTimeMillis();

        // ===== OPTIMIZACIÓN: Batch timeout dinámico =====
        // Si el dueño está mirando: 20s (para fallback rápido)
        // Si no: 60s/120s (ahorro de batería)
        long timeoutDinamico;
        
        if (duenoViendoMapa) {
            timeoutDinamico = 20000; // 20 segundos si el dueño mira
        } else {
            timeoutDinamico = currentSpeed > SPEED_THRESHOLD_MPS ?
                    BATCH_TIMEOUT_MS / 2 :  // 60s en movimiento
                    BATCH_TIMEOUT_MS;       // 120s parado
        }

        boolean shouldSend = locationBatch.size() >= BATCH_SIZE ||
                            (now - lastBatchSendTime) >= timeoutDinamico;

        if (shouldSend) {
            sendLocationBatch();
        }
    }

    /**
     * Envía batch de ubicaciones a Firestore con reintentos exponenciales
     * Usa subcollection si hay > 500 puntos para evitar límite de 1MB
     */
    private void sendLocationBatch() {
        // Primero, intentar reenviar batch fallido anterior (si existe)
        if (failedBatch != null && !failedBatch.isEmpty()) {
            long now = System.currentTimeMillis();
            long timeSinceLastRetry = now - lastBatchRetryTime;
            long retryDelay = BATCH_RETRY_DELAY_MS * (1L << batchRetryCount);  // Exponencial: 2s, 4s, 8s

            if (timeSinceLastRetry > retryDelay && batchRetryCount < MAX_BATCH_RETRIES) {
                Log.w(TAG, "🔄 Reintentando batch fallido (" + (batchRetryCount + 1) + "/" + MAX_BATCH_RETRIES + ") en " + retryDelay + "ms");
                sendBatchToFirestore(failedBatch);
                lastBatchRetryTime = now;
                batchRetryCount++;
                return;  // No procesar nuevo batch hasta que se resuelva el fallido
            } else if (batchRetryCount >= MAX_BATCH_RETRIES) {
                Log.e(TAG, "❌ Batch fallido después de " + MAX_BATCH_RETRIES + " reintentos, descartando " + failedBatch.size() + " ubicaciones");
                failedBatch = null;
                batchRetryCount = 0;
            }
        }

        if (locationBatch.isEmpty()) return;

        List<Map<String, Object>> batchToSend = new ArrayList<>(locationBatch);
        locationBatch.clear();
        lastBatchSendTime = System.currentTimeMillis();

        sendBatchToFirestore(batchToSend);
    }

    /**
     * Helper method que actualiza Firestore con el batch
     * Registra fallos para reintentos exponenciales
     */
    private void sendBatchToFirestore(List<Map<String, Object>> batchToSend) {
        DocumentReference reservaRef = db.collection("reservas").document(currentReservaId);

        // ===== MIGRACIÓN A SUBCOLLECTION SI ES NECESARIO =====
        if (usandoSubcollection || ubicacionesCount > MAX_UBICACIONES_IN_ARRAY) {
            if (!usandoSubcollection) {
                Log.d(TAG, "🔄 Migrando a subcollection: se alcanzaron " + ubicacionesCount + " ubicaciones");
                usandoSubcollection = true;
            }
            // Guardar en subcollection
            guardarEnSubcollection(batchToSend);
        } else {
            // Guardar en array principal con reintentos
            reservaRef.update("ubicaciones", FieldValue.arrayUnion(batchToSend.toArray()))
                    .addOnSuccessListener(aVoid -> {
                        ubicacionesCount += batchToSend.size();
                        failedBatch = null;  // Clear failed batch on success
                        batchRetryCount = 0;
                        Log.d(TAG, "✅ Batch enviado: " + batchToSend.size() + " ubicaciones (total: " + ubicacionesCount + ")");
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "❌ Error guardando batch: " + e.getMessage() + " - Almacenando para reintentos");
                        // Almacenar para reintentos exponenciales
                        failedBatch = new ArrayList<>(batchToSend);
                        lastBatchRetryTime = System.currentTimeMillis();
                        batchRetryCount = 0;

                        // También intentar guardado individual como fallback final
                        Log.w(TAG, "Intentando guardar puntos individualmente como fallback final...");
                        for (Map<String, Object> punto : batchToSend) {
                            reservaRef.update("ubicaciones", FieldValue.arrayUnion(punto))
                                    .addOnSuccessListener(v -> ubicacionesCount++)
                                    .addOnFailureListener(e2 -> Log.e(TAG, "Error guardando punto individual", e2));
                        }
                    });
        }
    }

    /**
     * Guarda ubicaciones en subcollection para evitar límite de 1MB
     */
    private void guardarEnSubcollection(List<Map<String, Object>> ubicaciones) {
        DocumentReference reservaRef = db.collection("reservas").document(currentReservaId);

        WriteBatch batch = db.batch();
        for (Map<String, Object> punto : ubicaciones) {
            // Usar timestamp como ID del documento
            String docId = String.valueOf(System.currentTimeMillis()) + "_" + secureRandom.nextDouble();
            DocumentReference ubicacionRef = reservaRef.collection("ubicaciones_historico").document(docId);
            batch.set(ubicacionRef, punto);
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    ubicacionesCount += ubicaciones.size();
                    Log.d(TAG, " Batch guardado en subcollection: " + ubicaciones.size() + " puntos (total: " + ubicacionesCount + ")");
                })
                .addOnFailureListener(e -> Log.e(TAG, " Error guardando en subcollection", e));
    }

    /**
     * Guarda la distancia acumulada en Firestore cada 30s
     */
    private void guardarDistanciaAcumulada() {
        // Validación defensiva - prevenir NullPointerException
        if (currentReservaId == null || currentReservaId.isEmpty()) {
            Log.w(TAG, " No se puede guardar distancia - currentReservaId es null o vacío");
            return;
        }

        DocumentReference reservaRef = db.collection("reservas").document(currentReservaId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("distancia_acumulada_metros", distanciaAcumuladaMetros);
        updates.put("distancia_km", Math.round(distanciaAcumuladaMetros / 10.0) / 100.0); // Redondear a 2 decimales

        reservaRef.update(updates)
                .addOnSuccessListener(aVoid ->
                    Log.v(TAG, " Distancia guardada: " + String.format("%.2f", distanciaAcumuladaMetros / 1000) + " km")
                )
                .addOnFailureListener(e -> Log.w(TAG, "Error guardando distancia", e));
    }

    /**
     * MODO PAUSA: Detiene el GPS completamente cuando no hay movimiento > 3 minutos
     * Ahorro de batería: ~15-20% durante la pausa
     */
    private void activarModoPausa() {
        if (gpsPausado) return; // Ya está pausado

        // Detener actualizaciones de GPS
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            gpsPausado = true;
            Log.i(TAG, " GPS APAGADO - Modo pausa activado (ahorro ~95% batería GPS)");

            // Actualizar notificación
            actualizarNotificacion("Paseo en pausa - GPS en espera");

            // Guardar estado en Firestore
            db.collection("reservas").document(currentReservaId)
                    .update("gps_pausado", true, "tiempo_pausa_inicio", System.currentTimeMillis())
                    .addOnFailureListener(e -> Log.w(TAG, "Error guardando estado pausa", e));

            // Programar chequeo periódico con ubicación de red (sin GPS)
            iniciarChequeoUbicacionRed();
        }
    }

    /**
     * Reactiva el GPS cuando se detecta movimiento después de una pausa
     */
    private void reactivarGPSDesdePausa() {
        if (!gpsPausado) return; // No está pausado

        gpsPausado = false;
        Log.i(TAG, "▶️ Reactivando GPS desde modo pausa");

        // Actualizar notificación
        actualizarNotificacion("Paseo en curso - GPS activo");

        // Reanudar actualizaciones de GPS
        requestLocationUpdates();

        // Actualizar estado en Firestore
        long tiempoPausa = System.currentTimeMillis() - ultimoMovimientoDetectado;
        db.collection("reservas").document(currentReservaId)
                .update("gps_pausado", false, "ultima_pausa_duracion_ms", tiempoPausa)
                .addOnFailureListener(e -> Log.w(TAG, "Error actualizando estado pausa", e));

        // Resetear timer de pausa
        tiempoSinMovimiento = 0;
        ultimoMovimientoDetectado = System.currentTimeMillis();
    }

    /**
     * FALLBACK: Obtener última ubicación conocida cuando falla red/GPS
     * Casos de uso:
     * - GPS está apagado (Modo Pausa) + Sin red celular (dead zone)
     * - WebSocket inactivo + Sin conectividad
     * Antigüedad máxima permitida: 10 minutos
     */
    private Location obtenerUltimaUbicacionConocida() {
        if (lastLocation != null) {
            long antigüedad = System.currentTimeMillis() - lastLocation.getTime();
            if (antigüedad < 600000) { // Menos de 10 minutos
                Log.d(TAG, "📍 Fallback: usando última ubicación conocida (antigüedad: " +
                      (antigüedad / 1000) + "s, precisión ±" + (int)lastLocation.getAccuracy() + "m)");
                return lastLocation;
            }
        }
        return null;
    }

    /**
     * Chequeo periódico con ubicación de red (Cell Tower) durante pausas
     * Consume ~0.5% vs ~10% del GPS
     */
    private void iniciarChequeoUbicacionRed() {
        // Solicitar ubicación de red cada 5 minutos durante la pausa
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (gpsPausado && currentReservaId != null) {
                    // Obtener ubicación aproximada sin GPS
                    if (ActivityCompat.checkSelfPermission(LocationService.this,
                            Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                        fusedLocationClient.getCurrentLocation(
                                Priority.PRIORITY_LOW_POWER, // Solo red celular
                                null
                        ).addOnSuccessListener(location -> {
                            if (location != null) {
                                Log.d(TAG, "📍 Ubicación de red durante pausa (±" + (int)location.getAccuracy() + "m)");

                                // Verificar si hay movimiento significativo
                                if (lastLocation != null) {
                                    float distancia = lastLocation.distanceTo(location);
                                    if (distancia > 50) { // Movimiento > 50m
                                        Log.i(TAG, "▶️ Movimiento significativo detectado - Reactivando GPS");
                                        reactivarGPSDesdePausa();
                                        return;
                                    }
                                }
                            }
                        }).addOnFailureListener(e -> {
                            // RED NO DISPONIBLE - Usar fallback (dead zone)
                            Log.w(TAG, " Red no disponible durante pausa, intentando fallback");
                            Location fallback = obtenerUltimaUbicacionConocida();
                            if (fallback != null) {
                                Log.d(TAG, "📍 Fallback activado: última ubicación disponible");

                                // Verificar movimiento con fallback
                                if (lastLocation != null) {
                                    float distancia = lastLocation.distanceTo(fallback);
                                    if (distancia > 50) { // Movimiento > 50m
                                        Log.i(TAG, "▶️ Movimiento significativo detectado (fallback) - Reactivando GPS");
                                        reactivarGPSDesdePausa();
                                    }
                                }
                            } else {
                                Log.w(TAG, " Sin fallback disponible - última ubicación muy antigua o null");
                            }
                        });
                    }

                    // Repetir en 5 minutos
                    handler.postDelayed(this, 300000);
                }
            }
        }, 300000); // 5 minutos
    }

    /**
     * Actualiza la notificación del servicio foreground
     */
    private void actualizarNotificacion(String texto) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Paseo en curso")
                        .setContentText(texto)
                        .setSmallIcon(R.drawable.walki_logo_secundario)
                        .setOngoing(true)
                        .build();
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
    }


    private void stopTracking() {
        Log.d(TAG, "Deteniendo servicio de rastreo.");

        // Remover listener de estado
        if (estadoListener != null) {
            estadoListener.remove();
            estadoListener = null;
            Log.d(TAG, " Listener de estado removido");
        }

        // Enviar batch final antes de detener
        sendLocationBatch();

        // ===== GUARDAR DISTANCIA FINAL - SOLO SI currentReservaId NO ES NULL =====
        if (currentReservaId != null && !currentReservaId.isEmpty()) {
            guardarDistanciaAcumulada();
        } else {
            Log.w(TAG, " No se guardó distancia final - currentReservaId es null");
        }

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        // Limpiar receiver de batería
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException e) {
                // Ya estaba desregistrado
            }
            batteryReceiver = null;
        }

        // Limpiar estado
        currentEstado = null;
        currentReservaId = null;

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Remover listener de estado si aún existe
        if (estadoListener != null) {
            estadoListener.remove();
            estadoListener = null;
        }

        // Remover listener de conexión de socket
        if (socketConnectionListener != null) {
            socketManager.removeOnConnectionListener(socketConnectionListener);
            socketConnectionListener = null;
        }

        // Asegurar limpieza de recursos
        sendLocationBatch();
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException e) {
                // Ignorar
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Rastreo de Paseo",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Mantiene activo el rastreo GPS durante el paseo");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification getNotification() {
        Intent notificationIntent = new Intent(this, PaseoEnCursoActivity.class);
        // Importante: permitir volver a la actividad existente
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // Si tenemos ID, lo pasamos para que la actividad sepa qué cargar al abrirse
        if (currentReservaId != null) {
            notificationIntent.putExtra("reserva_id", currentReservaId);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Paseo en curso")
                .setContentText("Compartiendo ubicación en tiempo real...")
                .setSmallIcon(R.drawable.walki_logo_secundario) 
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // No binding needed for now
    }
}
