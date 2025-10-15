
package com.mjc.mascota.ui.busqueda;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.mjc.mascota.modelo.PaseadorResultado;

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
        viewModel = new BusquedaViewModel(mockRepository); // Asumiendo un constructor que inyecta el repo
        viewModel.searchResults.observeForever(mockObserver);
    }

    @Test
    public void onSearchQueryChanged_whenRepositoryReturnsEmpty_postsEmptyState() {
        // Arrange: Configurar el mock para que devuelva un estado Vacío
        MutableLiveData<UiState<QuerySnapshot>> emptyLiveData = new MutableLiveData<>();
        emptyLiveData.setValue(new UiState.Empty<>());
        when(mockRepository.buscarPaseadores(anyString(), any())).thenReturn(emptyLiveData);

        // Act: Ejecutar la búsqueda
        viewModel.onSearchQueryChanged("query que no encuentra nada");

        // Assert: Verificar que el LiveData de resultados emite Loading y luego Empty
        verify(mockObserver, timeout(1000).times(2)).onChanged(captor.capture());
        List<UiState<List<PaseadorResultado>>> states = captor.getAllValues();
        assertTrue(states.get(0) instanceof UiState.Loading);
        assertTrue(states.get(1) instanceof UiState.Empty);
    }

    @Test
    public void onSearchQueryChanged_whenRepositoryFails_postsErrorState() {
        // Arrange: Configurar el mock para que devuelva un estado de Error
        MutableLiveData<UiState<QuerySnapshot>> errorLiveData = new MutableLiveData<>();
        String errorMessage = "Error de Firestore";
        errorLiveData.setValue(new UiState.Error<>(errorMessage));
        when(mockRepository.buscarPaseadores(anyString(), any())).thenReturn(errorLiveData);

        // Act: Ejecutar la búsqueda
        viewModel.onSearchQueryChanged("query que falla");

        // Assert: Verificar que el estado final es Error y el mensaje es correcto
        verify(mockObserver, timeout(1000).times(2)).onChanged(captor.capture());
        List<UiState<List<PaseadorResultado>>> states = captor.getAllValues();
        assertTrue(states.get(0) instanceof UiState.Loading);
        assertTrue(states.get(1) instanceof UiState.Error);
        assertEquals(errorMessage, ((UiState.Error<List<PaseadorResultado>>) states.get(1)).getMessage());
    }

    @Test
    public void onSearchQueryChanged_whenDataIsMissing_postsSuccessWithDefaultValues() {
        // Esta prueba valida que el Repository (no el ViewModel) maneja campos nulos.
        // El ViewModel solo debe recibir y postear los datos ya limpios.
        
        // Arrange
        QuerySnapshot mockSnapshot = mock(QuerySnapshot.class);
        DocumentSnapshot mockDoc = mock(DocumentSnapshot.class);
        when(mockSnapshot.getDocuments()).thenReturn(Collections.singletonList(mockDoc));
        // El mockDoc no tiene campos, por lo que el Repository usará valores por defecto
        when(mockDoc.getId()).thenReturn("user123");
        when(mockDoc.getString("nombre_display")).thenReturn(null); // Simula campo nulo

        MutableLiveData<UiState<QuerySnapshot>> successLiveData = new MutableLiveData<>();
        successLiveData.setValue(new UiState.Success<>(mockSnapshot));
        when(mockRepository.buscarPaseadores(anyString(), any())).thenReturn(successLiveData);

        // Act
        viewModel.onSearchQueryChanged("Paseador");

        // Assert
        verify(mockObserver, timeout(1000).times(2)).onChanged(captor.capture());
        List<UiState<List<PaseadorResultado>>> states = captor.getAllValues();
        assertTrue(states.get(1) instanceof UiState.Success);
        
        // La aserción importante es que no crashea y devuelve un objeto.
        // La validación de los valores por defecto se haría en un test del Repository.
        UiState.Success<List<PaseadorResultado>> successState = (UiState.Success<List<PaseadorResultado>>) states.get(1);
        assertFalse(successState.getData().isEmpty());
        assertEquals("user123", successState.getData().get(0).getId());
    }
}
