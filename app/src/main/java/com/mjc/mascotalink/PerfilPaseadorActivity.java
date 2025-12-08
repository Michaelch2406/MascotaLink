package com.mjc.mascotalink;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup; // Added this import
import android.view.MotionEvent; // Added this import
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.location.Location;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import android.location.Address;
import android.location.Geocoder;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.messaging.FirebaseMessaging;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.security.CredentialManager;
import com.mjc.mascotalink.security.EncryptedPreferencesHelper;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascota.modelo.Resena;
import com.mjc.mascota.ui.perfil.ResenaAdapter;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;
import com.mjc.mascotalink.network.SocketManager;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PerfilPaseadorActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "PerfilPaseadorActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SocketManager socketManager;

    // Views
    private Toolbar toolbar;
    private TextView toolbarTitle;
    private ImageView ivBack;
    private de.hdodenhof.circleimageview.CircleImageView ivAvatar;
    private Button btnVerGaleria;
    private ImageButton btnFavorito;
    private ImageView ivVerificadoBadge, ivVideoThumbnail, ivPlayButton;
    private TextView tvNombre, tvRol, tvVerificado, tvPrecio, tvDescripcion, tvDisponibilidad, tvTiposPerros, tvZonasServicioNombres;
    private TextView tvExperienciaDesde;
    private TextView tvPaseosCompletados, tvTiempoRespuesta, tvUltimaConexion, tvMiembroDesde;
    private TextView tvRatingValor, tvResenasTotal;
    private TextView tvEmailPaseador, tvTelefonoPaseador;
    private TextView badgePerfilEnLinea;
    private ImageView btnCopyEmailPaseador, btnCopyTelefonoPaseador;
    private RatingBar ratingBar;
    private LinearLayout llAcercaDe, llResenas, ajustes_section, soporte_section;
    private FrameLayout videoContainer;
    private android.widget.VideoView videoView;
    private android.widget.ProgressBar videoProgressBar;
    private TabLayout tabLayout;
    private Button btnCerrarSesion, btnGestionarGaleria;
    private ImageButton btnMensaje;
    private FloatingActionButton fabReservar;
    private ImageView ivEditZonas, ivEditDisponibilidad;
    private ImageButton ivEditPerfil;
    private GoogleMap googleMap;
    private List<Circle> mapCircles = new ArrayList<>();
    private View btnNotificaciones, btnMetodosPago, btnPrivacidad, btnCentroAyuda, btnTerminos, btnMisPaseos;
    private androidx.appcompat.widget.SwitchCompat switchNotificaciones;
    private androidx.appcompat.widget.SwitchCompat switchAceptaSolicitudes;
    private View bannerNoAceptando;
    private String metodoPagoId;
    private View skeletonLayout;
    private NestedScrollView scrollViewContent;
    private boolean isContentVisible = false;
    private String paseadorId;
    private String currentUserId;
    private String currentUserRole;
    private String videoUrl;
    private Double paseadorPrecioHora = null;
    private BottomNavigationView bottomNav;
    private String bottomNavRole = "PASEADOR";
    private int bottomNavSelectedItem = R.id.menu_perfil;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest profileLocationRequest;
    private LocationCallback profileLocationCallback;
    private Location lastProfileLocation;
    private long lastProfileLocationUpdateMs = 0L;
    private static final long PROFILE_MIN_UPDATE_MS = 30_000; // 30s
    private static final float PROFILE_MIN_DISTANCE_M = 50f; // 50m
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 2104;
    private boolean isOwnProfile = false;

    // Gallery Preview
    private RecyclerView rvGalleryPreview;
    private TextView tvGalleryPreviewHeader;
    private GalleryPreviewAdapter galleryPreviewAdapter;
    private List<String> galleryPreviewList = new ArrayList<>();

    private RecyclerView recyclerViewResenas;
    private LinearLayout llEmptyReviews;
    private ResenaAdapter resenaAdapter;
    private List<Resena> resenasList = new ArrayList<>();
    private DocumentSnapshot lastVisibleResena = null;
    private boolean isLoadingResenas = false;

    private ArrayList<String> galeriaImageUrls = new ArrayList<>();

    // Listeners
    private FirebaseAuth.AuthStateListener mAuthListener;
    private ListenerRegistration usuarioListener;
    private ListenerRegistration paseadorListener;
    private ListenerRegistration disponibilidadListener;
    private ListenerRegistration zonasListener;
    private ListenerRegistration metodoPagoListener;

    private static final int REQUEST_NOTIFICATION_PERMISSION = 123; 

    private Button btnVerMasResenas;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_paseador);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        socketManager = SocketManager.getInstance(this);

        initViews();
        
        // Manual Toolbar Setup
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        ivBack.setOnClickListener(v -> finish());

        setupListeners();
        // setupTabs() will be called after role is determined in setupRoleBasedUI
        setupResenasRecyclerView();
        setupAuthListener();

        paseadorId = getIntent().getStringExtra("paseadorId");

        // Initialize currentUserId and role from cache immediately to prevent bottom nav flicker
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
            String cachedRole = BottomNavManager.getUserRole(this);
            if (cachedRole != null) {
                currentUserRole = cachedRole;
                // Pre-calculate bottomNavRole based on cache
                boolean isOwnProfile = paseadorId == null || paseadorId.equals(currentUserId);
                if (isOwnProfile) {
                    bottomNavRole = "PASEADOR";
                    bottomNavSelectedItem = R.id.menu_perfil;
                } else if ("DUEÑO".equalsIgnoreCase(currentUserRole)) {
                    bottomNavRole = "DUEÑO";
                    bottomNavSelectedItem = R.id.menu_search;
                } else {
                    bottomNavRole = currentUserRole;
                    bottomNavSelectedItem = R.id.menu_search;
                }
            }
            com.mjc.mascotalink.util.UnreadBadgeManager.start(currentUserId);
        }
        
        com.mjc.mascotalink.util.UnreadBadgeManager.start(currentUserId);

        // Setup Bottom Navigation immediately to prevent flicker
        if (bottomNav != null) {
            setupBottomNavigation();
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbarTitle = findViewById(R.id.toolbar_title);
        ivBack = findViewById(R.id.iv_back);
        ivAvatar = findViewById(R.id.iv_avatar);
        btnVerGaleria = findViewById(R.id.btn_ver_galeria);
        btnGestionarGaleria = findViewById(R.id.btn_gestionar_galeria);
        btnFavorito = findViewById(R.id.btn_favorito);
        tvNombre = findViewById(R.id.tv_nombre);
        tvRol = findViewById(R.id.tv_rol);
        ivVerificadoBadge = findViewById(R.id.iv_verificado_badge);
        tvVerificado = findViewById(R.id.tv_verificado);
        tvPrecio = findViewById(R.id.tv_precio);
        tabLayout = findViewById(R.id.tab_layout);
        llAcercaDe = findViewById(R.id.ll_acerca_de);
        llResenas = findViewById(R.id.ll_resenas);
        videoContainer = findViewById(R.id.video_container);
        ivVideoThumbnail = findViewById(R.id.iv_video_thumbnail);
        ivPlayButton = findViewById(R.id.iv_play_button);
        videoView = findViewById(R.id.video_view);
        videoProgressBar = findViewById(R.id.video_progress_bar);
        tvDescripcion = findViewById(R.id.tv_descripcion);
        tvExperienciaDesde = findViewById(R.id.tv_experiencia_desde);
        tvDisponibilidad = findViewById(R.id.tv_disponibilidad);
        tvTiposPerros = findViewById(R.id.tv_tipos_perros);
        tvZonasServicioNombres = findViewById(R.id.tv_zonas_servicio_nombres);
        tvPaseosCompletados = findViewById(R.id.tv_paseos_completados);
        tvTiempoRespuesta = findViewById(R.id.tv_tiempo_respuesta);
        tvUltimaConexion = findViewById(R.id.tv_ultima_conexion);
        tvMiembroDesde = findViewById(R.id.tv_miembro_desde);
        badgePerfilEnLinea = findViewById(R.id.badge_perfil_en_linea);
        tvRatingValor = findViewById(R.id.tv_rating_valor);
        ratingBar = findViewById(R.id.rating_bar);
        tvResenasTotal = findViewById(R.id.tv_resenas_total);
        
        tvEmailPaseador = findViewById(R.id.tv_email_paseador);
        tvTelefonoPaseador = findViewById(R.id.tv_telefono_paseador);
        btnCopyEmailPaseador = findViewById(R.id.btn_copy_email_paseador);
        btnCopyTelefonoPaseador = findViewById(R.id.btn_copy_telefono_paseador);

        // Gallery Preview initialization
        tvGalleryPreviewHeader = findViewById(R.id.tv_gallery_preview_header);
        rvGalleryPreview = findViewById(R.id.rv_gallery_preview);
        rvGalleryPreview.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        galleryPreviewAdapter = new GalleryPreviewAdapter(this, galleryPreviewList);
        rvGalleryPreview.setAdapter(galleryPreviewAdapter);
        
        llEmptyReviews = findViewById(R.id.ll_empty_reviews);
        bannerNoAceptando = findViewById(R.id.banner_no_aceptando);

        btnMisPaseos = findViewById(R.id.btn_mis_paseos);
        btnNotificaciones = findViewById(R.id.btn_notificaciones);
        switchNotificaciones = findViewById(R.id.switch_notificaciones);
        switchAceptaSolicitudes = findViewById(R.id.switch_acepta_solicitudes);
        btnMetodosPago = findViewById(R.id.btn_metodos_pago);
        btnPrivacidad = findViewById(R.id.btn_privacidad);
        btnCentroAyuda = findViewById(R.id.btn_centro_ayuda);
        btnTerminos = findViewById(R.id.btn_terminos);
        btnCerrarSesion = findViewById(R.id.btn_cerrar_sesion);
        ajustes_section = findViewById(R.id.ajustes_section);
        soporte_section = findViewById(R.id.soporte_section);
        fabReservar = findViewById(R.id.fab_reservar);
        btnMensaje = findViewById(R.id.btn_mensaje); // In Toolbar
        ivEditPerfil = findViewById(R.id.iv_edit_perfil); // In Toolbar
        ivEditZonas = findViewById(R.id.iv_edit_zonas);
        ivEditDisponibilidad = findViewById(R.id.iv_edit_disponibilidad);
        skeletonLayout = findViewById(R.id.skeleton_layout);
        scrollViewContent = findViewById(R.id.scroll_view_content);
        recyclerViewResenas = findViewById(R.id.recycler_view_resenas);
        btnVerMasResenas = findViewById(R.id.btn_ver_mas_resenas);
        bottomNav = findViewById(R.id.bottom_nav);


        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_container);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupTabs() {
        llAcercaDe.setVisibility(View.VISIBLE);
        llResenas.setVisibility(View.GONE);
        tabLayout.removeAllTabs(); // Clear existing tabs

        String aboutTabText = isOwnProfile ? "Mi perfil" : "Información";
        tabLayout.addTab(tabLayout.newTab().setText(aboutTabText), true);
        tabLayout.addTab(tabLayout.newTab().setText("Reseñas"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    llAcercaDe.setVisibility(View.VISIBLE);
                    llResenas.setVisibility(View.GONE);
                } else {
                    llAcercaDe.setVisibility(View.GONE);
                    llResenas.setVisibility(View.VISIBLE);
                    if (resenasList.isEmpty()) {
                        cargarMasResenas(4); // Cargar inicialmente 4 para ver si hay más de 3
                    }
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void setupListeners() {
        ivEditPerfil.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilPaseadorActivity.this, EditarPerfilPaseadorActivity.class);
            intent.putExtra("paseadorId", paseadorId);
            startActivity(intent);
        });

        if (btnMisPaseos != null) {
            btnMisPaseos.setOnClickListener(v -> {
                Intent intent = new Intent(PerfilPaseadorActivity.this, HistorialPaseosActivity.class);
                intent.putExtra("rol_usuario", "PASEADOR");
                startActivity(intent);
            });
        }

        videoContainer.setOnClickListener(v -> {
            if (videoUrl != null && !videoUrl.isEmpty()) {
                ivVideoThumbnail.setVisibility(View.GONE);
                ivPlayButton.setVisibility(View.GONE);
                videoProgressBar.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.VISIBLE);

                android.widget.MediaController mediaController = new android.widget.MediaController(this);
                mediaController.setAnchorView(videoView);
                videoView.setMediaController(mediaController);
                videoView.setVideoURI(android.net.Uri.parse(videoUrl));
                videoView.setOnPreparedListener(mp -> {
                    videoProgressBar.setVisibility(View.GONE);
                    mp.start();
                });
                videoView.setOnCompletionListener(mp -> {
                    videoView.setVisibility(View.GONE);
                    ivVideoThumbnail.setVisibility(View.VISIBLE);
                    ivPlayButton.setVisibility(View.VISIBLE);
                });
                videoView.setOnErrorListener((mp, what, extra) -> {
                    videoProgressBar.setVisibility(View.GONE);
                    ivVideoThumbnail.setVisibility(View.VISIBLE);
                    ivPlayButton.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "No se puede reproducir este video", Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
        });

                btnMensaje.setOnClickListener(v -> {
            if (paseadorId == null || currentUserId == null) {
                Toast.makeText(this, "No se pudo abrir el chat", Toast.LENGTH_SHORT).show();
                return;
            }
            if (paseadorId.equals(currentUserId)) {
                Toast.makeText(this, "Este es tu perfil", Toast.LENGTH_SHORT).show();
                return;
            }
            com.mjc.mascotalink.util.ChatHelper.openOrCreateChat(this, db, currentUserId, paseadorId);
        });

        btnVerGaleria.setOnClickListener(v -> {
            if (galeriaImageUrls != null && !galeriaImageUrls.isEmpty()) {
                Intent intent = new Intent(PerfilPaseadorActivity.this, GaleriaActivity.class);
                intent.putStringArrayListExtra("imageUrls", galeriaImageUrls);
                startActivity(intent);
            } else {
                Toast.makeText(this, "No hay imágenes en la galería.", Toast.LENGTH_SHORT).show();
            }
        });

        btnGestionarGaleria.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilPaseadorActivity.this, GestionarGaleriaActivity.class);
            startActivity(intent);
        });

        fabReservar.setOnClickListener(v -> {
            if (paseadorId == null || paseadorPrecioHora == null) {
                Toast.makeText(this, "Cargando datos del paseador...", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(PerfilPaseadorActivity.this, ReservaActivity.class);
            intent.putExtra("paseador_id", paseadorId);
            intent.putExtra("paseador_nombre", tvNombre.getText().toString());
            intent.putExtra("precio_hora", paseadorPrecioHora);
            startActivity(intent);
        });

        ivEditZonas.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilPaseadorActivity.this, ZonasServicioActivity.class);
            startActivity(intent);
        });

        ivEditDisponibilidad.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilPaseadorActivity.this, DisponibilidadActivity.class);
            startActivity(intent);
        });

        btnCerrarSesion.setOnClickListener(v -> {
            detachDataListeners();
            com.mjc.mascotalink.util.UnreadBadgeManager.stop();
            new CredentialManager(PerfilPaseadorActivity.this).clearCredentials();
            try {
                EncryptedPreferencesHelper.getInstance(PerfilPaseadorActivity.this).clear();
            } catch (Exception e) {
                Log.e(TAG, "btnCerrarSesion: error limpiando prefs cifradas", e);
            }
            // Desconectar WebSocket antes de cerrar sesión
            com.mjc.mascotalink.network.SocketManager.getInstance(PerfilPaseadorActivity.this).disconnect();
            mAuth.signOut();
        });

        btnNotificaciones.setOnClickListener(v -> startActivity(new Intent(PerfilPaseadorActivity.this, NotificacionesActivity.class)));

        // Configurar switch de notificaciones
        com.mjc.mascotalink.utils.NotificacionesPreferences notifPrefs = new com.mjc.mascotalink.utils.NotificacionesPreferences(this);
        notifPrefs.loadFromFirestore(() -> {
            switchNotificaciones.setChecked(notifPrefs.isNotificacionesEnabled());
        });

        switchNotificaciones.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) { // Solo si el cambio fue por el usuario
                notifPrefs.setNotificacionesEnabled(isChecked);
                String mensaje = isChecked ? "Notificaciones activadas" : "Notificaciones desactivadas";
                Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
            }
        });

        // Configurar switch de aceptar solicitudes
        switchAceptaSolicitudes.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) { // Solo si el cambio fue por el usuario
                actualizarAceptaSolicitudes(isChecked);
            }
        });

        btnMetodosPago.setOnClickListener(v -> {
             Intent intent = new Intent(PerfilPaseadorActivity.this, MetodoPagoActivity.class);
             if (metodoPagoId != null && !metodoPagoId.isEmpty()) {
                 intent.putExtra("metodo_pago_id", metodoPagoId);
             }
             startActivity(intent);
         });

        btnPrivacidad.setOnClickListener(v -> startActivity(new Intent(PerfilPaseadorActivity.this, PoliticaPrivacidadActivity.class)));
        btnCentroAyuda.setOnClickListener(v -> startActivity(new Intent(PerfilPaseadorActivity.this, CentroAyudaActivity.class)));
        btnTerminos.setOnClickListener(v -> startActivity(new Intent(PerfilPaseadorActivity.this, TerminosCondicionesActivity.class)));


        scrollViewContent.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (btnVerMasResenas.getVisibility() == View.GONE && v.getChildAt(v.getChildCount() - 1) != null) {
                if ((scrollY >= (v.getChildAt(v.getChildCount() - 1).getMeasuredHeight() - v.getMeasuredHeight())) &&
                        scrollY > oldScrollY) {
                    cargarMasResenas(10); 
                }
            }
        });

        btnVerMasResenas.setOnClickListener(v -> {
            btnVerMasResenas.setVisibility(View.GONE);
            cargarMasResenas(10);
        });

        btnCopyEmailPaseador.setOnClickListener(v -> copyToClipboard("Correo", tvEmailPaseador.getText().toString()));
        btnCopyTelefonoPaseador.setOnClickListener(v -> copyToClipboard("Teléfono", tvTelefonoPaseador.getText().toString()));
    }

    private void copyToClipboard(String label, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, label + " copiado al portapapeles", Toast.LENGTH_SHORT).show();
        }
    }

    private void cargarAceptaSolicitudes() {
        if (currentUserId == null || switchAceptaSolicitudes == null) return;

        db.collection("usuarios").document(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && switchAceptaSolicitudes != null) {
                        Boolean aceptaSolicitudes = documentSnapshot.getBoolean("acepta_solicitudes");
                        // Por defecto es true si no existe el campo
                        boolean acepta = aceptaSolicitudes == null || aceptaSolicitudes;
                        switchAceptaSolicitudes.setChecked(acepta);
                        actualizarVisibilidadBanner(acepta);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar acepta_solicitudes", e);
                    // Por defecto activado
                    if (switchAceptaSolicitudes != null) {
                        switchAceptaSolicitudes.setChecked(true);
                    }
                    actualizarVisibilidadBanner(true);
                });
    }

    private void actualizarAceptaSolicitudes(boolean acepta) {
        if (currentUserId == null || switchAceptaSolicitudes == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("acepta_solicitudes", acepta);

        db.collection("usuarios").document(currentUserId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    String mensaje = acepta ?
                            "Ahora aceptas nuevas solicitudes" :
                            "Pausado: No aceptarás nuevas solicitudes";
                    Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
                    actualizarVisibilidadBanner(acepta);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al actualizar acepta_solicitudes", e);
                    Toast.makeText(this, "Error al actualizar configuración", Toast.LENGTH_SHORT).show();
                    // Revertir el switch
                    if (switchAceptaSolicitudes != null) {
                        switchAceptaSolicitudes.setChecked(!acepta);
                    }
                });
    }

    private void actualizarVisibilidadBanner(boolean aceptaSolicitudes) {
        if (bannerNoAceptando != null) {
            // Mostrar banner solo cuando NO acepta solicitudes
            bannerNoAceptando.setVisibility(aceptaSolicitudes ? View.GONE : View.VISIBLE);
        }
    }

    private void cargarEstadoAceptaSolicitudesParaVisitante() {
        if (paseadorId == null) return;

        db.collection("usuarios").document(paseadorId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Boolean aceptaSolicitudes = documentSnapshot.getBoolean("acepta_solicitudes");
                        // Por defecto es true si no existe el campo
                        boolean acepta = aceptaSolicitudes == null || aceptaSolicitudes;
                        actualizarVisibilidadBanner(acepta);
                    } else {
                        // Por defecto ocultar el banner
                        actualizarVisibilidadBanner(true);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar acepta_solicitudes del paseador", e);
                    // Por defecto ocultar el banner
                    actualizarVisibilidadBanner(true);
                });
    }

    private void setupAuthListener() {
        mAuthListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                currentUserId = user.getUid();
                if (paseadorId == null) {
                    paseadorId = currentUserId;
                }
                fetchCurrentUserRoleAndSetupUI();
                attachDataListeners();
                updateFcmToken();
            } else {
                currentUserId = null;
                currentUserRole = null;
                Intent intent = new Intent(PerfilPaseadorActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        };
    }

    private void updateFcmToken() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);
                    Map<String, Object> tokenMap = new HashMap<>();
                    tokenMap.put("fcmToken", token);
                    db.collection("usuarios").document(user.getUid())
                            .update(tokenMap)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token updated in Firestore"))
                            .addOnFailureListener(e -> Log.w(TAG, "Error updating FCM token", e));
                });
        }
    }

    private void fetchCurrentUserRoleAndSetupUI() {
        if (currentUserId == null) {
            setupRoleBasedUI(); 
            return;
        }
        db.collection("usuarios").document(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUserRole = documentSnapshot.getString("rol");
                    }
                    setupRoleBasedUI();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user role", e);
                    setupRoleBasedUI();
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Permiso de Notificaciones")
                            .setMessage("Para recibir actualizaciones importantes, habilita las notificaciones.")
                            .setPositiveButton("Aceptar", (dialog, which) -> {
                                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
                            })
                            .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                            .show();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("PerfilPaseadorActivity", "POST_NOTIFICATIONS permission granted.");
            } else {
                Log.w("PerfilPaseadorActivity", "POST_NOTIFICATIONS permission denied.");
                Toast.makeText(this, "Las notificaciones están deshabilitadas.", Toast.LENGTH_LONG).show();
            }
        }
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startProfileLocationUpdates();
                ensureLocationForProfile();
            } else {
                Log.w(TAG, "Permiso de ubicación denegado en perfil; no se actualizará ubicacion_actual.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();
        updatePresence(true);
        ensureLocationForProfile();
        startProfileLocationUpdates();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ivAvatar != null && !isDestroyed() && !isFinishing()) {
            try {
                Glide.with(this).clear(ivAvatar);
            } catch (Exception e) {
                Log.w(TAG, "Error clearing Glide request in onStop", e);
            }
        }
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
        detachDataListeners();
        updatePresence(false);
        stopProfileLocationUpdates();
    }

    private void setupRoleBasedUI() {
        isOwnProfile = paseadorId != null && paseadorId.equals(currentUserId);

        if (isOwnProfile) {
            toolbarTitle.setText("Perfil");
        } else {
            toolbarTitle.setText("Paseador");
        }

        if (isOwnProfile) {
            ivEditPerfil.setVisibility(View.VISIBLE);
            btnMensaje.setVisibility(View.GONE);
            btnFavorito.setVisibility(View.GONE);
            fabReservar.setVisibility(View.GONE);
            ivEditZonas.setVisibility(View.VISIBLE);
            ivEditDisponibilidad.setVisibility(View.VISIBLE);
            btnGestionarGaleria.setVisibility(View.VISIBLE);
            ajustes_section.setVisibility(View.VISIBLE);
            soporte_section.setVisibility(View.VISIBLE);
            btnCerrarSesion.setVisibility(View.VISIBLE);
            bottomNavRole = "PASEADOR";
            bottomNavSelectedItem = R.id.menu_perfil;
        } else if ("DUEÑO".equalsIgnoreCase(currentUserRole)) {
            ivEditPerfil.setVisibility(View.GONE);
            btnMensaje.setVisibility(View.VISIBLE);
            btnFavorito.setVisibility(View.VISIBLE);
            fabReservar.setVisibility(View.VISIBLE);
            ivEditZonas.setVisibility(View.GONE);
            ivEditDisponibilidad.setVisibility(View.GONE);
            btnGestionarGaleria.setVisibility(View.GONE);
            ajustes_section.setVisibility(View.GONE);
            soporte_section.setVisibility(View.GONE);
            btnCerrarSesion.setVisibility(View.GONE);
            configurarBotonFavorito(currentUserId, paseadorId);
            bottomNavRole = "DUEÑO";
            bottomNavSelectedItem = R.id.menu_search;
        } else {
            ivEditPerfil.setVisibility(View.GONE);
            btnMensaje.setVisibility(View.GONE);
            btnFavorito.setVisibility(View.GONE);
            fabReservar.setVisibility(View.GONE);
            ivEditZonas.setVisibility(View.GONE);
            ivEditDisponibilidad.setVisibility(View.GONE);
            btnGestionarGaleria.setVisibility(View.GONE);
            ajustes_section.setVisibility(View.GONE);
            soporte_section.setVisibility(View.GONE);
            btnCerrarSesion.setVisibility(View.GONE);
            bottomNavRole = currentUserRole != null ? currentUserRole : "DUEÑO";
            bottomNavSelectedItem = R.id.menu_search;
        }
        setupBottomNavigation();
        setupTabs(); // Call setupTabs here after isOwnProfile is determined
    }

    private void setupBottomNavigation() {
        if (bottomNav == null) return;
        String roleForNav = bottomNavRole != null ? bottomNavRole : (currentUserRole != null ? currentUserRole : "PASEADOR");
        BottomNavManager.setupBottomNav(this, bottomNav, roleForNav, bottomNavSelectedItem);
        com.mjc.mascotalink.util.UnreadBadgeManager.registerNav(bottomNav, this);
    }

    private void configurarBotonFavorito(String duenoId, String paseadorId) {
        DocumentReference favRef = db.collection("usuarios").document(duenoId)
                .collection("favoritos").document(paseadorId);
        favRef.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                btnFavorito.setImageResource(R.drawable.ic_corazon_lleno);
            } else {
                btnFavorito.setImageResource(R.drawable.ic_corazon);
            }
        });
        btnFavorito.setOnClickListener(v -> toggleFavorito(duenoId, paseadorId, favRef));
    }

    private void toggleFavorito(String duenoId, String paseadorId, DocumentReference favRef) {
        favRef.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                favRef.delete().addOnSuccessListener(aVoid -> {
                    btnFavorito.setImageResource(R.drawable.ic_corazon);
                    Toast.makeText(this, "Eliminado de favoritos", Toast.LENGTH_SHORT).show();
                });
            } else {
                DocumentReference usuarioPaseadorRef = db.collection("usuarios").document(paseadorId);
                DocumentReference perfilPaseadorRef = db.collection("paseadores").document(paseadorId);
                Task<DocumentSnapshot> usuarioTask = usuarioPaseadorRef.get();
                Task<DocumentSnapshot> paseadorTask = perfilPaseadorRef.get();
                Tasks.whenAllSuccess(usuarioTask, paseadorTask).addOnSuccessListener(results -> {
                    DocumentSnapshot usuarioDoc = (DocumentSnapshot) results.get(0);
                    DocumentSnapshot paseadorDoc = (DocumentSnapshot) results.get(1);
                    if (usuarioDoc.exists() && paseadorDoc.exists()) {
                        Map<String, Object> favoritoData = new HashMap<>();
                        favoritoData.put("fecha_agregado", FieldValue.serverTimestamp());
                        favoritoData.put("paseador_ref", perfilPaseadorRef);
                        favoritoData.put("nombre_display", usuarioDoc.getString("nombre_display"));
                        favoritoData.put("foto_perfil_url", usuarioDoc.getString("foto_perfil"));
                        favoritoData.put("calificacion_promedio", paseadorDoc.getDouble("calificacion_promedio"));
                        favoritoData.put("precio_hora", paseadorDoc.getDouble("precio_hora"));
                        favRef.set(favoritoData).addOnSuccessListener(aVoid -> {
                            btnFavorito.setImageResource(R.drawable.ic_corazon_lleno);
                            Toast.makeText(PerfilPaseadorActivity.this, "Agregado a favoritos", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        });
    }

    private void updatePresence(boolean online) {
        if (currentUserId == null) return;
        Map<String, Object> presence = new HashMap<>();
        presence.put("en_linea", online);
        presence.put("last_seen", FieldValue.serverTimestamp());
        db.collection("usuarios").document(currentUserId)
                .update(presence)
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo actualizar presencia", e));
    }

    /**
     * Si al entrar al perfil no existe ubicacion_actual / geohash, intenta rellenarlo con la última ubicación conocida.
     * No solicita permisos extra: requiere que ya haya permiso de ubicación concedido.
     */
    private void ensureLocationForProfile() {
        if (paseadorId == null) return;
        db.collection("usuarios").document(paseadorId).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;

                    Object ubicacionObj = doc.get("ubicacion_actual");
                    boolean tieneUbicacionValida = false;

                    if (ubicacionObj instanceof GeoPoint) {
                        tieneUbicacionValida = true;
                    } else if (ubicacionObj instanceof Map) {
                        // It exists but it's a Map, we should convert it to GeoPoint if possible
                        Map<?, ?> map = (Map<?, ?>) ubicacionObj;
                        if (map.containsKey("latitude") && map.containsKey("longitude")) {
                            tieneUbicacionValida = true;
                        } else if (map.containsKey("lat") && map.containsKey("lng")) {
                            tieneUbicacionValida = true;
                        }
                    }

                    boolean faltaGeohash = doc.getString("ubicacion_geohash") == null;

                    if (tieneUbicacionValida && !faltaGeohash) {
                         // Data is fine
                         return;
                    }

                    // If we are here, either location is missing/invalid OR geohash is missing.
                    // We need to try to fix it using current location or zone fallback.

                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "Sin permisos de ubicación; no se puede inicializar ubicacion_actual.");
                        tryZoneFallback();
                        return;
                    }

                    fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                        if (location == null) {
                            Log.w(TAG, "LastLocation es null; intentando fallback por zonas.");
                            tryZoneFallback();
                            return;
                        }
                        if (isInvalidLocation(location)) {
                            Log.w(TAG, "LastLocation inválida; intentando fallback por zonas.");
                            tryZoneFallback();
                            return;
                        }
                        GeoPoint punto = new GeoPoint(location.getLatitude(), location.getLongitude());
                        escribirUbicacionPerfil(punto);
                    }).addOnFailureListener(e -> {
                        Log.w(TAG, "Error obteniendo lastLocation", e);
                        tryZoneFallback();
                    });
                })
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo leer usuario para validar ubicacion", e));
    }

    private void tryZoneFallback() {
        db.collection("paseadores").document(paseadorId)
                .collection("zonas_servicio")
                .limit(1)
                .get()
                .addOnSuccessListener(zonas -> {
                    if (zonas == null || zonas.isEmpty()) {
                        Log.w(TAG, "Sin zonas_servicio para fallback de ubicacion.");
                        return;
                    }
                    DocumentSnapshot zona = zonas.getDocuments().get(0);
                    GeoPoint centro = zona.getGeoPoint("ubicacion_centro");
                    if (centro == null) {
                        Double lat = zona.getDouble("latitud");
                        Double lng = zona.getDouble("longitud");
                        if (lat != null && lng != null) {
                            centro = new GeoPoint(lat, lng);
                        }
                    }
                    if (centro != null) {
                        escribirUbicacionPerfil(centro);
                    } else {
                        Log.w(TAG, "Zona sin centro usable; no se actualiza ubicacion.");
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Error leyendo zonas_servicio para fallback", e));
    }

    private void escribirUbicacionPerfil(GeoPoint punto) {
        if (punto == null) return;
        if (isInvalidGeoPoint(punto)) {
            Log.w(TAG, "GeoPoint inválido; no se escribe ubicacion_actual.");
            return;
        }
        String geohash = GeoFireUtils.getGeoHashForLocation(new GeoLocation(punto.getLatitude(), punto.getLongitude()));
        Map<String, Object> updates = new HashMap<>();
        updates.put("ubicacion_actual", punto);
        updates.put("ubicacion_geohash", geohash);
        updates.put("last_seen", FieldValue.serverTimestamp());
        updates.put("en_linea", true);
        db.collection("usuarios").document(paseadorId)
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "ubicacion_actual inicializada/actualizada desde perfil."))
                .addOnFailureListener(e -> Log.w(TAG, "No se pudo guardar ubicacion_actual", e));
    }

    private void startProfileLocationUpdates() {
        if (!isOwnProfile) return;
        if (fusedLocationClient == null) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        if (profileLocationRequest == null) {
            profileLocationRequest = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, PROFILE_MIN_UPDATE_MS)
                    .setMinUpdateDistanceMeters(PROFILE_MIN_DISTANCE_M)
                    .build();
        }

        profileLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location loc : locationResult.getLocations()) {
                        if (loc == null) continue;
                    if (isInvalidLocation(loc)) continue;
                    long now = System.currentTimeMillis();
                    float shouldUpdateByDistance = lastProfileLocation == null ? Float.MAX_VALUE : lastProfileLocation.distanceTo(loc);
                    if (lastProfileLocation == null || (now - lastProfileLocationUpdateMs) >= PROFILE_MIN_UPDATE_MS || shouldUpdateByDistance >= PROFILE_MIN_DISTANCE_M) {
                        lastProfileLocationUpdateMs = now;
                        lastProfileLocation = loc;
                        GeoPoint punto = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                        escribirUbicacionPerfil(punto);
                    }
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(profileLocationRequest, profileLocationCallback, getMainLooper());
    }

    private void stopProfileLocationUpdates() {
        if (fusedLocationClient != null && profileLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(profileLocationCallback);
        }
    }

    private boolean isInvalidLocation(Location loc) {
        return loc == null || Double.isNaN(loc.getLatitude()) || Double.isNaN(loc.getLongitude())
                || (Math.abs(loc.getLatitude()) < 0.0001 && Math.abs(loc.getLongitude()) < 0.0001);
    }

    private boolean isInvalidGeoPoint(GeoPoint gp) {
        return gp == null || Double.isNaN(gp.getLatitude()) || Double.isNaN(gp.getLongitude())
                || (Math.abs(gp.getLatitude()) < 0.0001 && Math.abs(gp.getLongitude()) < 0.0001);
    }

    private void loadVideoThumbnail(String url, ImageView imageView) {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

        executor.execute(() -> {
            android.graphics.Bitmap bitmap = null;
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            try {
                // Use a map for headers if needed, usually empty for public URLs
                retriever.setDataSource(url, new HashMap<String, String>());
                // Extract frame at 1 second (1000000 microseconds)
                bitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving video thumbnail", e);
            } finally {
                try {
                    retriever.release();
                } catch (IOException e) {
                    // Ignore
                }
            }

            final android.graphics.Bitmap finalBitmap = bitmap;
            handler.post(() -> {
                if (finalBitmap != null) {
                    imageView.setImageBitmap(finalBitmap);
                    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                } else {
                    // Fallback or keep placeholder
                    imageView.setImageResource(R.drawable.galeria_paseos_foto1); 
                }
            });
        });
    }

    private void attachDataListeners() {
        detachDataListeners();
        DocumentReference userDocRef = db.collection("usuarios").document(paseadorId);
        usuarioListener = userDocRef.addSnapshotListener((usuarioDoc, e) -> {
            if (e != null) return;
            if (usuarioDoc != null && usuarioDoc.exists()) {
                tvNombre.setText(usuarioDoc.getString("nombre_display"));
                if (!isDestroyed() && !isFinishing()) {
                    Glide.with(this).load(MyApplication.getFixedUrl(usuarioDoc.getString("foto_perfil"))).placeholder(R.drawable.ic_person).into(ivAvatar);
                }
                Timestamp fechaRegistro = usuarioDoc.getTimestamp("fecha_registro");
                if (fechaRegistro != null) {
                    tvMiembroDesde.setText(String.format("Se unió en %s", formatMiembroDesde(fechaRegistro)));
                }
                Timestamp ultimaConexion = usuarioDoc.getTimestamp("ultima_conexion");
                if (ultimaConexion != null) {
                    tvUltimaConexion.setText(String.format("Últ. conexión: %s", formatUltimaConexion(ultimaConexion)));
                }
                
                tvEmailPaseador.setText(usuarioDoc.getString("correo"));
                tvTelefonoPaseador.setText(usuarioDoc.getString("telefono"));

                showContent();
            }
        });

        DocumentReference paseadorDocRef = db.collection("paseadores").document(paseadorId);
        paseadorListener = paseadorDocRef.addSnapshotListener((paseadorDoc, error) -> {
            if (error != null) return;
            if (paseadorDoc != null && paseadorDoc.exists()) {
                String verificacion = paseadorDoc.getString("verificacion_estado");
                if ("APROBADO".equalsIgnoreCase(verificacion)) {
                    ivVerificadoBadge.setVisibility(View.VISIBLE);
                    tvVerificado.setVisibility(View.VISIBLE);
                } else {
                    ivVerificadoBadge.setVisibility(View.GONE);
                    tvVerificado.setVisibility(View.GONE);
                }
                Double precio = paseadorDoc.getDouble("precio_hora");
                this.paseadorPrecioHora = precio;
                tvPrecio.setText(precio != null ? String.format(Locale.getDefault(), "• $%.2f/hora", precio) : "");
                tvPrecio.setVisibility(precio != null ? View.VISIBLE : View.GONE);

                Long paseos = paseadorDoc.getLong("num_servicios_completados");
                tvPaseosCompletados.setText(String.format(Locale.getDefault(), "%d Paseos completados", paseos != null ? paseos : 0));

                Long tiempoRespuesta = paseadorDoc.getLong("tiempo_respuesta_promedio_min");
                tvTiempoRespuesta.setText(tiempoRespuesta != null ? String.format(Locale.getDefault(), "Respuesta en %d min", tiempoRespuesta) : "Respuesta en -- min");

                Double rating = paseadorDoc.getDouble("calificacion_promedio");
                Long numResenas = paseadorDoc.getLong("total_resenas");
                tvRatingValor.setText(rating != null ? String.format(Locale.getDefault(), "%.1f", rating) : "0.0");
                ratingBar.setRating(rating != null ? rating.floatValue() : 0f);
                tvResenasTotal.setText(String.format(Locale.getDefault(), "%d reviews", numResenas != null ? numResenas : 0));

                Map<String, Object> perfil = (Map<String, Object>) paseadorDoc.get("perfil_profesional");
                if (perfil != null) {
                    tvDescripcion.setText((String) perfil.get("motivacion"));
                    videoUrl = MyApplication.getFixedUrl((String) perfil.get("video_presentacion_url"));
                    if (!isDestroyed() && !isFinishing() && videoUrl != null && !videoUrl.isEmpty()) {
                        // Usar método manual para cargar thumbnail
                        loadVideoThumbnail(videoUrl, ivVideoThumbnail);
                    }
                    // Experiencia Anos logic removed
                    tvExperienciaDesde.setVisibility(View.GONE);

                    // Cargar Galería desde URLs guardadas
                    List<String> urlsGaleria = (List<String>) perfil.get("galeria_paseos_urls");
                    galeriaImageUrls.clear();
                    galleryPreviewList.clear();
                    
                    if (urlsGaleria != null && !urlsGaleria.isEmpty()) {
                        for (String url : urlsGaleria) {
                            String fixedUrl = MyApplication.getFixedUrl(url);
                            galeriaImageUrls.add(fixedUrl);
                            galleryPreviewList.add(fixedUrl);
                        }
                        
                        // Actualizar adaptador de preview (ya tiene la lista actualizada en galleryPreviewList)
                        // galleryPreviewAdapter usa la referencia a galleryPreviewList pero es mejor asegurarse
                        // Sin embargo, en el código original se añadían a galleryPreviewList limitada a 4
                        // Vamos a recrear la lógica original pero con URLs corregidas
                        
                        galleryPreviewList.clear(); // Limpiamos de nuevo para llenar solo con max 4
                        int limit = Math.min(galeriaImageUrls.size(), 4);
                        for (int i = 0; i < limit; i++) {
                            galleryPreviewList.add(galeriaImageUrls.get(i));
                        }
                        galleryPreviewAdapter.setImageUrls(galleryPreviewList);
                        
                        rvGalleryPreview.setVisibility(View.VISIBLE);
                        tvGalleryPreviewHeader.setVisibility(View.VISIBLE);
                        btnVerGaleria.setVisibility(View.VISIBLE);
                    } else {
                        rvGalleryPreview.setVisibility(View.GONE);
                        tvGalleryPreviewHeader.setVisibility(View.GONE);
                        btnVerGaleria.setVisibility(View.GONE);
                    }
                }

                Map<String, Object> manejo = (Map<String, Object>) paseadorDoc.get("manejo_perros");
                if (manejo != null) {
                    List<String> tamanos = (List<String>) manejo.get("tamanos");
                    tvTiposPerros.setText(tamanos != null && !tamanos.isEmpty() ? android.text.TextUtils.join(", ", tamanos) : "No especificado");
                }
                showContent();
            }
        });

        cargarDisponibilidad(paseadorId);
        cargarZonasServicio(paseadorId);
        cargarMetodoPagoPredeterminado(currentUserId != null ? currentUserId : paseadorId);

        // Cargar switch de aceptar solicitudes (solo para perfil propio)
        if (isOwnProfile) {
            cargarAceptaSolicitudes();
        } else {
            // Si estamos viendo el perfil de otro paseador, verificar si acepta solicitudes
            // para mostrar el banner de advertencia
            cargarEstadoAceptaSolicitudesParaVisitante();
        }

        // Setup presence listeners if viewing someone else's profile
        if (!isOwnProfile && paseadorId != null) {
            setupPresenceListeners();
        }
    }

    private void detachDataListeners() {
        if (usuarioListener != null) usuarioListener.remove();
        if (paseadorListener != null) paseadorListener.remove();
        if (disponibilidadListener != null) disponibilidadListener.remove();
        if (zonasListener != null) zonasListener.remove();
        if (metodoPagoListener != null) metodoPagoListener.remove();
        cleanupPresenceListeners();
    }

    private void setupPresenceListeners() {
        if (socketManager == null || !socketManager.isConnected()) {
            Log.w(TAG, "SocketManager no conectado, no se puede configurar presencia");
            return;
        }

        // Listen for when paseador connects
        socketManager.on("user_connected", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");

                if (userId.equals(paseadorId)) {
                    runOnUiThread(() -> {
                        badgePerfilEnLinea.setVisibility(View.VISIBLE);
                    });
                    Log.d(TAG, "👁️ Paseador conectado: " + userId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error procesando user_connected", e);
            }
        });

        // Listen for when paseador disconnects
        socketManager.on("user_disconnected", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");

                if (userId.equals(paseadorId)) {
                    runOnUiThread(() -> {
                        badgePerfilEnLinea.setVisibility(View.GONE);
                        // Reload from Firestore to get updated ultima_conexion
                        reloadUltimaConexion();
                    });
                    Log.d(TAG, "👁️ Paseador desconectado: " + userId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error procesando user_disconnected", e);
            }
        });

        // Listen for online users query response
        socketManager.on("online_users_response", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                org.json.JSONArray onlineUsers = data.getJSONArray("online");
                org.json.JSONArray offlineUsers = data.getJSONArray("offline");

                boolean isOnline = false;
                Long lastActivity = null;

                // Check if paseador is online
                for (int i = 0; i < onlineUsers.length(); i++) {
                    JSONObject user = onlineUsers.getJSONObject(i);
                    if (user.getString("userId").equals(paseadorId)) {
                        isOnline = true;
                        if (user.has("lastActivity")) {
                            lastActivity = user.getLong("lastActivity");
                        }
                        break;
                    }
                }

                // If not online, check offline users for lastActivity
                if (!isOnline) {
                    for (int i = 0; i < offlineUsers.length(); i++) {
                        JSONObject user = offlineUsers.getJSONObject(i);
                        if (user.getString("userId").equals(paseadorId)) {
                            if (user.has("lastActivity") && !user.isNull("lastActivity")) {
                                lastActivity = user.getLong("lastActivity");
                            }
                            break;
                        }
                    }
                }

                final boolean finalIsOnline = isOnline;
                final Long finalLastActivity = lastActivity;

                runOnUiThread(() -> {
                    if (finalIsOnline) {
                        badgePerfilEnLinea.setVisibility(View.VISIBLE);
                    } else {
                        badgePerfilEnLinea.setVisibility(View.GONE);
                        // Update ultima_conexion text with last activity if available
                        if (finalLastActivity != null) {
                            String relativeTime = DateUtils.getRelativeTimeSpanString(
                                finalLastActivity,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS
                            ).toString();
                            tvUltimaConexion.setText("Activo " + relativeTime.toLowerCase());
                        } else {
                            reloadUltimaConexion();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error procesando online_users_response", e);
            }
        });

        // Query initial status and subscribe to presence updates
        socketManager.getOnlineUsers(new String[]{paseadorId});
        socketManager.subscribePresence(new String[]{paseadorId});
        Log.d(TAG, "👁️ Presencia configurada para paseador: " + paseadorId);
    }

    private void cleanupPresenceListeners() {
        if (socketManager != null && !isOwnProfile && paseadorId != null) {
            socketManager.off("user_connected");
            socketManager.off("user_disconnected");
            socketManager.off("online_users_response");
            socketManager.unsubscribePresence(new String[]{paseadorId});
            Log.d(TAG, "👁️ Limpieza de presencia para paseador: " + paseadorId);
        }
    }

    private void reloadUltimaConexion() {
        if (paseadorId == null) return;
        db.collection("usuarios").document(paseadorId).get()
            .addOnSuccessListener(doc -> {
                if (doc != null && doc.exists()) {
                    Timestamp ultimaConexion = doc.getTimestamp("ultima_conexion");
                    if (ultimaConexion != null) {
                        tvUltimaConexion.setText(String.format("Últ. conexión: %s", formatUltimaConexion(ultimaConexion)));
                        tvUltimaConexion.setTextColor(getColor(R.color.gray_text));
                    }
                }
            })
            .addOnFailureListener(e -> Log.w(TAG, "Error recargando ultima_conexion", e));
    }

    private void showContent() {
        if (!isContentVisible) {
            isContentVisible = true;
            skeletonLayout.setVisibility(View.GONE);
            scrollViewContent.setVisibility(View.VISIBLE);
        }
    }

    private void cargarDisponibilidad(String paseadorId) {
        if (disponibilidadListener != null) disponibilidadListener.remove();

        // Intentar cargar primero el horario estándar estructurado
        disponibilidadListener = db.collection("paseadores").document(paseadorId)
                .collection("disponibilidad").document("horario_default")
                .addSnapshotListener((doc, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Error escuchando horario_default", e);
                        tvDisponibilidad.setText("No especificada");
                        return;
                    }

                    if (doc != null && doc.exists()) {
                        // Lógica para nuevo formato (Mapas por día)
                        List<String> diasActivos = new ArrayList<>();
                        String horaInicio = null;
                        String horaFin = null;
                        String[] diasKeys = {"lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"};
                        String[] diasLabels = {"Lun", "Mar", "Mie", "Jue", "Vie", "Sab", "Dom"};

                        for (int i = 0; i < diasKeys.length; i++) {
                            Map<String, Object> diaData = (Map<String, Object>) doc.get(diasKeys[i]);
                            if (diaData != null && Boolean.TRUE.equals(diaData.get("disponible"))) {
                                diasActivos.add(diasLabels[i]);
                                if (horaInicio == null) {
                                    horaInicio = (String) diaData.get("hora_inicio");
                                    horaFin = (String) diaData.get("hora_fin");
                                }
                            }
                        }

                        if (!diasActivos.isEmpty()) {
                            tvDisponibilidad.setText(String.format("%s: %s - %s", android.text.TextUtils.join(", ", diasActivos), horaInicio, horaFin));
                        } else {
                            tvDisponibilidad.setText("No disponible temporalmente");
                        }
                    } else {
                        // Fallback: Buscar formato antiguo (lista 'dias') en cualquier documento
                        db.collection("paseadores").document(paseadorId).collection("disponibilidad")
                            .limit(1).get()
                            .addOnSuccessListener(querySnapshot -> {
                                if (!querySnapshot.isEmpty()) {
                                    DocumentSnapshot fallbackDoc = querySnapshot.getDocuments().get(0);
                                    List<String> dias = (List<String>) fallbackDoc.get("dias");
                                    String hInicio = fallbackDoc.getString("hora_inicio");
                                    String hFin = fallbackDoc.getString("hora_fin");
                                    
                                    if (dias != null && !dias.isEmpty() && hInicio != null && hFin != null) {
                                        tvDisponibilidad.setText(String.format("%s: %s - %s", android.text.TextUtils.join(", ", dias), hInicio, hFin));
                                    } else {
                                         tvDisponibilidad.setText("No especificada");
                                    }
                                } else {
                                    tvDisponibilidad.setText("No especificada");
                                }
                            });
                    }
                });
    }

    @SuppressWarnings("deprecation")
    private void cargarZonasServicio(String paseadorId) {
        if (zonasListener != null) zonasListener.remove();
        zonasListener = db.collection("paseadores").document(paseadorId).collection("zonas_servicio")
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null || googleMap == null) return;
                    googleMap.clear();
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        tvZonasServicioNombres.setText("No especificadas");
                        return;
                    }
                    LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                    List<String> nombresZonas = new ArrayList<>();
                    Geocoder geocoder = new Geocoder(this, new Locale("es", "ES"));
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        GeoPoint centro = doc.getGeoPoint("centro");
                        Double radioKm = doc.getDouble("radio_km");
                        if (centro != null && radioKm != null) {
                            LatLng latLng = new LatLng(centro.getLatitude(), centro.getLongitude());
                            googleMap.addCircle(new CircleOptions().center(latLng).radius(radioKm * 1000).strokeColor(Color.argb(128, 0, 150, 136)).fillColor(Color.argb(64, 0, 150, 136)));
                            boundsBuilder.include(latLng);
                            try {
                                List<Address> addresses = geocoder.getFromLocation(centro.getLatitude(), centro.getLongitude(), 1);
                                if (addresses != null && !addresses.isEmpty()) {
                                    String nombreZona = addresses.get(0).getSubLocality();
                                    if (nombreZona == null) nombreZona = addresses.get(0).getLocality();
                                    if (nombreZona != null && !nombresZonas.contains(nombreZona)) {
                                        nombresZonas.add(nombreZona);
                                    }
                                }
                            } catch (IOException ex) {
                                Log.e(TAG, "Error en Geocoder", ex);
                            }
                        }
                    }
                    tvZonasServicioNombres.setText(!nombresZonas.isEmpty() ? android.text.TextUtils.join(", ", nombresZonas) : "Nombres no disponibles");
                    if(querySnapshot.size() > 0) {
                       try {
                           LatLngBounds bounds = boundsBuilder.build();
                           // Check if bounds are too small (effectively a single point)
                           if (bounds.northeast.latitude == bounds.southwest.latitude && 
                               bounds.northeast.longitude == bounds.southwest.longitude) {
                               googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.getCenter(), 14));
                           } else {
                               googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                           }
                       } catch (IllegalStateException ex) {
                           Log.e(TAG, "No hay zonas para mostrar en el mapa.", ex);
                       }
                    }
                });
    }

    private void cargarMetodoPagoPredeterminado(String uid) {
        if (uid == null) return;
        if (metodoPagoListener != null) metodoPagoListener.remove();
        metodoPagoListener = db.collection("usuarios").document(uid).collection("metodos_pago")
                .whereEqualTo("predeterminado", true).limit(1)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e == null && queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        metodoPagoId = queryDocumentSnapshots.getDocuments().get(0).getId();
                    }
                });
    }

    private void setupResenasRecyclerView() {
        recyclerViewResenas.setLayoutManager(new LinearLayoutManager(this));
        resenaAdapter = new ResenaAdapter(this, resenasList);
        recyclerViewResenas.setAdapter(resenaAdapter);
    }

    private void cargarMasResenas(int limit) {
        if (isLoadingResenas) return;
        isLoadingResenas = true;
        
        Query query = db.collection("resenas_paseadores")
                .whereEqualTo("paseadorId", paseadorId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit);
                
        if (lastVisibleResena != null) {
            query = query.startAfter(lastVisibleResena);
        }
        
        query.get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                isLoadingResenas = false;
                if (resenasList.isEmpty()) {
                    llEmptyReviews.setVisibility(View.VISIBLE);
                    recyclerViewResenas.setVisibility(View.GONE);
                }
                return;
            }
            
            llEmptyReviews.setVisibility(View.GONE);
            recyclerViewResenas.setVisibility(View.VISIBLE);
            
            lastVisibleResena = queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
            
            List<Resena> nuevasResenas = new ArrayList<>();
            List<Task<DocumentSnapshot>> userTasks = new ArrayList<>();
            
            int fetchCount = queryDocumentSnapshots.size();
            boolean hasMore = false;
            
            if (limit == 4 && fetchCount == 4) {
                fetchCount = 3; 
                hasMore = true;
                lastVisibleResena = queryDocumentSnapshots.getDocuments().get(2);
            }

            for (int i = 0; i < fetchCount; i++) {
                DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(i);
                Resena resena = new Resena();
                resena.setId(doc.getId());
                resena.setComentario(doc.getString("comentario"));
                Double calif = doc.getDouble("calificacion");
                resena.setCalificacion(calif != null ? calif.floatValue() : 0f);
                resena.setFecha(doc.getTimestamp("timestamp"));
                
                nuevasResenas.add(resena);
                
                String autorId = doc.getString("duenoId");
                if (autorId == null) autorId = doc.getString("autorId");
                
                if (autorId != null) {
                    userTasks.add(db.collection("usuarios").document(autorId).get());
                } else {
                    userTasks.add(Tasks.forResult(null));
                }
            }
            
            final boolean showButton = hasMore;

            if (!userTasks.isEmpty()) {
                Tasks.whenAllSuccess(userTasks).addOnSuccessListener(userDocs -> {
                    for (int i = 0; i < userDocs.size(); i++) {
                        if (userDocs.get(i) instanceof DocumentSnapshot) {
                            DocumentSnapshot userDoc = (DocumentSnapshot) userDocs.get(i);
                            if (userDoc != null && userDoc.exists()) {
                                nuevasResenas.get(i).setAutorNombre(userDoc.getString("nombre_display"));
                                nuevasResenas.get(i).setAutorFotoUrl(userDoc.getString("foto_perfil"));
                            } else {
                                nuevasResenas.get(i).setAutorNombre("Usuario de Walki");
                            }
                        }
                    }
                    resenaAdapter.addResenas(nuevasResenas);
                    isLoadingResenas = false;
                    
                    if (showButton) {
                        btnVerMasResenas.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                resenaAdapter.addResenas(nuevasResenas);
                isLoadingResenas = false;
                if (showButton) {
                    btnVerMasResenas.setVisibility(View.VISIBLE);
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error cargando reseñas", e);
            isLoadingResenas = false;
        });
    }

    private String formatMiembroDesde(Timestamp timestamp) {
        return new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES")).format(timestamp.toDate());
    }

    private String formatUltimaConexion(Timestamp timestamp) {
        return DateUtils.getRelativeTimeSpanString(timestamp.toDate().getTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
    }

    // Helper method to recursively set OnTouchListener to all views in a hierarchy
    private void setTouchListenerRecursive(View view, View.OnTouchListener listener) {
        if (view == null) return;
        view.setOnTouchListener(listener);
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                setTouchListenerRecursive(viewGroup.getChildAt(i), listener);
            }}
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        this.googleMap.getUiSettings().setAllGesturesEnabled(true);
        this.googleMap.getUiSettings().setZoomControlsEnabled(true); // This line enables the +/- buttons

        // Apply recursive touch listener to map view
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_container);
        if (mapFragment != null && mapFragment.getView() != null) {
            setTouchListenerRecursive(mapFragment.getView(), (v, event) -> {
                int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        // Disallow parent NestedScrollView to intercept
                        if (scrollViewContent != null) scrollViewContent.requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // Allow parent NestedScrollView to intercept again
                        if (scrollViewContent != null) scrollViewContent.requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false; // IMPORTANT: Return false so the event still reaches the map
            });
        }

        if (paseadorId == null) {
             this.googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-0.180653, -78.467834), 12));
        }
    }
}

