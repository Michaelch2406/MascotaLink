package com.mjc.mascota.ui.busqueda;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.DocumentSnapshot;
import com.mjc.mascota.modelo.Filtros;
import com.mjc.mascota.modelo.PaseadorResultado;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.mjc.mascota.ui.busqueda.PaseadorRepository;
import com.mjc.mascota.ui.busqueda.PaseadorSearchResult;
import com.mjc.mascota.ui.busqueda.UiState;

import androidx.lifecycle.MediatorLiveData;
import java.util.Objects;

public class BusquedaViewModel extends ViewModel {

    private static final String TAG = "BusquedaViewModel";

    private final PaseadorRepository repository;
    private LiveData<UiState<List<PaseadorResultado>>> paseadoresPopularesState;

    // --- Lógica de Búsqueda y Paginación ---
    private final MediatorLiveData<UiState<List<PaseadorResultado>>> _searchResults = new MediatorLiveData<>();
    public final LiveData<UiState<List<PaseadorResultado>>> searchResults = _searchResults;

    private final MutableLiveData<SearchParameters> searchTrigger = new MutableLiveData<>();
    private LiveData<UiState<PaseadorSearchResult>> currentSearchSource = null;

    private boolean isLoadingMore = false;
    private boolean isLastPage = false;
    private DocumentSnapshot lastVisibleDocument = null;

    // Clase interna para agrupar los parámetros de búsqueda
    private static class SearchParameters {
        final String query;
        final Filtros filtros;

        SearchParameters(String query, Filtros filtros) {
            this.query = query;
            this.filtros = filtros;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SearchParameters that = (SearchParameters) o;
            return Objects.equals(query, that.query) && Objects.equals(filtros, that.filtros);
        }

        @Override
        public int hashCode() {
            return Objects.hash(query, filtros);
        }
    }

    public BusquedaViewModel() {
        this(new PaseadorRepository());
    }

    public BusquedaViewModel(PaseadorRepository repository) {
        this.repository = repository;
        loadPaseadoresPopulares();

        _searchResults.addSource(searchTrigger, params -> {
            isLastPage = false;
            lastVisibleDocument = null;
            isLoadingMore = false;
            executeSearch(params, false);
        });
    }

    public LiveData<UiState<List<PaseadorResultado>>> getPaseadoresPopularesState() {
        return paseadoresPopularesState;
    }

    public LiveData<Filtros> getFiltros() {
        return repository.getFiltros();
    }

    public void aplicarFiltros(Filtros nuevosFiltros) {
        String currentQuery = searchTrigger.getValue() != null ? searchTrigger.getValue().query : "";
        searchTrigger.setValue(new SearchParameters(currentQuery, nuevosFiltros));
    }

    public void limpiarFiltros() {
        String currentQuery = searchTrigger.getValue() != null ? searchTrigger.getValue().query : "";
        searchTrigger.setValue(new SearchParameters(currentQuery, new Filtros()));
    }

    // --- Acciones de la UI --- //
    public void loadPaseadoresPopulares() {
        if (paseadoresPopularesState == null) {
            paseadoresPopularesState = repository.getPaseadoresPopulares();
        }
    }

    public void onSearchQueryChanged(String query) {
        String normalizedQuery = normalizarTexto(query);
        Filtros currentFiltros = searchTrigger.getValue() != null ? searchTrigger.getValue().filtros : new Filtros();
        searchTrigger.setValue(new SearchParameters(normalizedQuery, currentFiltros));
    }

    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        String nfdNormalizedString = Normalizer.normalize(texto, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("").toLowerCase();
    }
    
    private void updateFiltros(Filtros nuevosFiltros) {
        String currentQuery = searchTrigger.getValue() != null ? searchTrigger.getValue().query : "";
        searchTrigger.setValue(new SearchParameters(currentQuery, nuevosFiltros));
    }

