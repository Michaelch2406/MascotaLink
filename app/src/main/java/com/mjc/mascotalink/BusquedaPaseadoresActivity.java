package com.mjc.mascotalink;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BusquedaPaseadoresActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "BusquedaPaseadores";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Views
    private SearchView searchView;
    private RecyclerView recyclerResultados, recyclerPopulares;
    private ProgressBar pbLoading;
    private TextView tvNoResultados, tvResultadosTitulo;
    private ImageView ivSettings;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private GoogleMap googleMap;

    // Chips de filtros
    private Chip chipCerca, chipDisponible, chipMejorCalificados, chipEconomico;

    // Adapters
    private PaseadorResultadoAdapter adaptadorResultados;
    private PaseadorPopularAdapter adaptadorPopulares;

    // Data
    private List<PaseadorResultado> listaPaseadores;
    private List<PaseadorResultado> listaPaseadoresPopulares;
    private Map<String, PaseadorResultado> paseadoresMap;

    // Paginación
    private DocumentSnapshot lastVisible;
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private static final int PAGE_SIZE = 10;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private com.google.android.gms.location.FusedLocationProviderClient fusedLocationProviderClient;
    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted) {
            getDeviceLocation();
        } else {
            // Opcional: Informar al usuario que la funcionalidad de ubicación no estará disponible
            Toast.makeText(this, "El permiso de ubicación fue denegado.", Toast.LENGTH_SHORT).show();
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_busqueda_paseadores);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        fusedLocationProviderClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);

        initViews();
        setupToolbar();
        setupRecyclerViews();
        setupSearchView();
        setupChipFilters();
        setupBottomNavigation();
        setupMap();

        cargarPaseadoresPopulares();
        cargarPaseadores(null);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        searchView = findViewById(R.id.search_view);
        recyclerResultados = findViewById(R.id.recycler_resultados);
        recyclerPopulares = findViewById(R.id.recycler_populares);
        pbLoading = findViewById(R.id.pb_loading);
        tvNoResultados = findViewById(R.id.tv_no_resultados);
        tvResultadosTitulo = findViewById(R.id.tv_resultados_titulo);
        ivSettings = findViewById(R.id.iv_settings);
        bottomNav = findViewById(R.id.bottom_nav);

        chipCerca = findViewById(R.id.chip_cerca);
        chipDisponible = findViewById(R.id.chip_disponible);
        chipMejorCalificados = findViewById(R.id.chip_mejor_calificados);
        chipEconomico = findViewById(R.id.chip_economico);

        listaPaseadores = new ArrayList<>();
        listaPaseadoresPopulares = new ArrayList<>();
        paseadoresMap = new HashMap<>();
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
        ivSettings.setOnClickListener(v -> {
            Toast.makeText(this, "Configuración", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupRecyclerViews() {
        // RecyclerView de resultados (vertical)
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerResultados.setLayoutManager(layoutManager);
        adaptadorResultados = new PaseadorResultadoAdapter(this, listaPaseadores);
        recyclerResultados.setAdapter(adaptadorResultados);

        // Paginación
        recyclerResultados.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!recyclerView.canScrollVertically(1) && !isLoading && hasMoreData) {
                    cargarMasPaseadores();
                }
            }
        });

        // RecyclerView de populares (horizontal)
        LinearLayoutManager layoutManagerPopulares = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerPopulares.setLayoutManager(layoutManagerPopulares);
        adaptadorPopulares = new PaseadorPopularAdapter(this, listaPaseadoresPopulares);
        recyclerPopulares.setAdapter(adaptadorPopulares);
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                buscarPaseadores(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (TextUtils.isEmpty(newText)) {
                    cargarPaseadores(null);
                }
                return true;
            }
        });
    }

    private void setupChipFilters() {
        chipCerca.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) aplicarFiltros();
        });

        chipDisponible.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) filtrarPorDisponibilidad();
        });

        chipMejorCalificados.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) filtrarPorCalificacion();
        });

        chipEconomico.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) filtrarPorPrecio();
        });
    }

    private void setupBottomNavigation() {
        bottomNav.setSelectedItemId(R.id.menu_search);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_home) {
                // Navegar a inicio
                Toast.makeText(this, "Inicio", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.menu_search) {
                // Ya estamos en búsqueda
                Toast.makeText(this, "Buscar", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.menu_walks) {
                // Ya estamos aquí
                return true;
            } else if (itemId == R.id.menu_messages) {
                Toast.makeText(this, "Mensajes", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.menu_perfil) {
                finish();
                return true;
            }
            return false;
        });
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setScrollGesturesEnabled(true);

        checkLocationPermissionAndGetLocation();
        cargarMarcadoresMapa();
    }

    private void checkLocationPermissionAndGetLocation() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            getDeviceLocation();
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void getDeviceLocation() {
        try {
            Task<android.location.Location> locationResult = fusedLocationProviderClient.getLastLocation();
            locationResult.addOnCompleteListener(this, task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    android.location.Location lastKnownLocation = task.getResult();
                    LatLng currentLocation = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 12));
                } else {
                    Log.d(TAG, "Current location is null. Using defaults.");
                    Log.e(TAG, "Exception: %s", task.getException());
                    LatLng quito = new LatLng(-0.1807, -78.4678);
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(quito, 12));
                }
            });
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    private void cargarPaseadoresPopulares() {
        db.collection("usuarios")
                .whereEqualTo("rol", "PASEADOR")
                .whereEqualTo("activo", true)
                .orderBy("nombre_display")
                .limit(10)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> paseadorIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        paseadorIds.add(doc.getId());
                    }
                    cargarDetallesPaseadores(paseadorIds, true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando paseadores populares", e);
                });
    }

    private void cargarPaseadores(DocumentSnapshot startAfter) {
        if (isLoading) return;
        
        isLoading = true;
        pbLoading.setVisibility(View.VISIBLE);

        Query query = db.collection("usuarios")
                .whereEqualTo("rol", "PASEADOR")
                .whereEqualTo("activo", true)
                .orderBy("nombre_display")
                .limit(PAGE_SIZE);

        if (startAfter != null) {
            query = query.startAfter(startAfter);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        lastVisible = queryDocumentSnapshots.getDocuments()
                                .get(queryDocumentSnapshots.size() - 1);

                        List<String> paseadorIds = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            paseadorIds.add(doc.getId());
                        }
                        cargarDetallesPaseadores(paseadorIds, false);

                        hasMoreData = queryDocumentSnapshots.size() == PAGE_SIZE;
                    } else {
                        hasMoreData = false;
                        if (listaPaseadores.isEmpty()) {
                            tvNoResultados.setVisibility(View.VISIBLE);
                        }
                    }
                    isLoading = false;
                    pbLoading.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando paseadores", e);
                    isLoading = false;
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Error al cargar paseadores", Toast.LENGTH_SHORT).show();
                });
    }

    private void cargarMasPaseadores() {
        if (lastVisible != null) {
            cargarPaseadores(lastVisible);
        }
    }

    private void cargarDetallesPaseadores(List<String> paseadorIds, boolean esPopular) {
        for (String paseadorId : paseadorIds) {
            Task<DocumentSnapshot> taskUsuario = db.collection("usuarios").document(paseadorId).get();
            Task<DocumentSnapshot> taskPaseador = db.collection("paseadores").document(paseadorId).get();

            Tasks.whenAllSuccess(taskUsuario, taskPaseador)
                    .addOnSuccessListener(results -> {
                        DocumentSnapshot usuario = (DocumentSnapshot) results.get(0);
                        DocumentSnapshot paseador = (DocumentSnapshot) results.get(1);

                        if (usuario.exists() && paseador.exists()) {
                            PaseadorResultado resultado = new PaseadorResultado();
                            resultado.setId(paseadorId);
                            resultado.setNombre(usuario.getString("nombre_display"));
                            resultado.setFotoUrl(usuario.getString("foto_perfil"));

                            Double calificacion = paseador.getDouble("calificacion_promedio");
                            resultado.setCalificacion(calificacion != null ? calificacion : 0.0);

                            Long numeroResenas = paseador.getLong("numero_resenas");
                            resultado.setNumeroResenas(numeroResenas != null ? numeroResenas.intValue() : 0);

                            Double tarifa = paseador.getDouble("tarifa_por_hora");
                            resultado.setTarifaPorHora(tarifa != null ? tarifa : 15.0);

                            // Calcular años de experiencia
                            Timestamp fechaInicio = paseador.getTimestamp("fecha_inicio_experiencia");
                            if (fechaInicio != null) {
                                long diff = System.currentTimeMillis() - fechaInicio.toDate().getTime();
                                int anos = (int) (diff / (1000L * 60 * 60 * 24 * 365));
                                resultado.setAnosExperiencia(Math.max(1, anos));
                            } else {
                                resultado.setAnosExperiencia(1);
                            }

                            // Cargar zonas de servicio
                            cargarZonasServicio(paseadorId, resultado, esPopular);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error cargando detalles del paseador " + paseadorId, e);
                    });
        }
    }

    private void cargarZonasServicio(String paseadorId, PaseadorResultado resultado, boolean esPopular) {
        db.collection("paseadores").document(paseadorId)
                .collection("zonas_servicio")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> zonas = new ArrayList<>();
                    List<GeoPoint> geoPoints = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String nombreZona = doc.getString("nombre");
                        if (nombreZona != null) {
                            zonas.add(nombreZona);
                        }
                        GeoPoint centro = doc.getGeoPoint("centro");
                        if (centro != null) {
                            geoPoints.add(centro);
                        }
                    }

                    if (!zonas.isEmpty()) {
                        resultado.setZonasServicio(zonas);
                        resultado.setZonaServicio(TextUtils.join(", ", zonas));
                    } else {
                        resultado.setZonaServicio("No especificada");
                    }
                    resultado.setZonasServicioGeoPoints(geoPoints);

                    // Verificar disponibilidad
                    verificarDisponibilidad(paseadorId, resultado, esPopular);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando zonas", e);
                    resultado.setZonaServicio("No especificada");
                    verificarDisponibilidad(paseadorId, resultado, esPopular);
                });
    }

    private void verificarDisponibilidad(String paseadorId, PaseadorResultado resultado, boolean esPopular) {
        String diaActual = obtenerDiaActual();
        
        db.collection("paseadores").document(paseadorId)
                .collection("disponibilidad")
                .whereEqualTo("dia_semana", diaActual)
                .whereEqualTo("activo", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    resultado.setDisponible(!queryDocumentSnapshots.isEmpty());
                    agregarPaseadorALista(resultado, esPopular);
                })
                .addOnFailureListener(e -> {
                    resultado.setDisponible(false);
                    agregarPaseadorALista(resultado, esPopular);
                });
    }

    private void agregarPaseadorALista(PaseadorResultado resultado, boolean esPopular) {
        paseadoresMap.put(resultado.getId(), resultado);

        if (esPopular) {
            if (!listaPaseadoresPopulares.contains(resultado)) {
                listaPaseadoresPopulares.add(resultado);
                adaptadorPopulares.notifyDataSetChanged();
            }
        } else {
            if (!listaPaseadores.contains(resultado)) {
                listaPaseadores.add(resultado);
                adaptadorResultados.notifyDataSetChanged();
                
                if (!listaPaseadores.isEmpty()) {
                    tvNoResultados.setVisibility(View.GONE);
                }
            }
        }
    }

    private void buscarPaseadores(String query) {
        if (TextUtils.isEmpty(query)) {
            listaPaseadores.clear();
            adaptadorResultados.notifyDataSetChanged();
            cargarPaseadores(null);
            return;
        }

        pbLoading.setVisibility(View.VISIBLE);
        tvNoResultados.setVisibility(View.GONE);
        listaPaseadores.clear();
        adaptadorResultados.notifyDataSetChanged();

        String queryEnd = query + '\uf8ff';

        db.collection("usuarios")
                .whereEqualTo("rol", "PASEADOR")
                .whereEqualTo("activo", true)
                .whereGreaterThanOrEqualTo("nombre_display", query)
                .whereLessThanOrEqualTo("nombre_display", queryEnd)
                .limit(20)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> paseadorIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        paseadorIds.add(doc.getId());
                    }

                    if (!paseadorIds.isEmpty()) {
                        cargarDetallesPaseadores(paseadorIds, false);
                    } else {
                        tvNoResultados.setVisibility(View.VISIBLE);
                    }
                    pbLoading.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error en búsqueda", e);
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Error al buscar", Toast.LENGTH_SHORT).show();
                });
    }

    private void aplicarFiltros() {
        Toast.makeText(this, "Filtro: Cerca de mí", Toast.LENGTH_SHORT).show();
        // Implementar filtrado por ubicación cercana
    }

    private void filtrarPorDisponibilidad() {
        if (chipDisponible.isChecked()) {
            List<PaseadorResultado> disponibles = new ArrayList<>();
            for (PaseadorResultado paseador : listaPaseadores) {
                if (paseador.isDisponible()) {
                    disponibles.add(paseador);
                }
            }
            adaptadorResultados.actualizarLista(disponibles);
            if (disponibles.isEmpty()) {
                tvNoResultados.setVisibility(View.VISIBLE);
            } else {
                tvNoResultados.setVisibility(View.GONE);
            }
        } else {
            adaptadorResultados.actualizarLista(listaPaseadores);
        }
    }

    private void filtrarPorCalificacion() {
        if (chipMejorCalificados.isChecked()) {
            List<PaseadorResultado> mejorCalificados = new ArrayList<>();
            for (PaseadorResultado paseador : listaPaseadores) {
                if (paseador.getCalificacion() >= 4.5) {
                    mejorCalificados.add(paseador);
                }
            }
            adaptadorResultados.actualizarLista(mejorCalificados);
            if (mejorCalificados.isEmpty()) {
                tvNoResultados.setVisibility(View.VISIBLE);
            } else {
                tvNoResultados.setVisibility(View.GONE);
            }
        } else {
            adaptadorResultados.actualizarLista(listaPaseadores);
        }
    }

    private void filtrarPorPrecio() {
        if (chipEconomico.isChecked()) {
            List<PaseadorResultado> economicos = new ArrayList<>();
            for (PaseadorResultado paseador : listaPaseadores) {
                if (paseador.getTarifaPorHora() <= 12.0) {
                    economicos.add(paseador);
                }
            }
            adaptadorResultados.actualizarLista(economicos);
            if (economicos.isEmpty()) {
                tvNoResultados.setVisibility(View.VISIBLE);
            } else {
                tvNoResultados.setVisibility(View.GONE);
            }
        } else {
            adaptadorResultados.actualizarLista(listaPaseadores);
        }
    }

    private void cargarMarcadoresMapa() {
        if (googleMap == null || listaPaseadores.isEmpty()) return;

        googleMap.clear();
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boolean hasMarkers = false;

        for (PaseadorResultado paseador : listaPaseadores) {
            if (paseador.getZonasServicioGeoPoints() != null && !paseador.getZonasServicioGeoPoints().isEmpty()) {
                for (GeoPoint centro : paseador.getZonasServicioGeoPoints()) {
                    LatLng position = new LatLng(centro.getLatitude(), centro.getLongitude());
                    googleMap.addMarker(new MarkerOptions()
                            .position(position)
                            .title(paseador.getNombre())
                            .snippet(String.format("$%.0f/hora", paseador.getTarifaPorHora()))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                    boundsBuilder.include(position);
                    hasMarkers = true;
                }
            }
        }

        if (hasMarkers) {
            LatLngBounds bounds = boundsBuilder.build();
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
        }
    }

    private String obtenerDiaActual() {
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        
        switch (dayOfWeek) {
            case Calendar.MONDAY: return "LUNES";
            case Calendar.TUESDAY: return "MARTES";
            case Calendar.WEDNESDAY: return "MIERCOLES";
            case Calendar.THURSDAY: return "JUEVES";
            case Calendar.FRIDAY: return "VIERNES";
            case Calendar.SATURDAY: return "SABADO";
            case Calendar.SUNDAY: return "DOMINGO";
            default: return "LUNES";
        }
    }
}
