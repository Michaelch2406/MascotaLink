package com.mjc.mascotalink.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.LocationRequest;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestor de presencia - Actualiza la ubicación cuando el usuario se logea
 * para que aparezca en tiempo real en el mapa de búsqueda
 */
public class PresenceManager {
    private static final String TAG = "PresenceManager";
    private FusedLocationProviderClient fusedLocationClient;
    private Context context;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    public PresenceManager(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Actualizar presencia y ubicación cuando el usuario se logea
     * Se ejecuta inmediatamente después del login
     */
    public void updatePresenceOnLogin() {
        if (auth.getCurrentUser() == null) {
            Log.w(TAG, "Usuario no autenticado");
            return;
        }

        String userId = auth.getCurrentUser().getUid();

        // Primero marcar como online
        markUserOnline(userId);

        // Intentar obtener la ubicación actual
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "✅ Permiso de ubicación disponible - Actualizando ubicación en login");
            requestCurrentLocation(userId);
        } else {
            Log.d(TAG, "⚠️ Permiso de ubicación NO disponible - Solo marcando online");
            // Sin permiso, solo marcar como online (la ubicación será old)
        }
    }

    /**
     * Marcar usuario como offline en Firestore (se llama desde MyApplication al logout)
     */
    public void markUserOffline() {
        if (auth.getCurrentUser() == null) {
            Log.w(TAG, "No hay usuario autenticado para marcar offline");
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        Map<String, Object> updates = new HashMap<>();
        updates.put("estado", "offline");
        updates.put("en_linea", false);
        updates.put("last_seen", Timestamp.now());

        // Actualizar en usuarios
        db.collection("usuarios").document(userId).update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Usuario marcado como offline en usuarios"))
                .addOnFailureListener(e -> Log.w(TAG, "Error marcando offline en usuarios", e));

        // Actualizar en paseadores_search
        db.collection("paseadores_search").document(userId).update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Usuario marcado como offline en paseadores_search"))
                .addOnFailureListener(e -> Log.d(TAG, "Nota: No es crítico si no existe en paseadores_search"));
    }

    /**
     * Marcar usuario como online en Firestore
     */
    private void markUserOnline(String userId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("estado", "online");
        updates.put("en_linea", true);
        updates.put("last_seen", Timestamp.now());
        updates.put("updated_at", Timestamp.now());

        // Actualizar en usuarios
        db.collection("usuarios").document(userId).update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Usuario marcado como online en usuarios"))
                .addOnFailureListener(e -> Log.w(TAG, "Error marcando online en usuarios", e));

        // Actualizar en paseadores_search
        db.collection("paseadores_search").document(userId).update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Usuario marcado como online en paseadores_search"))
                .addOnFailureListener(e -> Log.d(TAG, "Nota: No es crítico si no existe en paseadores_search (puede ser dueño)"));
    }

    /**
     * Obtener ubicación actual y actualizar en Firestore
     */
    private void requestCurrentLocation(String userId) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permiso de ubicación denegado");
            return;
        }

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            Log.d(TAG, "📍 Ubicación obtenida: " + location.getLatitude() + ", " + location.getLongitude());
                            updateLocationInFirestore(userId, location);
                        } else {
                            Log.w(TAG, "❌ No se pudo obtener ubicación (GPS puede estar desactivado)");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error obteniendo ubicación", e);
                    });

        } catch (SecurityException e) {
            Log.e(TAG, "Error de seguridad al obtener ubicación", e);
        }
    }

    /**
     * Actualizar ubicación en Firestore en las 3 colecciones
     */
    private void updateLocationInFirestore(String userId, Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        float accuracy = location.getAccuracy();

        GeoPoint geoPoint = new GeoPoint(lat, lng);
        String geohash = GeoFireUtils.getGeoHashForLocation(new GeoLocation(lat, lng));

        Map<String, Object> updates = new HashMap<>();
        updates.put("ubicacion_actual", geoPoint);
        updates.put("ubicacion", geoPoint);
        updates.put("ubicacion_geohash", geohash);
        updates.put("estado", "online");
        updates.put("en_linea", true);
        updates.put("updated_at", Timestamp.now());
        updates.put("last_seen", Timestamp.now());

        // ===== ACTUALIZAR 1: usuarios =====
        db.collection("usuarios").document(userId).update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Ubicación actualizada en usuarios"))
                .addOnFailureListener(e -> Log.w(TAG, "Error actualizando ubicación en usuarios", e));

        // ===== ACTUALIZAR 2: paseadores_search =====
        db.collection("paseadores_search").document(userId).update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Ubicación actualizada en paseadores_search"))
                .addOnFailureListener(e -> Log.d(TAG, "Nota: Usuario no existe en paseadores_search (puede ser dueño)"));

        Log.d(TAG, "📡 Ubicación enviada a Firestore: (" + lat + ", " + lng + ") - Geohash: " + geohash);
    }
}
