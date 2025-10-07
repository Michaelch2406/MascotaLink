package com.mjc.mascotalink;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
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
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PerfilPaseadorActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "PerfilPaseadorActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Views
    private ImageView ivAvatar, ivVerificadoBadge, ivVideoThumbnail;
    private TextView tvNombre, tvRol, tvVerificado, tvPrecio, tvDescripcion, tvDisponibilidad, tvTiposPerros;
    private TextView tvExperienciaAnos, tvExperienciaDesde;
    private TextView tvPaseosCompletados, tvTiempoRespuesta;
    private TextView tvRatingValor, tvResenasTotal;
    private RatingBar ratingBar;
    private LinearLayout barrasRatings, llAcercaDe, llResenas;
    private FrameLayout videoContainer;
    private TabLayout tabLayout;
    private GoogleMap googleMap;
    private List<Circle> mapCircles = new ArrayList<>();


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_paseador);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        setupToolbar();
        setupTabs();

        String paseadorId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (paseadorId != null) {
            cargarPerfilCompleto(paseadorId);
        } else {
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show();
            finish();
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
        tvPaseosCompletados = findViewById(R.id.tv_paseos_completados);
        tvTiempoRespuesta = findViewById(R.id.tv_tiempo_respuesta);

        // "Reseñas" views
        tvRatingValor = findViewById(R.id.tv_rating_valor);
        ratingBar = findViewById(R.id.rating_bar);
        tvResenasTotal = findViewById(R.id.tv_resenas_total);
        barrasRatings = findViewById(R.id.barras_ratings);

        // Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_container);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
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
            tvPaseosCompletados.setText(String.format(Locale.getDefault(), "%d paseos completados", paseos != null ? paseos : 0));

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
                tvDescripcion.setText((String) perfil.get("motivacion")); // Usando 'motivacion' como se pide

                String videoUrl = (String) perfil.get("video_presentacion_url");
                Glide.with(this)
                        .load(videoUrl)
                        .centerCrop()
                        .placeholder(R.drawable.galeria_paseos_foto1)
                        .into(ivVideoThumbnail);

                // Cargar experiencia
                Long anosExp = (Long) perfil.get("anos_experiencia");
                String inicioExp = (String) perfil.get("inicio_experiencia");
                if (anosExp != null) {
                    tvExperienciaAnos.setText(String.format(Locale.getDefault(), "Experiencia: %d años", anosExp));
                } else {
                    tvExperienciaAnos.setVisibility(View.GONE);
                }
                if (inicioExp != null && !inicioExp.isEmpty()) {
                    tvExperienciaDesde.setText("• Desde " + inicioExp);
                } else {
                    tvExperienciaDesde.setVisibility(View.GONE);
                }
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
                        // Opcional: Mover mapa a una ubicación por defecto si no hay zonas
                        return;
                    }

                    LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
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
                        }
                    }
                    LatLngBounds bounds = boundsBuilder.build();
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

                })
                .addOnFailureListener(e -> Log.e(TAG, "Error al cargar zonas de servicio", e));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        this.googleMap.getUiSettings().setAllGesturesEnabled(false); // Deshabilitar interacción con el mapa

        String paseadorId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (paseadorId == null) {
             LatLng quito = new LatLng(-0.180653, -78.467834);
             this.googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(quito, 12));
        }
    }
}