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
import com.google.firebase.firestore.ListenerRegistration;
import com.mjc.mascotalink.adapters.ActividadPaseoAdapter;
import com.mjc.mascotalink.adapters.FotosPaseoAdapter;
import com.mjc.mascotalink.modelo.PaseoActividad;
import com.mjc.mascotalink.util.BottomNavManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.firestore.GeoPoint;

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
    private ListenerRegistration reservaListener;
    private GoogleMap mMap;

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
    private View viewMapOverlay;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseo_en_curso_dueno);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

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
        verificarPermisosYEscuchar();
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        
        if (viewMapOverlay != null) {
            viewMapOverlay.setOnClickListener(v -> abrirMapaFullscreen());
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setAllGesturesEnabled(false); // Disable interaction in mini-map
        mMap.getUiSettings().setMapToolbarEnabled(false);
        
        // Initial empty state or loading
        if (!rutaPaseo.isEmpty()) {
            actualizarMapa(rutaPaseo);
        }
    }

    private void abrirMapaFullscreen() {
        if (ultimaUbicacionConocida == null) {
            Toast.makeText(this, "Ubicación no disponible", Toast.LENGTH_SHORT).show();
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reservaListener != null) {
            reservaListener.remove();
        }
        stopTimer();
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
        viewMapOverlay = findViewById(R.id.view_map_overlay);
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
        BottomNavManager.setupBottomNav(this, bottomNav, "DUENO", R.id.menu_walks);
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
                Toast.makeText(this, "El paseo ya no estÃ¡ en curso", Toast.LENGTH_SHORT).show();
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
        reservaListener = reservaRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Error escuchando reserva", error);
                return;
            }
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                manejarSnapshotReserva(snapshot);
                mostrarLoading(false);
                if (contentContainer != null) {
                    contentContainer.setVisibility(View.VISIBLE);
                }
            } else {
                Toast.makeText(this, "El paseo ha finalizado o no existe", Toast.LENGTH_SHORT).show();
                finish();
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
            if (estado.equalsIgnoreCase("COMPLETADO") || estado.startsWith("CANCELADO")) {
                Toast.makeText(this, "El paseo ha finalizado: " + estado, Toast.LENGTH_SHORT).show();
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
            tvEstado.setText("En curso"); // Restaurar texto
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
        tvNotasPaseador.setText(notas != null && !notas.isEmpty() ? notas : "Sin notas aÃºn");

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
        actualizarMapa(puntosMapa);

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
                        // Intenta obtener lat/lng de forma segura, manejando Double o String
                        Object latObj = map.get("lat");
                        Object lngObj = map.get("lng"); // o "lon"
                        if (lngObj == null) lngObj = map.get("lon");
                        
                        double lat = 0, lng = 0;
                        if (latObj instanceof Number) lat = ((Number) latObj).doubleValue();
                        if (lngObj instanceof Number) lng = ((Number) lngObj).doubleValue();
                        
                        if (lat != 0 && lng != 0) {
                            puntos.add(new LatLng(lat, lng));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error parseando punto de mapa: " + item, e);
                    }
                }
            }
        }
        return puntos;
    }

    private void actualizarMapa(List<LatLng> puntos) {
        if (mMap == null || puntos == null || puntos.isEmpty()) return;
        
        this.rutaPaseo = puntos;
        this.ultimaUbicacionConocida = puntos.get(puntos.size() - 1);

        mMap.clear();

        // Dibujar ruta
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(puntos)
                .width(10f)
                .color(getResources().getColor(R.color.blue_primary))
                .geodesic(true);
        mMap.addPolyline(polylineOptions);

        // Marcador de Inicio (Verde)
        mMap.addMarker(new MarkerOptions()
                .position(puntos.get(0))
                .title("Inicio")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        // Marcador Actual (Azul/Foto)
        mMap.addMarker(new MarkerOptions()
                .position(ultimaUbicacionConocida)
                .title("Ubicación actual")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        // Mover cámara
        try {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (LatLng p : puntos) {
                builder.include(p);
            }
            LatLngBounds bounds = builder.build();
            // Padding de 50px para que no quede pegado a los bordes
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
        } catch (Exception e) {
            // Fallback si los puntos son muy cercanos o hay error en bounds
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ultimaUbicacionConocida, 16f));
        }
    }

    private void cargarDatosPaseador(DocumentReference ref) {
        ref.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                nombrePaseador = doc.getString("nombre_display");
                if (nombrePaseador == null)
                    nombrePaseador = doc.getString("nombre");
                tvNombrePaseador.setText(nombrePaseador != null ? nombrePaseador : "Paseador no disponible");

                telefonoPaseador = doc.getString("telefono");
                if (telefonoPaseador == null || telefonoPaseador.isEmpty()) {
                    btnContactar.setEnabled(false);
                    btnContactar.setAlpha(0.6f);
                } else {
                    btnContactar.setEnabled(true);
                    btnContactar.setAlpha(1f);
                }

                Double rating = doc.getDouble("rating");
                Long numResenas = doc.getLong("numero_resenas");
                if (rating != null) {
                    tvRating.setText(
                            String.format(Locale.US, "%.1f (%d)", rating, numResenas != null ? numResenas : 0));
                } else {
                    tvRating.setText("N/A");
                }

                String fotoUrl = doc.getString("foto_perfil");
                if (fotoUrl != null && !fotoUrl.isEmpty()) {
                    Glide.with(this)
                            .load(fotoUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_user_placeholder)
                            .error(R.drawable.ic_user_placeholder)
                            .into(ivFotoPaseador);
                } else {
                    ivFotoPaseador.setImageResource(R.drawable.ic_user_placeholder);
                }
            }
        });
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
        
        // FIX: Usar 'foto_url' que es el campo estándar, con fallback a 'foto_principal_url'
        /* Nota: Si hay un ImageView para la mascota en esta pantalla, se cargaría aquí.
           Actualmente PaseoEnCursoDuenoActivity no tiene un ImageView específico para la mascota en el layout (ivFotoPaseador es para el paseador),
           pero actualizamos este método para consistencia en caso de que se añada en el futuro o se use en otro lugar. */
    }

    private void actualizarInfoFecha(Date inicio) {
        if (inicio == null)
            return;
        SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM, hh:mm a", new Locale("es", "ES"));
        String fechaStr = sdf.format(inicio);
        tvFechaHora.setText(String.format(Locale.getDefault(), "%s | %d min DuraciÃ³n", fechaStr, duracionMinutos));
    }

    private void iniciarProcesoCancelacion() {
        if (fechaInicioPaseo == null) {
            Toast.makeText(this, "El paseo aún no ha iniciado correctamente", Toast.LENGTH_SHORT).show();
            return;
        }

        long tiempoTranscurrido = calcularTiempoTranscurrido();
        // Regla: Bloquear cancelación antes de 10 minutos
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
        com.google.android.material.textfield.TextInputEditText etOtroMotivo = view.findViewById(R.id.et_otro_motivo);
        
        // Ocultar opción de "Éxito" para el dueño
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
        data.put("cancelado_por", "DUENO");
        data.put("fecha_solicitud_cancelacion", new Date());

        reservaRef.update(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Solicitud de cancelación enviada al paseador.", Toast.LENGTH_LONG).show();
                    mostrarLoading(false);
                    // No cerramos la actividad inmediatamente, esperamos respuesta o actualización de estado
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
            Toast.makeText(this, "TelÃ©fono del paseador no disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] opciones = { "Llamar", "WhatsApp", "SMS", "Cancelar" };
        new AlertDialog.Builder(this)
                .setTitle("Contactar a " + (nombrePaseador != null ? nombrePaseador : "Paseador"))
                .setItems(opciones, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            intentarLlamar();
                            break;
                        case 1:
                            abrirWhatsApp();
                            break;
                        case 2:
                            enviarSMS();
                            break;
                        case 3:
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

    private void abrirWhatsApp() {
        try {
            String url = "https://wa.me/" + telefonoPaseador + "?text=Hola, tengo una consulta sobre el paseo.";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp no instalado", Toast.LENGTH_SHORT).show();
        }
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
        Glide.with(this).load(url).into(imageView);
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













