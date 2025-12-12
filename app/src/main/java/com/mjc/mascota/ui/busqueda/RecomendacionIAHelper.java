package com.mjc.mascota.ui.busqueda;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.functions.FirebaseFunctions;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helper simplificado para manejar BottomSheet de recomendaciones IA
 */
public class RecomendacionIAHelper {

    private static final String TAG = "RecomendacionIAHelper";

    private final Activity activity;
    private BottomSheetDialog bottomSheetDialog;
    private View bottomSheetView;

    // Referencias a vistas
    private LinearLayout loadingContainer;
    private FrameLayout recommendationContainer;
    private LinearLayout errorContainer;
    private LinearLayout recommendationContent;
    private MaterialButton btnRetry;

    public interface OnRecommendationListener {
        void onSuccess(Map<String, Object> paseador);
        void onError(String error);
    }

    private OnRecommendationListener listener;

    public RecomendacionIAHelper(Activity activity) {
        this.activity = activity;
        initBottomSheet();
    }

    private void initBottomSheet() {
        bottomSheetView = LayoutInflater.from(activity)
                .inflate(R.layout.bottom_sheet_recomendacion_ia, null);

        bottomSheetDialog = new BottomSheetDialog(activity);
        bottomSheetDialog.setContentView(bottomSheetView);
        bottomSheetDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        bottomSheetDialog.getBehavior().setSkipCollapsed(true);

        // Inicializar vistas
        loadingContainer = bottomSheetView.findViewById(R.id.loading_container);
        recommendationContainer = bottomSheetView.findViewById(R.id.recommendation_container);
        errorContainer = bottomSheetView.findViewById(R.id.error_container);
        recommendationContent = bottomSheetView.findViewById(R.id.recommendation_content);
        btnRetry = bottomSheetView.findViewById(R.id.btn_retry);

        btnRetry.setOnClickListener(v -> {
            showLoading();
            buscarRecomendaciones();
        });
    }

    public void setOnRecommendationListener(OnRecommendationListener listener) {
        this.listener = listener;
    }

    public void show() {
        showLoading();
        bottomSheetDialog.show();
        buscarRecomendaciones();
    }

    public void dismiss() {
        if (bottomSheetDialog != null) {
            bottomSheetDialog.dismiss();
        }
    }

    private void showLoading() {
        loadingContainer.setVisibility(View.VISIBLE);
        recommendationContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loadingContainer.setVisibility(View.GONE);
        recommendationContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.VISIBLE);

