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

    // Throttling para Ubicación Actual (búsqueda)
    private long lastRealtimeUpdateTime = 0;
    private static final long REALTIME_UPDATE_INTERVAL_MS = 5000; // 5 segundos para "ubicacion_actual"

    // ===== OPTIMIZACIONES DE BATERÍA Y GPS =====

    // Batching de ubicaciones
    private List<Map<String, Object>> locationBatch = new ArrayList<>();
    private static final int BATCH_SIZE = 5; // Enviar cada 5 ubicaciones
    private static final long BATCH_TIMEOUT_MS = 60000; // O cada 60 segundos
    private long lastBatchSendTime = 0;

    // Detección de movimiento
    private Location lastLocation = null;
    private float currentSpeed = 0f;
    private static final float SPEED_THRESHOLD_MPS = 1.4f; // ~5 km/h
    private static final float STATIONARY_THRESHOLD_METERS = 10f;

    // Estado de batería
    private boolean isLowBattery = false;
    private static final int LOW_BATTERY_THRESHOLD = 20; // 20%
    private BroadcastReceiver batteryReceiver;

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

        // 2. Unirse al room del Socket
        if (socketManager.isConnected()) {
            socketManager.joinPaseo(currentReservaId);
        } else {
            socketManager.connect();
            // El listener de conexión en SocketManager o Activity debería encargarse de unirse,
            // pero aquí forzamos un reintento simple o dependemos de la reconexión automática.
            // Idealmente, SocketManager debería tener una cola de "rooms pending join".
        }

        // 3. Solicitar actualizaciones de ubicación
        requestLocationUpdates();
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
     * Construye un LocationRequest optimizado basado en batería y movimiento
     */
    private LocationRequest buildOptimalLocationRequest() {
        long interval;
        long minInterval;
        float minDistance;
        int priority;

        if (isLowBattery) {
            // Modo ahorro de batería: menos frecuente, menos precisión
            interval = 15000; // 15 segundos
            minInterval = 10000; // 10 segundos
            minDistance = 20; // 20 metros
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            Log.d(TAG, "GPS en modo AHORRO DE BATERÍA");
        } else if (currentSpeed < SPEED_THRESHOLD_MPS) {
            // Usuario casi detenido: reducir frecuencia
            interval = 10000; // 10 segundos
            minInterval = 7000; // 7 segundos
            minDistance = 10; // 10 metros
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            Log.d(TAG, "GPS en modo DETENIDO (baja velocidad)");
        } else {
            // Usuario en movimiento normal: alta precisión
            interval = 5000; // 5 segundos
            minInterval = 3000; // 3 segundos
            minDistance = 5; // 5 metros
            priority = Priority.PRIORITY_HIGH_ACCURACY;
            Log.d(TAG, "GPS en modo ALTA PRECISIÓN (movimiento)");
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

        double lat = location.getLatitude();
        double lng = location.getLongitude();
        float accuracy = location.getAccuracy();
        long now = System.currentTimeMillis();

        // Calcular velocidad y distancia desde última ubicación
        if (lastLocation != null) {
            float distance = lastLocation.distanceTo(location);
            long timeDelta = location.getTime() - lastLocation.getTime();

            if (timeDelta > 0) {
                currentSpeed = distance / (timeDelta / 1000f); // m/s
            }

            // Filtrar ubicaciones estacionarias con poca precisión
            if (distance < STATIONARY_THRESHOLD_METERS && accuracy > 20) {
                Log.v(TAG, "Ubicación filtrada: muy cercana y baja precisión");
                return;
            }
        }

        lastLocation = location;

        // 1. Enviar por WebSocket (Prioridad máxima, cada actualización válida)
        // Solo si hay movimiento significativo o es la primera ubicación
        if (socketManager.isConnected()) {
            socketManager.updateLocation(currentReservaId, lat, lng, accuracy);
        }

        // 2. Actualizar ubicación en tiempo real del usuario (para búsquedas/mapa general)
        if (now - lastRealtimeUpdateTime > REALTIME_UPDATE_INTERVAL_MS) {
            updateUserRealtimeLocation(lat, lng, accuracy);
            lastRealtimeUpdateTime = now;
        }

        // 3. Agregar a batch para historial (OPTIMIZACIÓN: no guardar individualmente)
        addLocationToBatch(location);

        // Ajustar GPS si cambió la velocidad significativamente
        if (Math.abs(currentSpeed - SPEED_THRESHOLD_MPS) < 0.5f) {
            // Cerca del umbral, podría necesitar ajuste
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
        Map<String, Object> puntoMap = new HashMap<>();
        puntoMap.put("lat", location.getLatitude());
        puntoMap.put("lng", location.getLongitude());
        puntoMap.put("acc", location.getAccuracy());
        puntoMap.put("ts", Timestamp.now());
        puntoMap.put("speed", location.getSpeed());

        locationBatch.add(puntoMap);

        long now = System.currentTimeMillis();
        boolean shouldSend = locationBatch.size() >= BATCH_SIZE ||
                            (now - lastBatchSendTime) >= BATCH_TIMEOUT_MS;

        if (shouldSend) {
            sendLocationBatch();
        }
    }

    /**
     * Envía batch de ubicaciones a Firestore
     * Usa update único en vez de múltiples arrayUnion
     */
    private void sendLocationBatch() {
        if (locationBatch.isEmpty()) return;

        List<Map<String, Object>> batchToSend = new ArrayList<>(locationBatch);
        locationBatch.clear();
        lastBatchSendTime = System.currentTimeMillis();

        // Usar WriteBatch para operaciones atómicas (más eficiente)
        DocumentReference reservaRef = db.collection("reservas").document(currentReservaId);

        // IMPORTANTE: Firestore tiene límite de 1MB por documento
        // Si el array crece mucho, usar sub-colección en producción
        reservaRef.update("ubicaciones", FieldValue.arrayUnion(batchToSend.toArray()))
                .addOnSuccessListener(aVoid ->
                    Log.d(TAG, "✅ Batch enviado: " + batchToSend.size() + " ubicaciones")
                )
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error guardando batch, reintentando individuales", e);
                    // Fallback: guardar individualmente
                    for (Map<String, Object> punto : batchToSend) {
                        reservaRef.update("ubicaciones", FieldValue.arrayUnion(punto));
                    }
                });
    }

    private void stopTracking() {
        Log.d(TAG, "Deteniendo servicio de rastreo.");

        // Enviar batch final antes de detener
        sendLocationBatch();

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

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
