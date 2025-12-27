package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.Timestamp;
import com.mjc.mascotalink.MyApplication;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.util.BottomNavManager;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class SolicitudDetalleActivity extends AppCompatActivity {

    private static final String TAG = "SolicitudDetalleActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;
    private String idReserva;
    private String grupoReservaId;  // Para manejar reservas agrupadas

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
    private com.google.android.material.button.MaterialButton btnCalendar;
    private BottomNavigationView bottomNav;
    private String bottomNavRole = "PASEADOR";
    private int bottomNavSelectedItem = R.id.menu_search;

    // Views para múltiples mascotas
    private LinearLayout layoutMascotaIndividual;
    private LinearLayout layoutMascotasMultiples;
    private RecyclerView rvMascotas;
    private MascotaDetalleAdapter mascotasAdapter;
    private List<MascotaDetalleAdapter.MascotaDetalle> mascotasDetalleList;

    // Datos
    private String idDueno;
    private String idMascota; // Backward compatibility - single pet
    private List<String> mascotasIds; // New format - multiple pets
    private List<String> mascotasNombres; // New format - multiple pet names
    private Date fecha;
    private Date horaInicio;
    private int duracionMinutos;
    private String estadoReserva;
    private String direccionRecogida; // Dirección para el calendario

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

        // Obtener id_reserva y grupo_reserva_id del Intent
        idReserva = getIntent().getStringExtra("id_reserva");
        grupoReservaId = getIntent().getStringExtra("grupo_reserva_id");

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
        btnCalendar = findViewById(R.id.btn_calendar);
        bottomNav = findViewById(R.id.bottom_nav);

        // Inicializar views para múltiples mascotas
        layoutMascotaIndividual = findViewById(R.id.layout_mascota_individual);
        layoutMascotasMultiples = findViewById(R.id.layout_mascotas_multiples);
        rvMascotas = findViewById(R.id.rv_mascotas);

        // Configurar RecyclerView
        mascotasDetalleList = new ArrayList<>();
        mascotasAdapter = new MascotaDetalleAdapter(this, mascotasDetalleList);
        rvMascotas.setLayoutManager(new LinearLayoutManager(this));
        rvMascotas.setAdapter(mascotasAdapter);

        // Botón atrás
        ivBack.setOnClickListener(v -> finish());

        // Botón Calendario
        btnCalendar.setOnClickListener(v -> {
            if (fecha == null || horaInicio == null) return;
            
            String nombreMascota = tvMascotaNombre.getText().toString();
            String nombreDueno = tvDuenoNombre.getText().toString();
            
            // Usar dirección real si existe, o genérica
            String direccionFinal = (direccionRecogida != null && !direccionRecogida.isEmpty()) 
                    ? direccionRecogida : "Ubicación del cliente";
            
            try {
                Toast.makeText(this, "Abriendo calendario...", Toast.LENGTH_SHORT).show();
                com.mjc.mascotalink.utils.GoogleCalendarHelper.addWalkToCalendarForWalker(
                        this,
                        nombreMascota,
                        nombreDueno,
                        direccionFinal,
                        new Timestamp(horaInicio),
                        null,
                        idReserva,
                        duracionMinutos,
                        tvNotas.getText().toString()
                );
                
                // CERRAR LA PANTALLA después de lanzar el calendario
                finishWithDelay();
                
            } catch (Exception e) {
                Log.e(TAG, "Error al abrir calendario", e);
                Toast.makeText(this, "Error al abrir la app de calendario: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

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

                    // Soportar ambos formatos: nuevo (mascotas array) y antiguo (id_mascota string)
                    @SuppressWarnings("unchecked")
                    List<String> mascotasFromDoc = (List<String>) documentSnapshot.get("mascotas");
                    String mascotaIdFromDoc = documentSnapshot.getString("id_mascota");

                    // Si tiene múltiples mascotas, no permitir ver perfil individual
                    if (mascotasFromDoc != null && mascotasFromDoc.size() > 1) {
                        Toast.makeText(this, "Esta reserva incluye múltiples mascotas. No se puede ver perfil individual.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Determinar el ID de la mascota a mostrar
                    String finalMascotaId = null;
                    if (mascotasFromDoc != null && !mascotasFromDoc.isEmpty()) {
                        finalMascotaId = mascotasFromDoc.get(0); // Primera mascota del array
                    } else if (mascotaIdFromDoc != null && !mascotaIdFromDoc.isEmpty()) {
                        finalMascotaId = mascotaIdFromDoc; // Formato antiguo
                    }

                    if (duenoRef != null && finalMascotaId != null && !finalMascotaId.isEmpty()) {
                        String duenoIdFromDoc = duenoRef.getId();

                        Log.d(TAG, "Lanzando PerfilMascotaActivity con dueno_id: " + duenoIdFromDoc + " y mascota_id: " + finalMascotaId);

                        Intent intent = new Intent(SolicitudDetalleActivity.this, PerfilMascotaActivity.class);
                        intent.putExtra("dueno_id", duenoIdFromDoc);
                        intent.putExtra("mascota_id", finalMascotaId);
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

                    // Verificar si es parte de un grupo
                    Boolean esGrupo = reservaDoc.getBoolean("es_grupo");
                    String grupoId = reservaDoc.getString("grupo_reserva_id");
                    String tipoReserva = reservaDoc.getString("tipo_reserva");
                    if (tipoReserva == null) tipoReserva = "PUNTUAL";

                    if (esGrupo != null && esGrupo && grupoId != null && !grupoId.isEmpty()) {
                        // Es un grupo de días específicos - cargar todas las reservas del grupo
                        grupoReservaId = grupoId;
                        cargarGrupoReservas(reservaDoc, grupoId);
                        return;
                    } else if ("SEMANAL".equals(tipoReserva)) {
                        // Reserva semanal: 7 días
                        cargarReservaSemanalMensual(reservaDoc, 7);
                        return;
                    } else if ("MENSUAL".equals(tipoReserva)) {
                        // Reserva mensual: 30 días
                        cargarReservaSemanalMensual(reservaDoc, 30);
                        return;
                    }

                    
                    // Obtener dirección de recogida
                    direccionRecogida = reservaDoc.getString("direccion_recogida");
                    if (direccionRecogida == null) direccionRecogida = "";

                    // Obtener datos de la reserva
                    DocumentReference duenoRef = reservaDoc.getDocumentReference("id_dueno");
                    if (duenoRef != null) {
                        idDueno = duenoRef.getId();
                    }

                    fecha = reservaDoc.getTimestamp("fecha") != null ? reservaDoc.getTimestamp("fecha").toDate() : new Date();
                    horaInicio = reservaDoc.getTimestamp("hora_inicio") != null ? reservaDoc.getTimestamp("hora_inicio").toDate() : new Date();
                    
                    // Safe check for duration
                    Long duracion = reservaDoc.getLong("duracion_minutos");
                    duracionMinutos = duracion != null ? duracion.intValue() : 60; // Default 60 min

                    // Soportar ambos formatos: nuevo (mascotas array) y antiguo (id_mascota string)
                    @SuppressWarnings("unchecked")
                    List<String> mascotasIdsFromDoc = (List<String>) reservaDoc.get("mascotas");
                    @SuppressWarnings("unchecked")
                    List<String> mascotasNombresFromDoc = (List<String>) reservaDoc.get("mascotas_nombres");
                    String idMascotaFromDoc = reservaDoc.getString("id_mascota");

                    if (mascotasIdsFromDoc != null && !mascotasIdsFromDoc.isEmpty()) {
                        // Formato nuevo: múltiples mascotas
                        mascotasIds = mascotasIdsFromDoc;
                        mascotasNombres = mascotasNombresFromDoc;
                        idMascota = null; // Clear old format
                    } else if (idMascotaFromDoc != null && !idMascotaFromDoc.isEmpty()) {
                        // Formato antiguo: una sola mascota
                        idMascota = idMascotaFromDoc;
                        mascotasIds = null;
                        mascotasNombres = null;
                    }

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
                    if (idDueno != null) {
                        if (mascotasIds != null && !mascotasIds.isEmpty()) {
                            // Formato nuevo: cargar datos completos desde Firestore
                            Log.d(TAG, "Cargando múltiples mascotas. IDs: " + mascotasIds.size());
                            cargarDatosMultiplesMascotas(idDueno, mascotasIds);
                        } else if (idMascota != null && !idMascota.isEmpty()) {
                            // Formato antiguo: una sola mascota
                            cargarDatosMascota(idDueno, idMascota);
                        }
                    }

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar reserva", e);
                    Toast.makeText(this, "Error al cargar los datos de la solicitud", Toast.LENGTH_SHORT).show();
                    finish();
        });
    }

    /**
     * Carga todas las reservas de un grupo y muestra información agrupada
     */
    /**
     * Carga datos para reservas SEMANAL o MENSUAL (1 documento que representa múltiples días)
     */
    private void cargarReservaSemanalMensual(DocumentSnapshot reservaDoc, int cantidadDias) {
        // Obtener dirección de recogida
        direccionRecogida = reservaDoc.getString("direccion_recogida");
        if (direccionRecogida == null) direccionRecogida = "";

        // Obtener datos de la reserva
        DocumentReference duenoRef = reservaDoc.getDocumentReference("id_dueno");
        if (duenoRef != null) {
            idDueno = duenoRef.getId();
        }

        Date fechaInicio = reservaDoc.getTimestamp("fecha") != null ?
                reservaDoc.getTimestamp("fecha").toDate() : new Date();

        // Calcular fecha fin
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(fechaInicio);
        cal.add(java.util.Calendar.DAY_OF_MONTH, cantidadDias - 1);
        Date fechaFin = cal.getTime();

        horaInicio = reservaDoc.getTimestamp("hora_inicio") != null ?
                reservaDoc.getTimestamp("hora_inicio").toDate() : new Date();

        Long duracion = reservaDoc.getLong("duracion_minutos");
        duracionMinutos = duracion != null ? duracion.intValue() : 60;

        // Soportar ambos formatos: nuevo (mascotas array) y antiguo (id_mascota string)
        @SuppressWarnings("unchecked")
        List<String> mascotasIdsFromDoc = (List<String>) reservaDoc.get("mascotas");
        @SuppressWarnings("unchecked")
        List<String> mascotasNombresFromDoc = (List<String>) reservaDoc.get("mascotas_nombres");
        String idMascotaFromDoc = reservaDoc.getString("id_mascota");

        if (mascotasIdsFromDoc != null && !mascotasIdsFromDoc.isEmpty()) {
            // Formato nuevo: múltiples mascotas
            mascotasIds = mascotasIdsFromDoc;
            mascotasNombres = mascotasNombresFromDoc;
            idMascota = null;
        } else if (idMascotaFromDoc != null && !idMascotaFromDoc.isEmpty()) {
            // Formato antiguo: una sola mascota
            idMascota = idMascotaFromDoc;
            mascotasIds = null;
            mascotasNombres = null;
        }

        String notas = reservaDoc.getString("notas");
        estadoReserva = reservaDoc.getString("estado");
        actualizarBotonesPorEstado();

        // Mostrar notas
        if (notas != null && !notas.isEmpty()) {
            tvNotas.setText(notas);
        } else {
            tvNotas.setText("Sin notas adicionales");
        }

        // Mostrar rango de fechas y horario
        String tipoTexto = cantidadDias == 7 ? "Semanal" : "Mensual";
        String fechaHorario = formatearFechaHorarioGrupo(fechaInicio, fechaFin, horaInicio, duracionMinutos, cantidadDias);
        tvFechaHorario.setText("🔁 " + tipoTexto + "\n" + fechaHorario);

        // Cargar datos del dueño
        if (duenoRef != null) {
            cargarDatosDueno(duenoRef);
        }

        // Cargar datos de la mascota
        if (idDueno != null) {
            if (mascotasIds != null && !mascotasIds.isEmpty()) {
                // Formato nuevo: cargar datos completos desde Firestore
                Log.d(TAG, "Cargando múltiples mascotas. IDs: " + mascotasIds.size());
                cargarDatosMultiplesMascotas(idDueno, mascotasIds);
            } else if (idMascota != null && !idMascota.isEmpty()) {
                // Formato antiguo: una sola mascota
                cargarDatosMascota(idDueno, idMascota);
            }
        }
    }

    private void cargarGrupoReservas(DocumentSnapshot primerReserva, String grupoId) {
        db.collection("reservas")
                .whereEqualTo("grupo_reserva_id", grupoId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(this, "No se encontraron reservas del grupo", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    // Ordenar reservas por fecha
                    java.util.List<DocumentSnapshot> reservas = new java.util.ArrayList<>(querySnapshot.getDocuments());
                    reservas.sort((r1, r2) -> {
                        Timestamp t1 = r1.getTimestamp("fecha");
                        Timestamp t2 = r2.getTimestamp("fecha");
                        if (t1 == null || t2 == null) return 0;
                        return t1.compareTo(t2);
                    });

                    int cantidadDias = reservas.size();
                    Date fechaInicio = reservas.get(0).getTimestamp("fecha") != null ?
                            reservas.get(0).getTimestamp("fecha").toDate() : new Date();
                    Date fechaFin = reservas.get(cantidadDias - 1).getTimestamp("fecha") != null ?
                            reservas.get(cantidadDias - 1).getTimestamp("fecha").toDate() : new Date();

                    // Obtener datos comunes (todos comparten dueño, mascota, hora, duración)
                    direccionRecogida = primerReserva.getString("direccion_recogida");
                    if (direccionRecogida == null) direccionRecogida = "";

                    DocumentReference duenoRef = primerReserva.getDocumentReference("id_dueno");
                    if (duenoRef != null) {
                        idDueno = duenoRef.getId();
                    }

                    horaInicio = primerReserva.getTimestamp("hora_inicio") != null ?
                            primerReserva.getTimestamp("hora_inicio").toDate() : new Date();

                    Long duracion = primerReserva.getLong("duracion_minutos");
                    duracionMinutos = duracion != null ? duracion.intValue() : 60;

                    // Soportar ambos formatos: nuevo (mascotas array) y antiguo (id_mascota string)
                    @SuppressWarnings("unchecked")
                    List<String> mascotasIdsFromDoc = (List<String>) primerReserva.get("mascotas");
                    @SuppressWarnings("unchecked")
                    List<String> mascotasNombresFromDoc = (List<String>) primerReserva.get("mascotas_nombres");
                    String idMascotaFromDoc = primerReserva.getString("id_mascota");

                    if (mascotasIdsFromDoc != null && !mascotasIdsFromDoc.isEmpty()) {
                        // Formato nuevo: múltiples mascotas
                        mascotasIds = mascotasIdsFromDoc;
                        mascotasNombres = mascotasNombresFromDoc;
                        idMascota = null;
                    } else if (idMascotaFromDoc != null && !idMascotaFromDoc.isEmpty()) {
                        // Formato antiguo: una sola mascota
                        idMascota = idMascotaFromDoc;
                        mascotasIds = null;
                        mascotasNombres = null;
                    }

                    String notas = primerReserva.getString("notas");
                    estadoReserva = primerReserva.getString("estado");
                    actualizarBotonesPorEstado();

                    // Mostrar notas
                    if (notas != null && !notas.isEmpty()) {
                        tvNotas.setText(notas);
                    } else {
                        tvNotas.setText("Sin notas adicionales");
                    }

                    // Mostrar rango de fechas y horario agrupado
                    String fechaHorario = formatearFechaHorarioGrupo(fechaInicio, fechaFin, horaInicio, duracionMinutos, cantidadDias);
                    tvFechaHorario.setText(fechaHorario);

                    // Cargar datos del dueño
                    if (duenoRef != null) {
                        cargarDatosDueno(duenoRef);
                    }

                    // Cargar datos de la mascota
                    if (idDueno != null) {
                        if (mascotasIds != null && !mascotasIds.isEmpty()) {
                            // Formato nuevo: cargar datos completos desde Firestore
                            Log.d(TAG, "Cargando múltiples mascotas. IDs: " + mascotasIds.size());
                            cargarDatosMultiplesMascotas(idDueno, mascotasIds);
                        } else if (idMascota != null && !idMascota.isEmpty()) {
                            // Formato antiguo: una sola mascota
                            cargarDatosMascota(idDueno, idMascota);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar grupo de reservas", e);
                    Toast.makeText(this, "Error al cargar los datos del grupo", Toast.LENGTH_SHORT).show();
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

        // CORRECCIÓN: Asegurar que el botón de perfil siempre esté habilitado al actualizar la UI
        if (btnVerPerfil != null) {
            btnVerPerfil.setEnabled(true);
        }

        // Mostrar botón calendario si está aceptada o confirmada
        if (ReservaEstadoValidator.ESTADO_ACEPTADO.equals(estadoReserva) || 
            ReservaEstadoValidator.ESTADO_CONFIRMADO.equals(estadoReserva)) {
            if (btnCalendar != null) {
                btnCalendar.setVisibility(View.VISIBLE);
                btnCalendar.setEnabled(true); // Asegurar habilitado
            }
            // Ocultar botones de decisión si ya está decidido
            btnAceptar.setVisibility(View.GONE);
            btnRechazar.setVisibility(View.GONE);
        } else {
            if (btnCalendar != null) {
                btnCalendar.setVisibility(View.GONE);
            }
            // Asegurar que sean visibles si es pendiente
            if (ReservaEstadoValidator.ESTADO_PENDIENTE_ACEPTACION.equals(estadoReserva)) {
                btnAceptar.setVisibility(View.VISIBLE);
                btnRechazar.setVisibility(View.VISIBLE);
            }
        }
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
                                    .load(MyApplication.getFixedUrl(fotoUrl))
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

    /**
     * Carga datos completos de múltiples mascotas desde Firestore y los muestra en cards
     */
    private void cargarDatosMultiplesMascotas(String duenoId, List<String> mascotasIds) {
        Log.d(TAG, "cargarDatosMultiplesMascotas - duenoId: " + duenoId + ", mascotasIds: " + (mascotasIds != null ? mascotasIds.size() : "null"));

        if (mascotasIds == null || mascotasIds.isEmpty()) {
            Log.d(TAG, "mascotasIds es null o vacío");
            layoutMascotaIndividual.setVisibility(View.VISIBLE);
            layoutMascotasMultiples.setVisibility(View.GONE);
            return;
        }

        // Si solo hay una mascota, usar el método tradicional
        if (mascotasIds.size() == 1) {
            Log.d(TAG, "Solo hay 1 mascota, usando layout individual");
            layoutMascotaIndividual.setVisibility(View.VISIBLE);
            layoutMascotasMultiples.setVisibility(View.GONE);
            cargarDatosMascota(duenoId, mascotasIds.get(0));
            return;
        }

        // Mostrar layout de múltiples mascotas
        Log.d(TAG, "Hay " + mascotasIds.size() + " mascotas, mostrando layout múltiple");
        layoutMascotaIndividual.setVisibility(View.GONE);
        layoutMascotasMultiples.setVisibility(View.VISIBLE);

        // Ocultar el campo de notas de la reserva (cada mascota tiene sus propias notas en las cards)
        View notasSection = findViewById(R.id.layout_notas_reserva);
        if (notasSection != null) {
            notasSection.setVisibility(View.GONE);
        }

        // Cargar datos completos de todas las mascotas
        List<MascotaDetalleAdapter.MascotaDetalle> mascotasTemp = new ArrayList<>();
        final int[] contador = {0}; // Para rastrear cuántas se han cargado

        for (String mascotaId : mascotasIds) {
            Log.d(TAG, "Cargando mascota con ID: " + mascotaId);
            db.collection("duenos").document(duenoId)
                    .collection("mascotas").document(mascotaId)
                    .get()
                    .addOnSuccessListener(mascotaDoc -> {
                        contador[0]++;
                        if (mascotaDoc.exists()) {
                            String nombre = mascotaDoc.getString("nombre");
                            String raza = mascotaDoc.getString("raza");
                            Double peso = mascotaDoc.getDouble("peso");

                            // Calcular edad en años desde fecha_nacimiento
                            Integer edadAnios = null;
                            com.google.firebase.Timestamp fechaNacimiento = mascotaDoc.getTimestamp("fecha_nacimiento");
                            if (fechaNacimiento != null) {
                                long edadMeses = calcularEdadEnMeses(fechaNacimiento.toDate());
                                edadAnios = (int) (edadMeses / 12);
                            }

                            // Obtener notas desde el mapa anidado instrucciones
                            String notas = null;
                            @SuppressWarnings("unchecked")
                            Map<String, Object> instrucciones = (Map<String, Object>) mascotaDoc.get("instrucciones");
                            if (instrucciones != null) {
                                notas = (String) instrucciones.get("notas_adicionales");
                            }

                            Log.d(TAG, "Mascota cargada: " + nombre + ", raza: " + raza + ", edad: " + edadAnios + " años, notas: " + (notas != null ? notas.substring(0, Math.min(notas.length(), 20)) : "null"));

                            MascotaDetalleAdapter.MascotaDetalle detalle = new MascotaDetalleAdapter.MascotaDetalle(
                                    nombre, raza, edadAnios, peso, notas
                            );
                            mascotasTemp.add(detalle);
                        } else {
                            Log.e(TAG, "Documento de mascota no existe: " + mascotaId);
                        }

                        // Cuando se hayan cargado todas, actualizar adapter
                        if (contador[0] == mascotasIds.size()) {
                            Log.d(TAG, "Todas las mascotas cargadas (" + mascotasTemp.size() + "), actualizando adapter");
                            mascotasAdapter.updateList(mascotasTemp);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error al cargar mascota: " + mascotaId, e);
                        contador[0]++;
                        // Aunque falle, seguir con el proceso
                        if (contador[0] == mascotasIds.size()) {
                            Log.d(TAG, "Todas las mascotas procesadas (con errores), actualizando adapter con " + mascotasTemp.size() + " items");
                            mascotasAdapter.updateList(mascotasTemp);
                        }
                    });
        }
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

    /**
     * Formatea el rango de fechas para reservas agrupadas
     */
    private String formatearFechaHorarioGrupo(Date fechaInicio, Date fechaFin, Date horaInicio, int duracionMinutos, int cantidadDias) {
        try {
            SimpleDateFormat sdfFecha = new SimpleDateFormat("d 'de' MMM", new Locale("es", "ES"));
            String fechaInicioStr = sdfFecha.format(fechaInicio);
            String fechaFinStr = sdfFecha.format(fechaFin);

            // Calcular hora de fin
            Calendar cal = Calendar.getInstance();
            cal.setTime(horaInicio);
            cal.add(Calendar.MINUTE, duracionMinutos);
            Date horaFin = cal.getTime();

            SimpleDateFormat sdfHora = new SimpleDateFormat("h:mm a", Locale.US);
            String horaInicioStr = sdfHora.format(horaInicio);
            String horaFinStr = sdfHora.format(horaFin);

            return cantidadDias + " días (" + fechaInicioStr + " - " + fechaFinStr + ")\n" +
                    horaInicioStr + " - " + horaFinStr + " cada día";
        } catch (Exception e) {
            return cantidadDias + " días seleccionados";
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

        // Si es un grupo, rechazar todas las reservas del grupo atómicamente
        if (grupoReservaId != null && !grupoReservaId.isEmpty()) {
            rechazarGrupoReservas(motivo);
        } else {
            // Reserva individual - usar retry helper para operación crítica de cambio de estado
            com.mjc.mascotalink.util.FirestoreRetryHelper.execute(
                    () -> db.collection("reservas").document(idReserva).update(
                            "estado", ReservaEstadoValidator.ESTADO_RECHAZADO,
                            "motivo_rechazo", motivo,
                            "fecha_respuesta", com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ),
                    aVoid -> {
                        Toast.makeText(this, "Solicitud rechazada", Toast.LENGTH_SHORT).show();
                        estadoReserva = ReservaEstadoValidator.ESTADO_RECHAZADO;
                        actualizarBotonesPorEstado();
                        // Volver a SolicitudesActivity después de 1 segundo
                        finishWithDelay();
                    },
                    e -> {
                        Log.e(TAG, "Error al rechazar solicitud después de reintentos", e);
                        Toast.makeText(this, "No se pudo rechazar después de varios intentos. Verifica tu conexión.", Toast.LENGTH_LONG).show();
                        btnRechazar.setEnabled(true);
                        btnAceptar.setEnabled(true);
                    },
                    3  // 3 reintentos para operación crítica
            );
        }
    }

    /**
     * Rechaza todas las reservas de un grupo atómicamente usando transacción
     */
    private void rechazarGrupoReservas(String motivo) {
        // Primero obtener todas las reservas del grupo
        db.collection("reservas")
                .whereEqualTo("grupo_reserva_id", grupoReservaId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(this, "No se encontraron reservas del grupo", Toast.LENGTH_SHORT).show();
                        btnRechazar.setEnabled(true);
                        btnAceptar.setEnabled(true);
                        return;
                    }

                    // Usar transacción para actualizar todas las reservas atómicamente
                    db.runTransaction(transaction -> {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            transaction.update(doc.getReference(),
                                    "estado", ReservaEstadoValidator.ESTADO_RECHAZADO,
                                    "motivo_rechazo", motivo,
                                    "fecha_respuesta", com.google.firebase.firestore.FieldValue.serverTimestamp()
                            );
                        }
                        return null;
                    }).addOnSuccessListener(aVoid -> {
                        int cantidadReservas = querySnapshot.size();
                        Toast.makeText(this, cantidadReservas + " reservas rechazadas", Toast.LENGTH_SHORT).show();
                        estadoReserva = ReservaEstadoValidator.ESTADO_RECHAZADO;
                        actualizarBotonesPorEstado();
                        // Volver a SolicitudesActivity después de 1 segundo
                        finishWithDelay();
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Error al rechazar grupo de reservas", e);
                        Toast.makeText(this, "Error al rechazar las reservas. Verifica tu conexión.", Toast.LENGTH_LONG).show();
                        btnRechazar.setEnabled(true);
                        btnAceptar.setEnabled(true);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar grupo de reservas para rechazar", e);
                    Toast.makeText(this, "Error al cargar las reservas del grupo", Toast.LENGTH_SHORT).show();
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

        // Si es un grupo, aceptar todas las reservas del grupo atómicamente
        if (grupoReservaId != null && !grupoReservaId.isEmpty()) {
            aceptarGrupoReservas();
        } else {
            // Reserva individual - usar retry helper para operación crítica de aceptación
            com.mjc.mascotalink.util.FirestoreRetryHelper.execute(
                    () -> db.collection("reservas").document(idReserva).update(
                            "estado", ReservaEstadoValidator.ESTADO_ACEPTADO,
                            "fecha_respuesta", com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ),
                    aVoid -> {
                        Toast.makeText(this, "¡Solicitud aceptada!", Toast.LENGTH_SHORT).show();
                        estadoReserva = ReservaEstadoValidator.ESTADO_ACEPTADO;
                        actualizarBotonesPorEstado();
                        // NO cerramos la actividad para permitir agregar al calendario
                    },
                    e -> {
                        Log.e(TAG, "Error al aceptar solicitud después de reintentos", e);
                        Toast.makeText(this, "No se pudo aceptar después de varios intentos. Verifica tu conexión.", Toast.LENGTH_LONG).show();
                        btnAceptar.setEnabled(true);
                        btnRechazar.setEnabled(true);
                        btnVerPerfil.setEnabled(true);
                    },
                    3  // 3 reintentos para operación crítica
            );
        }
    }

    /**
     * Acepta todas las reservas de un grupo atómicamente usando transacción
     */
    private void aceptarGrupoReservas() {
        // Primero obtener todas las reservas del grupo
        db.collection("reservas")
                .whereEqualTo("grupo_reserva_id", grupoReservaId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(this, "No se encontraron reservas del grupo", Toast.LENGTH_SHORT).show();
                        btnAceptar.setEnabled(true);
                        btnRechazar.setEnabled(true);
                        btnVerPerfil.setEnabled(true);
                        return;
                    }

                    // Usar transacción para actualizar todas las reservas atómicamente
                    db.runTransaction(transaction -> {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            transaction.update(doc.getReference(),
                                    "estado", ReservaEstadoValidator.ESTADO_ACEPTADO,
                                    "fecha_respuesta", com.google.firebase.firestore.FieldValue.serverTimestamp()
                            );
                        }
                        return null;
                    }).addOnSuccessListener(aVoid -> {
                        int cantidadReservas = querySnapshot.size();
                        Toast.makeText(this, "¡" + cantidadReservas + " reservas aceptadas!", Toast.LENGTH_SHORT).show();
                        estadoReserva = ReservaEstadoValidator.ESTADO_ACEPTADO;
                        actualizarBotonesPorEstado();
                        // NO cerramos la actividad para permitir agregar al calendario
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Error al aceptar grupo de reservas", e);
                        Toast.makeText(this, "Error al aceptar las reservas. Verifica tu conexión.", Toast.LENGTH_LONG).show();
                        btnAceptar.setEnabled(true);
                        btnRechazar.setEnabled(true);
                        btnVerPerfil.setEnabled(true);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al cargar grupo de reservas para aceptar", e);
                    Toast.makeText(this, "Error al cargar las reservas del grupo", Toast.LENGTH_SHORT).show();
                    btnAceptar.setEnabled(true);
                    btnRechazar.setEnabled(true);
                    btnVerPerfil.setEnabled(true);
                });
    }

    /**
     * Finaliza la actividad después de un delay de 1 segundo
     */
    private void finishWithDelay() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish, 1000);
    }

    /**
     * Calcula la edad en meses a partir de una fecha de nacimiento
     */
    private long calcularEdadEnMeses(Date fechaNacimiento) {
        if (fechaNacimiento == null) return 0;

        Calendar birthDate = Calendar.getInstance();
        birthDate.setTime(fechaNacimiento);

        Calendar today = Calendar.getInstance();

        int years = today.get(Calendar.YEAR) - birthDate.get(Calendar.YEAR);
        int months = today.get(Calendar.MONTH) - birthDate.get(Calendar.MONTH);

        return years * 12L + months;
    }
}
