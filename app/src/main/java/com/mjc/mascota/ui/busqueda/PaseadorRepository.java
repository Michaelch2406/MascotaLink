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

import com.mjc.mascota.modelo.Filtros;

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
                liveData.setValue(new UiState.Error<>("Error al cargar los paseadores populares."));
                return;
            }

            if (value == null || value.isEmpty()) {
                liveData.setValue(new UiState.Empty<>());
            } else {
                combineUserDataWithPaseadorData(value).observeForever(liveData::setValue);
            }
        });
        listeners.add(listener);
        return liveData;
    }

    private LiveData<UiState<List<PaseadorResultado>>> combineUserDataWithPaseadorData(QuerySnapshot userSnapshots) {
        MutableLiveData<UiState<List<PaseadorResultado>>> liveData = new MutableLiveData<>();
        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (DocumentSnapshot userDoc : userSnapshots) {
            tasks.add(db.collection("paseadores").document(userDoc.getId()).get());
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(paseadorDocs -> {
            ArrayList<PaseadorResultado> resultados = new ArrayList<>();
            if (paseadorDocs.isEmpty()) {
                liveData.setValue(new UiState.Empty<>());
                return;
            }

            List<Task<QuerySnapshot>> zonaTasks = new ArrayList<>();
            for (int i = 0; i < userSnapshots.size(); i++) {
                DocumentSnapshot userDoc = userSnapshots.getDocuments().get(i);
                DocumentSnapshot paseadorDoc = (DocumentSnapshot) paseadorDocs.get(i);

                if (!paseadorDoc.exists()) {
                    continue;
                }

                PaseadorResultado resultado = new PaseadorResultado();
                resultado.setId(userDoc.getId());
                resultado.setNombre(getStringSafely(userDoc, "nombre_display", "N/A"));
                resultado.setFotoUrl(getStringSafely(userDoc, "foto_perfil", null));
                resultado.setCalificacion(getDoubleSafely(paseadorDoc, "calificacion_promedio", 0.0));
                resultado.setTotalResenas(getLongSafely(paseadorDoc, "num_servicios_completados", 0L).intValue());
                resultado.setTarifaPorHora(getDoubleSafely(paseadorDoc, "tarifa_por_hora", 0.0));
                
                String experienciaStr = getStringSafely(paseadorDoc, "experiencia_general", "0");
                try {
                    // Extraer solo los números de la cadena
                    String numeros = experienciaStr.replaceAll("[^0-9]", "");
                    resultado.setAnosExperiencia(Integer.parseInt(numeros));
                } catch (NumberFormatException e) {
                    resultado.setAnosExperiencia(0);
                }

                resultados.add(resultado);

                zonaTasks.add(db.collection("paseadores").document(paseadorDoc.getId()).collection("zonas_servicio").limit(1).get());
            }

            Tasks.whenAllSuccess(zonaTasks).addOnSuccessListener(zonaSnapshots -> {
                for (int i = 0; i < zonaSnapshots.size(); i++) {
                    QuerySnapshot zonaSnapshot = (QuerySnapshot) zonaSnapshots.get(i);
                    if (!zonaSnapshot.isEmpty()) {
                        resultados.get(i).setZonaPrincipal(zonaSnapshot.getDocuments().get(0).getString("direccion"));
                    } else {
                        resultados.get(i).setZonaPrincipal("Sin zona especificada");
                    }
                }
                liveData.setValue(new UiState.Success<>(resultados));
            }).addOnFailureListener(e -> {
                liveData.setValue(new UiState.Success<>(resultados));
            });

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al combinar datos de paseadores populares", e);
            liveData.setValue(new UiState.Error<>("No se pudieron cargar los perfiles completos de paseadores populares."));
        });
        return liveData;
    }

    public LiveData<UiState<PaseadorSearchResult>> buscarPaseadores(String query, DocumentSnapshot lastVisible, Filtros filtros) {
        MutableLiveData<UiState<PaseadorSearchResult>> liveData = new MutableLiveData<>();
        liveData.setValue(new UiState.Loading<>());

        Query firestoreQuery = db.collection("paseadores_search").whereEqualTo("activo", true);

        // Aplicar filtro de texto
        if (query != null && !query.isEmpty()) {
            String queryNormalizado = query.toLowerCase();
            firestoreQuery = firestoreQuery.whereGreaterThanOrEqualTo("nombre_lowercase", queryNormalizado)
                                           .whereLessThanOrEqualTo("nombre_lowercase", queryNormalizado + "\uf8ff");
        }

        // Aplicar filtros del diálogo
        if (filtros != null) {
            if (filtros.getMinCalificacion() > 0) {
                firestoreQuery = firestoreQuery.whereGreaterThanOrEqualTo("calificacion_promedio", filtros.getMinCalificacion());
            }
            if (filtros.getMinPrecio() > 0) {
                firestoreQuery = firestoreQuery.whereGreaterThanOrEqualTo("tarifa_por_hora", filtros.getMinPrecio());
            }
            if (filtros.getMaxPrecio() < 100) {
                firestoreQuery = firestoreQuery.whereLessThanOrEqualTo("tarifa_por_hora", filtros.getMaxPrecio());
            }
            if (filtros.getTamanosMascota() != null && !filtros.getTamanosMascota().isEmpty()) {
                firestoreQuery = firestoreQuery.whereArrayContainsAny("tipos_perro_aceptados", filtros.getTamanosMascota());
            }

            // Aplicar ordenamiento
            String orden = filtros.getOrden();
            if (orden != null) {
                switch (orden) {
                    case "Precio (menor a mayor)":
                        firestoreQuery = firestoreQuery.orderBy("tarifa_por_hora", Query.Direction.ASCENDING);
                        break;
                    case "Precio (mayor a menor)":
                        firestoreQuery = firestoreQuery.orderBy("tarifa_por_hora", Query.Direction.DESCENDING);
                        break;
                    case "Calificación (mejor a peor)":
                        firestoreQuery = firestoreQuery.orderBy("calificacion_promedio", Query.Direction.DESCENDING);
                        break;
                    default: // Por defecto, si no hay texto, ordenar por nombre
                        if (query == null || query.isEmpty()) {
                           firestoreQuery = firestoreQuery.orderBy("nombre_display", Query.Direction.ASCENDING);
                        }
                        break;
                }
            }
        } else if (query == null || query.isEmpty()){
             firestoreQuery = firestoreQuery.orderBy("nombre_display", Query.Direction.ASCENDING);
        }

        firestoreQuery = firestoreQuery.limit(15);

        if (lastVisible != null) {
            firestoreQuery = firestoreQuery.startAfter(lastVisible);
        }

        firestoreQuery.get()
            .addOnSuccessListener(snapshots -> {
                if (snapshots == null || snapshots.isEmpty()) {
                    Log.d(TAG, "Búsqueda no arrojó resultados para query: " + query);
                    liveData.setValue(new UiState.Empty<>());
                    return;
                }

                Log.d(TAG, "Búsqueda exitosa para query: " + query + ", encontrados: " + snapshots.size());

                DocumentSnapshot newLastVisible = snapshots.getDocuments().get(snapshots.size() - 1);
                ArrayList<PaseadorResultado> resultados = new ArrayList<>();

                for (DocumentSnapshot doc : snapshots) {
                    PaseadorResultado resultado = new PaseadorResultado();
                    resultado.setId(doc.getId());
                    resultado.setNombre(getStringSafely(doc, "nombre_display", "N/A"));
                    resultado.setFotoUrl(getStringSafely(doc, "foto_perfil", null));
                    resultado.setCalificacion(getDoubleSafely(doc, "calificacion_promedio", 0.0));
                    resultado.setTotalResenas(getLongSafely(doc, "num_servicios_completados", 0L).intValue());
                    resultado.setTarifaPorHora(getDoubleSafely(doc, "tarifa_por_hora", 0.0));
                    resultado.setAnosExperiencia(getLongSafely(doc, "anos_experiencia", 0L).intValue());
                    // La zona principal ahora se debe obtener por separado si no está en la colección de búsqueda
                    // Por ahora, la dejaremos fuera para simplificar.
                    resultado.setZonaPrincipal("Sin zona especificada"); 
                    resultados.add(resultado);
                }

                liveData.setValue(new UiState.Success<>(new PaseadorSearchResult(resultados, newLastVisible)));

            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error en la búsqueda de paseadores para query: " + query, e);
                liveData.setValue(new UiState.Error<>("Error al realizar la búsqueda."));
            });

        return liveData;
    }

    // --- Métodos Helper de Seguridad --- //

    private String getStringSafely(DocumentSnapshot doc, String field, String defaultValue) {
        try {
            String value = doc.getString(field);
            return (value != null) ? value : defaultValue;
        } catch (Exception e) {
            Log.w(TAG, "Error al leer el campo '" + field + "' como String.", e);
            return defaultValue;
        }    }

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

    public void cleanupListeners() {
        for (ListenerRegistration listener : listeners) {
            listener.remove();
        }
        listeners.clear();
        Log.d(TAG, "Todos los listeners de Firestore han sido limpiados.");
    }
}
