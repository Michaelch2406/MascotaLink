package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.util.ChatHelper;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class DetallePaseoActivity extends AppCompatActivity {

    private static final String TAG = "DetallePaseoActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;
    private String currentUserRole;
    private String idReserva;

    // Views
    private ImageButton ivBack;
    
    // Status Card
    private View layoutStatusHeader;
    private TextView tvStatusTitle;
    private TextView tvStatusSubtitle;
    private ImageView ivStatusIcon;

    // Profile Card
    private TextView tvLabelPaseador;
    private CircleImageView ivPaseadorFoto;
    private TextView tvPaseadorNombre;
    private TextView tvVerPerfil;
    private ImageButton btnChat;
    private View btnVerPerfilPaseador;

    // Service Details
    private TextView tvFechaHora;
    private TextView tvDetalleDuracion;
    private TextView tvDetallePrecio;

    // Mascotas
    private RecyclerView rvMascotas;
    private MascotaDetalleAdapter mascotasAdapter;
    private List<MascotaDetalleAdapter.MascotaDetalle> mascotasDetalleList;

    // Bottom Actions
    private LinearLayout layoutActions;
    private MaterialButton btnAccionPrincipal;
    private MaterialButton btnCancelar;

    // Datos
    private String idDueno;
    private String idPaseador;
    private String targetProfileId;
    private String estadoActual;

    // Skeleton
    private View skeletonLayout;
    private View scrollViewContent;
    private static final long MIN_SKELETON_TIME = 800;
    private long startTimeMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalle_paseo);

        startTimeMillis = System.currentTimeMillis();
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        
        if (currentUser == null) {
            finish();
            return;
        }
        currentUserId = currentUser.getUid();
        
        // Determinar rol
        currentUserRole = BottomNavManager.getUserRole(this);
        if (currentUserRole == null) currentUserRole = "DUEÑO";

        idReserva = getIntent().getStringExtra("id_reserva");
        if (idReserva == null) {
            // Compatibilidad con otros intents
            idReserva = getIntent().getStringExtra("reserva_id");
        }

        if (idReserva == null || idReserva.isEmpty()) {
            Toast.makeText(this, "Error: No se pudo cargar el paseo", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        showSkeleton();
        setupListeners();
        cargarDatosReserva();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        skeletonLayout = findViewById(R.id.skeleton_layout);
        scrollViewContent = findViewById(R.id.scroll_view_content);
        
        // Status
        layoutStatusHeader = findViewById(R.id.layout_status_header);
        tvStatusTitle = findViewById(R.id.tv_status_title);
        tvStatusSubtitle = findViewById(R.id.tv_status_subtitle);
        ivStatusIcon = findViewById(R.id.iv_status_icon);

        // Profile
        tvLabelPaseador = findViewById(R.id.tv_label_paseador);
        ivPaseadorFoto = findViewById(R.id.iv_paseador_foto);
        tvPaseadorNombre = findViewById(R.id.tv_paseador_nombre);
        tvVerPerfil = findViewById(R.id.tv_ver_perfil);
        btnChat = findViewById(R.id.btn_chat);
        btnVerPerfilPaseador = findViewById(R.id.btn_ver_perfil_paseador);

        // Details
        tvFechaHora = findViewById(R.id.tv_fecha_hora);
        tvDetalleDuracion = findViewById(R.id.tv_detalle_duracion);
        tvDetallePrecio = findViewById(R.id.tv_precio);

        // Mascotas
        rvMascotas = findViewById(R.id.rv_mascotas);
        mascotasDetalleList = new ArrayList<>();
        mascotasAdapter = new MascotaDetalleAdapter(this, mascotasDetalleList);
        rvMascotas.setLayoutManager(new LinearLayoutManager(this));
        rvMascotas.setAdapter(mascotasAdapter);

        // Actions
        layoutActions = findViewById(R.id.layout_actions);
        btnAccionPrincipal = findViewById(R.id.btn_accion_principal);
        btnCancelar = findViewById(R.id.btn_cancelar);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        btnVerPerfilPaseador.setOnClickListener(v -> {
            if (targetProfileId == null) return;
            Intent intent;
            if ("PASEADOR".equalsIgnoreCase(currentUserRole)) {
                intent = new Intent(this, PerfilDuenoActivity.class);
                intent.putExtra("id_dueno", targetProfileId);
            } else {
                intent = new Intent(this, PerfilPaseadorActivity.class);
                intent.putExtra("paseadorId", targetProfileId);
            }
            startActivity(intent);
        });

        btnChat.setOnClickListener(v -> {
            if (targetProfileId != null) {
                ChatHelper.openOrCreateChat(this, db, currentUserId, targetProfileId);
            }
        });

        btnAccionPrincipal.setOnClickListener(v -> {
            // Ir al mapa en vivo
            Intent intent;
            if ("PASEADOR".equalsIgnoreCase(currentUserRole)) {
                intent = new Intent(this, PaseoEnCursoActivity.class);
            } else {
                intent = new Intent(this, PaseoEnCursoDuenoActivity.class);
            }
            intent.putExtra("id_reserva", idReserva);
            startActivity(intent);
        });

        btnCancelar.setOnClickListener(v -> mostrarDialogCancelar());
    }

    private void cargarDatosReserva() {
        db.collection("reservas").document(idReserva).addSnapshotListener((doc, e) -> {
            if (e != null) return;
            if (doc == null || !doc.exists()) {
                Toast.makeText(this, "El paseo ya no existe", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            estadoActual = doc.getString("estado");
            
            // Obtener IDs
            DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
            DocumentReference paseadorRef = doc.getDocumentReference("id_paseador");
            idDueno = duenoRef != null ? duenoRef.getId() : null;
            idPaseador = paseadorRef != null ? paseadorRef.getId() : null;

            // Configurar UI según rol
            if ("PASEADOR".equalsIgnoreCase(currentUserRole)) {
                targetProfileId = idDueno;
                tvLabelPaseador.setText("Cliente");
                btnCancelar.setText("CANCELAR PASEO");
            } else {
                targetProfileId = idPaseador;
                tvLabelPaseador.setText("Paseador asignado");
                btnCancelar.setText("CANCELAR PASEO");
            }

            // Cargar datos del otro usuario
            cargarDatosUsuario(targetProfileId);

            // Cargar detalles
            Date fecha = doc.getDate("fecha");
            Date hora = doc.getDate("hora_inicio");
            Double precio = doc.getDouble("costo_total");
            int duracion = doc.getLong("duracion_minutos") != null ? doc.getLong("duracion_minutos").intValue() : 60;

            SimpleDateFormat sdf = new SimpleDateFormat("EEEE d 'de' MMMM • HH:mm", new Locale("es", "ES"));
            if (fecha != null && hora != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(hora);
                String horaStr = String.format(Locale.getDefault(), "%02d:%02d", 
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
                
                SimpleDateFormat sdfFecha = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "ES"));
                tvFechaHora.setText(sdfFecha.format(fecha) + " • " + horaStr);
            }

            tvDetalleDuracion.setText(duracion + " minutos");
            tvDetallePrecio.setText(precio != null ? String.format(Locale.getDefault(), "$%.2f Total", precio) : "$0.00");

            // Configurar Estado Visual
            configurarEstadoVisual(estadoActual);

            // Cargar Mascotas
            @SuppressWarnings("unchecked")
            List<String> mascotasIds = (List<String>) doc.get("mascotas");
            String idMascotaLegacy = doc.getString("id_mascota");
            
            if (mascotasIds != null && !mascotasIds.isEmpty()) {
                cargarMascotas(idDueno, mascotasIds);
            } else if (idMascotaLegacy != null) {
                List<String> singleList = new ArrayList<>();
                singleList.add(idMascotaLegacy);
                cargarMascotas(idDueno, singleList);
            }
            
            hideSkeleton();
        });
    }

    private void showSkeleton() {
        if (skeletonLayout != null) {
            skeletonLayout.setVisibility(View.VISIBLE);
            applyShimmer(skeletonLayout);
        }
        if (scrollViewContent != null) {
            scrollViewContent.setVisibility(View.GONE);
        }
    }

    private void hideSkeleton() {
        long elapsedTime = System.currentTimeMillis() - startTimeMillis;
        long delay = Math.max(0, MIN_SKELETON_TIME - elapsedTime);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (skeletonLayout != null) skeletonLayout.setVisibility(View.GONE);
            if (scrollViewContent != null) scrollViewContent.setVisibility(View.VISIBLE);
        }, delay);
    }

    private void applyShimmer(View view) {
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyShimmer(group.getChildAt(i));
            }
        } else if (view.getBackground() != null) {
            try {
                android.view.animation.Animation anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.shimmer_animation);
                view.startAnimation(anim);
            } catch (Exception e) {
                // Ignore if anim resource not found
            }
        }
    }

    private void configurarEstadoVisual(String estado) {
        int colorFondo;
        int icono;
        String titulo;
        String subtitulo;
        boolean mostrarMapa = false;

        switch (estado) {
            case "CONFIRMADO":
                colorFondo = R.drawable.bg_gradient_blue_card;
                icono = R.drawable.ic_calendar;
                titulo = "Paseo Confirmado";
                subtitulo = "Todo listo para el servicio";
                mostrarMapa = false; // Aún no inicia
                break;
            case "LISTO_PARA_INICIAR":
                colorFondo = R.drawable.bg_gradient_orange_card;
                icono = R.drawable.ic_access_time;
                titulo = "Listo para Iniciar";
                subtitulo = "El paseo está por comenzar";
                mostrarMapa = true; // Ya se puede entrar
                break;
            case "EN_CURSO":
                colorFondo = R.drawable.bg_gradient_green_card;
                icono = R.drawable.ic_walk;
                titulo = "Paseo en Curso";
                subtitulo = "Monitoreo en tiempo real activo";
                mostrarMapa = true;
                break;
            default:
                colorFondo = R.drawable.bg_gradient_gray_card;
                icono = R.drawable.ic_info;
                titulo = estado;
                subtitulo = "Estado del servicio";
                mostrarMapa = false;
        }

        layoutStatusHeader.setBackgroundResource(colorFondo);
        ivStatusIcon.setImageResource(icono);
        tvStatusTitle.setText(titulo);
        tvStatusSubtitle.setText(subtitulo);

        if (mostrarMapa) {
            btnAccionPrincipal.setVisibility(View.VISIBLE);
            if ("LISTO_PARA_INICIAR".equals(estado) && "PASEADOR".equalsIgnoreCase(currentUserRole)) {
                btnAccionPrincipal.setText("INICIAR PASEO");
                btnAccionPrincipal.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.orange_primary));
            } else {
                btnAccionPrincipal.setText("VER MAPA EN VIVO");
                btnAccionPrincipal.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.green_success));
            }
        } else {
            btnAccionPrincipal.setVisibility(View.GONE);
        }
    }

    private void cargarDatosUsuario(String userId) {
        if (userId == null) return;
        db.collection("usuarios").document(userId).get().addOnSuccessListener(doc -> {
            if (isFinishing() || isDestroyed()) return;
            if (doc.exists()) {
                String nombre = doc.getString("nombre_display");
                String foto = doc.getString("foto_perfil");
                tvPaseadorNombre.setText(nombre != null ? nombre : "Usuario");
                if (foto != null && !isFinishing() && !isDestroyed()) {
                    Glide.with(this).load(MyApplication.getFixedUrl(foto))
                        .placeholder(R.drawable.ic_user_placeholder).into(ivPaseadorFoto);
                }
            }
        });
    }

    private void cargarMascotas(String duenoId, List<String> ids) {
        if (duenoId == null || ids == null) return;
        mascotasDetalleList.clear();
        
        for (String id : ids) {
            db.collection("duenos").document(duenoId).collection("mascotas").document(id)
                .get().addOnSuccessListener(doc -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (doc.exists()) {
                        // Extraer datos con seguridad de tipos
                        String nombre = doc.getString("nombre");
                        String raza = doc.getString("raza");
                        Integer edad = null;
                        if (doc.contains("edad")) {
                            Object edadObj = doc.get("edad");
                            if (edadObj instanceof Long) edad = ((Long) edadObj).intValue();
                        }
                        Double peso = null;
                        if (doc.contains("peso")) {
                            Object pesoObj = doc.get("peso");
                            if (pesoObj instanceof Double) peso = (Double) pesoObj;
                            else if (pesoObj instanceof Long) peso = ((Long) pesoObj).doubleValue();
                        }

                        mascotasDetalleList.add(new MascotaDetalleAdapter.MascotaDetalle(
                            nombre, raza, edad, peso, ""
                        ));
                        mascotasAdapter.notifyDataSetChanged();
                    }
                });
        }
    }

    private void mostrarDialogCancelar() {
        if (!ReservaEstadoValidator.canTransition(estadoActual, "CANCELADO")) {
            Toast.makeText(this, "No se puede cancelar en este estado.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Cancelar Paseo")
            .setMessage("¿Estás seguro de cancelar este servicio? Se notificará al usuario.")
            .setPositiveButton("Sí, cancelar", (d, w) -> {
                db.collection("reservas").document(idReserva)
                    .update("estado", "CANCELADO")
                    .addOnSuccessListener(v -> finish());
            })
            .setNegativeButton("No", null)
            .show();
    }
}