
package com.mjc.mascota.ui.busqueda;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.mjc.mascota.modelo.PaseadorResultado;

import com.mjc.mascota.modelo.Filtros;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BusquedaViewModelTest {

    // Regla para que LiveData se ejecute de forma síncrona en las pruebas
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private PaseadorRepository mockRepository;

    @Mock
    private Observer<UiState<List<PaseadorResultado>>> mockObserver;

    @Captor
    private ArgumentCaptor<UiState<List<PaseadorResultado>>> captor;

    private BusquedaViewModel viewModel;

    @Before
    public void setUp() {
        MutableLiveData<UiState<List<PaseadorResultado>>> popularLiveData = new MutableLiveData<>();
        popularLiveData.setValue(new UiState.Empty<>());
        when(mockRepository.getPaseadoresPopulares()).thenReturn(popularLiveData);

        viewModel = new BusquedaViewModel(mockRepository);
    }

    @Test
    public void onSearchQueryChanged_whenRepositoryReturnsEmpty_postsEmptyState() throws InterruptedException {
        // Arrange
        MutableLiveData<UiState<PaseadorSearchResult>> emptyLiveData = new MutableLiveData<>();
        emptyLiveData.setValue(new UiState.Empty<>());
        when(mockRepository.buscarPaseadores(anyString(), any(), any(Filtros.class))).thenReturn(emptyLiveData);

        // Act
        viewModel.onSearchQueryChanged("query");

        // Assert
        UiState<List<PaseadorResultado>> result = LiveDataTestUtil.getOrAwaitValue(viewModel.searchResults);
        assertTrue(result instanceof UiState.Empty);
    }

    @Test
    public void onSearchQueryChanged_whenRepositoryFails_postsErrorState() throws InterruptedException {
        // Arrange
        MutableLiveData<UiState<PaseadorSearchResult>> errorLiveData = new MutableLiveData<>();
        String errorMessage = "Error";
        errorLiveData.setValue(new UiState.Error<>(errorMessage));
        when(mockRepository.buscarPaseadores(anyString(), any(), any(Filtros.class))).thenReturn(errorLiveData);

        // Act
        viewModel.onSearchQueryChanged("query");

        // Assert
        UiState<List<PaseadorResultado>> result = LiveDataTestUtil.getOrAwaitValue(viewModel.searchResults);
        assertTrue(result instanceof UiState.Error);
        assertEquals(errorMessage, ((UiState.Error<List<PaseadorResultado>>) result).getMessage());
    }

    @Test
    public void onSearchQueryChanged_whenRepositorySucceeds_postsSuccessState() throws InterruptedException {
        // Arrange
        PaseadorSearchResult searchResult = new PaseadorSearchResult(Collections.emptyList(), null);
        MutableLiveData<UiState<PaseadorSearchResult>> successLiveData = new MutableLiveData<>();
        successLiveData.setValue(new UiState.Success<>(searchResult));
        when(mockRepository.buscarPaseadores(anyString(), any(), any(Filtros.class))).thenReturn(successLiveData);

        // Act
        viewModel.onSearchQueryChanged("query");

        // Assert
        UiState<List<PaseadorResultado>> result = LiveDataTestUtil.getOrAwaitValue(viewModel.searchResults);
        assertTrue(result instanceof UiState.Success);
    }
}
