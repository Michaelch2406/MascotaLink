package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;
import com.mjc.mascotalink.adapters.FotosPaseoAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private TextView tvFechaFinalizacion, tvDuracionReal, tvDistancia, tvCostoTotal;
    private TextView tvLabelRol, tvNombreOtro, tvTituloCalificacion, tvYaCalificado;
    private ShapeableImageView ivPerfilOtro;
    private ImageView ivCheckSuccess; // Added reference
    private RecyclerView rvFotosResumen;
    private FotosPaseoAdapter fotosAdapter;
    private View cardCalificacion;
    private RatingBar ratingBar;
    private TextInputEditText etComentario;
    private TextInputLayout tilComentario;
    private Button btnEnviarCalificacion;
    private View llDistancia;

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
        setupToolbar();
        determineRoleAndLoadData();
        animateSuccessIcon(); // Start animation
    }

    private void initViews() {
        tvFechaFinalizacion = findViewById(R.id.tv_fecha_finalizacion);
        tvDuracionReal = findViewById(R.id.tv_duracion_real);
        tvDistancia = findViewById(R.id.tv_distancia);
        tvCostoTotal = findViewById(R.id.tv_costo_total);
        tvLabelRol = findViewById(R.id.tv_label_rol);
        tvNombreOtro = findViewById(R.id.tv_nombre_otro);
        ivPerfilOtro = findViewById(R.id.iv_perfil_otro);
        ivCheckSuccess = findViewById(R.id.iv_check_success); // Bind view
        tvTituloCalificacion = findViewById(R.id.tv_titulo_calificacion);
        tvYaCalificado = findViewById(R.id.tv_ya_calificado);
        
        rvFotosResumen = findViewById(R.id.rv_fotos_resumen);
        rvFotosResumen.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        fotosAdapter = new FotosPaseoAdapter(this, new FotosPaseoAdapter.OnFotoInteractionListener() {
            @Override
            public void onFotoClick(String url) {
                // Optional: Show full screen
            }
            @Override
            public void onFotoLongClick(String url) {}
        });
        rvFotosResumen.setAdapter(fotosAdapter);

        cardCalificacion = findViewById(R.id.card_calificacion);
        ratingBar = findViewById(R.id.rating_bar);
        etComentario = findViewById(R.id.et_comentario);
        tilComentario = findViewById(R.id.til_comentario);
        btnEnviarCalificacion = findViewById(R.id.btn_enviar_calificacion);
        llDistancia = findViewById(R.id.ll_distancia);

        btnEnviarCalificacion.setOnClickListener(v -> submitRating());
    }

    private void animateSuccessIcon() {
        if (ivCheckSuccess == null) return;
        
        // Scale Animation (Pop effect)
        ivCheckSuccess.setScaleX(0f);
        ivCheckSuccess.setScaleY(0f);
        
        ivCheckSuccess.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(new android.view.animation.OvershootInterpolator())
            .withEndAction(() -> {
                // Shake Animation
                android.animation.ObjectAnimator rotate = android.animation.ObjectAnimator.ofFloat(ivCheckSuccess, "rotation", 0f, -20f, 20f, -20f, 20f, 0f);
                rotate.setDuration(500);
                rotate.start();
            })
            .start();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> checkUnsavedRatingAndExit());
    }

    private void determineRoleAndLoadData() {
        // Fetch user role first
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
        // 1. Dates
        Date fechaFin = doc.getDate("fecha_fin_paseo");
        if (fechaFin != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM, h:mm a", new Locale("es", "ES"));
            tvFechaFinalizacion.setText(sdf.format(fechaFin));
        }

        // 2. Duration
        Long duracion = doc.getLong("tiempo_total_minutos");
        if (duracion == null) duracion = doc.getLong("duracion_minutos");
        if (duracion != null) {
            tvDuracionReal.setText(duracion + " min");
        }

        // 3. Distance (Placeholder or Actual)
        // In a real app, we would sum distances from 'ubicaciones' array
        llDistancia.setVisibility(View.GONE); // Hide for now unless we calculate it

        // 4. Cost
        Double costo = doc.getDouble("costo_total");
        if (costo != null) {
            tvCostoTotal.setText(String.format(Locale.US, "$%.2f", costo));
        }

        // 5. Counterpart Info
        DocumentReference duenoRef = doc.getDocumentReference("id_dueno");
        DocumentReference paseadorRef = doc.getDocumentReference("id_paseador");

        if (duenoRef != null) idDueno = duenoRef.getId();
        if (paseadorRef != null) idPaseador = paseadorRef.getId();

        if ("DUEÑO".equalsIgnoreCase(currentUserRole)) {
            // I am Owner, show Walker info
            tvLabelRol.setText("Paseador");
            loadCounterpartProfile(paseadorRef, "paseadores");
        } else {
            // I am Walker, show Owner info
            tvLabelRol.setText("Dueño");
            loadCounterpartProfile(duenoRef, "duenos");
        }

        // 6. Photos
        List<String> fotos = (List<String>) doc.get("fotos_paseo");
        if (fotos != null && !fotos.isEmpty()) {
            findViewById(R.id.tv_label_fotos).setVisibility(View.VISIBLE);
            rvFotosResumen.setVisibility(View.VISIBLE);
            fotosAdapter.submitList(fotos);
        }
    }

    private void loadCounterpartProfile(DocumentReference ref, String collection) {
        if (ref == null) return;
        ref.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String nombre = doc.getString("nombre_display");
                if (nombre == null) nombre = doc.getString("nombre");
                tvNombreOtro.setText(nombre != null ? nombre : "Usuario");

                String fotoUrl = doc.getString("foto_perfil");
                if (fotoUrl != null && !fotoUrl.isEmpty()) {
                    Glide.with(this).load(fotoUrl).into(ivPerfilOtro);
                }
            }
        });
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
                        showRatedState();
                    }
                });
    }

    private void showRatedState() {
        cardCalificacion.setVisibility(View.GONE);
        tvYaCalificado.setVisibility(View.VISIBLE);
    }

    private void submitRating() {
        float stars = ratingBar.getRating();
        String comment = etComentario.getText().toString().trim();

        if (stars < 1) {
            Toast.makeText(this, "Por favor selecciona al menos 1 estrella", Toast.LENGTH_SHORT).show();
            return;
        }
        if (comment.length() < 10) {
            tilComentario.setError("El comentario debe tener al menos 10 caracteres");
            return;
        }
        tilComentario.setError(null);

        if (isRatingSubmitting) return;
        isRatingSubmitting = true;
        btnEnviarCalificacion.setEnabled(false);
        btnEnviarCalificacion.setText("Enviando...");

        // Prepare Data
        String targetCollection = "DUEÑO".equalsIgnoreCase(currentUserRole) ? "resenas_paseadores" : "resenas_duenos";
        String targetUserId = "DUEÑO".equalsIgnoreCase(currentUserRole) ? idPaseador : idDueno;
        
        // Note: If structure is Root Collection -> Document (Review)
        Map<String, Object> reviewData = new HashMap<>();
        reviewData.put("autorId", currentUserId);
        reviewData.put("reservaId", reservaId);
        reviewData.put("calificacion", stars);
        reviewData.put("comentario", comment);
        reviewData.put("timestamp", FieldValue.serverTimestamp());
        
        if ("DUEÑO".equalsIgnoreCase(currentUserRole)) {
            reviewData.put("paseadorId", idPaseador); // Target
            reviewData.put("duenoId", currentUserId); // Author
        } else {
            reviewData.put("duenoId", idDueno); // Target
            reviewData.put("paseadorId", currentUserId); // Author
        }

        // Transaction to ensure atomicity (Add Review + Update Average)
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            // 1. Reference to new review doc
            DocumentReference newReviewRef = db.collection(targetCollection).document();
            
            // 2. Reference to target user profile
            String profileCollection = "DUEÑO".equalsIgnoreCase(currentUserRole) ? "paseadores" : "duenos";
            DocumentReference profileRef = db.collection(profileCollection).document(targetUserId);
            
            DocumentSnapshot profileSnap = transaction.get(profileRef);
            
            // 3. Calculate new average
            double currentAvg = 0.0;
            long totalReviews = 0;
            
            if (profileSnap.exists()) {
                Double avgObj = profileSnap.getDouble("calificacion_promedio");
                Long totalObj = profileSnap.getLong("total_resenas");
                if (avgObj != null) currentAvg = avgObj;
                if (totalObj != null) totalReviews = totalObj;
            }

            // Math: NewAvg = ((OldAvg * Total) + NewRating) / (Total + 1)
            double newTotalScore = (currentAvg * totalReviews) + stars;
            long newTotalReviews = totalReviews + 1;
            double newAvg = newTotalScore / newTotalReviews;

            // 4. Writes
            transaction.set(newReviewRef, reviewData);
            transaction.update(profileRef, "calificacion_promedio", newAvg);
            transaction.update(profileRef, "total_resenas", newTotalReviews);
            
            return null;
        }).addOnSuccessListener(aVoid -> {
            isRatingSubmitting = false;
            Toast.makeText(this, "¡Gracias por tu feedback!", Toast.LENGTH_LONG).show();
            showRatedState();
            
            // Send Notification (Simulated / Placeholder)
            sendNotificationToCounterpart();
            
            // Optional: Finish after delay
            // new Handler().postDelayed(this::finish, 2000);
        }).addOnFailureListener(e -> {
            isRatingSubmitting = false;
            btnEnviarCalificacion.setEnabled(true);
            btnEnviarCalificacion.setText("Enviar Calificación");
            Log.e(TAG, "Error submitting rating", e);
            Toast.makeText(this, "Error al enviar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void sendNotificationToCounterpart() {
        // In a real app with Cloud Functions, this is handled by a trigger on the 'reseñas' collection.
        // For this prototype, we will just log it or write to a 'notificaciones' collection if it existed.
        // Since we don't have a Notification Service implemented in this turn, we skip the actual push.
        Log.d(TAG, "Notification would be sent to user: " + ("DUEÑO".equalsIgnoreCase(currentUserRole) ? idPaseador : idDueno));
    }

    private void checkUnsavedRatingAndExit() {
        if (cardCalificacion.getVisibility() == View.VISIBLE && ratingBar.getRating() > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("¿Salir sin calificar?")
                    .setMessage("Aún no has enviado tu calificación. ¿Estás seguro de que quieres salir?")
                    .setPositiveButton("Salir", (dialog, which) -> finish())
                    .setNegativeButton("Cancelar", null)
                    .show();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        checkUnsavedRatingAndExit();
    }
}
