package com.mjc.mascotalink;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.MotionEvent;
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
import java.io.IOException;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PerfilPaseadorActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "PerfilPaseadorActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Views
    private ImageView ivAvatar, ivVerificadoBadge, ivVideoThumbnail;
    private TextView tvNombre, tvRol, tvVerificado, tvPrecio, tvDescripcion, tvDisponibilidad, tvTiposPerros, tvZonasServicioNombres;
    private TextView tvExperienciaAnos, tvExperienciaDesde;
    private TextView tvPaseosCompletados, tvTiempoRespuesta, tvUltimaConexion, tvMiembroDesde;
    private TextView tvRatingValor, tvResenasTotal;
    private RatingBar ratingBar;
    private LinearLayout barrasRatings, llAcercaDe, llResenas;
    private FrameLayout videoContainer;
    private TabLayout tabLayout;
    private Button btnCerrarSesion;
    private ImageButton btnMensaje;
    private FloatingActionButton fabReservar;
    private GoogleMap googleMap;
    private List<Circle> mapCircles = new ArrayList<>();
    private View btnEditarPerfil, btnNotificaciones, btnMetodosPago, btnPrivacidad, btnCentroAyuda, btnTerminos;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_paseador);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        setupListeners();
        setupTabs();

        String paseadorId = getIntent().getStringExtra("paseadorId");
        String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        if (paseadorId == null && currentUserId != null) {
            paseadorId = currentUserId;
        }

        if (paseadorId != null) {
            if (paseadorId.equals(currentUserId)) {
                // Es el perfil del propio usuario, ocultar botones de acción
                fabReservar.setVisibility(View.GONE);
                btnMensaje.setVisibility(View.GONE);
            } else {
                // Es el perfil de otro paseador, verificar rol del usuario actual
                if (currentUserId != null) {
                    db.collection("usuarios").document(currentUserId).get().addOnSuccessListener(documentSnapshot -> {
                        String userRole = documentSnapshot.getString("rol");
                        if ("dueno".equals(userRole)) {
                            fabReservar.setVisibility(View.VISIBLE);
                            btnMensaje.setVisibility(View.VISIBLE);
                        } else {
                            fabReservar.setVisibility(View.GONE);
                            btnMensaje.setVisibility(View.GONE);
                        }
                    });
                } else {
                    // No hay usuario logueado, ocultar
                    fabReservar.setVisibility(View.GONE);
                    btnMensaje.setVisibility(View.GONE);
                }
            }
            cargarPerfilCompleto(paseadorId);
        } else {
            Toast.makeText(this, "Error: No se pudo cargar el perfil.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String paseadorId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (paseadorId != null) {
            cargarPerfilCompleto(paseadorId);
        }
    }

    private void initViews() {
        ivAvatar = findViewById(R.id.iv_avatar);
        tvNombre = findViewById(R.id.tv_nombre);
        tvRol = findViewById(R.id.tv_rol);
        ivVerificadoBadge = findViewById(R.id.iv_verificado_badge);
        tvVerificado = findViewById(R.id.tv_verificado);
        tvPrecio = findViewById(R.id.tv_precio);
        tabLayout = findViewById(R.id.tab_layout);
        llAcercaDe = findViewById(R.id.ll_acerca_de);
        llResenas = findViewById(R.id.ll_resenas);

        // "Acerca de" views
        videoContainer = findViewById(R.id.video_container);
        ivVideoThumbnail = findViewById(R.id.iv_video_thumbnail);
        tvDescripcion = findViewById(R.id.tv_descripcion);
        tvExperienciaAnos = findViewById(R.id.tv_experiencia_anos);
        tvExperienciaDesde = findViewById(R.id.tv_experiencia_desde);
        tvDisponibilidad = findViewById(R.id.tv_disponibilidad);
        tvTiposPerros = findViewById(R.id.tv_tipos_perros);
        tvZonasServicioNombres = findViewById(R.id.tv_zonas_servicio_nombres);

        // Estadisticas
        tvPaseosCompletados = findViewById(R.id.tv_paseos_completados);
        tvTiempoRespuesta = findViewById(R.id.tv_tiempo_respuesta);
        tvUltimaConexion = findViewById(R.id.tv_ultima_conexion);
        tvMiembroDesde = findViewById(R.id.tv_miembro_desde);

        // "Reseñas" views
        tvRatingValor = findViewById(R.id.tv_rating_valor);
        ratingBar = findViewById(R.id.rating_bar);
        tvResenasTotal = findViewById(R.id.tv_resenas_total);
        barrasRatings = findViewById(R.id.barras_ratings);

        // Ajustes
        btnEditarPerfil = findViewById(R.id.btn_editar_perfil);
        btnNotificaciones = findViewById(R.id.btn_notificaciones);
        btnMetodosPago = findViewById(R.id.btn_metodos_pago);
        btnPrivacidad = findViewById(R.id.btn_privacidad);
        btnCentroAyuda = findViewById(R.id.btn_centro_ayuda);
        btnTerminos = findViewById(R.id.btn_terminos);
        btnCerrarSesion = findViewById(R.id.btn_cerrar_sesion);

        // New buttons
        fabReservar = findViewById(R.id.fab_reservar);
        btnMensaje = findViewById(R.id.btn_mensaje);

        // Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_container);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

    }

    @SuppressWarnings("unchecked")
    private void setupListeners() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        fabReservar.setOnClickListener(v -> showToast("Próximamente: Reservar"));
        btnMensaje.setOnClickListener(v -> showToast("Próximamente: Enviar Mensaje"));

        fabReservar.setOnTouchListener(new View.OnTouchListener() {
            private int lastAction;
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            @SuppressWarnings("unchecked")
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = v.getLeft();
                        initialY = v.getTop();
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        lastAction = MotionEvent.ACTION_DOWN;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        v.setX(initialX + (event.getRawX() - initialTouchX));
                        v.setY(initialY + (event.getRawY() - initialTouchY));
                        lastAction = MotionEvent.ACTION_MOVE;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            v.performClick();
                        }
                        break;
                    default:
                        return false;
                }
                return true;
            }
        });

        btnEditarPerfil.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilPaseadorActivity.this, EditarPerfilPaseadorActivity.class);
            startActivity(intent);
        });
        btnNotificaciones.setOnClickListener(v -> showToast("Próximamente: Notificaciones"));
        btnMetodosPago.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilPaseadorActivity.this, MetodoPagoActivity.class);
            startActivity(intent);
        });
        btnPrivacidad.setOnClickListener(v -> showToast("Próximamente: Privacidad"));
        btnCentroAyuda.setOnClickListener(v -> showToast("Próximamente: Centro de Ayuda"));
        btnTerminos.setOnClickListener(v -> showToast("Próximamente: Términos y Condiciones"));

        btnCerrarSesion.setOnClickListener(v -> {
            // Limpiar preferencias de "recordar sesión"
            getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE).edit().clear().apply();

            mAuth.signOut();
            Intent intent = new Intent(PerfilPaseadorActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.menu_perfil);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_home) {
                showToast("Próximamente: Inicio");
                return true;
            } else if (itemId == R.id.menu_search) {
                showToast("Próximamente: Buscar");
                return true;
            } else if (itemId == R.id.menu_walks) {
                showToast("Próximamente: Paseos");
                return true;
            } else if (itemId == R.id.menu_messages) {
                showToast("Próximamente: Mensajes");
                return true;
            } else if (itemId == R.id.menu_perfil) {
                // Ya estamos aquí
                return true;
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
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("unchecked")
    private void cargarPerfilCompleto(String paseadorId) {
        Task<DocumentSnapshot> usuarioTask = db.collection("usuarios").document(paseadorId).get();
        Task<DocumentSnapshot> paseadorTask = db.collection("paseadores").document(paseadorId).get();

        Tasks.whenAllSuccess(usuarioTask, paseadorTask).addOnSuccessListener(results -> {
            DocumentSnapshot usuarioDoc = (DocumentSnapshot) results.get(0);
            DocumentSnapshot paseadorDoc = (DocumentSnapshot) results.get(1);

            if (!usuarioDoc.exists() || !paseadorDoc.exists()) {
                Toast.makeText(this, "Perfil no encontrado o incompleto.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "No se encontró el documento de usuario o paseador para el UID: " + paseadorId);
                return;
            }

            // Cargar datos de la colección 'usuarios'
            tvNombre.setText(usuarioDoc.getString("nombre_display"));
            Glide.with(this)
                    .load(usuarioDoc.getString("foto_perfil"))
                    .circleCrop()
                    .placeholder(R.drawable.bg_avatar_circle)
                    .into(ivAvatar);

            Timestamp fechaRegistro = usuarioDoc.getTimestamp("fecha_registro");
            if (fechaRegistro != null) {
                tvMiembroDesde.setText(String.format("Se unió en %s", formatMiembroDesde(fechaRegistro)));
            }

            Timestamp ultimaConexion = usuarioDoc.getTimestamp("ultima_conexion");
            if (ultimaConexion != null) {
                tvUltimaConexion.setText(String.format("Últ. conexión: %s", formatUltimaConexion(ultimaConexion)));
            }

            // Cargar datos de la colección 'paseadores'
            String verificacion = paseadorDoc.getString("verificacion_estado");
            if ("APROBADO".equalsIgnoreCase(verificacion)) {
                ivVerificadoBadge.setVisibility(View.VISIBLE);
                tvVerificado.setVisibility(View.VISIBLE);
            } else {
                ivVerificadoBadge.setVisibility(View.GONE);
                tvVerificado.setVisibility(View.GONE);
            }

            Double precio = paseadorDoc.getDouble("precio_hora");
            if (precio != null) {
                tvPrecio.setText(String.format(Locale.getDefault(), "• $%.0f/hora", precio));
            } else {
                tvPrecio.setVisibility(View.GONE);
            }

            Long paseos = paseadorDoc.getLong("num_servicios_completados");
            tvPaseosCompletados.setText(String.format(Locale.getDefault(), "%d Paseos completados", paseos != null ? paseos : 0));

            Long tiempoRespuesta = paseadorDoc.getLong("tiempo_respuesta_promedio_min");
            if (tiempoRespuesta != null) {
                tvTiempoRespuesta.setText(String.format(Locale.getDefault(), "Respuesta en %d min", tiempoRespuesta));
            } else {
                tvTiempoRespuesta.setText("Respuesta en -- min");
            }

            Double rating = paseadorDoc.getDouble("calificacion_promedio");
            Long numResenas = paseadorDoc.getLong("num_resenas");
            if (rating != null) {
                tvRatingValor.setText(String.format(Locale.getDefault(), "%.1f", rating));
                ratingBar.setRating(rating.floatValue());
            }
            tvResenasTotal.setText(String.format(Locale.getDefault(), "%d reviews", numResenas != null ? numResenas : 0));

            // Cargar datos del mapa 'perfil_profesional'
            Map<String, Object> perfil = (Map<String, Object>) paseadorDoc.get("perfil_profesional");
            if (perfil != null) {
                tvDescripcion.setText((String) perfil.get("motivacion"));

                String videoUrl = (String) perfil.get("video_presentacion_url");
                Glide.with(this)
                        .load(videoUrl)
                        .centerCrop()
                        .placeholder(R.drawable.galeria_paseos_foto1)
                        .into(ivVideoThumbnail);

                Timestamp inicioExpTimestamp = (Timestamp) perfil.get("fecha_inicio_experiencia");
                if (inicioExpTimestamp != null) {
                    Date startDate = inicioExpTimestamp.toDate();
                    LocalDate localStartDate = startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    LocalDate currentDate = LocalDate.now();
                    int years = Period.between(localStartDate, currentDate).getYears();
                    tvExperienciaAnos.setText(String.format(Locale.getDefault(), "Experiencia: %d años", years));
                } else {
                    tvExperienciaAnos.setVisibility(View.GONE);
                }
                tvExperienciaDesde.setVisibility(View.GONE); // Ocultar siempre el campo antiguo
            }

            // Cargar datos del mapa 'manejo_perros'
            Map<String, Object> manejo = (Map<String, Object>) paseadorDoc.get("manejo_perros");
            if (manejo != null) {
                List<String> tamanos = (List<String>) manejo.get("tamanos");
                if (tamanos != null && !tamanos.isEmpty()) {
                    tvTiposPerros.setText(android.text.TextUtils.join(", ", tamanos));
                } else {
                    tvTiposPerros.setText("No especificado");
                }
            }

            // Cargar sub-colecciones
            cargarDisponibilidad(paseadorId);
            cargarZonasServicio(paseadorId);

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error al cargar el perfil.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error al buscar documentos de perfil", e);
        });
    }

    private void cargarDisponibilidad(String paseadorId) {
        db.collection("paseadores").document(paseadorId).collection("disponibilidad")
                .limit(1).get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        tvDisponibilidad.setText("No especificada");
                        return;
                    }
                    DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                    List<String> dias = (List<String>) doc.get("dias");
                    String horaInicio = doc.getString("hora_inicio");
                    String horaFin = doc.getString("hora_fin");

                    if (dias != null && !dias.isEmpty() && horaInicio != null && horaFin != null) {
                        String diasStr = android.text.TextUtils.join(", ", dias);
                        tvDisponibilidad.setText(String.format("%s: %s - %s", diasStr, horaInicio, horaFin));
                    } else {
                        tvDisponibilidad.setText("No especificada");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar disponibilidad", e);
                    tvDisponibilidad.setText("Error al cargar");
                });
    }

    private void cargarZonasServicio(String paseadorId) {
        db.collection("paseadores").document(paseadorId).collection("zonas_servicio")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (googleMap == null) return;
                    if (querySnapshot.isEmpty()) {
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
                            double radioEnMetros = radioKm * 1000;

                            googleMap.addCircle(new CircleOptions()
                                    .center(latLng)
                                    .radius(radioEnMetros)
                                    .strokeColor(Color.argb(128, 0, 150, 136))
                                    .fillColor(Color.argb(64, 0, 150, 136)));
                            boundsBuilder.include(latLng);

                            try {
                                List<Address> addresses = geocoder.getFromLocation(centro.getLatitude(), centro.getLongitude(), 1);
                                if (addresses != null && !addresses.isEmpty()) {
                                    Address address = addresses.get(0);
                                    String nombreZona = address.getSubLocality(); // Barrio o sector
                                    if (nombreZona == null) {
                                        nombreZona = address.getLocality(); // Ciudad
                                    }
                                    if (nombreZona != null && !nombresZonas.contains(nombreZona)) {
                                        nombresZonas.add(nombreZona);
                                    }
                                }
                            } catch (IOException e) {
                                Log.e(TAG, "Error en Geocoder al obtener nombre de la zona", e);
                            }
                        }
                    }

                    if (!nombresZonas.isEmpty()) {
                        tvZonasServicioNombres.setText(android.text.TextUtils.join(", ", nombresZonas));
                    } else {
                        tvZonasServicioNombres.setText("Nombres no disponibles");
                    }

                    try {
                        LatLngBounds bounds = boundsBuilder.build();
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "No hay zonas de servicio para mostrar en el mapa.", e);
                    }

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar zonas de servicio", e);
                    tvZonasServicioNombres.setText("Error al cargar zonas");
                });
    }

    private String formatMiembroDesde(Timestamp timestamp) {
        return new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES")).format(timestamp.toDate());
    }

    private String formatUltimaConexion(Timestamp timestamp) {
        long ahora = System.currentTimeMillis();
        long tiempoPasado = timestamp.toDate().getTime();
        return DateUtils.getRelativeTimeSpanString(tiempoPasado, ahora, DateUtils.MINUTE_IN_MILLIS).toString();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        this.googleMap.getUiSettings().setAllGesturesEnabled(false);

        String paseadorId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (paseadorId == null) {
             LatLng quito = new LatLng(-0.180653, -78.467834);
             this.googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(quito, 12));
        }
    }
}