        if (listener != null) {
            listener.onError(message);
        }
    }

    private void showRecommendation(Map<String, Object> recommendation, Map<String, Object> paseadorData) {
        loadingContainer.setVisibility(View.GONE);
        recommendationContainer.setVisibility(View.VISIBLE);
        errorContainer.setVisibility(View.GONE);

        recommendationContent.removeAllViews();
        View itemView = LayoutInflater.from(activity)
                .inflate(R.layout.item_paseador_recomendacion_ia, recommendationContent, false);

        bindData(itemView, recommendation, paseadorData);
        recommendationContent.addView(itemView);

        if (listener != null) {
            listener.onSuccess(recommendation);
        }
    }

    private void bindData(View itemView, Map<String, Object> recommendation, Map<String, Object> paseadorData) {
        // Información básica
        TextView tvWalkerName = itemView.findViewById(R.id.tvWalkerName);
        TextView tvLocation = itemView.findViewById(R.id.tvLocation);
        TextView tvExperience = itemView.findViewById(R.id.tvExperience);
        TextView tvSpecialty = itemView.findViewById(R.id.tvSpecialty);
        TextView tvRating = itemView.findViewById(R.id.tvRating);
        TextView tvReviewCount = itemView.findViewById(R.id.tvReviewCount);
        TextView tvPrice = itemView.findViewById(R.id.tvPrice);
        TextView tvMatchScore = itemView.findViewById(R.id.tvMatchScore);
        ImageView ivProfilePhoto = itemView.findViewById(R.id.ivProfilePhoto);
        View vAvailabilityDot = itemView.findViewById(R.id.vAvailabilityDot);
        ChipGroup chipGroupReasons = itemView.findViewById(R.id.chipGroupReasons);

        // Nombre
        String nombre = (String) recommendation.get("nombre");
        tvWalkerName.setText(nombre != null ? nombre : "Paseador");

        // Match Score
        int matchScore = getIntValue(recommendation.get("match_score"));
        tvMatchScore.setText(matchScore + "% Match");

        // Rating
        double calificacion = getDoubleValue(paseadorData.get("calificacion_promedio"));
        tvRating.setText(String.format(Locale.getDefault(), "%.1f", calificacion));

        // Reseñas
        int servicios = getIntValue(paseadorData.get("num_servicios_completados"));
        tvReviewCount.setText("(" + servicios + " reseñas)");

        // Precio
        double precio = getDoubleValue(paseadorData.get("precio_hora"));
        tvPrice.setText(String.format(Locale.getDefault(), "$%.0f/h", precio));

        // Experiencia
        int experiencia = getIntValue(paseadorData.get("anos_experiencia"));
        tvExperience.setText(experiencia + " años exp.");

        // Ubicación (simplificada)
        tvLocation.setText("Zona cercana");

        // Especialidad
        tvSpecialty.setText("Especialista en tu mascota");

        // Disponibilidad (asumiendo disponible)
        vAvailabilityDot.setVisibility(View.VISIBLE);

        // Foto (Cargar con Glide)
        String fotoUrl = (String) paseadorData.get("foto_url"); // Campo usual en paseadores_search
        if (fotoUrl == null) fotoUrl = (String) paseadorData.get("foto_perfil"); // Fallback

        if (fotoUrl != null && !fotoUrl.isEmpty()) {
            Glide.with(activity)
                    .load(fotoUrl)
                    .placeholder(R.drawable.bg_avatar_circle)
                    .error(R.drawable.bg_avatar_circle)
                    .circleCrop()
                    .into(ivProfilePhoto);
        } else {
            ivProfilePhoto.setImageResource(R.drawable.bg_avatar_circle);
        }

        // Razones de IA
        chipGroupReasons.removeAllViews();
        String razonIA = (String) recommendation.get("razon_ia");
        if (razonIA != null && !razonIA.isEmpty()) {
            Chip chip = new Chip(activity);
            chip.setText("✨ " + razonIA);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setTextColor(activity.getResources().getColor(R.color.walki_ai_purple));
            chip.setChipBackgroundColorResource(R.color.white);
            chip.setChipStrokeColorResource(R.color.walki_ai_purple_light);
            chip.setChipStrokeWidth(3f); // 1dp aprox
            chipGroupReasons.addView(chip);
        }

        // Verified Badge
        ImageView ivVerifiedBadge = itemView.findViewById(R.id.ivVerifiedBadge);
        String verificacionEstado = (String) paseadorData.get("verificacion_estado");
        if ("verificado".equals(verificacionEstado)) {
            ivVerifiedBadge.setVisibility(View.VISIBLE);
        } else {
            ivVerifiedBadge.setVisibility(View.GONE);
        }

        // Botones
        MaterialButton btnViewProfile = itemView.findViewById(R.id.btnViewProfile);
        MaterialButton btnFavorite = itemView.findViewById(R.id.btnFavorite);
        MaterialButton btnShare = itemView.findViewById(R.id.btnShare);

        String paseadorId = (String) recommendation.get("id");

        btnViewProfile.setOnClickListener(v -> {
            Log.d(TAG, "Ver perfil: " + paseadorId);
            bottomSheetDialog.dismiss();
            
            // Navegar al perfil del paseador
            android.content.Intent intent = new android.content.Intent(activity, com.mjc.mascotalink.PerfilPaseadorActivity.class);
            intent.putExtra("paseador_id", paseadorId);
            activity.startActivity(intent);
        });

        btnFavorite.setOnClickListener(v -> {
            // TODO: Agregar a favoritos
            Log.d(TAG, "Favorito: " + paseadorId);
        });

        btnShare.setOnClickListener(v -> {
            // TODO: Compartir
            Log.d(TAG, "Compartir: " + paseadorId);
        });
    }

    private void buscarRecomendaciones() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showError("Debes iniciar sesión");
            return;
        }

        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Obtener usuario
        db.collection("usuarios").document(userId).get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        showError("No se encontró tu perfil");
                        return;
                    }

                    Map<String, Object> userData = userDoc.getData();

                    // Obtener mascota
                    db.collection("mascotas")
                            .whereEqualTo("usuario_id", userId)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(petSnapshot -> {
                                if (petSnapshot.isEmpty()) {
                                    showError("Registra tu mascota primero");
                                    return;
                                }

                                Map<String, Object> petData = petSnapshot.getDocuments().get(0).getData();

                                // Obtener ubicación
                                Map<String, Object> userLocation = new HashMap<>();
                                Object ubicacionObj = userData.get("ubicacion_principal");

                                if (ubicacionObj instanceof Map) {
                                    Map<String, Object> ubicacion = (Map<String, Object>) ubicacionObj;
                                    Object geopointObj = ubicacion.get("geopoint");

                                    if (geopointObj instanceof GeoPoint) {
                                        GeoPoint geoPoint = (GeoPoint) geopointObj;
                                        userLocation.put("latitude", geoPoint.getLatitude());
                                        userLocation.put("longitude", geoPoint.getLongitude());
                                    }
                                }

                                // Llamar Cloud Function
                                llamarCloudFunction(userData, petData, userLocation);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error obteniendo mascota", e);
                                showError("Error al obtener tu mascota");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error obteniendo usuario", e);
                    showError("Error al obtener tu perfil");
                });
    }

    private void llamarCloudFunction(Map<String, Object> userData,
                                     Map<String, Object> petData,
                                     Map<String, Object> userLocation) {

        Map<String, Object> data = new HashMap<>();
        data.put("userData", userData);
        data.put("petData", petData);
        data.put("userLocation", userLocation);

        FirebaseFunctions.getInstance()
                .getHttpsCallable("recomendarPaseadores")
                .call(data)
                .addOnSuccessListener(result -> {
                    Map<String, Object> response = (Map<String, Object>) result.getData();
                    List<Map<String, Object>> recommendations =
                            (List<Map<String, Object>>) response.get("recommendations");

                    if (recommendations == null || recommendations.isEmpty()) {
                        showError("No encontramos matches perfectos");
                        return;
                    }

                    // Cargar detalles de TODOS los recomendados en paralelo
                    List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot>> tasks = new ArrayList<>();
                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    for (Map<String, Object> rec : recommendations) {
                        String paseadorId = (String) rec.get("id");
                        tasks.add(db.collection("paseadores_search").document(paseadorId).get());
                    }

                    com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks)
                            .addOnSuccessListener(objects -> {
                                loadingContainer.setVisibility(View.GONE);
                                recommendationContainer.setVisibility(View.VISIBLE);
                                errorContainer.setVisibility(View.GONE);
                                recommendationContent.removeAllViews();

                                for (int i = 0; i < objects.size(); i++) {
                                    com.google.firebase.firestore.DocumentSnapshot doc = (com.google.firebase.firestore.DocumentSnapshot) objects.get(i);
                                    if (doc.exists()) {
                                        View itemView = LayoutInflater.from(activity)
                                                .inflate(R.layout.item_paseador_recomendacion_ia, recommendationContent, false);

                                        // Añadir margen inferior si hay múltiples cartas
                                        if (objects.size() > 1 && i < objects.size() - 1) {
                                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                            );
                                            params.setMargins(0, 0, 0, 32); // 32dp de margen
                                            itemView.setLayoutParams(params);
                                        }

                                        bindData(itemView, recommendations.get(i), doc.getData());
                                        recommendationContent.addView(itemView);
                                    }
                                }

                                if (listener != null && !recommendations.isEmpty()) {
                                    listener.onSuccess(recommendations.get(0));
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error cargando detalles de paseadores", e);
                                showError("Error al cargar datos de recomendación");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error llamando Cloud Function", e);
                    showError("Error al buscar recomendaciones");
                });
    }

    private int getIntValue(Object obj) {
        if (obj instanceof Long) {
            return ((Long) obj).intValue();
        } else if (obj instanceof Double) {
            return ((Double) obj).intValue();
        } else if (obj instanceof Integer) {
            return (Integer) obj;
        }
        return 0;
    }

    private double getDoubleValue(Object obj) {
        if (obj instanceof Double) {
            return (Double) obj;
        } else if (obj instanceof Long) {
            return ((Long) obj).doubleValue();
        } else if (obj instanceof Integer) {
            return ((Integer) obj).doubleValue();
        }
        return 0.0;
    }
}
