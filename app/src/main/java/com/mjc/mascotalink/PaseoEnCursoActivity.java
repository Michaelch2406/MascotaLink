package com.mjc.mascotalink;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.mjc.mascotalink.adapters.FotosPaseoAdapter;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.util.FirebaseQueryOptimizer;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.network.SocketManager;
import com.mjc.mascotalink.network.NetworkMonitorHelper;

import com.mjc.mascotalink.util.WhatsAppUtil;

import com.mjc.mascotalink.service.LocationService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Maps & Graphics Imports
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PaseoEnCursoActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "PaseoEnCursoActivity";
    private static final int REQUEST_CODE_CAMERA = 2101;
    private static final int REQUEST_CODE_GALLERY = 2102;
    private static final int REQUEST_PERMISSION_CALL = 2201;
    private static final int REQUEST_PERMISSION_CAMERA = 2202;
    private static final int REQUEST_PERMISSION_LOCATION = 2203;
    private static final int SAVE_DELAY_MS = 500;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    @Inject
    FirebaseFirestore db;
    @Inject
    FirebaseAuth auth;
    private DocumentReference reservaRef;
    private FirebaseQueryOptimizer firebaseOptimizer;
    @Inject
    SocketManager socketManager;
    private NetworkMonitorHelper networkMonitor;

    private TextView tvNombreMascota;
    private TextView tvPaseador;
    private TextView tvFechaHora;
    private TextView tvHoras;
    private TextView tvMinutos;
    private TextView tvSegundos;
    private TextView tvEstado;
    private TextView tvUbicacionEstado;
    private com.google.android.material.imageview.ShapeableImageView ivFotoMascota;
    private com.mjc.mascotalink.views.OverlappingAvatarsView overlappingAvatars;
    private TextInputEditText etNotas;
    private RecyclerView rvFotos;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnContactar;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnCancelar;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnFinalizar;
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton btnComenzarPaseo;
    private MaterialButton btnAdjuntar;
    private BottomNavigationView bottomNav;
    private ProgressBar pbLoading;
    private ProgressBar pbProgresoPaseo;
    private TextView tvDistancia; // New: Distance TextView
    private androidx.core.widget.NestedScrollView scrollContainer; // New: Scroll Container

    private FotosPaseoAdapter fotosAdapter;

    // Map Variables
    private GoogleMap mMap;
    private Marker marcadorActual;
    private Marker marcadorInicio;
    private Polyline polylineRuta;
    private List<LatLng> rutaPaseo = new ArrayList<>();
    private double distanciaTotalMetros = 0.0;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private Runnable saveRunnable;
    private boolean isUpdatingNotesFromRemote = false;
    private android.location.Location lastSentLocation = null;
    private long lastSentAt = 0L;
    private long lastGoodLocationTime = 0; // Última vez que se obtuvo buena precisión
    private long lastFirestoreUpdate = 0L; // Última actualización a Firestore
    private static final long FIRESTORE_UPDATE_INTERVAL = 30000; // 30 segundos entre actualizaciones a Firestore
    private long lastMovementTime = System.currentTimeMillis();

    private Date fechaInicioPaseo;
    private long duracionMinutos = 0L;
    private String idReserva;
    private String contactoDueno;
    private String telefonoPendiente;
    private String nombreMascota = "";
    private String nombreDueno = "Dueño";
    private String roleActual = "PASEADOR";
    private String currentPaseadorNombre = ""; // New member variable

    private String mascotaIdActual;
    private String paseadorIdActual;
    private String duenoIdActual;

    // Flag para evitar que LocationService se inicie múltiples veces
    private boolean locationServiceStarted = false;

    // Ventana de tiempo para permitir inicio anticipado
    private static final long VENTANA_ANTICIPACION_MS = 15 * 60 * 1000; // 15 minutos




    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_paseo_en_curso);

        // db, auth, and socketManager are now injected by Hilt
        // db = FirebaseFirestore.getInstance();
        // auth = FirebaseAuth.getInstance();
        // socketManager = SocketManager.getInstance(this);

        // Inicializar FirebaseQueryOptimizer para lifecycle-aware listeners
        firebaseOptimizer = new FirebaseQueryOptimizer();

        // Inicializar NetworkMonitorHelper para monitoreo robusto de red
        networkMonitor = new NetworkMonitorHelper(this, socketManager, new NetworkMonitorHelper.NetworkCallback() {
            @Override
            public void onNetworkLost() {
                runOnUiThread(() -> actualizarEstadoUbicacion("Ubicación: sin red"));
            }

            @Override
            public void onNetworkAvailable() {
                Log.d(TAG, "Red disponible nuevamente");
            }

            @Override
            public void onReconnected() {
                Log.d(TAG, "Reconectado exitosamente");
            }
        });

        initViews();
        setupToolbar();

        idReserva = getIntent().getStringExtra("id_reserva");
        if (idReserva == null || idReserva.isEmpty()) {
            Toast.makeText(this, "Error: Reserva no encontrada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        reservaRef = db.collection("reservas").document(idReserva);

        // Conectar al WebSocket antes de unirse al paseo
        socketManager.connect();

        // Unirse al paseo vía WebSocket para streaming en tiempo real
        socketManager.joinPaseo(idReserva);

        // Configurar detección de cambios de red con NetworkMonitorHelper
        networkMonitor.setCurrentRoom(idReserva, NetworkMonitorHelper.RoomType.PASEO);
        networkMonitor.register();

        // Diferir operaciones no críticas para mejorar tiempo de inicio
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            setupMap();
            setupRecyclerView();
            setupNotesWatcher();
            setupButtons();
            cargarRoleYBottomNav();
            escucharReserva();
            setupLocationUpdates();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarRoleYBottomNav();
        if (fechaInicioPaseo != null) {
            startTimer();
        }
        startLocationUpdates();

        // Reconectar al WebSocket y unirse al paseo
        if (idReserva != null) {
            socketManager.connect();
            socketManager.joinPaseo(idReserva);
        }
    }

    private void initViews() {
        tvNombreMascota = findViewById(R.id.tv_nombre_mascota);
        tvPaseador = findViewById(R.id.tv_paseador);
        tvFechaHora = findViewById(R.id.tv_fecha_hora);
        tvHoras = findViewById(R.id.tv_horas);
        tvMinutos = findViewById(R.id.tv_minutos);
        tvSegundos = findViewById(R.id.tv_segundos);
        tvEstado = findViewById(R.id.tv_estado);
        tvUbicacionEstado = findViewById(R.id.tv_ubicacion_estado);
        tvDistancia = findViewById(R.id.tv_distancia); // Initialize
        ivFotoMascota = findViewById(R.id.iv_foto_mascota);
        overlappingAvatars = findViewById(R.id.overlapping_avatars);
        etNotas = findViewById(R.id.et_notas);
        rvFotos = findViewById(R.id.rv_fotos);
        btnContactar = findViewById(R.id.btn_contactar);
        btnCancelar = findViewById(R.id.btn_cancelar_paseo);
        btnFinalizar = findViewById(R.id.btn_finalizar_paseo);
        btnComenzarPaseo = findViewById(R.id.btn_comenzar_paseo);
        btnAdjuntar = findViewById(R.id.btn_adjuntar_fotos);
        bottomNav = findViewById(R.id.bottom_nav);
        pbLoading = findViewById(R.id.pb_loading);
        pbProgresoPaseo = findViewById(R.id.pb_progreso_paseo);
        scrollContainer = findViewById(R.id.scroll_container); // Initialize

        // Animación de escala en la tarjeta de mascota
        View cardMascota = findViewById(R.id.card_mascota);
        if (cardMascota != null) {
            cardMascota.setAlpha(0.0f);
            cardMascota.postDelayed(() -> {
                android.view.animation.Animation scaleUp = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.scale_up);
                cardMascota.startAnimation(scaleUp);
                cardMascota.setAlpha(1.0f);
            }, 200);
        }
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

    private void abrirMapaFullscreen() {
        if (lastSentLocation == null) {
            Toast.makeText(this, "Ubicacion no disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri gmmIntentUri = Uri.parse("geo:" + lastSentLocation.getLatitude() + "," + lastSentLocation.getLongitude() + "?q=" + lastSentLocation.getLatitude() + "," + lastSentLocation.getLongitude() + "(Mi Ubicación)");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Uri browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + lastSentLocation.getLatitude() + "," + lastSentLocation.getLongitude());
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
            startActivity(browserIntent);
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        fotosAdapter = new FotosPaseoAdapter(this, new FotosPaseoAdapter.OnFotoInteractionListener() {
            @Override
            public void onFotoClick(String url) {
                mostrarFotoCompleta(url);
            }

            @Override
            public void onFotoLongClick(String url) {
                mostrarDialogEliminarFoto(url);
            }
        });
        rvFotos.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvFotos.setAdapter(fotosAdapter);
    }

    private void setupNotesWatcher() {
        etNotas.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingNotesFromRemote) return;
                if (reservaRef == null) return;
                saveHandler.removeCallbacks(saveRunnable);
                saveRunnable = () -> guardarNotasEnFirebase(s.toString());
                saveHandler.postDelayed(saveRunnable, SAVE_DELAY_MS);
            }
        });
    }

    private void setupButtons() {
        btnContactar.setOnClickListener(v -> mostrarOpcionesContacto());
        btnCancelar.setOnClickListener(v -> iniciarProcesoCancelacion());
        btnFinalizar.setOnClickListener(v -> mostrarDialogoFinalizar());
        btnComenzarPaseo.setOnClickListener(v -> comenzarPaseoManualmente());
        btnAdjuntar.setOnClickListener(v -> mostrarOpcionesAdjuntar());

        // Click listeners for direct navigation
        ivFotoMascota.setOnClickListener(v -> navigateToPetProfile());
        tvNombreMascota.setOnClickListener(v -> navigateToPetProfile());
        tvPaseador.setOnClickListener(v -> navigateToOwnerProfile());
    }

    private void navigateToPetProfile() {
        if (mascotaIdActual != null && duenoIdActual != null && !mascotaIdActual.isEmpty() && !duenoIdActual.isEmpty()) {
            Intent intent = new Intent(this, PerfilMascotaActivity.class);
            intent.putExtra("mascota_id", mascotaIdActual);
            intent.putExtra("dueno_id", duenoIdActual); // Pass owner ID for context
            startActivity(intent);
        } else {
            Toast.makeText(this, "Información de la mascota no disponible.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Cannot navigate to pet profile: mascotaIdActual or duenoIdActual is null/empty.");
        }
    }

    private void navigateToOwnerProfile() {
        if (duenoIdActual != null && !duenoIdActual.isEmpty()) {
            Intent intent = new Intent(this, PerfilDuenoActivity.class);
            intent.putExtra("id_dueno", duenoIdActual);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Información del dueño no disponible.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Cannot navigate to owner profile: duenoIdActual is null/empty.");
        }
    }

    private void mostrarDialogoFinalizar() {
        new AlertDialog.Builder(this)
            .setTitle("Finalizar Paseo")
            .setMessage("¿Estás seguro de que deseas marcar el paseo como completado?")
            .setPositiveButton("Sí, finalizar", (dialog, which) -> confirmarFinalizacionExito())
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /**
     * Muestra la UI cuando el paseo está en estado LISTO_PARA_INICIAR
     * (esperando que el paseador lo inicie manualmente)
     */
    private void mostrarUIListoParaIniciar(@NonNull DocumentSnapshot snapshot) {
        // Mostrar botón "Comenzar Paseo" y ocultar otros botones
        btnComenzarPaseo.setVisibility(View.VISIBLE);
        btnFinalizar.setVisibility(View.GONE);
        btnContactar.setVisibility(View.VISIBLE); // Puede contactar antes de comenzar
        btnCancelar.setVisibility(View.VISIBLE); // Puede cancelar antes de comenzar

        // Cargar información básica del paseo
        Timestamp horaInicio = snapshot.getTimestamp("hora_inicio");
        if (horaInicio != null) {
            actualizarFechaHora(horaInicio.toDate());
        }

        Long duracion = snapshot.getLong("duracion_minutos");
        if (duracion != null) {
            duracionMinutos = duracion;
        }

        // Verificar si esta en ventana permitida para iniciar
        boolean dentroDeVentana = estaEnVentanaPermitida(horaInicio);

        if (dentroDeVentana) {
            // Puede iniciar ahora
            btnComenzarPaseo.setEnabled(true);
            btnComenzarPaseo.setText("Comenzar Paseo");
            btnComenzarPaseo.setAlpha(1.0f);
            tvEstado.setText("Listo para iniciar");
        } else {
            // Aun no puede iniciar
            btnComenzarPaseo.setEnabled(false);
            String horaPermitida = obtenerHoraPermitidaParaIniciar(horaInicio);
            btnComenzarPaseo.setText("Disponible a las " + horaPermitida);
            btnComenzarPaseo.setAlpha(0.6f);

            long minutosRestantes = calcularMinutosParaVentana(horaInicio);
            tvEstado.setText("Podras iniciar en " + minutosRestantes + " minutos");
        }

        // Cargar info de la mascota - soportar ambos formatos
        @SuppressWarnings("unchecked")
        List<String> mascotasNombres = (List<String>) snapshot.get("mascotas_nombres");
        @SuppressWarnings("unchecked")
        List<String> mascotasFotos = (List<String>) snapshot.get("mascotas_fotos");
        String mascotaId = snapshot.getString("id_mascota");

        if (mascotasNombres != null && !mascotasNombres.isEmpty()) {
            // Formato nuevo: múltiples mascotas
            nombreMascota = String.join(", ", mascotasNombres);
            tvNombreMascota.setText(nombreMascota);

            // Mostrar avatares superpuestos si hay fotos
            if (mascotasFotos != null && mascotasFotos.size() > 1) {
                overlappingAvatars.setVisibility(View.VISIBLE);
                ivFotoMascota.setVisibility(View.GONE);
                overlappingAvatars.setImageUrls(mascotasFotos);
            } else if (mascotasFotos != null && mascotasFotos.size() == 1) {
                overlappingAvatars.setVisibility(View.GONE);
                ivFotoMascota.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(MyApplication.getFixedUrl(mascotasFotos.get(0)))
                        .placeholder(R.drawable.ic_pet_placeholder)
                        .error(R.drawable.ic_pet_placeholder)
                        .into(ivFotoMascota);
            } else {
                overlappingAvatars.setVisibility(View.GONE);
                ivFotoMascota.setVisibility(View.VISIBLE);
                ivFotoMascota.setImageResource(R.drawable.ic_pet_placeholder);
            }
        } else if (mascotaId != null && !mascotaId.isEmpty()) {
            // Formato antiguo: una sola mascota
            overlappingAvatars.setVisibility(View.GONE);
            ivFotoMascota.setVisibility(View.VISIBLE);
            mascotaIdActual = mascotaId;
            cargarDatosMascota(mascotaId);
        }

        // Cargar info del dueño
        DocumentReference duenoRef = snapshot.getDocumentReference("id_dueno");
        if (duenoRef == null) {
            String duenoPath = snapshot.getString("id_dueno");
            if (duenoPath != null && !duenoPath.isEmpty()) {
                duenoRef = db.document(duenoPath);
            }
        }
        if (duenoRef != null) {
            duenoIdActual = duenoRef.getId();
            cargarDatosDueno(duenoRef);
        }

        // No iniciar timer ni tracking hasta que se pulse "Comenzar Paseo"
        stopTimer();
    }

    /**
     * Inicia el paseo manualmente cuando el paseador pulsa el botón "Comenzar Paseo"
     * Transición: LISTO_PARA_INICIAR -> EN_CURSO
     */
    private void comenzarPaseoManualmente() {
        // 1. Verificar ventana de tiempo
        reservaRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) return;

            Timestamp horaInicio = snapshot.getTimestamp("hora_inicio");
            if (!estaEnVentanaPermitida(horaInicio)) {
                long minutosRestantes = calcularMinutosParaVentana(horaInicio);
                Toast.makeText(this,
                    "Podrás iniciar el paseo en " + minutosRestantes + " minutos",
                    Toast.LENGTH_LONG).show();
                return;
            }

            // 2. Verificar permisos de ubicación
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                    .setTitle("Permisos de Ubicación Requeridos")
                    .setMessage("Para iniciar el paseo necesitamos acceso a tu ubicación en tiempo real.")
                    .setPositiveButton("Dar Permisos", (d, w) -> {
                        ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            REQUEST_PERMISSION_LOCATION);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
                return;
            }

            // 3. Verificar GPS activado
            android.location.LocationManager locationManager =
                (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
            boolean gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);

            if (!gpsEnabled) {
                new AlertDialog.Builder(this)
                    .setTitle("GPS Desactivado")
                    .setMessage("Por favor activa el GPS para iniciar el paseo y rastrear la ubicación.")
                    .setPositiveButton("Activar GPS", (d, w) -> {
                        Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
                return;
            }

            // 4. Todo OK - Mostrar confirmación final
            mostrarDialogoConfirmacionInicio();
        });
    }

    /**
     * Muestra el diálogo de confirmación final para iniciar el paseo
     */
    private void mostrarDialogoConfirmacionInicio() {
        new AlertDialog.Builder(this)
            .setTitle("Comenzar Paseo")
            .setMessage("¿Estás listo para comenzar el paseo ahora?")
            .setPositiveButton("Sí, comenzar", (dialog, which) -> {
                mostrarLoading(true);

                // Actualizar estado a EN_CURSO y establecer fecha_inicio_paseo
                Map<String, Object> updates = new HashMap<>();
                updates.put("estado", "EN_CURSO");
                updates.put("fecha_inicio_paseo", Timestamp.now());
                updates.put("actualizado_por_paseador", true);

                reservaRef.update(updates)
                    .addOnSuccessListener(unused -> {
                        Log.d(TAG, " Paseo iniciado manualmente - Notificando vía WebSocket");
                        Toast.makeText(this, "¡Paseo iniciado! Disfruta el recorrido.", Toast.LENGTH_SHORT).show();

                        // TalkBack: Anunciar inicio del paseo
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                            findViewById(android.R.id.content).announceForAccessibility("Paseo iniciado correctamente. El cronómetro está en marcha. Disfruta el recorrido con " + (tvNombreMascota != null ? tvNombreMascota.getText() : "la mascota"));
                        }

                        mostrarLoading(false);

                        // Enviar notificación explícita vía WebSocket al dueño
                        enviarNotificacionInicioPaseo();

                        // El listener de Firestore se encargará de actualizar la UI
                        // cuando detecte el cambio a EN_CURSO
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, " Error al iniciar paseo manualmente", e);
                        Toast.makeText(this, "Error al iniciar el paseo: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                        mostrarLoading(false);
                    });
            })
            .setNegativeButton("Aún no", null)
            .show();
    }

    /**
     * Envía notificación explícita vía WebSocket cuando el paseador inicia el paseo
     * Esto asegura que el dueño reciba la señal inmediatamente
     */
    private void enviarNotificacionInicioPaseo() {
        if (socketManager == null || !socketManager.isConnected()) {
            Log.w(TAG, " SocketManager no disponible o desconectado");
            return;
        }

        try {
            // Actualizar estado del paseo vía WebSocket
            socketManager.updatePaseoEstado(idReserva, "EN_CURSO");
            Log.d(TAG, "📡 Notificación de inicio enviada vía WebSocket - Paseo: " + idReserva);
        } catch (Exception e) {
            Log.e(TAG, " Error enviando notificación de inicio vía WebSocket", e);
        }
    }

    private void cargarRoleYBottomNav() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sesión expirada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        db.collection("usuarios").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    roleActual = doc != null ? doc.getString("rol") : "PASEADOR";
                    if (roleActual == null) roleActual = "PASEADOR";
                    BottomNavManager.setupBottomNav(this, bottomNav, roleActual, R.id.menu_walks);
                    if (doc != null) {
                        paseadorIdActual = doc.getId();
                        String nombre = doc.getString("nombre_display");
                        if (nombre != null && !nombre.isEmpty()) {
                            tvPaseador.setText(getString(R.string.paseo_en_curso_label_paseador, nombre));
                            currentPaseadorNombre = nombre; // Store the walker's name
                        }
                    }
                })
                .addOnFailureListener(e -> BottomNavManager.setupBottomNav(this, bottomNav, "PASEADOR", R.id.menu_walks));
    }





    private void escucharReserva() {
        mostrarLoading(true);

        firebaseOptimizer.addDocumentListener(this, reservaRef,
            new FirebaseQueryOptimizer.DocumentSnapshotCallback() {
                @Override
                public void onSuccess(DocumentSnapshot snapshot) {
                    if (snapshot == null || !snapshot.exists()) {
                        mostrarLoading(false);
                        Toast.makeText(PaseoEnCursoActivity.this,
                            "La reserva ya no está disponible", Toast.LENGTH_SHORT).show();
                        // Limpiar caché cuando la reserva ya no existe
                        /*
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            cacheManager.clearCache(user.getUid());
                        }
                        */
                        finish();
                        return;
                    }

                    // Guardar en caché para futuras aperturas rápidas
                    /*
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        cacheManager.saveReservaToCache(user.getUid(), snapshot);
                    }
                    */

                    manejarSnapshotReserva(snapshot);
                    mostrarLoading(false);
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error escuchando reserva", e);
                    mostrarLoading(false);
                    Toast.makeText(PaseoEnCursoActivity.this,
                        "Error al cargar el paseo", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void manejarSnapshotReserva(@NonNull DocumentSnapshot snapshot) {
        String estado = snapshot.getString("estado");
        if (estado == null) estado = "";

        // Auto-transicion de CONFIRMADO a LISTO_PARA_INICIAR si ya paso la hora
        if ("CONFIRMADO".equalsIgnoreCase(estado)) {
            Timestamp horaInicio = snapshot.getTimestamp("hora_inicio");
            if (horaInicio != null) {
                long ahora = System.currentTimeMillis();
                long horaProgramadaMs = horaInicio.toDate().getTime();
                long horaMinPermitidaMs = horaProgramadaMs - VENTANA_ANTICIPACION_MS;

                // Si estamos dentro de la ventana de 15 minutos antes o ya paso la hora
                if (ahora >= horaMinPermitidaMs) {
                    // Si ya paso la hora programada, auto-transicionar a LISTO_PARA_INICIAR
                    if (ahora >= horaProgramadaMs) {
                        Log.d(TAG, "Auto-transicionando de CONFIRMADO a LISTO_PARA_INICIAR");
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("estado", "LISTO_PARA_INICIAR");
                        updates.put("hasTransitionedToReady", true);
                        updates.put("actualizado_por_sistema", true);
                        updates.put("last_updated", Timestamp.now());

                        reservaRef.update(updates)
                            .addOnSuccessListener(unused -> {
                                Log.d(TAG, "Paseo transicionado a LISTO_PARA_INICIAR exitosamente");
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error al transicionar a LISTO_PARA_INICIAR", e);
                            });

                        // No continuar procesando, esperar a que el listener detecte el cambio
                        return;
                    } else {
                        // Estamos dentro de la ventana de 15 minutos pero aun no paso la hora
                        // Mostrar UI como si fuera LISTO_PARA_INICIAR (el boton estara deshabilitado hasta que sea tiempo)
                        Log.d(TAG, "Dentro de ventana de 15 minutos, mostrando UI de inicio");
                        mostrarUIListoParaIniciar(snapshot);
                        return;
                    }
                }
            }
        }

        // --- FIX: Manejo de Solicitud de Cancelación Bilateral ---
        if ("SOLICITUD_CANCELACION".equalsIgnoreCase(estado)) {
            String motivo = snapshot.getString("motivo_cancelacion");
            mostrarDialogoSolicitudCancelacion(motivo != null ? motivo : "Sin motivo especificado");
            return; // Detener actualización de UI normal mientras se resuelve
        }
        // ---------------------------------------------------------

        // Manejo del nuevo estado LISTO_PARA_INICIAR
        if ("LISTO_PARA_INICIAR".equalsIgnoreCase(estado)) {
            mostrarUIListoParaIniciar(snapshot);
            return;
        }

        //vibe-fix: Validar transiciones de estado cuando el paseo se completa
        if (!estado.equalsIgnoreCase("EN_CURSO")) {
            if (estado.equalsIgnoreCase("COMPLETADO")) {
                // Si el paseo fue completado (por acción propia o remota), cerrar la actividad
                Log.d(TAG, "Paseo completado, cerrando actividad");
                Toast.makeText(this, "El paseo ha sido completado.", Toast.LENGTH_SHORT).show();
                stopTimer();
                // Resetear flag para próximos paseos
                locationServiceStarted = false;
                // Update en_paseo to false when walk finishes, before activity closes
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
            } else {
                // Otro estado (CANCELADO, etc.)
                Toast.makeText(this, "Este paseo ya no está en curso.", Toast.LENGTH_SHORT).show();
                stopTimer();
                // Resetear flag para próximos paseos
                locationServiceStarted = false;
                // Update en_paseo to false if the walk is ending for any reason
                finish();
            }
            return;
        }
        tvEstado.setText(getString(R.string.paseo_en_curso_state_en_progreso));

        // 🔴 CRÍTICO: Iniciar LocationService cuando estado cambia a EN_CURSO
        // Esto asegura que se comience a grabar ubicaciones INMEDIATAMENTE
        // cuando el paseador hace clic en "Comenzar" (no esperar a salir/entrar app)
        // El check de duplicados está en startLocationService()
        Log.d(TAG, "✅ Estado EN_CURSO detectado - Iniciando LocationService");
        startLocationUpdates();

        // Ocultar botón "Comenzar Paseo" cuando ya está en curso
        btnComenzarPaseo.setVisibility(View.GONE);

        Timestamp inicioTimestamp = snapshot.getTimestamp("fecha_inicio_paseo");
        if (inicioTimestamp == null) {
            inicioTimestamp = snapshot.getTimestamp("hora_inicio");
        }
        if (inicioTimestamp != null) {
            Date nuevaFecha = inicioTimestamp.toDate();
            boolean reiniciarTimer = fechaInicioPaseo == null || fechaInicioPaseo.getTime() != nuevaFecha.getTime();
            fechaInicioPaseo = nuevaFecha;
            if (reiniciarTimer) {
                startTimer();
            }
            actualizarFechaHora(inicioTimestamp.toDate());
        }

        Long duracion = snapshot.getLong("duracion_minutos");
        if (duracion != null) {
            duracionMinutos = duracion;
            actualizarFechaHora(inicioTimestamp != null ? inicioTimestamp.toDate() : null);
        }

        String notas = snapshot.getString("notas_paseador");
        actualizarNotasDesdeServidor(notas);

        Object contactoEmergencia = snapshot.get("contacto_emergencia");
        if (contactoEmergencia instanceof String && !((String) contactoEmergencia).isEmpty()) {
            contactoDueno = (String) contactoEmergencia;
        }

        // Safely cast fotos_paseo to List<String>
        Object fotosObj = snapshot.get("fotos_paseo");
        List<String> fotos = new ArrayList<>();
        if (fotosObj instanceof List) {
            for (Object item : (List<?>) fotosObj) {
                if (item instanceof String) {
                    fotos.add((String) item);
                } else {
                    Log.w(TAG, "Unexpected item type in fotos_paseo: " + (item != null ? item.getClass().getName() : "null"));
                }
            }
        } else if (fotosObj != null) {
            Log.w(TAG, "fotos_paseo is not a List: " + fotosObj.getClass().getName());
        }
        actualizarGaleria(fotos);

        DocumentReference duenoRef = snapshot.getDocumentReference("id_dueno");
        if (duenoRef == null) {
            String duenoPath = snapshot.getString("id_dueno");
            if (duenoPath != null && !duenoPath.isEmpty()) {
                duenoRef = db.document(duenoPath);
            }
        }
        if (duenoRef != null) {
            String nuevoDuenoId = duenoRef.getId();
            if (!nuevoDuenoId.equals(duenoIdActual)) {
                duenoIdActual = nuevoDuenoId;
                cargarDatosDueno(duenoRef);
                // Si ya tenemos una mascota identificada, recargar sus datos ahora que tenemos el contexto del dueño
                if (mascotaIdActual != null && !mascotaIdActual.isEmpty()) {
                    cargarDatosMascota(mascotaIdActual);
                }
            }
        }

        // Cargar info de la mascota - soportar ambos formatos
        @SuppressWarnings("unchecked")
        List<String> mascotasNombresListener = (List<String>) snapshot.get("mascotas_nombres");
        @SuppressWarnings("unchecked")
        List<String> mascotasFotosListener = (List<String>) snapshot.get("mascotas_fotos");
        String mascotaId = snapshot.getString("id_mascota");

        if (mascotasNombresListener != null && !mascotasNombresListener.isEmpty()) {
            // Formato nuevo: múltiples mascotas
            String nuevosNombres = String.join(", ", mascotasNombresListener);
            if (nombreMascota == null || !nuevosNombres.equals(nombreMascota)) {
                nombreMascota = nuevosNombres;
                tvNombreMascota.setText(nombreMascota);

                // Mostrar avatares superpuestos si hay fotos
                if (mascotasFotosListener != null && mascotasFotosListener.size() > 1) {
                    overlappingAvatars.setVisibility(View.VISIBLE);
                    ivFotoMascota.setVisibility(View.GONE);
                    overlappingAvatars.setImageUrls(mascotasFotosListener);
                } else if (mascotasFotosListener != null && mascotasFotosListener.size() == 1) {
                    overlappingAvatars.setVisibility(View.GONE);
                    ivFotoMascota.setVisibility(View.VISIBLE);
                    Glide.with(this)
                            .load(MyApplication.getFixedUrl(mascotasFotosListener.get(0)))
                            .placeholder(R.drawable.ic_pet_placeholder)
                            .error(R.drawable.ic_pet_placeholder)
                            .into(ivFotoMascota);
                } else {
                    overlappingAvatars.setVisibility(View.GONE);
                    ivFotoMascota.setVisibility(View.VISIBLE);
                    ivFotoMascota.setImageResource(R.drawable.ic_pet_placeholder);
                }
            }
        } else if (mascotaId != null && !mascotaId.isEmpty() && !mascotaId.equals(mascotaIdActual)) {
            // Formato antiguo: una sola mascota
            overlappingAvatars.setVisibility(View.GONE);
            ivFotoMascota.setVisibility(View.VISIBLE);
            mascotaIdActual = mascotaId;
            cargarDatosMascota(mascotaId);
        }

        DocumentReference paseadorRef = snapshot.getDocumentReference("id_paseador");
        if (paseadorRef == null) {
            String paseadorPath = snapshot.getString("id_paseador");
            if (paseadorPath != null && !paseadorPath.isEmpty()) {
                paseadorRef = db.document(paseadorPath);
            }
        }
        if (paseadorRef != null) {
            String nuevoPaseadorId = paseadorRef.getId();
            if (!nuevoPaseadorId.equals(paseadorIdActual)) {
                paseadorIdActual = nuevoPaseadorId;
                cargarDatosPaseador(paseadorRef);
            }
        }

        // ===== CARGAR DISTANCIA GUARDADA =====
        cargarDistanciaGuardada(snapshot);

        // ===== CARGAR RECORRIDO GUARDADO (UBICACIONES) =====
        cargarRecorridoGuardado(snapshot);
    }

    private void cargarDatosMascota(String mascotaId) {
        if (duenoIdActual == null || duenoIdActual.isEmpty()) {
            Log.w(TAG, "duenoIdActual es nulo o vacío. Intentando cargar mascota desde colección global.");
            cargarMascotaDesdeColeccionGlobal(mascotaId);
            return;
        }

        // First, try to load from the subcollection within the owner's document
        db.collection("duenos").document(duenoIdActual).collection("mascotas").document(mascotaId).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        processMascotaDocument(doc);
                    } else {
                        cargarMascotaDesdeColeccionGlobal(mascotaId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando mascota desde subcolección", e);
                    cargarMascotaDesdeColeccionGlobal(mascotaId);
                });
    }

    private void cargarMascotaDesdeColeccionGlobal(String mascotaId) {
        db.collection("mascotas").document(mascotaId).get()
                .addOnSuccessListener(globalDoc -> {
                    if (globalDoc != null && globalDoc.exists()) {
                        processMascotaDocument(globalDoc);
                    } else {
                        Log.w(TAG, "Mascota document not found in subcollection or top-level collection for ID: " + mascotaId);
                        tvNombreMascota.setText("Mascota");
                        ivFotoMascota.setImageResource(R.drawable.ic_pet_placeholder);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error cargando mascota desde colección global", e));
    }

    private void processMascotaDocument(@NonNull DocumentSnapshot doc) {
        nombreMascota = doc.getString("nombre") != null ? doc.getString("nombre") : "Mascota";
        tvNombreMascota.setText(nombreMascota);
        
        String urlFoto = doc.getString("foto_url");
        String urlFotoPrincipal = doc.getString("foto_principal_url");
        
        Log.d(TAG, "Procesando mascota ID: " + doc.getId());
        Log.d(TAG, " - Nombre: " + nombreMascota);
        Log.d(TAG, " - foto_url: " + urlFoto);
        Log.d(TAG, " - foto_principal_url: " + urlFotoPrincipal);

        if (urlFoto == null || urlFoto.isEmpty()) {
            urlFoto = urlFotoPrincipal;
        }
        
        if (urlFoto != null && !urlFoto.isEmpty()) {
            Log.d(TAG, "Intentando cargar foto con Glide: " + urlFoto);
            if (!isDestroyed() && !isFinishing()) {
                Glide.with(this)
                        .load(MyApplication.getFixedUrl(urlFoto))
                        .placeholder(R.drawable.ic_pet_placeholder)
                        .error(R.drawable.ic_pet_placeholder)
                        .into(ivFotoMascota);
            }
        } else {
            Log.w(TAG, "No se encontró URL de foto válida para la mascota.");
            ivFotoMascota.setImageResource(R.drawable.ic_pet_placeholder);
        }
    }

    private void cargarDatosDueno(DocumentReference duenoRef) {
        duenoRef.get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;
                    String telefono = doc.getString("telefono");
                    if (telefono != null && !telefono.isEmpty()) {
                        contactoDueno = telefono;
                    }
                    // Guardar nombre para nombre de archivo
                    String nombre = doc.getString("nombre_display");
                    if (nombre != null && !nombre.isEmpty()) {
                        nombreDueno = nombre.replace(" ", "_"); // Sanitizar para nombre de archivo
                        // Mostrar nombre del dueño en la UI (reutilizando tvPaseador)
                        tvPaseador.setText("Dueño: " + nombre);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error cargando datos del dueño", e));
    }

    private void cargarDatosPaseador(DocumentReference paseadorRef) {
        paseadorRef.get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;
                    String nombre = doc.getString("nombre_display");
                    if (nombre == null || nombre.isEmpty()) {
                        nombre = doc.getString("nombre");
                    }
                    // Ya no mostramos el nombre del paseador aquí, preferimos el dueño
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error cargando paseo", e));
    }

    /**
     * Carga la distancia guardada desde Firestore
     * Esto permite continuar desde donde quedó si se cerró la app
     */
    private void cargarDistanciaGuardada(@NonNull DocumentSnapshot snapshot) {
        Object distanciaObj = snapshot.get("distancia_acumulada_metros");
        if (distanciaObj instanceof Number) {
            distanciaTotalMetros = ((Number) distanciaObj).doubleValue();
            if (tvDistancia != null) {
                tvDistancia.setText(String.format(Locale.getDefault(), "Distancia: %.2f km", distanciaTotalMetros / 1000));
            }
            Log.d(TAG, "📏 Distancia cargada desde Firestore: " + String.format("%.2f", distanciaTotalMetros / 1000) + " km");
        } else {
            // Si no existe, inicializar en 0
            distanciaTotalMetros = 0.0;
        }
    }

    /**
     * Carga el recorrido guardado (polyline) desde Firestore
     * Esto permite mostrar el recorrido completo al reabrir la app
     */
    private void cargarRecorridoGuardado(@NonNull DocumentSnapshot snapshot) {
        Object ubicacionesObj = snapshot.get("ubicaciones");
        if (ubicacionesObj instanceof List) {
            List<?> ubicacionesList = (List<?>) ubicacionesObj;

            // Limpiar recorrido actual
            rutaPaseo.clear();

            // Reconstruir polyline desde ubicaciones guardadas
            for (Object ubicObj : ubicacionesList) {
                if (ubicObj instanceof Map) {
                    Map<?, ?> ubicMap = (Map<?, ?>) ubicObj;
                    Object latObj = ubicMap.get("lat");
                    Object lngObj = ubicMap.get("lng");

                    if (latObj instanceof Number && lngObj instanceof Number) {
                        double lat = ((Number) latObj).doubleValue();
                        double lng = ((Number) lngObj).doubleValue();
                        rutaPaseo.add(new LatLng(lat, lng));
                    }
                }
            }

            Log.d(TAG, "📍 Recorrido cargado: " + rutaPaseo.size() + " puntos");

            // Actualizar mapa si ya está listo
            if (mMap != null && !rutaPaseo.isEmpty()) {
                // Redibujar polyline completa
                if (polylineRuta != null) {
                    polylineRuta.setPoints(rutaPaseo);
                }

                // Marcar inicio
                if (marcadorInicio == null) {
                    marcadorInicio = mMap.addMarker(new MarkerOptions()
                            .position(rutaPaseo.get(0))
                            .title("Inicio")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                }

                // Centrar en última ubicación
                LatLng ultimaUbicacion = rutaPaseo.get(rutaPaseo.size() - 1);
                actualizarMapa(ultimaUbicacion);
            }
        }
    }

    private void actualizarFechaHora(@Nullable Date inicio) {
        if (inicio == null) {
            tvFechaHora.setText("");
            return;
        }
        Date fin = new Date(inicio.getTime() + TimeUnit.MINUTES.toMillis(Math.max(duracionMinutos, 0)));
        SimpleDateFormat fechaFormat = new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES"));
        SimpleDateFormat horaFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        String texto = fechaFormat.format(inicio) + ", " + horaFormat.format(inicio) + " - " + horaFormat.format(fin);
        tvFechaHora.setText(texto);
    }

    private void actualizarNotasDesdeServidor(@Nullable String notas) {
        isUpdatingNotesFromRemote = true;
        if (notas == null) notas = "";
        if (!notas.equals(etNotas.getText() != null ? etNotas.getText().toString() : "")) {
            etNotas.setText(notas);
            if (notas.length() > 0) {
                etNotas.setSelection(notas.length());
            }
        }
        isUpdatingNotesFromRemote = false;
    }

    private void actualizarGaleria(@Nullable List<String> fotos) {
        if (fotos == null || fotos.isEmpty()) {
            rvFotos.setVisibility(View.GONE);
            fotosAdapter.submitList(new ArrayList<>());
        } else {
            rvFotos.setVisibility(View.VISIBLE);
            fotosAdapter.submitList(fotos);
        }
    }

    private void guardarNotasEnFirebase(String notas) {
        reservaRef.update("notas_paseador", notas)
                .addOnFailureListener(e -> Log.e(TAG, "Error guardando notas", e));
    }

    private void mostrarOpcionesAdjuntar() {
        final String[] opciones = {"Cámara", "Galería"};
        new AlertDialog.Builder(this)
                .setTitle("Adjuntar fotos")
                .setItems(opciones, (dialog, which) -> {
                    if (which == 0) {
                        intentarAbrirCamara();
                    } else {
                        abrirGaleria();
                    }
                })
                .show();
    }

    private void intentarAbrirCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION_CAMERA);
        } else {
            abrirCamara();
        }
    }

    private void abrirCamara() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CODE_CAMERA);
        } else {
            Toast.makeText(this, "No hay cámara disponible", Toast.LENGTH_SHORT).show();
        }
    }

    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_CODE_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQUEST_CODE_CAMERA) {
            Bitmap foto = (Bitmap) data.getParcelableExtra("data");
            if (foto != null) {
                subirFotoAFirebase(foto);
            }
        } else if (requestCode == REQUEST_CODE_GALLERY) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    ContentResolver resolver = getContentResolver();
                    InputStream inputStream = resolver.openInputStream(uri);
                    if (inputStream != null) {
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        inputStream.close();
                        if (bitmap != null) {
                            subirFotoAFirebase(bitmap);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error leyendo imagen de galería", e);
                    Toast.makeText(this, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void subirFotoAFirebase(Bitmap foto) {
        mostrarLoading(true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        foto.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] data = baos.toByteArray();

        //vibe-fix: Validar tamaño de foto < 5MB antes de subir
        final long MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
        if (data.length > MAX_SIZE_BYTES) {
            mostrarLoading(false);
            double sizeMB = data.length / (1024.0 * 1024.0);
            String mensaje = String.format(Locale.getDefault(), 
                "La imagen es demasiado grande (%.2f MB). El tamaño máximo permitido es 5 MB. Por favor, selecciona una imagen más pequeña.", 
                sizeMB);
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
            Log.w(TAG, "Intento de subir foto mayor a 5MB: " + sizeMB + " MB");
            return;
        }

        // Formato descriptivo: Mascota_Dueno_FechaHora.jpg
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String fechaHora = sdf.format(new Date());
        String nombreMascotaSanitizado = nombreMascota.replace(" ", "_");
        
        String nombreArchivo = String.format("%s_%s_%s.jpg", nombreMascotaSanitizado, nombreDueno, fechaHora);
        
        StorageReference ref = FirebaseStorage.getInstance().getReference("paseos/" + idReserva + "/" + nombreArchivo);

        ref.putBytes(data)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> agregarFotoAlArray(uri.toString()))
                .addOnFailureListener(e -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "Error al subir foto", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error subiendo foto", e);
                });
    }

    private void agregarFotoAlArray(String url) {
        Map<String, Object> nuevaActividad = new HashMap<>();
        nuevaActividad.put("evento", "FOTO_SUBIDA");
        nuevaActividad.put("descripcion", "El paseador ha subido una nueva foto");
        nuevaActividad.put("timestamp", new Date());

        // Usar retry helper para operación crítica
        com.mjc.mascotalink.util.FirestoreRetryHelper.execute(
                () -> reservaRef.update(
                        "fotos_paseo", FieldValue.arrayUnion(url),
                        "actividad", FieldValue.arrayUnion(nuevaActividad)
                ),
                unused -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "Foto guardada", Toast.LENGTH_SHORT).show();
                },
                e -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "No se pudo guardar la foto después de varios intentos", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error guardando foto después de reintentos", e);
                },
                3  // 3 reintentos para operación importante
        );
    }

    private void mostrarDialogEliminarFoto(String url) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar foto")
                .setMessage("¿Deseas eliminar esta foto del paseo?")
                .setPositiveButton("Eliminar", (dialog, which) -> eliminarFoto(url))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void eliminarFoto(String url) {
        reservaRef.update("fotos_paseo", FieldValue.arrayRemove(url))
                .addOnFailureListener(e -> Log.e(TAG, "No se pudo eliminar la foto", e));
    }

    private void mostrarFotoCompleta(String url) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_fullscreen_image, null);
        ImageView imageView = dialogView.findViewById(R.id.iv_fullscreen);
        Glide.with(this)
                .load(MyApplication.getFixedUrl(url))
                .placeholder(R.drawable.ic_pet_placeholder)
                .error(R.drawable.ic_pet_placeholder)
                .into(imageView);
        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void mostrarOpcionesContacto() {
        if (contactoDueno == null || contactoDueno.isEmpty()) {
            Toast.makeText(this, "Contacto no disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_contactar_persona, null);
        builder.setView(view);

        LinearLayout optionChat = view.findViewById(R.id.option_chat);
        LinearLayout optionCall = view.findViewById(R.id.option_call);
        LinearLayout optionWhatsApp = view.findViewById(R.id.option_whatsapp);
        LinearLayout optionSms = view.findViewById(R.id.option_sms);
        MaterialButton btnCerrar = view.findViewById(R.id.btn_cerrar_dialog);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        optionChat.setOnClickListener(v -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "Inicia sesion para chatear", Toast.LENGTH_SHORT).show();
                return;
            }
            if (duenoIdActual == null) {
                Toast.makeText(this, "No se pudo abrir el chat", Toast.LENGTH_SHORT).show();
                return;
            }
            com.mjc.mascotalink.util.ChatHelper.openOrCreateChat(this, db, user.getUid(), duenoIdActual);
            dialog.dismiss();
        });

        optionCall.setOnClickListener(v -> {
            intentarLlamar(contactoDueno);
            dialog.dismiss();
        });

        optionWhatsApp.setOnClickListener(v -> {
            enviarWhatsApp(contactoDueno);
            dialog.dismiss();
        });

        optionSms.setOnClickListener(v -> {
            enviarSms(contactoDueno);
            dialog.dismiss();
        });

        btnCerrar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void intentarLlamar(String telefono) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            telefonoPendiente = telefono;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, REQUEST_PERMISSION_CALL);
        } else {
            realizarLlamada(telefono);
        }
    }

    private void realizarLlamada(String telefono) {
        if (telefono == null || telefono.isEmpty()) {
            Toast.makeText(this, "Numero no disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + telefono));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo iniciar la llamada", Toast.LENGTH_SHORT).show();
        }
    }

    private void enviarWhatsApp(String telefono) {
        String mensaje = String.format(Locale.getDefault(),
                "¡Hola! Soy %s, el paseador a cargo de %s el día de hoy.",
                currentPaseadorNombre, nombreMascota);
        WhatsAppUtil.abrirWhatsApp(this, telefono, mensaje);
    }

    private void enviarSms(String telefono) {
        String mensaje = String.format(Locale.getDefault(),
                "¡Hola! Soy %s, el paseador a cargo de %s el día de hoy.",
                currentPaseadorNombre, nombreMascota);
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + telefono));
        intent.putExtra("sms_body", mensaje);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo enviar SMS", Toast.LENGTH_SHORT).show();
        }
    }

    private void iniciarProcesoCancelacion() {
        if (fechaInicioPaseo == null) {
            Toast.makeText(this, "El paseo aún no ha iniciado correctamente", Toast.LENGTH_SHORT).show();
            return;
        }

        long tiempoTranscurrido = calcularTiempoTranscurrido();
        // Regla: Bloquear cancelación antes de 10 minutos (600,000 ms)
        if (tiempoTranscurrido < 10 * 60 * 1000) {
            long minutosRestantes = 10 - TimeUnit.MILLISECONDS.toMinutes(tiempoTranscurrido);
            Toast.makeText(this, "Debes esperar " + minutosRestantes + " minutos más para cancelar.", Toast.LENGTH_LONG).show();
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
        TextInputEditText etOtroMotivo = view.findViewById(R.id.et_otro_motivo);
        MaterialButton btnConfirmar = view.findViewById(R.id.btn_confirmar_cancelacion);
        MaterialButton btnVolver = view.findViewById(R.id.btn_volver_paseo);

        rgMotivos.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_otro) {
                etOtroMotivo.setVisibility(View.VISIBLE);
            } else {
                etOtroMotivo.setVisibility(View.GONE);
            }
        });

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnConfirmar.setOnClickListener(v -> {
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

            dialog.dismiss();
            confirmarCancelacionPaseador(motivo);
        });

        btnVolver.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void confirmarCancelacionPaseador(String motivo) {
        mostrarLoading(true);
        Map<String, Object> data = new HashMap<>();
        data.put("estado", "CANCELADO"); // Estado final para que el sistema lo reconozca como terminado
        data.put("sub_estado", "CANCELADO_PASEADOR"); // Detalle para lógica interna
        data.put("motivo_cancelacion", motivo);
        data.put("cancelado_por", "PASEADOR");
        data.put("fecha_fin_paseo", new Date());

        reservaRef.update(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Paseo cancelado.", Toast.LENGTH_SHORT).show();
                    mostrarLoading(false);
                    stopLocationService(); // Stop tracking immediately
                    new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1000);
                })
                .addOnFailureListener(e -> {
                    mostrarLoading(false);
                    Toast.makeText(this, "Error al cancelar paseo", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error cancelando paseo", e);
                });
    }

    private void confirmarFinalizacionExito() {
        mostrarLoading(true);
        Map<String, Object> data = new HashMap<>();
        data.put("estado", "COMPLETADO");
        data.put("fecha_fin_paseo", new Date());
        data.put("tiempo_total_minutos", TimeUnit.MILLISECONDS.toMinutes(calcularTiempoTranscurrido()));

        // Usar retry helper para operación MUY crítica (finalización de paseo)
        com.mjc.mascotalink.util.FirestoreRetryHelper.executeCritical(
            () -> reservaRef.update(data),
            unused -> {
                Toast.makeText(this, "¡Paseo finalizado con éxito!", Toast.LENGTH_SHORT).show();
                mostrarLoading(false);
                stopLocationService(); // Stop tracking immediately

                Intent intent = new Intent(PaseoEnCursoActivity.this, ResumenPaseoActivity.class);
                intent.putExtra("id_reserva", idReserva);
                startActivity(intent);
                finish();
            },
            e -> {
                mostrarLoading(false);
                Toast.makeText(this, "Error al finalizar paseo después de varios intentos. Verifica tu conexión.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Error crítico finalizando paseo", e);
            }
        );
    }

    private void mostrarDialogoSolicitudCancelacion(String motivo) {
        if (isFinishing()) return;

        new AlertDialog.Builder(this)
                .setTitle("Solicitud de cancelación")
                .setMessage("El dueño ha solicitado cancelar el paseo.\n\nMotivo: " + motivo)
                .setCancelable(false)
                .setPositiveButton("Aceptar cancelación", (dialog, which) -> aceptarCancelacionMutua())
                .setNegativeButton("Rechazar / Continuar", (dialog, which) -> rechazarCancelacion())
                .show();
    }

    private void aceptarCancelacionMutua() {
        mostrarLoading(true);
        Map<String, Object> data = new HashMap<>();
        data.put("estado", "CANCELADO");
        data.put("sub_estado", "CANCELADO_MUTUO");
        data.put("fecha_fin_paseo", new Date());

        // Usar retry helper para operación crítica (cancelación mutua requiere confirmación)
        com.mjc.mascotalink.util.FirestoreRetryHelper.executeCritical(
            () -> reservaRef.update(data),
            unused -> {
                Toast.makeText(this, "Paseo cancelado mutuamente.", Toast.LENGTH_SHORT).show();
                mostrarLoading(false);
                finish();
            },
            e -> {
                mostrarLoading(false);
                Toast.makeText(this, "Error al aceptar cancelación después de varios intentos. Verifica tu conexión.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Error crítico aceptando cancelación mutua", e);
            }
        );
    }

    private void rechazarCancelacion() {
        mostrarLoading(true);
        // Volver al estado activo
        // Usar retry helper para operación crítica (cambio de estado de paseo)
        com.mjc.mascotalink.util.FirestoreRetryHelper.executeCritical(
            () -> reservaRef.update("estado", "EN_CURSO"),
            unused -> {
                Toast.makeText(this, "Cancelación rechazada. El paseo continúa.", Toast.LENGTH_SHORT).show();
                mostrarLoading(false);
            },
            e -> {
                mostrarLoading(false);
                Toast.makeText(this, "Error al rechazar cancelación después de varios intentos.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Error crítico rechazando cancelación", e);
            }
        );
    }

    private long calcularTiempoTranscurrido() {
        if (fechaInicioPaseo == null) return 0;
        return Math.max(0, System.currentTimeMillis() - fechaInicioPaseo.getTime());
    }

    private long obtenerTiempoMinimo() {
        long duracionReal = duracionMinutos > 0 ? duracionMinutos : 60; // Si es 0, asumir 60 minutos por seguridad
        return (long) (duracionReal * 60000 * 0.5f);
    }

    /**
     * Verifica si el paseador esta dentro de la ventana permitida para iniciar el paseo
     * Permitido: 15 minutos antes de la hora programada hasta sin limite despues
     */
    private boolean estaEnVentanaPermitida(Timestamp horaInicio) {
        if (horaInicio == null) return true; // Si no hay hora, permitir

        long ahora = System.currentTimeMillis();
        long horaProgramadaMs = horaInicio.toDate().getTime();
        long horaMinPermitidaMs = horaProgramadaMs - VENTANA_ANTICIPACION_MS;

        return ahora >= horaMinPermitidaMs;
    }

    /**
     * Calcula cuantos minutos faltan para poder iniciar el paseo
     */
    private long calcularMinutosParaVentana(Timestamp horaInicio) {
        if (horaInicio == null) return 0;

        long ahora = System.currentTimeMillis();
        long horaProgramadaMs = horaInicio.toDate().getTime();
        long horaMinPermitidaMs = horaProgramadaMs - VENTANA_ANTICIPACION_MS;
        long diferencia = horaMinPermitidaMs - ahora;

        return Math.max(0, TimeUnit.MILLISECONDS.toMinutes(diferencia));
    }

    /**
     * Obtiene la hora a partir de la cual se puede iniciar el paseo (15 min antes)
     */
    private String obtenerHoraPermitidaParaIniciar(Timestamp horaInicio) {
        if (horaInicio == null) return "";

        long horaProgramadaMs = horaInicio.toDate().getTime();
        long horaMinPermitidaMs = horaProgramadaMs - VENTANA_ANTICIPACION_MS;
        Date horaPermitida = new Date(horaMinPermitidaMs);

        SimpleDateFormat horaFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        return horaFormat.format(horaPermitida);
    }

    private void startTimer() {
        stopTimer();
        //vibe-fix: Validar fecha_inicio_paseo NULL antes de iniciar temporizador
        if (fechaInicioPaseo == null) {
            Log.w(TAG, "startTimer: fechaInicioPaseo es null, cancelando temporizador");
            Toast.makeText(this, "Error: No se pudo obtener la fecha de inicio del paseo", Toast.LENGTH_LONG).show();
            tvHoras.setText("00");
            tvMinutos.setText("00");
            tvSegundos.setText("00");
            return;
        }
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                //vibe-fix: Validar fecha_inicio_paseo NULL en cada tick del temporizador
                if (isFinishing() || isDestroyed()) {
                    stopTimer();
                    return;
                }
                if (fechaInicioPaseo == null) {
                    Log.w(TAG, "Timer tick: fechaInicioPaseo es null, deteniendo temporizador");
                    stopTimer();
                    runOnUiThread(() -> {
                        Toast.makeText(PaseoEnCursoActivity.this, 
                            "Error: La fecha de inicio del paseo no está disponible", 
                            Toast.LENGTH_LONG).show();
                        tvHoras.setText("00");
                        tvMinutos.setText("00");
                        tvSegundos.setText("00");
                    });
                    return;
                }
                long elapsed = calcularTiempoTranscurrido();
                long horas = TimeUnit.MILLISECONDS.toHours(elapsed);
                long minutos = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60;
                long segundos = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60;

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        tvHoras.setText(String.format(Locale.getDefault(), "%02d", horas));
                        tvMinutos.setText(String.format(Locale.getDefault(), "%02d", minutos));
                        tvSegundos.setText(String.format(Locale.getDefault(), "%02d", segundos));
                        
                        // Update progress bar
                        if (duracionMinutos > 0) {
                            long totalMillis = duracionMinutos * 60 * 1000;
                            int progress = (int) ((elapsed * 100) / totalMillis);
                            if (pbProgresoPaseo != null) {
                                pbProgresoPaseo.setProgress(Math.min(progress, 100));
                            }
                        } else {
                            if (pbProgresoPaseo != null) {
                                pbProgresoPaseo.setProgress(0);
                            }
                        }

                        // Logic for button visibility (50% of agreed duration)
                        if (duracionMinutos > 0) {
                             long totalMillis = duracionMinutos * 60 * 1000;
                             if (elapsed >= totalMillis * 0.5) {
                                 if (btnFinalizar.getVisibility() != View.VISIBLE) {
                                     btnFinalizar.setVisibility(View.VISIBLE);
                                 }
                             } else {
                                 if (btnFinalizar.getVisibility() != View.GONE) {
                                     btnFinalizar.setVisibility(View.GONE);
                                 }
                             }
                        }
                    }
                });
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }



    private void mostrarLoading(boolean mostrar) {
        pbLoading.setVisibility(mostrar ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
        // 🔴 CRÍTICO: NO detener LocationService en onPause()
        // LocationService DEBE continuar en background para grabar ubicaciones
        // Si detenemos aquí, pierden todas las ubicaciones mientras la app está paused
        // stopLocationUpdates();  // ← COMENTADO

        Log.d(TAG, "onPause() - LocationService CONTINÚA ACTIVO en background");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();

        Log.d(TAG, "onDestroy() - LocationService continúa activo, manejándose con sus propios listeners");

        saveHandler.removeCallbacksAndMessages(null);

        // Limpiar monitor de red
        if (networkMonitor != null) {
            networkMonitor.unregister();
        }
    }


    private void setupLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Más responsivo: 7s, 6m
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 7000)
                .setMinUpdateDistanceMeters(6)
                .setWaitForAccurateLocation(false)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Log.d(TAG, "📡 LocationCallback ejecutado - Ubicaciones recibidas: " +
                      locationResult.getLocations().size());
                for (android.location.Location location : locationResult.getLocations()) {
                    if (location != null) {
                        procesarUbicacion(location);
                    }
                }
            }
        };

        checkLocationPermissionAndStart();
        solicitarPrimeraUbicacionRapida();
    }

    private void solicitarPrimeraUbicacionRapida() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        procesarUbicacion(loc);
                    } else {
                        actualizarEstadoUbicacion("Ubicación: buscando señal...");
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo obtener ubicación inicial rápida", e));
    }

    private void checkLocationPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_LOCATION);
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationService() {
        // 🔴 CRÍTICO: Evitar duplicados - NO iniciar si ya se inició en esta sesión
        if (locationServiceStarted) {
            Log.d(TAG, "ℹ️ LocationService ya iniciado, ignorando reinicio");
            return;
        }

        Log.d(TAG, ">>> INTENTANDO INICIAR LocationService para paseo: " + idReserva);
        try {
            Intent serviceIntent = new Intent(this, LocationService.class);
            serviceIntent.setAction(LocationService.ACTION_START_TRACKING);
            serviceIntent.putExtra(LocationService.EXTRA_RESERVA_ID, idReserva);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Log.d(TAG, ">>> Iniciando como FOREGROUND service (Android O+)");
                startForegroundService(serviceIntent);
            } else {
                Log.d(TAG, ">>> Iniciando como servicio normal");
                startService(serviceIntent);
            }
            Log.d(TAG, ">>> LocationService iniciado exitosamente");
            locationServiceStarted = true;  // Marcar como iniciado
            actualizarEstadoUbicacion("📍 Grabando ubicaciones en tiempo real...");
        } catch (Exception e) {
            Log.e(TAG, ">>> ERROR al iniciar LocationService", e);
            Toast.makeText(this, "Error al iniciar tracking: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.setAction(LocationService.ACTION_STOP_TRACKING);
        startService(serviceIntent);
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            // Start the Foreground Service for background tracking
            startLocationService();

            if (fusedLocationClient != null && locationCallback != null) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
                // NO sobrescribir el mensaje "📍 Grabando ubicaciones en tiempo real..." que ya se configuró en startLocationService()
                Log.d(TAG, " Location updates iniciados - Intervalo: 7s, Min distancia: 6m");
            } else {
                Log.e(TAG, " No se puede iniciar location updates - fusedLocationClient o callback null");
            }
        } else {
            Log.e(TAG, " Permiso ACCESS_FINE_LOCATION no otorgado");
        }
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        stopLocationService();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setAllGesturesEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.setBuildingsEnabled(true); // Ensure 3D buildings are enabled

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null && mapFragment.getView() != null) {
            setTouchListenerRecursive(mapFragment.getView(), (v, event) -> {
                int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        if (scrollContainer != null) scrollContainer.requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (scrollContainer != null) scrollContainer.requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            });
        }

        if (!rutaPaseo.isEmpty()) {
            actualizarMapa(rutaPaseo.get(rutaPaseo.size() - 1));
        }
    }

    private void setTouchListenerRecursive(View view, View.OnTouchListener listener) {
        if (view == null) return;
        view.setOnTouchListener(listener);
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                setTouchListenerRecursive(viewGroup.getChildAt(i), listener);
            }
        }
    }

    private void actualizarMapa(LatLng nuevaPos) {
        if (mMap == null || nuevaPos == null) return;

        // Actualizar Polilínea
        if (polylineRuta == null) {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .add(nuevaPos) // Start with current
                    .width(16f)
                    .color(ContextCompat.getColor(this, R.color.blue_primary))
                    .jointType(JointType.ROUND)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .geodesic(true);
            polylineRuta = mMap.addPolyline(polylineOptions);
        }
        polylineRuta.setPoints(rutaPaseo);

        // Marcador de Inicio
        if (marcadorInicio == null && !rutaPaseo.isEmpty()) {
            marcadorInicio = mMap.addMarker(new MarkerOptions()
                    .position(rutaPaseo.get(0))
                    .title("Inicio")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        }

        // Marcador Actual (Paseador)
        BitmapDescriptor walkerIcon = getResizedBitmapDescriptor(R.drawable.ic_paseador_perro_marcador, 120);
        if (marcadorActual == null) {
            marcadorActual = mMap.addMarker(new MarkerOptions()
                    .position(nuevaPos)
                    .title("Yo")
                    .icon(walkerIcon != null ? walkerIcon : BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .anchor(0.5f, 1.0f));
            
            // Initial move with tilt
            com.google.android.gms.maps.model.CameraPosition cameraPosition =
                    new com.google.android.gms.maps.model.CameraPosition.Builder()
                            .target(nuevaPos)
                            .zoom(17.5f)
                            .bearing(0)
                            .tilt(45)
                            .build();
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        } else {
            animarMarcador(marcadorActual, nuevaPos);
            if (walkerIcon != null) {
                marcadorActual.setIcon(walkerIcon);
            }
            // Auto-follow with perspective preference
            com.google.android.gms.maps.model.CameraPosition cameraPosition =
                    new com.google.android.gms.maps.model.CameraPosition.Builder()
                            .target(nuevaPos)
                            .zoom(17.5f)
                            .bearing(0)
                            .tilt(45) // Fixed 3D tilt
                            .build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 700, null);
        }
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
            if (widthPx <= 0 || intrinsicWidth <= 0 || intrinsicHeight <= 0) return null;
            int heightPx = (int) ((float) widthPx * ((float) intrinsicHeight / (float) intrinsicWidth));
            drawable.setBounds(0, 0, widthPx, heightPx);
            Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.draw(canvas);
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } catch (Exception e) {
            return null;
        }
    }

    private void procesarUbicacion(android.location.Location location) {
        if (reservaRef == null || location == null) return;

        long now = System.currentTimeMillis();
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : -1;
        
        Log.d(TAG, "📍 Procesando ubicación (UI): " + 
              location.getLatitude() + ", Lng: " + location.getLongitude());

        // Distancia y Ruta
        if (lastSentLocation != null) {
            float dist = location.distanceTo(lastSentLocation);
            if (dist > 5) { // Filtrar ruido mínimo
                distanciaTotalMetros += dist;
                if (tvDistancia != null) {
                    tvDistancia.setText(String.format(Locale.getDefault(), "Distancia: %.2f km", distanciaTotalMetros / 1000));
                }
            }
        }
        
        LatLng nuevaLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        // Solo agregar si se movió o es el primero
        if (rutaPaseo.isEmpty() || (lastSentLocation != null && location.distanceTo(lastSentLocation) > 2)) {
             rutaPaseo.add(nuevaLatLng);
             actualizarMapa(nuevaLatLng);
        }

        boolean isGoodPrecision = accuracy > 0 && accuracy <= 100f;
        boolean isAcceptablePrecision = accuracy > 100f && accuracy <= 500f;
        boolean isBadPrecision = !location.hasAccuracy() || accuracy > 500f;

        String mensaje = "Ubicación: ";
        if (isBadPrecision) {
            mensaje += "buscando señal GPS...";
            actualizarEstadoUbicacion(mensaje);
            return; // No actualizamos UI de movimiento si la precisión es mala
        }

        if (isAcceptablePrecision) {
            mensaje += "señal débil (" + (int)accuracy + "m)";
            if (now - lastGoodLocationTime < 30000) {
                // Si tuvimos buena señal hace poco, toleramos esta
                actualizarEstadoUbicacion(mensaje);
            } else {
                // Si llevamos tiempo con mala señal, avisar
                actualizarEstadoUbicacion(mensaje);
                return; 
            }
        } else {
            lastGoodLocationTime = now;
        }

        // Determinar si hay movimiento para la UI
        boolean enMovimiento = location.getSpeed() > 0.7f; // ~2.5 km/h
        if (lastSentLocation != null) {
            float dist = location.distanceTo(lastSentLocation);
            if (dist > 10) { // Solo considerar movimiento si se ha movido 10 metros
                 enMovimiento = true;
            }
        }

        // La transmisión de datos (Firestore/Socket) ahora la maneja LocationService.
        
        lastSentLocation = location;
        
        actualizarEstadoUbicacion(formatearEstadoUbicacion(location, enMovimiento));
    }

    private String formatearEstadoUbicacion(android.location.Location loc, boolean enMovimiento) {
        int acc = (int) loc.getAccuracy();
        long timeStationary = System.currentTimeMillis() - lastMovementTime;
        boolean isStationaryLongTime = timeStationary > (6 * 60 * 1000); // 6 minutos

        // Update Color based on stationary time
        if (tvUbicacionEstado != null) {
            if (isStationaryLongTime) {
                tvUbicacionEstado.setTextColor(ContextCompat.getColor(this, R.color.red_error));
            } else if (enMovimiento) {
                tvUbicacionEstado.setTextColor(ContextCompat.getColor(this, R.color.blue_primary));
            } else {
                tvUbicacionEstado.setTextColor(ContextCompat.getColor(this, R.color.gray_dark));
            }
        }

        String mov = enMovimiento ? "en movimiento" : "detenido";
        if (isStationaryLongTime) {
            long mins = TimeUnit.MILLISECONDS.toMinutes(timeStationary);
            mov = "detenido por " + mins + " min";
        }
        
        return "Ubicación: hace instantes (±" + acc + " m, " + mov + ")";
    }

    private void actualizarEstadoUbicacion(String texto) {
        if (tvUbicacionEstado != null) {
            tvUbicacionEstado.setText(texto);

            // TalkBack: Anunciar cambios de ubicación críticos
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                if (texto.contains("sin red") || texto.contains("buscando")) {
                    tvUbicacionEstado.announceForAccessibility(texto);
                } else if (texto.contains("±")) {
                    // Anunciar solo cuando hay ubicación confirmada con precisión
                    tvUbicacionEstado.announceForAccessibility("Ubicación actualizada: " + texto);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //vibe-fix: Agregar callback onRequestPermissionsResult() para llamadas telefónicas
        if (requestCode == REQUEST_PERMISSION_CALL) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (telefonoPendiente != null && !telefonoPendiente.isEmpty()) {
                    realizarLlamada(telefonoPendiente);
                } else {
                    Log.w(TAG, "Permiso de llamada concedido pero telefonoPendiente es null");
                    Toast.makeText(this, "Error: No se pudo obtener el número de teléfono", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Permiso de llamada denegado. No se puede realizar la llamada.", Toast.LENGTH_LONG).show();
                Log.d(TAG, "Usuario denegó permiso de llamada");
            }
            telefonoPendiente = null;
        } else if (requestCode == REQUEST_PERMISSION_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                abrirCamara();
            } else {
                Toast.makeText(this, "Permiso de cámara denegado. No se pueden tomar fotos.", Toast.LENGTH_LONG).show();
                Log.d(TAG, "Usuario denegó permiso de cámara");
            }
        } else if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado. No se podrá rastrear el paseo.", Toast.LENGTH_LONG).show();
            }
        }
    }
}


