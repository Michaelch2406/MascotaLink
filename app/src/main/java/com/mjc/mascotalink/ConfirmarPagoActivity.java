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

    // Views
    private ImageView ivBack;
    private TextView tvMontoTotal;
    private TextView tvPaseadorNombre;
    private TextView tvMascotaNombre;
    private TextView tvFecha;
    private TextView tvHora;
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
        if (costoTotal <= 0) {
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
        btnProcesarPago = findViewById(R.id.btn_procesar_pago);
        btnCancelar = findViewById(R.id.btn_cancelar);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());
        btnProcesarPago.setOnClickListener(v -> procesarPago());
        btnCancelar.setOnClickListener(v -> mostrarDialogCancelar());
    }

    private void cargarDatosReserva() {
        // Si no se pasaron los datos, cargarlos desde Firebase
        if (paseadorNombre == null || mascotaNombre == null) {
            db.collection("reservas").document(reservaId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            // Extraer datos del documento
                            costoTotal = documentSnapshot.getDouble("costo_total");
                            
                            // Formatear y mostrar datos
                            mostrarDatos();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show();
                    });
        } else {
            mostrarDatos();
        }
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

    // --- FIX INICIO: Interfaz para manejar el resultado de la validación asíncrona ---
    private interface ValidationCallback {
        void onValidationComplete(boolean isValid, String errorMessage);
    }
    // --- FIX FIN ---

    private void procesarPago() {
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
        // Simular procesamiento de 2 segundos
        new Handler().postDelayed(() -> {
            String pagoId = "PAGO_" + System.currentTimeMillis();

            Map<String, Object> updates = new HashMap<>();
            updates.put("estado", "CONFIRMADO");
            updates.put("id_pago", pagoId);
            updates.put("estado_pago", "PROCESADO");
            updates.put("fecha_pago", Timestamp.now());
            updates.put("metodo_pago", "PAGO_INTERNO");

            // RIESGO: Una falla en la actualización de la BD podría dejar al usuario en un
            // estado de "pago en proceso" infinito.
            // SOLUCIÓN: Se implementa on-failure-listener para reintentar o notificar.
            db.collection("reservas").document(reservaId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        progressBar.setVisibility(View.GONE);
                        btnProcesarPago.setText("Procesar Pago");
                        
                        Toast.makeText(this, "¡Pago procesado exitosamente!", 
                                Toast.LENGTH_LONG).show();

                        new Handler().postDelayed(() -> {
                            Intent intent = new Intent(ConfirmarPagoActivity.this, PaseosActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }, 1000);
                    })
                    .addOnFailureListener(e -> {
                        intentosFallidos++;
                        if (intentosFallidos >= MAX_INTENTOS) {
                            Toast.makeText(this, "Máximo de intentos alcanzado. Contacta soporte.", 
                                    Toast.LENGTH_LONG).show();
                            btnProcesarPago.setEnabled(false); // Deshabilitar permanentemente
                            btnProcesarPago.setAlpha(0.5f);
                        } else {
                            Toast.makeText(this, "Error al procesar pago. Intento " + intentosFallidos + "/" + MAX_INTENTOS, 
                                    Toast.LENGTH_SHORT).show();
                            restaurarUIAposFallo();
                        }
                    });
        }, 2000);
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
        new AlertDialog.Builder(this)
                .setTitle("Cancelar Reserva")
                .setMessage("¿Estás seguro de cancelar esta reserva?")
                .setPositiveButton("Sí, cancelar", (dialog, which) -> {
                    // Actualizar estado a cancelado
                    db.collection("reservas").document(reservaId)
                            .update("estado", "CANCELADO")
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Reserva cancelada", Toast.LENGTH_SHORT).show();
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
                        if (!"PENDIENTE_PAGO".equals(estado)) {
                            callback.onValidationComplete(false, "Esta reserva ya fue procesada o cancelada.");
                        } else {
                            // ¡Todo en orden!
                            callback.onValidationComplete(true, null);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onValidationComplete(false, "Error de red al validar la reserva.");
                });
    }
}

