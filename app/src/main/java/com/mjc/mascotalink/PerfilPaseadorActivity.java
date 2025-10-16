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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.ListenerRegistration;
import com.mjc.mascota.ui.busqueda.BusquedaPaseadoresActivity;

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
    private ImageView ivEditZonas, ivEditDisponibilidad, ivEditPerfil;
    private GoogleMap googleMap;
    private List<Circle> mapCircles = new ArrayList<>();
    private View btnNotificaciones, btnMetodosPago, btnPrivacidad, btnCentroAyuda, btnTerminos;
    private String metodoPagoId; // Variable to store the payment method ID
    private View skeletonLayout;
    private androidx.core.widget.NestedScrollView scrollViewContent;
    private boolean isContentVisible = false;
    private String paseadorId;

    // Listeners for real-time updates
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
        setupListeners();
        setupTabs();

        paseadorId = getIntent().getStringExtra("paseadorId");
        String viewerRole = getIntent().getStringExtra("viewerRole");
        String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        if (paseadorId == null && currentUserId != null) {
            paseadorId = currentUserId;
        }

        if (paseadorId != null) {
            boolean isOwnerViewing = "DUEÑO".equalsIgnoreCase(viewerRole);
            boolean isOwnProfile = paseadorId.equals(currentUserId);

            if (isOwnProfile) {
                // Paseador viendo su propio perfil
                fabReservar.setVisibility(View.GONE);
                btnMensaje.setVisibility(View.GONE);
                ivEditPerfil.setVisibility(View.VISIBLE);
                ivEditZonas.setVisibility(View.VISIBLE);
                ivEditDisponibilidad.setVisibility(View.VISIBLE);
            } else if (isOwnerViewing) {
                // Dueño viendo el perfil de un paseador
                fabReservar.setVisibility(View.VISIBLE);
                btnMensaje.setVisibility(View.VISIBLE);
                ivEditPerfil.setVisibility(View.GONE);
                ivEditZonas.setVisibility(View.GONE);
                ivEditDisponibilidad.setVisibility(View.GONE);
            } else {
                // Otro caso (ej. no logueado, o un paseador viendo a otro)
                fabReservar.setVisibility(View.GONE);
                btnMensaje.setVisibility(View.GONE);
                ivEditPerfil.setVisibility(View.GONE);
                ivEditZonas.setVisibility(View.GONE);
                ivEditDisponibilidad.setVisibility(View.GONE);
            }
        } else {
            Toast.makeText(this, "Error: No se pudo cargar el perfil.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (paseadorId != null) {
            cargarPerfilCompleto(paseadorId);
            cargarMetodoPagoPredeterminado(paseadorId);
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
        btnNotificaciones = findViewById(R.id.btn_notificaciones);
        btnMetodosPago = findViewById(R.id.btn_metodos_pago);
        btnPrivacidad = findViewById(R.id.btn_privacidad);
        btnCentroAyuda = findViewById(R.id.btn_centro_ayuda);
        btnTerminos = findViewById(R.id.btn_terminos);
        btnCerrarSesion = findViewById(R.id.btn_cerrar_sesion);

        // New buttons
        fabReservar = findViewById(R.id.fab_reservar);
        btnMensaje = findViewById(R.id.btn_mensaje);
        ivEditPerfil = findViewById(R.id.iv_edit_perfil);

        // Edit Icons
        ivEditZonas = findViewById(R.id.iv_edit_zonas);
        ivEditDisponibilidad = findViewById(R.id.iv_edit_disponibilidad);

        // Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_container);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        skeletonLayout = findViewById(R.id.skeleton_layout);
        scrollViewContent = findViewById(R.id.scroll_view_content);

    }

    @SuppressWarnings("unchecked")
    private void setupListeners() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        fabReservar.setOnClickListener(v -> showToast("Próximamente: Reservar"));
        btnMensaje.setOnClickListener(v -> showToast("Próximamente: Enviar Mensaje"));

        ivEditPerfil.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilPaseadorActivity.this, EditarPerfilPaseadorActivity.class);
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

        btnNotificaciones.setOnClickListener(v -> showToast("Próximamente: Notificaciones"));
        btnMetodosPago.setOnClickListener(v -> {
            Intent intent = new Intent(PerfilPaseadorActivity.this, MetodoPagoActivity.class);
            if (metodoPagoId != null && !metodoPagoId.isEmpty()) {
                intent.putExtra("metodo_pago_id", metodoPagoId);
            }
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
                Intent intent = new Intent(this, BusquedaPaseadoresActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
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

    private void cargarMetodoPagoPredeterminado(String uid) {
        if (metodoPagoListener != null) metodoPagoListener.remove();
        Query query = db.collection("usuarios").document(uid).collection("metodos_pago")
                .whereEqualTo("predeterminado", true).limit(1);

        metodoPagoListener = query.addSnapshotListener((queryDocumentSnapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                metodoPagoId = queryDocumentSnapshots.getDocuments().get(0).getId();
            } else {
                // Si no hay predeterminado, busca el más reciente en tiempo real también
                db.collection("usuarios").document(uid).collection("metodos_pago")
                        .orderBy("fecha_registro", Query.Direction.DESCENDING).limit(1)
                        .addSnapshotListener((snapshots, error) -> {
                            if (error != null) {
                                Log.w(TAG, "Listen failed for recent payment method.", error);
                                return;
                            }
                            if (snapshots != null && !snapshots.isEmpty()) {
                                metodoPagoId = snapshots.getDocuments().get(0).getId();
                            }
                        });
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void cargarPerfilCompleto(String paseadorId) {
        // Listener for the 'usuarios' document
        usuarioListener = db.collection("usuarios").document(paseadorId).addSnapshotListener((usuarioDoc, e) -> {
            if (e != null) {
                Log.e(TAG, "Error al escuchar documento de usuario", e);
                return;
            }

            if (usuarioDoc != null && usuarioDoc.exists()) {
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
                showContent();
            } else {
                Log.d(TAG, "El documento de usuario no existe o está vacío");
            }
        });

        // Listener for the 'paseadores' document
        paseadorListener = db.collection("paseadores").document(paseadorId).addSnapshotListener((paseadorDoc, e) -> {
            if (e != null) {
                Log.e(TAG, "Error al escuchar documento de paseador", e);
                Toast.makeText(this, "Error al cargar el perfil.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (paseadorDoc != null && paseadorDoc.exists()) {
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
                    tvPrecio.setVisibility(View.VISIBLE);
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
                        tvExperienciaAnos.setVisibility(View.VISIBLE);
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
                showContent();
            } else {
                Log.e(TAG, "No se encontró el documento de paseador para el UID: " + paseadorId);
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Perfil no disponible")
                        .setMessage("El perfil de este paseador ya no se encuentra disponible o está incompleto.")
                        .setPositiveButton("Aceptar", (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
            }
        });
    }

    private void showContent() {
        if (!isContentVisible) {
            isContentVisible = true;
            skeletonLayout.setVisibility(View.GONE);
            scrollViewContent.setVisibility(View.VISIBLE);
            // Optionally, add a fade-in animation here
        }
    }

    private void cargarDisponibilidad(String paseadorId) {
        if (disponibilidadListener != null) disponibilidadListener.remove();
        disponibilidadListener = db.collection("paseadores").document(paseadorId).collection("disponibilidad")
                .limit(1).addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error al cargar disponibilidad", e);
                        tvDisponibilidad.setText("Error al cargar");
                        return;
                    }
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
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
                });
    }

    private void cargarZonasServicio(String paseadorId) {
        if (zonasListener != null) zonasListener.remove();
        zonasListener = db.collection("paseadores").document(paseadorId).collection("zonas_servicio")
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error al cargar zonas de servicio", e);
                        tvZonasServicioNombres.setText("Error al cargar zonas");
                        return;
                    }
                    if (googleMap == null) return;
                    googleMap.clear(); // Limpiar círculos anteriores para reflejar cambios

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
                            } catch (IOException ex) {
                                Log.e(TAG, "Error en Geocoder al obtener nombre de la zona", ex);
                            }
                        }
                    }

                    if (!nombresZonas.isEmpty()) {
                        tvZonasServicioNombres.setText(android.text.TextUtils.join(", ", nombresZonas));
                    } else {
                        tvZonasServicioNombres.setText("Nombres no disponibles");
                    }

                    try {
                        if(querySnapshot.size() > 0) {
                           LatLngBounds bounds = boundsBuilder.build();
                           googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                        }
                    } catch (IllegalStateException ex) {
                        Log.e(TAG, "No hay zonas de servicio para mostrar en el mapa.", ex);
                    }
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

    @Override
    protected void onStop() {
        super.onStop();
        // Remove all listeners to prevent memory leaks
        if (usuarioListener != null) {
            usuarioListener.remove();
        }
        if (paseadorListener != null) {
            paseadorListener.remove();
        }
        if (disponibilidadListener != null) {
            disponibilidadListener.remove();
        }
        if (zonasListener != null) {
            zonasListener.remove();
        }
        if (metodoPagoListener != null) {
            metodoPagoListener.remove();
        }
    }
}
