package com.mjc.mascotalink.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
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
import com.mjc.mascotalink.PaseoEnCursoActivity;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.network.SocketManager;

import java.util.HashMap;
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

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // socketManager = SocketManager.getInstance(this); // Injected by Hilt
        // db = FirebaseFirestore.getInstance(); // Injected by Hilt
        // auth = FirebaseAuth.getInstance(); // Injected by Hilt

        createNotificationChannel();
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
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(3000)
                .setMinUpdateDistanceMeters(5)
                .setWaitForAccurateLocation(false)
                .build();

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

    private void processLocation(Location location) {
        if (currentReservaId == null || auth.getCurrentUser() == null) return;

        double lat = location.getLatitude();
        double lng = location.getLongitude();
        float accuracy = location.getAccuracy();
        long now = System.currentTimeMillis();

        // 1. Enviar por WebSocket (Prioridad máxima, cada actualización válida)
        if (socketManager.isConnected()) {
            socketManager.updateLocation(currentReservaId, lat, lng, accuracy);
        }

        // 2. Actualizar ubicación en tiempo real del usuario (para búsquedas/mapa general)
        if (now - lastRealtimeUpdateTime > REALTIME_UPDATE_INTERVAL_MS) {
            updateUserRealtimeLocation(lat, lng, accuracy);
            lastRealtimeUpdateTime = now;
        }

        // 3. Guardar historial en la reserva (Throttled para ahorrar costos y DB)
        if (now - lastFirestoreSaveTime > FIRESTORE_SAVE_INTERVAL_MS) {
            saveLocationToHistory(location);
            lastFirestoreSaveTime = now;
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

    private void saveLocationToHistory(Location location) {
        Map<String, Object> puntoMap = new HashMap<>();
        puntoMap.put("lat", location.getLatitude());
        puntoMap.put("lng", location.getLongitude());
        puntoMap.put("acc", location.getAccuracy());
        puntoMap.put("ts", Timestamp.now());
        puntoMap.put("speed", location.getSpeed());

        db.collection("reservas").document(currentReservaId)
                .update("ubicaciones", FieldValue.arrayUnion(puntoMap))
                .addOnFailureListener(e -> Log.w(TAG, "Error guardando historial", e));
    }

    private void stopTracking() {
        Log.d(TAG, "Deteniendo servicio de rastreo.");
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        stopForeground(true);
        stopSelf();
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
