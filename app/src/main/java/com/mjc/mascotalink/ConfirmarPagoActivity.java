package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.mjc.mascotalink.security.EncryptedPreferencesHelper;
import com.mjc.mascotalink.security.SessionManager;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import android.util.Log;

public class ConfirmarPagoActivity extends AppCompatActivity {

    private static final String TAG = "ConfirmarPagoActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EncryptedPreferencesHelper encryptedPrefs;
    private SessionManager sessionManager;
    private com.google.firebase.firestore.ListenerRegistration reservaListener;

    // Views
    private ImageView ivBack;
    private TextView tvMontoTotal;
    private TextView tvPaseadorNombre;
    private TextView tvMascotaNombre;
    private TextView tvFecha;
    private TextView tvHora;
    private TextView tvEstadoReserva;
    private TextView tvEstadoMensaje;
    private Button btnProcesarPago;
    private Button btnCancelar;
    private ProgressBar progressBar;

    // Variables
    private String reservaId;
    private double costoTotal;
    private String paseadorNombre;
    private String mascotaNombre;
    private String fechaReserva;
    private String horaReserva;
    private String direccionRecogida;
    private String estadoReserva;
    private String estadoPago;
    private String idPaseador;
    private String idDueno;
    private int intentosFallidos = 0;
    private static final int MAX_INTENTOS = 3;

