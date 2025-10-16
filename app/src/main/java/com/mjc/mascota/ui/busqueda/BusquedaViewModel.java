
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

public class BusquedaViewModel extends ViewModel {

    private final PaseadorRepository repository;
    private LiveData<UiState<List<PaseadorResultado>>> paseadoresPopularesState;

    // --- Lógica de Búsqueda y Paginación ---
    private final MutableLiveData<UiState<List<PaseadorResultado>>> _searchResults = new MutableLiveData<>();
    public final LiveData<UiState<List<PaseadorResultado>>> searchResults = _searchResults;

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
        debounceHandler.removeCallbacks(debounceRunnable);
        debounceRunnable = () -> executeSearch(query, false);
        debounceHandler.postDelayed(debounceRunnable, 500); // 500ms de delay
    }

    public void loadMore() {
        if (isLoadingMore || isLastPage) return;
        executeSearch(currentQuery, true);
    }

    private void executeSearch(String query, boolean isPaginating) {
        if (!isPaginating) {
            currentQuery = query;
            lastVisibleDocument = null;
            isLastPage = false;
            _searchResults.setValue(new UiState.Loading<>());
        }

        isLoadingMore = true;

        repository.buscarPaseadores(query, lastVisibleDocument).observeForever(new androidx.lifecycle.Observer<UiState<PaseadorSearchResult>>() {
            @Override
            public void onChanged(UiState<PaseadorSearchResult> uiState) {
                if (uiState instanceof UiState.Success) {
                    PaseadorSearchResult searchResult = ((UiState.Success<PaseadorSearchResult>) uiState).getData();
                    lastVisibleDocument = searchResult.lastVisible;
                    List<PaseadorResultado> newResults = searchResult.resultados;

                    if (newResults.size() < 15) {
                        isLastPage = true;
                    }

                    if (isPaginating) {
                        UiState<List<PaseadorResultado>> currentState = _searchResults.getValue();
                        if (currentState instanceof UiState.Success) {
                            List<PaseadorResultado> currentList = new ArrayList<>(((UiState.Success<List<PaseadorResultado>>) currentState).getData());
                            currentList.addAll(newResults);
                            _searchResults.setValue(new UiState.Success<>(currentList));
                        }
                    } else {
                        _searchResults.setValue(new UiState.Success<>(newResults));
                    }

                } else if (uiState instanceof UiState.Empty) {
                    if (!isPaginating) {
                        _searchResults.setValue(new UiState.Empty<>());
                    }
                    isLastPage = true;
                } else if (uiState instanceof UiState.Error) {
                    _searchResults.setValue(new UiState.Error<>(((UiState.Error<PaseadorSearchResult>) uiState).getMessage()));
                }
                isLoadingMore = false;
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
