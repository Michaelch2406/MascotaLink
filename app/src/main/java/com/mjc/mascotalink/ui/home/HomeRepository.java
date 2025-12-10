package com.mjc.mascotalink.ui.home;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.HashMap;
import java.util.Map;

public class HomeRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String TAG = "HomeRepository";
    private final MutableLiveData<String> lastError = new MutableLiveData<>();

    public LiveData<String> getLastError() {
        return lastError;
    }

    public LiveData<Map<String, Object>> getUserProfile(String userId) {
        MutableLiveData<Map<String, Object>> data = new MutableLiveData<>();
        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    data.setValue(snapshot.getData());
                } else {
                    Log.w(TAG, "getUserProfile: User document does not exist");
                    data.setValue(null);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "getUserProfile: Error loading user profile", e);
                lastError.setValue("Error al cargar perfil: " + e.getMessage());
                data.setValue(null);
            });
        return data;
    }
    
    public LiveData<Map<String, Object>> getWalkerStats(String userId) {
        MutableLiveData<Map<String, Object>> data = new MutableLiveData<>();
        db.collection("paseadores").document(userId).get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    data.setValue(snapshot.getData());
                } else {
                    Log.w(TAG, "getWalkerStats: Walker document does not exist");
                    // Set default stats
                    Map<String, Object> defaultStats = new HashMap<>();
                    defaultStats.put("num_servicios_completados", 0L);
                    defaultStats.put("calificacion_promedio", 0.0);
                    data.setValue(defaultStats);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "getWalkerStats: Error loading walker stats", e);
                lastError.setValue("Error al cargar estadísticas: " + e.getMessage());
                // Set default stats on error
                Map<String, Object> defaultStats = new HashMap<>();
                defaultStats.put("num_servicios_completados", 0L);
                defaultStats.put("calificacion_promedio", 0.0);
                data.setValue(defaultStats);
            });
        return data;
    }

    public LiveData<Map<String, Object>> getActiveReservation(String userId, String role) {
        MutableLiveData<Map<String, Object>> data = new MutableLiveData<>();
        String field = role.equals("PASEADOR") ? "id_paseador" : "id_dueno";
        
        // Buscar reservas EN_CURSO o EN_PROGRESO
        db.collection("reservas")
            .whereEqualTo(field, db.collection("usuarios").document(userId))
            .whereIn("estado", java.util.Arrays.asList("EN_CURSO", "EN_PROGRESO"))
            .limit(1)
            .addSnapshotListener((snapshots, e) -> {
                if (e != null) {
                    Log.e(TAG, "Error listening active reservation", e);
                    lastError.setValue("Error al verificar paseos activos: " + e.getMessage());
                    return;
                }
                if (snapshots != null && !snapshots.isEmpty()) {
                    Map<String, Object> resData = snapshots.getDocuments().get(0).getData();
                    resData.put("id_documento", snapshots.getDocuments().get(0).getId());
                    data.setValue(resData);
                } else {
                    data.setValue(null); // No active reservation
                }
            });
        return data;
    }
}