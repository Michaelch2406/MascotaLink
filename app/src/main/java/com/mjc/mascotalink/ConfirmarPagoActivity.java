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

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.security.EncryptedPreferencesHelper;
import com.mjc.mascotalink.security.SessionManager;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ConfirmarPagoActivity extends AppCompatActivity {

    private static final String TAG = "ConfirmarPagoActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EncryptedPreferencesHelper encryptedPrefs;
    private SessionManager sessionManager;

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
        btnProcesarPago.setOnClickListener(v -> procesarPago());
        btnCancelar.setOnClickListener(v -> mostrarDialogCancelar());
    }

    private void cargarDatosReserva() {
        db.collection("reservas").document(reservaId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "La reserva ya no está disponible.", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

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

                    mostrarDatos();
                    actualizarEstadoUI();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al cargar la reserva. Intenta nuevamente.", Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void mostrarDatos() {
        tvMontoTotal.setText(String.format(Locale.US, "IMPORTE A PAGAR: $%.1f", costoTotal));
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

        // 1. Create Payment Document in "pagos" collection
        Map<String, Object> pagoData = new HashMap<>();
        pagoData.put("id_usuario", currentUid); // Required by validatePaymentOnCreate
        pagoData.put("id_dueno", idDueno != null ? idDueno : currentUid); // For notification
        pagoData.put("id_paseador", idPaseador); // For notification
        pagoData.put("monto", costoTotal); // Required by validatePaymentOnCreate
        pagoData.put("estado", "procesando"); // Initial status
        pagoData.put("reserva_id", reservaId);
        pagoData.put("fecha_creacion", Timestamp.now());

        db.collection("pagos").add(pagoData)
                .addOnSuccessListener(documentReference -> {
                    String pagoId = documentReference.getId();

                    // 2. Update Payment Document to "confirmado" (Triggers Notification)
                    documentReference.update("estado", "confirmado")
                            .addOnSuccessListener(aVoid -> {
                                // 3. Update Reservation Document
                                completarReserva(pagoId);
                            })
                            .addOnFailureListener(e -> {
                                manejarFalloPago("Error al confirmar estado del pago: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    manejarFalloPago("Error al crear registro de pago: " + e.getMessage());
                });
    }

    private void completarReserva(String pagoId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("estado", ReservaEstadoValidator.ESTADO_CONFIRMADO);
        updates.put("id_pago", pagoId);
        updates.put("transaction_id", pagoId);
        updates.put("estado_pago", ReservaEstadoValidator.ESTADO_PAGO_CONFIRMADO);
        updates.put("fecha_pago", Timestamp.now());
        String metodoPagoGuardado = encryptedPrefs != null ? encryptedPrefs.getString("selected_payment_method", "PAGO_INTERNO") : "PAGO_INTERNO";
        updates.put("metodo_pago", metodoPagoGuardado);
        updates.put("hasTransitionedToInCourse", false);

        db.collection("reservas").document(reservaId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcesarPago.setText("Procesar Pago");
                    if (encryptedPrefs != null) {
                        encryptedPrefs.putString("payment_status", "COMPLETED");
                        encryptedPrefs.putString("payment_id", pagoId);
                        encryptedPrefs.putString("payment_token", pagoId);
                    }
                    if (mAuth.getCurrentUser() != null) {
                        sessionManager.createSession(mAuth.getCurrentUser().getUid());
                    }
                    Toast.makeText(this, "Pago procesado exitosamente!",
                            Toast.LENGTH_LONG).show();
                    estadoReserva = ReservaEstadoValidator.ESTADO_CONFIRMADO;
                    estadoPago = ReservaEstadoValidator.ESTADO_PAGO_CONFIRMADO;
                    actualizarEstadoUI();

                    new Handler().postDelayed(() -> {
                        Intent intent = new Intent(ConfirmarPagoActivity.this, PaseosActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }, 1000);
                })
                .addOnFailureListener(e -> {
                    manejarFalloPago("Error al actualizar reserva: " + e.getMessage());
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
}

