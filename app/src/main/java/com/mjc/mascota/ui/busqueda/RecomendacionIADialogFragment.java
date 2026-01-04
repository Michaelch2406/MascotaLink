package com.mjc.mascota.ui.busqueda;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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
import com.mjc.mascotalink.utils.InputUtils;

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

    private androidx.viewpager2.widget.ViewPager2 viewPagerRecommendations;
    private LinearLayout layoutPageIndicator;
    private RecomendacionIAPagerAdapter pagerAdapter;

    private TextView tvErrorMessage;
    private MaterialButton btnRetry;
    private ImageView ivLoadingIcon;
    private TextView tvLoadingMessage;
    private TextView tvLoadingSubtext;
    private MaterialButton btnCancelSearch;

    private FusedLocationProviderClient fusedLocationClient;
    private String tamanoPetActual; // Guardar el tamaño del perro para usarlo al mostrar recomendaciones

    // Tracking de tareas asíncronas para cancelarlas en onDestroyView y prevenir memory leaks
    private com.google.android.gms.tasks.Task<Location> locationTask;
    private com.google.android.gms.tasks.Task<com.google.firebase.functions.HttpsCallableResult> cloudFunctionTask;

    // Handler para timeout de GPS
    private android.os.Handler locationTimeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable locationTimeoutRunnable;
    private static final long LOCATION_TIMEOUT_MS = 10000; // 10 segundos

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cleanup para prevenir memory leaks
        if (locationTask != null && !locationTask.isComplete()) {
            Log.d(TAG, "Cancelando tarea de ubicación pendiente");
        }

        if (cloudFunctionTask != null && !cloudFunctionTask.isComplete()) {
            Log.d(TAG, "Cancelando llamada a Cloud Function pendiente");
        }

        // Cancelar timeout de GPS si está pendiente
        if (locationTimeoutHandler != null && locationTimeoutRunnable != null) {
            locationTimeoutHandler.removeCallbacks(locationTimeoutRunnable);
        }

        // Limpiar referencias
        fusedLocationClient = null;
        locationTask = null;
        cloudFunctionTask = null;
        locationTimeoutHandler = null;
        locationTimeoutRunnable = null;
    }

    private void initViews(View view) {
        layoutSkeleton = view.findViewById(R.id.layoutSkeleton);
        layoutContent = view.findViewById(R.id.layoutContent);
        layoutError = view.findViewById(R.id.layoutError);

        viewPagerRecommendations = view.findViewById(R.id.viewPagerRecommendations);
        layoutPageIndicator = view.findViewById(R.id.layoutPageIndicator);

        tvErrorMessage = view.findViewById(R.id.tvErrorMessage);
        btnRetry = view.findViewById(R.id.btnRetry);
        ivLoadingIcon = view.findViewById(R.id.ivLoadingIcon);
        tvLoadingMessage = view.findViewById(R.id.tvLoadingMessage);
        tvLoadingSubtext = view.findViewById(R.id.tvLoadingSubtext);
        btnCancelSearch = view.findViewById(R.id.btnCancelSearch);
    }

    private void setupListeners() {
        if (getView() != null) {
            getView().setOnClickListener(v -> dismiss());
        }

        View cardView = getView() != null ? getView().findViewById(R.id.cardMain) : null;
        if (cardView != null) {
            cardView.setOnClickListener(v -> {});
        }

        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> buscarRecomendaciones());
        }

        if (btnCancelSearch != null) {
            btnCancelSearch.setOnClickListener(v -> {
                Log.d(TAG, "Usuario canceló la búsqueda");
                registrarEventoTelemetria("cancelar", null, null, "Usuario canceló búsqueda");
                dismiss();
            });
        }
    }

    private void showSkeleton() {
        layoutSkeleton.setVisibility(View.VISIBLE);
        layoutContent.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
    }

    private void updateLoadingStep(int iconResId, String message, String subtext) {
        if (ivLoadingIcon != null && tvLoadingMessage != null) {
            // Animación de escala y fade para el icono
            ivLoadingIcon.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    ivLoadingIcon.setImageResource(iconResId);
                    ivLoadingIcon.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(300)
                        .start();
                });

            // Animación de slide para el mensaje
            tvLoadingMessage.animate()
                .translationY(-10f)
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    tvLoadingMessage.setText(message);
                    tvLoadingMessage.setTranslationY(10f);
                    tvLoadingMessage.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(200)
                        .start();
                });

            // Actualizar submensaje con fade
            if (tvLoadingSubtext != null) {
                if (subtext != null && !subtext.isEmpty()) {
                    tvLoadingSubtext.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            tvLoadingSubtext.setText(subtext);
                            tvLoadingSubtext.setVisibility(View.VISIBLE);
                            tvLoadingSubtext.animate().alpha(1f).setDuration(200).start();
                        });
                } else {
                    tvLoadingSubtext.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction(() -> tvLoadingSubtext.setVisibility(View.GONE));
                }
            }
        }
    }

    private void updateLoadingStep(int iconResId, String message) {
        updateLoadingStep(iconResId, message, null);
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
        updateLoadingStep(R.drawable.ic_account_circle,
            "Verificando tu perfil...",
            "Consultando información de tu mascota");
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

        updateLoadingStep(R.drawable.ic_location_on,
            "Obteniendo tu ubicación...",
            "Buscando paseadores cercanos");
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
            updateLoadingStep(R.drawable.ic_gps_fixed,
                "Obteniendo tu ubicación GPS...",
                "Activando GPS del dispositivo");

            // Configurar timeout de 10 segundos
            locationTimeoutRunnable = () -> {
                if (locationTask != null && !locationTask.isComplete()) {
                    Log.e(TAG, "Timeout: GPS no respondió en 10 segundos");
                    showError("No se pudo obtener tu ubicación. Verifica que el GPS esté activado y que la app tenga permisos de ubicación.");
                }
            };
            locationTimeoutHandler.postDelayed(locationTimeoutRunnable, LOCATION_TIMEOUT_MS);

            // Guardar referencia a la tarea para poder cancelarla si es necesario
            locationTask = fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        // Cancelar timeout ya que obtuvimos la ubicación
                        locationTimeoutHandler.removeCallbacks(locationTimeoutRunnable);

                        if (location != null) {
                            Map<String, Object> userLocation = new HashMap<>();
                            userLocation.put(FirestoreConstants.FIELD_LATITUDE, location.getLatitude());
                            userLocation.put(FirestoreConstants.FIELD_LONGITUDE, location.getLongitude());
                            Log.d(TAG, " Ubicación actual obtenida del GPS");
                            updateLoadingStep(R.drawable.ic_search,
                                "Buscando paseadores cercanos...",
                                "Analizando disponibilidad en tu zona");
                            llamarCloudFunction(sanitizeData(userData), sanitizeData(petData), userLocation);
                        } else {
                            Log.e(TAG, " No se pudo obtener ubicación actual del GPS");
                            showError("No se pudo obtener tu ubicación actual. Por favor activa el GPS y los permisos de ubicación.");
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Cancelar timeout
                        locationTimeoutHandler.removeCallbacks(locationTimeoutRunnable);

                        Log.e(TAG, " Error al obtener ubicación del GPS: " + e.getMessage(), e);
                        showError("Error al obtener tu ubicación: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            // Cancelar timeout
            if (locationTimeoutRunnable != null) {
                locationTimeoutHandler.removeCallbacks(locationTimeoutRunnable);
            }
            Log.e(TAG, " Sin permisos de ubicación: " + e.getMessage(), e);
            showError("Se requieren permisos de ubicación. Por favor actívalos en la configuración de la app.");
        }
    }

    private Map<String, Object> sanitizeData(Map<String, Object> data) {
        Map<String, Object> cleanData = new HashMap<>();
        if (data == null) return cleanData;

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();

            // Sanitizar strings para prevenir XSS
            if (value instanceof String) {
                cleanData.put(entry.getKey(), InputUtils.sanitizeInput((String) value));
            } else if (value instanceof com.google.firebase.Timestamp) {
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
        // Guardar el tamaño del perro para usarlo cuando se muestren las recomendaciones
        tamanoPetActual = (String) petData.get("tamano");

        updateLoadingStep(R.drawable.ic_auto_awesome,
            "Generando recomendación con IA...",
            "Analizando el mejor match para tu mascota");

        Map<String, Object> data = new HashMap<>();
        data.put("userData", userData);
        data.put("petData", petData);
        data.put("userLocation", userLocation);

        // Guardar referencia a la tarea y mejorar manejo de errores
        cloudFunctionTask = FirebaseFunctions.getInstance()
                .getHttpsCallable("recomendarPaseadores")
                .call(data)
                .addOnSuccessListener(result -> processCloudFunctionResult(result))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error llamando Cloud Function", e);

                    // Mensajes de error específicos según el tipo de excepción
                    String message;
                    if (e instanceof com.google.firebase.functions.FirebaseFunctionsException) {
                        com.google.firebase.functions.FirebaseFunctionsException ffe =
                            (com.google.firebase.functions.FirebaseFunctionsException) e;
                        switch (ffe.getCode()) {
                            case UNAUTHENTICATED:
                                message = "Debes iniciar sesión para usar esta función";
                                break;
                            case PERMISSION_DENIED:
                                message = "No tienes permiso para solicitar recomendaciones";
                                break;
                            case RESOURCE_EXHAUSTED:
                                message = "Has alcanzado el límite de recomendaciones. Intenta más tarde";
                                break;
                            case UNAVAILABLE:
                                message = "Servicio temporalmente no disponible. Intenta en unos minutos";
                                break;
                            case INVALID_ARGUMENT:
                                message = ffe.getMessage() != null ? ffe.getMessage() : "Datos inválidos";
                                break;
                            default:
                                message = "Error al buscar recomendaciones: " + ffe.getMessage();
                        }
                    } else {
                        message = "Error de conexión. Verifica tu internet";
                    }
                    showError(message);
                });
    }

    private void processCloudFunctionResult(com.google.firebase.functions.HttpsCallableResult result) {
        Map<String, Object> response = (Map<String, Object>) result.getData();
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) response.get("recommendations");

        if (recommendations == null || recommendations.isEmpty()) {
            showError("No encontramos matches perfectos");
            return;
        }

        loadAllRecommendations(recommendations, tamanoPetActual);
    }

    private void loadAllRecommendations(List<Map<String, Object>> recommendations, String tamanoPet) {
        showContent();

        pagerAdapter = new RecomendacionIAPagerAdapter(recommendations, tamanoPet, new RecomendacionIAPagerAdapter.OnRecommendationActionListener() {
            @Override
            public void onViewProfile(String paseadorId, int matchScore) {
                registrarEventoTelemetria("ver_perfil", paseadorId, matchScore, null);
                Intent intent = new Intent(getContext(), PerfilPaseadorActivity.class);
                intent.putExtra("paseador_id", paseadorId);
                startActivity(intent);
            }

            @Override
            public void onFavorite(String paseadorId, int matchScore) {
                registrarEventoTelemetria("favorito", paseadorId, matchScore, null);
                toggleFavorite(paseadorId);
            }

            @Override
            public void onShare(String paseadorId, int matchScore) {
                registrarEventoTelemetria("compartir", paseadorId, matchScore, null);
                shareRecommendation(paseadorId);
            }

            @Override
            public void onNotInterested(String paseadorId) {
                registrarEventoTelemetria("no_interesado", paseadorId, null, null);
                Toast.makeText(getContext(), "Entendido, no mostraremos más esta recomendación", Toast.LENGTH_SHORT).show();

                int currentPosition = viewPagerRecommendations.getCurrentItem();
                recommendations.remove(currentPosition);
                pagerAdapter.notifyItemRemoved(currentPosition);

                if (recommendations.isEmpty()) {
                    dismiss();
                } else {
                    updatePageIndicators(recommendations.size());
                }
            }

            @Override
            public void onCloseDialog() {
                dismiss();
            }
        });

        viewPagerRecommendations.setAdapter(pagerAdapter);

        updatePageIndicators(recommendations.size());

        viewPagerRecommendations.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updatePageIndicatorSelection(position);
            }
        });

        String firstPaseadorId = (String) recommendations.get(0).get(FirestoreConstants.FIELD_ID);
        int firstMatchScore = getIntValue(recommendations.get(0).get(FirestoreConstants.FIELD_MATCH_SCORE_LOWER));
        registrarEventoTelemetria("exito", firstPaseadorId, firstMatchScore, null);
    }

    private void updatePageIndicators(int count) {
        layoutPageIndicator.removeAllViews();

        if (count <= 1) {
            layoutPageIndicator.setVisibility(View.GONE);
            return;
        }

        layoutPageIndicator.setVisibility(View.VISIBLE);

        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(getContext());
            int size = (int) (8 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(
                (int) (4 * getResources().getDisplayMetrics().density),
                0,
                (int) (4 * getResources().getDisplayMetrics().density),
                0
            );
            dot.setLayoutParams(params);
            dot.setImageResource(i == 0 ? R.drawable.dot_indicator_active : R.drawable.dot_indicator_inactive);
            layoutPageIndicator.addView(dot);
        }
    }

    private void updatePageIndicatorSelection(int position) {
        int count = layoutPageIndicator.getChildCount();
        for (int i = 0; i < count; i++) {
            ImageView dot = (ImageView) layoutPageIndicator.getChildAt(i);
            dot.setImageResource(i == position ? R.drawable.dot_indicator_active : R.drawable.dot_indicator_inactive);
        }
    }

    private void toggleFavorite(String paseadorId) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection(FirestoreConstants.COLLECTION_USUARIOS)
                .document(userId)
                .collection(FirestoreConstants.COLLECTION_FAVORITOS)
                .document(paseadorId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        db.collection(FirestoreConstants.COLLECTION_USUARIOS)
                                .document(userId)
                                .collection(FirestoreConstants.COLLECTION_FAVORITOS)
                                .document(paseadorId)
                                .delete()
                                .addOnSuccessListener(v -> Toast.makeText(getContext(), "Eliminado de favoritos", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error al eliminar de favoritos", Toast.LENGTH_SHORT).show());
                    } else {
                        Map<String, Object> favoritoData = new HashMap<>();
                        favoritoData.put(FirestoreConstants.FIELD_PASEADOR_ID, paseadorId);
                        favoritoData.put(FirestoreConstants.FIELD_TIMESTAMP, FieldValue.serverTimestamp());

                        db.collection(FirestoreConstants.COLLECTION_USUARIOS)
                                .document(userId)
                                .collection(FirestoreConstants.COLLECTION_FAVORITOS)
                                .document(paseadorId)
                                .set(favoritoData)
                                .addOnSuccessListener(v -> Toast.makeText(getContext(), "Agregado a favoritos", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error al agregar a favoritos", Toast.LENGTH_SHORT).show());
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error al consultar favoritos", Toast.LENGTH_SHORT).show());
    }

    private void shareRecommendation(String paseadorId) {
        Toast.makeText(getContext(), "Preparando para compartir...", Toast.LENGTH_SHORT).show();

        // Obtener la vista actual del ViewPager de forma más confiable
        int currentPosition = viewPagerRecommendations.getCurrentItem();
        View cardView = getViewPagerCurrentView(currentPosition);

        if (cardView == null) {
            Toast.makeText(getContext(), "Error al preparar la captura", Toast.LENGTH_SHORT).show();
            return;
        }

        // Crear un Bitmap de la tarjeta
        Bitmap cardBitmap = createBitmapFromView(cardView);

        if (cardBitmap == null) {
            Toast.makeText(getContext(), "Error al capturar la tarjeta", Toast.LENGTH_SHORT).show();
            return;
        }

        // Guardar el bitmap en un archivo temporal
        File imageFile = saveBitmapToFile(cardBitmap);

        if (imageFile == null) {
            Toast.makeText(getContext(), "Error al guardar la imagen", Toast.LENGTH_SHORT).show();
            return;
        }

        // Obtener el URI del archivo
        Uri imageUri = FileProvider.getUriForFile(getContext(),
            getContext().getPackageName() + ".provider", imageFile);

        // Crear intent para compartir imagen + texto
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Recomendación de Paseador - Walki");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
            "🤖 ¡La IA de Walki me recomendó este paseador perfecto para mi perro! https://walki.app/");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(shareIntent, "Compartir recomendación"));
    }

    private View getViewPagerCurrentView(int position) {
        try {
            // Intentar obtener el RecyclerView del ViewPager2
            androidx.recyclerview.widget.RecyclerView recyclerView = null;
            for (int i = 0; i < viewPagerRecommendations.getChildCount(); i++) {
                View child = viewPagerRecommendations.getChildAt(i);
                if (child instanceof androidx.recyclerview.widget.RecyclerView) {
                    recyclerView = (androidx.recyclerview.widget.RecyclerView) child;
                    break;
                }
            }

            if (recyclerView != null) {
                androidx.recyclerview.widget.RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
                if (holder != null) {
                    return holder.itemView;
                }
            }

            // Fallback: intentar obtener directamente
            for (int i = 0; i < viewPagerRecommendations.getChildCount(); i++) {
                View child = viewPagerRecommendations.getChildAt(i);
                if (child != null) {
                    return child;
                }
            }

            return null;
        } catch (Exception e) {
            Log.e("ShareRecommendation", "Error getting ViewPager current view", e);
            return null;
        }
    }

    private Bitmap createBitmapFromView(View view) {
        try {
            // Buscar el CardView dentro de la vista actual del item
            androidx.cardview.widget.CardView cardView = view.findViewById(R.id.cardMainRecommendation);

            if (cardView == null) {
                Log.e("ShareRecommendation", "CardView not found");
                return null;
            }

            // Obtener ancho y alto actuales del CardView (ya está medido en pantalla)
            int width = cardView.getWidth();
            int height = cardView.getHeight();

            // Si la vista aún no está medida, usar getMeasuredWidth/Height
            if (width <= 0 || height <= 0) {
                width = cardView.getMeasuredWidth();
                height = cardView.getMeasuredHeight();
            }

            // Encontrar las vistas que queremos capturar (hasta cardPrice)
            View cardPrice = cardView.findViewById(R.id.cardPrice);

            int captureHeight = height; // Por defecto, capturar todo

            if (cardPrice != null) {
                // Calcular la altura hasta el final de cardPrice
                int cardPriceBottom = cardPrice.getBottom();
                int cardViewPaddingBottom = cardView.getPaddingBottom();

                // Capturar hasta cardPrice + un poco de espacio
                captureHeight = cardPriceBottom + cardViewPaddingBottom + 20; // 20dp de margen extra
            }

            // Crear bitmap sin modificar el layout actual de la vista
            Bitmap bitmap = Bitmap.createBitmap(width, captureHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // Dibujar la vista (el fondo se dibuja automáticamente)
            cardView.draw(canvas);

            // Crear un Path con bordes redondeados SOLO en la parte inferior
            android.graphics.Path path = new android.graphics.Path();
            float radius = 32f; // Mismo radio que app:cardCornerRadius="32dp"

            // Path: recto arriba, redondeado abajo
            path.moveTo(0, 0); // Esquina superior izquierda
            path.lineTo(width, 0); // Línea superior recta
            path.lineTo(width, captureHeight - radius); // Línea recta derecha hasta antes de la curva
            path.arcTo(width - radius * 2, captureHeight - radius * 2, width, captureHeight, 0, 90, false); // Arco esquina inferior derecha
            path.lineTo(radius, captureHeight); // Línea inferior
            path.arcTo(0, captureHeight - radius * 2, radius * 2, captureHeight, 90, 90, false); // Arco esquina inferior izquierda
            path.lineTo(0, 0); // Línea izquierda recta
            path.close();

            // Hacer clip del canvas con el path
            canvas.clipPath(path);

            // Volver a dibujar la vista con el clip aplicado
            cardView.draw(canvas);

            return bitmap;
        } catch (Exception e) {
            Log.e("ShareRecommendation", "Error creating bitmap from view", e);
            return null;
        }
    }

    private File saveBitmapToFile(Bitmap bitmap) {
        try {
            File cacheDir = getContext().getCacheDir();
            File imageFile = new File(cacheDir, "recomendacion_paseador.png");

            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            return imageFile;
        } catch (IOException e) {
            Log.e("ShareRecommendation", "Error saving bitmap to file", e);
            return null;
        }
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
