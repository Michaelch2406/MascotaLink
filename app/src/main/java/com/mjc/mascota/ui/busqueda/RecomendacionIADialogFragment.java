package com.mjc.mascota.ui.busqueda;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.mjc.mascotalink.PerfilPaseadorActivity;
import com.mjc.mascotalink.R;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DialogFragment para mostrar recomendación IA como overlay flotante
 * Reemplaza el BottomSheet con un diseño más intuitivo
 */
public class RecomendacionIADialogFragment extends DialogFragment {

    private static final String TAG = "RecomendacionIADialog";

    // Views
    private View layoutSkeleton;
    private View layoutContent;
    private LinearLayout layoutError;
    private ImageButton btnClose;

    // Content views
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

    // Error views
    private TextView tvErrorMessage;
    private MaterialButton btnRetry;

    // Data
    private String paseadorId;
    private int matchScore;

    // Location
    private FusedLocationProviderClient fusedLocationClient;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        // Hacer que el dialog sea fullscreen con fondo transparente
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

        // Inicializar cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        initViews(view);
        setupListeners();

        // Iniciar búsqueda de recomendaciones
        buscarRecomendaciones();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Hacer el dialog fullscreen
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        }
    }

    private void initViews(View view) {
        // Layouts de estados
        layoutSkeleton = view.findViewById(R.id.layoutSkeleton);
        layoutContent = view.findViewById(R.id.layoutContent);
        layoutError = view.findViewById(R.id.layoutError);
        btnClose = view.findViewById(R.id.btnClose);

        // Content views (del item incluido)
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

        // Error views
        tvErrorMessage = view.findViewById(R.id.tvErrorMessage);
        btnRetry = view.findViewById(R.id.btnRetry);
    }

    private void setupListeners() {
        // Cerrar al tocar el fondo oscuro
        if (getView() != null) {
            getView().setOnClickListener(v -> dismiss());
        }

        // Evitar que se cierre al tocar la card
        View cardView = getView().findViewById(R.id.cardMain);
        if (cardView != null) {
            cardView.setOnClickListener(v -> {
                // No hacer nada, solo prevenir propagación
            });
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

        // 🆕 MEJORA #8: Telemetría
        registrarEventoTelemetria("error", null, null, message);
    }

    private void buscarRecomendaciones() {
        showSkeleton();

        // 🆕 MEJORA #8: Telemetría
        registrarEventoTelemetria("solicitud", null, null, null);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showError("Debes iniciar sesión");
            return;
        }

        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Obtener datos del usuario y mascota (reutilizando lógica de RecomendacionIAHelper)
        db.collection("usuarios").document(userId).get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        showError("No se encontró tu perfil");
                        return;
                    }

                    Map<String, Object> userData = userDoc.getData();

                    // Obtener mascotas
                    db.collection("duenos").document(userId).collection("mascotas")
                            .get()
                            .addOnSuccessListener(petSnapshot -> {
                                if (petSnapshot.isEmpty()) {
                                    showError("Registra tu mascota primero");
                                    return;
                                }

                                List<DocumentSnapshot> mascotas = petSnapshot.getDocuments();

                                if (mascotas.size() == 1) {
                                    procesarRecomendacionConMascota(mascotas.get(0).getData(), userData);
                                } else {
                                    // TODO: Mostrar selector si tiene múltiples mascotas
                                    // Por ahora usar la primera
                                    procesarRecomendacionConMascota(mascotas.get(0).getData(), userData);
                                }
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

    private void procesarRecomendacionConMascota(Map<String, Object> petData, Map<String, Object> userData) {
        // Obtener ubicación - intentar múltiples fuentes
        Map<String, Object> userLocation = new HashMap<>();

        // Intentar 1: ubicacion_principal.geopoint
        Object ubicacionObj = userData.get("ubicacion_principal");
        Log.d(TAG, "🔍 ubicacion_principal: " + ubicacionObj);

        if (ubicacionObj instanceof Map) {
            Map<String, Object> ubicacion = (Map<String, Object>) ubicacionObj;
            Object geopointObj = ubicacion.get("geopoint");
            Log.d(TAG, "🔍 geopoint: " + geopointObj);

            if (geopointObj instanceof GeoPoint) {
                GeoPoint geoPoint = (GeoPoint) geopointObj;
                userLocation.put("latitude", geoPoint.getLatitude());
                userLocation.put("longitude", geoPoint.getLongitude());
                Log.d(TAG, "✅ Ubicación obtenida de ubicacion_principal.geopoint");
            }
        }

        // Intentar 2: ubicacion directa (formato antiguo)
        if (userLocation.isEmpty()) {
            Object ubicacionDirecta = userData.get("ubicacion");
            Log.d(TAG, "🔍 ubicacion directa: " + ubicacionDirecta);

            if (ubicacionDirecta instanceof GeoPoint) {
                GeoPoint geoPoint = (GeoPoint) ubicacionDirecta;
                userLocation.put("latitude", geoPoint.getLatitude());
                userLocation.put("longitude", geoPoint.getLongitude());
                Log.d(TAG, "✅ Ubicación obtenida de ubicacion directa");
            }
        }

        // Intentar 3: direccion_coordenadas
        if (userLocation.isEmpty()) {
            Object direccionCoords = userData.get("direccion_coordenadas");
            Log.d(TAG, "🔍 direccion_coordenadas: " + direccionCoords);

            if (direccionCoords instanceof GeoPoint) {
                GeoPoint geoPoint = (GeoPoint) direccionCoords;
                userLocation.put("latitude", geoPoint.getLatitude());
                userLocation.put("longitude", geoPoint.getLongitude());
                Log.d(TAG, "✅ Ubicación obtenida de direccion_coordenadas");
            }
        }

        // Intentar 4: Campos separados lat/lng
        if (userLocation.isEmpty()) {
            Object lat = userData.get("latitude");
            Object lng = userData.get("longitude");
            Log.d(TAG, "🔍 lat/lng separados: " + lat + ", " + lng);

            if (lat != null && lng != null) {
                userLocation.put("latitude", lat);
                userLocation.put("longitude", lng);
                Log.d(TAG, "✅ Ubicación obtenida de lat/lng separados");
            }
        }

        Log.d(TAG, "📍 userLocation final: " + userLocation);

        // Si no hay ubicación guardada, obtener ubicación actual del GPS
        if (!userLocation.containsKey("latitude") || !userLocation.containsKey("longitude")) {
            Log.w(TAG, "⚠️ No hay ubicación guardada, obteniendo ubicación actual del GPS...");
            obtenerUbicacionActualYContinuar(userData, petData);
            return;
        }

        // Llamar Cloud Function
        llamarCloudFunction(sanitizeData(userData), sanitizeData(petData), userLocation);
    }

    /**
     * Obtiene la ubicación actual del GPS en tiempo real
     */
    private void obtenerUbicacionActualYContinuar(Map<String, Object> userData, Map<String, Object> petData) {
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            Map<String, Object> userLocation = new HashMap<>();
                            userLocation.put("latitude", location.getLatitude());
                            userLocation.put("longitude", location.getLongitude());

                            Log.d(TAG, "✅ Ubicación actual obtenida del GPS: " +
                                  location.getLatitude() + ", " + location.getLongitude());

                            // Continuar con la recomendación
                            llamarCloudFunction(sanitizeData(userData), sanitizeData(petData), userLocation);
                        } else {
                            Log.e(TAG, "❌ No se pudo obtener ubicación actual del GPS");
                            showError("No se pudo obtener tu ubicación actual. Por favor activa el GPS y los permisos de ubicación.");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Error al obtener ubicación del GPS: " + e.getMessage(), e);
                        showError("Error al obtener tu ubicación: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Sin permisos de ubicación: " + e.getMessage(), e);
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
                .addOnSuccessListener(result -> {
                    Map<String, Object> response = (Map<String, Object>) result.getData();
                    List<Map<String, Object>> recommendations =
                            (List<Map<String, Object>>) response.get("recommendations");

                    if (recommendations == null || recommendations.isEmpty()) {
                        showError("No encontramos matches perfectos");
                        return;
                    }

                    // Cargar detalles del primer recomendado
                    Map<String, Object> firstRec = recommendations.get(0);
                    String paseadorId = (String) firstRec.get("id");

                    FirebaseFirestore.getInstance()
                            .collection("paseadores_search")
                            .document(paseadorId)
                            .get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    showContent();
                                    bindData(firstRec, doc.getData());

                                    // 🆕 MEJORA #8: Telemetría
                                    registrarEventoTelemetria("exito", paseadorId, getIntValue(firstRec.get("match_score")), null);
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error cargando datos", e);
                                showError("Error al cargar recomendación");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error llamando Cloud Function", e);
                    showError("Error al buscar recomendaciones");
                });
    }

    private void bindData(Map<String, Object> recommendation, Map<String, Object> paseadorData) {
        // Guardar para telemetría
        this.paseadorId = (String) recommendation.get("id");
        this.matchScore = getIntValue(recommendation.get("match_score"));

        // Obtener tags una sola vez para usar en varios lugares
        List<String> tags = (List<String>) recommendation.get("tags");

        // Nombre
        String nombre = (String) recommendation.get("nombre");
        tvWalkerName.setText(nombre != null ? nombre : "Paseador");

        // Ubicación
        String ubicacion = (String) paseadorData.get("ubicacion_texto");
        if (tvLocation != null) {
            tvLocation.setText(ubicacion != null ? ubicacion : "No especificada");
        }

        // Experiencia
        int experiencia = getIntValue(paseadorData.get("anos_experiencia"));
        if (tvExperience != null) {
            tvExperience.setText(experiencia + " años exp.");
        }

        // Especialidad (de los tags de la recomendación o del perfil)
        if (tvSpecialty != null && tags != null && !tags.isEmpty()) {
            tvSpecialty.setText(tags.get(0)); // Primer tag como especialidad
        } else if (tvSpecialty != null) {
            String tipoMascota = (String) paseadorData.get("especialidad_tipo_mascota");
            tvSpecialty.setText(tipoMascota != null ? "Especialista en " + tipoMascota : "Paseador Profesional");
        }

        // Match Score
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

        // Razón de IA (explicación detallada de Gemini)
        String razonIA = (String) recommendation.get("razon_ia");
        if (tvRazonIA != null) {
            if (razonIA != null && !razonIA.isEmpty()) {
                tvRazonIA.setText(razonIA);
                tvRazonIA.setVisibility(View.VISIBLE);
            } else {
                tvRazonIA.setVisibility(View.GONE);
            }
        }

        // Foto
        String fotoUrl = (String) paseadorData.get("foto_url");
        if (fotoUrl != null && !fotoUrl.isEmpty()) {
            Glide.with(this)
                    .load(fotoUrl)
                    .placeholder(R.drawable.bg_avatar_circle)
                    .error(R.drawable.bg_avatar_circle)
                    .circleCrop()
                    .into(ivProfilePhoto);
        }

        // Badge verificado
        String verificacionEstado = (String) paseadorData.get("verificacion_estado");
        ivVerifiedBadge.setVisibility("verificado".equals(verificacionEstado) ? View.VISIBLE : View.GONE);

        // Tags/razones (ya obtenidos al inicio del método)
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

        setupActionButtons(paseadorId, paseadorData);
    }

    private void setupActionButtons(String paseadorId, Map<String, Object> paseadorData) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Ver Perfil
        btnViewProfile.setOnClickListener(v -> {
            // 🆕 MEJORA #8: Telemetría
            registrarEventoTelemetria("ver_perfil", paseadorId, matchScore, null);

            dismiss();
            Intent intent = new Intent(requireActivity(), PerfilPaseadorActivity.class);
            intent.putExtra("paseador_id", paseadorId);
            startActivity(intent);
        });

        // Favorito
        btnFavorite.setOnClickListener(v -> {
            if (currentUser == null) return;

            Map<String, Object> favData = new HashMap<>();
            favData.put("id", paseadorId);
            favData.put("nombre", paseadorData.get("nombre"));
            favData.put("foto_url", paseadorData.get("foto_url"));
            favData.put("calificacion", paseadorData.get("calificacion_promedio"));
            favData.put("timestamp", FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance()
                    .collection("usuarios")
                    .document(currentUser.getUid())
                    .collection("favoritos")
                    .document(paseadorId)
                    .set(favData)
                    .addOnSuccessListener(aVoid -> {
                        // 🆕 MEJORA #8: Telemetría
                        registrarEventoTelemetria("favorito", paseadorId, matchScore, null);
                    });
        });

        // Compartir
        btnShare.setOnClickListener(v -> {
            // 🆕 MEJORA #8: Telemetría
            registrarEventoTelemetria("compartir", paseadorId, matchScore, null);

            String shareText = "¡Mira este paseador que encontré en Walki!\n\n" +
                    "Nombre: " + paseadorData.get("nombre") + "\n" +
                    "Calificación: " + String.format(Locale.getDefault(), "%.1f", getDoubleValue(paseadorData.get("calificacion_promedio"))) + " ⭐\n\n" +
                    "Descarga la app: https://walki.app";

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            sendIntent.setType("text/plain");

            Intent shareIntent = Intent.createChooser(sendIntent, "Compartir paseador");
            startActivity(shareIntent);
        });

        // 🆕 MEJORA A: No me interesa
        btnNotInterested.setOnClickListener(v -> {
            mostrarDialogNoMeInteresa(paseadorId);
        });
    }

    /**
     * 🆕 MEJORA A: Dialog para preguntar razón de "No me interesa"
     */
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

                    // 🆕 MEJORA #8: Telemetría con razón
                    registrarEventoTelemetria("no_me_interesa", paseadorId, matchScore, razonSeleccionada);

                    dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * 🆕 MEJORA #8: Registrar evento de telemetría
     */
    private void registrarEventoTelemetria(String evento, String paseadorId, Integer matchScore, String errorMsg) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        Map<String, Object> telemetria = new HashMap<>();
        telemetria.put("userId", currentUser.getUid());
        telemetria.put("timestamp", FieldValue.serverTimestamp());
        telemetria.put("evento", evento);

        if (paseadorId != null) telemetria.put("paseadorId", paseadorId);
        if (matchScore != null) telemetria.put("matchScore", matchScore);
        if (errorMsg != null) telemetria.put("errorMsg", errorMsg);

        FirebaseFirestore.getInstance()
                .collection("recomendaciones_ia_logs")
                .add(telemetria)
                .addOnSuccessListener(doc -> Log.d(TAG, "📊 Telemetría: " + evento))
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
