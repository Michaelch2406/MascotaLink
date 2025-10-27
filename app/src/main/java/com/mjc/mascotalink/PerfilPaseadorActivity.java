package com.mjc.mascotalink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
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
import com.google.firebase.storage.FirebaseStorage;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.GaleriaActivity;
import com.mjc.mascota.modelo.Resena;
import com.mjc.mascota.ui.perfil.ResenaAdapter;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;

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

    // Views
    private Toolbar toolbar;
    private TextView toolbarTitle;
    private ImageView ivBack;
    private de.hdodenhof.circleimageview.CircleImageView ivAvatar;
    private Button btnVerGaleria;
    private ImageButton btnFavorito;
    private ImageView ivVerificadoBadge, ivVideoThumbnail, ivPlayButton;
    private TextView tvNombre, tvRol, tvVerificado, tvPrecio, tvDescripcion, tvDisponibilidad, tvTiposPerros, tvZonasServicioNombres;
    private TextView tvExperienciaAnos, tvExperienciaDesde;
    private TextView tvPaseosCompletados, tvTiempoRespuesta, tvUltimaConexion, tvMiembroDesde;
    private TextView tvRatingValor, tvResenasTotal;
    private RatingBar ratingBar;
    private LinearLayout barrasRatings, llAcercaDe, llResenas, ajustes_section, soporte_section;
    private FrameLayout videoContainer;
    private android.widget.VideoView videoView;
    private android.widget.ProgressBar videoProgressBar;
    private TabLayout tabLayout;
    private Button btnCerrarSesion;
    private ImageButton btnMensaje;
    private FloatingActionButton fabReservar;
    private ImageView ivEditZonas, ivEditDisponibilidad;
    private ImageButton ivEditPerfil;
    private GoogleMap googleMap;
    private List<Circle> mapCircles = new ArrayList<>();
    private View btnNotificaciones, btnMetodosPago, btnPrivacidad, btnCentroAyuda, btnTerminos;
    private String metodoPagoId;
    private View skeletonLayout;
    private NestedScrollView scrollViewContent;
    private boolean isContentVisible = false;
    private String paseadorId;
    private String currentUserId;
    private String currentUserRole;
    private String videoUrl;

    private RecyclerView recyclerViewResenas;
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


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_paseador);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        setupToolbar();
        setupListeners();
        setupTabs();
        setupResenasRecyclerView();
        setupAuthListener();

        paseadorId = getIntent().getStringExtra("paseadorId");
    }

    private void setupAuthListener() {
        mAuthListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                // User is signed in
                currentUserId = user.getUid();
                if (paseadorId == null) {
                    paseadorId = currentUserId; // Default to viewing own profile
                }
                fetchCurrentUserRoleAndSetupUI();
                attachDataListeners();
            } else {
                // User is signed out
                currentUserId = null;
                currentUserRole = null;
                // If we are on a profile screen and the user signs out, redirect to Login
                if (!(this instanceof LoginActivity)) {
                    Intent intent = new Intent(PerfilPaseadorActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }
        };
    }

    private void fetchCurrentUserRoleAndSetupUI() {
        if (currentUserId == null) {
            setupRoleBasedUI(); // Setup as guest
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
                    setupRoleBasedUI(); // Setup UI even if role fetch fails (read-only mode)
                });
    }


    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Clear Glide load to prevent crash on destroy
        if (ivAvatar != null) {
            Glide.with(this).clear(ivAvatar);
        }
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
        detachDataListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbarTitle = findViewById(R.id.toolbar_title);
        ivBack = findViewById(R.id.iv_back);
        ivAvatar = findViewById(R.id.iv_avatar);
        btnVerGaleria = findViewById(R.id.btn_ver_galeria);
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
        tvExperienciaAnos = findViewById(R.id.tv_experiencia_anos);
        tvExperienciaDesde = findViewById(R.id.tv_experiencia_desde);
        tvDisponibilidad = findViewById(R.id.tv_disponibilidad);
        tvTiposPerros = findViewById(R.id.tv_tipos_perros);
        tvZonasServicioNombres = findViewById(R.id.tv_zonas_servicio_nombres);
        tvPaseosCompletados = findViewById(R.id.tv_paseos_completados);
        tvTiempoRespuesta = findViewById(R.id.tv_tiempo_respuesta);
        tvUltimaConexion = findViewById(R.id.tv_ultima_conexion);
        tvMiembroDesde = findViewById(R.id.tv_miembro_desde);
        tvRatingValor = findViewById(R.id.tv_rating_valor);
        ratingBar = findViewById(R.id.rating_bar);
        tvResenasTotal = findViewById(R.id.tv_resenas_total);
        barrasRatings = findViewById(R.id.barras_ratings);
        btnNotificaciones = findViewById(R.id.btn_notificaciones);
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

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_container);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false); // Use custom TextView for title
        ivBack.setOnClickListener(v -> finish());
    }

    private void setupRoleBasedUI() {
        boolean isOwnProfile = paseadorId.equals(currentUserId);

        // Set Toolbar Title
        if (isOwnProfile) {
            toolbarTitle.setText("Perfil");
        } else {
            toolbarTitle.setText("Paseador");
        }

        // Configure button visibility based on role
        if (isOwnProfile) {
            // Case 1: PASEADOR viewing their own profile
            ivEditPerfil.setVisibility(View.VISIBLE);
            btnMensaje.setVisibility(View.GONE);
            btnFavorito.setVisibility(View.GONE); // Cannot favorite oneself
            fabReservar.setVisibility(View.GONE);

            ivEditZonas.setVisibility(View.VISIBLE);
            ivEditDisponibilidad.setVisibility(View.VISIBLE);
            ajustes_section.setVisibility(View.VISIBLE);
            soporte_section.setVisibility(View.VISIBLE);
            btnCerrarSesion.setVisibility(View.VISIBLE);

        } else if ("DUEÑO".equals(currentUserRole)) {
            // Case 2: DUEÑO viewing a PASEADOR's profile
            ivEditPerfil.setVisibility(View.GONE);
            btnMensaje.setVisibility(View.VISIBLE);
            btnFavorito.setVisibility(View.VISIBLE); // Can add to favorites
            fabReservar.setVisibility(View.VISIBLE);

            ivEditZonas.setVisibility(View.GONE);
            ivEditDisponibilidad.setVisibility(View.GONE);
            ajustes_section.setVisibility(View.GONE);
            soporte_section.setVisibility(View.GONE);
            btnCerrarSesion.setVisibility(View.GONE);

            configurarBotonFavorito(currentUserId, paseadorId);

        } else {
            // Case 3: Read-only view (not logged in, another paseador, etc.)
            ivEditPerfil.setVisibility(View.GONE);
            btnMensaje.setVisibility(View.GONE);
            btnFavorito.setVisibility(View.GONE);
            fabReservar.setVisibility(View.GONE);

            ivEditZonas.setVisibility(View.GONE);
            ivEditDisponibilidad.setVisibility(View.GONE);
            ajustes_section.setVisibility(View.GONE);
            soporte_section.setVisibility(View.GONE);
            btnCerrarSesion.setVisibility(View.GONE);
        }
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

        // Set click listener to toggle favorite status
        btnFavorito.setOnClickListener(v -> toggleFavorito(duenoId, paseadorId, favRef));
    }

    private void toggleFavorito(String duenoId, String paseadorId, DocumentReference favRef) {
        favRef.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                // El paseador ya está en favoritos, así que lo eliminamos
                favRef.delete().addOnSuccessListener(aVoid -> {
                    btnFavorito.setImageResource(R.drawable.ic_corazon);
                    Toast.makeText(this, "Eliminado de favoritos", Toast.LENGTH_SHORT).show();
                });
            } else {
                // El paseador no está en favoritos, así que lo agregamos con datos desnormalizados
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


    private void setupListeners() {
        ivEditPerfil.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilPaseadorActivity.this, EditarPerfilPaseadorActivity.class);
            intent.putExtra("paseadorId", paseadorId);
            startActivity(intent);
        });

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
                    // Opcional: volver a mostrar la miniatura cuando el video termina
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
            Toast.makeText(this, "Próximamente: Iniciar chat", Toast.LENGTH_SHORT).show();
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

        fabReservar.setOnClickListener(v -> Toast.makeText(this, "Próximamente: Reservar", Toast.LENGTH_SHORT).show());

        ivEditZonas.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilPaseadorActivity.this, ZonasServicioActivity.class);
            startActivity(intent);
        });

        ivEditDisponibilidad.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilPaseadorActivity.this, DisponibilidadActivity.class);
            startActivity(intent);
        });

        btnCerrarSesion.setOnClickListener(v -> {
            // Detach listeners FIRST to prevent PERMISSION_DENIED noise on logout
            detachDataListeners();

            // Limpiar preferencias de "recordar sesión"
            SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
            prefs.edit().clear().apply();

            // Sign out AFTER detaching listeners. The AuthStateListener will handle navigation.
            mAuth.signOut();
        });

        btnNotificaciones.setOnClickListener(v -> Toast.makeText(this, "Próximamente: Notificaciones", Toast.LENGTH_SHORT).show());
        btnMetodosPago.setOnClickListener(v -> {
             Intent intent = new Intent(PerfilPaseadorActivity.this, MetodoPagoActivity.class);
             if (metodoPagoId != null && !metodoPagoId.isEmpty()) {
                 intent.putExtra("metodo_pago_id", metodoPagoId);
             }
             startActivity(intent);
         });
        btnPrivacidad.setOnClickListener(v -> Toast.makeText(this, "Próximamente: Privacidad", Toast.LENGTH_SHORT).show());
        btnCentroAyuda.setOnClickListener(v -> Toast.makeText(this, "Próximamente: Centro de Ayuda", Toast.LENGTH_SHORT).show());
        btnTerminos.setOnClickListener(v -> Toast.makeText(this, "Próximamente: Términos y Condiciones", Toast.LENGTH_SHORT).show());


        scrollViewContent.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (v.getChildAt(v.getChildCount() - 1) != null) {
                if ((scrollY >= (v.getChildAt(v.getChildCount() - 1).getMeasuredHeight() - v.getMeasuredHeight())) &&
                        scrollY > oldScrollY) {
                    cargarMasResenas();
                }
            }
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.menu_perfil);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_home) {
                Toast.makeText(this, "Próximamente: Inicio", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.menu_search) {
                Intent intent = new Intent(this, BusquedaPaseadoresActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.menu_walks) {
                Toast.makeText(this, "Próximamente: Paseos", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.menu_messages) {
                Toast.makeText(this, "Próximamente: Mensajes", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.menu_perfil) {
                return true; // Already here
            }
            return false;
        });
    }

    private void setupTabs() {
        llAcercaDe.setVisibility(View.VISIBLE);
        llResenas.setVisibility(View.GONE);
        tabLayout.addTab(tabLayout.newTab().setText("Acerca de"), true);
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
                        cargarMasResenas();
                    }
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void attachDataListeners() {
        detachDataListeners(); // Ensure no duplicate listeners

        // Listener for the 'usuarios' document
        DocumentReference userDocRef = db.collection("usuarios").document(paseadorId);
        usuarioListener = userDocRef.addSnapshotListener((usuarioDoc, e) -> {
            if (e != null) {
                Log.e(TAG, "Error al escuchar documento de usuario", e);
                return;
            }
            if (usuarioDoc != null && usuarioDoc.exists()) {
                tvNombre.setText(usuarioDoc.getString("nombre_display"));
                Glide.with(this).load(usuarioDoc.getString("foto_perfil")).placeholder(R.drawable.ic_person).into(ivAvatar);
                Timestamp fechaRegistro = usuarioDoc.getTimestamp("fecha_registro");
                if (fechaRegistro != null) {
                    tvMiembroDesde.setText(String.format("Se unió en %s", formatMiembroDesde(fechaRegistro)));
                }
                Timestamp ultimaConexion = usuarioDoc.getTimestamp("ultima_conexion");
                if (ultimaConexion != null) {
                    tvUltimaConexion.setText(String.format("Últ. conexión: %s", formatUltimaConexion(ultimaConexion)));
                }
                String galeriaFolderPath = usuarioDoc.getString("galeria_paseos");
                if (galeriaFolderPath != null && !galeriaFolderPath.isEmpty()) {
                    btnVerGaleria.setVisibility(View.VISIBLE);
                    FirebaseStorage.getInstance().getReference().child(galeriaFolderPath).listAll()
                        .addOnSuccessListener(listResult -> {
                            List<Task<android.net.Uri>> urlTasks = new ArrayList<>();
                            for (com.google.firebase.storage.StorageReference item : listResult.getItems()) {
                                urlTasks.add(item.getDownloadUrl());
                            }
                            Tasks.whenAllSuccess(urlTasks).addOnSuccessListener(urls -> {
                                galeriaImageUrls.clear();
                                for (Object urlObject : urls) {
                                    galeriaImageUrls.add(((android.net.Uri) urlObject).toString());
                                }
                            });
                        });
                } else {
                    btnVerGaleria.setVisibility(View.GONE);
                }
                showContent();
            }
        });

        // Listener for the 'paseadores' document
        DocumentReference paseadorDocRef = db.collection("paseadores").document(paseadorId);
        paseadorListener = paseadorDocRef.addSnapshotListener((paseadorDoc, error) -> {
            if (error != null) {
                Log.e(TAG, "Error al escuchar documento de paseador", error);
                return;
            }
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
                tvPrecio.setText(precio != null ? String.format(Locale.getDefault(), "• $%.2f/hora", precio) : "");
                tvPrecio.setVisibility(precio != null ? View.VISIBLE : View.GONE);

                Long paseos = paseadorDoc.getLong("num_servicios_completados");
                tvPaseosCompletados.setText(String.format(Locale.getDefault(), "%d Paseos completados", paseos != null ? paseos : 0));

                Long tiempoRespuesta = paseadorDoc.getLong("tiempo_respuesta_promedio_min");
                tvTiempoRespuesta.setText(tiempoRespuesta != null ? String.format(Locale.getDefault(), "Respuesta en %d min", tiempoRespuesta) : "Respuesta en -- min");

                Double rating = paseadorDoc.getDouble("calificacion_promedio");
                Long numResenas = paseadorDoc.getLong("num_resenas");
                tvRatingValor.setText(rating != null ? String.format(Locale.getDefault(), "%.1f", rating) : "0.0");
                ratingBar.setRating(rating != null ? rating.floatValue() : 0f);
                tvResenasTotal.setText(String.format(Locale.getDefault(), "%d reviews", numResenas != null ? numResenas : 0));

                Map<String, Object> perfil = (Map<String, Object>) paseadorDoc.get("perfil_profesional");
                if (perfil != null) {
                    tvDescripcion.setText((String) perfil.get("motivacion"));
                    videoUrl = (String) perfil.get("video_presentacion_url");
                    Glide.with(this).load(videoUrl).centerCrop().placeholder(R.drawable.galeria_paseos_foto1).into(ivVideoThumbnail);
                    Timestamp inicioExpTimestamp = (Timestamp) perfil.get("fecha_inicio_experiencia");
                    if (inicioExpTimestamp != null) {
                        int years = Period.between(inicioExpTimestamp.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now()).getYears();
                        tvExperienciaAnos.setText(String.format(Locale.getDefault(), "Experiencia: %d años", years));
                        tvExperienciaAnos.setVisibility(View.VISIBLE);
                    } else {
                        tvExperienciaAnos.setVisibility(View.GONE);
                    }
                    tvExperienciaDesde.setVisibility(View.GONE);
                }

                Map<String, Object> manejo = (Map<String, Object>) paseadorDoc.get("manejo_perros");
                if (manejo != null) {
                    List<String> tamanos = (List<String>) manejo.get("tamanos");
                    tvTiposPerros.setText(tamanos != null && !tamanos.isEmpty() ? android.text.TextUtils.join(", ", tamanos) : "No especificado");
                }
                showContent();
            }
        });

        // Listeners for sub-collections
        cargarDisponibilidad(paseadorId);
        cargarZonasServicio(paseadorId);
        cargarMetodoPagoPredeterminado(currentUserId != null ? currentUserId : paseadorId);
    }

    private void detachDataListeners() {
        if (usuarioListener != null) usuarioListener.remove();
        if (paseadorListener != null) paseadorListener.remove();
        if (disponibilidadListener != null) disponibilidadListener.remove();
        if (zonasListener != null) zonasListener.remove();
        if (metodoPagoListener != null) metodoPagoListener.remove();
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
        disponibilidadListener = db.collection("paseadores").document(paseadorId).collection("disponibilidad")
                .limit(1).addSnapshotListener((querySnapshot, e) -> {
                    if (e != null || querySnapshot == null || querySnapshot.isEmpty()) {
                        tvDisponibilidad.setText("No especificada");
                        return;
                    }
                    DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                    List<String> dias = (List<String>) doc.get("dias");
                    String horaInicio = doc.getString("hora_inicio");
                    String horaFin = doc.getString("hora_fin");
                    if (dias != null && !dias.isEmpty() && horaInicio != null && horaFin != null) {
                        tvDisponibilidad.setText(String.format("%s: %s - %s", android.text.TextUtils.join(", ", dias), horaInicio, horaFin));
                    } else {
                        tvDisponibilidad.setText("No especificada");
                    }
                });
    }

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
                           googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100));
                       } catch (IllegalStateException ex) {
                           Log.e(TAG, "No hay zonas para mostrar en el mapa.", ex);
                       }
                    }
                });
    }

    private void cargarMetodoPagoPredeterminado(String uid) {
        if (uid == null) return;
        if (metodoPagoListener != null) metodoPagoListener.remove();
        db.collection("usuarios").document(uid).collection("metodos_pago")
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

    private void cargarMasResenas() {
        if (isLoadingResenas) return;
        isLoadingResenas = true;
        Query query = db.collection("resenas").whereEqualTo("paseador_ref", db.collection("paseadores").document(paseadorId)).orderBy("fecha_creacion", Query.Direction.DESCENDING).limit(10);
        if (lastVisibleResena != null) {
            query = query.startAfter(lastVisibleResena);
        }
        query.get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                isLoadingResenas = false;
                return;
            }
            lastVisibleResena = queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
            List<Resena> nuevasResenas = new ArrayList<>();
            List<Task<DocumentSnapshot>> userTasks = new ArrayList<>();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                Resena resena = doc.toObject(Resena.class);
                resena.setId(doc.getId());
                nuevasResenas.add(resena);
                DocumentReference userRef = doc.getDocumentReference("dueño_ref");
                if (userRef != null) userTasks.add(userRef.get());
            }
            if (!userTasks.isEmpty()) {
                Tasks.whenAllSuccess(userTasks).addOnSuccessListener(userDocs -> {
                    for (int i = 0; i < userDocs.size(); i++) {
                        DocumentSnapshot userDoc = (DocumentSnapshot) userDocs.get(i);
                        if (userDoc.exists()) {
                            nuevasResenas.get(i).setAutorNombre(userDoc.getString("nombre_display"));
                            nuevasResenas.get(i).setAutorFotoUrl(userDoc.getString("foto_perfil"));
                        }
                    }
                    resenaAdapter.addResenas(nuevasResenas);
                    isLoadingResenas = false;
                });
            } else {
                isLoadingResenas = false;
            }
        }).addOnFailureListener(e -> isLoadingResenas = false);
    }

    private String formatMiembroDesde(Timestamp timestamp) {
        return new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES")).format(timestamp.toDate());
    }

    private String formatUltimaConexion(Timestamp timestamp) {
        return DateUtils.getRelativeTimeSpanString(timestamp.toDate().getTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        this.googleMap.getUiSettings().setAllGesturesEnabled(true);
        this.googleMap.getUiSettings().setZoomControlsEnabled(true);
        if (paseadorId == null) {
             this.googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-0.180653, -78.467834), 12));
        }
    }
}
