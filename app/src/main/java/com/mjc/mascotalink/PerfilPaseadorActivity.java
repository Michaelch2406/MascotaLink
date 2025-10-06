package com.mjc.mascotalink;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PerfilPaseadorActivity extends AppCompatActivity implements OnMapReadyCallback {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Views
    private ImageView ivAvatar, ivVerificadoBadge, ivVideoThumbnail;
    private TextView tvNombre, tvRol, tvVerificado, tvPrecio, tvDescripcion, tvDisponibilidad, tvTiposPerros;
    private TextView tvPaseosCompletados, tvTiempoRespuesta;
    private TextView tvRatingValor, tvResenasTotal;
    private RatingBar ratingBar;
    private LinearLayout barrasRatings, llAcercaDe, llResenas;
    private FrameLayout videoContainer;
    private TabLayout tabLayout;
    private GoogleMap googleMap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_paseador);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        setupToolbar();
        setupTabs();

        // Get paseadorId from intent or default for now
        String paseadorId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (paseadorId != null) {
            cargarPerfilPaseador(paseadorId);
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

    @SuppressWarnings("unchecked")
    private void cargarPerfilPaseador(String paseadorId) {
        db.collection("paseadores").document(paseadorId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document == null || !document.exists()) {
                        Toast.makeText(this, "Perfil no encontrado", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Cargar datos de usuario (colección 'users')
                    String nombre = document.getString("nombre_display");
                    String fotoUrl = document.getString("foto_perfil");
                    tvNombre.setText(nombre);
                    Glide.with(this).load(fotoUrl).circleCrop().into(ivAvatar);

                    // Cargar datos de paseador (colección 'paseadores')
                    String verificacion = document.getString("verificacion_estado");
                    if ("APROBADO".equalsIgnoreCase(verificacion)) {
                        ivVerificadoBadge.setVisibility(View.VISIBLE);
                        tvVerificado.setVisibility(View.VISIBLE);
                    } else {
                        ivVerificadoBadge.setVisibility(View.GONE);
                        tvVerificado.setVisibility(View.GONE);
                    }

                    // Perfil Profesional
                    Map<String, Object> perfil = (Map<String, Object>) document.get("perfil_profesional");
                    if (perfil != null) {
                        tvDescripcion.setText((String) perfil.get("experiencia_general"));
                        String videoUrl = (String) perfil.get("video_presentacion_url");
                        // TODO: Implementar reproductor de video. Por ahora se muestra thumbnail.
                        Glide.with(this).load(videoUrl).centerCrop().into(ivVideoThumbnail);
                    }

                    // Precio
                    Double precio = document.getDouble("precio_hora");
                    if(precio != null) {
                         tvPrecio.setText(String.format(Locale.getDefault(), "• $%.0f/hora", precio));
                    }

                    // Manejo de perros
                    Map<String, Object> manejo = (Map<String, Object>) document.get("manejo_perros");
                    if (manejo != null) {
                        List<String> tamanos = (List<String>) manejo.get("tamanos");
                        if (tamanos != null) {
                            tvTiposPerros.setText(android.text.TextUtils.join(", ", tamanos));
                        }
                    }

                    // Métricas
                    Long paseos = document.getLong("num_servicios_completados");
                    tvPaseosCompletados.setText(String.format(Locale.getDefault(), "%d paseos completados", paseos != null ? paseos : 0));
                    // TODO: Cargar tiempo de respuesta desde Firestore
                    tvTiempoRespuesta.setText("• Respuesta: Próximamente");

                    // Calificaciones
                    Double rating = document.getDouble("calificacion_promedio");
                    Long numResenas = document.getLong("num_resenas"); // Asumiendo este campo
                    if (rating != null) {
                        tvRatingValor.setText(String.format(Locale.getDefault(), "%.1f", rating));
                        ratingBar.setRating(rating.floatValue());
                    }
                    tvResenasTotal.setText(String.format(Locale.getDefault(), "%d reviews", numResenas != null ? numResenas : 0));

                    // TODO: Cargar y dibujar barras de calificaciones
                    // Map<String, Long> dist = (Map<String, Long>) document.get("calificaciones_distribucion");

                    cargarDisponibilidad(paseadorId);
                    cargarZonasServicio(paseadorId);
                });
    }

    private void cargarDisponibilidad(String paseadorId) {
        // TODO: Implementar la carga de la subcolección de disponibilidad
        // y actualizar el TextView tv_disponibilidad.
        tvDisponibilidad.setText("Disponibilidad: Próximamente");
    }

    private void cargarZonasServicio(String paseadorId) {
        // TODO: Implementar la carga de la subcolección de zonas de servicio
        // y dibujar los círculos en el mapa.
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        // TODO: Mover la cámara a la ubicación del paseador o una ubicación por defecto
        LatLng quito = new LatLng(-0.180653, -78.467834);
        this.googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(quito, 12));
    }
}