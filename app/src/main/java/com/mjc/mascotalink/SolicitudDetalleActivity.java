package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.mjc.mascotalink.MyApplication;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.util.BottomNavManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class SolicitudDetalleActivity extends AppCompatActivity {

    private static final String TAG = "SolicitudDetalleActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;
    private String currentUserRole;
    private String idReserva;
    private String grupoReservaId;

    // Views Generales
    private ImageView ivBack;
    private LinearLayout layoutBottomActions;
    
    // Status Card
    private TextView tvStatusTitle;
    private TextView tvStatusSubtitle;
    private ImageView ivStatusIcon;

    // Profile Card (Dynamic: Walker or Owner)
    private TextView tvLabelPerfil;
    private CircleImageView ivPerfilFoto;
    private TextView tvPerfilNombre;
    private TextView tvPerfilSubtitulo;
    private View btnVerPerfilOverlay;

    // Service Details
    private TextView tvDetalleFecha;
    private TextView tvDetalleDireccion;
    private TextView tvDetallePrecio;

    // Mascotas
    private View layoutMascotaIndividual;
    private TextView tvMascotaNombre;
    private TextView tvMascotaRaza;
    private TextView tvMascotaEdad;
    private TextView tvMascotaPeso;
    
    private View layoutMascotasMultiples;
    private RecyclerView rvMascotas;
    private MascotaDetalleAdapter mascotasAdapter;
    private List<MascotaDetalleAdapter.MascotaDetalle> mascotasDetalleList;

    // Notas
    private TextView tvNotas;

    // Actions
    private LinearLayout layoutActionsPaseador;
    private MaterialButton btnRechazar;
    private MaterialButton btnAceptar;
    private MaterialButton btnCancelarSolicitud;

    // Datos
    private String idDueno;
    private String idPaseador;
    private String targetProfileId; // ID del perfil a mostrar (el "otro" usuario)

    // Skeleton
    private View skeletonLayout;
    private View scrollViewContent;
    private static final long MIN_SKELETON_TIME = 800; // Tiempo mínimo para evitar parpadeo
    private long startTimeMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_solicitud_detalle);

        startTimeMillis = System.currentTimeMillis();
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        
        if (currentUser == null) {
            finish();
            return;
        }
        currentUserId = currentUser.getUid();
        
        // Determinar rol actual
        currentUserRole = BottomNavManager.getUserRole(this);
        if (currentUserRole == null) currentUserRole = "DUEÑO"; // Default safe

        idReserva = getIntent().getStringExtra("id_reserva");
        grupoReservaId = getIntent().getStringExtra("grupo_reserva_id");

        if (idReserva == null || idReserva.isEmpty()) {
            Toast.makeText(this, "Error: No se pudo cargar la solicitud", Toast.LENGTH_SHORT).show();
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
        tvStatusTitle = findViewById(R.id.tv_status_title);
        tvStatusSubtitle = findViewById(R.id.tv_status_subtitle);
        ivStatusIcon = findViewById(R.id.iv_status_icon);

        // Profile
        tvLabelPerfil = findViewById(R.id.tv_label_perfil);
        ivPerfilFoto = findViewById(R.id.iv_perfil_foto);
        tvPerfilNombre = findViewById(R.id.tv_perfil_nombre);
        tvPerfilSubtitulo = findViewById(R.id.tv_perfil_subtitulo);
        btnVerPerfilOverlay = findViewById(R.id.btn_ver_perfil_overlay);

        // Details
        tvDetalleFecha = findViewById(R.id.tv_detalle_fecha);
        tvDetalleDireccion = findViewById(R.id.tv_detalle_direccion);
        tvDetallePrecio = findViewById(R.id.tv_detalle_precio);

        // Mascotas
        layoutMascotaIndividual = findViewById(R.id.layout_mascota_individual);
        tvMascotaNombre = findViewById(R.id.tv_mascota_nombre);
        tvMascotaRaza = findViewById(R.id.tv_mascota_raza);
        tvMascotaEdad = findViewById(R.id.tv_mascota_edad);
        tvMascotaPeso = findViewById(R.id.tv_mascota_peso);

        layoutMascotasMultiples = findViewById(R.id.layout_mascotas_multiples);
        rvMascotas = findViewById(R.id.rv_mascotas);
        
        mascotasDetalleList = new ArrayList<>();
        mascotasAdapter = new MascotaDetalleAdapter(this, mascotasDetalleList);
        rvMascotas.setLayoutManager(new LinearLayoutManager(this));
        rvMascotas.setAdapter(mascotasAdapter);

        // Notas
        tvNotas = findViewById(R.id.tv_notas);

        // Actions
        layoutBottomActions = findViewById(R.id.layout_bottom_actions);
        layoutActionsPaseador = findViewById(R.id.layout_actions_paseador);
        btnRechazar = findViewById(R.id.btn_rechazar);
        btnAceptar = findViewById(R.id.btn_aceptar);
        btnCancelarSolicitud = findViewById(R.id.btn_cancelar_solicitud);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        btnVerPerfilOverlay.setOnClickListener(v -> {
            if (targetProfileId == null) return;
            
            Intent intent;
            if ("PASEADOR".equalsIgnoreCase(currentUserRole)) {
                // Soy Paseador, veo perfil de Dueño
                intent = new Intent(this, PerfilDuenoActivity.class);
                intent.putExtra("id_dueno", targetProfileId);
            } else {
                // Soy Dueño, veo perfil de Paseador
                intent = new Intent(this, PerfilPaseadorActivity.class);
                intent.putExtra("paseadorId", targetProfileId);
            }
            startActivity(intent);
        });

        // Configurar botones según rol
        if ("PASEADOR".equalsIgnoreCase(currentUserRole)) {
            layoutActionsPaseador.setVisibility(View.VISIBLE);
            btnCancelarSolicitud.setVisibility(View.GONE);
            
            btnRechazar.setOnClickListener(v -> mostrarDialogRechazar());
            btnAceptar.setOnClickListener(v -> aceptarSolicitud());
            
            // Textos para el paseador
            tvStatusTitle.setText("Nueva Solicitud");
            tvStatusSubtitle.setText("Tienes una nueva solicitud pendiente");
            tvLabelPerfil.setText("Información del cliente");
            
        } else {
            // DUEÑO
            layoutActionsPaseador.setVisibility(View.GONE);
            btnCancelarSolicitud.setVisibility(View.VISIBLE);
            
            btnCancelarSolicitud.setOnClickListener(v -> mostrarDialogCancelar());
            
            // Textos para el dueño
            tvStatusTitle.setText("Solicitud Enviada");
            tvStatusSubtitle.setText("Esperando respuesta del paseador");
            tvLabelPerfil.setText("Paseador solicitado");
        }
    }

    private void cargarDatosReserva() {
        db.collection("reservas").document(idReserva).get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) {
                    Toast.makeText(this, "La solicitud no existe", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // Obtener IDs
                DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
                DocumentReference paseadorRef = doc.getDocumentReference("id_paseador");
                
                idDueno = duenoRef != null ? duenoRef.getId() : null;
                idPaseador = paseadorRef != null ? paseadorRef.getId() : null;

                // Validación de seguridad básica
                if (!currentUserId.equals(idDueno) && !currentUserId.equals(idPaseador)) {
                    Toast.makeText(this, "No tienes permiso para ver esta solicitud", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // Cargar perfil del OTRO usuario
                if ("PASEADOR".equalsIgnoreCase(currentUserRole)) {
                    targetProfileId = idDueno;
                    cargarDatosUsuario(idDueno, "dueños");
                } else {
                    targetProfileId = idPaseador;
                    cargarDatosUsuario(idPaseador, "paseadores");
                }

                // Cargar detalles del servicio
                Date fecha = doc.getDate("fecha");
                Date hora = doc.getDate("hora_inicio");
                Double precio = doc.getDouble("costo_total");
                int duracion = doc.getLong("duracion_minutos") != null ? doc.getLong("duracion_minutos").intValue() : 60;
                String direccion = doc.getString("direccion_recogida");
                String notas = doc.getString("notas_dueno");

                SimpleDateFormat sdfFecha = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "ES"));
                SimpleDateFormat sdfHora = new SimpleDateFormat("HH:mm", Locale.getDefault());

                if (fecha != null && hora != null) {
                    // Calcular hora fin
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(hora);
                    cal.add(Calendar.MINUTE, duracion);
                    Date horaFin = cal.getTime();
                    
                    tvDetalleFecha.setText(String.format("%s • %s - %s", 
                        sdfFecha.format(fecha), sdfHora.format(hora), sdfHora.format(horaFin)));
                }

                tvDetallePrecio.setText(precio != null ? String.format(Locale.getDefault(), "$%.2f Total", precio) : "$0.00");
                tvDetalleDireccion.setText(direccion != null ? direccion : "Ubicación del cliente");
                
                if (notas != null && !notas.isEmpty()) {
                    tvNotas.setText(notas);
                } else {
                    tvNotas.setText("Sin notas adicionales.");
                }

                // Cargar Mascotas (Lógica híbrida)
                @SuppressWarnings("unchecked")
                List<String> mascotasIds = (List<String>) doc.get("mascotas");
                String idMascotaLegacy = doc.getString("id_mascota");

                if (mascotasIds != null && mascotasIds.size() > 1) {
                    // Múltiples mascotas -> RecyclerView
                    layoutMascotaIndividual.setVisibility(View.GONE);
                    layoutMascotasMultiples.setVisibility(View.VISIBLE);
                    cargarMultiplesMascotas(idDueno, mascotasIds);
                } else {
                    // Una sola mascota -> Card Individual
                    layoutMascotaIndividual.setVisibility(View.VISIBLE);
                    layoutMascotasMultiples.setVisibility(View.GONE);
                    
                    String targetMascotaId = (mascotasIds != null && !mascotasIds.isEmpty()) ? mascotasIds.get(0) : idMascotaLegacy;
                    if (targetMascotaId != null) {
                        cargarMascotaIndividual(idDueno, targetMascotaId);
                    }
                }
                
                hideSkeleton();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error cargando reserva", e);
                Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show();
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

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
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
            // Si tiene fondo de esqueleto, aplicar animación
            android.view.animation.Animation anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.shimmer_animation);
            view.startAnimation(anim);
        }
    }

    private void cargarDatosUsuario(String userId, String collection) {
        if (userId == null) return;
        
        // Intentar cargar nombre display de la colección de usuarios primero
        db.collection("usuarios").document(userId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String nombre = doc.getString("nombre_display");
                    String foto = doc.getString("foto_perfil");
                    
                    tvPerfilNombre.setText(nombre != null ? nombre : "Usuario");
                    
                    if (foto != null && !foto.isEmpty()) {
                        Glide.with(this)
                            .load(MyApplication.getFixedUrl(foto))
                            .placeholder(R.drawable.ic_user_placeholder)
                            .into(ivPerfilFoto);
                    }
                    
                    // Subtítulo dinámico
                    if ("PASEADOR".equalsIgnoreCase(currentUserRole)) {
                        tvPerfilSubtitulo.setText("Dueño de mascota");
                    } else {
                        // Si es paseador, cargar rating si es posible
                        cargarRatingPaseador(userId);
                    }
                }
            });
    }
    
    private void cargarRatingPaseador(String paseadorId) {
        db.collection("paseadores").document(paseadorId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    Double rating = doc.getDouble("calificacion_promedio");
                    if (rating != null) {
                        tvPerfilSubtitulo.setText(String.format(Locale.getDefault(), "⭐ %.1f • Ver perfil completo", rating));
                    }
                }
            });
    }

    private void cargarMascotaIndividual(String duenoId, String mascotaId) {
        if (duenoId == null || mascotaId == null) return;
        
        db.collection("duenos").document(duenoId).collection("mascotas").document(mascotaId)
            .get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    tvMascotaNombre.setText(doc.getString("nombre"));
                    tvMascotaRaza.setText(doc.getString("raza"));
                    tvMascotaEdad.setText(doc.getString("edad") + " años");
                    tvMascotaPeso.setText(doc.getString("peso") + " kg");
                }
            });
    }

    private void cargarMultiplesMascotas(String duenoId, List<String> ids) {
        if (duenoId == null || ids == null) return;
        
        mascotasDetalleList.clear();
        for (String id : ids) {
            db.collection("duenos").document(duenoId).collection("mascotas").document(id)
                .get().addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // Extraer datos con seguridad de tipos
                        String nombre = doc.getString("nombre");
                        String raza = doc.getString("raza");
                        
                        Integer edad = null;
                        if (doc.contains("edad")) {
                            Object edadObj = doc.get("edad");
                            if (edadObj instanceof Long) edad = ((Long) edadObj).intValue();
                            else if (edadObj instanceof String) {
                                try { edad = Integer.parseInt((String) edadObj); } catch (Exception e) {}
                            }
                        }
                        
                        Double peso = null;
                        if (doc.contains("peso")) {
                            Object pesoObj = doc.get("peso");
                            if (pesoObj instanceof Double) peso = (Double) pesoObj;
                            else if (pesoObj instanceof Long) peso = ((Long) pesoObj).doubleValue();
                            else if (pesoObj instanceof String) {
                                try { peso = Double.parseDouble((String) pesoObj); } catch (Exception e) {}
                            }
                        }

                        mascotasDetalleList.add(new MascotaDetalleAdapter.MascotaDetalle(
                            nombre,
                            raza,
                            edad,
                            peso,
                            "" // Notas vacías por ahora en la lista resumida
                        ));
                        mascotasAdapter.notifyDataSetChanged();
                    }
                });
        }
    }

    private void aceptarSolicitud() {
        // Implementar lógica existente de aceptación
        // ... (Reutilizar lógica previa si es necesario, o simplificar para este ejemplo)
        db.collection("reservas").document(idReserva)
            .update("estado", "ACEPTADO", "fecha_respuesta", com.google.firebase.Timestamp.now())
            .addOnSuccessListener(v -> {
                Toast.makeText(this, "Solicitud aceptada", Toast.LENGTH_SHORT).show();
                finish();
            });
    }

    private void mostrarDialogRechazar() {
        new AlertDialog.Builder(this)
            .setTitle("Rechazar solicitud")
            .setMessage("¿Estás seguro de que deseas rechazar esta solicitud?")
            .setPositiveButton("Rechazar", (d, w) -> {
                db.collection("reservas").document(idReserva)
                    .update("estado", "RECHAZADO", "fecha_respuesta", com.google.firebase.Timestamp.now())
                    .addOnSuccessListener(v -> finish());
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }
    
    private void mostrarDialogCancelar() {
        new AlertDialog.Builder(this)
            .setTitle("Cancelar solicitud")
            .setMessage("¿Quieres cancelar esta solicitud enviada? El paseador será notificado.")
            .setPositiveButton("Sí, cancelar", (d, w) -> {
                db.collection("reservas").document(idReserva)
                    .update("estado", "CANCELADO", "fecha_cancelacion", com.google.firebase.Timestamp.now())
                    .addOnSuccessListener(v -> {
                        Toast.makeText(this, "Solicitud cancelada", Toast.LENGTH_SHORT).show();
                        finish();
                    });
            })
            .setNegativeButton("Volver", null)
            .show();
    }
}