    public void setCalificacionMinima(double calificacion) {
        Filtros filtrosActuales = searchTrigger.getValue() != null ? searchTrigger.getValue().filtros : new Filtros();
        filtrosActuales.setMinCalificacion((float) calificacion);
        updateFiltros(filtrosActuales);
    }

    public void setExperienciaMinima(int experiencia) {
        Filtros filtrosActuales = searchTrigger.getValue() != null ? searchTrigger.getValue().filtros : new Filtros();
        filtrosActuales.setExperienciaMinima(experiencia);
        updateFiltros(filtrosActuales);
    }

    public void setPrecioMaximo(double precio) {
        Filtros filtrosActuales = searchTrigger.getValue() != null ? searchTrigger.getValue().filtros : new Filtros();
        filtrosActuales.setMaxPrecio((float) precio);
        updateFiltros(filtrosActuales);
    }

    public void setSoloVerificados(boolean soloVerificados) {
        Filtros filtrosActuales = searchTrigger.getValue() != null ? searchTrigger.getValue().filtros : new Filtros();
        filtrosActuales.setSoloVerificados(soloVerificados);
        updateFiltros(filtrosActuales);
    }

    public void loadMore() {
        if (isLoadingMore || isLastPage) return;
        SearchParameters currentParams = searchTrigger.getValue();
        if (currentParams != null) {
            executeSearch(currentParams, true);
        }
    }
    
    private void executeSearch(SearchParameters params, final boolean isPaginating) {
        if (!isPaginating) {
            _searchResults.setValue(UiState.loading());
        }

        if (currentSearchSource != null) {
            _searchResults.removeSource(currentSearchSource);
        }

        isLoadingMore = true;
        currentSearchSource = repository.buscarPaseadores(params.query, lastVisibleDocument, params.filtros);

        _searchResults.addSource(currentSearchSource, uiState -> {
            if (uiState instanceof UiState.Success) {
                handleSuccess(uiState, isPaginating);
            } else {
                handleLoadingOrError(uiState);
            }
        });
    }

    private void handleSuccess(UiState<PaseadorSearchResult> uiState, boolean isPaginating) {
        PaseadorSearchResult searchResult = ((UiState.Success<PaseadorSearchResult>) uiState).getData();
        if (searchResult == null) {
            _searchResults.setValue(UiState.empty());
            isLastPage = true;
            isLoadingMore = false;
            return;
        }

        lastVisibleDocument = searchResult.lastVisible;
        List<PaseadorResultado> newResults = searchResult.resultados;

        if (newResults == null || newResults.isEmpty()) {
            if (!isPaginating) {
                _searchResults.setValue(UiState.empty());
            }
            isLastPage = true;
        } else {
            if (newResults.size() < 15) { // Límite de página
                isLastPage = true;
            }

            if (isPaginating) {
                UiState<List<PaseadorResultado>> currentState = _searchResults.getValue();
                if (currentState instanceof UiState.Success) {
                    List<PaseadorResultado> currentList = new ArrayList<>(((UiState.Success<List<PaseadorResultado>>) currentState).getData());
                    currentList.addAll(newResults);
                    _searchResults.setValue(UiState.success(currentList));
                }
            } else {
                _searchResults.setValue(UiState.success(newResults));
            }
        }
        isLoadingMore = false;
    }

    private void handleLoadingOrError(UiState<PaseadorSearchResult> uiState) {
        _searchResults.setValue((UiState) uiState);
        if (uiState instanceof UiState.Empty) {
            isLastPage = true;
        }
        isLoadingMore = false;
    }

    public void toggleFavorito(String paseadorId, boolean isFavorito) {
        repository.toggleFavorito(paseadorId, isFavorito);
    }

    public List<String> getSearchHistory() {
        return repository.getSearchHistory();
    }

    public void clearSearchHistory() {
        repository.clearSearchHistory();
    }

    public void removeFromSearchHistory(String query) {
        repository.removeFromSearchHistory(query);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.cleanupListeners();
    }
}
