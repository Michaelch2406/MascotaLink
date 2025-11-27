package com.mjc.mascotalink;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Transaction;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ResumenPaseoActivity extends AppCompatActivity {

    private static final String TAG = "ResumenPaseoActivity";

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String reservaId;
    private String currentUserId;
    private String currentUserRole; // "DUEÑO" or "PASEADOR"

    // UI Components
    private TextView tvFechaPaseo, tvDuracionReal, tvCostoFinal, tvDistanciaRecorrida;
    private TextView tvTituloCalificacion;
    private View cardCalificacion;
    private RatingBar ratingBar;
    private TextInputEditText etComentario;
    private TextInputLayout tilComentario;
    private Button btnEnviarCalificacion;
    private Button btnVolverInicio;

    // Data
    private String idPaseador;
    private String idDueno;
    private boolean isRatingSubmitting = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resumen_paseo);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            finish();
            return;
        }
        currentUserId = auth.getCurrentUser().getUid();

        // Get Intent Data
        reservaId = getIntent().getStringExtra("id_reserva");
        if (reservaId == null || reservaId.isEmpty()) {
            Toast.makeText(this, "Error: Reserva no encontrada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        determineRoleAndLoadData();
    }

    private void initViews() {
        tvFechaPaseo = findViewById(R.id.tv_fecha_paseo);
        tvDuracionReal = findViewById(R.id.tv_duracion_real);
        tvDistanciaRecorrida = findViewById(R.id.tv_distancia_recorrida);
        tvCostoFinal = findViewById(R.id.tv_costo_final);
        
        // Calificación
        cardCalificacion = findViewById(R.id.card_calificacion);
        tvTituloCalificacion = findViewById(R.id.tv_titulo_calificacion);
        ratingBar = findViewById(R.id.rating_bar);
        etComentario = findViewById(R.id.et_comentario);
        tilComentario = findViewById(R.id.til_comentario);
        btnEnviarCalificacion = findViewById(R.id.btn_enviar_calificacion);
        btnVolverInicio = findViewById(R.id.btn_volver_inicio);

        btnEnviarCalificacion.setOnClickListener(v -> submitRating());
        
        btnVolverInicio.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void determineRoleAndLoadData() {
        db.collection("usuarios").document(currentUserId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentUserRole = doc.getString("rol");
                        loadReservaDetails();
                    } else {
                        Toast.makeText(this, "Error perfil usuario", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user role", e);
                    finish();
                });
    }

    private void loadReservaDetails() {
        db.collection("reservas").document(reservaId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Reserva no existe", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    populateUI(doc);
                    checkIfAlreadyRated();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading reserva", e));
    }

    private void populateUI(DocumentSnapshot doc) {
        // 1. Fecha
        Date fechaFin = doc.getDate("fecha_fin_paseo");
        if (fechaFin == null) fechaFin = doc.getDate("fecha"); // Fallback
        if (fechaFin != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM, h:mm a", new Locale("es", "ES"));
            tvFechaPaseo.setText(sdf.format(fechaFin));
        }

        // 2. Duration
        Long duracion = doc.getLong("tiempo_total_minutos");
        if (duracion == null) duracion = doc.getLong("duracion_minutos");
        if (duracion != null) {
            tvDuracionReal.setText(duracion + " min");
        }
        
        // 3. Distancia (Calculada desde el array de ubicaciones)
        List<GeoPoint> ubicaciones = (List<GeoPoint>) doc.get("ubicaciones");
        double distanciaMeters = 0;

        if (ubicaciones != null && ubicaciones.size() > 1) {
            for (int i = 0; i < ubicaciones.size() - 1; i++) {
                GeoPoint p1 = ubicaciones.get(i);
                GeoPoint p2 = ubicaciones.get(i + 1);
                
                float[] results = new float[1];
                Location.distanceBetween(
                    p1.getLatitude(), p1.getLongitude(),
                    p2.getLatitude(), p2.getLongitude(),
                    results
                );
                distanciaMeters += results[0];
            }
        }
        
        if (distanciaMeters > 0) {
            double distanciaKm = distanciaMeters / 1000.0;
            tvDistanciaRecorrida.setText(String.format(Locale.US, "%.2f km", distanciaKm));
        } else {
            tvDistanciaRecorrida.setText("0.0 km");
        }

        // 4. Cost
        Double costo = doc.getDouble("costo_total");
        if (costo != null) {
            tvCostoFinal.setText(String.format(Locale.US, "$%.2f", costo));
        }

        // 5. IDs for rating logic
        DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
        DocumentReference paseadorRef = doc.getDocumentReference("id_paseador");

        if (duenoRef != null) idDueno = duenoRef.getId();
        if (paseadorRef != null) idPaseador = paseadorRef.getId();
    }

    private void checkIfAlreadyRated() {
        String collectionToCheck = "DUEÑO".equalsIgnoreCase(currentUserRole) ? "resenas_paseadores" : "resenas_duenos";
        
        db.collection(collectionToCheck)
                .whereEqualTo("reservaId", reservaId)
                .whereEqualTo("autorId", currentUserId)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Already rated, hide card or show "Thanks"
                        cardCalificacion.setVisibility(View.GONE);
                    }
                });
    }

    private void submitRating() {
        float stars = ratingBar.getRating();
        String comment = etComentario.getText().toString().trim();

        if (stars < 0.5) {
            Toast.makeText(this, "Por favor selecciona al menos media estrella", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!comment.isEmpty() && comment.length() < 10) {
            tilComentario.setError("Si dejas un comentario, debe tener al menos 10 caracteres");
            return;
        }
        tilComentario.setError(null);

        if (isRatingSubmitting) return;
        isRatingSubmitting = true;
        btnEnviarCalificacion.setEnabled(false);
        btnEnviarCalificacion.setText("Enviando...");

        String targetCollection = "DUEÑO".equalsIgnoreCase(currentUserRole) ? "resenas_paseadores" : "resenas_duenos";
        String targetUserId = "DUEÑO".equalsIgnoreCase(currentUserRole) ? idPaseador : idDueno;
        
        Map<String, Object> reviewData = new HashMap<>();
        reviewData.put("autorId", currentUserId);
        reviewData.put("reservaId", reservaId);
        reviewData.put("calificacion", stars);
        reviewData.put("comentario", comment);
        reviewData.put("timestamp", FieldValue.serverTimestamp());
        
        if ("DUEÑO".equalsIgnoreCase(currentUserRole)) {
            reviewData.put("paseadorId", idPaseador);
            reviewData.put("duenoId", currentUserId);
        } else {
            reviewData.put("duenoId", idDueno);
            reviewData.put("paseadorId", currentUserId);
        }

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference newReviewRef = db.collection(targetCollection).document();
            
            String profileCollection = "DUEÑO".equalsIgnoreCase(currentUserRole) ? "paseadores" : "duenos";
            DocumentReference profileRef = db.collection(profileCollection).document(targetUserId);
            
            DocumentSnapshot profileSnap = transaction.get(profileRef);
            
            double currentAvg = 0.0;
            long totalReviews = 0;
            
            if (profileSnap.exists()) {
                Double avgObj = profileSnap.getDouble("calificacion_promedio");
                Long totalObj = profileSnap.getLong("total_resenas");
                if (avgObj != null) currentAvg = avgObj;
                if (totalObj != null) totalReviews = totalObj;
            }

            double newTotalScore = (currentAvg * totalReviews) + stars;
            long newTotalReviews = totalReviews + 1;
            double newAvg = newTotalScore / newTotalReviews;

            transaction.set(newReviewRef, reviewData);
            transaction.update(profileRef, "calificacion_promedio", newAvg);
            transaction.update(profileRef, "total_resenas", newTotalReviews);
            
            return null;
        }).addOnSuccessListener(aVoid -> {
            isRatingSubmitting = false;
            Toast.makeText(this, "¡Gracias por tu feedback!", Toast.LENGTH_LONG).show();
            cardCalificacion.setVisibility(View.GONE);
            
            new AlertDialog.Builder(this)
                .setTitle("¡Calificación Enviada!")
                .setMessage("Tu opinión ayuda a mejorar la comunidad.")
                .setPositiveButton("Ir al Inicio", (dialog, which) -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .setCancelable(false)
                .show();

        }).addOnFailureListener(e -> {
            isRatingSubmitting = false;
            btnEnviarCalificacion.setEnabled(true);
            btnEnviarCalificacion.setText("Enviar Calificación");
            Log.e(TAG, "Error submitting rating", e);
            Toast.makeText(this, "Error al enviar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onBackPressed() {
        // Check if user rated
        if (cardCalificacion.getVisibility() == View.VISIBLE) {
             new AlertDialog.Builder(this)
                .setTitle("¿Salir sin calificar?")
                .setMessage("Tu opinión es importante. ¿Seguro que quieres salir?")
                .setPositiveButton("Salir", (dialog, which) -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .setNegativeButton("Cancelar", null)
                .show();
        } else {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }
}