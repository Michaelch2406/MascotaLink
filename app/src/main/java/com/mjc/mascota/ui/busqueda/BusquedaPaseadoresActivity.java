package com.mjc.mascota.ui.busqueda;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQueryBounds;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Query;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.google.maps.android.ui.IconGenerator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.mjc.mascota.modelo.Filtros;
import com.mjc.mascota.modelo.PaseadorResultado;
import com.mjc.mascotalink.LoginActivity;
import com.mjc.mascotalink.PerfilDuenoActivity;
import com.mjc.mascotalink.PerfilPaseadorActivity;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.util.BottomNavManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BusquedaPaseadoresActivity extends AppCompatActivity implements OnMapReadyCallback, PaseadorResultadoAdapter.OnItemClickListener, PaseadorResultadoAdapter.OnFavoritoToggleListener {

    private static final String TAG = "BusquedaPaseadoresActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String SEARCH_QUERY_KEY = "SEARCH_QUERY_KEY";
    private static final String SEARCH_HISTORY_PREFS = "SearchHistoryPrefs";
    private static final String SEARCH_HISTORY_KEY = "SearchHistory";
    private static final long SEARCH_DELAY_MS = 300; // 300ms para búsqueda fluida

    private BusquedaViewModel viewModel;
    private FirebaseAuth mAuth;
    private GoogleMap mMap;
    private ClusterManager<PaseadorClusterItem> mClusterManager;

    private FusedLocationProviderClient fusedLocationClient;
    private Location lastKnownLocation;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private final Handler mapDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable mapDebounceRunnable;
    private static final long MAP_DEBOUNCE_DELAY_MS = 1000;
    private static final long ONLINE_TIMEOUT_MS = 3 * 60 * 1000; // 3 minutos para considerar "en línea"

    private float currentSearchRadiusKm = 10.0f; // Radio de búsqueda inicial

    // Cache para los marcadores de paseadores en el mapa
    private final ConcurrentHashMap<String, PaseadorMarker> cachedPaseadorMarkers = new ConcurrentHashMap<>();

    // Handler para el retraso en la búsqueda
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // Vistas de la UI
    private ProgressBar progressBar;
    private View emptyStateView;
    private View errorStateView;
    private Button retryButton;
    private RecyclerView recyclerViewPopulares;
    private PaseadorPopularAdapter popularesAdapter;
    private RecyclerView recyclerViewResultados;
    private PaseadorResultadoAdapter resultadosAdapter;
    private AutoCompleteTextView searchAutocomplete;
    private ArrayAdapter<String> searchHistoryAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private NestedScrollView contentScrollView;
    private BottomNavigationView bottomNav;
    private String userRole = "DUEÑO";
    private Context glideContext;

    // Map Interaction Constants & Views
    private static final int MAP_HEIGHT_COLLAPSED_DP = 250;
    private static final int MAP_HEIGHT_EXPANDED_DP = 500;
    private View mapContainer;
    private View viewMapOverlay;
    private boolean isMapExpanded = false;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabRefreshMap;

    // Handler y Runnable para la actualización periódica del mapa
    private final Handler periodicRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable periodicRefreshRunnable;
    private static final long PERIODIC_REFRESH_INTERVAL_MS = 5 * 60 * 1000; // 5 minutos

    // Location Timeout
    private final Handler locationTimeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable locationTimeoutRunnable;
    private static final long LOCATION_TIMEOUT_MS = 10000; // 10 seconds

    // Optimization
    private Location lastLoadedLocation = null;
    private float lastLoadedZoom = -1;
    private String selectedPaseadorId = null; // Track selected marker

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_busqueda_paseadores);

        mAuth = FirebaseAuth.getInstance();
        glideContext = getApplicationContext();
        
        // Initialize role from cache to prevent flicker
        String cachedRole = BottomNavManager.getUserRole(this);
        if (cachedRole != null) {
             userRole = cachedRole;
        }

        viewModel = new ViewModelProvider(this).get(BusquedaViewModel.class);
        
        // Setup Bottom Navigation immediately to prevent flicker
        if (bottomNav != null) {
             setupBottomNavigation();
        }

        progressBar = findViewById(R.id.progressBar);
        emptyStateView = findViewById(R.id.emptyStateView);
        errorStateView = findViewById(R.id.errorStateView);
        retryButton = findViewById(R.id.retryButton);
        recyclerViewPopulares = findViewById(R.id.recycler_paseadores_populares);
        recyclerViewResultados = findViewById(R.id.recycler_resultados_busqueda);
        searchAutocomplete = findViewById(R.id.search_autocomplete);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        contentScrollView = findViewById(R.id.content_scroll_view);
        bottomNav = findViewById(R.id.bottom_navigation);
        
        // Map Views
        mapContainer = findViewById(R.id.map_container);
        viewMapOverlay = findViewById(R.id.view_map_overlay);
        fabRefreshMap = findViewById(R.id.fab_refresh_map);
        
        fabRefreshMap.setOnClickListener(v -> {
            Toast.makeText(this, "Actualizando mapa...", Toast.LENGTH_SHORT).show();
            if (mMap != null) {
                LatLng center = mMap.getCameraPosition().target;
                cargarPaseadoresCercanos(center, currentSearchRadiusKm);
            } else {
                startLocationUpdates(); // Try to get location if map not ready or location lost
            }
        });

        setupRecyclerViews();
        setupSearch();
        setupPullToRefresh();
        setupPagination();
        setupToolbar();
        setupMapInteraction();

        retryButton.setOnClickListener(v -> {
            viewModel.onSearchQueryChanged(searchAutocomplete.getText().toString());
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationRequest();
        createLocationCallback();

        if (savedInstanceState != null) {
            String savedQuery = savedInstanceState.getString(SEARCH_QUERY_KEY);
            if (savedQuery != null && !savedQuery.isEmpty()) {
                searchAutocomplete.setText(savedQuery);
            }
        }

        // Configurar la actualización periódica del mapa
        periodicRefreshRunnable = () -> {
            if (lastKnownLocation != null && mMap != null) {
                Log.d(TAG, "Actualizando paseadores periódicamente.");
                cargarPaseadoresCercanos(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()), currentSearchRadiusKm);
            }
            periodicRefreshHandler.postDelayed(periodicRefreshRunnable, PERIODIC_REFRESH_INTERVAL_MS);
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Limpiar recursos
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        mapDebounceHandler.removeCallbacksAndMessages(null);
        searchHandler.removeCallbacksAndMessages(null);
        periodicRefreshHandler.removeCallbacksAndMessages(null);
        locationTimeoutHandler.removeCallbacks(locationTimeoutRunnable);
        cachedPaseadorMarkers.clear();
        if (mClusterManager != null) {
            mClusterManager.clearItems();
        }
        if (mMap != null) {
            mMap.clear();
        }
    }

    private void setupMapInteraction() {
        if (viewMapOverlay == null || contentScrollView == null || mapContainer == null) return;

        // 1. Al tocar el overlay (mapa en estado colapsado)
        viewMapOverlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!isMapExpanded) {
                    expandMap();
                }
                // Bloquear scroll del padre para permitir mover el mapa
                contentScrollView.requestDisallowInterceptTouchEvent(true);
                // Ocultar overlay para permitir interacción directa con el mapa
                viewMapOverlay.setVisibility(View.GONE); 
                return true; // Consumir evento
            }
            return false;
        });

        // 2. Al tocar fuera (en el ScrollView) o hacer scroll
        contentScrollView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_MOVE) {
                if (isMapExpanded) {
                    // Si se toca fuera, colapsar y restaurar overlay
                    collapseMap();
                    contentScrollView.requestDisallowInterceptTouchEvent(false);
                    viewMapOverlay.setVisibility(View.VISIBLE);
                }
            }
            return false; // No consumir, permitir scroll normal
        });
    }

    private void expandMap() {
        isMapExpanded = true;
        animateMapHeight(MAP_HEIGHT_COLLAPSED_DP, MAP_HEIGHT_EXPANDED_DP);
    }

    private void collapseMap() {
        isMapExpanded = false;
        animateMapHeight(MAP_HEIGHT_EXPANDED_DP, MAP_HEIGHT_COLLAPSED_DP);
    }

    private void animateMapHeight(int startDp, int endDp) {
        final float density = getResources().getDisplayMetrics().density;
        int startHeight = (int) (startDp * density);
        int endHeight = (int) (endDp * density);

        ValueAnimator anim = ValueAnimator.ofInt(startHeight, endHeight);
        anim.addUpdateListener(valueAnimator -> {
            int val = (Integer) valueAnimator.getAnimatedValue();
            ViewGroup.LayoutParams layoutParams = mapContainer.getLayoutParams();
            layoutParams.height = val;
            mapContainer.setLayoutParams(layoutParams);
        });
        anim.setDuration(300);
        anim.start();
    }

    private void createLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                locationTimeoutHandler.removeCallbacks(locationTimeoutRunnable); // Cancel timeout
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        lastKnownLocation = location;
                        Log.d(TAG, "Ubicación actualizada: " + location.getLatitude() + ", " + location.getLongitude());
                        if (mMap != null) {
                            // Only move camera initially or if drastic change
                            if (lastLoadedLocation == null) {
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 12));
                                cargarPaseadoresCercanos(new LatLng(location.getLatitude(), location.getLongitude()), currentSearchRadiusKm);
                            }
                        }
                    }
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        checkUserSessionAndRole();
        startLocationUpdates();
        periodicRefreshHandler.postDelayed(periodicRefreshRunnable, PERIODIC_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();
        
        // FIX: Limpiar estado al volver. Si hay texto, se limpia para reiniciar la experiencia.
        // Esto evita que se muestre un resultado "fantasma" al regresar.
        if (searchAutocomplete != null && !searchAutocomplete.getText().toString().isEmpty()) {
             searchAutocomplete.setText(""); // Esto disparar el TextWatcher que limpiar la UI
        } else {
             // Si ya está vacío, asegurar la visibilidad correcta
             recyclerViewResultados.setVisibility(View.GONE);
             contentScrollView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        if (viewModel != null) {
            viewModel.getPaseadoresPopularesState().removeObservers(this);
            viewModel.searchResults.removeObservers(this);
        }
        stopLocationUpdates();
        periodicRefreshHandler.removeCallbacks(periodicRefreshRunnable);
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
                        if (rol != null) {
                            userRole = rol;
                            BottomNavManager.saveUserRole(this, rol);
                        }
                        Log.d(TAG, "checkUserSessionAndRole: Rol encontrado: " + rol);
                        if ("DUEÑO".equals(rol)) {
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
        recyclerViewPopulares.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        popularesAdapter = new PaseadorPopularAdapter();
        recyclerViewPopulares.setAdapter(popularesAdapter);
        popularesAdapter.setOnItemClickListener(paseador -> {
            Intent intent = new Intent(BusquedaPaseadoresActivity.this, PerfilPaseadorActivity.class);
            intent.putExtra("paseadorId", paseador.getId());
            intent.putExtra("viewerRole", "DUEÑO");
            startActivity(intent);
        });

        recyclerViewResultados.setLayoutManager(new LinearLayoutManager(this));
        resultadosAdapter = new PaseadorResultadoAdapter();
        recyclerViewResultados.setAdapter(resultadosAdapter);
        resultadosAdapter.setOnItemClickListener(this);
        resultadosAdapter.setOnFavoritoToggleListener(this);
    }

    @Override
    public void onItemClick(PaseadorResultado paseador) {
        if (paseador == null) return; // Safety check

        Intent intent = new Intent(BusquedaPaseadoresActivity.this, PerfilPaseadorActivity.class);
        intent.putExtra("paseadorId", paseador.getId());
        intent.putExtra("viewerRole", "DUEÑO");
        startActivity(intent);

        PaseadorMarker marker = cachedPaseadorMarkers.get(paseador.getId());
        if (marker != null) {
            centrarMapaEnPaseador(marker.getPaseadorId(), marker.getUbicacion());
        }
    }

    @Override
    public void onFavoritoToggle(String paseadorId, boolean add) {
        viewModel.toggleFavorito(paseadorId, add);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            viewModel.onSearchQueryChanged(searchAutocomplete.getText().toString());
        }, 300);
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
                
                // Limpiar siempre la búsqueda anterior pendiente para evitar ejecuciones dobles
                searchHandler.removeCallbacks(searchRunnable);

                if (!query.isEmpty()) {
                    // Buscar desde el primer caracter con un pequeño retraso (debounce)
                    searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
                } else {
                     // Si está vacío, limpiar resultados inmediatamente
                     viewModel.onSearchQueryChanged("");
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
        searchHistoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(history));
        searchAutocomplete.setAdapter(searchHistoryAdapter);
    }

    private Set<String> getSearchHistory() {
        return getSharedPreferences(SEARCH_HISTORY_PREFS, MODE_PRIVATE)
                .getStringSet(SEARCH_HISTORY_KEY, new HashSet<>());
    }

    private void saveSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) return;

        Set<String> history = getSearchHistory();
        Set<String> newHistory = new HashSet<>(history);
        newHistory.add(query.trim());

        getSharedPreferences(SEARCH_HISTORY_PREFS, MODE_PRIVATE)
                .edit()
                .putStringSet(SEARCH_HISTORY_KEY, newHistory)
                .apply();

        if (searchHistoryAdapter != null) {
            searchHistoryAdapter.clear();
            searchHistoryAdapter.addAll(newHistory);
            searchHistoryAdapter.notifyDataSetChanged();
        }
    }

    private void setupPullToRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (searchAutocomplete != null) {
                searchAutocomplete.setText(""); // Limpiar texto visualmente
            }
            viewModel.onSearchQueryChanged(""); // Forzar b??squeda vac??a al ViewModel
            
            // Restaurar visibilidad manualmente por seguridad
            recyclerViewResultados.setVisibility(View.GONE);
            contentScrollView.setVisibility(View.VISIBLE);
            
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void showFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_filtros_busqueda, null);
        builder.setView(dialogView);

        Spinner spinnerOrdenar = dialogView.findViewById(R.id.spinner_ordenar_por);
        RangeSlider sliderDistancia = dialogView.findViewById(R.id.slider_distancia);
        RangeSlider sliderPrecio = dialogView.findViewById(R.id.slider_precio);
        RatingBar ratingBar = dialogView.findViewById(R.id.rating_bar_calificacion);
        ChipGroup chipGroup = dialogView.findViewById(R.id.chip_group_tamano_mascota);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.ordenamiento_busqueda, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        spinnerOrdenar.setAdapter(adapter);

        Filtros currentFiltros = viewModel.filtros.getValue();
        if (currentFiltros != null) {
            String[] ordenValues = getResources().getStringArray(R.array.ordenamiento_busqueda);
            int spinnerPosition = Arrays.asList(ordenValues).indexOf(currentFiltros.getOrden());
            spinnerOrdenar.setSelection(spinnerPosition);

            sliderDistancia.setValues(0f, currentFiltros.getMaxDistancia());
            sliderPrecio.setValues(0f, currentFiltros.getMinPrecio(), currentFiltros.getMaxPrecio());

            ratingBar.setRating(currentFiltros.getMinCalificacion());

            if (currentFiltros.getTamanosMascota() != null) {
                for (String tamano : currentFiltros.getTamanosMascota()) {
                    if (tamano.equals("Peque??o")) dialogView.findViewById(R.id.chip_pequeno).performClick();
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
            if (((com.google.android.material.chip.Chip)dialogView.findViewById(R.id.chip_pequeno)).isChecked()) tamanos.add("Peque??o");
            if (((com.google.android.material.chip.Chip)dialogView.findViewById(R.id.chip_mediano)).isChecked()) tamanos.add("Mediano");
            if (((com.google.android.material.chip.Chip)dialogView.findViewById(R.id.chip_grande)).isChecked()) tamanos.add("Grande");
            nuevosFiltros.setTamanosMascota(tamanos);

            viewModel.aplicarFiltros(nuevosFiltros);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.button_limpiar_filtros).setOnClickListener(v -> {
            viewModel.limpiarFiltros();
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
                    viewModel.loadMore();
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void setupObservers() {
        viewModel.getPaseadoresPopularesState().observe(this, uiState -> {
            if (uiState instanceof UiState.Success) {
                popularesAdapter.submitList(((UiState.Success<java.util.List<PaseadorResultado>>) uiState).getData());
            }
        });

        viewModel.searchResults.observe(this, uiState -> {
            if (uiState instanceof UiState.Loading) {
                showLoading();
            } else if (uiState instanceof UiState.Success) {
                showSuccessSearchResults(((UiState.Success<java.util.List<PaseadorResultado>>) uiState).getData());
              } else if (uiState instanceof UiState.Error) {
                  showError(((UiState.Error<java.util.List<PaseadorResultado>>) uiState).getMessage());
              } else if (uiState instanceof UiState.Empty) {
                  if (isQueryEmpty()) {
                      showBaseState();
                  } else {
                      showEmpty();
                  }
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

      private void showBaseState() {
          progressBar.setVisibility(View.GONE);
          contentScrollView.setVisibility(View.VISIBLE);
          recyclerViewResultados.setVisibility(View.GONE);
          emptyStateView.setVisibility(View.GONE);
          errorStateView.setVisibility(View.GONE);
      }

      private boolean isQueryEmpty() {
          return searchAutocomplete == null || searchAutocomplete.getText() == null || searchAutocomplete.getText().toString().trim().isEmpty();
      }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        locationTimeoutHandler.removeCallbacks(locationTimeoutRunnable);
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            
            // Ubicaci??n Inmediata (??ltima conocida)
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null && lastKnownLocation == null) { // Solo si no tenemos una m??s reciente
                    lastKnownLocation = location;
                    Log.d(TAG, "Ubicaci??n inmediata (LastKnown): " + location.getLatitude());
                    if (mMap != null) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(location.getLatitude(), location.getLongitude()), 12));
                        cargarPaseadoresCercanos(new LatLng(location.getLatitude(), location.getLongitude()), currentSearchRadiusKm);
                    }
                }
            });

            // Setup Timeout
            locationTimeoutRunnable = () -> {
                stopLocationUpdates();
                Log.w(TAG, "Location timeout reached. Defaulting to Quito.");
                Toast.makeText(this, "No se pudo obtener ubicaci??n precisa. Mostrando zona por defecto.", Toast.LENGTH_LONG).show();
                
                // Default to Quito
                LatLng quito = new LatLng(-0.180653, -78.467834);
                if (mMap != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(quito, 12));
                }
                cargarPaseadoresCercanos(quito, currentSearchRadiusKm);
            };
            locationTimeoutHandler.postDelayed(locationTimeoutRunnable, LOCATION_TIMEOUT_MS);

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } else {
            checkLocationPermission();
        }
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

        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setScrollGesturesEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);
        mMap.getUiSettings().setTiltGesturesEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(true);

        mClusterManager = new ClusterManager<>(BusquedaPaseadoresActivity.this, mMap);
        PaseadorClusterRenderer renderer = new PaseadorClusterRenderer(BusquedaPaseadoresActivity.this, mMap, mClusterManager);
        mClusterManager.setRenderer(renderer);
        mMap.setOnCameraIdleListener(mClusterManager);
        mMap.setOnMarkerClickListener(mClusterManager);

        // Two-stage selection logic
        mClusterManager.setOnClusterItemClickListener(item -> {
            String clickedId = item.getPaseadorMarker().getPaseadorId();
            
            if (clickedId.equals(selectedPaseadorId)) {
                // Second click: Navigate to Profile
                Intent intent = new Intent(BusquedaPaseadoresActivity.this, PerfilPaseadorActivity.class);
                intent.putExtra("paseadorId", clickedId);
                intent.putExtra("viewerRole", "DUE??O");
                startActivity(intent);
                return true;
            } else {
                // First click: Select and Center
                selectedPaseadorId = clickedId;
                
                // Animate Camera
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(item.getPosition(), 15f));
                
                // Re-render to update icons (Normal vs Selected)
                // We iterate to find the marker and update it specifically if possible, 
                // but simplest robust way is to re-render or update specific markers.
                // Since createCompositeBitmap checks 'selectedPaseadorId', we just need to refresh.
                Marker marker = renderer.getMarker(item);
                if (marker != null) {
                    marker.showInfoWindow();
                    // Force icon update
                    renderer.onBeforeClusterItemRendered(item, new MarkerOptions()); // Trigger icon update logic manually or rely on cluster re-render
                    // Actually, we need to manually update the icon on the existing marker object
                    renderer.updateMarkerIcon(marker, item);
                }
                
                // Deselect others (reset their icons)
                for (PaseadorClusterItem otherItem : mClusterManager.getAlgorithm().getItems()) {
                    if (!otherItem.getPaseadorMarker().getPaseadorId().equals(clickedId)) {
                        Marker otherMarker = renderer.getMarker(otherItem);
                        if (otherMarker != null) {
                            renderer.updateMarkerIcon(otherMarker, otherItem);
                        }
                    }
                }
                
                return true; // Consume event (don't do default behavior)
            }
        });

        // Deselect on map click
        mMap.setOnMapClickListener(latLng -> {
            if (selectedPaseadorId != null) {
                selectedPaseadorId = null;
                // Reset all icons
                for (PaseadorClusterItem item : mClusterManager.getAlgorithm().getItems()) {
                    Marker marker = renderer.getMarker(item);
                    if (marker != null) {
                        renderer.updateMarkerIcon(marker, item);
                    }
                }
            }
        });

        // Configurar el InfoWindowAdapter personalizado (Keep existing logic for InfoWindow click as backup)
        PaseadorInfoWindowAdapter infoWindowAdapter = new PaseadorInfoWindowAdapter(BusquedaPaseadoresActivity.this, paseadorId -> {
            Intent intent = new Intent(BusquedaPaseadoresActivity.this, PerfilPaseadorActivity.class);
            intent.putExtra("paseadorId", paseadorId);
            intent.putExtra("viewerRole", "DUE??O");
            startActivity(intent);
        });
        mMap.setInfoWindowAdapter(infoWindowAdapter);
        mMap.setOnInfoWindowClickListener(marker -> {
            // Delegar el click al listener del adaptador
            if (marker.getTag() instanceof String) {
                infoWindowAdapter.getListener().onVerPerfilClick((String) marker.getTag());
            }
        });

        checkLocationPermission();

        LatLng quito = new LatLng(-0.180653, -78.467834);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(quito, 12));

        if (lastKnownLocation != null) {
            cargarPaseadoresCercanos(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()), currentSearchRadiusKm);
        }

        mMap.setOnCameraIdleListener(() -> {
            mClusterManager.onCameraIdle();
            
            LatLng center = mMap.getCameraPosition().target;
            float zoom = mMap.getCameraPosition().zoom;

            // Optimization: Reload only if moved > 500m or zoom changed significantly (> 1 level)
            boolean shouldReload = false;
            if (lastLoadedLocation == null) {
                shouldReload = true;
            } else {
                float[] results = new float[1];
                Location.distanceBetween(lastLoadedLocation.getLatitude(), lastLoadedLocation.getLongitude(),
                        center.latitude, center.longitude, results);
                if (results[0] > 500 || Math.abs(zoom - lastLoadedZoom) > 1.0) {
                    shouldReload = true;
                }
            }

            if (shouldReload) {
                // Update optimization variables
                if (lastLoadedLocation == null) {
                    lastLoadedLocation = new Location("map_center");
                }
                lastLoadedLocation.setLatitude(center.latitude);
                lastLoadedLocation.setLongitude(center.longitude);
                lastLoadedZoom = zoom;

                mapDebounceHandler.removeCallbacks(mapDebounceRunnable);
                mapDebounceRunnable = () -> {
                    cargarPaseadoresCercanos(center, currentSearchRadiusKm);
                };
                mapDebounceHandler.postDelayed(mapDebounceRunnable, MAP_DEBOUNCE_DELAY_MS);
            }
        });
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                    startLocationUpdates();
                }
            } else {
                Toast.makeText(this, "El permiso de ubicaci??n es necesario para ver paseadores cercanos", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupBottomNavigation() {
        if (bottomNav == null) {
            return;
        }
        String roleForNav = userRole != null ? userRole : "DUE??O";
        BottomNavManager.setupBottomNav(this, bottomNav, roleForNav, R.id.menu_search);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (searchAutocomplete != null) {
            outState.putString(SEARCH_QUERY_KEY, searchAutocomplete.getText().toString());
        }
    }

    private void cargarPaseadoresCercanos(LatLng ubicacionUsuario, double radioKm) {
        if (ubicacionUsuario == null) {
            Log.w(TAG, "cargarPaseadoresCercanos: ubicacionUsuario es null, se omite b??squeda geoespacial.");
            return;
        }
        if (mMap == null || mClusterManager == null) {
            Log.w(TAG, "cargarPaseadoresCercanos: mapa no inicializado.");
            return;
        }

        if (Double.isNaN(ubicacionUsuario.latitude) || Double.isNaN(ubicacionUsuario.longitude)
                || (Math.abs(ubicacionUsuario.latitude) < 0.0001 && Math.abs(ubicacionUsuario.longitude) < 0.0001)) {
            Log.w(TAG, "cargarPaseadoresCercanos: ubicacionUsuario inv??lida, se omite consulta.");
            return;
        }

        if (progressBar != null && cachedPaseadorMarkers.isEmpty()) {
            progressBar.setVisibility(View.VISIBLE);
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        GeoLocation center = new GeoLocation(ubicacionUsuario.latitude, ubicacionUsuario.longitude);
        double radiusMeters = radioKm * 1000d;
        List<GeoQueryBounds> bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusMeters);
        List<Task<QuerySnapshot>> geoTasks = new ArrayList<>();

        for (GeoQueryBounds b : bounds) {
            Query q = firestore.collection("usuarios")
                    .whereEqualTo("rol", "PASEADOR")
                    .whereEqualTo("activo", true)
                    .orderBy("ubicacion_geohash")
                    .startAt(b.startHash)
                    .endAt(b.endHash)
                    .limit(50);
            geoTasks.add(q.get());
        }

        Tasks.whenAllComplete(geoTasks).addOnCompleteListener(all -> {
            Set<String> seenPaseadores = new HashSet<>();
            List<Task<PaseadorMarker>> paseadorMarkerTasks = new ArrayList<>();

            for (Task<QuerySnapshot> task : geoTasks) {
                if (!task.isSuccessful() || task.getResult() == null) {
                    Log.w(TAG, "Geoquery parcial fallida o vac??a", task.getException());
                    continue;
                }

                for (DocumentSnapshot doc : task.getResult()) {
                    if (!seenPaseadores.add(doc.getId())) continue; // evitar duplicados
                    if (!isUsuarioEnLinea(doc)) continue;

                    LatLng ubicacionActual = extraerLatLng(doc.get("ubicacion_actual"));
                    String geohash = doc.getString("ubicacion_geohash");

                    if (ubicacionActual == null || geohash == null) {
                        Log.w(TAG, "Documento sin ubicacion_actual/geohash: " + doc.getId());
                        continue;
                    }

                    double distanceMeters = GeoFireUtils.getDistanceBetween(
                            new GeoLocation(ubicacionActual.latitude, ubicacionActual.longitude), center);

                    if (distanceMeters <= radiusMeters) {
                        paseadorMarkerTasks.add(buildPaseadorMarkerFromUser(doc, ubicacionUsuario));
                    }
                }
            }

            if (paseadorMarkerTasks.isEmpty()) {
                cargarPaseadoresCercanosFallback(ubicacionUsuario);
                return;
            }

            Tasks.whenAllSuccess(paseadorMarkerTasks)
                    .addOnSuccessListener(list -> {
                        int added = 0;
                        for (Object obj : list) {
                            if (obj instanceof PaseadorMarker) {
                                PaseadorMarker paseadorMarker = (PaseadorMarker) obj;
                                if (paseadorMarker != null) {
                                    cachedPaseadorMarkers.put(paseadorMarker.getPaseadorId(), paseadorMarker);
                                    mClusterManager.addItem(new PaseadorClusterItem(paseadorMarker));
                                    added++;
                                }
                            }
                        }
                        if (added > 0) {
                            mClusterManager.clearItems();
                            List<PaseadorClusterItem> items = new ArrayList<>(cachedPaseadorMarkers.size());
                            for (PaseadorMarker pm : cachedPaseadorMarkers.values()) {
                                items.add(new PaseadorClusterItem(pm));
                            }
                            mClusterManager.addItems(items);
                            mClusterManager.cluster();
                        } else {
                            Log.w(TAG, "Geoquery sin marcadores v??lidos; se conservan los existentes.");
                        }
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error al construir PaseadorMarkers", e);
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                    });
        });
    }

    private void cargarPaseadoresCercanosFallback(LatLng ubicacionUsuario) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection("usuarios")
                .whereEqualTo("rol", "PASEADOR")
                .whereEqualTo("activo", true)
                .limit(50)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Log.w(TAG, "Fallback: error al obtener usuarios", task.getException());
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        return;
                    }

                    List<Task<PaseadorMarker>> paseadorMarkerTasks = new ArrayList<>();
                    for (DocumentSnapshot doc : task.getResult()) {
                        // Relaxed check: Show all active walkers in fallback too
                        paseadorMarkerTasks.add(buildPaseadorMarkerFromUser(doc, ubicacionUsuario));
                    }

                    if (paseadorMarkerTasks.isEmpty()) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        Log.w(TAG, "Fallback sin marcadores; se conserva estado previo.");
                        return;
                    }

                    Tasks.whenAllSuccess(paseadorMarkerTasks)
                            .addOnSuccessListener(list -> {
                                int added = 0;
                                for (Object obj : list) {
                                    if (obj instanceof PaseadorMarker) {
                                        PaseadorMarker paseadorMarker = (PaseadorMarker) obj;
                                        if (paseadorMarker != null) {
                                            cachedPaseadorMarkers.put(paseadorMarker.getPaseadorId(), paseadorMarker);
                                            mClusterManager.addItem(new PaseadorClusterItem(paseadorMarker));
                                            added++;
                                        }
                                    }
                                }
                                if (added > 0) {
                                    mClusterManager.clearItems();
                                    List<PaseadorClusterItem> items = new ArrayList<>(cachedPaseadorMarkers.size());
                                    for (PaseadorMarker pm : cachedPaseadorMarkers.values()) {
                                        items.add(new PaseadorClusterItem(pm));
                                    }
                                    mClusterManager.addItems(items);
                                    mClusterManager.cluster();
                                } else {
                                    Log.w(TAG, "Fallback sin nuevos marcadores; se conserva estado previo.");
                                }
                                if (progressBar != null) progressBar.setVisibility(View.GONE);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Fallback: error construyendo PaseadorMarkers", e);
                                if (progressBar != null) progressBar.setVisibility(View.GONE);
                            });
                });
    }

    private boolean isUsuarioEnLinea(DocumentSnapshot doc) {
        // RELAXED LOGIC: Return true if the user is marked as 'activo' generally.
        // We don't want to hide walkers just because they aren't "online" right this second.
        // The map should show all AVAILABLE walkers (who have set themselves as active/working).
        Boolean activo = doc.getBoolean("activo");
        if (Boolean.TRUE.equals(activo)) {
            return true;
        }

        // Fallback to legacy logic if 'activo' is missing or false but they are online
        try {
            Boolean enLinea = doc.getBoolean("en_linea");
            Timestamp lastSeen = doc.getTimestamp("last_seen");
            long now = System.currentTimeMillis();
            boolean reciente = lastSeen != null && (now - lastSeen.toDate().getTime()) <= ONLINE_TIMEOUT_MS;

            if (reciente) return true;
            return Boolean.TRUE.equals(enLinea);
        } catch (Exception e) {
            Log.w(TAG, "Error evaluando estado en l??nea", e);
            return false;
        }
    }

    // M??todo renombrado y adaptado
    private Task<PaseadorMarker> buildPaseadorMarkerFromUser(DocumentSnapshot userDoc, LatLng ubicacionUsuario) {
        String userId = userDoc.getId();
        
        // 1. Intentar obtener ubicaci??n en tiempo real del perfil
        LatLng ubicacionRealtime = extraerLatLng(userDoc.get("ubicacion_actual"));

        if (ubicacionRealtime != null) {
            return crearMarcador(userId, userDoc, ubicacionRealtime, ubicacionUsuario, true);
        } 
        
        // 2. Fallback: Buscar zona de servicio principal
        return FirebaseFirestore.getInstance().collection("paseadores").document(userId)
                .collection("zonas_servicio")
                .limit(1) 
                .get()
                .continueWithTask(task -> {
                    LatLng ubicacionFinal = null;
                    
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot zonaDoc = task.getResult().getDocuments().get(0);
                        com.google.firebase.firestore.GeoPoint zonaGp = zonaDoc.getGeoPoint("ubicacion_centro");
                        if (zonaGp == null) zonaGp = zonaDoc.getGeoPoint("centro"); // compatibilidad
                        
                        if (zonaGp != null) {
                            ubicacionFinal = new LatLng(zonaGp.getLatitude(), zonaGp.getLongitude());
                        } else {
                            Double lat = zonaDoc.getDouble("latitud");
                            Double lng = zonaDoc.getDouble("longitud");
                            if (lat != null && lng != null) {
                                ubicacionFinal = new LatLng(lat, lng);
                            }
                        }
                    }
                    
                    if (ubicacionFinal == null) {
                        // 3. Last Resort: Check top-level fields on 'usuarios' or 'paseadores' doc if we had access
                        // For now, just log.
                        Log.w(TAG, "Paseador " + userId + " dropped: No realtime location AND no service zone found.");
                        return Tasks.forResult(null);
                    }
                    
                    return crearMarcador(userId, userDoc, ubicacionFinal, ubicacionUsuario, false);
                });
    }

    private Task<PaseadorMarker> crearMarcador(String userId, DocumentSnapshot userDoc, LatLng ubicacionPaseador, LatLng ubicacionUsuario, boolean esRealtime) {
        float[] results = new float[1];
        Location.distanceBetween(ubicacionUsuario.latitude, ubicacionUsuario.longitude,
                ubicacionPaseador.latitude, ubicacionPaseador.longitude, results);
        double distanciaKm = results[0] / 1000;

        // Filtro de radio
        if (distanciaKm <= currentSearchRadiusKm) {
            String nombre = userDoc.getString("nombre_display");
            String fotoUrl = userDoc.getString("foto_perfil");

            return FirebaseFirestore.getInstance().collection("paseadores").document(userId).get().continueWithTask(task1 -> {
                if (task1.isSuccessful()) {
                    DocumentSnapshot paseadorDoc = task1.getResult();
                    if (paseadorDoc.exists()) {
                        double calificacion = paseadorDoc.getDouble("calificacion_promedio") != null ? paseadorDoc.getDouble("calificacion_promedio") : 0.0;
                        
                        // Chain 1: Check Availability (Schedule)
                        return verificarDisponibilidadActual(userId).continueWithTask(task2 -> {
                            boolean disponible = !task2.isSuccessful() || task2.getResult(); // Si falla o no hay datos, considerar disponible
                            
                            // Chain 2: Busy status (omit reservation query to evitar PERMISSION_DENIED; default false)
                            boolean enPaseo = userDoc.getBoolean("en_paseo") != null && userDoc.getBoolean("en_paseo");
                            Log.d(TAG, "Paseador " + userId + " - Disponible (schedule): " + disponible + ", En Paseo: " + enPaseo); // DEBUG LOG
                            return Tasks.forResult(new PaseadorMarker(userId, nombre, ubicacionPaseador, calificacion, fotoUrl, disponible, distanciaKm, enPaseo));
                        });
                    }
                }
                return Tasks.forResult(null);
            });
        }
        return Tasks.forResult(null);
    }

    private LatLng extraerLatLng(Object ubicacionObj) {
        if (ubicacionObj instanceof com.google.firebase.firestore.GeoPoint) {
            com.google.firebase.firestore.GeoPoint gp = (com.google.firebase.firestore.GeoPoint) ubicacionObj;
            return new LatLng(gp.getLatitude(), gp.getLongitude());
        }
        if (ubicacionObj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) ubicacionObj;
            Object latObj = map.get("lat");
            Object lngObj = map.get("lng");
            if (lngObj == null) lngObj = map.get("lon");
            if (latObj instanceof Number && lngObj instanceof Number) {
                return new LatLng(((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue());
            }
        }
        return null;
    }
    /**
     * Verifica si el paseador est?? disponible en el d??a y hora actual
     * Compatible con estructura Firebase:
     * - dia_semana: "Lunes", "Martes", etc.
     * - hora_inicio: "08:00"
     * - hora_fin: "17:00"
     * - activo: true/false
     */
    private Task<Boolean> verificarDisponibilidadActual(String paseadorId) {
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK); // Sunday=1, Monday=2, ..., Saturday=7
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(Calendar.MINUTE);

        String diaActual;
        switch (dayOfWeek) {
            case Calendar.MONDAY:
                diaActual = "Lunes";
                break;
            case Calendar.TUESDAY:
                diaActual = "Martes";
                break;
            case Calendar.WEDNESDAY:
                diaActual = "Mi??rcoles";
                break;
            case Calendar.THURSDAY:
                diaActual = "Jueves";
                break;
            case Calendar.FRIDAY:
                diaActual = "Viernes";
                break;
            case Calendar.SATURDAY:
                diaActual = "S??bado";
                break;
            case Calendar.SUNDAY:
                diaActual = "Domingo";
                break;
            default:
                Log.e(TAG, "D??a de la semana inv??lido: " + dayOfWeek);
                return Tasks.forResult(false);
        }

        Log.d(TAG, String.format("Verificando disponibilidad para %s a las %02d:%02d",
                diaActual, currentHour, currentMinute));

        // Nuevo esquema: documentos con campos {activo: bool, dias: [array], hora_inicio, hora_fin}
        return FirebaseFirestore.getInstance()
                .collection("paseadores").document(paseadorId)
                .collection("disponibilidad")
                .whereEqualTo("activo", true)
                .whereArrayContains("dias", diaActual)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Error consultando disponibilidad", task.getException());
                        return true; // No bloquear si hay error
                    }

                    QuerySnapshot querySnapshot = task.getResult();
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        Log.d(TAG, "No hay disponibilidad configurada para " + diaActual + ". Se considera disponible.");
                        return true; // No ocultar por falta de configuraci??n
                    }

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String horaInicio = doc.getString("hora_inicio"); // "08:00"
                        String horaFin = doc.getString("hora_fin");       // "17:00"

                        if (horaInicio == null || horaFin == null) {
                            Log.w(TAG, "Documento sin hora_inicio o hora_fin: " + doc.getId());
                            continue;
                        }

                        try {
                            String[] inicioSplit = horaInicio.split(":");
                            String[] finSplit = horaFin.split(":");

                            int horaInicioParsed = Integer.parseInt(inicioSplit[0]);
                            int minutoInicioParsed = inicioSplit.length > 1 ? Integer.parseInt(inicioSplit[1]) : 0;

                            int horaFinParsed = Integer.parseInt(finSplit[0]);
                            int minutoFinParsed = finSplit.length > 1 ? Integer.parseInt(finSplit[1]) : 0;

                            int minutosActuales = currentHour * 60 + currentMinute;
                            int minutosInicio = horaInicioParsed * 60 + minutoInicioParsed;
                            int minutosFin = horaFinParsed * 60 + minutoFinParsed;

                            boolean disponible = minutosActuales >= minutosInicio && minutosActuales < minutosFin;

                            if (disponible) {
                                Log.d(TAG, String.format("Paseador disponible: %s-%s (actual: %02d:%02d)",
                                        horaInicio, horaFin, currentHour, currentMinute));
                                return true; // Est?? disponible en esta franja
                            }

                        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                            Log.e(TAG, "Error parseando horarios: " + horaInicio + " - " + horaFin, e);
                            continue;
                        }
                    }

                    Log.d(TAG, "Paseador no disponible en este horario");
                    return false; // No est?? disponible en las franjas configuradas
                });
    }

    private static final int MARKER_IMAGE_SIZE = 120;

    private BitmapDescriptor crearMarkerIconPersonalizado(PaseadorMarker paseador) {
        try {
            Context glideCtx = glideContext != null ? glideContext : getApplicationContext();
            Bitmap bitmap = Glide.with(glideCtx)
                    .asBitmap()
                    .load(paseador.getFotoUrl())
                    .apply(RequestOptions.circleCropTransform())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .submit(MARKER_IMAGE_SIZE, MARKER_IMAGE_SIZE)
                    .get(5, TimeUnit.SECONDS);

            Bitmap finalBitmap = Bitmap.createBitmap(MARKER_IMAGE_SIZE, MARKER_IMAGE_SIZE + 30, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(finalBitmap);

            canvas.drawBitmap(bitmap, 0, 0, null);

            String ratingText = String.format(Locale.getDefault(), "%.1f", paseador.getCalificacion());
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.parseColor("#8BC34A"));
            paint.setTextSize(30);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);

            Rect textBounds = new Rect();
            paint.getTextBounds(ratingText, 0, ratingText.length(), textBounds);
            float x = finalBitmap.getWidth() / 2f;
            float y = finalBitmap.getHeight() - 10;
            canvas.drawText(ratingText, x, y - textBounds.exactCenterY(), paint);

            return BitmapDescriptorFactory.fromBitmap(finalBitmap);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.e(TAG, "Error al cargar imagen para marcador: " + e.getMessage());
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
        }
    }

    private void centrarMapaEnPaseador(String paseadorId, LatLng ubicacion) {
        if (mMap != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ubicacion, 15f));

            for (PaseadorClusterItem item : mClusterManager.getAlgorithm().getItems()) {
                if (item.getPaseadorMarker().getPaseadorId().equals(paseadorId)) {
                    Marker marker = ((PaseadorClusterRenderer)mClusterManager.getRenderer()).getMarker(item);
                    if (marker != null) {
                        marker.showInfoWindow();
                        break;
                    }
                }
            }
        }
    }

    private static class PaseadorClusterItem implements com.google.maps.android.clustering.ClusterItem {
        private final PaseadorMarker mPaseadorMarker;

        public PaseadorClusterItem(PaseadorMarker paseadorMarker) {
            mPaseadorMarker = paseadorMarker;
        }

        @NonNull
        @Override
        public LatLng getPosition() {
            return mPaseadorMarker.getUbicacion();
        }

        @Nullable
        @Override
        public String getTitle() {
            return mPaseadorMarker.getNombre();
        }

        @Nullable
        @Override
        public String getSnippet() {
            String estado = mPaseadorMarker.isEnPaseo() ? "En un paseo" : (mPaseadorMarker.isDisponible() ? "Disponible" : "No disponible");
            return String.format(Locale.getDefault(), "%.1f km - %.1f \n%s", mPaseadorMarker.getDistanciaKm(), mPaseadorMarker.getCalificacion(), estado);
        }

        @Nullable
        @Override
        public Float getZIndex() {
            return null;
        }

        public PaseadorMarker getPaseadorMarker() {
            return mPaseadorMarker;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PaseadorClusterItem that = (PaseadorClusterItem) o;
            return mPaseadorMarker.getPaseadorId().equals(that.mPaseadorMarker.getPaseadorId());
        }

        @Override
        public int hashCode() {
            return mPaseadorMarker.getPaseadorId().hashCode();
        }
    }

    private class PaseadorClusterRenderer extends DefaultClusterRenderer<PaseadorClusterItem> {
        private final IconGenerator mIconGenerator;
        private final ImageView mImageView;
        private final int mDensity;
        private final int mMarkerPadding;

        public PaseadorClusterRenderer(Context context, GoogleMap map, ClusterManager<PaseadorClusterItem> clusterManager) {
            super(context, map, clusterManager);
            mIconGenerator = new IconGenerator(context);
            mImageView = new ImageView(context);
            mIconGenerator.setContentView(mImageView);

            mDensity = (int) context.getResources().getDisplayMetrics().density;
            mMarkerPadding = (int) (5 * mDensity);
            mImageView.setPadding(mMarkerPadding, mMarkerPadding, mMarkerPadding, mMarkerPadding);

            int markerImageSize = (int) (MAP_MARKER_IMAGE_SIZE_DP * mDensity);
            mImageView.setLayoutParams(new ViewGroup.LayoutParams(markerImageSize, markerImageSize));

            mIconGenerator.setTextAppearance(R.style.ClusterIcon_TextAppearance);
        }

        private static final int MAP_MARKER_IMAGE_SIZE_DP = 50;

        public void updateMarkerIcon(Marker marker, PaseadorClusterItem item) {
            PaseadorMarker paseador = item.getPaseadorMarker();
            boolean isSelected = paseador.getPaseadorId().equals(selectedPaseadorId);
            Context glideCtx = glideContext != null ? glideContext : getApplicationContext();
            
            Glide.with(glideCtx)
                .asBitmap()
                .load(paseador.getFotoUrl())
                .apply(RequestOptions.circleCropTransform()
                        .placeholder(R.drawable.ic_person)
                        .diskCacheStrategy(DiskCacheStrategy.ALL))
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        if (marker != null && marker.getTag() != null) { // Check validity
                             Bitmap finalBitmap = createCompositeBitmap(resource, paseador.getCalificacion(), paseador.isDisponible(), paseador.isEnPaseo(), isSelected);
                             try {
                                marker.setIcon(BitmapDescriptorFactory.fromBitmap(finalBitmap));
                             } catch (IllegalArgumentException e) {
                                Log.e(TAG, "Error updating marker icon", e);
                             }
                        }
                    }
                    @Override public void onLoadCleared(@Nullable Drawable placeholder) {}
                    @Override public void onLoadFailed(@Nullable Drawable errorDrawable) {}
                });
        }

        @Override
        protected void onBeforeClusterItemRendered(@NonNull PaseadorClusterItem item, @NonNull MarkerOptions markerOptions) {
            final PaseadorMarker paseador = item.getPaseadorMarker();
            boolean isSelected = paseador.getPaseadorId().equals(selectedPaseadorId);
            Context glideCtx = glideContext != null ? glideContext : getApplicationContext();

            Glide.with(glideCtx)
                    .asBitmap()
                    .load(paseador.getFotoUrl())
                    .apply(RequestOptions.circleCropTransform()
                            .placeholder(R.drawable.ic_person)
                            .diskCacheStrategy(DiskCacheStrategy.ALL))
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            Bitmap finalBitmap = createCompositeBitmap(resource, paseador.getCalificacion(), paseador.isDisponible(), paseador.isEnPaseo(), isSelected);
                            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(finalBitmap));
                            Marker existingMarker = getMarker(item);
                            if (existingMarker != null) {
                                existingMarker.setIcon(BitmapDescriptorFactory.fromBitmap(finalBitmap));
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            Bitmap defaultBitmap = createCompositeBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_person), paseador.getCalificacion(), paseador.isDisponible(), paseador.isEnPaseo(), isSelected);
                            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(defaultBitmap));
                            Marker existingMarker = getMarker(item);
                            if (existingMarker != null) {
                                existingMarker.setIcon(BitmapDescriptorFactory.fromBitmap(defaultBitmap));
                            }
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            Log.e(TAG, "Error al cargar imagen de paseador: " + paseador.getFotoUrl());
                            Bitmap defaultBitmap = createCompositeBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_person), paseador.getCalificacion(), paseador.isDisponible(), paseador.isEnPaseo(), isSelected);
                            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(defaultBitmap));
                            Marker existingMarker = getMarker(item);
                            if (existingMarker != null) {
                                existingMarker.setIcon(BitmapDescriptorFactory.fromBitmap(defaultBitmap));
                            }
                        }
                    });

            markerOptions.title(item.getTitle());
            markerOptions.snippet(item.getSnippet());
            cachedPaseadorMarkers.put(paseador.getPaseadorId(), paseador);
        }

        @Override
        protected void onClusterItemRendered(@NonNull PaseadorClusterItem clusterItem, @NonNull Marker marker) {
            super.onClusterItemRendered(clusterItem, marker);
            marker.setTag(clusterItem.getPaseadorMarker().getPaseadorId()); // CR??TICO: Establecer el tag del marcador
        }

        private Bitmap createCompositeBitmap(Bitmap profileBitmap, double calificacion, boolean disponible, boolean enPaseo, boolean isSelected) {
            // Prevent NullPointerException if bitmap loading failed completely
            if (profileBitmap == null) {
                profileBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                profileBitmap.eraseColor(Color.GRAY);
            }

            // Dise??o Moderno: Pin Flotante (Estilo Airbnb/Uber)
            // Base size: 60dp Normal, 80dp Selected
            int baseSizeDp = isSelected ? 80 : 60;
            int imageSize = (int) (baseSizeDp * mDensity); 
            int borderSize = (int) ((isSelected ? 4 : 3) * mDensity); // Thicker border if selected
            int shadowOffset = (int) ((isSelected ? 4 : 2) * mDensity); // Deeper shadow if selected
            int totalSize = imageSize + (borderSize * 2);
            int canvasSize = totalSize + shadowOffset + 10; 

            Bitmap finalBitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(finalBitmap);

            float centerX = canvasSize / 2f;
            float centerY = (canvasSize - shadowOffset) / 2f;
            float radius = imageSize / 2f;

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            // 1. Sombra
            paint.setColor(Color.parseColor("#40000000")); 
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(centerX, centerY + shadowOffset, radius + borderSize, paint);

            // 2. Borde (Base del Pin)
            // Blue if selected, White if normal
            paint.setColor(isSelected ? ContextCompat.getColor(BusquedaPaseadoresActivity.this, R.color.blue_primary) : Color.WHITE);
            canvas.drawCircle(centerX, centerY, radius + borderSize, paint);
            
            // Inner white border for selected state to separate blue from image
            if (isSelected) {
                 paint.setColor(Color.WHITE);
                 canvas.drawCircle(centerX, centerY, radius + (borderSize / 2f), paint);
            }

            // 3. Foto de Perfil
            if (profileBitmap.getWidth() != imageSize || profileBitmap.getHeight() != imageSize) {
                profileBitmap = Bitmap.createScaledBitmap(profileBitmap, imageSize, imageSize, true);
            }
            canvas.drawBitmap(profileBitmap, centerX - radius, centerY - radius, null);

            // 4. Badge de Estado
            float badgeRadius = (int) ((isSelected ? 9 : 7) * mDensity); 
            float badgeX = centerX + (radius * 0.707f); 
            float badgeY = centerY + (radius * 0.707f);

            paint.setColor(Color.WHITE);
            canvas.drawCircle(badgeX, badgeY, badgeRadius + (2 * mDensity), paint);

            int colorEstado;
            if (enPaseo) {
                colorEstado = Color.parseColor("#FF9800"); 
            } else if (disponible) {
                colorEstado = Color.parseColor("#4CAF50"); 
            } else {
                colorEstado = Color.GRAY; 
            }
            paint.setColor(colorEstado);
            canvas.drawCircle(badgeX, badgeY, badgeRadius, paint);

            return finalBitmap;
        }

        @Override
        protected boolean shouldRenderAsCluster(@NonNull com.google.maps.android.clustering.Cluster<PaseadorClusterItem> cluster) {
            return cluster.getSize() > 3;
        }
    }
}


