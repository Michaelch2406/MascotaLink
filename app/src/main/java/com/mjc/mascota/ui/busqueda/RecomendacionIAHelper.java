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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.functions.FirebaseFunctions;
import com.mjc.mascota.utils.FirestoreConstants;
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
        registrarEventoTelemetria("solicitud", null, null, null);
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
        registrarEventoTelemetria("error", null, null, message);

        if (listener != null) {
            listener.onError(message);
        }
    }

    private void bindData(View itemView, Map<String, Object> recommendation, Map<String, Object> paseadorData) {
        bindBasicInfo(itemView, recommendation, paseadorData);
        bindPhoto(itemView, paseadorData);
        bindReasons(itemView, recommendation);
        bindVerifiedBadge(itemView, paseadorData);
        setupActionButtons(itemView, recommendation, paseadorData);
    }

    private void bindBasicInfo(View itemView, Map<String, Object> recommendation, Map<String, Object> paseadorData) {
        TextView tvWalkerName = itemView.findViewById(R.id.tvWalkerName);
        TextView tvLocation = itemView.findViewById(R.id.tvLocation);
        TextView tvExperience = itemView.findViewById(R.id.tvExperience);
        TextView tvSpecialty = itemView.findViewById(R.id.tvSpecialty);
        TextView tvRating = itemView.findViewById(R.id.tvRating);
        TextView tvReviewCount = itemView.findViewById(R.id.tvReviewCount);
        TextView tvPrice = itemView.findViewById(R.id.tvPrice);
        TextView tvMatchScore = itemView.findViewById(R.id.tvMatchScore);
        View vAvailabilityDot = itemView.findViewById(R.id.vAvailabilityDot);

        String nombre = (String) recommendation.get(FirestoreConstants.FIELD_NOMBRE);
        tvWalkerName.setText(nombre != null ? nombre : "Paseador");

        int matchScore = getIntValue(recommendation.get(FirestoreConstants.FIELD_MATCH_SCORE_LOWER));
        tvMatchScore.setText(matchScore + "% Match");

        double calificacion = getDoubleValue(paseadorData.get(FirestoreConstants.FIELD_CALIFICACION_PROMEDIO));
        tvRating.setText(String.format(Locale.getDefault(), "%.1f", calificacion));

        int servicios = getIntValue(paseadorData.get(FirestoreConstants.FIELD_NUM_SERVICIOS_COMPLETADOS));
        tvReviewCount.setText("(" + servicios + " reseñas)");

        double precio = getDoubleValue(paseadorData.get(FirestoreConstants.FIELD_PRECIO_HORA));
        tvPrice.setText(String.format(Locale.getDefault(), "$%.0f/h", precio));

        int experiencia = getIntValue(paseadorData.get(FirestoreConstants.FIELD_ANOS_EXPERIENCIA));
        tvExperience.setText(experiencia + " años exp.");

        tvLocation.setText("Zona cercana");
        tvSpecialty.setText("Especialista en tu mascota");
        vAvailabilityDot.setVisibility(View.VISIBLE);
    }

    private void bindPhoto(View itemView, Map<String, Object> paseadorData) {
        ImageView ivProfilePhoto = itemView.findViewById(R.id.ivProfilePhoto);
        String fotoUrl = (String) paseadorData.get(FirestoreConstants.FIELD_FOTO_URL);
        if (fotoUrl == null) {
            fotoUrl = (String) paseadorData.get(FirestoreConstants.FIELD_FOTO_PERFIL);
        }

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
    }

    private void bindReasons(View itemView, Map<String, Object> recommendation) {
        ChipGroup chipGroupReasons = itemView.findViewById(R.id.chipGroupReasons);
        chipGroupReasons.removeAllViews();

        String razonIA = (String) recommendation.get(FirestoreConstants.FIELD_RAZON_IA);
        if (razonIA != null && !razonIA.isEmpty()) {
            addReasonChip(chipGroupReasons, "✨ " + razonIA);
        }

        List<String> tags = (List<String>) recommendation.get(FirestoreConstants.FIELD_TAGS);
        if (tags != null && !tags.isEmpty()) {
            for (String tag : tags) {
                addReasonChip(chipGroupReasons, tag);
            }
        }
    }

    private void addReasonChip(ChipGroup chipGroup, String text) {
        Chip chip = new Chip(activity);
        chip.setText(text);
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setTextColor(activity.getResources().getColor(R.color.walki_ai_purple));
        chip.setChipBackgroundColorResource(R.color.white);
        chip.setChipStrokeColorResource(R.color.walki_ai_purple_light);
        chip.setChipStrokeWidth(3f);
        chipGroup.addView(chip);
    }

    private void bindVerifiedBadge(View itemView, Map<String, Object> paseadorData) {
        ImageView ivVerifiedBadge = itemView.findViewById(R.id.ivVerifiedBadge);
        String verificacionEstado = (String) paseadorData.get(FirestoreConstants.FIELD_VERIFICACION_ESTADO);
        if ("verificado".equals(verificacionEstado)) {
            ivVerifiedBadge.setVisibility(View.VISIBLE);
        } else {
            ivVerifiedBadge.setVisibility(View.GONE);
        }
    }

    private void setupActionButtons(View itemView, Map<String, Object> recommendation, Map<String, Object> paseadorData) {
        MaterialButton btnViewProfile = itemView.findViewById(R.id.btnViewProfile);
        MaterialButton btnFavorite = itemView.findViewById(R.id.btnFavorite);
        MaterialButton btnShare = itemView.findViewById(R.id.btnShare);

        String paseadorId = (String) recommendation.get(FirestoreConstants.FIELD_ID);
        int matchScore = getIntValue(recommendation.get(FirestoreConstants.FIELD_MATCH_SCORE_LOWER));

        setupFavoriteButton(btnFavorite, paseadorId, matchScore);
        setupViewProfileButton(btnViewProfile, paseadorId, matchScore);
        setupShareButton(btnShare, recommendation, paseadorData, paseadorId, matchScore);
    }

    private void setupFavoriteButton(MaterialButton btnFavorite, String paseadorId, int matchScore) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        FirebaseFirestore.getInstance()
                .collection(FirestoreConstants.COLLECTION_USUARIOS)
                .document(currentUser.getUid())
                .collection(FirestoreConstants.COLLECTION_FAVORITOS)
                .document(paseadorId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        setFavoriteButtonState(btnFavorite, true);
                    } else {
                        setFavoriteButtonState(btnFavorite, false);
                    }
                });

        btnFavorite.setOnClickListener(v -> toggleFavorite(btnFavorite, paseadorId, matchScore, currentUser));
    }

    private void setFavoriteButtonState(MaterialButton button, boolean isFavorite) {
        if (isFavorite) {
            button.setIconResource(R.drawable.ic_corazon_lleno);
            button.setIconTintResource(R.color.red_error);
            button.setTag("is_fav");
        } else {
            button.setIconResource(R.drawable.ic_favorite);
            button.setIconTintResource(R.color.grey_600);
            button.setTag("not_fav");
        }
    }

    private void toggleFavorite(MaterialButton button, String paseadorId, int matchScore, FirebaseUser currentUser) {
        boolean isFav = "is_fav".equals(button.getTag());
        com.google.firebase.firestore.DocumentReference favRef = FirebaseFirestore.getInstance()
                .collection(FirestoreConstants.COLLECTION_USUARIOS)
                .document(currentUser.getUid())
                .collection(FirestoreConstants.COLLECTION_FAVORITOS)
                .document(paseadorId);

        if (isFav) {
            removeFavorite(favRef, button);
        } else {
            addFavorite(favRef, button, paseadorId, matchScore);
        }
    }

    private void removeFavorite(com.google.firebase.firestore.DocumentReference favRef, MaterialButton button) {
        favRef.delete().addOnSuccessListener(aVoid -> setFavoriteButtonState(button, false));
    }

    private void addFavorite(com.google.firebase.firestore.DocumentReference favRef, MaterialButton button,
                            String paseadorId, int matchScore) {
        Map<String, Object> favData = new HashMap<>();
        favData.put(FirestoreConstants.FIELD_ID, paseadorId);
        favData.put(FirestoreConstants.FIELD_TIMESTAMP, com.google.firebase.firestore.FieldValue.serverTimestamp());

        favRef.set(favData).addOnSuccessListener(aVoid -> {
            setFavoriteButtonState(button, true);
            registrarEventoTelemetria("favorito", paseadorId, matchScore, null);
        });
    }

    private void setupViewProfileButton(MaterialButton btnViewProfile, String paseadorId, int matchScore) {
        btnViewProfile.setOnClickListener(v -> {
            Log.d(TAG, "Ver perfil: " + paseadorId);
            registrarEventoTelemetria("ver_perfil", paseadorId, matchScore, null);
            bottomSheetDialog.dismiss();

            android.content.Intent intent = new android.content.Intent(activity, com.mjc.mascotalink.PerfilPaseadorActivity.class);
            intent.putExtra("paseador_id", paseadorId);
            activity.startActivity(intent);
        });
    }

    private void setupShareButton(MaterialButton btnShare, Map<String, Object> recommendation,
                                  Map<String, Object> paseadorData, String paseadorId, int matchScore) {
        btnShare.setOnClickListener(v -> {
            registrarEventoTelemetria("compartir", paseadorId, matchScore, null);
            String shareText = buildShareText(recommendation, paseadorData);

            android.content.Intent sendIntent = new android.content.Intent();
            sendIntent.setAction(android.content.Intent.ACTION_SEND);
            sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareText);
            sendIntent.setType("text/plain");

            android.content.Intent shareIntent = android.content.Intent.createChooser(sendIntent, "Compartir paseador via");
            activity.startActivity(shareIntent);
        });
    }

    private String buildShareText(Map<String, Object> recommendation, Map<String, Object> paseadorData) {
        return "¡Mira este paseador que encontré en Walki!\n\n" +
                "Nombre: " + recommendation.get(FirestoreConstants.FIELD_NOMBRE) + "\n" +
                "Calificación: " + String.format(Locale.getDefault(), "%.1f",
                    getDoubleValue(paseadorData.get(FirestoreConstants.FIELD_CALIFICACION_PROMEDIO))) + " ⭐\n" +
                "Razón IA: " + recommendation.get(FirestoreConstants.FIELD_RAZON_IA) + "\n\n" +
                "Descarga la app aquí: https://walki.app";
    }

    private void buscarRecomendaciones() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showError("Debes iniciar sesión");
            return;
        }

        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection(FirestoreConstants.COLLECTION_USUARIOS).document(userId).get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        showError("No se encontró tu perfil");
                        return;
                    }

                    Map<String, Object> userData = userDoc.getData();
                    loadMascotasAndProcess(userId, userData, db);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error obteniendo usuario", e);
                    showError("Error al obtener tu perfil");
                });
    }

    private void loadMascotasAndProcess(String userId, Map<String, Object> userData, FirebaseFirestore db) {
        db.collection(FirestoreConstants.COLLECTION_DUENOS).document(userId)
                .collection(FirestoreConstants.COLLECTION_MASCOTAS)
                .get()
                .addOnSuccessListener(petSnapshot -> {
                    if (petSnapshot.isEmpty()) {
                        showError("Registra tu mascota primero");
                        return;
                    }

                    List<com.google.firebase.firestore.DocumentSnapshot> mascotas = petSnapshot.getDocuments();
                    if (mascotas.size() == 1) {
                        procesarRecomendacionConMascota(mascotas.get(0).getData(), userData);
                    } else {
                        mostrarSelectorMascota(mascotas, userData);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error obteniendo mascota", e);
                    showError("Error al obtener tu mascota");
                });
    }

    private Map<String, Object> sanitizeData(Map<String, Object> data) {
        Map<String, Object> cleanData = new HashMap<>();
        if (data == null) return cleanData;

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof com.google.firebase.Timestamp) {
                cleanData.put(entry.getKey(), ((com.google.firebase.Timestamp) value).toDate().getTime());
            } else if (value instanceof GeoPoint) {
                Map<String, Double> loc = new HashMap<>();
                loc.put("lat", ((GeoPoint) value).getLatitude());
                loc.put("lng", ((GeoPoint) value).getLongitude());
                cleanData.put(entry.getKey(), loc);
            } else if (value instanceof com.google.firebase.firestore.DocumentReference) {
                cleanData.put(entry.getKey(), ((com.google.firebase.firestore.DocumentReference) value).getPath());
            } else if (value instanceof Map) {
                cleanData.put(entry.getKey(), sanitizeData((Map<String, Object>) value));
            } else {
                cleanData.put(entry.getKey(), value);
            }
        }
        return cleanData;
    }

    private void llamarCloudFunction(Map<String, Object> userData, Map<String, Object> petData, Map<String, Object> userLocation) {
        Map<String, Object> data = new HashMap<>();
        data.put("userData", userData);
        data.put("petData", petData);
        data.put("userLocation", userLocation);

        FirebaseFunctions.getInstance()
                .getHttpsCallable("recomendarPaseadores")
                .call(data)
                .addOnSuccessListener(result -> processCloudFunctionResponse(result))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error llamando Cloud Function", e);
                    showError("Error al buscar recomendaciones");
                });
    }

    private void processCloudFunctionResponse(com.google.firebase.functions.HttpsCallableResult result) {
        Map<String, Object> response = (Map<String, Object>) result.getData();
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) response.get("recommendations");

        if (recommendations == null || recommendations.isEmpty()) {
            showError("No encontramos matches perfectos");
            return;
        }

        loadRecommendationDetails(recommendations);
    }

    private void loadRecommendationDetails(List<Map<String, Object>> recommendations) {
        List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot>> tasks = new ArrayList<>();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        for (Map<String, Object> rec : recommendations) {
            String paseadorId = (String) rec.get(FirestoreConstants.FIELD_ID);
            tasks.add(db.collection(FirestoreConstants.COLLECTION_PASEADORES_SEARCH).document(paseadorId).get());
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(objects -> displayRecommendations(objects, recommendations))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando detalles de paseadores", e);
                    showError("Error al cargar datos de recomendación");
                });
    }

    private void displayRecommendations(List<Object> objects, List<Map<String, Object>> recommendations) {
        loadingContainer.setVisibility(View.GONE);
        recommendationContainer.setVisibility(View.VISIBLE);
        errorContainer.setVisibility(View.GONE);
        recommendationContent.removeAllViews();

        for (int i = 0; i < objects.size(); i++) {
            com.google.firebase.firestore.DocumentSnapshot doc = (com.google.firebase.firestore.DocumentSnapshot) objects.get(i);
            if (doc.exists()) {
                addRecommendationCard(doc, recommendations.get(i), i, objects.size());
            }
        }

        if (!recommendations.isEmpty()) {
            Map<String, Object> firstRec = recommendations.get(0);
            String paseadorId = (String) firstRec.get(FirestoreConstants.FIELD_ID);
            Integer matchScore = getIntValue(firstRec.get(FirestoreConstants.FIELD_MATCH_SCORE_LOWER));
            registrarEventoTelemetria("exito", paseadorId, matchScore, null);

            if (listener != null) {
                listener.onSuccess(firstRec);
            }
        }
    }

    private void addRecommendationCard(com.google.firebase.firestore.DocumentSnapshot doc,
                                      Map<String, Object> recommendation, int index, int total) {
        View itemView = LayoutInflater.from(activity)
                .inflate(R.layout.item_paseador_recomendacion_ia, recommendationContent, false);

        if (total > 1 && index < total - 1) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 32);
            itemView.setLayoutParams(params);
        }

        bindData(itemView, recommendation, doc.getData());
        recommendationContent.addView(itemView);
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

    private void registrarEventoTelemetria(String evento, String paseadorId, Integer matchScore, String errorMsg) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        Map<String, Object> telemetria = new HashMap<>();
        telemetria.put(FirestoreConstants.FIELD_USER_ID, currentUser.getUid());
        telemetria.put(FirestoreConstants.FIELD_TIMESTAMP, com.google.firebase.firestore.FieldValue.serverTimestamp());
        telemetria.put(FirestoreConstants.FIELD_EVENTO, evento);

        if (paseadorId != null) {
            telemetria.put(FirestoreConstants.FIELD_PASEADOR_ID, paseadorId);
        }
        if (matchScore != null) {
            telemetria.put(FirestoreConstants.FIELD_MATCH_SCORE, matchScore);
        }
        if (errorMsg != null) {
            telemetria.put(FirestoreConstants.FIELD_ERROR_MSG, errorMsg);
        }

        FirebaseFirestore.getInstance()
                .collection(FirestoreConstants.COLLECTION_RECOMENDACIONES_IA_LOGS)
                .add(telemetria)
                .addOnSuccessListener(doc -> Log.d(TAG, "📊 Telemetría: " + evento))
                .addOnFailureListener(e -> Log.e(TAG, "Error guardando telemetría", e));
    }

    private void procesarRecomendacionConMascota(Map<String, Object> petData, Map<String, Object> userData) {
        Map<String, Object> userLocation = extractUserLocation(userData);
        llamarCloudFunction(sanitizeData(userData), sanitizeData(petData), userLocation);
    }

    private Map<String, Object> extractUserLocation(Map<String, Object> userData) {
        Map<String, Object> userLocation = new HashMap<>();
        Object ubicacionObj = userData.get(FirestoreConstants.FIELD_UBICACION_PRINCIPAL);

        if (ubicacionObj instanceof Map) {
            Map<String, Object> ubicacion = (Map<String, Object>) ubicacionObj;
            Object geopointObj = ubicacion.get(FirestoreConstants.FIELD_GEOPOINT);

            if (geopointObj instanceof GeoPoint) {
                GeoPoint geoPoint = (GeoPoint) geopointObj;
                userLocation.put(FirestoreConstants.FIELD_LATITUDE, geoPoint.getLatitude());
                userLocation.put(FirestoreConstants.FIELD_LONGITUDE, geoPoint.getLongitude());
            }
        }
        return userLocation;
    }

    private void mostrarSelectorMascota(List<com.google.firebase.firestore.DocumentSnapshot> mascotas, Map<String, Object> userData) {
        String[] nombresMascotas = buildMascotaNames(mascotas);

        new android.app.AlertDialog.Builder(activity)
                .setTitle("Selecciona tu mascota")
                .setItems(nombresMascotas, (dialog, which) -> {
                    Map<String, Object> mascotaSeleccionada = mascotas.get(which).getData();
                    Log.d(TAG, "Mascota seleccionada: " + mascotaSeleccionada.get(FirestoreConstants.FIELD_NOMBRE));
                    procesarRecomendacionConMascota(mascotaSeleccionada, userData);
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    dialog.dismiss();
                    bottomSheetDialog.dismiss();
                })
                .setCancelable(true)
                .show();
    }

    private String[] buildMascotaNames(List<com.google.firebase.firestore.DocumentSnapshot> mascotas) {
        String[] nombresMascotas = new String[mascotas.size()];
        for (int i = 0; i < mascotas.size(); i++) {
            Map<String, Object> mascota = mascotas.get(i).getData();
            String nombre = (String) mascota.get(FirestoreConstants.FIELD_NOMBRE);
            String tipo = (String) mascota.get("tipo_mascota");
            nombresMascotas[i] = nombre + " (" + (tipo != null ? tipo : "Mascota") + ")";
        }
        return nombresMascotas;
    }
}
