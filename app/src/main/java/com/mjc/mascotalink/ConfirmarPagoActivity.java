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

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Obtener datos del intent
        Intent intent = getIntent();
        reservaId = intent.getStringExtra("reserva_id");
        costoTotal = intent.getDoubleExtra("costo_total", 0.0);
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

    private void procesarPago() {
        // Validar la reserva antes de procesar
        if (!validarReserva()) {
            return;
        }

        // Deshabilitar botón y mostrar loading
        btnProcesarPago.setEnabled(false);
        btnProcesarPago.setAlpha(0.6f);
        btnCancelar.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        btnProcesarPago.setText("");

        // Simular procesamiento de 2 segundos
        new Handler().postDelayed(() -> {
            // Generar ID de pago único
            String pagoId = "PAGO_" + System.currentTimeMillis();

            // Actualizar documento en Firebase
            Map<String, Object> updates = new HashMap<>();
            updates.put("estado", "CONFIRMADO");
            updates.put("id_pago", pagoId);
            updates.put("estado_pago", "PROCESADO");
            updates.put("fecha_pago", Timestamp.now());
            updates.put("metodo_pago", "PAGO_INTERNO");

            db.collection("reservas").document(reservaId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        progressBar.setVisibility(View.GONE);
                        btnProcesarPago.setText("Procesar Pago");
                        
                        Toast.makeText(this, "¡Pago procesado exitosamente!", 
                                Toast.LENGTH_LONG).show();

                        // CRÍTICO: Navegar a PaseosActivity después de pago exitoso
                        new Handler().postDelayed(() -> {
                            Intent intent = new Intent(ConfirmarPagoActivity.this, PaseosActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }, 1000);
                    })
                    .addOnFailureListener(e -> {
                        intentosFallidos++;
                        progressBar.setVisibility(View.GONE);
                        btnProcesarPago.setText("Procesar Pago");
                        
                        if (intentosFallidos >= MAX_INTENTOS) {
                            // Después de 3 intentos fallidos, deshabilitar completamente
                            btnProcesarPago.setEnabled(false);
                            btnProcesarPago.setAlpha(0.5f);
                            Toast.makeText(this, "Máximo de intentos alcanzado. Contacta soporte.", 
                                    Toast.LENGTH_LONG).show();
                        } else {
                            // Permitir reintentar
                            btnProcesarPago.setEnabled(true);
                            btnProcesarPago.setAlpha(1.0f);
                            btnCancelar.setEnabled(true);
                            Toast.makeText(this, "Error al procesar pago. Intento " + intentosFallidos + "/" + MAX_INTENTOS, 
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }, 2000);
    }

    private void mostrarDialogCancelar() {
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

    private boolean validarReserva() {
        if (reservaId == null || reservaId.isEmpty()) {
            Toast.makeText(this, "Error: ID de reserva no válido", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (costoTotal <= 0) {
            Toast.makeText(this, "Error: Costo total inválido", Toast.LENGTH_SHORT).show();
            return false;
        }
        
        // Validar que la reserva existe en Firebase antes de procesar
        db.collection("reservas").document(reservaId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Error: Reserva no encontrada", Toast.LENGTH_SHORT).show();
                        btnProcesarPago.setEnabled(false);
                    } else {
                        String estado = documentSnapshot.getString("estado");
                        if (!"PENDIENTE_PAGO".equals(estado)) {
                            Toast.makeText(this, "Esta reserva ya fue procesada", Toast.LENGTH_SHORT).show();
                            btnProcesarPago.setEnabled(false);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al validar reserva", Toast.LENGTH_SHORT).show();
                });
        
        return true;
    }
}