    // Variables para Google Calendar
    private Timestamp horaInicioTimestamp;
    private Timestamp horaFinTimestamp;
    private int duracionMinutos = 60;
    private boolean esGrupoReserva = false;
    private String grupoReservaIdActual;
    private int cantidadDiasGrupo = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirmar_pago);

        // --- FIX INICIO: Validaciones críticas de seguridad y datos ---
        // RIESGO: Un usuario no autenticado no debería poder confirmar un pago.
        // SOLUCIÓN: Se verifica la sesión del usuario. Si es nula, se cierra la actividad.
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        encryptedPrefs = EncryptedPreferencesHelper.getInstance(this);
        sessionManager = new SessionManager(this);

        if (!sessionManager.isSessionValid()) {
            Toast.makeText(this, "Sesión expirada. Por favor inicia sesión de nuevo", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        sessionManager.validateAndRefreshToken();

        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Error: Sesión de usuario no válida.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // RIESGO: Si 'reserva_id' es nulo o 'costo_total' es inválido, el pago no puede
        // procesarse correctamente, llevando a datos corruptos en Firebase.
        // SOLUCIÓN: Se valida la presencia y validez de los extras del Intent.
        Intent intent = getIntent();
        reservaId = intent.getStringExtra("reserva_id");
        costoTotal = intent.getDoubleExtra("costo_total", 0.0);

        if (reservaId == null || reservaId.isEmpty()) {
            Toast.makeText(this, "Error: ID de reserva no válido.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // Si costoTotal es 0 (no vino en el intent), confiamos en que cargarDatosReserva lo obtendrá de Firestore.
        // Solo bloqueamos si es un valor negativo explícito (error de lógica).
        if (costoTotal < 0) {
            Toast.makeText(this, "Error: El costo total de la reserva es inválido.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // --- FIX FIN ---

        paseadorNombre = intent.getStringExtra("paseador_nombre");
        mascotaNombre = intent.getStringExtra("mascota_nombre");
        fechaReserva = intent.getStringExtra("fecha_reserva");
        horaReserva = intent.getStringExtra("hora_reserva");
        direccionRecogida = intent.getStringExtra("direccion_recogida");

        initViews();
        setupListeners();
        cargarDatosReserva();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        tvMontoTotal = findViewById(R.id.tv_monto_total);
        tvPaseadorNombre = findViewById(R.id.tv_paseador_nombre);
        tvMascotaNombre = findViewById(R.id.tv_mascota_nombre);
        tvFecha = findViewById(R.id.tv_fecha);
        tvHora = findViewById(R.id.tv_hora);
        tvEstadoReserva = findViewById(R.id.tv_estado_reserva);
        tvEstadoMensaje = findViewById(R.id.tv_estado_mensaje);
        btnProcesarPago = findViewById(R.id.btn_procesar_pago);
        btnCancelar = findViewById(R.id.btn_cancelar);
        progressBar = findViewById(R.id.progress_bar);
        btnProcesarPago.setEnabled(false);
        btnProcesarPago.setAlpha(0.6f);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());
        btnProcesarPago.setOnClickListener(v -> {
            v.setEnabled(false);
            procesarPago();
        });
        btnCancelar.setOnClickListener(v -> mostrarDialogCancelar());
    }

    private void cargarDatosReserva() {
        // Usar listener en tiempo real para detectar cambios de estado
        reservaListener = db.collection("reservas").document(reservaId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error en listener de reserva", error);
                        Toast.makeText(this, "Error al cargar la reserva. Intenta nuevamente.", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    if (documentSnapshot == null || !documentSnapshot.exists()) {
                        Toast.makeText(this, "La reserva ya no está disponible.", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    // Detectar si es parte de un grupo
                    Boolean esGrupo = documentSnapshot.getBoolean("es_grupo");
                    String grupoReservaId = documentSnapshot.getString("grupo_reserva_id");

                    if (esGrupo != null && esGrupo && grupoReservaId != null) {
                        // Es un grupo, cargar todas las reservas del grupo
                        cargarGrupoReservas(documentSnapshot, grupoReservaId);
                    } else {
                        // Es una reserva individual, cargar normalmente
                        cargarReservaIndividual(documentSnapshot);
                    }

                    // Log para debugging
                    String nuevoEstado = documentSnapshot.getString("estado");
                    if (nuevoEstado != null && !nuevoEstado.equals(estadoReserva)) {
                        Log.d(TAG, "Estado de reserva cambió: " + estadoReserva + " → " + nuevoEstado);
                    }
                });
    }

    private void cargarReservaIndividual(DocumentSnapshot documentSnapshot) {
        Double totalDoc = documentSnapshot.getDouble("costo_total");
        if (totalDoc != null) {
            costoTotal = totalDoc;
        }

        Timestamp fechaTimestamp = documentSnapshot.getTimestamp("fecha");
        if (fechaTimestamp != null) {
            fechaReserva = new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).format(fechaTimestamp.toDate());
        }

        Timestamp horaTimestamp = documentSnapshot.getTimestamp("hora_inicio");
        if (horaTimestamp != null) {
            horaReserva = new SimpleDateFormat("h:mm a", Locale.US).format(horaTimestamp.toDate());
            horaInicioTimestamp = horaTimestamp;
        }

        Timestamp horaFinTs = documentSnapshot.getTimestamp("hora_fin");
        if (horaFinTs != null) {
            horaFinTimestamp = horaFinTs;
        }

        Long duracion = documentSnapshot.getLong("duracion");
        if (duracion != null) {
            duracionMinutos = duracion.intValue();
        }

        estadoReserva = documentSnapshot.getString("estado");
        estadoPago = documentSnapshot.getString("estado_pago");

        // Extract IDs handling both DocumentReference and String
        Object paseadorObj = documentSnapshot.get("id_paseador");
        if (paseadorObj instanceof com.google.firebase.firestore.DocumentReference) {
            idPaseador = ((com.google.firebase.firestore.DocumentReference) paseadorObj).getId();
        } else if (paseadorObj instanceof String) {
            idPaseador = (String) paseadorObj;
        }

        Object duenoObj = documentSnapshot.get("id_dueno");
        if (duenoObj instanceof com.google.firebase.firestore.DocumentReference) {
            idDueno = ((com.google.firebase.firestore.DocumentReference) duenoObj).getId();
        } else if (duenoObj instanceof String) {
            idDueno = (String) duenoObj;
        }
        
        // Obtener dirección de recogida desde Firestore si no vino en el intent
        if (direccionRecogida == null || direccionRecogida.isEmpty()) {
            direccionRecogida = documentSnapshot.getString("direccion_recogida");
        }

        // Cargar nombres del paseador y mascota
        cargarNombrePaseadorYMascota(documentSnapshot);
    }

    private void cargarGrupoReservas(DocumentSnapshot primerReserva, String grupoReservaId) {
        Log.d(TAG, "Cargando grupo de reservas con ID: " + grupoReservaId);

        // Cargar todas las reservas del grupo
        db.collection("reservas")
                .whereEqualTo("grupo_reserva_id", grupoReservaId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        // Fallback: solo una reserva
                        cargarReservaIndividual(primerReserva);
                        return;
                    }

                    // Obtener dirección de recogida
                    if (direccionRecogida == null || direccionRecogida.isEmpty()) {
                        direccionRecogida = primerReserva.getString("direccion_recogida");
                    }

                    int cantidadReservas = querySnapshot.size();
                    double costoTotalGrupo = 0.0;
                    java.util.Date fechaMasTemprana = null;
                    java.util.Date fechaMasTardia = null;

                    // Sumar costos y encontrar rango de fechas
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Double costo = doc.getDouble("costo_total");
                        if (costo != null) {
                            costoTotalGrupo += costo;
                        }

                        Timestamp fechaTs = doc.getTimestamp("fecha");
                        if (fechaTs != null) {
                            java.util.Date fecha = fechaTs.toDate();
                            if (fechaMasTemprana == null || fecha.before(fechaMasTemprana)) {
                                fechaMasTemprana = fecha;
                            }
                            if (fechaMasTardia == null || fecha.after(fechaMasTardia)) {
                                fechaMasTardia = fecha;
                            }
                        }
                    }

                    // Establecer datos del grupo
                    costoTotal = costoTotalGrupo;

                    // Formato de fechas para el grupo
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.ENGLISH);
                    if (fechaMasTemprana != null && fechaMasTardia != null) {
                        fechaReserva = sdf.format(fechaMasTemprana) + " - " + sdf.format(fechaMasTardia) +
                                      " (" + cantidadReservas + " días)";
                    } else {
                        fechaReserva = cantidadReservas + " días";
                    }

                    // Usar hora de la primera reserva
                    Timestamp horaTimestamp = primerReserva.getTimestamp("hora_inicio");
                    if (horaTimestamp != null) {
                        horaReserva = new SimpleDateFormat("h:mm a", Locale.US).format(horaTimestamp.toDate());
                        horaInicioTimestamp = horaTimestamp;
                    }

                    Timestamp horaFinTs = primerReserva.getTimestamp("hora_fin");
                    if (horaFinTs != null) {
                        horaFinTimestamp = horaFinTs;
                    }

                    Long duracion = primerReserva.getLong("duracion");
                    if (duracion != null) {
                        duracionMinutos = duracion.intValue();
                    }

                    // Guardar datos del grupo para el calendario
                    esGrupoReserva = true;
                    grupoReservaIdActual = grupoReservaId;
                    cantidadDiasGrupo = cantidadReservas;

                    // Usar estado de la primera reserva (todas deberían tener el mismo estado)
                    estadoReserva = primerReserva.getString("estado");
                    estadoPago = primerReserva.getString("estado_pago");

                    // Extract IDs handling both DocumentReference and String
                    Object paseadorObj = primerReserva.get("id_paseador");
                    if (paseadorObj instanceof com.google.firebase.firestore.DocumentReference) {
                        idPaseador = ((com.google.firebase.firestore.DocumentReference) paseadorObj).getId();
                    } else if (paseadorObj instanceof String) {
                        idPaseador = (String) paseadorObj;
                    }

                    Object duenoObj = primerReserva.get("id_dueno");
                    if (duenoObj instanceof com.google.firebase.firestore.DocumentReference) {
                        idDueno = ((com.google.firebase.firestore.DocumentReference) duenoObj).getId();
                    } else if (duenoObj instanceof String) {
                        idDueno = (String) duenoObj;
                    }

                    Log.d(TAG, "Grupo cargado: " + cantidadReservas + " reservas, costo total: $" + costoTotal);

                    // Cargar nombres del paseador y mascota
                    cargarNombrePaseadorYMascota(primerReserva);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando grupo de reservas, usando datos individuales", e);
                    // Fallback: cargar como individual
                    cargarReservaIndividual(primerReserva);
                });
    }

    private void cargarNombrePaseadorYMascota(DocumentSnapshot reservaSnapshot) {
        // Intentar usar campo desnormalizado primero
        String paseadorNombreDesnormalizado = reservaSnapshot.getString("paseador_nombre");

        if (paseadorNombreDesnormalizado != null && !paseadorNombreDesnormalizado.isEmpty()) {
            // Usar dato desnormalizado
            paseadorNombre = paseadorNombreDesnormalizado;
            Log.d(TAG, "Usando paseador_nombre desnormalizado: " + paseadorNombre);
            mostrarDatos();
            actualizarEstadoUI();
        } else if (idPaseador != null) {
            // Fallback: consultar Firebase (para reservas antiguas)
            Log.d(TAG, "Campo desnormalizado no disponible, consultando Firebase");
            db.collection("usuarios").document(idPaseador)
                    .get()
                    .addOnSuccessListener(paseadorDoc -> {
                        if (paseadorDoc.exists()) {
                            paseadorNombre = paseadorDoc.getString("nombre_display");
                            if (paseadorNombre == null) {
                                paseadorNombre = paseadorDoc.getString("nombre");
                            }
                        }
                        // Actualizar UI después de cargar nombre del paseador
                        mostrarDatos();
                        actualizarEstadoUI();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error cargando nombre del paseador", e);
                        paseadorNombre = "Paseador";
                        mostrarDatos();
                        actualizarEstadoUI();
                    });
        } else {
            paseadorNombre = "No asignado";
            mostrarDatos();
            actualizarEstadoUI();
        }

        // Cargar nombre de la mascota - soportar ambos formatos
        @SuppressWarnings("unchecked")
        List<String> mascotasNombres = (List<String>) reservaSnapshot.get("mascotas_nombres");
        String idMascota = reservaSnapshot.getString("id_mascota");

        if (mascotasNombres != null && !mascotasNombres.isEmpty()) {
            // Formato nuevo: múltiples mascotas con nombres precargados
            mascotaNombre = String.join(", ", mascotasNombres);
            mostrarDatos();
            actualizarEstadoUI();
        } else if (idMascota != null && idDueno != null) {
            // Formato antiguo: una sola mascota, cargar nombre desde Firestore
            // Intentar primero en la colección del dueño
            db.collection("duenos").document(idDueno)
                    .collection("mascotas").document(idMascota)
                    .get()
                    .addOnSuccessListener(mascotaDoc -> {
                        if (mascotaDoc.exists()) {
                            mascotaNombre = mascotaDoc.getString("nombre");
                            mostrarDatos();
                        } else {
                            // Si no existe, intentar en colección global
                            db.collection("mascotas").document(idMascota)
                                    .get()
                                    .addOnSuccessListener(globalMascotaDoc -> {
                                        if (globalMascotaDoc.exists()) {
                                            mascotaNombre = globalMascotaDoc.getString("nombre");
                                        }
                                        mostrarDatos();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error cargando mascota de colección global", e);
                                        mascotaNombre = "Mascota";
                                        mostrarDatos();
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error cargando nombre de la mascota", e);
                        mascotaNombre = "Mascota";
                        mostrarDatos();
                    });
        } else {
            // Si no hay datos de mascota, solo mostrar con datos actuales
            mostrarDatos();
            actualizarEstadoUI();
        }
    }

    private void mostrarDatos() {
        tvMontoTotal.setText(String.format(Locale.US, "$%.1f", costoTotal));
        tvPaseadorNombre.setText(paseadorNombre != null ? paseadorNombre : "Sophia Carter");
        tvMascotaNombre.setText(mascotaNombre != null ? mascotaNombre : "Buddy");
        
        // Formatear fecha si viene de la reserva
        if (fechaReserva != null) {
            tvFecha.setText(fechaReserva);
        } else {
            tvFecha.setText("July 20, 2024");
        }
        
        // Mostrar hora
        if (horaReserva != null) {
            tvHora.setText(horaReserva);
        } else {
            tvHora.setText("10:00 AM");
        }
    }

    private void actualizarEstadoUI() {
        if (tvEstadoReserva == null || tvEstadoMensaje == null) {
            return;
        }

        String estadoEtiqueta = formatearEstado(estadoReserva);
        tvEstadoReserva.setText("Estado: " + estadoEtiqueta);

        if (ReservaEstadoValidator.canPay(estadoReserva) && !ReservaEstadoValidator.isPagoCompletado(estadoPago)) {
            habilitarBotonPago(true);
            btnProcesarPago.setText("Procesar Pago");
            tvEstadoMensaje.setText("Reserva aceptada. Ya puedes completar el pago.");
            return;
        }

        habilitarBotonPago(false);

        if (ReservaEstadoValidator.ESTADO_PENDIENTE_ACEPTACION.equals(estadoReserva)) {
            tvEstadoMensaje.setText("Esperando a que el paseador acepte la solicitud.");
            btnProcesarPago.setText("Pago bloqueado");
        } else if (ReservaEstadoValidator.ESTADO_CONFIRMADO.equals(estadoReserva)) {
            tvEstadoMensaje.setText("Pago confirmado. Todo listo para el paseo.");
        } else if (ReservaEstadoValidator.ESTADO_RECHAZADO.equals(estadoReserva)) {
            tvEstadoMensaje.setText("La reserva fue rechazada. No se puede pagar.");
        } else if (ReservaEstadoValidator.ESTADO_CANCELADO.equals(estadoReserva)) {
            tvEstadoMensaje.setText("La reserva fue cancelada. Contacta soporte si es necesario.");
        } else if (ReservaEstadoValidator.isPagoCompletado(estadoPago)) {
            tvEstadoMensaje.setText("El pago ya fue procesado.");
        } else {
            tvEstadoMensaje.setText("Estado actual: " + (estadoReserva != null ? estadoReserva : "desconocido"));
        }
    }

    private void habilitarBotonPago(boolean habilitado) {
        btnProcesarPago.setEnabled(habilitado);
        btnProcesarPago.setAlpha(habilitado ? 1f : 0.6f);
    }

    private String formatearEstado(String estado) {
        if (estado == null) {
            return "No disponible";
        }
        switch (estado) {
            case ReservaEstadoValidator.ESTADO_PENDIENTE_ACEPTACION:
                return "Pendiente de aceptación";
            case ReservaEstadoValidator.ESTADO_ACEPTADO:
                return "Aceptado";
            case ReservaEstadoValidator.ESTADO_CONFIRMADO:
                return "Confirmado";
            case ReservaEstadoValidator.ESTADO_RECHAZADO:
                return "Rechazado";
            case ReservaEstadoValidator.ESTADO_CANCELADO:
                return "Cancelado";
            default:
                return estado;
        }
    }

    // --- FIX INICIO: Interfaz para manejar el resultado de la validación asíncrona ---
    private interface ValidationCallback {
        void onValidationComplete(boolean isValid, String errorMessage);
    }
    // --- FIX FIN ---

    private void procesarPago() {
        if (!ReservaEstadoValidator.canPay(estadoReserva)) {
            Toast.makeText(this, "La reserva aún no fue aceptada por el paseador.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ReservaEstadoValidator.isPagoCompletado(estadoPago)) {
            Toast.makeText(this, "El pago ya fue procesado.", Toast.LENGTH_SHORT).show();
            return;
        }
        String metodoPagoSeleccionado = encryptedPrefs != null ? encryptedPrefs.getString("selected_payment_method", "") : "";
        if (metodoPagoSeleccionado == null || metodoPagoSeleccionado.isEmpty()) {
            metodoPagoSeleccionado = "PAGO_INTERNO";
        }

        // Deshabilitar botón y mostrar loading para evitar clics múltiples
        btnProcesarPago.setEnabled(false);
        btnProcesarPago.setAlpha(0.6f);
        btnCancelar.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        btnProcesarPago.setText("");

        // --- FIX INICIO: Uso del patrón callback para la validación asíncrona ---
        // RIESGO: La validación de Firebase es asíncrona. Llamar a un método que devuelve
        // boolean de forma síncrona (como antes) no espera el resultado de la red,
        // creando una condición de carrera y permitiendo procesar pagos de reservas inválidas.
        // SOLUCIÓN: Se pasa un callback a validarReserva(). La lógica de pago se mueve
        // DENTRO del callback, asegurando que solo se ejecute DESPUÉS de que la
        // validación de Firebase haya terminado y sea exitosa.
        validarReserva(new ValidationCallback() {
            @Override
            public void onValidationComplete(boolean isValid, String errorMessage) {
                if (isValid) {
                    // La validación fue exitosa, proceder con el pago
                    ejecutarLogicaDePago();
                } else {
                    // La validación falló, mostrar error y restaurar la UI
                    Toast.makeText(ConfirmarPagoActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    restaurarUIAposFallo();
                }
            }
        });
        // --- FIX FIN ---
    }

    private void ejecutarLogicaDePago() {
        if (mAuth.getCurrentUser() == null) return;
        String currentUid = mAuth.getCurrentUser().getUid();

        final DocumentReference reservaRef = db.collection("reservas").document(reservaId);
        final DocumentReference nuevoPagoRef = db.collection("pagos").document();
        final String nuevoPagoId = nuevoPagoRef.getId();

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(reservaRef);

            if (!snapshot.exists()) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException(
                    "Reserva no encontrada",
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND);
            }

            String estadoActual = snapshot.getString("estado");
            String estadoPagoActual = snapshot.getString("estado_pago");

            if (!ReservaEstadoValidator.ESTADO_ACEPTADO.equals(estadoActual)) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException(
                    "Estado inválido para pagar: " + estadoActual,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }

            if (ReservaEstadoValidator.isPagoCompletado(estadoPagoActual)) {
                 throw new com.google.firebase.firestore.FirebaseFirestoreException(
                    "Pago ya realizado",
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.ALREADY_EXISTS);
            }

            // Detectar si es un grupo de reservas
            Boolean esGrupo = snapshot.getBoolean("es_grupo");
            String grupoReservaId = snapshot.getString("grupo_reserva_id");

            // 1. Crear documento de pago
            Map<String, Object> pagoData = new HashMap<>();
            pagoData.put("id_usuario", currentUid);
            pagoData.put("id_dueno", idDueno != null ? idDueno : currentUid);
            pagoData.put("id_paseador", idPaseador);
            pagoData.put("monto", costoTotal);
            pagoData.put("estado", "confirmado");
            pagoData.put("reserva_id", reservaId);
            pagoData.put("fecha_creacion", com.google.firebase.firestore.FieldValue.serverTimestamp());

            // Si es grupo, agregar el ID del grupo al pago
            if (esGrupo != null && esGrupo && grupoReservaId != null) {
                pagoData.put("grupo_reserva_id", grupoReservaId);
                pagoData.put("es_pago_grupal", true);
            }

            transaction.set(nuevoPagoRef, pagoData);

            // 2. Preparar actualizaciones
            Map<String, Object> updates = new HashMap<>();
            updates.put("estado", ReservaEstadoValidator.ESTADO_CONFIRMADO);
            updates.put("id_pago", nuevoPagoId);
            updates.put("transaction_id", nuevoPagoId);
            updates.put("estado_pago", ReservaEstadoValidator.ESTADO_PAGO_CONFIRMADO);
            updates.put("fecha_pago", com.google.firebase.firestore.FieldValue.serverTimestamp());
            updates.put("hasTransitionedToInCourse", false);

            String metodoPago = encryptedPrefs != null ?
                encryptedPrefs.getString("selected_payment_method", "PAGO_INTERNO") : "PAGO_INTERNO";
            Log.d(TAG, "Procesando pago con método: " + metodoPago);
            updates.put("metodo_pago", metodoPago);

            // 3. Si es grupo, actualizar TODAS las reservas del grupo
            if (esGrupo != null && esGrupo && grupoReservaId != null) {
                Log.d(TAG, "Actualizando grupo de reservas con ID: " + grupoReservaId);

                try {
                    // Cargar todas las reservas del grupo
                    QuerySnapshot grupoSnapshot = Tasks.await(db.collection("reservas")
                            .whereEqualTo("grupo_reserva_id", grupoReservaId)
                            .get());

                    Log.d(TAG, "Encontradas " + grupoSnapshot.size() + " reservas en el grupo");

                    // Actualizar cada reserva del grupo
                    for (QueryDocumentSnapshot reservaDoc : grupoSnapshot) {
                        DocumentReference reservaGrupoRef = reservaDoc.getReference();
                        transaction.update(reservaGrupoRef, updates);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "Error al cargar grupo de reservas: " + e.getMessage(), e);
                    throw new RuntimeException("No se pudo actualizar el grupo de reservas", e);
                } catch (ExecutionException e) {
                    Log.e(TAG, "Error al cargar grupo de reservas: " + e.getMessage(), e);
                    throw new RuntimeException("No se pudo actualizar el grupo de reservas", e);
                }
            } else {
                // Solo una reserva, actualizar normalmente
                transaction.update(reservaRef, updates);
            }

            return nuevoPagoId;
        }).addOnSuccessListener(pagoId -> {
            progressBar.setVisibility(View.GONE);
            btnProcesarPago.setText("Procesar Pago");
            
            if (encryptedPrefs != null) {
                encryptedPrefs.putString("payment_status", "COMPLETED");
                encryptedPrefs.putString("payment_id", pagoId);
            }
            
            Toast.makeText(this, "Pago procesado exitosamente!", Toast.LENGTH_LONG).show();

            mostrarDialogoAgregarCalendario();
            
        }).addOnFailureListener(e -> {
            manejarFalloPago("Error en transacción de pago: " + e.getMessage());
        });
    }



    private void manejarFalloPago(String errorMsg) {
        if (encryptedPrefs != null) {
            encryptedPrefs.putString("payment_status", "FAILED");
            encryptedPrefs.putString("payment_error", errorMsg);
        }
        intentosFallidos++;
        if (intentosFallidos >= MAX_INTENTOS) {
            Toast.makeText(this, "Máximo de intentos alcanzado. Contacta soporte.",
                    Toast.LENGTH_LONG).show();
            btnProcesarPago.setEnabled(false);
            btnProcesarPago.setAlpha(0.5f);
        } else {
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            restaurarUIAposFallo();
        }
    }

    private void restaurarUIAposFallo() {
        progressBar.setVisibility(View.GONE);
        btnProcesarPago.setText("Procesar Pago");
        btnProcesarPago.setEnabled(true);
        btnProcesarPago.setAlpha(1.0f);
        btnCancelar.setEnabled(true);
    }

    private void mostrarDialogCancelar() {
        // --- FIX: Añadir check de isFinishing() ---
        // RIESGO: Mostrar un diálogo después de que la actividad se está cerrando
        // puede causar un crash (WindowManager$BadTokenException).
        // SOLUCIÓN: Se verifica si la actividad está en proceso de cierre antes de mostrar el diálogo.
        if (isFinishing()) {
            return;
        }
        if (!ReservaEstadoValidator.canTransition(estadoReserva, ReservaEstadoValidator.ESTADO_CANCELADO)) {
            Toast.makeText(this, "No se puede cancelar esta reserva en su estado actual.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Cancelar Reserva")
                .setMessage("¿Estás seguro de cancelar esta reserva?")
                .setPositiveButton("Sí, cancelar", (dialog, which) -> {
                    // Actualizar estado a cancelado
                    db.collection("reservas").document(reservaId)
                            .update("estado", ReservaEstadoValidator.ESTADO_CANCELADO)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Reserva cancelada", Toast.LENGTH_SHORT).show();
                                estadoReserva = ReservaEstadoValidator.ESTADO_CANCELADO;
                                actualizarEstadoUI();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error al cancelar: " + e.getMessage(), 
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void validarReserva(ValidationCallback callback) {
        // Validaciones síncronas primero
        if (reservaId == null || reservaId.isEmpty()) {
            callback.onValidationComplete(false, "Error: ID de reserva no válido");
            return;
        }
        
        if (costoTotal <= 0) {
            callback.onValidationComplete(false, "Error: Costo total inválido");
            return;
        }
        
        // Validación asíncrona en Firebase
        db.collection("reservas").document(reservaId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot == null || !documentSnapshot.exists()) {
                        callback.onValidationComplete(false, "Error: La reserva ya no existe.");
                    } else {
                    String estado = documentSnapshot.getString("estado");
                    String estadoPagoDoc = documentSnapshot.getString("estado_pago");
                    if (!ReservaEstadoValidator.canPay(estado)) {
                        callback.onValidationComplete(false, "Esta reserva no está lista para pagar.");
                        return;
                    }
                    if (ReservaEstadoValidator.isPagoCompletado(estadoPagoDoc)) {
                        callback.onValidationComplete(false, "El pago ya fue registrado.");
                        return;
                    }
                    callback.onValidationComplete(true, null);
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onValidationComplete(false, "Error de red al validar la reserva.");
                });
    }

    private void mostrarDialogoAgregarCalendario() {
        if (!com.mjc.mascotalink.utils.GoogleCalendarHelper.isCalendarAvailable(this)) {
            navegarAPaseos();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Agregar al Calendario")
                .setMessage("¿Deseas agregar este paseo a tu calendario para recibir recordatorios?")
                .setPositiveButton("Sí, agregar", (dialog, which) -> {
                    agregarAlCalendario();
                    // Aumentar delay a 2 segundos para dar tiempo a que se abra el calendario
                    new Handler().postDelayed(this::navegarAPaseos, 2000);
                })
                .setNegativeButton("No, gracias", (dialog, which) -> navegarAPaseos())
                .setCancelable(false)
                .show();
    }

    private void agregarAlCalendario() {
        if (horaInicioTimestamp == null) {
            Toast.makeText(this, "No se puede agregar: fecha no disponible", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "No se puede agregar al calendario: hora de inicio no disponible");
            return;
        }

        Toast.makeText(this, "Abriendo calendario...", Toast.LENGTH_SHORT).show();

        try {
            if (esGrupoReserva && grupoReservaIdActual != null && cantidadDiasGrupo > 1) {
                com.mjc.mascotalink.utils.GoogleCalendarHelper.addRecurringWalksToCalendar(
                        this,
                        mascotaNombre != null ? mascotaNombre : "Mi mascota",
                        paseadorNombre != null ? paseadorNombre : "Paseador",
                        direccionRecogida != null ? direccionRecogida : "Ubicación del paseo",
                        horaInicioTimestamp,
                        duracionMinutos,
                        cantidadDiasGrupo,
                        grupoReservaIdActual
                );
            } else {
                com.mjc.mascotalink.utils.GoogleCalendarHelper.addWalkToCalendar(
                        this,
                        mascotaNombre != null ? mascotaNombre : "Mi mascota",
                        paseadorNombre != null ? paseadorNombre : "Paseador",
                        direccionRecogida != null ? direccionRecogida : "Ubicación del paseo",
                        horaInicioTimestamp,
                        horaFinTimestamp,
                        reservaId,
                        duracionMinutos
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Error crítico al abrir calendario", e);
            Toast.makeText(this, "Error al abrir la app de calendario", Toast.LENGTH_SHORT).show();
        }
    }

    private void navegarAPaseos() {
        Intent intent = new Intent(ConfirmarPagoActivity.this, PaseosActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reservaListener != null) {
            reservaListener.remove();
            reservaListener = null;
        }
    }
}

