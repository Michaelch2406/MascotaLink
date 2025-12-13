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
import com.mjc.mascotalink.PerfilPaseadorActivity;
import com.mjc.mascotalink.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private boolean isFavorite = false;

    // Location
    private FusedLocationProviderClient fusedLocationClient;

    // Card view para compartir como imagen
    private View cardMainRecommendation;

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

        // Card principal para compartir como imagen
        cardMainRecommendation = view.findViewById(R.id.cardMainRecommendation);

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

        // Ubicación (intentar de varias fuentes)
        String ubicacion = null;

        // Intento 1: zonas_principales (primera zona)
        List<String> zonasPrincipales = (List<String>) paseadorData.get("zonas_principales");
        if (zonasPrincipales != null && !zonasPrincipales.isEmpty()) {
            ubicacion = zonasPrincipales.get(0);
        }

        // Intento 2: extraer de los tags de distancia
        if (ubicacion == null && tags != null && !tags.isEmpty()) {
            for (String tag : tags) {
                if (tag.contains("km")) {
                    ubicacion = tag; // ej: "📍 2.5km"
                    break;
                }
            }
        }

        if (tvLocation != null) {
            tvLocation.setText(ubicacion != null ? ubicacion : "Ubicación no especificada");

            // Adaptar diseño según longitud de ubicación
            View llLocationExperience = getView().findViewById(R.id.llLocationExperience);
            View llExperience = getView().findViewById(R.id.llExperience);
            View tvSeparator = getView().findViewById(R.id.tvLocationSeparator);

            if (llLocationExperience instanceof LinearLayout && llExperience != null) {
                LinearLayout layoutPadre = (LinearLayout) llLocationExperience;

                // Si la ubicación es CORTA (<=20 caracteres), usar modo horizontal (1 línea)
                if (ubicacion != null && ubicacion.length() <= 20) {
                    layoutPadre.setOrientation(LinearLayout.HORIZONTAL);
                    layoutPadre.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    // Mostrar separador "•"
                    if (tvSeparator != null) {
                        tvSeparator.setVisibility(View.VISIBLE);
                    }

                    // Quitar margen top de experiencia (están en la misma línea)
                    if (llExperience.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) llExperience.getLayoutParams();
                        params.topMargin = 0;
                        llExperience.setLayoutParams(params);
                    }
                } else {
                    // Si la ubicación es LARGA (>20 caracteres), usar modo vertical (2 líneas)
                    layoutPadre.setOrientation(LinearLayout.VERTICAL);
                    layoutPadre.setGravity(android.view.Gravity.CENTER);

                    // Ocultar separador "•"
                    if (tvSeparator != null) {
                        tvSeparator.setVisibility(View.GONE);
                    }

                    // Agregar margen top de experiencia (en línea separada)
                    if (llExperience.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) llExperience.getLayoutParams();
                        params.topMargin = (int) (4 * getResources().getDisplayMetrics().density); // 4dp
                        llExperience.setLayoutParams(params);
                    }
                }
            }
        }

        // Experiencia
        int experiencia = getIntValue(paseadorData.get("anos_experiencia"));
        if (tvExperience != null) {
            tvExperience.setText(experiencia + " años exp.");
        }

        // Especialidad (buscar tag apropiado o usar tipos_perro_aceptados)
        if (tvSpecialty != null) {
            String especialidad = null;

            // Intento 1: Buscar en los tags uno que sea sobre tipo de perro
            if (tags != null && !tags.isEmpty()) {
                for (String tag : tags) {
                    // Validar que el tag no sea null antes de usar contains
                    if (tag != null && (tag.contains("Acepta") || tag.contains("🐕") ||
                        tag.contains("Grande") || tag.contains("Mediano") || tag.contains("Pequeño") ||
                        tag.contains("Especialista") || tag.contains("perro"))) {
                        especialidad = tag;
                        break;
                    }
                }
            }

            // Intento 2: Usar tipos_perro_aceptados del paseador
            if (especialidad == null) {
                List<String> tiposAceptados = (List<String>) paseadorData.get("tipos_perro_aceptados");
                if (tiposAceptados != null && !tiposAceptados.isEmpty()) {
                    String primerTipo = tiposAceptados.get(0);
                    // Validar que no sea null ni vacío
                    if (primerTipo != null && !primerTipo.isEmpty()) {
                        especialidad = "Especialista en " + primerTipo;
                    }
                }
            }

            // Intento 3: Usar especialidad_tipo_mascota si existe
            if (especialidad == null) {
                String tipoMascota = (String) paseadorData.get("especialidad_tipo_mascota");
                if (tipoMascota != null && !tipoMascota.isEmpty()) {
                    especialidad = "Especialista en " + tipoMascota;
                }
            }

            // Default: Paseador Profesional
            tvSpecialty.setText(especialidad != null ? especialidad : "Paseador Profesional");
        }

        // Match Score
        tvMatchScore.setText(matchScore + "% Match");

        // Rating
        double calificacion = getDoubleValue(paseadorData.get("calificacion_promedio"));
        tvRating.setText(String.format(Locale.getDefault(), "%.1f", calificacion));

        // Reseñas
        int servicios = getIntValue(paseadorData.get("num_servicios_completados"));
        tvReviewCount.setText("(" + servicios + " reseñas)");

        // Precio (solo el valor, el "/h" está en un TextView separado en el XML)
        double precio = getDoubleValue(paseadorData.get("precio_hora"));
        tvPrice.setText(String.format(Locale.getDefault(), "$%.1f", precio));

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

        // Badge verificado (verificar si es "APROBADO" - el valor en mayúscula que viene de la BD)
        String verificacionEstado = (String) paseadorData.get("verificacion_estado");
        if (ivVerifiedBadge != null) {
            boolean esVerificado = "APROBADO".equalsIgnoreCase(verificacionEstado);
            ivVerifiedBadge.setVisibility(esVerificado ? View.VISIBLE : View.GONE);
            // También controlar la visibilidad del CardView padre si tiene visibilidad propia
            if (ivVerifiedBadge.getParent() instanceof View) {
                ((View) ivVerifiedBadge.getParent()).setVisibility(esVerificado ? View.VISIBLE : View.GONE);
            }
        }

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

        // Verificar si ya está en favoritos y actualizar icono
        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                    .collection("usuarios")
                    .document(currentUser.getUid())
                    .collection("favoritos")
                    .document(paseadorId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        isFavorite = doc.exists();
                        updateFavoriteIcon();
                    });
        }

        // Ver Perfil
        btnViewProfile.setOnClickListener(v -> {
            registrarEventoTelemetria("ver_perfil", paseadorId, matchScore, null);

            dismiss();
            Intent intent = new Intent(requireActivity(), PerfilPaseadorActivity.class);
            intent.putExtra("paseador_id", paseadorId);
            startActivity(intent);
        });

        // Favorito - Toggle (agregar o quitar)
        btnFavorite.setOnClickListener(v -> {
            if (currentUser == null) return;

            if (isFavorite) {
                // Quitar de favoritos
                FirebaseFirestore.getInstance()
                        .collection("usuarios")
                        .document(currentUser.getUid())
                        .collection("favoritos")
                        .document(paseadorId)
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            isFavorite = false;
                            updateFavoriteIcon();
                            Toast.makeText(requireContext(), "Eliminado de favoritos", Toast.LENGTH_SHORT).show();
                            registrarEventoTelemetria("quitar_favorito", paseadorId, matchScore, null);
                        });
            } else {
                // Agregar a favoritos
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
                            isFavorite = true;
                            updateFavoriteIcon();
                            Toast.makeText(requireContext(), "Agregado a favoritos", Toast.LENGTH_SHORT).show();
                            registrarEventoTelemetria("favorito", paseadorId, matchScore, null);
                        });
            }
        });

        // Compartir - Capturar y compartir como imagen
        btnShare.setOnClickListener(v -> {
            registrarEventoTelemetria("compartir", paseadorId, matchScore, null);
            compartirComoImagen();
        });

        // No me interesa
        btnNotInterested.setOnClickListener(v -> {
            mostrarDialogNoMeInteresa(paseadorId);
        });
    }

    /**
     * Actualiza el icono del botón de favoritos según el estado
     */
    private void updateFavoriteIcon() {
        if (btnFavorite != null) {
            if (isFavorite) {
                // Corazón lleno en rojo
                btnFavorite.setIconResource(R.drawable.ic_favorite_filled);
                btnFavorite.setIconTint(null); // Usar el color del drawable (rojo)
            } else {
                // Corazón vacío en gris
                btnFavorite.setIconResource(R.drawable.ic_favorite);
                btnFavorite.setIconTint(android.content.res.ColorStateList.valueOf(0xFF64748B)); // Gris
            }
        }
    }

    /**
     * Captura el CardView como imagen y la comparte
     */
    private void compartirComoImagen() {
        View rootView = getView();
        if (rootView == null || cardMainRecommendation == null) {
            Toast.makeText(requireContext(), "Error al capturar imagen", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "rootView o cardMainRecommendation es null");
            return;
        }

        // Verificar que el view esté visible y tenga dimensiones
        if (cardMainRecommendation.getVisibility() != View.VISIBLE) {
            Toast.makeText(requireContext(), "El contenido no está visible", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "cardMainRecommendation no está visible");
            return;
        }

        if (cardMainRecommendation.getWidth() == 0 || cardMainRecommendation.getHeight() == 0) {
            Toast.makeText(requireContext(), "Error: el contenido no tiene dimensiones", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "cardMainRecommendation no tiene dimensiones: " + cardMainRecommendation.getWidth() + "x" + cardMainRecommendation.getHeight());
            return;
        }

        try {
            Log.d(TAG, "Preparando captura para compartir...");

            // Obtener referencias a los elementos
            View ivWalkiLogo = rootView.findViewById(R.id.ivWalkiLogoShare);
            View cardAiReasoning = rootView.findViewById(R.id.cardAiReasoning);
            View llActionButtons = rootView.findViewById(R.id.llActionButtons);
            View btnNoMeInteresa = rootView.findViewById(R.id.btnNoMeInteresa);
            View vBottomGradient = rootView.findViewById(R.id.vBottomGradient);
            View llHintText = rootView.findViewById(R.id.llHintText);
            View tvHeadlineTitle = rootView.findViewById(R.id.tvHeadlineTitle);
            View tvHeadlineSubtitle = rootView.findViewById(R.id.tvHeadlineSubtitle);

            // Guardar visibilidad original
            int logoVis = ivWalkiLogo != null ? ivWalkiLogo.getVisibility() : View.GONE;
            int aiReasoningVis = cardAiReasoning != null ? cardAiReasoning.getVisibility() : View.GONE;
            int actionButtonsVis = llActionButtons != null ? llActionButtons.getVisibility() : View.GONE;
            int noInterestedVis = btnNoMeInteresa != null ? btnNoMeInteresa.getVisibility() : View.GONE;
            int gradientVis = vBottomGradient != null ? vBottomGradient.getVisibility() : View.GONE;
            int hintVis = llHintText != null ? llHintText.getVisibility() : View.GONE;
            int titleVis = tvHeadlineTitle != null ? tvHeadlineTitle.getVisibility() : View.GONE;
            int subtitleVis = tvHeadlineSubtitle != null ? tvHeadlineSubtitle.getVisibility() : View.GONE;

            // MOSTRAR logo, OCULTAR elementos no deseados
            if (ivWalkiLogo != null) ivWalkiLogo.setVisibility(View.VISIBLE);
            if (cardAiReasoning != null) cardAiReasoning.setVisibility(View.GONE);
            if (llActionButtons != null) llActionButtons.setVisibility(View.GONE);
            if (btnNoMeInteresa != null) btnNoMeInteresa.setVisibility(View.GONE);
            if (vBottomGradient != null) vBottomGradient.setVisibility(View.GONE);
            if (llHintText != null) llHintText.setVisibility(View.GONE);
            if (tvHeadlineTitle != null) tvHeadlineTitle.setVisibility(View.GONE);
            if (tvHeadlineSubtitle != null) tvHeadlineSubtitle.setVisibility(View.GONE);

            // Forzar re-layout y esperar a que se complete
            rootView.requestLayout();
            rootView.post(() -> {
                try {
                    Log.d(TAG, "Capturando layout completo con logo...");

                    // Capturar todo el layout raíz (incluye logo + card)
                    Bitmap bitmap = capturarViewComoBitmap(rootView);

                    Log.d(TAG, "Bitmap capturado correctamente: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                    // Restaurar visibilidad original
                    if (ivWalkiLogo != null) ivWalkiLogo.setVisibility(logoVis);
                    if (cardAiReasoning != null) cardAiReasoning.setVisibility(aiReasoningVis);
                    if (llActionButtons != null) llActionButtons.setVisibility(actionButtonsVis);
                    if (btnNoMeInteresa != null) btnNoMeInteresa.setVisibility(noInterestedVis);
                    if (vBottomGradient != null) vBottomGradient.setVisibility(gradientVis);
                    if (llHintText != null) llHintText.setVisibility(hintVis);
                    if (tvHeadlineTitle != null) tvHeadlineTitle.setVisibility(titleVis);
                    if (tvHeadlineSubtitle != null) tvHeadlineSubtitle.setVisibility(subtitleVis);

                    // Continuar con guardar y compartir
                    guardarYCompartirImagen(bitmap);

                } catch (Exception e) {
                    Log.e(TAG, "Error al capturar imagen", e);
                    Toast.makeText(requireContext(), "Error al capturar imagen", Toast.LENGTH_SHORT).show();

                    // Restaurar visibilidad en caso de error
                    if (ivWalkiLogo != null) ivWalkiLogo.setVisibility(logoVis);
                    if (cardAiReasoning != null) cardAiReasoning.setVisibility(aiReasoningVis);
                    if (llActionButtons != null) llActionButtons.setVisibility(actionButtonsVis);
                    if (btnNoMeInteresa != null) btnNoMeInteresa.setVisibility(noInterestedVis);
                    if (vBottomGradient != null) vBottomGradient.setVisibility(gradientVis);
                    if (llHintText != null) llHintText.setVisibility(hintVis);
                    if (tvHeadlineTitle != null) tvHeadlineTitle.setVisibility(titleVis);
                    if (tvHeadlineSubtitle != null) tvHeadlineSubtitle.setVisibility(subtitleVis);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error al preparar captura", e);
            Toast.makeText(requireContext(), "Error al compartir imagen", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Guarda el bitmap y lo comparte
     */
    private void guardarYCompartirImagen(Bitmap bitmap) {
        try {

            // Guardar el bitmap temporalmente
            File cachePath = new File(requireContext().getCacheDir(), "images");
            cachePath.mkdirs();
            File imageFile = new File(cachePath, "recomendacion_ia_" + System.currentTimeMillis() + ".png");

            Log.d(TAG, "Guardando imagen en: " + imageFile.getAbsolutePath());

            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Log.d(TAG, "Imagen guardada correctamente. Tamaño: " + imageFile.length() + " bytes");

            // Obtener URI usando FileProvider
            String authority = requireContext().getPackageName() + ".provider";
            Log.d(TAG, "Usando authority: " + authority);

            Uri imageUri = FileProvider.getUriForFile(
                    requireContext(),
                    authority,
                    imageFile
            );

            Log.d(TAG, "URI generado: " + imageUri.toString());

            // Compartir la imagen
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "🤖 ¡La IA de Walki me recomendó este paseador perfecto para mi perro!\n\nDescarga la app: https://walki.app");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Log.d(TAG, "Abriendo selector de compartir...");
            startActivity(Intent.createChooser(shareIntent, "Compartir recomendación"));

            Toast.makeText(requireContext(), "Imagen capturada correctamente", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Log.e(TAG, "Error al compartir imagen", e);
            Toast.makeText(requireContext(), "Error al compartir imagen", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Captura un View como Bitmap con fondo blanco y mejor calidad
     */
    private Bitmap capturarViewComoBitmap(View view) {
        // Asegurar que el view esté completamente medido y dibujado
        view.measure(
                View.MeasureSpec.makeMeasureSpec(view.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(view.getHeight(), View.MeasureSpec.EXACTLY)
        );
        view.layout(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());

        // Crear bitmap con calidad alta
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Dibujar fondo blanco para evitar transparencias
        canvas.drawColor(android.graphics.Color.WHITE);

        // Dibujar el view
        view.draw(canvas);

        return bitmap;
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
