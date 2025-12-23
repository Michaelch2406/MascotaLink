package com.mjc.mascota.ui.busqueda;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.functions.FirebaseFunctions;
import com.mjc.mascota.utils.FirestoreConstants;
import com.mjc.mascotalink.PerfilPaseadorActivity;
import com.mjc.mascotalink.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecomendacionIADialogFragment extends DialogFragment {

    private static final String TAG = "RecomendacionIADialog";

    private View layoutSkeleton;
    private View layoutContent;
    private LinearLayout layoutError;
    private ImageButton btnClose;

    private ImageView ivProfilePhoto;
    private ImageView ivVerifiedBadge;
    private TextView tvWalkerName;
    private TextView tvLocation;
    private TextView tvExperience;
    private TextView tvSpecialty;
    private TextView tvMatchScore;
    private TextView tvRating;
    private TextView tvReviewCount;
    private TextView tvPrice;
    private TextView tvRazonIA;
    private ChipGroup chipGroupReasons;
    private MaterialButton btnViewProfile;
    private MaterialButton btnFavorite;
    private MaterialButton btnShare;
    private MaterialButton btnNotInterested;

    private TextView tvErrorMessage;
    private MaterialButton btnRetry;

    private String paseadorId;
    private int matchScore;
    private boolean isFavorite = false;

    private FusedLocationProviderClient fusedLocationClient;
    private View cardMainRecommendation;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_recomendacion_ia_overlay, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        initViews(view);
        setupListeners();
        buscarRecomendaciones();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        }
    }

    private void initViews(View view) {
        layoutSkeleton = view.findViewById(R.id.layoutSkeleton);
        layoutContent = view.findViewById(R.id.layoutContent);
        layoutError = view.findViewById(R.id.layoutError);
        btnClose = view.findViewById(R.id.btnClose);
        cardMainRecommendation = view.findViewById(R.id.cardMainRecommendation);

        ivProfilePhoto = view.findViewById(R.id.ivProfilePhoto);
        ivVerifiedBadge = view.findViewById(R.id.ivVerifiedBadge);
        tvWalkerName = view.findViewById(R.id.tvWalkerName);
        tvLocation = view.findViewById(R.id.tvLocation);
        tvExperience = view.findViewById(R.id.tvExperience);
        tvSpecialty = view.findViewById(R.id.tvSpecialty);
        tvMatchScore = view.findViewById(R.id.tvMatchScore);
        tvRating = view.findViewById(R.id.tvRating);
        tvReviewCount = view.findViewById(R.id.tvReviewCount);
        tvPrice = view.findViewById(R.id.tvPrice);
        tvRazonIA = view.findViewById(R.id.tvRazonIA);
        chipGroupReasons = view.findViewById(R.id.chipGroupReasons);
        btnViewProfile = view.findViewById(R.id.btnViewProfile);
        btnFavorite = view.findViewById(R.id.btnFavorite);
        btnShare = view.findViewById(R.id.btnShare);
        btnNotInterested = view.findViewById(R.id.btnNoMeInteresa);

        tvErrorMessage = view.findViewById(R.id.tvErrorMessage);
        btnRetry = view.findViewById(R.id.btnRetry);
    }

    private void setupListeners() {
        if (getView() != null) {
            getView().setOnClickListener(v -> dismiss());
        }

        View cardView = getView().findViewById(R.id.cardMain);
        if (cardView != null) {
            cardView.setOnClickListener(v -> {});
        }

        btnClose.setOnClickListener(v -> dismiss());
        btnRetry.setOnClickListener(v -> buscarRecomendaciones());
    }

    private void showSkeleton() {
        layoutSkeleton.setVisibility(View.VISIBLE);
        layoutContent.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
    }

    private void showContent() {
        layoutSkeleton.setVisibility(View.GONE);
        layoutContent.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
    }

    private void showError(String message) {
        layoutSkeleton.setVisibility(View.GONE);
        layoutContent.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        tvErrorMessage.setText(message);
        registrarEventoTelemetria("error", null, null, message);
    }

    private void buscarRecomendaciones() {
        showSkeleton();
        registrarEventoTelemetria("solicitud", null, null, null);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showError("Debes iniciar sesión");
            return;
        }

        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection(FirestoreConstants.COLLECTION_USUARIOS).document(userId).get()
                .addOnSuccessListener(userDoc -> processUserData(userDoc, userId, db))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error obteniendo usuario", e);
                    showError("Error al obtener tu perfil");
                });
    }

    private void processUserData(DocumentSnapshot userDoc, String userId, FirebaseFirestore db) {
        if (!userDoc.exists()) {
            showError("No se encontró tu perfil");
            return;
        }

        Map<String, Object> userData = userDoc.getData();
        db.collection(FirestoreConstants.COLLECTION_DUENOS).document(userId)
                .collection(FirestoreConstants.COLLECTION_MASCOTAS)
                .get()
                .addOnSuccessListener(petSnapshot -> processMascotasData(petSnapshot, userData))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error obteniendo mascota", e);
                    showError("Error al obtener tu mascota");
                });
    }

    private void processMascotasData(com.google.firebase.firestore.QuerySnapshot petSnapshot, Map<String, Object> userData) {
        if (petSnapshot.isEmpty()) {
            showError("Registra tu mascota primero");
            return;
        }

        List<DocumentSnapshot> mascotas = petSnapshot.getDocuments();
        procesarRecomendacionConMascota(mascotas.get(0).getData(), userData);
    }

    private void procesarRecomendacionConMascota(Map<String, Object> petData, Map<String, Object> userData) {
        Map<String, Object> userLocation = extractUserLocation(userData);

        if (!userLocation.containsKey(FirestoreConstants.FIELD_LATITUDE) ||
            !userLocation.containsKey(FirestoreConstants.FIELD_LONGITUDE)) {
            Log.w(TAG, " No hay ubicación guardada, obteniendo ubicación actual del GPS...");
            obtenerUbicacionActualYContinuar(userData, petData);
            return;
        }

        llamarCloudFunction(sanitizeData(userData), sanitizeData(petData), userLocation);
    }

    private Map<String, Object> extractUserLocation(Map<String, Object> userData) {
        Map<String, Object> userLocation = new HashMap<>();

        GeoPoint geoPoint = tryGetGeoPointFromUbicacionPrincipal(userData);
        if (geoPoint == null) {
            geoPoint = tryGetGeoPointFromUbicacion(userData);
        }
        if (geoPoint == null) {
            geoPoint = tryGetGeoPointFromDireccionCoordenadas(userData);
        }

        if (geoPoint != null) {
            userLocation.put(FirestoreConstants.FIELD_LATITUDE, geoPoint.getLatitude());
            userLocation.put(FirestoreConstants.FIELD_LONGITUDE, geoPoint.getLongitude());
            return userLocation;
        }

        return tryGetLocationFromSeparateFields(userData);
    }

    private GeoPoint tryGetGeoPointFromUbicacionPrincipal(Map<String, Object> userData) {
        Object ubicacionObj = userData.get(FirestoreConstants.FIELD_UBICACION_PRINCIPAL);
        if (ubicacionObj instanceof Map) {
            Map<String, Object> ubicacion = (Map<String, Object>) ubicacionObj;
            Object geopointObj = ubicacion.get(FirestoreConstants.FIELD_GEOPOINT);
            if (geopointObj instanceof GeoPoint) {
                Log.d(TAG, " Ubicación obtenida de ubicacion_principal.geopoint");
                return (GeoPoint) geopointObj;
            }
        }
        return null;
    }

    private GeoPoint tryGetGeoPointFromUbicacion(Map<String, Object> userData) {
        Object ubicacionDirecta = userData.get(FirestoreConstants.FIELD_UBICACION);
        if (ubicacionDirecta instanceof GeoPoint) {
            Log.d(TAG, " Ubicación obtenida de ubicacion directa");
            return (GeoPoint) ubicacionDirecta;
        }
        return null;
    }

    private GeoPoint tryGetGeoPointFromDireccionCoordenadas(Map<String, Object> userData) {
        Object direccionCoords = userData.get(FirestoreConstants.FIELD_DIRECCION_COORDENADAS);
        if (direccionCoords instanceof GeoPoint) {
            Log.d(TAG, " Ubicación obtenida de direccion_coordenadas");
            return (GeoPoint) direccionCoords;
        }
        return null;
    }

    private Map<String, Object> tryGetLocationFromSeparateFields(Map<String, Object> userData) {
        Map<String, Object> userLocation = new HashMap<>();
        Object lat = userData.get(FirestoreConstants.FIELD_LATITUDE);
        Object lng = userData.get(FirestoreConstants.FIELD_LONGITUDE);

        if (lat != null && lng != null) {
            userLocation.put(FirestoreConstants.FIELD_LATITUDE, lat);
            userLocation.put(FirestoreConstants.FIELD_LONGITUDE, lng);
            Log.d(TAG, " Ubicación obtenida de lat/lng separados");
        }
        return userLocation;
    }

    private void obtenerUbicacionActualYContinuar(Map<String, Object> userData, Map<String, Object> petData) {
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            Map<String, Object> userLocation = new HashMap<>();
                            userLocation.put(FirestoreConstants.FIELD_LATITUDE, location.getLatitude());
                            userLocation.put(FirestoreConstants.FIELD_LONGITUDE, location.getLongitude());
                            Log.d(TAG, " Ubicación actual obtenida del GPS");
                            llamarCloudFunction(sanitizeData(userData), sanitizeData(petData), userLocation);
                        } else {
                            Log.e(TAG, " No se pudo obtener ubicación actual del GPS");
                            showError("No se pudo obtener tu ubicación actual. Por favor activa el GPS y los permisos de ubicación.");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, " Error al obtener ubicación del GPS: " + e.getMessage(), e);
                        showError("Error al obtener tu ubicación: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            Log.e(TAG, " Sin permisos de ubicación: " + e.getMessage(), e);
            showError("Se requieren permisos de ubicación. Por favor actívalos en la configuración de la app.");
        }
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

    private void llamarCloudFunction(Map<String, Object> userData, Map<String, Object> petData,
                                      Map<String, Object> userLocation) {
        Map<String, Object> data = new HashMap<>();
        data.put("userData", userData);
        data.put("petData", petData);
        data.put("userLocation", userLocation);

        FirebaseFunctions.getInstance()
                .getHttpsCallable("recomendarPaseadores")
                .call(data)
                .addOnSuccessListener(result -> processCloudFunctionResult(result))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error llamando Cloud Function", e);
                    showError("Error al buscar recomendaciones");
                });
    }

    private void processCloudFunctionResult(com.google.firebase.functions.HttpsCallableResult result) {
        Map<String, Object> response = (Map<String, Object>) result.getData();
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) response.get("recommendations");

        if (recommendations == null || recommendations.isEmpty()) {
            showError("No encontramos matches perfectos");
            return;
        }

        loadFirstRecommendation(recommendations.get(0));
    }

    private void loadFirstRecommendation(Map<String, Object> firstRec) {
        String paseadorId = (String) firstRec.get(FirestoreConstants.FIELD_ID);

        FirebaseFirestore.getInstance()
                .collection(FirestoreConstants.COLLECTION_PASEADORES_SEARCH)
                .document(paseadorId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        showContent();
                        bindData(firstRec, doc.getData());
                        registrarEventoTelemetria("exito", paseadorId, getIntValue(firstRec.get(FirestoreConstants.FIELD_MATCH_SCORE_LOWER)), null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error cargando datos", e);
                    showError("Error al cargar recomendación");
                });
    }

    private void bindData(Map<String, Object> recommendation, Map<String, Object> paseadorData) {
        this.paseadorId = (String) recommendation.get(FirestoreConstants.FIELD_ID);
        this.matchScore = getIntValue(recommendation.get(FirestoreConstants.FIELD_MATCH_SCORE_LOWER));

        List<String> tags = (List<String>) recommendation.get(FirestoreConstants.FIELD_TAGS);

        bindBasicInfo(recommendation, paseadorData);
        bindLocation(paseadorData, tags);
        bindExperienceAndSpecialty(paseadorData, tags);
        bindMatchAndRating(recommendation, paseadorData);
        bindPhoto(paseadorData);
        bindVerifiedBadge(paseadorData);
        bindReason(recommendation);
        bindTags(tags);
        setupActionButtons(paseadorId, paseadorData);
    }

    private void bindBasicInfo(Map<String, Object> recommendation, Map<String, Object> paseadorData) {
        String nombre = (String) recommendation.get(FirestoreConstants.FIELD_NOMBRE);
        tvWalkerName.setText(nombre != null ? nombre : "Paseador");

        double precio = getDoubleValue(paseadorData.get(FirestoreConstants.FIELD_PRECIO_HORA));
        tvPrice.setText(String.format(Locale.getDefault(), "$%.1f", precio));
    }

    private void bindLocation(Map<String, Object> paseadorData, List<String> tags) {
        String ubicacion = extractUbicacion(paseadorData, tags);
        tvLocation.setText(ubicacion != null ? ubicacion : "Ubicación no especificada");
        adjustLocationLayout(ubicacion);
    }

    private String extractUbicacion(Map<String, Object> paseadorData, List<String> tags) {
        List<String> zonasPrincipales = (List<String>) paseadorData.get(FirestoreConstants.FIELD_ZONAS_PRINCIPALES);
        if (zonasPrincipales != null && !zonasPrincipales.isEmpty()) {
            return zonasPrincipales.get(0);
        }

        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && tag.contains("km")) {
                    return tag;
                }
            }
        }
        return null;
    }

    private void adjustLocationLayout(String ubicacion) {
        View llLocationExperience = getView().findViewById(R.id.llLocationExperience);
        View llExperience = getView().findViewById(R.id.llExperience);
        View tvSeparator = getView().findViewById(R.id.tvLocationSeparator);

        if (!(llLocationExperience instanceof LinearLayout) || llExperience == null) return;

        LinearLayout layoutPadre = (LinearLayout) llLocationExperience;
        if (ubicacion != null && ubicacion.length() <= 20) {
            setHorizontalLayout(layoutPadre, llExperience, tvSeparator);
        } else {
            setVerticalLayout(layoutPadre, llExperience, tvSeparator);
        }
    }

    private void setHorizontalLayout(LinearLayout parent, View experience, View separator) {
        parent.setOrientation(LinearLayout.HORIZONTAL);
        parent.setGravity(android.view.Gravity.CENTER_VERTICAL);
        if (separator != null) separator.setVisibility(View.VISIBLE);
        setMarginTop(experience, 0);
    }

    private void setVerticalLayout(LinearLayout parent, View experience, View separator) {
        parent.setOrientation(LinearLayout.VERTICAL);
        parent.setGravity(android.view.Gravity.CENTER);
        if (separator != null) separator.setVisibility(View.GONE);
        setMarginTop(experience, (int) (4 * getResources().getDisplayMetrics().density));
    }

    private void setMarginTop(View view, int marginTop) {
        if (view.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
            params.topMargin = marginTop;
            view.setLayoutParams(params);
        }
    }

    private void bindExperienceAndSpecialty(Map<String, Object> paseadorData, List<String> tags) {
        int experiencia = getIntValue(paseadorData.get(FirestoreConstants.FIELD_ANOS_EXPERIENCIA));
        tvExperience.setText(experiencia + " años exp.");

        String especialidad = extractEspecialidad(paseadorData, tags);
        tvSpecialty.setText(especialidad != null ? especialidad : "Paseador Profesional");
    }

    private String extractEspecialidad(Map<String, Object> paseadorData, List<String> tags) {
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && isSpecialtyTag(tag)) {
                    return tag;
                }
            }
        }

        List<String> tiposAceptados = (List<String>) paseadorData.get(FirestoreConstants.FIELD_TIPOS_PERRO_ACEPTADOS);
        if (tiposAceptados != null && !tiposAceptados.isEmpty()) {
            String primerTipo = tiposAceptados.get(0);
            if (primerTipo != null && !primerTipo.isEmpty()) {
                return "Especialista en " + primerTipo;
            }
        }

        String tipoMascota = (String) paseadorData.get(FirestoreConstants.FIELD_ESPECIALIDAD_TIPO_MASCOTA);
        if (tipoMascota != null && !tipoMascota.isEmpty()) {
            return "Especialista en " + tipoMascota;
        }

        return null;
    }

    private boolean isSpecialtyTag(String tag) {
        return tag.contains("Acepta") || tag.contains("🐕") ||
               tag.contains("Grande") || tag.contains("Mediano") ||
               tag.contains("Pequeño") || tag.contains("Especialista") ||
               tag.contains("perro");
    }

    private void bindMatchAndRating(Map<String, Object> recommendation, Map<String, Object> paseadorData) {
        tvMatchScore.setText(matchScore + "% Match");

        double calificacion = getDoubleValue(paseadorData.get(FirestoreConstants.FIELD_CALIFICACION_PROMEDIO));
        tvRating.setText(String.format(Locale.getDefault(), "%.1f", calificacion));

        int servicios = getIntValue(paseadorData.get(FirestoreConstants.FIELD_NUM_SERVICIOS_COMPLETADOS));
        tvReviewCount.setText("(" + servicios + " reseñas)");
    }

    private void bindPhoto(Map<String, Object> paseadorData) {
        String fotoUrl = (String) paseadorData.get(FirestoreConstants.FIELD_FOTO_URL);
        if (fotoUrl != null && !fotoUrl.isEmpty()) {
            Glide.with(this)
                    .load(fotoUrl)
                    .placeholder(R.drawable.bg_avatar_circle)
                    .error(R.drawable.bg_avatar_circle)
                    .circleCrop()
                    .into(ivProfilePhoto);
        }
    }

    private void bindVerifiedBadge(Map<String, Object> paseadorData) {
        String verificacionEstado = (String) paseadorData.get(FirestoreConstants.FIELD_VERIFICACION_ESTADO);
        boolean esVerificado = FirestoreConstants.STATUS_APROBADO.equalsIgnoreCase(verificacionEstado);

        if (ivVerifiedBadge != null) {
            ivVerifiedBadge.setVisibility(esVerificado ? View.VISIBLE : View.GONE);
            if (ivVerifiedBadge.getParent() instanceof View) {
                ((View) ivVerifiedBadge.getParent()).setVisibility(esVerificado ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void bindReason(Map<String, Object> recommendation) {
        String razonIA = (String) recommendation.get(FirestoreConstants.FIELD_RAZON_IA);
        if (tvRazonIA != null) {
            if (razonIA != null && !razonIA.isEmpty()) {
                tvRazonIA.setText(razonIA);
                tvRazonIA.setVisibility(View.VISIBLE);
            } else {
                tvRazonIA.setVisibility(View.GONE);
            }
        }
    }

    private void bindTags(List<String> tags) {
        chipGroupReasons.removeAllViews();
        if (tags != null) {
            for (String tag : tags) {
                Chip chip = new Chip(requireContext());
                chip.setText(tag);
                chip.setClickable(false);
                chip.setCheckable(false);
                chipGroupReasons.addView(chip);
            }
        }
    }

    private void setupActionButtons(String paseadorId, Map<String, Object> paseadorData) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            checkAndUpdateFavoriteStatus(currentUser, paseadorId);
        }

        setupViewProfileButton(paseadorId);
        setupFavoriteButton(currentUser, paseadorId, paseadorData);
        setupShareButton(paseadorId);
        setupNotInterestedButton(paseadorId);
    }

    private void checkAndUpdateFavoriteStatus(FirebaseUser currentUser, String paseadorId) {
        FirebaseFirestore.getInstance()
                .collection(FirestoreConstants.COLLECTION_USUARIOS)
                .document(currentUser.getUid())
                .collection(FirestoreConstants.COLLECTION_FAVORITOS)
                .document(paseadorId)
                .get()
                .addOnSuccessListener(doc -> {
                    isFavorite = doc.exists();
                    updateFavoriteIcon();
                });
    }

    private void setupViewProfileButton(String paseadorId) {
        btnViewProfile.setOnClickListener(v -> {
            registrarEventoTelemetria("ver_perfil", paseadorId, matchScore, null);
            dismiss();
            Intent intent = new Intent(requireActivity(), PerfilPaseadorActivity.class);
            intent.putExtra("paseador_id", paseadorId);
            startActivity(intent);
        });
    }

    private void setupFavoriteButton(FirebaseUser currentUser, String paseadorId, Map<String, Object> paseadorData) {
        btnFavorite.setOnClickListener(v -> {
            if (currentUser == null) return;
            toggleFavorite(currentUser, paseadorId, paseadorData);
        });
    }

    private void toggleFavorite(FirebaseUser currentUser, String paseadorId, Map<String, Object> paseadorData) {
        if (isFavorite) {
            removeFavorite(currentUser, paseadorId);
        } else {
            addFavorite(currentUser, paseadorId, paseadorData);
        }
    }

    private void removeFavorite(FirebaseUser currentUser, String paseadorId) {
        FirebaseFirestore.getInstance()
                .collection(FirestoreConstants.COLLECTION_USUARIOS)
                .document(currentUser.getUid())
                .collection(FirestoreConstants.COLLECTION_FAVORITOS)
                .document(paseadorId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    isFavorite = false;
                    updateFavoriteIcon();
                    Toast.makeText(requireContext(), "Eliminado de favoritos", Toast.LENGTH_SHORT).show();
                    registrarEventoTelemetria("quitar_favorito", paseadorId, matchScore, null);
                });
    }

    private void addFavorite(FirebaseUser currentUser, String paseadorId, Map<String, Object> paseadorData) {
        Map<String, Object> favData = new HashMap<>();
        favData.put(FirestoreConstants.FIELD_ID, paseadorId);
        favData.put(FirestoreConstants.FIELD_NOMBRE, paseadorData.get(FirestoreConstants.FIELD_NOMBRE));
        favData.put(FirestoreConstants.FIELD_FOTO_URL, paseadorData.get(FirestoreConstants.FIELD_FOTO_URL));
        favData.put("calificacion", paseadorData.get(FirestoreConstants.FIELD_CALIFICACION_PROMEDIO));
        favData.put(FirestoreConstants.FIELD_TIMESTAMP, FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection(FirestoreConstants.COLLECTION_USUARIOS)
                .document(currentUser.getUid())
                .collection(FirestoreConstants.COLLECTION_FAVORITOS)
                .document(paseadorId)
                .set(favData)
                .addOnSuccessListener(aVoid -> {
                    isFavorite = true;
                    updateFavoriteIcon();
                    Toast.makeText(requireContext(), "Agregado a favoritos", Toast.LENGTH_SHORT).show();
                    registrarEventoTelemetria("favorito", paseadorId, matchScore, null);
                });
    }

    private void setupShareButton(String paseadorId) {
        btnShare.setOnClickListener(v -> {
            registrarEventoTelemetria("compartir", paseadorId, matchScore, null);
            compartirComoImagen();
        });
    }

    private void setupNotInterestedButton(String paseadorId) {
        btnNotInterested.setOnClickListener(v -> mostrarDialogNoMeInteresa(paseadorId));
    }

    private void updateFavoriteIcon() {
        if (btnFavorite != null) {
            if (isFavorite) {
                btnFavorite.setIconResource(R.drawable.ic_favorite_filled);
                btnFavorite.setIconTint(null);
            } else {
                btnFavorite.setIconResource(R.drawable.ic_favorite);
                btnFavorite.setIconTint(android.content.res.ColorStateList.valueOf(0xFF64748B));
            }
        }
    }

    private void compartirComoImagen() {
        View rootView = getView();
        if (!validateViewsForCapture(rootView)) return;

        try {
            Log.d(TAG, "Preparando captura para compartir...");
            ViewVisibilityState state = new ViewVisibilityState(rootView);
            state.prepareForCapture();

            rootView.requestLayout();
            rootView.post(() -> captureAndShare(rootView, state));

        } catch (Exception e) {
            Log.e(TAG, "Error al preparar captura", e);
            Toast.makeText(requireContext(), "Error al compartir imagen", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean validateViewsForCapture(View rootView) {
        if (rootView == null || cardMainRecommendation == null) {
            Toast.makeText(requireContext(), "Error al capturar imagen", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "rootView o cardMainRecommendation es null");
            return false;
        }

        if (cardMainRecommendation.getVisibility() != View.VISIBLE) {
            Toast.makeText(requireContext(), "El contenido no está visible", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "cardMainRecommendation no está visible");
            return false;
        }

        if (cardMainRecommendation.getWidth() == 0 || cardMainRecommendation.getHeight() == 0) {
            Toast.makeText(requireContext(), "Error: el contenido no tiene dimensiones", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "cardMainRecommendation no tiene dimensiones");
            return false;
        }

        return true;
    }

    private void captureAndShare(View rootView, ViewVisibilityState state) {
        try {
            Log.d(TAG, "Capturando layout completo con logo...");
            Bitmap bitmap = capturarViewComoBitmap(rootView);
            Log.d(TAG, "Bitmap capturado correctamente: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            state.restore();
            guardarYCompartirImagen(bitmap);

        } catch (Exception e) {
            Log.e(TAG, "Error al capturar imagen", e);
            Toast.makeText(requireContext(), "Error al capturar imagen", Toast.LENGTH_SHORT).show();
            state.restore();
        }
    }

    private class ViewVisibilityState {
        private View rootView;
        private View ivWalkiLogo, cardAiReasoning, llActionButtons, btnNoMeInteresa;
        private View vBottomGradient, llHintText, tvHeadlineTitle, tvHeadlineSubtitle, btnClose;
        private int logoVis, aiReasoningVis, actionButtonsVis, noInterestedVis;
        private int gradientVis, hintVis, titleVis, subtitleVis, closeVis;

        ViewVisibilityState(View rootView) {
            this.rootView = rootView;
            findViews();
            saveVisibility();
        }

        private void findViews() {
            ivWalkiLogo = rootView.findViewById(R.id.ivWalkiLogoShare);
            cardAiReasoning = rootView.findViewById(R.id.cardAiReasoning);
            llActionButtons = rootView.findViewById(R.id.llActionButtons);
            btnNoMeInteresa = rootView.findViewById(R.id.btnNoMeInteresa);
            vBottomGradient = rootView.findViewById(R.id.vBottomGradient);
            llHintText = rootView.findViewById(R.id.llHintText);
            tvHeadlineTitle = rootView.findViewById(R.id.tvHeadlineTitle);
            tvHeadlineSubtitle = rootView.findViewById(R.id.tvHeadlineSubtitle);
            btnClose = rootView.findViewById(R.id.btnClose);
        }

        private void saveVisibility() {
            logoVis = getVisibility(ivWalkiLogo);
            aiReasoningVis = getVisibility(cardAiReasoning);
            actionButtonsVis = getVisibility(llActionButtons);
            noInterestedVis = getVisibility(btnNoMeInteresa);
            gradientVis = getVisibility(vBottomGradient);
            hintVis = getVisibility(llHintText);
            titleVis = getVisibility(tvHeadlineTitle);
            subtitleVis = getVisibility(tvHeadlineSubtitle);
            closeVis = getVisibility(btnClose);
        }

        private int getVisibility(View view) {
            return view != null ? view.getVisibility() : View.GONE;
        }

        void prepareForCapture() {
            setVisibility(ivWalkiLogo, View.VISIBLE);
            setVisibility(cardAiReasoning, View.GONE);
            setVisibility(llActionButtons, View.GONE);
            setVisibility(btnNoMeInteresa, View.GONE);
            setVisibility(vBottomGradient, View.GONE);
            setVisibility(llHintText, View.GONE);
            setVisibility(tvHeadlineTitle, View.GONE);
            setVisibility(tvHeadlineSubtitle, View.GONE);
            setVisibility(btnClose, View.GONE);

            if (cardMainRecommendation != null && ivWalkiLogo != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) cardMainRecommendation.getLayoutParams();
                params.topToBottom = R.id.ivWalkiLogoShare;
                cardMainRecommendation.setLayoutParams(params);
            }
        }

        void restore() {
            setVisibility(ivWalkiLogo, logoVis);
            setVisibility(cardAiReasoning, aiReasoningVis);
            setVisibility(llActionButtons, actionButtonsVis);
            setVisibility(btnNoMeInteresa, noInterestedVis);
            setVisibility(vBottomGradient, gradientVis);
            setVisibility(llHintText, hintVis);
            setVisibility(tvHeadlineTitle, titleVis);
            setVisibility(tvHeadlineSubtitle, subtitleVis);
            setVisibility(btnClose, closeVis);

            if (cardMainRecommendation != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) cardMainRecommendation.getLayoutParams();
                params.topToBottom = R.id.tvHeadlineSubtitle;
                cardMainRecommendation.setLayoutParams(params);
            }
        }

        private void setVisibility(View view, int visibility) {
            if (view != null) view.setVisibility(visibility);
        }
    }

    private void guardarYCompartirImagen(Bitmap bitmap) {
        try {
            File cachePath = new File(requireContext().getCacheDir(), "images");
            cachePath.mkdirs();
            File imageFile = new File(cachePath, "recomendacion_ia_" + System.currentTimeMillis() + ".png");

            Log.d(TAG, "Guardando imagen en: " + imageFile.getAbsolutePath());

            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Log.d(TAG, "Imagen guardada correctamente. Tamaño: " + imageFile.length() + " bytes");

            String authority = requireContext().getPackageName() + ".provider";
            Uri imageUri = FileProvider.getUriForFile(requireContext(), authority, imageFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "🤖 ¡La IA de Walki me recomendó este paseador perfecto para mi perro!\n\nDescarga la app: https://walki.app");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Compartir recomendación"));
            Toast.makeText(requireContext(), "Imagen capturada correctamente", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Log.e(TAG, "Error al compartir imagen", e);
            Toast.makeText(requireContext(), "Error al compartir imagen", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap capturarViewComoBitmap(View view) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(view.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(view.getHeight(), View.MeasureSpec.EXACTLY)
        );
        view.layout(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());

        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.WHITE);
        view.draw(canvas);

        return bitmap;
    }

    private void mostrarDialogNoMeInteresa(String paseadorId) {
        String[] razones = {
                "Muy caro",
                "Muy lejos",
                "Prefiero más experiencia",
                "No me convence su perfil",
                "Otro"
        };

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("¿Por qué no te interesa?")
                .setItems(razones, (dialog, which) -> {
                    String razonSeleccionada = razones[which];
                    registrarEventoTelemetria("no_me_interesa", paseadorId, matchScore, razonSeleccionada);
                    dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void registrarEventoTelemetria(String evento, String paseadorId, Integer matchScore, String errorMsg) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        Map<String, Object> telemetria = new HashMap<>();
        telemetria.put(FirestoreConstants.FIELD_USER_ID, currentUser.getUid());
        telemetria.put(FirestoreConstants.FIELD_TIMESTAMP, FieldValue.serverTimestamp());
        telemetria.put(FirestoreConstants.FIELD_EVENTO, evento);

        if (paseadorId != null) telemetria.put(FirestoreConstants.FIELD_PASEADOR_ID, paseadorId);
        if (matchScore != null) telemetria.put(FirestoreConstants.FIELD_MATCH_SCORE, matchScore);
        if (errorMsg != null) telemetria.put(FirestoreConstants.FIELD_ERROR_MSG, errorMsg);

        FirebaseFirestore.getInstance()
                .collection(FirestoreConstants.COLLECTION_RECOMENDACIONES_IA_LOGS)
                .add(telemetria)
                .addOnSuccessListener(doc -> Log.d(TAG, " Telemetría: " + evento))
                .addOnFailureListener(e -> Log.e(TAG, "Error telemetría", e));
    }

    private int getIntValue(Object obj) {
        if (obj instanceof Long) return ((Long) obj).intValue();
        else if (obj instanceof Double) return ((Double) obj).intValue();
        else if (obj instanceof Integer) return (Integer) obj;
        return 0;
    }

    private double getDoubleValue(Object obj) {
        if (obj instanceof Double) return (Double) obj;
        else if (obj instanceof Long) return ((Long) obj).doubleValue();
        else if (obj instanceof Integer) return ((Integer) obj).doubleValue();
        return 0.0;
    }
}
