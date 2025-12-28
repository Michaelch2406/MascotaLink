package com.mjc.mascotalink.ui.home;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public class HomeRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String TAG = "HomeRepository";
    private final MutableLiveData<String> lastError = new MutableLiveData<>();
    private static final long VENTANA_ANTICIPACION_MS = 15 * 60 * 1000;

    private ListenerRegistration activeReservationListener;

    public LiveData<String> getLastError() {
        return lastError;
    }

    public LiveData<Map<String, Object>> getUserProfile(String userId) {
        MutableLiveData<Map<String, Object>> data = new MutableLiveData<>();

        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "getUserProfile: userId is null or empty");
            data.setValue(null);
            return data;
        }

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

        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "getWalkerStats: userId is null or empty");
            data.setValue(getDefaultStats());
            return data;
        }

        db.collection("paseadores").document(userId).get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    data.setValue(snapshot.getData());
                } else {
                    Log.w(TAG, "getWalkerStats: Walker document does not exist");
                    data.setValue(getDefaultStats());
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "getWalkerStats: Error loading walker stats", e);
                lastError.setValue("Error al cargar estadísticas: " + e.getMessage());
                data.setValue(getDefaultStats());
            });
        return data;
    }

    private Map<String, Object> getDefaultStats() {
        Map<String, Object> defaultStats = new HashMap<>();
        defaultStats.put("num_servicios_completados", 0L);
        defaultStats.put("calificacion_promedio", 0.0);
        return defaultStats;
    }

    public LiveData<Map<String, Object>> getActiveReservation(String userId, String role) {
        MutableLiveData<Map<String, Object>> data = new MutableLiveData<>();

        if (userId == null || userId.isEmpty() || role == null) {
            Log.w(TAG, "getActiveReservation: invalid parameters");
            data.setValue(null);
            return data;
        }

        String field = role.equals("PASEADOR") ? "id_paseador" : "id_dueno";
        long startTime = System.currentTimeMillis();

        if (activeReservationListener != null) {
            activeReservationListener.remove();
            activeReservationListener = null;
        }

        activeReservationListener = db.collection("reservas")
            .whereEqualTo(field, db.collection("usuarios").document(userId))
            .whereIn("estado", Arrays.asList("CONFIRMADO", "LISTO_PARA_INICIAR", "EN_CURSO", "PENDIENTE_ACEPTACION", "PENDIENTE", "ACEPTADO"))
            .limit(10)
            .addSnapshotListener((snapshots, e) -> {
                long queryTime = System.currentTimeMillis() - startTime;
                Log.d(TAG, "getActiveReservation query completed in " + queryTime + "ms");

                if (e != null) {
                    Log.e(TAG, "Error listening active reservation", e);
                    lastError.setValue("Error al verificar paseos activos: " + e.getMessage());
                    return;
                }

                if (snapshots != null && !snapshots.isEmpty()) {
                    com.google.firebase.firestore.DocumentSnapshot reservaActiva = null;
                    long ahora = System.currentTimeMillis();

                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                        String estado = doc.getString("estado");

                        if ("EN_CURSO".equals(estado)) {
                            reservaActiva = doc;
                            break;
                        }

                        if ("LISTO_PARA_INICIAR".equals(estado)) {
                            reservaActiva = doc;
                            break;
                        }

                        if ("PENDIENTE_ACEPTACION".equals(estado) || "PENDIENTE".equals(estado)) {
                            reservaActiva = doc;
                            continue;
                        }

                        if ("ACEPTADO".equals(estado)) {
                            if (reservaActiva == null) {
                                reservaActiva = doc;
                            }
                            continue;
                        }

                        if ("CONFIRMADO".equals(estado)) {
                            Timestamp horaInicio = doc.getTimestamp("hora_inicio");
                            if (horaInicio != null) {
                                long horaProgramadaMs = horaInicio.toDate().getTime();
                                long horaMinPermitidaMs = horaProgramadaMs - VENTANA_ANTICIPACION_MS;

                                if (ahora >= horaMinPermitidaMs) {
                                    if (ahora >= horaProgramadaMs && !Boolean.TRUE.equals(doc.getBoolean("hasTransitionedToReady"))) {
                                        Log.d(TAG, "Auto-transitioning reservation " + doc.getId() + " to LISTO_PARA_INICIAR");
                                        doc.getReference().update(
                                            "estado", "LISTO_PARA_INICIAR",
                                            "hasTransitionedToReady", true,
                                            "actualizado_por_sistema", true,
                                            "last_updated", Timestamp.now()
                                        ).addOnFailureListener(error ->
                                            Log.e(TAG, "Error auto-transitioning state", error)
                                        );
                                    }
                                    reservaActiva = doc;
                                    break;
                                }
                            }
                        }
                    }

                    if (reservaActiva != null) {
                        Map<String, Object> resData = new HashMap<>(reservaActiva.getData());
                        resData.put("id_documento", reservaActiva.getId());
                        data.setValue(resData);
                    } else {
                        data.setValue(null);
                    }
                } else {
                    data.setValue(null);
                }
            });
        return data;
    }

    public void cleanup() {
        if (activeReservationListener != null) {
            activeReservationListener.remove();
            activeReservationListener = null;
            Log.d(TAG, "Firestore listener cleaned up");
        }
    }
}
