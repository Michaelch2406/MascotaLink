package com.mjc.mascota.ui.busqueda;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.mjc.mascota.modelo.PaseadorResultado;

import java.util.ArrayList;
import java.util.List;

import com.mjc.mascota.modelo.Filtros;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascota.utils.FirestoreConstants;

import org.json.JSONArray;
import org.json.JSONObject;
import android.content.SharedPreferences;
import android.text.TextUtils;

public class PaseadorRepository {
    private static final String TAG = "PaseadorRepository";
    private static final String POPULARES_CACHE_PREF = "paseadores_populares_cache";
    private static final String POPULARES_CACHE_KEY = "populares_json";
    private static final String SEARCH_CACHE_PREF = "paseadores_search_cache";
    private static final String SEARCH_HISTORY_KEY = "search_history";
    private static final int MAX_SEARCH_HISTORY = 10;
    private static final long SEARCH_CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutos

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private final java.util.Map<String, CachedSearchResult> searchCache = new java.util.LinkedHashMap<String, CachedSearchResult>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, CachedSearchResult> eldest) {
            return size() > 20;
        }
    };
    private final MutableLiveData<Filtros> _filtros = new MutableLiveData<>(new Filtros());

    // --- Filtros ---
    public LiveData<Filtros> getFiltros() {
        return _filtros;
    }

    public void setFiltros(Filtros filtros) {
        _filtros.postValue(filtros);
    }
    
    public void limpiarFiltros() {
        _filtros.postValue(new Filtros());
    }

    public void setCalificacionMinima(double calificacion) {
        Filtros current = _filtros.getValue();
        if (current != null) {
            current.setMinCalificacion((float) calificacion);
            _filtros.postValue(current);
        }
    }

    public void setExperienciaMinima(int experiencia) {
        Filtros current = _filtros.getValue();
        if (current != null) {
            current.setExperienciaMinima(experiencia);
            _filtros.postValue(current);
        }
    }

    public void setPrecioMaximo(double precio) {
        Filtros current = _filtros.getValue();
        if (current != null) {
            current.setMaxPrecio((float) precio);
            _filtros.postValue(current);
        }
    }

    public void setSoloEnLinea(boolean soloEnLinea) {
        Filtros current = _filtros.getValue();
        if (current != null) {
            current.setSoloEnLinea(soloEnLinea);
            _filtros.postValue(current);
        }
    }

    public LiveData<UiState<List<PaseadorResultado>>> getPaseadoresPopulares() {
        MutableLiveData<UiState<List<PaseadorResultado>>> liveData = new MutableLiveData<>();
        liveData.setValue(new UiState.Loading<>());

        List<PaseadorResultado> cached = readCachedPopulares();
        if (cached != null && !cached.isEmpty()) {
            liveData.setValue(new UiState.Success<>(cached));
        }

        // OPTIMIZACIÓN: Usar paseadores_search directamente (1 sola consulta)
        // En vez de usuarios + paseadores + zonasServicio (22 consultas)
        Query query = db.collection("paseadores_search")
                .whereEqualTo(FirestoreConstants.FIELD_ACTIVO, true)
                .whereEqualTo(FirestoreConstants.FIELD_VERIFICACION_ESTADO, FirestoreConstants.STATUS_APROBADO)
                .orderBy(FirestoreConstants.FIELD_NOMBRE_DISPLAY)
                .limit(10);

        // OPTIMIZACIÓN: Usar .get() en vez de snapshot listener para reducir consumo de batería
        query.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e(TAG, "Error al obtener paseadores populares", task.getException());
                liveData.setValue(new UiState.Error<>("Error al cargar los paseadores populares."));
                return;
            }

            QuerySnapshot value = task.getResult();
            if (value == null || value.isEmpty()) {
                liveData.setValue(new UiState.Empty<>());
            } else {
                // Obtener favoritos del usuario actual
                com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                Task<QuerySnapshot> favoritosTask = getFavoritosTask(currentUser);

                favoritosTask.addOnSuccessListener(favoritosSnapshot -> {
                    java.util.Set<String> favoritosIds = extractFavoritosIds(favoritosSnapshot);

                    // Construir resultados directamente desde paseadores_search
                    ArrayList<PaseadorResultado> resultados = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        PaseadorResultado resultado = buildSearchResultado(doc, favoritosIds);
                        resultados.add(resultado);
                    }

                    liveData.setValue(new UiState.Success<>(resultados));
                    cachePopulares(resultados);
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Error al obtener favoritos", e);
                    // Continuar sin favoritos
                    ArrayList<PaseadorResultado> resultados = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        PaseadorResultado resultado = buildSearchResultado(doc, new java.util.HashSet<>());
                        resultados.add(resultado);
                    }
                    liveData.setValue(new UiState.Success<>(resultados));
                });
            }
        });

        return liveData;
    }

    private LiveData<UiState<List<PaseadorResultado>>> combineUserDataWithPaseadorData(QuerySnapshot userSnapshots) {
        MutableLiveData<UiState<List<PaseadorResultado>>> liveData = new MutableLiveData<>();

        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        Task<QuerySnapshot> favoritosTask = getFavoritosTask(currentUser);

        favoritosTask.addOnSuccessListener(favoritosSnapshot -> {
            java.util.Set<String> favoritosIds = extractFavoritosIds(favoritosSnapshot);
            List<Task<DocumentSnapshot>> paseadorTasks = createPaseadorTasks(userSnapshots);

            Tasks.whenAllSuccess(paseadorTasks).addOnSuccessListener(paseadorDocs ->
                processPaseadoresData(userSnapshots, paseadorDocs, favoritosIds, liveData))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al combinar datos de paseadores populares", e);
                    liveData.setValue(new UiState.Error<>("No se pudieron cargar los perfiles completos."));
                });
        });
        return liveData;
    }

    private Task<QuerySnapshot> getFavoritosTask(com.google.firebase.auth.FirebaseUser currentUser) {
        return currentUser != null ?
            db.collection(FirestoreConstants.COLLECTION_USUARIOS)
              .document(currentUser.getUid())
              .collection(FirestoreConstants.COLLECTION_FAVORITOS)
              .get() :
            Tasks.forResult(null);
    }

    private java.util.Set<String> extractFavoritosIds(QuerySnapshot favoritosSnapshot) {
        java.util.Set<String> favoritosIds = new java.util.HashSet<>();
        if (favoritosSnapshot != null) {
            for (DocumentSnapshot doc : favoritosSnapshot) {
                favoritosIds.add(doc.getId());
            }
        }
        return favoritosIds;
    }

    private List<Task<DocumentSnapshot>> createPaseadorTasks(QuerySnapshot userSnapshots) {
        List<Task<DocumentSnapshot>> paseadorTasks = new ArrayList<>();
        for (DocumentSnapshot userDoc : userSnapshots) {
            paseadorTasks.add(db.collection(FirestoreConstants.COLLECTION_PASEADORES)
                .document(userDoc.getId()).get());
        }
        return paseadorTasks;
    }

    private void processPaseadoresData(QuerySnapshot userSnapshots, List<Object> paseadorDocs,
                                       java.util.Set<String> favoritosIds,
                                       MutableLiveData<UiState<List<PaseadorResultado>>> liveData) {
        ArrayList<PaseadorResultado> resultados = new ArrayList<>();
        List<Task<QuerySnapshot>> zonaTasks = new ArrayList<>();

        for (int i = 0; i < userSnapshots.size(); i++) {
            DocumentSnapshot userDoc = userSnapshots.getDocuments().get(i);
            DocumentSnapshot paseadorDoc = (DocumentSnapshot) paseadorDocs.get(i);

            if (!isValidPaseador(paseadorDoc)) continue;

            PaseadorResultado resultado = buildPaseadorResultado(userDoc, paseadorDoc, favoritosIds);
            resultados.add(resultado);

            zonaTasks.add(db.collection(FirestoreConstants.COLLECTION_PASEADORES)
                .document(paseadorDoc.getId())
                .collection(FirestoreConstants.COLLECTION_ZONAS_SERVICIO)
                .limit(1).get());
        }

        loadZonasAndComplete(zonaTasks, resultados, liveData);
    }

    private boolean isValidPaseador(DocumentSnapshot paseadorDoc) {
        if (!paseadorDoc.exists()) return false;
        String estado = paseadorDoc.getString(FirestoreConstants.FIELD_VERIFICACION_ESTADO);
        return FirestoreConstants.STATUS_APROBADO.equals(estado);
    }

    private PaseadorResultado buildPaseadorResultado(DocumentSnapshot userDoc, DocumentSnapshot paseadorDoc,
                                                     java.util.Set<String> favoritosIds) {
        PaseadorResultado resultado = new PaseadorResultado();
        resultado.setId(userDoc.getId());
        resultado.setNombre(getStringSafely(userDoc, FirestoreConstants.FIELD_NOMBRE_DISPLAY, FirestoreConstants.DEFAULT_NAME));
        resultado.setFotoUrl(getStringSafely(userDoc, FirestoreConstants.FIELD_FOTO_PERFIL, null));
        resultado.setCalificacion(getDoubleSafely(paseadorDoc, FirestoreConstants.FIELD_CALIFICACION_PROMEDIO, 0.0));
        resultado.setTotalResenas(getLongSafely(paseadorDoc, FirestoreConstants.FIELD_NUM_SERVICIOS_COMPLETADOS, 0L).intValue());
        resultado.setTarifaPorHora(getDoubleSafely(paseadorDoc, FirestoreConstants.FIELD_PRECIO_HORA, 0.0));
        resultado.setFavorito(favoritosIds.contains(userDoc.getId()));

        String estadoPresencia = getStringSafely(userDoc, FirestoreConstants.FIELD_ESTADO, FirestoreConstants.STATUS_OFFLINE);
        resultado.setEnLinea(FirestoreConstants.STATUS_ONLINE.equalsIgnoreCase(estadoPresencia));

        setExperienciaFromString(resultado, paseadorDoc);

        return resultado;
    }

    private void setExperienciaFromString(PaseadorResultado resultado, DocumentSnapshot paseadorDoc) {
        String experienciaStr = getStringSafely(paseadorDoc, FirestoreConstants.FIELD_EXPERIENCIA_GENERAL, "0");
        try {
            String numeros = experienciaStr.replaceAll("[^0-9]", "");
            resultado.setAnosExperiencia(Integer.parseInt(numeros));
        } catch (NumberFormatException e) {
            resultado.setAnosExperiencia(0);
        }
    }

    private void loadZonasAndComplete(List<Task<QuerySnapshot>> zonaTasks,
                                     ArrayList<PaseadorResultado> resultados,
                                     MutableLiveData<UiState<List<PaseadorResultado>>> liveData) {
        Tasks.whenAllSuccess(zonaTasks).addOnSuccessListener(zonaSnapshots -> {
            for (int i = 0; i < zonaSnapshots.size(); i++) {
                QuerySnapshot zonaSnapshot = (QuerySnapshot) zonaSnapshots.get(i);
                String zona = extractZonaPrincipal(zonaSnapshot);
                resultados.get(i).setZonaPrincipal(zona);
            }
            liveData.setValue(new UiState.Success<>(resultados));
        }).addOnFailureListener(e -> liveData.setValue(new UiState.Success<>(resultados)));
    }

    private String extractZonaPrincipal(QuerySnapshot zonaSnapshot) {
        if (!zonaSnapshot.isEmpty()) {
            String direccion = zonaSnapshot.getDocuments().get(0).getString(FirestoreConstants.FIELD_DIRECCION);
            return direccion != null ? direccion : FirestoreConstants.DEFAULT_ZONE;
        }
        return FirestoreConstants.DEFAULT_ZONE;
    }

    private void cachePopulares(List<PaseadorResultado> data) {
        try {
            JSONArray array = new JSONArray();
            for (PaseadorResultado p : data) {
                JSONObject obj = new JSONObject();
                obj.put(FirestoreConstants.FIELD_ID, p.getId());
                obj.put(FirestoreConstants.FIELD_NOMBRE, p.getNombre());
                obj.put("foto", p.getFotoUrl());
                obj.put("calificacion", p.getCalificacion());
                obj.put("totalResenas", p.getTotalResenas());
                obj.put("tarifa", p.getTarifaPorHora());
                obj.put("zona", p.getZonaPrincipal());
                obj.put("favorito", p.isFavorito());
                obj.put("enLinea", p.isEnLinea());
                obj.put("anosExp", p.getAnosExperiencia());
                array.put(obj);
            }
            SharedPreferences prefs = MyApplication.getAppContext().getSharedPreferences(POPULARES_CACHE_PREF, android.content.Context.MODE_PRIVATE);
            prefs.edit().putString(POPULARES_CACHE_KEY, array.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "No se pudo cachear populares", e);
        }
    }

    private List<PaseadorResultado> readCachedPopulares() {
        try {
            SharedPreferences prefs = MyApplication.getAppContext().getSharedPreferences(POPULARES_CACHE_PREF, android.content.Context.MODE_PRIVATE);
            String raw = prefs.getString(POPULARES_CACHE_KEY, null);
            if (TextUtils.isEmpty(raw)) return null;

            JSONArray array = new JSONArray(raw);
            List<PaseadorResultado> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                list.add(parsePaseadorFromJson(array.getJSONObject(i)));
            }
            return list;
        } catch (Exception e) {
            Log.w(TAG, "No se pudo leer cache de populares", e);
            return null;
        }
    }

    private PaseadorResultado parsePaseadorFromJson(JSONObject obj) {
        PaseadorResultado p = new PaseadorResultado();
        p.setId(obj.optString(FirestoreConstants.FIELD_ID, ""));
        p.setNombre(obj.optString(FirestoreConstants.FIELD_NOMBRE, FirestoreConstants.DEFAULT_NAME));
        p.setFotoUrl(obj.optString("foto", null));
        p.setCalificacion(obj.optDouble("calificacion", 0));
        p.setTotalResenas(obj.optInt("totalResenas", 0));
        p.setTarifaPorHora(obj.optDouble("tarifa", 0));
        p.setZonaPrincipal(obj.optString("zona", FirestoreConstants.DEFAULT_ZONE));
        p.setFavorito(obj.optBoolean("favorito", false));
        p.setEnLinea(obj.optBoolean("enLinea", false));
        p.setAnosExperiencia(obj.optInt("anosExp", 0));
        return p;
    }

    public LiveData<UiState<PaseadorSearchResult>> buscarPaseadores(String query, DocumentSnapshot lastVisible, Filtros filtros) {
        MutableLiveData<UiState<PaseadorSearchResult>> liveData = new MutableLiveData<>();

        if (lastVisible == null) {
            List<PaseadorResultado> cached = getCachedResults(query, filtros);
            if (cached != null && !cached.isEmpty()) {
                Log.d(TAG, "Retornando resultados cacheados para: " + query);
                liveData.setValue(new UiState.Success<>(new PaseadorSearchResult(new ArrayList<>(cached), null)));
                return liveData;
            }
        }

        liveData.setValue(new UiState.Loading<>());

        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        Task<QuerySnapshot> favoritosTask = getFavoritosTask(currentUser);

        Query firestoreQuery = buildSearchQuery(query, filtros, lastVisible);
        Task<QuerySnapshot> busquedaTask = firestoreQuery.get();

        final String queryFinal = query;
        final Filtros filtrosFinal = filtros;

        Tasks.whenAllSuccess(favoritosTask, busquedaTask).addOnSuccessListener(results -> {
            processSearchResults(results, liveData);

            if (lastVisible == null && queryFinal != null && !queryFinal.isEmpty()) {
                saveSearchToHistory(queryFinal);
            }

            UiState<PaseadorSearchResult> state = liveData.getValue();
            if (state instanceof UiState.Success && lastVisible == null) {
                PaseadorSearchResult searchResult = ((UiState.Success<PaseadorSearchResult>) state).getData();
                if (searchResult != null && searchResult.resultados != null) {
                    cacheSearchResults(queryFinal, filtrosFinal, searchResult.resultados);
                }
            }
        })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error en la búsqueda de paseadores para query: " + query, e);
                liveData.setValue(new UiState.Error<>("Error al realizar la búsqueda."));
            });

        return liveData;
    }

    private Query buildSearchQuery(String query, Filtros filtros, DocumentSnapshot lastVisible) {
        Query firestoreQuery = db.collection(FirestoreConstants.COLLECTION_PASEADORES_SEARCH)
                .whereEqualTo(FirestoreConstants.FIELD_ACTIVO, true)
                .whereEqualTo(FirestoreConstants.FIELD_VERIFICACION_ESTADO, FirestoreConstants.STATUS_APROBADO);

        firestoreQuery = applyQueryFilter(firestoreQuery, query);
        firestoreQuery = applyFiltros(firestoreQuery, filtros, query);
        firestoreQuery = firestoreQuery.limit(15);

        if (lastVisible != null) {
            firestoreQuery = firestoreQuery.startAfter(lastVisible);
        }

        return firestoreQuery;
    }

    private Query applyQueryFilter(Query query, String searchQuery) {
        if (searchQuery != null && !searchQuery.isEmpty()) {
            String queryNormalizado = searchQuery.toLowerCase();
            return query.whereGreaterThanOrEqualTo("nombre_lowercase", queryNormalizado)
                    .whereLessThanOrEqualTo("nombre_lowercase", queryNormalizado + "\uf8ff");
        }
        return query;
    }

    private Query applyFiltros(Query query, Filtros filtros, String searchQuery) {
        if (filtros == null) {
            if (searchQuery == null || searchQuery.isEmpty()) {
                return query.orderBy(FirestoreConstants.FIELD_NOMBRE_DISPLAY, Query.Direction.ASCENDING);
            }
            return query;
        }

        if (filtros.getMinCalificacion() > 0) {
            query = query.whereGreaterThanOrEqualTo(FirestoreConstants.FIELD_CALIFICACION_PROMEDIO, filtros.getMinCalificacion());
        }
        if (filtros.getMinPrecio() > 0) {
            query = query.whereGreaterThanOrEqualTo(FirestoreConstants.FIELD_TARIFA_POR_HORA, filtros.getMinPrecio());
        }
        if (filtros.getMaxPrecio() < 100) {
            query = query.whereLessThanOrEqualTo(FirestoreConstants.FIELD_TARIFA_POR_HORA, filtros.getMaxPrecio());
        }
        if (filtros.isSoloEnLinea()) {
            query = query.whereEqualTo(FirestoreConstants.FIELD_ESTADO, FirestoreConstants.STATUS_ONLINE);
        }
        if (filtros.getTamanosMascota() != null && !filtros.getTamanosMascota().isEmpty()) {
            query = query.whereArrayContainsAny(FirestoreConstants.FIELD_TIPOS_PERRO_ACEPTADOS, filtros.getTamanosMascota());
        }

        return applyOrdenamiento(query, filtros.getOrden(), searchQuery);
    }

    private Query applyOrdenamiento(Query query, String orden, String searchQuery) {
        if (orden == null) {
            if (searchQuery == null || searchQuery.isEmpty()) {
                return query.orderBy(FirestoreConstants.FIELD_NOMBRE_DISPLAY, Query.Direction.ASCENDING);
            }
            return query;
        }

        switch (orden) {
            case "Distancia (más cercano)":
                // Ordenar por nombre como fallback ya que distancia exacta requiere ubicación del usuario
                // TODO: Implementar ordenamiento por distancia calculada en el cliente
                return query.orderBy(FirestoreConstants.FIELD_NOMBRE_DISPLAY, Query.Direction.ASCENDING);

            case "Precio (menor a mayor)":
                return query.orderBy(FirestoreConstants.FIELD_TARIFA_POR_HORA, Query.Direction.ASCENDING);

            case "Precio (mayor a menor)":
                return query.orderBy(FirestoreConstants.FIELD_TARIFA_POR_HORA, Query.Direction.DESCENDING);

            case "Calificación (mejor a peor)":
                return query.orderBy(FirestoreConstants.FIELD_CALIFICACION_PROMEDIO, Query.Direction.DESCENDING);

            default:
                if (searchQuery == null || searchQuery.isEmpty()) {
                    return query.orderBy(FirestoreConstants.FIELD_NOMBRE_DISPLAY, Query.Direction.ASCENDING);
                }
                return query;
        }
    }

    private void processSearchResults(List<Object> results, MutableLiveData<UiState<PaseadorSearchResult>> liveData) {
        QuerySnapshot favoritosSnapshot = (QuerySnapshot) results.get(0);
        QuerySnapshot busquedaSnapshot = (QuerySnapshot) results.get(1);

        java.util.Set<String> favoritosIds = extractFavoritosIds(favoritosSnapshot);

        if (busquedaSnapshot == null || busquedaSnapshot.isEmpty()) {
            liveData.setValue(new UiState.Empty<>());
            return;
        }

        DocumentSnapshot newLastVisible = busquedaSnapshot.getDocuments().get(busquedaSnapshot.size() - 1);
        ArrayList<PaseadorResultado> resultados = new ArrayList<>();
        java.util.Map<String, Integer> indexMap = new java.util.HashMap<>();
        java.util.List<Task<?>> detailTasks = new ArrayList<>();

        for (DocumentSnapshot doc : busquedaSnapshot) {
            PaseadorResultado resultado = buildSearchResultado(doc, favoritosIds);
            int idx = resultados.size();
            indexMap.put(doc.getId(), idx);
            resultados.add(resultado);

            addDetailTasks(doc.getId(), indexMap, resultados, detailTasks);
        }

        completeSearchResults(detailTasks, resultados, newLastVisible, liveData);
    }

    private PaseadorResultado buildSearchResultado(DocumentSnapshot doc, java.util.Set<String> favoritosIds) {
        PaseadorResultado resultado = new PaseadorResultado();
        resultado.setId(doc.getId());
        resultado.setNombre(getStringSafely(doc, FirestoreConstants.FIELD_NOMBRE_DISPLAY, FirestoreConstants.DEFAULT_NAME));
        resultado.setFotoUrl(getStringSafely(doc, FirestoreConstants.FIELD_FOTO_PERFIL, null));
        resultado.setCalificacion(getDoubleSafely(doc, FirestoreConstants.FIELD_CALIFICACION_PROMEDIO, 0.0));
        resultado.setTotalResenas(getLongSafely(doc, FirestoreConstants.FIELD_NUM_SERVICIOS_COMPLETADOS, 0L).intValue());

        Double tarifa = getDoubleSafely(doc, FirestoreConstants.FIELD_TARIFA_POR_HORA, null);
        if (tarifa == null) {
            tarifa = getDoubleSafely(doc, FirestoreConstants.FIELD_PRECIO_HORA, 0.0);
        }
        resultado.setTarifaPorHora(tarifa);

        resultado.setAnosExperiencia(getLongSafely(doc, FirestoreConstants.FIELD_ANOS_EXPERIENCIA, 0L).intValue());

        // OPTIMIZACIÓN: Extraer zona principal de zonas_principales (ya está en paseadores_search)
        String zonaPrincipal = FirestoreConstants.DEFAULT_ZONE;
        Object zonasObj = doc.get("zonas_principales");
        if (zonasObj instanceof java.util.List) {
            java.util.List<?> zonasList = (java.util.List<?>) zonasObj;
            if (!zonasList.isEmpty() && zonasList.get(0) instanceof String) {
                zonaPrincipal = (String) zonasList.get(0);
            }
        }
        resultado.setZonaPrincipal(zonaPrincipal);

        resultado.setFavorito(favoritosIds.contains(doc.getId()));

        // OPTIMIZACIÓN: Obtener estado de presencia de paseadores_search si existe
        String estadoPresencia = getStringSafely(doc, FirestoreConstants.FIELD_ESTADO, null);
        resultado.setEnLinea(FirestoreConstants.STATUS_ONLINE.equalsIgnoreCase(estadoPresencia));

        return resultado;
    }

    private void addDetailTasks(String docId, java.util.Map<String, Integer> indexMap,
                               ArrayList<PaseadorResultado> resultados, java.util.List<Task<?>> detailTasks) {
        // OPTIMIZACIÓN COMPLETA: Ya no se necesitan consultas adicionales
        // Todos los datos (incluyendo estado en línea) ya están en paseadores_search
        // Este método se mantiene por compatibilidad pero no agrega tareas
    }

    private void updatePaseadorDetails(DocumentSnapshot pDoc, String docId,
                                      java.util.Map<String, Integer> indexMap,
                                      ArrayList<PaseadorResultado> resultados) {
        Integer position = indexMap.get(docId);
        if (position == null || position >= resultados.size()) return;

        PaseadorResultado res = resultados.get(position);
        if (res.getTarifaPorHora() == 0.0) {
            Double precioHora = pDoc.getDouble(FirestoreConstants.FIELD_PRECIO_HORA);
            if (precioHora != null) res.setTarifaPorHora(precioHora);
        }
        Double calif = pDoc.getDouble(FirestoreConstants.FIELD_CALIFICACION_PROMEDIO);
        if (calif != null) res.setCalificacion(calif);
        Long total = pDoc.getLong(FirestoreConstants.FIELD_NUM_SERVICIOS_COMPLETADOS);
        if (total != null) res.setTotalResenas(total.intValue());
    }

    private void updateOnlineStatus(DocumentSnapshot uDoc, String docId,
                                   java.util.Map<String, Integer> indexMap,
                                   ArrayList<PaseadorResultado> resultados) {
        Integer position = indexMap.get(docId);
        if (position == null || position >= resultados.size()) return;

        PaseadorResultado res = resultados.get(position);
        String estadoPresencia = uDoc.getString(FirestoreConstants.FIELD_ESTADO);
        res.setEnLinea(FirestoreConstants.STATUS_ONLINE.equalsIgnoreCase(estadoPresencia));
    }

    private void updateZona(QuerySnapshot zonas, String docId,
                          java.util.Map<String, Integer> indexMap,
                          ArrayList<PaseadorResultado> resultados) {
        Integer position = indexMap.get(docId);
        if (position == null || position >= resultados.size()) return;

        if (zonas != null && !zonas.isEmpty()) {
            String direccion = zonas.getDocuments().get(0).getString(FirestoreConstants.FIELD_DIRECCION);
            if (direccion != null && !direccion.isEmpty()) {
                resultados.get(position).setZonaPrincipal(direccion);
            }
        }
    }

    private void completeSearchResults(java.util.List<Task<?>> detailTasks,
                                      ArrayList<PaseadorResultado> resultados,
                                      DocumentSnapshot newLastVisible,
                                      MutableLiveData<UiState<PaseadorSearchResult>> liveData) {
        if (detailTasks.isEmpty()) {
            liveData.setValue(new UiState.Success<>(new PaseadorSearchResult(resultados, newLastVisible)));
        } else {
            Tasks.whenAllComplete(detailTasks).addOnCompleteListener(done ->
                    liveData.setValue(new UiState.Success<>(new PaseadorSearchResult(resultados, newLastVisible))));
        }
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

    public void cleanupListeners() {
        for (ListenerRegistration listener : listeners) {
            listener.remove();
        }
        listeners.clear();
        Log.d(TAG, "Todos los listeners de Firestore han sido limpiados.");
    }

    public void toggleFavorito(String paseadorId, boolean add) {
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        DocumentReference favRef = db.collection(FirestoreConstants.COLLECTION_USUARIOS)
                .document(userId)
                .collection(FirestoreConstants.COLLECTION_FAVORITOS)
                .document(paseadorId);

        if (add) {
            addToFavoritos(paseadorId, favRef);
        } else {
            favRef.delete();
        }
    }

    private void addToFavoritos(String paseadorId, DocumentReference favRef) {
        DocumentReference usuarioPaseadorRef = db.collection(FirestoreConstants.COLLECTION_USUARIOS).document(paseadorId);
        DocumentReference perfilPaseadorRef = db.collection(FirestoreConstants.COLLECTION_PASEADORES).document(paseadorId);

        Task<DocumentSnapshot> usuarioTask = usuarioPaseadorRef.get();
        Task<DocumentSnapshot> paseadorTask = perfilPaseadorRef.get();

        Tasks.whenAllSuccess(usuarioTask, paseadorTask).addOnSuccessListener(results -> {
            DocumentSnapshot usuarioDoc = (DocumentSnapshot) results.get(0);
            DocumentSnapshot paseadorDoc = (DocumentSnapshot) results.get(1);

            if (usuarioDoc.exists() && paseadorDoc.exists()) {
                java.util.Map<String, Object> favoritoData = buildFavoritoData(usuarioDoc, paseadorDoc, perfilPaseadorRef);
                favRef.set(favoritoData);
            }
        });
    }

    private java.util.Map<String, Object> buildFavoritoData(DocumentSnapshot usuarioDoc,
                                                            DocumentSnapshot paseadorDoc,
                                                            DocumentReference perfilPaseadorRef) {
        java.util.Map<String, Object> favoritoData = new java.util.HashMap<>();
        favoritoData.put("fecha_agregado", com.google.firebase.firestore.FieldValue.serverTimestamp());
        favoritoData.put("paseador_ref", perfilPaseadorRef);
        favoritoData.put(FirestoreConstants.FIELD_NOMBRE_DISPLAY, usuarioDoc.getString(FirestoreConstants.FIELD_NOMBRE_DISPLAY));
        favoritoData.put("foto_perfil_url", usuarioDoc.getString(FirestoreConstants.FIELD_FOTO_PERFIL));
        favoritoData.put(FirestoreConstants.FIELD_CALIFICACION_PROMEDIO, paseadorDoc.getDouble(FirestoreConstants.FIELD_CALIFICACION_PROMEDIO));
        favoritoData.put(FirestoreConstants.FIELD_PRECIO_HORA, paseadorDoc.getDouble(FirestoreConstants.FIELD_PRECIO_HORA));
        return favoritoData;
    }

    // --- Cache de Búsquedas Recientes --- //

    private static class CachedSearchResult {
        final List<PaseadorResultado> resultados;
        final long timestamp;

        CachedSearchResult(List<PaseadorResultado> resultados) {
            this.resultados = resultados;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > SEARCH_CACHE_EXPIRY_MS;
        }
    }

    private String buildCacheKey(String query, Filtros filtros) {
        StringBuilder key = new StringBuilder(query != null ? query.toLowerCase() : "");
        if (filtros != null) {
            key.append("_").append(filtros.getMinCalificacion());
            key.append("_").append(filtros.getMinPrecio());
            key.append("_").append(filtros.getMaxPrecio());
            key.append("_").append(filtros.getOrden() != null ? filtros.getOrden() : "");
            if (filtros.getTamanosMascota() != null) {
                key.append("_").append(String.join(",", filtros.getTamanosMascota()));
            }
        }
        return key.toString();
    }

    public List<PaseadorResultado> getCachedResults(String query, Filtros filtros) {
        String cacheKey = buildCacheKey(query, filtros);
        CachedSearchResult cached = searchCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            Log.d(TAG, "Cache hit para: " + cacheKey);
            return cached.resultados;
        }
        return null;
    }

    private void cacheSearchResults(String query, Filtros filtros, List<PaseadorResultado> resultados) {
        if (resultados == null || resultados.isEmpty()) return;
        String cacheKey = buildCacheKey(query, filtros);
        searchCache.put(cacheKey, new CachedSearchResult(new ArrayList<>(resultados)));
        Log.d(TAG, "Cacheando resultados para: " + cacheKey);
    }

    public void clearSearchCache() {
        searchCache.clear();
        Log.d(TAG, "Cache de búsquedas limpiado");
    }

    // --- Historial de Búsquedas --- //

    public void saveSearchToHistory(String query) {
        if (query == null || query.trim().isEmpty()) return;
        try {
            SharedPreferences prefs = MyApplication.getAppContext().getSharedPreferences(SEARCH_CACHE_PREF, android.content.Context.MODE_PRIVATE);
            String raw = prefs.getString(SEARCH_HISTORY_KEY, "[]");
            JSONArray history = new JSONArray(raw);

            // Remover si ya existe
            for (int i = history.length() - 1; i >= 0; i--) {
                if (query.equalsIgnoreCase(history.getString(i))) {
                    history.remove(i);
                }
            }

            // Agregar al inicio
            JSONArray newHistory = new JSONArray();
            newHistory.put(query.trim());
            for (int i = 0; i < Math.min(history.length(), MAX_SEARCH_HISTORY - 1); i++) {
                newHistory.put(history.getString(i));
            }

            prefs.edit().putString(SEARCH_HISTORY_KEY, newHistory.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Error guardando historial de búsqueda", e);
        }
    }

    public List<String> getSearchHistory() {
        List<String> history = new ArrayList<>();
        try {
            SharedPreferences prefs = MyApplication.getAppContext().getSharedPreferences(SEARCH_CACHE_PREF, android.content.Context.MODE_PRIVATE);
            String raw = prefs.getString(SEARCH_HISTORY_KEY, "[]");
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                history.add(array.getString(i));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error leyendo historial de búsqueda", e);
        }
        return history;
    }

    public void clearSearchHistory() {
        try {
            SharedPreferences prefs = MyApplication.getAppContext().getSharedPreferences(SEARCH_CACHE_PREF, android.content.Context.MODE_PRIVATE);
            prefs.edit().remove(SEARCH_HISTORY_KEY).apply();
        } catch (Exception e) {
            Log.w(TAG, "Error limpiando historial", e);
        }
    }

    public void removeFromSearchHistory(String query) {
        if (query == null) return;
        try {
            SharedPreferences prefs = MyApplication.getAppContext().getSharedPreferences(SEARCH_CACHE_PREF, android.content.Context.MODE_PRIVATE);
            String raw = prefs.getString(SEARCH_HISTORY_KEY, "[]");
            JSONArray history = new JSONArray(raw);
            JSONArray newHistory = new JSONArray();

            for (int i = 0; i < history.length(); i++) {
                if (!query.equalsIgnoreCase(history.getString(i))) {
                    newHistory.put(history.getString(i));
                }
            }

            prefs.edit().putString(SEARCH_HISTORY_KEY, newHistory.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Error removiendo del historial", e);
        }
    }
}
