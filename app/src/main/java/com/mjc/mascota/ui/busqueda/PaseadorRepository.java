package com.mjc.mascota.ui.busqueda;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.mjc.mascota.modelo.PaseadorResultado;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PaseadorRepository {

    private static final String TAG = "PaseadorRepository";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<ListenerRegistration> listeners = new ArrayList<>();

    public LiveData<UiState<List<PaseadorResultado>>> getPaseadoresPopulares() {
        MutableLiveData<UiState<List<PaseadorResultado>>> liveData = new MutableLiveData<>();
        liveData.setValue(new UiState.Loading<>());

        Query query = db.collection("usuarios")
                .whereEqualTo("rol", "PASEADOR")
                .whereEqualTo("activo", true)
                .orderBy("nombre_display") // Ordenar por un campo para consistencia
                .limit(10);

        ListenerRegistration listener = query.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e(TAG, "Error al obtener IDs de paseadores populares", error);
                liveData.setValue(new UiState.Error<>("Error al cargar los datos."));
                return;
            }

            if (value == null || value.isEmpty()) {
                liveData.setValue(new UiState.Empty<>());
            } else {
                // Tenemos la lista de usuarios, ahora necesitamos los detalles de 'paseadores'
                combineUserDataWithPaseadorData(value, liveData);
            }
        });
        listeners.add(listener);
        return liveData;
    }

    private void combineUserDataWithPaseadorData(QuerySnapshot userSnapshots, MutableLiveData<UiState<List<PaseadorResultado>>> liveData) {
        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (DocumentSnapshot userDoc : userSnapshots) {
            // Para cada usuario, creamos una tarea para obtener su perfil de paseador
            tasks.add(db.collection("paseadores").document(userDoc.getId()).get());
        }

        // Cuando todas las tareas de obtener perfiles de paseador terminen
        Tasks.whenAllSuccess(tasks).addOnSuccessListener(paseadorDocs -> {
            ArrayList<PaseadorResultado> resultados = new ArrayList<>();
            for (int i = 0; i < userSnapshots.size(); i++) {
                DocumentSnapshot userDoc = userSnapshots.getDocuments().get(i);
                DocumentSnapshot paseadorDoc = (DocumentSnapshot) paseadorDocs.get(i);

                if (!paseadorDoc.exists()) {
                    Log.w(TAG, "Inconsistencia de datos: Usuario " + userDoc.getId() + " tiene rol PASEADOR pero no tiene perfil en 'paseadores'.");
                    continue; // Saltar este usuario inconsistente
                }

                // Construir el objeto de resultado de forma segura
                PaseadorResultado resultado = new PaseadorResultado();
                resultado.setId(userDoc.getId());
                resultado.setNombre(getStringSafely(userDoc, "nombre_display", "N/A"));
                resultado.setFotoUrl(getStringSafely(userDoc, "foto_perfil", null));
                resultado.setCalificacion(getDoubleSafely(paseadorDoc, "calificacion_promedio", 0.0));
                resultado.setTotalResenas(getLongSafely(paseadorDoc, "num_servicios_completados", 0L).intValue());
                resultado.setTarifaPorHora(getDoubleSafely(paseadorDoc, "tarifa_por_hora", 0.0));
                
                // Calcular años de experiencia de forma segura
                com.google.firebase.Timestamp ts = paseadorDoc.getTimestamp("fecha_inicio_experiencia");
                if (ts != null) {
                    long diff = System.currentTimeMillis() - ts.toDate().getTime();
                    resultado.setAnosExperiencia((int) TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS) / 365);
                } else {
                    resultado.setAnosExperiencia(0);
                }

                resultados.add(resultado);
            }

            if (resultados.isEmpty()) {
                liveData.setValue(new UiState.Empty<>());
            } else {
                liveData.setValue(new UiState.Success<>(resultados));
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al combinar datos de paseadores", e);
            liveData.setValue(new UiState.Error<>("No se pudieron cargar los perfiles completos."));
        });
    }

    // --- Métodos Helper de Seguridad --- //

    private String getStringSafely(DocumentSnapshot doc, String field, String defaultValue) {
        try {
            String value = doc.getString(field);
            return (value != null) ? value : defaultValue;
        } catch (Exception e) {
            Log.w(TAG, "Error al leer el campo '" + field + "' como String.", e);
            return defaultValue;
        }
    }

    private Double getDoubleSafely(DocumentSnapshot doc, String field, Double defaultValue) {
        try {
            Double value = doc.getDouble(field);
            return (value != null) ? value : defaultValue;
        } catch (Exception e) {
            Log.w(TAG, "Error al leer el campo '" + field + "' como Double.", e);
            return defaultValue;
        }
    }

    private Long getLongSafely(DocumentSnapshot doc, String field, Long defaultValue) {
        try {
            Long value = doc.getLong(field);
            return (value != null) ? value : defaultValue;
        } catch (Exception e) {
            Log.w(TAG, "Error al leer el campo '" + field + "' como Long.", e);
            return defaultValue;
        }
    }

    public LiveData<UiState<QuerySnapshot>> buscarPaseadores(String query, DocumentSnapshot lastVisible) {
        MutableLiveData<UiState<QuerySnapshot>> liveData = new MutableLiveData<>();
        liveData.setValue(new UiState.Loading<>());

        // Normalizar el query para búsqueda case-insensitive
        String queryNormalizado = query.toLowerCase();

        Query firestoreQuery = db.collection("usuarios")
                .whereEqualTo("rol", "PASEADOR")
                .whereEqualTo("activo", true)
                // ASUNCIÓN: Se debe tener un campo normalizado en minúsculas para búsquedas case-insensitive eficientes.
                .whereGreaterThanOrEqualTo("nombre_lowercase", queryNormalizado)
                .whereLessThanOrEqualTo("nombre_lowercase", queryNormalizado + "\uf8ff")
                .orderBy("nombre_lowercase")
                .limit(15);

        if (lastVisible != null) {
            firestoreQuery = firestoreQuery.startAfter(lastVisible);
        }

        firestoreQuery.get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                    liveData.setValue(new UiState.Empty<>());
                } else {
                    // Devolvemos los snapshots para que el ViewModel gestione la paginación
                    liveData.setValue(new UiState.Success<>(queryDocumentSnapshots));
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error en la búsqueda de paseadores", e);
                liveData.setValue(new UiState.Error<>("Error al realizar la búsqueda."));
            });

        return liveData;
    }

    public void cleanupListeners() {
        for (ListenerRegistration listener : listeners) {
            listener.remove();
        }
        listeners.clear();
        Log.d(TAG, "Todos los listeners de Firestore han sido limpiados.");
    }
}