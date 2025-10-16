package com.mjc.mascota.ui.busqueda;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

public class BusquedaPaseadoresActivity extends AppCompatActivity implements OnMapReadyCallback { 

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String SEARCH_QUERY_KEY = "SEARCH_QUERY_KEY";
    private BusquedaViewModel viewModel;
    private FirebaseAuth mAuth;
    private GoogleMap mMap;
    private ClusterManager<MyItem> mClusterManager;



    // Vistas de la UI
    private ProgressBar progressBar;
    private View emptyStateView;
    private View errorStateView;
    private Button retryButton;
    private RecyclerView recyclerViewPopulares;
    private PaseadorResultadoAdapter popularesAdapter;
    private RecyclerView recyclerViewResultados;
    private PaseadorResultadoAdapter resultadosAdapter;
    private SearchView searchView;
    private NestedScrollView contentScrollView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_busqueda_paseadores); // Layout de la FASE 4

        mAuth = FirebaseAuth.getInstance();
        viewModel = new ViewModelProvider(this).get(BusquedaViewModel.class);

        // El binding de las vistas se haría aquí con findViewById o ViewBinding
        progressBar = findViewById(R.id.progressBar);
        emptyStateView = findViewById(R.id.emptyStateView);
        errorStateView = findViewById(R.id.errorStateView);
        retryButton = findViewById(R.id.retryButton);
        recyclerViewPopulares = findViewById(R.id.recycler_paseadores_populares);
        // Suponiendo que el layout tiene un segundo RecyclerView para los resultados
        recyclerViewResultados = findViewById(R.id.recycler_resultados_busqueda); 
        searchView = findViewById(R.id.search_view);
        contentScrollView = findViewById(R.id.content_scroll_view);

        setupRecyclerViews();
        setupSearch();
        setupPagination();
        checkUserSessionAndRole();

        retryButton.setOnClickListener(v -> {
            // Reintentar la última acción que falló
            viewModel.onSearchQueryChanged(searchView.getQuery().toString());
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
                searchView.setQuery(savedQuery, true);
            }
        }
    }

    private void checkUserSessionAndRole() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            redirectToLogin("No hay sesión activa.");
            return;
        }

        FirebaseFirestore.getInstance().collection("usuarios").document(currentUser.getUid()).get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String rol = documentSnapshot.getString("rol");
                    if ("DUEÑO".equals(rol)) {
                        setupObservers();
                    } else {
                        Toast.makeText(this, "Acceso no autorizado para este rol.", Toast.LENGTH_LONG).show();
                        if ("PASEADOR".equals(rol)) {
                            startActivity(new Intent(this, PerfilPaseadorActivity.class));
                        }
                        finish();
                    }
                } else {
                    redirectToLogin("Error: Perfil de usuario no encontrado.");
                }
            })
            .addOnFailureListener(e -> redirectToLogin("Error al verificar el perfil de usuario."));
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
            intent.putExtra("viewerRole", "DUENO"); // Informar que un dueño está viendo el perfil
            startActivity(intent);
        });

        // Adapter para los resultados de búsqueda
        recyclerViewResultados.setLayoutManager(new LinearLayoutManager(this));
        resultadosAdapter = new PaseadorResultadoAdapter();
        recyclerViewResultados.setAdapter(resultadosAdapter);
        // Aquí también se configuraría el click listener para este adapter
    }

    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                viewModel.onSearchQueryChanged(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                viewModel.onSearchQueryChanged(newText);
                // Mostrar/ocultar vistas según si hay texto o no
                if (newText.isEmpty()) {
                    recyclerViewResultados.setVisibility(View.GONE);
                    contentScrollView.setVisibility(View.VISIBLE); // Mostrar populares, etc.
                } else {
                    recyclerViewResultados.setVisibility(View.VISIBLE);
                    contentScrollView.setVisibility(View.GONE); // Ocultar populares
                }
                return true;
            }
        });
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
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.GONE);
        resultadosAdapter.submitList(data);
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerViewPopulares.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.GONE);
    }

    private void showSuccess(java.util.List<PaseadorResultado> data) {
        progressBar.setVisibility(View.GONE);
        recyclerViewPopulares.setVisibility(View.VISIBLE);
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.GONE);
        // Enviar la nueva lista al adapter. DiffUtil se encargará del resto.
        popularesAdapter.submitList(data);
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        recyclerViewPopulares.setVisibility(View.GONE);
        emptyStateView.setVisibility(View.GONE);
        errorStateView.setVisibility(View.VISIBLE);
        ((TextView) errorStateView.findViewById(R.id.error_message)).setText(message);
    }

    private void showEmpty() {
        progressBar.setVisibility(View.GONE);
        recyclerViewPopulares.setVisibility(View.GONE);
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
                        if (searchView != null) {
                            outState.putString(SEARCH_QUERY_KEY, searchView.getQuery().toString());
                        }
                    }
                }

                