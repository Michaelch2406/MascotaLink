package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.adapters.ActividadPaseoAdapter;
import com.mjc.mascotalink.adapters.FotosPaseoAdapter;
import com.mjc.mascotalink.modelo.PaseoActividad;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.util.FirebaseQueryOptimizer;
import com.mjc.mascotalink.network.SocketManager;
import com.mjc.mascotalink.network.NetworkMonitorHelper;

import com.mjc.mascotalink.util.WhatsAppUtil;

import org.json.JSONObject;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import com.google.firebase.firestore.GeoPoint;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.mjc.mascotalink.MyApplication;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PaseoEnCursoDuenoActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "PaseoEnCursoDueno";
    private static final int REQUEST_PERMISSION_CALL = 2201;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private DocumentReference reservaRef;
    private FirebaseQueryOptimizer firebaseOptimizer;
    private GoogleMap mMap;
    private SocketManager socketManager;
    private NetworkMonitorHelper networkMonitor;

    // UI Elements
    private TextView tvNombrePaseador;
    private TextView tvRating;
    private TextView tvInfoMascota;
    private TextView tvFechaHora;
    private TextView tvEstado;
    private TextView tvHoras, tvMinutos, tvSegundos;
    private TextView tvNotasPaseador;
    private View layoutFotosEmpty;
    private TextView tvActividadEmpty;
    private View contentContainer;
    private ShapeableImageView ivFotoPaseador;
    private ProgressBar pbProgresoPaseo;
    private ProgressBar pbLoading;
    private RecyclerView rvFotos;
    private RecyclerView rvActividad;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnContactar;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnCancelar;
    private BottomNavigationView bottomNav;
    private androidx.core.widget.NestedScrollView scrollContainer; // Added for map interaction handling

    // Adapters
    private FotosPaseoAdapter fotosAdapter;
    private ActividadPaseoAdapter actividadAdapter;

    // Logic
    private String idReserva;
    private String idPaseador;
    private String currentUserId;
    private String telefonoPaseador;
    private String nombrePaseador;
    private Date fechaInicioPaseo;
    private long duracionMinutos = 0L;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    
    // Map Data
    private List<LatLng> rutaPaseo = new ArrayList<>();
    private LatLng ultimaUbicacionConocida;
    private TextView tvUbicacionEstado;
    private Marker marcadorActual;
    private Marker marcadorInicio;
    private Polyline polylineRuta;
    private long lastWalkerMovementTime = System.currentTimeMillis(); // Rastrear inactividad del paseador

    // Cache



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseo_en_curso_dueno);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        socketManager = SocketManager.getInstance(this);

        // Inicializar FirebaseQueryOptimizer para lifecycle-aware listeners
        firebaseOptimizer = new FirebaseQueryOptimizer();

        // Inicializar NetworkMonitorHelper para monitoreo robusto de red
        networkMonitor = new NetworkMonitorHelper(this, socketManager, new NetworkMonitorHelper.NetworkCallback() {
            private com.google.android.material.snackbar.Snackbar reconnectSnackbar = null;

            @Override
            public void onNetworkLost() {
                runOnUiThread(() -> {
                    if (tvUbicacionEstado != null) {
                        tvUbicacionEstado.setText("⚠️ Sin conexión - Intentando reconectar...");
                        tvUbicacionEstado.setTextColor(ContextCompat.getColor(PaseoEnCursoDuenoActivity.this, R.color.red_error));
                    }
                    // Mostrar Snackbar persistente
                    if (reconnectSnackbar == null || !reconnectSnackbar.isShown()) {
                        reconnectSnackbar = com.google.android.material.snackbar.Snackbar.make(
                            findViewById(android.R.id.content),
                            "Conexión perdida. Reconectando...",
                            com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
                        );
                        reconnectSnackbar.setAction("Reintentar", v -> {
                            if (networkMonitor != null) {
                                networkMonitor.forceReconnect();
                            }
                        });
                        reconnectSnackbar.show();
                    }
                });
            }

            @Override
            public void onNetworkAvailable() {
                Log.d(TAG, "Red disponible nuevamente");
                runOnUiThread(() -> {
                    if (tvUbicacionEstado != null) {
                        tvUbicacionEstado.setText("Conectando...");
                        tvUbicacionEstado.setTextColor(ContextCompat.getColor(PaseoEnCursoDuenoActivity.this, R.color.secondary));
                    }
                });
            }

            @Override
            public void onReconnected() {
                Log.d(TAG, "Reconectado exitosamente");
                runOnUiThread(() -> {
                    if (tvUbicacionEstado != null) {
                        tvUbicacionEstado.setTextColor(ContextCompat.getColor(PaseoEnCursoDuenoActivity.this, R.color.blue_primary));
                    }
                    // Dismiss Snackbar de reconexión
                    if (reconnectSnackbar != null && reconnectSnackbar.isShown()) {
                        reconnectSnackbar.dismiss();
                    }
                    // Mostrar confirmación
                    com.google.android.material.snackbar.Snackbar.make(
                        findViewById(android.R.id.content),
                        "✅ Conexión restaurada",
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show();
                });
            }

            @Override
            public void onRetrying(int attempt, long delayMs) {
                runOnUiThread(() -> {
                    String msg = String.format(java.util.Locale.US,
                        "Reintentando conexión (%d/5)...", attempt);
                    if (tvUbicacionEstado != null) {
                        tvUbicacionEstado.setText(msg);
                    }
                    if (reconnectSnackbar != null && reconnectSnackbar.isShown()) {
                        reconnectSnackbar.setText("Reintento " + attempt + "/5 en " + (delayMs/1000) + "s...");
                    }
                });
            }

            @Override
            public void onReconnectionFailed(int attempts) {
                runOnUiThread(() -> {
                    if (tvUbicacionEstado != null) {
                        tvUbicacionEstado.setText("❌ Conexión fallida tras " + attempts + " intentos");
                        tvUbicacionEstado.setTextColor(ContextCompat.getColor(PaseoEnCursoDuenoActivity.this, R.color.red_error));
                    }
                    if (reconnectSnackbar != null && reconnectSnackbar.isShown()) {
                        reconnectSnackbar.dismiss();
                    }
                    // Snackbar con acción de reintento manual
                    com.google.android.material.snackbar.Snackbar.make(
                        findViewById(android.R.id.content),
                        "No se pudo reconectar. Verifica tu conexión.",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAction("Reintentar", v -> {
                        if (networkMonitor != null) {
                            networkMonitor.forceReconnect();
                        }
                    }).show();
                });
            }

            @Override
            public void onNetworkTypeChanged(NetworkMonitorHelper.NetworkType type) {
                Log.d(TAG, "Tipo de red cambió a: " + type);
            }

            @Override
            public void onNetworkQualityChanged(NetworkMonitorHelper.NetworkQuality quality) {
                Log.d(TAG, "Calidad de red: " + quality);
            }
        });

        initViews();
        setupToolbar();
        setupRecyclerViews();
        setupButtons();
        setupBottomNav();
        setupMap();

        // owner-vibe-fix: estado de carga inicial
        mostrarLoading(true);
        if (contentContainer != null) {
            contentContainer.setVisibility(View.GONE);
        }

        idReserva = getIntent().getStringExtra("id_reserva");
        if (idReserva == null || idReserva.isEmpty()) {
            Toast.makeText(this, "Error: Reserva no encontrada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Error: usuario no autenticado", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = user.getUid();

        reservaRef = db.collection("reservas").document(idReserva);

        // Configurar WebSocket listeners para ubicación en tiempo real
        setupWebSocketListeners();

        // Configurar monitoreo de cambios de red con NetworkMonitorHelper
        networkMonitor.setCurrentRoom(idReserva, NetworkMonitorHelper.RoomType.PASEO);
        networkMonitor.register();

        // Luego verificar permisos y sincronizar con Firestore
        verificarPermisosYEscuchar();
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        
        View btnAbrirMaps = findViewById(R.id.btn_abrir_maps);
        if (btnAbrirMaps != null) {
            btnAbrirMaps.setOnClickListener(v -> abrirMapaFullscreen());
        }

        View btnMapType = findViewById(R.id.btn_map_type);
        if (btnMapType != null) {
            btnMapType.setOnClickListener(v -> toggleMapType());
        }
    }

    private void toggleMapType() {
        if (mMap == null) return;
        int currentType = mMap.getMapType();
        if (currentType == GoogleMap.MAP_TYPE_NORMAL) {
            mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        } else {
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setAllGesturesEnabled(true); // Enable interaction in mini-map
        mMap.getUiSettings().setMapToolbarEnabled(false);

        // CRITICAL FIX: Setup Touch Listener RECURSIVELY on the Map View hierarchy
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null && mapFragment.getView() != null) {
            setTouchListenerRecursive(mapFragment.getView(), (v, event) -> {
                int action = event.getAction();
                switch (action) {
                    case android.view.MotionEvent.ACTION_DOWN:
                    case android.view.MotionEvent.ACTION_MOVE:
                        // Disallow parent NestedScrollView to intercept
                        if (scrollContainer != null) scrollContainer.requestDisallowInterceptTouchEvent(true);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        // Allow parent NestedScrollView to intercept again
                        if (scrollContainer != null) scrollContainer.requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false; // IMPORTANT: Return false so the event still reaches the map
            });
        }
        
        // Initial empty state or loading
        if (!rutaPaseo.isEmpty()) {
            actualizarMapa(rutaPaseo, null);
        }
    }

    // Helper method to recursively set OnTouchListener to all views in a hierarchy
    private void setTouchListenerRecursive(View view, View.OnTouchListener listener) {
        if (view == null) return;
        view.setOnTouchListener(listener);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup viewGroup = (android.view.ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                setTouchListenerRecursive(viewGroup.getChildAt(i), listener);
            }
        }
    }

    private void abrirMapaFullscreen() {
        if (ultimaUbicacionConocida == null) {
            Toast.makeText(this, "Ubicacion no disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Uri gmmIntentUri = Uri.parse("geo:" + ultimaUbicacionConocida.latitude + "," + ultimaUbicacionConocida.longitude + "?q=" + ultimaUbicacionConocida.latitude + "," + ultimaUbicacionConocida.longitude + "(Paseador)");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            // Fallback to browser if Maps app not installed
            Uri browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + ultimaUbicacionConocida.latitude + "," + ultimaUbicacionConocida.longitude);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
            startActivity(browserIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNav(); // Refresh nav state
        if (fechaInicioPaseo != null) {
            startTimer();
        }

        // Reconectar al paseo para recibir ubicación en tiempo real
        if (idReserva != null && socketManager.isConnected()) {
            socketManager.joinPaseo(idReserva);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // FirebaseQueryOptimizer maneja la limpieza de listeners automáticamente
        // No es necesario remover listeners manualmente

        stopTimer();

        // Limpiar listeners de WebSocket
        socketManager.off("walker_location");
        socketManager.off("joined_paseo");

        // Limpiar monitor de red
        if (networkMonitor != null) {
            networkMonitor.unregister();
        }
    }

    private void initViews() {
        tvNombrePaseador = findViewById(R.id.tv_nombre_paseador);
        tvRating = findViewById(R.id.tv_rating);
        tvInfoMascota = findViewById(R.id.tv_info_mascota);
        tvFechaHora = findViewById(R.id.tv_fecha_hora);
        tvEstado = findViewById(R.id.tv_estado);
        tvHoras = findViewById(R.id.tv_horas);
        tvMinutos = findViewById(R.id.tv_minutos);
        tvSegundos = findViewById(R.id.tv_segundos);
        tvNotasPaseador = findViewById(R.id.tv_notas_paseador);
        layoutFotosEmpty = findViewById(R.id.layout_fotos_empty);
        tvActividadEmpty = findViewById(R.id.tv_actividad_empty);
        ivFotoPaseador = findViewById(R.id.iv_foto_paseador);
        pbProgresoPaseo = findViewById(R.id.pb_progreso_paseo);
        pbLoading = findViewById(R.id.pb_loading);
        contentContainer = findViewById(R.id.content_container);
        rvFotos = findViewById(R.id.rv_fotos);
        rvActividad = findViewById(R.id.rv_actividad);
        btnContactar = findViewById(R.id.btn_contactar_paseador);
        btnCancelar = findViewById(R.id.btn_cancelar_paseo);
        bottomNav = findViewById(R.id.bottom_nav);
        tvUbicacionEstado = findViewById(R.id.tv_ubicacion_estado);
        scrollContainer = findViewById(R.id.scroll_container); // Initialize scrollContainer
    }
    
    // ... (Rest of setup methods: setupToolbar, setupRecyclerViews, setupButtons, setupBottomNav) ...

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerViews() {
        // Fotos Adapter (Read-only for owner)
        fotosAdapter = new FotosPaseoAdapter(this, new FotosPaseoAdapter.OnFotoInteractionListener() {
            @Override
            public void onFotoClick(String url) {
                mostrarFotoCompleta(url);
            }

            @Override
            public void onFotoLongClick(String url) {
                // Owner cannot delete photos
            }
        });
        rvFotos.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvFotos.setAdapter(fotosAdapter);

        // Actividad Adapter
        actividadAdapter = new ActividadPaseoAdapter();
        rvActividad.setLayoutManager(new LinearLayoutManager(this));
        rvActividad.setAdapter(actividadAdapter);
    }

    private void setupButtons() {
        btnContactar.setOnClickListener(v -> mostrarOpcionesContacto());
        btnCancelar.setOnClickListener(v -> iniciarProcesoCancelacion());
    }

    private void setupBottomNav() {
        // Assuming owner role for this activity
        BottomNavManager.setupBottomNav(this, bottomNav, "DUEÑO", R.id.menu_walks);
    }

    /**
     * Configurar listeners de WebSocket para recibir ubicación en tiempo real
     */
    private void setupWebSocketListeners() {
        // Listener para confirmación de unión al paseo
        socketManager.on("joined_paseo", args -> {
            if (args.length > 0) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    String paseoId = data.getString("paseoId");
                    Log.d(TAG, "✅ Unido al paseo vía WebSocket: " + paseoId);
                } catch (Exception e) {
                    Log.e(TAG, "Error parseando joined_paseo", e);
                }
            }
        });

        // Listener para actualizaciones de ubicación en tiempo real
        socketManager.on("walker_location", args -> {
            if (args.length > 0) {
                try {
                    JSONObject data = (JSONObject) args[0];
                    double latitud = data.getDouble("latitud");
                    double longitud = data.getDouble("longitud");
                    double accuracy = data.optDouble("accuracy", 0);
                    long timestamp = data.optLong("timestamp", System.currentTimeMillis());

                    Log.d(TAG, "📍 Ubicación en tiempo real: " + latitud + ", " + longitud);

                    // Actualizar mapa en el hilo principal
                    runOnUiThread(() -> {
                        LatLng nuevaUbicacion = new LatLng(latitud, longitud);

                        // Agregar a la ruta
                        if (!rutaPaseo.isEmpty()) {
                            // Solo agregar si hay movimiento significativo
                            LatLng ultima = rutaPaseo.get(rutaPaseo.size() - 1);
                            float[] results = new float[1];
                            android.location.Location.distanceBetween(
                                ultima.latitude, ultima.longitude,
                                latitud, longitud, results);

                            if (results[0] > 5f) { // Más de 5 metros
                                rutaPaseo.add(nuevaUbicacion);
                            }
                        } else {
                            rutaPaseo.add(nuevaUbicacion);
                        }

                        ultimaUbicacionConocida = nuevaUbicacion;

                        // Actualizar mapa inmediatamente
                        if (mMap != null) {
                            actualizarMapaEnTiempoReal(nuevaUbicacion, (float) accuracy);
                        }

                        // Actualizar estado de ubicación
                        long diffSec = (System.currentTimeMillis() - timestamp) / 1000;
                        String estado = String.format(Locale.US,
                            "Ubicación: hace %d s (±%.0f m, en tiempo real)",
                            diffSec, accuracy);
                        if (tvUbicacionEstado != null) {
                            tvUbicacionEstado.setText(estado);
                            tvUbicacionEstado.setTextColor(
                                ContextCompat.getColor(this, R.color.blue_primary));
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error procesando walker_location", e);
                }
            }
        });

        // Unirse al paseo si está conectado
        if (idReserva != null && socketManager.isConnected()) {
            socketManager.joinPaseo(idReserva);
        }
    }

    /**
     * Actualizar mapa en tiempo real con animación suave
     */
    private void actualizarMapaEnTiempoReal(LatLng nuevaPos, float accuracy) {
        if (mMap == null) return;

        // Actualizar polyline si existe
        if (polylineRuta != null && !rutaPaseo.isEmpty()) {
            polylineRuta.setPoints(rutaPaseo);
        }

        // Animar marcador a nueva posición
        if (marcadorActual != null) {
            animarMarcador(marcadorActual, nuevaPos);
        } else {
            // Crear marcador si no existe
            BitmapDescriptor walkerIcon = getResizedBitmapDescriptor(R.drawable.ic_paseador_perro_marcador, 120);
            marcadorActual = mMap.addMarker(new MarkerOptions()
                    .position(nuevaPos)
                    .title("Paseador")
                    .icon(walkerIcon != null ? walkerIcon : BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .anchor(0.5f, 1.0f));
        }

        // Mover cámara suavemente si hay desplazamiento significativo
        if (marcadorActual != null) {
            LatLng currentCameraPos = mMap.getCameraPosition().target;
            float[] results = new float[1];
            android.location.Location.distanceBetween(
                currentCameraPos.latitude, currentCameraPos.longitude,
                nuevaPos.latitude, nuevaPos.longitude, results);

            // Mover cámara solo si el paseador se movió más de 10 metros
            if (results[0] > 10f) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(nuevaPos, 17f), 500, null);
            }
        }
    }



    private void verificarPermisosYEscuchar() {
        mostrarLoading(true);

        reservaRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                mostrarLoading(false);
                Toast.makeText(this, "Reserva no encontrada", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String currentUserIdSnapshot = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
            if (currentUserId == null || currentUserId.isEmpty() || !currentUserId.equals(currentUserIdSnapshot)) {
                mostrarLoading(false);
                Toast.makeText(this, "Error: usuario no autenticado", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            DocumentReference duenoRef = snapshot.getDocumentReference("id_dueno");
            String duenoId = duenoRef != null ? duenoRef.getId() : snapshot.getString("id_dueno");

            if (!currentUserId.equals(duenoId)) {
                mostrarLoading(false);
                Toast.makeText(this, "No tienes permiso para ver este paseo", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String estado = snapshot.getString("estado");
            if (!"EN_CURSO".equalsIgnoreCase(estado)) {
                mostrarLoading(false);
                Toast.makeText(this, "El paseo ya no está? en curso", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // Start listening if validation passes
            escucharReserva();

        }).addOnFailureListener(e -> {
            mostrarLoading(false);
            Toast.makeText(this, "Error al verificar permisos", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void escucharReserva() {
        firebaseOptimizer.addDocumentListener(this, reservaRef,
            new FirebaseQueryOptimizer.DocumentSnapshotCallback() {
                @Override
                public void onSuccess(DocumentSnapshot snapshot) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        // Guardar en caché para futuras aperturas rápidas
                        /*
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            cacheManager.saveReservaToCache(user.getUid(), snapshot);
                        }
                        */

                        manejarSnapshotReserva(snapshot);
                        mostrarLoading(false);
                        if (contentContainer != null) {
                            contentContainer.setVisibility(View.VISIBLE);
                        }
                    } else {
                        // Limpiar caché cuando la reserva ya no existe
                        /*
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            cacheManager.clearCache(user.getUid());
                        }
                        */
                        Toast.makeText(PaseoEnCursoDuenoActivity.this,
                            "El paseo ha finalizado o no existe", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error escuchando reserva", e);
                }
            });
    }

    private void manejarSnapshotReserva(DocumentSnapshot snapshot) {
        String estado = snapshot.getString("estado");
        if (estado == null) estado = "";

        // --- FIX: Manejo de Estado de Solicitud de Cancelación (Dueño) ---
        if ("SOLICITUD_CANCELACION".equalsIgnoreCase(estado)) {
            tvEstado.setText("Esperando confirmación...");
            tvEstado.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            if (btnCancelar != null) {
                btnCancelar.setEnabled(false);
                btnCancelar.setAlpha(0.5f);
            }
            // Continuar cargando datos para mostrar info, pero no finalizar
        } else if (!"EN_CURSO".equalsIgnoreCase(estado) && !"EN_PROGRESO".equalsIgnoreCase(estado)) {
             // Bloque existente para estados finales
            if (estado.equalsIgnoreCase("COMPLETADO")) {
                Toast.makeText(this, "El paseo ha finalizado con éxito.", Toast.LENGTH_SHORT).show();
                stopTimer();
                
                Intent intent = new Intent(PaseoEnCursoDuenoActivity.this, ResumenPaseoActivity.class);
                intent.putExtra("id_reserva", idReserva);
                startActivity(intent);
                finish();
            } else if (estado.startsWith("CANCELADO")) {
                Toast.makeText(this, "El paseo ha sido cancelado: " + estado, Toast.LENGTH_SHORT).show();
                stopTimer();
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
            } else {
                Toast.makeText(this, "El paseo ya no está en curso", Toast.LENGTH_SHORT).show();
                finish();
            }
            return;
        } else {
            // Si volvió a EN_CURSO (rechazado), reactivar botón
            if (btnCancelar != null) {
                btnCancelar.setEnabled(true);
                btnCancelar.setAlpha(1f);
            }
            tvEstado.setText(R.string.paseo_en_curso_state_en_progreso); // Restaurar texto
            tvEstado.setTextColor(getResources().getColor(R.color.color_en_curso));
        }
        // -------------------------------------------------------------------

        // owner-vibe-fix: Tiempos y duracion
        Timestamp inicioTimestamp = snapshot.getTimestamp("fecha_inicio_paseo");
        if (inicioTimestamp == null) {
            inicioTimestamp = snapshot.getTimestamp("hora_inicio");
        }

        Long duracion = snapshot.getLong("duracion_minutos");
        if (duracion != null) {
            duracionMinutos = duracion;
        }

        if (inicioTimestamp != null) {
            Date nuevaFecha = inicioTimestamp.toDate();
            boolean reiniciarTimer = fechaInicioPaseo == null || fechaInicioPaseo.getTime() != nuevaFecha.getTime();
            fechaInicioPaseo = nuevaFecha;
            if (reiniciarTimer) {
                startTimer();
            }
            actualizarInfoFecha(nuevaFecha);
        } else {
            tvHoras.setText("00");
            tvMinutos.setText("00");
            tvSegundos.setText("00");
            tvFechaHora.setText("Esperando que el paseador inicie");
        }

        // 3. Notas
        String notas = snapshot.getString("notas_paseador");
        tvNotasPaseador.setText(notas != null && !notas.isEmpty() ? notas : "Sin notas aún");

        // 4. Fotos
        Object fotosObj = snapshot.get("fotos_paseo");
        List<String> fotos = new ArrayList<>();
        if (fotosObj instanceof List) {
            for (Object item : (List<?>) fotosObj) {
                if (item instanceof String) {
                    fotos.add((String) item);
                }
            }
        }
        fotosAdapter.submitList(fotos);
        boolean hayFotos = !fotos.isEmpty();
        rvFotos.setVisibility(hayFotos ? View.VISIBLE : View.GONE);
        if (layoutFotosEmpty != null) {
            layoutFotosEmpty.setVisibility(hayFotos ? View.GONE : View.VISIBLE);
        }

        // 5. Actividad (Timeline)
        Object actividadObj = snapshot.get("actividad");
        List<PaseoActividad> actividades = new ArrayList<>();
        if (actividadObj instanceof List) {
            for (Object item : (List<?>) actividadObj) {
                if (item instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) item;
                    PaseoActividad actividad = new PaseoActividad();
                    actividad.setEvento((String) map.get("evento"));
                    actividad.setDescripcion((String) map.get("descripcion"));
                    
                    Object tsObj = map.get("timestamp");
                    if (tsObj instanceof Timestamp) {
                        actividad.setTimestamp((Timestamp) tsObj);
                    } else if (tsObj instanceof Date) {
                        actividad.setTimestamp(new Timestamp((Date) tsObj));
                    } else {
                        // Manejo de error silencioso o timestamp por defecto para evitar crash
                        actividad.setTimestamp(null); 
                    }
                    
                    actividades.add(actividad);
                }
            }
        }
        Collections.sort(actividades, (o1, o2) -> {
            Timestamp t1 = o1.getTimestamp();
            Timestamp t2 = o2.getTimestamp();
            if (t1 == null) {
                return 1;
            }
            if (t2 == null) {
                return -1;
            }
            return t2.compareTo(t1);
        });
        actividadAdapter.setEventos(actividades);
        boolean hayActividad = !actividades.isEmpty();
        rvActividad.setVisibility(hayActividad ? View.VISIBLE : View.GONE);
        if (tvActividadEmpty != null) {
            tvActividadEmpty.setVisibility(hayActividad ? View.GONE : View.VISIBLE);
        }
        
        // 7. Actualizar Mapa
        List<LatLng> puntosMapa = parsearUbicaciones(snapshot.get("ubicaciones"));
        actualizarMapa(puntosMapa, snapshot.get("ubicaciones"));

        // 6. Cargar datos relacionados (Paseador, Mascota) solo si no se han cargado
        if (idPaseador == null) {
            DocumentReference paseadorRef = snapshot.getDocumentReference("id_paseador");
            if (paseadorRef != null) {
                idPaseador = paseadorRef.getId();
                cargarDatosPaseador(paseadorRef);
            } else {
                String pid = snapshot.getString("id_paseador");
                if (pid != null) {
                    idPaseador = pid;
                    cargarDatosPaseador(db.collection("usuarios").document(pid));
                }
            }
        }

        String mascotaId = snapshot.getString("id_mascota");
        if (mascotaId != null) {
            cargarDatosMascota(mascotaId);
        }
    }

    @SuppressWarnings("unchecked")
    private List<LatLng> parsearUbicaciones(Object ubicacionesObj) {
        List<LatLng> puntos = new ArrayList<>();
        if (ubicacionesObj instanceof List) {
            for (Object item : (List<?>) ubicacionesObj) {
                if (item instanceof GeoPoint) {
                    GeoPoint gp = (GeoPoint) item;
                    puntos.add(new LatLng(gp.getLatitude(), gp.getLongitude()));
                } else if (item instanceof Map) {
                    try {
                        Map<String, Object> map = (Map<String, Object>) item;
                        Object latObj = map.get("lat");
                        Object lngObj = map.get("lng");
                        if (lngObj == null) lngObj = map.get("lon");

                        double lat = 0, lng = 0;
                        if (latObj instanceof Number) lat = ((Number) latObj).doubleValue();
                        if (lngObj instanceof Number) lng = ((Number) lngObj).doubleValue();

                        if (lat != 0 || lng != 0) {
                            puntos.add(new LatLng(lat, lng));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error parseando punto de mapa: " + item, e);
                    }
                }
            }
        }
        // Limitar para evitar sobrecarga de polil??nea
        if (puntos.size() > 200) {
            return puntos.subList(puntos.size() - 200, puntos.size());
        }
        return puntos;
    }

    private void actualizarMapa(List<LatLng> puntos, Object ubicacionesRaw) {
        if (mMap == null || puntos == null || puntos.isEmpty()) return;

        LatLng previo = this.ultimaUbicacionConocida;
        this.rutaPaseo = puntos;
        this.ultimaUbicacionConocida = puntos.get(puntos.size() - 1);

        // Ruta sin limpiar el mapa para evitar parpadeos
        if (polylineRuta == null) {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(puntos)
                    .width(16f)
                    .color(getResources().getColor(R.color.blue_primary))
                    .jointType(JointType.ROUND)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .geodesic(true);
            polylineRuta = mMap.addPolyline(polylineOptions);
        } else {
            polylineRuta.setPoints(puntos);
        }

        // Marcador de inicio (solo una vez)
        if (marcadorInicio == null && !puntos.isEmpty()) {
            marcadorInicio = mMap.addMarker(new MarkerOptions()
                    .position(puntos.get(0))
                    .title("Inicio")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        }

        // Marcador actual con animaci?n
        BitmapDescriptor walkerIcon = getResizedBitmapDescriptor(R.drawable.ic_paseador_perro_marcador, 120);
        if (marcadorActual == null) {
            marcadorActual = mMap.addMarker(new MarkerOptions()
                    .position(ultimaUbicacionConocida)
                    .title("Paseador")
                    .icon(walkerIcon != null ? walkerIcon : BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .anchor(0.5f, 1.0f));
        } else {
            animarMarcador(marcadorActual, ultimaUbicacionConocida);
            if (walkerIcon != null) {
                marcadorActual.setIcon(walkerIcon);
            }
        }

        // Mover c?mara suavemente si hay desplazamiento significativo
        try {
            float zoomLevel = 17.0f;
            boolean moverCamara = true;
            if (previo != null) {
                float[] results = new float[1];
                android.location.Location.distanceBetween(previo.latitude, previo.longitude,
                        ultimaUbicacionConocida.latitude, ultimaUbicacionConocida.longitude, results);
                moverCamara = results[0] > 8f;
            }
            if (moverCamara) {
                com.google.android.gms.maps.model.CameraPosition cameraPosition =
                        new com.google.android.gms.maps.model.CameraPosition.Builder()
                                .target(ultimaUbicacionConocida)
                                .zoom(zoomLevel)
                                .bearing(0)
                                .tilt(45) // Fixed 3D tilt
                                .build();
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 700, null);
            }
        } catch (Exception e) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ultimaUbicacionConocida, 16f));
        }

        actualizarEstadoUbicacionDueno(ubicacionesRaw);
    }

    private void actualizarEstadoUbicacionDueno(Object ubicacionesRaw) {
        if (tvUbicacionEstado == null) return;
        if (!(ubicacionesRaw instanceof List)) {
            tvUbicacionEstado.setText("Ubicación: sin datos");
            tvUbicacionEstado.setTextColor(ContextCompat.getColor(this, R.color.gray_dark));
            return;
        }
        List<?> lista = (List<?>) ubicacionesRaw;
        if (lista.isEmpty()) {
            tvUbicacionEstado.setText("Ubicación: sin datos");
            tvUbicacionEstado.setTextColor(ContextCompat.getColor(this, R.color.gray_dark));
            return;
        }
        Object last = lista.get(lista.size() - 1);
        Double acc = null;
        Double speed = null;
        Timestamp ts = null;
        if (last instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) last;
            Object accObj = map.get("acc");
            Object speedObj = map.get("speed");
            Object tsObj = map.get("ts");
            if (accObj instanceof Number) acc = ((Number) accObj).doubleValue();
            if (speedObj instanceof Number) speed = ((Number) speedObj).doubleValue();
            if (tsObj instanceof Timestamp) {
                ts = (Timestamp) tsObj;
            } else if (tsObj instanceof Date) {
                ts = new Timestamp((Date) tsObj);
            }
        } else if (last instanceof GeoPoint) {
            tvUbicacionEstado.setText("Ubicación: actualizada");
            tvUbicacionEstado.setTextColor(ContextCompat.getColor(this, R.color.gray_dark));
            return;
        }

        boolean enMovimiento = speed != null && speed > 0.7;
        if (enMovimiento) {
            lastWalkerMovementTime = System.currentTimeMillis();
        }

        long timeStationary = System.currentTimeMillis() - lastWalkerMovementTime;
        boolean isStationaryLongTime = timeStationary > (6 * 60 * 1000); // 6 minutos

        StringBuilder sb = new StringBuilder("Ubicación: ");
        
        if (ts != null) {
            long diffSec = (new Date().getTime() - ts.toDate().getTime()) / 1000;
            if (diffSec < 60) {
                sb.append("hace ").append(diffSec).append(" s");
            } else {
                sb.append("hace ").append(diffSec / 60).append(" min");
            }
        } else {
            sb.append("actualizada");
        }

        if (acc != null) {
            sb.append(" (±").append(acc.intValue()).append(" m");
        }

        // Lógica de Color y Estado
        if (isStationaryLongTime) {
            long mins = TimeUnit.MILLISECONDS.toMinutes(timeStationary);
            sb.append(", detenido por ").append(mins).append(" min");
            tvUbicacionEstado.setTextColor(ContextCompat.getColor(this, R.color.red_error));
        } else if (enMovimiento) {
            sb.append(", en movimiento");
            tvUbicacionEstado.setTextColor(ContextCompat.getColor(this, R.color.blue_primary));
        } else {
            sb.append(", detenido");
            tvUbicacionEstado.setTextColor(ContextCompat.getColor(this, R.color.gray_dark));
        }
        
        sb.append(")");
        tvUbicacionEstado.setText(sb.toString());
    }

    private void animarMarcador(Marker marker, LatLng destino) {
        if (marker == null || destino == null) return;
        final LatLng inicio = marker.getPosition();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(700);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            double lat = inicio.latitude + (destino.latitude - inicio.latitude) * t;
            double lng = inicio.longitude + (destino.longitude - inicio.longitude) * t;
            marker.setPosition(new LatLng(lat, lng));
        });
        animator.start();
    }

    private BitmapDescriptor getResizedBitmapDescriptor(int resourceId, int widthPx) {
        try {
            Drawable drawable = ContextCompat.getDrawable(this, resourceId);
            if (drawable == null) return null;

            int intrinsicWidth = drawable.getIntrinsicWidth();
            int intrinsicHeight = drawable.getIntrinsicHeight();

            if (widthPx <= 0 || intrinsicWidth <= 0 || intrinsicHeight <= 0) {
                Log.w(TAG, "Dimensiones inválidas para el marcador. Width: " + widthPx + ", Intrinsic: " + intrinsicWidth + "x" + intrinsicHeight);
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
            }

            int heightPx = (int) ((float) widthPx * ((float) intrinsicHeight / (float) intrinsicWidth));

            if (heightPx <= 0) {
                 heightPx = widthPx; // Fallback to square
            }

            drawable.setBounds(0, 0, widthPx, heightPx);
            Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.draw(canvas);

            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Error creando icono personalizado", e);
            return null;
        }
    }

    private void cargarDatosPaseador(DocumentReference ref) {
        // 1. Cargar datos básicos del Usuario (Nombre, Foto, Teléfono)
        ref.get().addOnSuccessListener(userDoc -> {
            if (userDoc.exists()) {
                nombrePaseador = userDoc.getString("nombre_display");
                if (nombrePaseador == null)
                    nombrePaseador = userDoc.getString("nombre");
                tvNombrePaseador.setText(nombrePaseador != null ? nombrePaseador : "Paseador no disponible");

                telefonoPaseador = userDoc.getString("telefono");
                if (telefonoPaseador == null || telefonoPaseador.isEmpty()) {
                    btnContactar.setEnabled(false);
                    btnContactar.setAlpha(0.6f);
                } else {
                    btnContactar.setEnabled(true);
                    btnContactar.setAlpha(1f);
                }

                String fotoUrl = userDoc.getString("foto_perfil");
                if (fotoUrl != null && !fotoUrl.isEmpty()) {
                    if (!isDestroyed() && !isFinishing()) {
                        Glide.with(this)
                                .load(MyApplication.getFixedUrl(fotoUrl))
                                .circleCrop()
                                .placeholder(R.drawable.ic_user_placeholder)
                                .error(R.drawable.ic_user_placeholder)
                                .into(ivFotoPaseador);
                    }
                } else {
                    ivFotoPaseador.setImageResource(R.drawable.ic_user_placeholder);
                }

                // 2. Cargar calificación desde la colección 'paseadores'
                db.collection("paseadores").document(userDoc.getId()).get()
                    .addOnSuccessListener(paseadorDoc -> {
                        if (paseadorDoc.exists()) {
                            Double rating = paseadorDoc.getDouble("calificacion_promedio");
                            // Intentar obtener total_resenas, fallback a numero_resenas o num_servicios_completados si se prefiere
                            Long numResenas = paseadorDoc.getLong("total_resenas");
                            if (numResenas == null) numResenas = paseadorDoc.getLong("num_servicios_completados");

                            if (rating != null) {
                                tvRating.setText(String.format(Locale.US, "%.1f (%d)", rating, numResenas != null ? numResenas : 0));
                            } else {
                                tvRating.setText("N/A");
                            }
                        } else {
                            tvRating.setText("N/A");
                        }
                    })
                    .addOnFailureListener(e -> tvRating.setText("N/A"));
            }
        }).addOnFailureListener(e -> Toast.makeText(this, "Error cargando datos del paseador", Toast.LENGTH_SHORT).show());
    }

    private void cargarDatosMascota(String mascotaId) {
        // Try to load from the owner's subcollection first (primary source)
        db.collection("duenos").document(currentUserId).collection("mascotas").document(mascotaId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        mostrarDatosMascota(doc);
                    } else {
                        // Fallback: Try global collection
                        cargarMascotaGlobal(mascotaId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error cargando mascota de subcolección, intentando global", e);
                    cargarMascotaGlobal(mascotaId);
                });
    }

    private void cargarMascotaGlobal(String mascotaId) {
        db.collection("mascotas").document(mascotaId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                mostrarDatosMascota(doc);
            } else {
                tvInfoMascota.setText("Información de mascota no disponible");
            }
        }).addOnFailureListener(e -> tvInfoMascota.setText("Error cargando mascota"));
    }

    private void mostrarDatosMascota(DocumentSnapshot doc) {
        String nombre = doc.getString("nombre");
        String raza = doc.getString("raza");
        String nombreSafe = (nombre != null && !nombre.isEmpty()) ? nombre : "Mascota";
        String razaSafe = (raza != null && !raza.isEmpty()) ? raza : "raza no disponible";
        tvInfoMascota.setText(String.format("%s, %s", nombreSafe, razaSafe));
        
        // Cargar foto de la mascota si hay un ImageView disponible en el futuro
        // Por ahora, solo logueamos la URL para depuración
        String urlFoto = doc.getString("foto_url");
        if (urlFoto == null || urlFoto.isEmpty()) {
            urlFoto = doc.getString("foto_principal_url");
        }
        if (urlFoto != null) {
            Log.d(TAG, "URL Foto Mascota: " + urlFoto);
        }
    }

    private void actualizarInfoFecha(Date inicio) {
        if (inicio == null)
            return;
        
        // Calcular hora fin
        long duracionMillis = TimeUnit.MINUTES.toMillis(Math.max(duracionMinutos, 0));
        Date fin = new Date(inicio.getTime() + duracionMillis);

        SimpleDateFormat fechaFormat = new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES"));
        SimpleDateFormat horaFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        
        // Formato: "12 de Mayo, 10:00 AM - 10:30 AM"
        String fechaStr = fechaFormat.format(inicio);
        String horaInicioStr = horaFormat.format(inicio);
        String horaFinStr = horaFormat.format(fin);
        
        tvFechaHora.setText(String.format("%s, %s - %s", fechaStr, horaInicioStr, horaFinStr));
    }

    private void iniciarProcesoCancelacion() {
        if (fechaInicioPaseo == null) {
            Toast.makeText(this, "El paseo aún no ha iniciado correctamente", Toast.LENGTH_SHORT).show();
            return;
        }

        long tiempoTranscurrido = calcularTiempoTranscurrido();
        // Regla: Bloquear cancelaci??n antes de 10 minutos
        if (tiempoTranscurrido < 10 * 60 * 1000) {
            long minutosRestantes = 10 - TimeUnit.MILLISECONDS.toMinutes(tiempoTranscurrido);
            Toast.makeText(this, "Debes esperar " + minutosRestantes + " minutos m??s para cancelar.", Toast.LENGTH_LONG).show();
            return;
        }

        mostrarDialogoCancelacion();
    }

    private void mostrarDialogoCancelacion() {
        if (isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_cancelar_paseo, null);
        builder.setView(view);

        android.widget.RadioGroup rgMotivos = view.findViewById(R.id.rg_motivos);
        com.google.android.material.textfield.TextInputEditText etOtroMotivo = view.findViewById(R.id.et_otro_motivo);
        
        // Ocultar opci??n de "??xito" para el dueño
        View rbExito = view.findViewById(R.id.rb_finalizar_exito);
        if (rbExito != null) rbExito.setVisibility(View.GONE);
        
        rgMotivos.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_otro) {
                etOtroMotivo.setVisibility(View.VISIBLE);
            } else {
                etOtroMotivo.setVisibility(View.GONE);
            }
        });

        builder.setPositiveButton("Enviar Solicitud", (dialog, which) -> {
            String motivo = "";
            int selectedId = rgMotivos.getCheckedRadioButtonId();
            
            if (selectedId == -1) {
                Toast.makeText(this, "Debes seleccionar un motivo", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedId == R.id.rb_perro_no_disponible) motivo = "Perro no disponible";
            else if (selectedId == R.id.rb_emergencia) motivo = "Emergencia con la mascota";
            else if (selectedId == R.id.rb_seguridad) motivo = "Problema de seguridad";
            else if (selectedId == R.id.rb_otro) motivo = etOtroMotivo.getText().toString();

            if (motivo.isEmpty()) {
                Toast.makeText(this, "Debes especificar el motivo", Toast.LENGTH_SHORT).show();
                return;
            }

            confirmarCancelacionDueno(motivo);
        });

        builder.setNegativeButton("Volver al paseo", null);
        builder.show();
    }

    private void confirmarCancelacionDueno(String motivo) {
        mostrarLoading(true);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("estado", "SOLICITUD_CANCELACION"); // Estado intermedio
        data.put("motivo_cancelacion", motivo);
        data.put("cancelado_por", "DUEÑO");
        data.put("fecha_solicitud_cancelacion", new Date());

        reservaRef.update(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Solicitud de cancelaci??n enviada al paseador.", Toast.LENGTH_LONG).show();
                    mostrarLoading(false);
                    // No cerramos la actividad inmediatamente, esperamos respuesta o actualizaci??n de estado
                })
                .addOnFailureListener(e -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "Error al enviar solicitud", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error cancelando", e);
                });
    }

    private long calcularTiempoTranscurrido() {
        if (fechaInicioPaseo == null) return 0;
        return Math.max(0, System.currentTimeMillis() - fechaInicioPaseo.getTime());
    }

    private void startTimer() {
        stopTimer();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed())
                    return;

                if (fechaInicioPaseo == null) {
                    tvHoras.setText("00");
                    tvMinutos.setText("00");
                    tvSegundos.setText("00");
                    pbProgresoPaseo.setProgress(0);
                    timerHandler.postDelayed(this, 1000);
                    return;
                }

                long elapsed = System.currentTimeMillis() - fechaInicioPaseo.getTime();
                long elapsedSeconds = elapsed / 1000;
                long hours = elapsedSeconds / 3600;
                long minutes = (elapsedSeconds % 3600) / 60;
                long seconds = elapsedSeconds % 60;

                tvHoras.setText(String.format(Locale.US, "%02d", hours));
                tvMinutos.setText(String.format(Locale.US, "%02d", minutes));
                tvSegundos.setText(String.format(Locale.US, "%02d", seconds));

                // Update progress bar
                if (duracionMinutos > 0) {
                    long totalMillis = duracionMinutos * 60 * 1000;
                    int progress = (int) ((elapsed * 100) / totalMillis);
                    pbProgresoPaseo.setProgress(Math.min(progress, 100));
                } else {
                    pbProgresoPaseo.setProgress(0);
                }

                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void mostrarOpcionesContacto() {
        if (telefonoPaseador == null || telefonoPaseador.isEmpty()) {
            Toast.makeText(this, "Telefono del paseador no disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] opciones = { "Chat", "Llamar", "WhatsApp", "SMS", "Cancelar" };
        new AlertDialog.Builder(this)
                .setTitle("Contactar a " + (nombrePaseador != null ? nombrePaseador : "Paseador"))
                .setItems(opciones, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            FirebaseUser user = auth.getCurrentUser();
                            if (user == null) {
                                Toast.makeText(this, "Inicia sesion para chatear", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (idPaseador == null) {
                                Toast.makeText(this, "No se pudo abrir el chat", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            com.mjc.mascotalink.util.ChatHelper.openOrCreateChat(this, db, user.getUid(), idPaseador);
                            break;
                        case 1:
                            intentarLlamar();
                            break;
                        case 2:
                            enviarWhatsApp(telefonoPaseador);
                            break;
                        case 3:
                            enviarSMS();
                            break;
                        case 4:
                            dialog.dismiss();
                            break;
                    }
                })
                .show();
    }

    private void intentarLlamar() {
        try {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + telefonoPaseador));
            startActivity(dialIntent);
        } catch (Exception e) {
            Toast.makeText(this, "No se puede realizar la llamada", Toast.LENGTH_SHORT).show();
        }
    }

    private void enviarWhatsApp(String telefono) {
        WhatsAppUtil.abrirWhatsApp(this, telefono, "Hola, tengo una consulta sobre el paseo.");
    }

    private void enviarSMS() {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + telefonoPaseador));
        intent.putExtra("sms_body", "Hola, tengo una consulta sobre el paseo.");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo enviar SMS", Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarFotoCompleta(String url) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_fullscreen_image, null);
        ShapeableImageView imageView = dialogView.findViewById(R.id.iv_fullscreen);
        Glide.with(this).load(MyApplication.getFixedUrl(url)).into(imageView);
        new AlertDialog.Builder(this).setView(dialogView).setPositiveButton("Cerrar", null).show();
    }

    private void mostrarLoading(boolean show) {
        pbLoading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CALL) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                intentarLlamar();
            } else {
                Toast.makeText(this, "Permiso de llamada denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
