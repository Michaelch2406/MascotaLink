package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class SolicitudDetalleActivity extends AppCompatActivity {

    private static final String TAG = "SolicitudDetalleActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;
    private String idReserva;

    // Views
    private ImageButton ivBack;
    private CircleImageView ivDuenoFoto;
    private TextView tvDuenoNombre;
    private TextView tvFechaHorario;
    private TextView tvMascotaNombre;
    private TextView tvMascotaRaza;
    private TextView tvMascotaEdad;
    private TextView tvMascotaPeso;
    private TextView tvNotas;
    private Button btnVerPerfil;
    private Button btnRechazar;
    private Button btnAceptar;
    private BottomNavigationView bottomNav;
    private String bottomNavRole = "PASEADOR";
    private int bottomNavSelectedItem = R.id.menu_search;

    // Datos
    private String idDueno;
    private String idMascota; // Make idMascota a field
    private Date fecha;
    private Date horaInicio;
    private int duracionMinutos;
    private String estadoReserva;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_solicitud_detalle);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        
        if (currentUser == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        currentUserId = currentUser.getUid();

        // Obtener id_reserva del Intent
        idReserva = getIntent().getStringExtra("id_reserva");
        if (idReserva == null || idReserva.isEmpty()) {
            Toast.makeText(this, "Error: No se pudo cargar la solicitud", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupBottomNavigation();
        cargarDatosReserva();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupBottomNavigation();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        ivDuenoFoto = findViewById(R.id.iv_dueno_foto);
        tvDuenoNombre = findViewById(R.id.tv_dueno_nombre);
        tvFechaHorario = findViewById(R.id.tv_fecha_horario);
        tvMascotaNombre = findViewById(R.id.tv_mascota_nombre);
        tvMascotaRaza = findViewById(R.id.tv_mascota_raza);
        tvMascotaEdad = findViewById(R.id.tv_mascota_edad);
        tvMascotaPeso = findViewById(R.id.tv_mascota_peso);
        tvNotas = findViewById(R.id.tv_notas);
        btnVerPerfil = findViewById(R.id.btn_ver_perfil);
        btnRechazar = findViewById(R.id.btn_rechazar);
        btnAceptar = findViewById(R.id.btn_aceptar);
        bottomNav = findViewById(R.id.bottom_nav);

        // Botón atrás
        ivBack.setOnClickListener(v -> finish());

        // Botón Ver Perfil (Dueño)
        btnVerPerfil.setOnClickListener(v -> {
            if (idDueno != null && !idDueno.isEmpty()) {
                Intent intent = new Intent(SolicitudDetalleActivity.this, PerfilDuenoActivity.class);
                intent.putExtra("id_dueno", idDueno);
                startActivity(intent);
            } else {
                Toast.makeText(this, "No se pudo abrir el perfil del dueño", Toast.LENGTH_SHORT).show();
            }
        });

        // Make pet name clickable to see pet profile.
        // This implementation re-fetches the reservation on click to ensure data is fresh
        // and avoids any potential race conditions with class member variables.
        tvMascotaNombre.setOnClickListener(v -> {
            if (idReserva == null || idReserva.isEmpty()) {
                Toast.makeText(this, "Error: ID de reserva no disponible.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Re-fetch the document to get the most accurate data just before launching the intent
            db.collection("reservas").document(idReserva).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    DocumentReference duenoRef = documentSnapshot.getDocumentReference("id_dueno");
                    String mascotaIdFromDoc = documentSnapshot.getString("id_mascota");

                    if (duenoRef != null && mascotaIdFromDoc != null && !mascotaIdFromDoc.isEmpty()) {
                        String duenoIdFromDoc = duenoRef.getId();
                        
                        Log.d(TAG, "Lanzando PerfilMascotaActivity con dueno_id: " + duenoIdFromDoc + " y mascota_id: " + mascotaIdFromDoc);
                        
                        Intent intent = new Intent(SolicitudDetalleActivity.this, PerfilMascotaActivity.class);
                        intent.putExtra("dueno_id", duenoIdFromDoc);
                        intent.putExtra("mascota_id", mascotaIdFromDoc);
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, "No se pudo abrir el perfil de la mascota. Datos incompletos en la reserva.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "La reserva ya no existe.", Toast.LENGTH_SHORT).show();
                }
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error al re-obtener la reserva para ver perfil de mascota", e);
                Toast.makeText(this, "Error de red. No se pudo abrir el perfil.", Toast.LENGTH_SHORT).show();
            });
        });

        // Botón Rechazar
        btnRechazar.setOnClickListener(v -> mostrarDialogRechazar());

        // Botón Aceptar
        btnAceptar.setOnClickListener(v -> aceptarSolicitud());
    }

    private void cargarDatosReserva() {
        db.collection("reservas").document(idReserva)
                .get()
                .addOnSuccessListener(reservaDoc -> {
                    if (!reservaDoc.exists()) {
                        Toast.makeText(this, "La solicitud no existe", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    // Validación de permisos: el paseador debe ser el usuario actual
                    DocumentReference paseadorRef = reservaDoc.getDocumentReference("id_paseador");
                    if (paseadorRef != null && !currentUserId.equals(paseadorRef.getId())) {
                        Toast.makeText(this, "No tienes permiso para ver esta solicitud", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    // Obtener datos de la reserva
                    DocumentReference duenoRef = reservaDoc.getDocumentReference("id_dueno");
                    if (duenoRef != null) {
                        idDueno = duenoRef.getId();
                    }

                    fecha = reservaDoc.getTimestamp("fecha") != null ? reservaDoc.getTimestamp("fecha").toDate() : new Date();
                    horaInicio = reservaDoc.getTimestamp("hora_inicio") != null ? reservaDoc.getTimestamp("hora_inicio").toDate() : new Date();
                    Long duracion = reservaDoc.getLong("duracion_minutos");
                    duracionMinutos = duracion != null ? duracion.intValue() : 60;

                    idMascota = reservaDoc.getString("id_mascota"); // Assign to field
                    String notas = reservaDoc.getString("notas");
                    estadoReserva = reservaDoc.getString("estado");
                    actualizarBotonesPorEstado();

                    // Mostrar notas
                    if (notas != null && !notas.isEmpty()) {
                        tvNotas.setText(notas);
                    } else {
                        tvNotas.setText("Sin notas adicionales");
                    }

                    // Calcular y mostrar fecha y horario
                    String fechaHorario = formatearFechaHorario(fecha, horaInicio, duracionMinutos);
                    tvFechaHorario.setText(fechaHorario);

                    // Cargar datos del dueño
                    if (duenoRef != null) {
                        cargarDatosDueno(duenoRef);
                    }

                    // Cargar datos de la mascota
                    if (idDueno != null && idMascota != null && !idMascota.isEmpty()) {
                        cargarDatosMascota(idDueno, idMascota);
                    }

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar reserva", e);
                    Toast.makeText(this, "Error al cargar los datos de la solicitud", Toast.LENGTH_SHORT).show();
                    finish();
        });
    }

    private void setupBottomNavigation() {
        if (bottomNav == null) {
            return;
        }
        BottomNavManager.setupBottomNav(this, bottomNav, bottomNavRole, bottomNavSelectedItem);
    }

    private void actualizarBotonesPorEstado() {
        boolean puedeAceptar = ReservaEstadoValidator.canTransition(estadoReserva, ReservaEstadoValidator.ESTADO_ACEPTADO);
        boolean puedeRechazar = ReservaEstadoValidator.canTransition(estadoReserva, ReservaEstadoValidator.ESTADO_RECHAZADO);
        actualizarBoton(btnAceptar, puedeAceptar);
        actualizarBoton(btnRechazar, puedeRechazar);
    }

    private void actualizarBoton(Button boton, boolean habilitado) {
        if (boton == null) return;
        boton.setEnabled(habilitado);
        boton.setAlpha(habilitado ? 1f : 0.5f);
    }

    private void cargarDatosDueno(DocumentReference duenoRef) {
        duenoRef.get()
                .addOnSuccessListener(duenoDoc -> {
                    if (duenoDoc.exists()) {
                        String nombre = duenoDoc.getString("nombre_display");
                        String fotoUrl = duenoDoc.getString("foto_perfil");

                        tvDuenoNombre.setText(nombre != null ? nombre : "Usuario desconocido");

                        if (fotoUrl != null && !fotoUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(fotoUrl)
                                    .placeholder(R.drawable.ic_person)
                                    .circleCrop()
                                    .into(ivDuenoFoto);
                        } else {
                            ivDuenoFoto.setImageResource(R.drawable.ic_person);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar datos del dueño", e);
                    tvDuenoNombre.setText("Usuario desconocido");
                });
    }

    private void cargarDatosMascota(String duenoId, String mascotaId) {
        db.collection("duenos").document(duenoId)
                .collection("mascotas").document(mascotaId)
                .get()
                .addOnSuccessListener(mascotaDoc -> {
                    if (mascotaDoc.exists()) {
                        String nombre = mascotaDoc.getString("nombre");
                        String raza = mascotaDoc.getString("raza");
                        Double peso = mascotaDoc.getDouble("peso");

                        tvMascotaNombre.setText(nombre != null ? nombre : "Mascota");
                        tvMascotaRaza.setText(raza != null ? raza : "No especificada");

                        Timestamp fechaNacimientoTimestamp = mascotaDoc.getTimestamp("fecha_nacimiento");
                        if (fechaNacimientoTimestamp != null) {
                            Date fechaNacimiento = fechaNacimientoTimestamp.toDate();
                            Calendar dob = Calendar.getInstance();
                            dob.setTime(fechaNacimiento);
                            Calendar today = Calendar.getInstance();
                            int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
                            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                                age--;
                            }
                            tvMascotaEdad.setText(age + " años");
                        } else {
                            tvMascotaEdad.setText("No especificada");
                        }

                        if (peso != null) {
                            tvMascotaPeso.setText(String.format(Locale.US, "%.1f kg", peso));
                        } else {
                            tvMascotaPeso.setText("No especificado");
                        }
                    } else {
                        tvMascotaNombre.setText("Mascota eliminada");
                        tvMascotaRaza.setText("No disponible");
                        tvMascotaEdad.setText("No disponible");
                        tvMascotaPeso.setText("No disponible");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar datos de la mascota", e);
                    tvMascotaNombre.setText("Error al cargar");
                    tvMascotaRaza.setText("Error al cargar");
                    tvMascotaEdad.setText("Error al cargar");
                    tvMascotaPeso.setText("Error al cargar");
                });
    }

    private String formatearFechaHorario(Date fecha, Date horaInicio, int duracionMinutos) {
        try {
            // Formato para fecha: "12 de junio"
            SimpleDateFormat sdfFecha = new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES"));
            String fechaStr = sdfFecha.format(fecha);

            // Calcular hora de fin
            Calendar cal = Calendar.getInstance();
            cal.setTime(horaInicio);
            cal.add(Calendar.MINUTE, duracionMinutos);
            Date horaFin = cal.getTime();

            // Formato para horas: "10:00 AM - 11:00 AM"
            SimpleDateFormat sdfHora = new SimpleDateFormat("h:mm a", Locale.US);
            String horaInicioStr = sdfHora.format(horaInicio);
            String horaFinStr = sdfHora.format(horaFin);

            return fechaStr + ", " + horaInicioStr + " - " + horaFinStr;
        } catch (Exception e) {
            return "Fecha no disponible";
        }
    }

    private void mostrarDialogRechazar() {
        if (isFinishing()) {
            return;
        }

        if (!ReservaEstadoValidator.canTransition(estadoReserva, ReservaEstadoValidator.ESTADO_RECHAZADO)) {
            Toast.makeText(this, "No se puede rechazar esta solicitud en su estado actual.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] motivos = {
            "Estoy ocupado",
            "No puedo llegar a esa hora",
            "No trabajo ese día",
            "Otro motivo"
        };

        new AlertDialog.Builder(this)
                .setTitle("Motivo de rechazo")
                .setItems(motivos, (dialog, which) -> {
                    String motivo = motivos[which];
                    rechazarSolicitud(motivo);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void rechazarSolicitud(String motivo) {
        if (!ReservaEstadoValidator.canTransition(estadoReserva, ReservaEstadoValidator.ESTADO_RECHAZADO)) {
            Toast.makeText(this, "No se puede rechazar esta solicitud en su estado actual.", Toast.LENGTH_SHORT).show();
            return;
        }
        btnRechazar.setEnabled(false);
        btnAceptar.setEnabled(false);

        db.collection("reservas").document(idReserva)
                .update(
                        "estado", ReservaEstadoValidator.ESTADO_RECHAZADO,
                        "motivo_rechazo", motivo,
                        "fecha_respuesta", Timestamp.now()
                )
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Solicitud rechazada", Toast.LENGTH_SHORT).show();
                    estadoReserva = ReservaEstadoValidator.ESTADO_RECHAZADO;
                    actualizarBotonesPorEstado();
                    // Volver a SolicitudesActivity después de 1 segundo
                    new android.os.Handler().postDelayed(() -> {
                        finish();
                    }, 1000);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al rechazar solicitud", e);
                    Toast.makeText(this, "No se pudo rechazar. Intenta de nuevo.", Toast.LENGTH_SHORT).show();
                    btnRechazar.setEnabled(true);
                    btnAceptar.setEnabled(true);
                });
    }

    private void aceptarSolicitud() {
        if (!ReservaEstadoValidator.canTransition(estadoReserva, ReservaEstadoValidator.ESTADO_ACEPTADO)) {
            Toast.makeText(this, "No se puede aceptar esta solicitud en su estado actual.", Toast.LENGTH_SHORT).show();
            return;
        }
        btnAceptar.setEnabled(false);
        btnRechazar.setEnabled(false);
        btnVerPerfil.setEnabled(false);

        db.collection("reservas").document(idReserva)
                .update(
                        "estado", ReservaEstadoValidator.ESTADO_ACEPTADO,
                        "fecha_respuesta", Timestamp.now()
                )
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "¡Solicitud aceptada!", Toast.LENGTH_SHORT).show();
                    estadoReserva = ReservaEstadoValidator.ESTADO_ACEPTADO;
                    actualizarBotonesPorEstado();
                    // Volver a SolicitudesActivity después de 1 segundo
                    new android.os.Handler().postDelayed(() -> {
                        finish();
                    }, 1000);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al aceptar solicitud", e);
                    Toast.makeText(this, "No se pudo aceptar. Intenta de nuevo.", Toast.LENGTH_SHORT).show();
                    btnAceptar.setEnabled(true);
                    btnRechazar.setEnabled(true);
                    btnVerPerfil.setEnabled(true);
                });
    }
}
