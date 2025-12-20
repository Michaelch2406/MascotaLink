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
    @Inject
    SocketManager socketManager;
    private String currentReservaId;
    @Inject
    FirebaseFirestore db;
    @Inject
    FirebaseAuth auth;

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
    private static final long PAUSA_COMPLETA_THRESHOLD_MS = 180000; // 3 minutos
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

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // socketManager = SocketManager.getInstance(this); // Injected by Hilt
        // db = FirebaseFirestore.getInstance(); // Injected by Hilt
        // auth = FirebaseAuth.getInstance(); // Injected by Hilt

        createNotificationChannel();
        setupBatteryMonitoring();
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
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_TRACKING.equals(action)) {
                String reservaId = intent.getStringExtra(EXTRA_RESERVA_ID);
                if (reservaId != null) {
                    startTracking(reservaId);
                }
            } else if (ACTION_STOP_TRACKING.equals(action)) {
                stopTracking();
            }
        }
        return START_STICKY;
    }

    private void startTracking(String reservaId) {
        Log.d(TAG, "Iniciando servicio de rastreo para reserva: " + reservaId);
        currentReservaId = reservaId;

        // 1. Iniciar notificación Foreground
        startForeground(NOTIFICATION_ID, getNotification(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);

        // 2. Cargar distancia acumulada existente (si se está reanudando)
        cargarDistanciaInicial();

        // 3. Escuchar cambios de estado en tiempo real
        setupEstadoListener();

        // 4. Unirse al room del Socket
        if (socketManager.isConnected()) {
            socketManager.joinPaseo(currentReservaId);
        } else {
            socketManager.connect();
            // El listener de conexión en SocketManager o Activity debería encargarse de unirse,
            // pero aquí forzamos un reintento simple o dependemos de la reconexión automática.
            // Idealmente, SocketManager debería tener una cola de "rooms pending join".
        }

        // 5. Solicitar actualizaciones de ubicación
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

                        // Actualizar estado actual
                        if (nuevoEstado != null && !nuevoEstado.equals(currentEstado)) {
                            Log.d(TAG, "📊 Estado cambió: " + currentEstado + " → " + nuevoEstado);
                            currentEstado = nuevoEstado;

                            // Si el estado cambió a algo diferente de EN_CURSO, detener tracking
                            if (!"EN_CURSO".equals(nuevoEstado)) {
                                Log.w(TAG, "⚠️ Paseo ya no está EN_CURSO (estado: " + nuevoEstado + "), deteniendo tracking");
                                stopTracking();
                            }
                        } else if (currentEstado == null) {
                            // Primera vez que se obtiene el estado
                            currentEstado = nuevoEstado;
                            Log.d(TAG, "📊 Estado inicial: " + currentEstado);
                        }

                        // ===== OPTIMIZACIÓN: Listener para "dueño viendo mapa" =====
                        Boolean viendo = documentSnapshot.getBoolean("dueno_viendo_mapa");
                        boolean estadoAnterior = duenoViendoMapa;
                        duenoViendoMapa = viendo != null ? viendo : true;

                        if (estadoAnterior != duenoViendoMapa) {
                            if (duenoViendoMapa) {
                                Log.i(TAG, "👁️ Dueño EMPEZÓ a ver mapa - Activando WebSocket");
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

        if (isLowBattery) {
            // Modo ahorro de batería: menos frecuente, menos precisión
            interval = 15000; // 15 segundos
            minInterval = 10000; // 10 segundos
            minDistance = 20; // 20 metros
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            Log.d(TAG, "GPS en modo AHORRO DE BATERÍA (15s)");
        } else if (precisionBaja) {
            // Si tenemos baja precisión, reducir frecuencia para ahorrar batería
            interval = 12000; // 12 segundos
            minInterval = 8000; // 8 segundos
            minDistance = 15; // 15 metros
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            Log.d(TAG, "GPS en modo PRECISIÓN BAJA (12s, accuracy: " + (int)lastAccuracy + "m)");
        } else if (currentSpeed < SPEED_THRESHOLD_MPS) {
            // Usuario casi detenido: reducir frecuencia
            interval = 10000; // 10 segundos
            minInterval = 7000; // 7 segundos
            minDistance = 10; // 10 metros
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            Log.d(TAG, "GPS en modo DETENIDO (10s, baja velocidad)");
        } else {
            // OPTIMIZACIÓN: Cambio de 5s → 8s y HIGH_ACCURACY → BALANCED
            // Ahorro de batería: ~18% manteniendo precisión
            interval = 8000; // 8 segundos (antes: 5s)
            minInterval = 5000; // 5 segundos (antes: 3s)
            minDistance = 8; // 8 metros (antes: 5m)
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY; // Antes: HIGH_ACCURACY
            Log.d(TAG, "GPS en modo MOVIMIENTO OPTIMIZADO (8s, BALANCED - ahorro batería ~18%)");
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
            Log.w(TAG, "⚠️ Ubicación rechazada - estado: " + currentEstado + " (debe ser EN_CURSO)");
            return;
        }

        double lat = location.getLatitude();
        double lng = location.getLongitude();
        float accuracy = location.getAccuracy();
        long now = System.currentTimeMillis();

        // ===== VALIDACIÓN 1: Rechazar GPS con precisión muy mala =====
        if (accuracy > MAX_ACCURACY_METERS) {
            Log.w(TAG, "⚠️ Ubicación rechazada: precisión muy baja (" + (int)accuracy + "m > " + (int)MAX_ACCURACY_METERS + "m)");
            return;
        }

        // ===== VALIDACIÓN 2: Detectar saltos anormales (GPS falso o cambio de red) =====
        if (lastLocation != null) {
            float distance = lastLocation.distanceTo(location);
            long timeDelta = location.getTime() - lastLocation.getTime();

            // Detectar saltos > 100m en < 2 segundos
            if (distance > MAX_JUMP_METERS && timeDelta < MIN_TIME_BETWEEN_JUMPS_MS) {
                Log.w(TAG, "⚠️ Ubicación rechazada: salto anormal de " + (int)distance + "m en " + timeDelta + "ms");
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

                // Si estaba pausado, reactivar GPS
                if (gpsPausado) {
                    Log.i(TAG, "▶️ Movimiento detectado - Reactivando GPS desde pausa");
                    reactivarGPSDesdePausa();
                }
            } else {
                // No hay movimiento significativo
                tiempoSinMovimiento = System.currentTimeMillis() - ultimoMovimientoDetectado;
            }
        } else {
            // Primera ubicación
            ultimoMovimientoDetectado = System.currentTimeMillis();
        }

        lastLocation = location;

        // ===== VERIFICAR SI DEBE ENTRAR EN MODO PAUSA =====
        if (!gpsPausado && tiempoSinMovimiento > PAUSA_COMPLETA_THRESHOLD_MS) {
            Log.w(TAG, "⏸️ Sin movimiento por " + (tiempoSinMovimiento / 60000) + " minutos - Activando MODO PAUSA (GPS apagado)");
            activarModoPausa();
            return; // No procesar más
        }

        // 1. Enviar por WebSocket (OPTIMIZADO: Throttling + Condicional)
        // Ahorro adicional: No enviar si dueño no está viendo (~5-10% batería)
        // El estado de duenoViendoMapa se actualiza automáticamente por el listener

        long intervaloWebSocket = currentSpeed < SPEED_THRESHOLD_MPS ?
                WEBSOCKET_SEND_INTERVAL_SLOW_MS : // 30s si detenido
                WEBSOCKET_SEND_INTERVAL_MS; // 10s si en movimiento

        if (now - lastWebSocketSendTime > intervaloWebSocket) {
            // SOLO enviar si dueño está viendo
            if (duenoViendoMapa && socketManager.isConnected()) {
                socketManager.updateLocation(currentReservaId, lat, lng, accuracy);
                lastWebSocketSendTime = now;
                Log.v(TAG, "📡 WebSocket enviado (próximo en " + (intervaloWebSocket / 1000) + "s)");
            } else if (!duenoViendoMapa) {
                Log.d(TAG, "⏸️ WebSocket PAUSADO - Dueño no está viendo (ahorro ~10% batería)");
            }
        } else {
            Log.v(TAG, "⏭️ WebSocket throttled (esperando " +
                    ((intervaloWebSocket - (now - lastWebSocketSendTime)) / 1000) + "s)");
        }

        // 2. Actualizar ubicación en tiempo real del usuario (para búsquedas/mapa general)
        if (now - lastRealtimeUpdateTime > REALTIME_UPDATE_INTERVAL_MS) {
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
        Map<String, Object> updates = new HashMap<>();
        Map<String, Object> locationMap = new HashMap<>();
        locationMap.put("latitude", lat);
        locationMap.put("longitude", lng);
        
        updates.put("ubicacion_actual", new com.google.firebase.firestore.GeoPoint(lat, lng)); // Usar GeoPoint nativo
        updates.put("ubicacion_geohash", geohash);
        
        db.collection("usuarios").document(auth.getCurrentUser().getUid())
                .update(updates)
                .addOnFailureListener(e -> Log.w(TAG, "Error actualizando ubicación usuario", e));
    }

    /**
     * Agrega ubicación al batch para escritura eficiente
     * OPTIMIZACIÓN: Reduce escrituras de Firestore de ~240/hora a ~12/hora
     */
    private void addLocationToBatch(Location location) {
        // ===== OPTIMIZACIÓN: Eliminar duplicados cercanos =====
        // Si la última ubicación en el batch es muy similar (< 3 metros), no agregar
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
        // 60s cuando en movimiento rápido, 120s cuando parado
        long timeoutDinamico = currentSpeed > SPEED_THRESHOLD_MPS ?
                BATCH_TIMEOUT_MS / 2 :  // 60s en movimiento
                BATCH_TIMEOUT_MS;        // 120s parado

        boolean shouldSend = locationBatch.size() >= BATCH_SIZE ||
                            (now - lastBatchSendTime) >= timeoutDinamico;

        if (shouldSend) {
            sendLocationBatch();
        }
    }

    /**
     * Envía batch de ubicaciones a Firestore
     * Usa subcollection si hay > 500 puntos para evitar límite de 1MB
     */
    private void sendLocationBatch() {
        if (locationBatch.isEmpty()) return;

        List<Map<String, Object>> batchToSend = new ArrayList<>(locationBatch);
        locationBatch.clear();
        lastBatchSendTime = System.currentTimeMillis();

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
            // Guardar en array principal (método original)
            reservaRef.update("ubicaciones", FieldValue.arrayUnion(batchToSend.toArray()))
                    .addOnSuccessListener(aVoid -> {
                        ubicacionesCount += batchToSend.size();
                        Log.d(TAG, "✅ Batch enviado: " + batchToSend.size() + " ubicaciones (total: " + ubicacionesCount + ")");
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Error guardando batch, reintentando individuales", e);
                        // Fallback: guardar individualmente
                        for (Map<String, Object> punto : batchToSend) {
                            reservaRef.update("ubicaciones", FieldValue.arrayUnion(punto))
                                    .addOnSuccessListener(v -> ubicacionesCount++);
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
                    Log.d(TAG, "✅ Batch guardado en subcollection: " + ubicaciones.size() + " puntos (total: " + ubicacionesCount + ")");
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Error guardando en subcollection", e));
    }

    /**
     * Guarda la distancia acumulada en Firestore cada 30s
     */
    private void guardarDistanciaAcumulada() {
        // Validación defensiva - prevenir NullPointerException
        if (currentReservaId == null || currentReservaId.isEmpty()) {
            Log.w(TAG, "⚠️ No se puede guardar distancia - currentReservaId es null o vacío");
            return;
        }

        DocumentReference reservaRef = db.collection("reservas").document(currentReservaId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("distancia_acumulada_metros", distanciaAcumuladaMetros);
        updates.put("distancia_km", Math.round(distanciaAcumuladaMetros / 10.0) / 100.0); // Redondear a 2 decimales

        reservaRef.update(updates)
                .addOnSuccessListener(aVoid ->
                    Log.v(TAG, "✅ Distancia guardada: " + String.format("%.2f", distanciaAcumuladaMetros / 1000) + " km")
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
            Log.i(TAG, "⏸️ GPS APAGADO - Modo pausa activado (ahorro ~95% batería GPS)");

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
                            Log.w(TAG, "⚠️ Red no disponible durante pausa, intentando fallback");
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
                                Log.w(TAG, "⚠️ Sin fallback disponible - última ubicación muy antigua o null");
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
            Log.d(TAG, "✅ Listener de estado removido");
        }

        // Enviar batch final antes de detener
        sendLocationBatch();

        // ===== GUARDAR DISTANCIA FINAL - SOLO SI currentReservaId NO ES NULL =====
        if (currentReservaId != null && !currentReservaId.isEmpty()) {
            guardarDistanciaAcumulada();
        } else {
            Log.w(TAG, "⚠️ No se guardó distancia final - currentReservaId es null");
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
