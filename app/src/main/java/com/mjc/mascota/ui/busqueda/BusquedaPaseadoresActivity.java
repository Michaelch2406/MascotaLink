package com.mjc.mascota.ui.busqueda;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascota.modelo.Filtros;
import com.mjc.mascotalink.LoginActivity;
import com.mjc.mascotalink.PerfilDuenoActivity;
import com.mjc.mascotalink.PerfilPaseadorActivity;
import com.mjc.mascotalink.R;
import com.mjc.mascota.modelo.PaseadorResultado;
import com.mjc.mascota.ui.busqueda.BusquedaViewModel;
import com.mjc.mascota.ui.busqueda.PaseadorResultadoAdapter;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterManager;
import com.mjc.mascota.utils.MyItem;
import com.mjc.mascota.ui.busqueda.UiState;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.appcompat.widget.SearchView;
import androidx.core.widget.NestedScrollView;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RatingBar;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BusquedaPaseadoresActivity extends AppCompatActivity implements OnMapReadyCallback { 

    private static final String TAG = "BusquedaPaseadoresActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String SEARCH_QUERY_KEY = "SEARCH_QUERY_KEY";
    private static final String SEARCH_HISTORY_PREFS = "SearchHistoryPrefs";
    private static final String SEARCH_HISTORY_KEY = "SearchHistory";
    private static final long SEARCH_DELAY_MS = 2000; // 2 segundos

    private BusquedaViewModel viewModel;
    private FirebaseAuth mAuth;
    private GoogleMap mMap;
    private ClusterManager<MyItem> mClusterManager;

    // Handler para el retraso en la búsqueda
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // Vistas de la UI
    private ProgressBar progressBar;
    private View emptyStateView;
    private View errorStateView;
    private Button retryButton;
    private RecyclerView recyclerViewPopulares;
    private PaseadorResultadoAdapter popularesAdapter;
    private RecyclerView recyclerViewResultados;
    private PaseadorResultadoAdapter resultadosAdapter;
    private AutoCompleteTextView searchAutocomplete;
    private SwipeRefreshLayout swipeRefreshLayout;
    private NestedScrollView contentScrollView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_busqueda_paseadores); // Layout de la FASE 4

        mAuth = FirebaseAuth.getInstance();
        viewModel = new ViewModelProvider(this).get(BusquedaViewModel.class);

        // El binding de las vistas se haría aquí con findViewById o ViewBinding
        progressBar = findViewById(R.id.progressBar);
        emptyStateView = findViewById(R.id.emptyStateView);
        errorStateView = findViewById(R.id.errorStateView);
        retryButton = findViewById(R.id.retryButton);
        recyclerViewPopulares = findViewById(R.id.recycler_paseadores_populares);
        recyclerViewResultados = findViewById(R.id.recycler_resultados_busqueda);
        // Suponiendo que el layout tiene un segundo RecyclerView para los resultados
        searchAutocomplete = findViewById(R.id.search_autocomplete);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        contentScrollView = findViewById(R.id.content_scroll_view);

        setupRecyclerViews();
        setupSearch();
        setupPullToRefresh(); // Añadido
        setupPagination();
        setupToolbar();


        retryButton.setOnClickListener(v -> {
            // Reintentar la última acción que falló
            viewModel.onSearchQueryChanged(searchAutocomplete.getText().toString());
        });

        // Iniciar la carga del mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setupBottomNavigation();

        // Restaurar estado si es necesario (ej. después de rotación)
        if (savedInstanceState != null) {
            String savedQuery = savedInstanceState.getString(SEARCH_QUERY_KEY);
            if (savedQuery != null && !savedQuery.isEmpty()) {
                searchAutocomplete.setText(savedQuery);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        // Se comprueba la sesión y el rol del usuario cada vez que la actividad se vuelve visible.
        // Esto asegura que si el usuario cierra sesión y vuelve a entrar, o si su rol cambia,
        // la UI reaccione correctamente sin necesidad de reiniciar la app.
        checkUserSessionAndRole();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        // Se quitan los observadores cuando la actividad ya no es visible.
        // Esto previene cualquier actualización de la UI en segundo plano y evita posibles memory leaks,
        // aunque LiveData ya es consciente del ciclo de vida. Es una buena práctica ser explícito.
        if (viewModel != null) {
            viewModel.getPaseadoresPopularesState().removeObservers(this);
            viewModel.searchResults.removeObservers(this);
        }
    }

    private void checkUserSessionAndRole() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "checkUserSessionAndRole: No hay sesión activa.");
            redirectToLogin("No hay sesión activa.");
            return;
        }

        Log.d(TAG, "checkUserSessionAndRole: Verificando rol para UID: " + currentUser.getUid());
        FirebaseFirestore.getInstance().collection("usuarios").document(currentUser.getUid()).get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String rol = documentSnapshot.getString("rol");
                    Log.d(TAG, "checkUserSessionAndRole: Rol encontrado: " + rol);
                    if ("DUEÑO".equals(rol)) {
                        // El rol es correcto, procedemos a configurar los observadores
                        // para recibir las actualizaciones de datos.
                        setupObservers();
                    } else {
                        Log.w(TAG, "checkUserSessionAndRole: Acceso no autorizado para rol: " + rol);
                        Toast.makeText(this, "Acceso no autorizado para este rol.", Toast.LENGTH_LONG).show();
                        if ("PASEADOR".equals(rol)) {
                            startActivity(new Intent(this, PerfilPaseadorActivity.class));
                        }
                        finish();
                    }
                } else {
                    Log.e(TAG, "checkUserSessionAndRole: Perfil de usuario no encontrado para UID: " + currentUser.getUid());
                    redirectToLogin("Error: Perfil de usuario no encontrado.");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "checkUserSessionAndRole: Error al verificar el perfil de usuario.", e);
                redirectToLogin("Error al verificar el perfil de usuario.");
            });
    }

    private void setupRecyclerViews() {
        // Configuración para el RecyclerView de paseadores populares
        recyclerViewPopulares.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        popularesAdapter = new PaseadorResultadoAdapter();
        recyclerViewPopulares.setAdapter(popularesAdapter);

        // Configurar el listener para los clicks
        popularesAdapter.setOnItemClickListener(paseador -> {
            // Acción al hacer click: ir al perfil del paseador
            Intent intent = new Intent(BusquedaPaseadoresActivity.this, PerfilPaseadorActivity.class);
            intent.putExtra("paseadorId", paseador.getId());
            intent.putExtra("viewerRole", "DUEÑO"); // Informar que un dueño está viendo el perfil
            startActivity(intent);
        });

        // Adapter para los resultados de búsqueda
        recyclerViewResultados.setLayoutManager(new LinearLayoutManager(this));
        resultadosAdapter = new PaseadorResultadoAdapter();
        recyclerViewResultados.setAdapter(resultadosAdapter);
        resultadosAdapter.setOnItemClickListener(paseador -> {
            // Acción al hacer click: ir al perfil del paseador
            Intent intent = new Intent(BusquedaPaseadoresActivity.this, PerfilPaseadorActivity.class);
            intent.putExtra("paseadorId", paseador.getId());
            intent.putExtra("viewerRole", "DUEÑO"); // Informar que un dueño está viendo el perfil
            startActivity(intent);
        });
    }

    private void setupSearch() {
        setupSearchHistory();

        searchRunnable = () -> {
            String query = searchAutocomplete.getText().toString();
            viewModel.onSearchQueryChanged(query);
            saveSearchQuery(query);
        };

        searchAutocomplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString();
                if (query.length() > 2 || query.isEmpty()) {
                    searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
                }

                if (query.isEmpty()) {
                    recyclerViewResultados.setVisibility(View.GONE);
                    contentScrollView.setVisibility(View.VISIBLE);
                } else {
                    recyclerViewResultados.setVisibility(View.VISIBLE);
                    contentScrollView.setVisibility(View.GONE);
                }
            }
        });

        searchAutocomplete.setOnItemClickListener((parent, view, position, id) -> {
            String query = (String) parent.getItemAtPosition(position);
            searchAutocomplete.setText(query);
            viewModel.onSearchQueryChanged(query);
        });
    }

    private void setupSearchHistory() {
        Set<String> history = getSearchHistory();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(history));
        searchAutocomplete.setAdapter(adapter);
    }

    private Set<String> getSearchHistory() {
        return getSharedPreferences(SEARCH_HISTORY_PREFS, MODE_PRIVATE)
                .getStringSet(SEARCH_HISTORY_KEY, new HashSet<>());
    }

    private void saveSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) return;

        Set<String> history = getSearchHistory();
        // Usar un nuevo HashSet para poder modificarlo
        Set<String> newHistory = new HashSet<>(history);
        newHistory.add(query.trim());

        getSharedPreferences(SEARCH_HISTORY_PREFS, MODE_PRIVATE)
                .edit()
                .putStringSet(SEARCH_HISTORY_KEY, newHistory)
                .apply();

        // Actualizar el adaptador del AutoCompleteTextView
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) searchAutocomplete.getAdapter();
        if (adapter != null) {
            adapter.clear();
            adapter.addAll(newHistory);
            adapter.notifyDataSetChanged();
        }
    }

    private void setupPullToRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.onSearchQueryChanged(searchAutocomplete.getText().toString());
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void showFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_filtros_busqueda, null);
        builder.setView(dialogView);

        // Configurar vistas del diálogo
        Spinner spinnerOrdenar = dialogView.findViewById(R.id.spinner_ordenar_por);
        RangeSlider sliderDistancia = dialogView.findViewById(R.id.slider_distancia);
        RangeSlider sliderPrecio = dialogView.findViewById(R.id.slider_precio);
        RatingBar ratingBar = dialogView.findViewById(R.id.rating_bar_calificacion);
        ChipGroup chipGroup = dialogView.findViewById(R.id.chip_group_tamano_mascota);

        // Llenar Spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.ordenamiento_busqueda, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOrdenar.setAdapter(adapter);

        // Cargar estado actual de los filtros desde el ViewModel
        Filtros currentFiltros = viewModel.filtros.getValue();
        if (currentFiltros != null) {
            // Set spinner selection
            String[] ordenValues = getResources().getStringArray(R.array.ordenamiento_busqueda);
            int spinnerPosition = Arrays.asList(ordenValues).indexOf(currentFiltros.getOrden());
            spinnerOrdenar.setSelection(spinnerPosition);

            // Set sliders
            sliderDistancia.setValues(0f, currentFiltros.getMaxDistancia());
            sliderPrecio.setValues(currentFiltros.getMinPrecio(), currentFiltros.getMaxPrecio());

            // Set rating
            ratingBar.setRating(currentFiltros.getMinCalificacion());

            // Set chips
            if (currentFiltros.getTamanosMascota() != null) {
                for (String tamano : currentFiltros.getTamanosMascota()) {
                    if (tamano.equals("Pequeño")) dialogView.findViewById(R.id.chip_pequeno).performClick();
                    if (tamano.equals("Mediano")) dialogView.findViewById(R.id.chip_mediano).performClick();
                    if (tamano.equals("Grande")) dialogView.findViewById(R.id.chip_grande).performClick();
                }
            }
        }


        final AlertDialog dialog = builder.create();

        dialogView.findViewById(R.id.button_aplicar_filtros).setOnClickListener(v -> {
            Filtros nuevosFiltros = new Filtros();
            nuevosFiltros.setOrden(spinnerOrdenar.getSelectedItem().toString());
            nuevosFiltros.setMaxDistancia(sliderDistancia.getValues().get(1));
            nuevosFiltros.setMinPrecio(sliderPrecio.getValues().get(0));
            nuevosFiltros.setMaxPrecio(sliderPrecio.getValues().get(1));
            nuevosFiltros.setMinCalificacion(ratingBar.getRating());

            List<String> tamanos = new ArrayList<>();
            if (((com.google.android.material.chip.Chip)dialogView.findViewById(R.id.chip_pequeno)).isChecked()) tamanos.add("Pequeño");
            if (((com.google.android.material.chip.Chip)dialogView.findViewById(R.id.chip_mediano)).isChecked()) tamanos.add("Mediano");
            if (((com.google.android.material.chip.Chip)dialogView.findViewById(R.id.chip_grande)).isChecked()) tamanos.add("Grande");
            nuevosFiltros.setTamanosMascota(tamanos);

            viewModel.aplicarFiltros(nuevosFiltros);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.button_limpiar_filtros).setOnClickListener(v -> {
            viewModel.limpiarFiltros();
            // Resetear vistas del dialogo
            spinnerOrdenar.setSelection(0);
            sliderDistancia.setValues(0f, 50f);
            sliderPrecio.setValues(0f, 100f);
            ratingBar.setRating(0);
            ((com.google.android.material.chip.Chip)dialogView.findViewById(R.id.chip_pequeno)).setChecked(false);
            ((com.google.android.material.chip.Chip)dialogView.findViewById(R.id.chip_mediano)).setChecked(false);
            ((com.google.android.material.chip.Chip)dialogView.findViewById(R.id.chip_grande)).setChecked(false);
        });

        dialog.show();
    }

    private void setupToolbar() {
        findViewById(R.id.back_arrow).setOnClickListener(v -> finish());
        findViewById(R.id.settings_icon).setOnClickListener(v -> showFilterDialog());
    }

    private void setupPagination() {
        recyclerViewResultados.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && layoutManager.findLastCompletelyVisibleItemPosition() == resultadosAdapter.getItemCount() - 1) {
                    // El usuario ha llegado al final de la lista
                    viewModel.loadMore();
                }
            }
        });
    }

    private void setupObservers() {
        // Observer para paseadores populares
        viewModel.getPaseadoresPopularesState().observe(this, uiState -> {
            // Lógica para populares (loading, success, etc.)
            if (uiState instanceof UiState.Success) {
                popularesAdapter.submitList(((UiState.Success<java.util.List<PaseadorResultado>>) uiState).getData());
            }
        });

        // Observer para resultados de búsqueda
        viewModel.searchResults.observe(this, uiState -> {
            if (uiState instanceof UiState.Loading) {
                showLoading();
            } else if (uiState instanceof UiState.Success) {
                showSuccessSearchResults(((UiState.Success<java.util.List<PaseadorResultado>>) uiState).getData());
            } else if (uiState instanceof UiState.Error) {
                showError(((UiState.Error<java.util.List<PaseadorResultado>>) uiState).getMessage());
            } else if (uiState instanceof UiState.Empty) {
                showEmpty();
            }
        });
    }

    private void showSuccessSearchResults(java.util.List<PaseadorResultado> data) {
        progressBar.setVisibility(View.GONE);
        contentScrollView.setVisibility(View.GONE);
        recyclerViewResultados.setVisibility(View.VISIBLE);
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.GONE);
        resultadosAdapter.submitList(data);
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        contentScrollView.setVisibility(View.GONE);
        recyclerViewResultados.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.GONE);
    }

    private void showSuccess(java.util.List<PaseadorResultado> data) {
        progressBar.setVisibility(View.GONE);
        contentScrollView.setVisibility(View.VISIBLE);
        recyclerViewResultados.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.GONE);
        // Enviar la nueva lista al adapter. DiffUtil se encargará del resto.
        popularesAdapter.submitList(data);
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        contentScrollView.setVisibility(View.GONE);
        recyclerViewResultados.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.VISIBLE);
        ((TextView) errorStateView.findViewById(R.id.error_message)).setText(message);
    }

    private void showEmpty() {
        progressBar.setVisibility(View.GONE);
        contentScrollView.setVisibility(View.GONE);
        recyclerViewResultados.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.VISIBLE);
        errorStateView.setVisibility(View.GONE);
    }

    private void redirectToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        mAuth.signOut();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        
            @Override
            public void onMapReady(@NonNull GoogleMap googleMap) {
                mMap = googleMap;

                // --- MEJORA 4: MAPA MEJORADO ---
                mMap.getUiSettings().setZoomControlsEnabled(true);
                mMap.getUiSettings().setScrollGesturesEnabled(true);
                mMap.getUiSettings().setZoomGesturesEnabled(true);
                mMap.getUiSettings().setTiltGesturesEnabled(true);
                mMap.getUiSettings().setRotateGesturesEnabled(true);
                // --- FIN MEJORA 4 ---

                // Inicializar el ClusterManager
                mClusterManager = new ClusterManager<>(this, mMap);
        
                mMap.setOnCameraIdleListener(mClusterManager);
                mMap.setOnMarkerClickListener(mClusterManager);
        
                // Pedir permisos y configurar la UI del mapa
                checkLocationPermission();
        
                // Mover la cámara a una ubicación por defecto (ej. Quito)
                LatLng quito = new LatLng(-0.180653, -78.467834);
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(quito, 12));
                
                // Aquí se podría llamar a viewModel.loadPaseadoresCercanos() para poblar el mapa
            }
        
            private void checkLocationPermission() {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                } else {
                    // El permiso no ha sido concedido, solicitarlo.
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE);
                }
            }
        
            @Override
            public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        // Permiso concedido
                        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED) {
                            mMap.setMyLocationEnabled(true);
                        }
                    } else {
                        // Permiso denegado. Se puede mostrar un mensaje al usuario.
                        Toast.makeText(this, "El permiso de ubicación es necesario para ver paseadores cercanos", Toast.LENGTH_LONG).show();
                    }
                }
            }
        
            private void addPaseadoresToMap(java.util.List<PaseadorResultado> paseadores) {
                if (mMap == null || mClusterManager == null) return;
        
                mClusterManager.clearItems();
        
                for (PaseadorResultado paseador : paseadores) {
                    // Asumiendo que PaseadorResultado tiene latitud y longitud
                    // MyItem item = new MyItem(paseador.getLat(), paseador.getLng(), paseador.getNombre(), "Tarifa: $" + paseador.getTarifaPorHora());
                    // mClusterManager.addItem(item);
                }
                        mClusterManager.cluster(); // Agrupar los nuevos items
                    }
                
                    private void setupBottomNavigation() {
                        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
                        bottomNav.setSelectedItemId(R.id.menu_search); // Marcar "Buscar" como activo
                
                        bottomNav.setOnItemSelectedListener(item -> {
                            int itemId = item.getItemId();
                            if (itemId == R.id.menu_home) {
                                // startActivity(new Intent(this, MainActivity.class));
                                // overridePendingTransition(0, 0);
                                return true;
                            } else if (itemId == R.id.menu_search) {
                                // Ya estamos aquí
                                return true;
                            } else if (itemId == R.id.menu_walks) {
                                // startActivity(new Intent(this, PaseosActivity.class));
                                // overridePendingTransition(0, 0);
                                return true;
                            } else if (itemId == R.id.menu_messages) {
                                // startActivity(new Intent(this, MensajesActivity.class));
                                // overridePendingTransition(0, 0);
                                return true;
                            } else if (itemId == R.id.menu_perfil) {
                                // Asumiendo que el rol es DUEÑO, vamos a su perfil
                                startActivity(new Intent(this, PerfilDuenoActivity.class));
                                overridePendingTransition(0, 0);
                                return true;
                            }
                            return false;
                        });
                    }
                
                    @Override
                    protected void onSaveInstanceState(@NonNull Bundle outState) {
                        super.onSaveInstanceState(outState);
                        // Guardar la consulta de búsqueda actual para restaurarla si la Activity se destruye
                        if (searchAutocomplete != null) {
                            outState.putString(SEARCH_QUERY_KEY, searchAutocomplete.getText().toString());
                        }
                    }
                }

                