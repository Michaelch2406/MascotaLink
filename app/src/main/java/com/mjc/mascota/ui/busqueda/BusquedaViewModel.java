
package com.mjc.mascota.ui.busqueda;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.mjc.mascota.modelo.PaseadorResultado;

import java.util.ArrayList;
import java.util.List;

import com.mjc.mascota.modelo.Filtros;

import android.util.Log;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class BusquedaViewModel extends ViewModel {

    private static final String TAG = "BusquedaViewModel";

    private final PaseadorRepository repository;
    private LiveData<UiState<List<PaseadorResultado>>> paseadoresPopularesState;

    // --- Lógica de Búsqueda y Paginación ---
    private final MutableLiveData<UiState<List<PaseadorResultado>>> _searchResults = new MutableLiveData<>();
    public final LiveData<UiState<List<PaseadorResultado>>> searchResults = _searchResults;

    private final MutableLiveData<Filtros> _filtros = new MutableLiveData<>(new Filtros());
    public final LiveData<Filtros> filtros = _filtros;

    private String currentQuery = "";
    private DocumentSnapshot lastVisibleDocument = null;
    private boolean isLoadingMore = false;
    private boolean isLastPage = false;

    // --- Lógica de Debounce ---
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;

    public BusquedaViewModel() {
        this(new PaseadorRepository());
    }

    public BusquedaViewModel(PaseadorRepository repository) {
        this.repository = repository;
        loadPaseadoresPopulares();
    }

    // --- LiveData Públicos --- //
    public LiveData<UiState<List<PaseadorResultado>>> getPaseadoresPopularesState() {
        return paseadoresPopularesState;
    }

    // --- Acciones de la UI --- //
    public void loadPaseadoresPopulares() {
        if (paseadoresPopularesState == null) { // Cargar solo una vez
            paseadoresPopularesState = repository.getPaseadoresPopulares();
        }
    }

    public void onSearchQueryChanged(String query) {
        Log.d(TAG, "onSearchQueryChanged: " + query);
        debounceHandler.removeCallbacks(debounceRunnable);
        debounceRunnable = () -> {
            String normalizedQuery = normalizarTexto(query);
            
            // Verificar si hay filtros activos
            Filtros currentFiltros = _filtros.getValue();
            boolean hasActiveFilters = currentFiltros != null && (
                    currentFiltros.getMinCalificacion() > 0 ||
                    currentFiltros.getExperienciaMinima() > 0 ||
                    currentFiltros.getMaxPrecio() < 100 || // Default maxPrecio is 100
                    currentFiltros.isSoloVerificados() ||
                    (currentFiltros.getTamanosMascota() != null && !currentFiltros.getTamanosMascota().isEmpty()) ||
                    !currentFiltros.getOrden().equals("Distancia (más cercano)") // Default order
            );

            if (normalizedQuery.isEmpty()) {
                if (!hasActiveFilters) {
                    currentQuery = "";
                    lastVisibleDocument = null;
                    isLastPage = true;
                    _searchResults.setValue(UiState.empty()); // Mostrar estado vacío si no hay query ni filtros
                    return;
                }
                // Si la query es vacía pero hay filtros activos, continuamos con la búsqueda para aplicar los filtros
            }
            executeSearch(normalizedQuery, false, _filtros.getValue());
        };
        debounceHandler.postDelayed(debounceRunnable, 300); // 300ms de delay
    }

    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        String nfdNormalizedString = Normalizer.normalize(texto, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("").toLowerCase();
    }

    public void aplicarFiltros(Filtros nuevosFiltros) {
        Log.d(TAG, "aplicarFiltros: " + nuevosFiltros.toString());
        _filtros.setValue(nuevosFiltros);
        executeSearch(currentQuery, false, nuevosFiltros);
    }

    public void limpiarFiltros() {
        Log.d(TAG, "limpiarFiltros");
        _filtros.setValue(new Filtros());
        executeSearch(currentQuery, false, new Filtros());
    }

    public void setCalificacionMinima(double calificacion) {
        Filtros filtrosActuales = _filtros.getValue();
        if (filtrosActuales != null) {
            filtrosActuales.setMinCalificacion((float) calificacion);
            _filtros.setValue(filtrosActuales);
            executeSearch(currentQuery, false, filtrosActuales);
        }
    }

    public void setExperienciaMinima(int experiencia) {
        Filtros filtrosActuales = _filtros.getValue();
        if (filtrosActuales != null) {
            filtrosActuales.setExperienciaMinima(experiencia);
            _filtros.setValue(filtrosActuales);
            executeSearch(currentQuery, false, filtrosActuales);
        }
    }

    public void setPrecioMaximo(double precio) {
        Filtros filtrosActuales = _filtros.getValue();
        if (filtrosActuales != null) {
            filtrosActuales.setMaxPrecio((float) precio);
            _filtros.setValue(filtrosActuales);
            executeSearch(currentQuery, false, filtrosActuales);
        }
    }

    public void setSoloVerificados(boolean soloVerificados) {
        Filtros filtrosActuales = _filtros.getValue();
        if (filtrosActuales != null) {
            filtrosActuales.setSoloVerificados(soloVerificados);
            _filtros.setValue(filtrosActuales);
            executeSearch(currentQuery, false, filtrosActuales);
        }
    }

    public void loadMore() {
        Log.d(TAG, "loadMore");
        if (isLoadingMore || isLastPage) return;
        executeSearch(currentQuery, true, _filtros.getValue());
    }

    public void toggleFavorito(String paseadorId, boolean isFavorito) {
        // Llama al repositorio para cambiar el estado de favorito.
        // La UI se actualizará automáticamente si el repositorio emite nuevos datos,
        // o se puede forzar una recarga si es necesario.
        repository.toggleFavorito(paseadorId, isFavorito);
    }

    // --- Métodos de Historial de Búsqueda ---
    public List<String> getSearchHistory() {
        return repository.getSearchHistory();
    }

    public void clearSearchHistory() {
        repository.clearSearchHistory();
    }

    public void removeFromSearchHistory(String query) {
        repository.removeFromSearchHistory(query);
    }

    private void executeSearch(String query, final boolean isPaginating, Filtros filtros) {
        if (!isPaginating) {
            currentQuery = query;
            lastVisibleDocument = null;
            isLastPage = false;
            _searchResults.setValue(UiState.loading());
        }

        isLoadingMore = true;

        repository.buscarPaseadores(query, lastVisibleDocument, filtros).observeForever(new androidx.lifecycle.Observer<UiState<PaseadorSearchResult>>() {
            @Override
            public void onChanged(UiState<PaseadorSearchResult> uiState) {
                if (this == null) return; // Evitar operar sobre observer desvinculado

                if (uiState instanceof UiState.Success) {
                    PaseadorSearchResult searchResult = ((UiState.Success<PaseadorSearchResult>) uiState).getData();
                    if (searchResult == null) return;

                    lastVisibleDocument = searchResult.lastVisible;
                    List<PaseadorResultado> newResults = searchResult.resultados;

                    if (newResults == null || newResults.size() < 15) {
                        isLastPage = true;
                    }

                    if (isPaginating) {
                        UiState<List<PaseadorResultado>> currentState = _searchResults.getValue();
                        if (currentState instanceof UiState.Success) {
                            List<PaseadorResultado> currentList = new ArrayList<>(((UiState.Success<List<PaseadorResultado>>) currentState).getData());
                            currentList.addAll(newResults);
                            _searchResults.postValue(UiState.success(currentList));
                        }
                    } else {
                        _searchResults.postValue(UiState.success(newResults));
                    }

                } else if (uiState instanceof UiState.Empty) {
                    if (!isPaginating) {
                        _searchResults.postValue(UiState.empty());
                    }
                    isLastPage = true;
                } else if (uiState instanceof UiState.Error) {
                    _searchResults.postValue(UiState.error(((UiState.Error<PaseadorSearchResult>) uiState).getMessage()));
                }
                isLoadingMore = false;
                // Desvincular el observer para evitar fugas de memoria y ejecuciones múltiples
                repository.buscarPaseadores(query, lastVisibleDocument, filtros).removeObserver(this);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Cuando el ViewModel se destruye, limpiar todos los listeners del repositorio
        repository.cleanupListeners();
    }
}
