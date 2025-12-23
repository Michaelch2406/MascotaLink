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
    private static final long VENTANA_ANTICIPACION_MS = 15 * 60 * 1000; // 15 minutos

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

        // FASE 1 - PERFORMANCE: Query optimizada con whereIn() - una sola query en lugar de múltiples
        // NOTA: Para mejor performance, crear índice compuesto en Firestore:
        // Collection: reservas
        // Fields: id_dueno (Ascending), estado (Array-contains), hora_inicio (Descending)
        // Fields: id_paseador (Ascending), estado (Array-contains), hora_inicio (Descending)

        long startTime = System.currentTimeMillis();

        // Buscar reservas LISTO_PARA_INICIAR, EN_CURSO, CONFIRMADO o PENDIENTE (ambas versiones)
        db.collection("reservas")
            .whereEqualTo(field, db.collection("usuarios").document(userId))
            .whereIn("estado", java.util.Arrays.asList("CONFIRMADO", "LISTO_PARA_INICIAR", "EN_CURSO", "PENDIENTE_ACEPTACION", "PENDIENTE"))
            .limit(10) // Aumentar limite para poder filtrar
            .addSnapshotListener((snapshots, e) -> {
                long queryTime = System.currentTimeMillis() - startTime;
                Log.d(TAG, " getActiveReservation query completada en " + queryTime + "ms");

                if (e != null) {
                    Log.e(TAG, "Error listening active reservation", e);
                    lastError.setValue("Error al verificar paseos activos: " + e.getMessage());
                    return;
                }
                if (snapshots != null && !snapshots.isEmpty()) {
                    com.google.firebase.firestore.DocumentSnapshot reservaActiva = null;
                    long ahora = System.currentTimeMillis();

                    // Buscar la primera reserva que este realmente activa
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                        String estado = doc.getString("estado");

                        // Si esta EN_CURSO, usar inmediatamente
                        if ("EN_CURSO".equals(estado)) {
                            reservaActiva = doc;
                            break;
                        }

                        // Si esta LISTO_PARA_INICIAR, usar
                        if ("LISTO_PARA_INICIAR".equals(estado)) {
                            reservaActiva = doc;
                            break;
                        }
                        
                        // Si es PENDIENTE_ACEPTACION o PENDIENTE, considerar como activa (para mostrar alerta naranja)
                        if ("PENDIENTE_ACEPTACION".equals(estado) || "PENDIENTE".equals(estado)) {
                            reservaActiva = doc;
                            // No hacemos break inmediato porque preferimos mostrar una EN_CURSO si existe simultáneamente
                            // Pero si es lo único que hay, se mostrará.
                            continue;
                        }

                        // Si esta CONFIRMADO, verificar si estamos dentro de la ventana de 15 minutos o si ya paso la hora
                        if ("CONFIRMADO".equals(estado)) {
                            com.google.firebase.Timestamp horaInicio = doc.getTimestamp("hora_inicio");
                            if (horaInicio != null) {
                                long horaProgramadaMs = horaInicio.toDate().getTime();
                                long horaMinPermitidaMs = horaProgramadaMs - VENTANA_ANTICIPACION_MS;

                                // Si estamos dentro de la ventana de 15 minutos o ya paso la hora
                                if (ahora >= horaMinPermitidaMs) {
                                    // Si ya paso la hora programada, auto-transicionar a LISTO_PARA_INICIAR
                                    if (ahora >= horaProgramadaMs) {
                                        Log.d(TAG, "Auto-transicionando reserva " + doc.getId() + " de CONFIRMADO a LISTO_PARA_INICIAR");
                                        doc.getReference().update(
                                            "estado", "LISTO_PARA_INICIAR",
                                            "hasTransitionedToReady", true,
                                            "actualizado_por_sistema", true,
                                            "last_updated", com.google.firebase.Timestamp.now()
                                        );
                                    }
                                    // Usar esta reserva (ya sea CONFIRMADO dentro de ventana o transitando a LISTO_PARA_INICIAR)
                                    reservaActiva = doc;
                                    break;
                                }
                            }
                        }
                    }

                    if (reservaActiva != null) {
                        Map<String, Object> resData = reservaActiva.getData();
                        resData.put("id_documento", reservaActiva.getId());
                        data.setValue(resData);
                    } else {
                        data.setValue(null); // No active reservation
                    }
                } else {
                    data.setValue(null); // No active reservation
                }
            });
        return data;
    }
}