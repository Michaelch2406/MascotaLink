package com.mjc.mascotalink;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import com.mjc.mascotalink.util.BottomNavManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PaseoEnCursoDuenoActivity extends AppCompatActivity {

    private static final String TAG = "PaseoEnCursoDueno";
    private static final int REQUEST_PERMISSION_CALL = 2201;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private DocumentReference reservaRef;
    private ListenerRegistration reservaListener;

    // UI Elements
    private TextView tvNombrePaseador;
    private TextView tvRating;
    private TextView tvInfoMascota;
    private TextView tvFechaHora;
    private TextView tvEstado;
    private TextView tvHoras, tvMinutos, tvSegundos;
    private TextView tvNotasPaseador;
    private ShapeableImageView ivFotoPaseador;
    private ProgressBar pbProgresoPaseo;
    private ProgressBar pbLoading;
    private RecyclerView rvFotos;
    private RecyclerView rvActividad;
    private MaterialButton btnContactar;
    private BottomNavigationView bottomNav;

    // Adapters
    private FotosPaseoAdapter fotosAdapter;
    private ActividadPaseoAdapter actividadAdapter;

    // Logic
    private String idReserva;
    private String idPaseador;
    private String telefonoPaseador;
    private String nombrePaseador;
    private Date fechaInicioPaseo;
    private long duracionMinutos = 0L;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

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

        idReserva = getIntent().getStringExtra("id_reserva");
        if (idReserva == null || idReserva.isEmpty()) {
            Toast.makeText(this, "Error: Reserva no encontrada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        reservaRef = db.collection("reservas").document(idReserva);
        verificarPermisosYEscuchar();
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
        ivFotoPaseador = findViewById(R.id.iv_foto_paseador);
        pbProgresoPaseo = findViewById(R.id.pb_progreso_paseo);
        pbLoading = findViewById(R.id.pb_loading);
        rvFotos = findViewById(R.id.rv_fotos);
        rvActividad = findViewById(R.id.rv_actividad);
        btnContactar = findViewById(R.id.btn_contactar_paseador);
        bottomNav = findViewById(R.id.bottom_nav);
    }

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

            String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
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
                Toast.makeText(this, "El paseo ya no está en curso", Toast.LENGTH_SHORT).show();
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
            if (snapshot != null && snapshot.exists()) {
                manejarSnapshotReserva(snapshot);
            } else {
                Toast.makeText(this, "El paseo ha finalizado o no existe", Toast.LENGTH_SHORT).show();
                finish();
            }
            mostrarLoading(false);
        });
    }

    private void manejarSnapshotReserva(DocumentSnapshot snapshot) {
        // 1. Estado
        String estado = snapshot.getString("estado");
        if (!"EN_CURSO".equalsIgnoreCase(estado)) {
            Toast.makeText(this, "El paseo ha finalizado", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 2. Tiempos y Duración
        Timestamp inicioTimestamp = snapshot.getTimestamp("fecha_inicio_paseo");
        if (inicioTimestamp == null)
            inicioTimestamp = snapshot.getTimestamp("hora_inicio");

        Long duracion = snapshot.getLong("duracion_minutos");
        if (duracion != null)
            duracionMinutos = duracion;

        if (inicioTimestamp != null) {
            Date nuevaFecha = inicioTimestamp.toDate();
            boolean reiniciarTimer = fechaInicioPaseo == null || fechaInicioPaseo.getTime() != nuevaFecha.getTime();
            fechaInicioPaseo = nuevaFecha;
            if (reiniciarTimer)
                startTimer();
            actualizarInfoFecha(nuevaFecha);
        }

        // 3. Notas
        String notas = snapshot.getString("notas_paseador");
        tvNotasPaseador.setText(notas != null && !notas.isEmpty() ? notas : "Sin notas por el momento.");

        // 4. Fotos
        Object fotosObj = snapshot.get("fotos_paseo");
        List<String> fotos = new ArrayList<>();
        if (fotosObj instanceof List) {
            for (Object item : (List<?>) fotosObj) {
                if (item instanceof String)
                    fotos.add((String) item);
            }
        }
        fotosAdapter.submitList(fotos);
        rvFotos.setVisibility(fotos.isEmpty() ? View.GONE : View.VISIBLE);

        // 5. Actividad (Timeline)
        Object actividadObj = snapshot.get("actividad");
        List<Map<String, Object>> actividades = new ArrayList<>();
        if (actividadObj instanceof List) {
            for (Object item : (List<?>) actividadObj) {
                if (item instanceof Map)
                    actividades.add((Map<String, Object>) item);
            }
        }
        // Sort by timestamp descending (newest first)
        Collections.sort(actividades, (o1, o2) -> {
            Timestamp t1 = (Timestamp) o1.get("timestamp");
            Timestamp t2 = (Timestamp) o2.get("timestamp");
            if (t1 == null)
                return 1;
            if (t2 == null)
                return -1;
            return t2.compareTo(t1);
        });
        actividadAdapter.setEventos(actividades);

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

    private void cargarDatosPaseador(DocumentReference ref) {
        ref.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                nombrePaseador = doc.getString("nombre_display");
                if (nombrePaseador == null)
                    nombrePaseador = doc.getString("nombre");
                tvNombrePaseador.setText(nombrePaseador != null ? nombrePaseador : "Paseador");

                telefonoPaseador = doc.getString("telefono");

                Double rating = doc.getDouble("rating");
                Long numResenas = doc.getLong("numero_resenas");
                if (rating != null) {
                    tvRating.setText(
                            String.format(Locale.US, "%.1f (%d)", rating, numResenas != null ? numResenas : 0));
                }

                String fotoUrl = doc.getString("foto_perfil");
                if (fotoUrl != null && !fotoUrl.isEmpty()) {
                    Glide.with(this)
                            .load(fotoUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_user_placeholder)
                            .error(R.drawable.ic_user_placeholder)
                            .into(ivFotoPaseador);
                }
            }
        });
    }

    private void cargarDatosMascota(String mascotaId) {
        db.collection("mascotas").document(mascotaId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String nombre = doc.getString("nombre");
                String raza = doc.getString("raza");
                tvInfoMascota.setText(
                        String.format("%s, %s", nombre != null ? nombre : "Mascota", raza != null ? raza : ""));
            }
        });
    }

    private void actualizarInfoFecha(Date inicio) {
        if (inicio == null)
            return;
        SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM, hh:mm a", new Locale("es", "ES"));
        String fechaStr = sdf.format(inicio);
        tvFechaHora.setText(String.format(Locale.getDefault(), "%s | %d min Duración", fechaStr, duracionMinutos));
    }

    private void startTimer() {
        stopTimer();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed())
                    return;

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
            Toast.makeText(this, "Teléfono del paseador no disponible", Toast.LENGTH_SHORT).show();
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CALL_PHONE },
                        REQUEST_PERMISSION_CALL);
                return;
            }
        }
        startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + telefonoPaseador)));
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
