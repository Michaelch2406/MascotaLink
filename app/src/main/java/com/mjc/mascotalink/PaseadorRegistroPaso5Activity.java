package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.Timestamp;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.text.ParseException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import java.util.ArrayList;
import java.util.List;

public class PaseadorRegistroPaso5Activity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "PaseadorPaso5";
    private static final String PREFS = "WizardPaseador";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private Button btnGuardar;
    private TextView tvValidationMessages;
    private ImageView ivPagoCheck, ivDisponibilidadCheck, ivPerrosCheck;

    private GoogleMap mMap;
    private Circle zonaCircle;
    private LatLng zonaCentro;
    private double zonaRadioKm = 5.0; // Default radius

    private final ActivityResultLauncher<Intent> videoLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), this::handleVideoResult
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso5);

        setupFirebase();
        setupViews();
        setupListeners();
        setupMap();
        setupPlacesAutocomplete(); // Add this call
        loadState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        verificarCompletitudTotal();
    }

    private void setupFirebase() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
    }

    private void setupViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        btnGuardar = findViewById(R.id.btn_guardar_continuar);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);
        ivPagoCheck = findViewById(R.id.iv_pago_check);
        ivDisponibilidadCheck = findViewById(R.id.iv_disponibilidad_check);
        ivPerrosCheck = findViewById(R.id.iv_perros_check);
    }

    private void setupListeners() {
        findViewById(R.id.btn_metodo_pago).setOnClickListener(v -> startActivity(new Intent(this, MetodoPagoActivity.class)));
        findViewById(R.id.btn_grabar_video).setOnClickListener(v -> grabarVideoPresentacion());
        findViewById(R.id.btn_eliminar_video).setOnClickListener(v -> eliminarVideo());
        findViewById(R.id.row_disponibilidad).setOnClickListener(v -> startActivity(new Intent(this, DisponibilidadActivity.class)));
        findViewById(R.id.row_tipos_perros).setOnClickListener(v -> startActivity(new Intent(this, TiposPerrosActivity.class)));
        btnGuardar.setOnClickListener(v -> completarRegistro());
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_container);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupPlacesAutocomplete() {
        if (!Places.isInitialized()) {
            try {
                ApplicationInfo app = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
                Bundle bundle = app.metaData;
                String apiKey = bundle.getString("com.google.android.geo.API_KEY");

                if (apiKey == null || apiKey.isEmpty()) {
                    Toast.makeText(this, "Maps API Key not found in manifest", Toast.LENGTH_LONG).show();
                    return;
                }
                Places.initialize(getApplicationContext(), apiKey);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to load meta-data, NameNotFound: " + e.getMessage());
            }
        }

        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        if (autocompleteFragment != null) {
            autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS));
            autocompleteFragment.setHint("Buscar dirección de servicio");
            autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                @Override
                public void onPlaceSelected(@NonNull Place place) {
                    Log.i(TAG, "Place: " + place.getName() + ", " + place.getId() + ", " + place.getLatLng());
                    zonaCentro = place.getLatLng();
                    saveState();
                    dibujarCirculo();
                    verificarCompletitudTotal();
                }

                @Override
                public void onError(@NonNull com.google.android.gms.common.api.Status status) {
                    Log.e(TAG, "An error occurred: " + status);
                    Toast.makeText(PaseadorRegistroPaso5Activity.this, "Error en búsqueda: " + status.getStatusMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapClickListener(latLng -> {
            zonaCentro = latLng;
            saveState();
            dibujarCirculo();
            verificarCompletitudTotal();
        });
        loadMapState();
    }

    private void dibujarCirculo() {
        if (mMap == null || zonaCentro == null) return;
        if (zonaCircle != null) zonaCircle.remove();
        zonaCircle = mMap.addCircle(new CircleOptions()
                .center(zonaCentro)
                .radius(zonaRadioKm * 1000)
                .strokeColor(Color.parseColor("#2680EB"))
                .strokeWidth(2f)
                .fillColor(Color.parseColor("#302680EB")));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(zonaCentro, 12f));
    }

    private void grabarVideoPresentacion() {
        Intent intent = new Intent(this, VideoRecordActivity.class);
        videoLauncher.launch(intent);
    }

    private void handleVideoResult(androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri tempUri = result.getData().getData();
            if (tempUri != null) {
                Uri permanentUri = FileStorageHelper.copyFileToInternalStorage(this, tempUri, "VIDEO_PRESENTACION_");
                if (permanentUri != null) {
                    SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                    editor.putString("videoPresentacionUri", permanentUri.toString());
                    editor.apply();
                    mostrarPreviewVideo(permanentUri);
                    verificarCompletitudTotal();
                } else {
                    Toast.makeText(this, "Error al guardar el video.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void eliminarVideo() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.remove("videoPresentacionUri");
        editor.apply();
        findViewById(R.id.video_preview_container).setVisibility(View.GONE);
        verificarCompletitudTotal();
    }

    private void mostrarPreviewVideo(Uri uri) {
        FrameLayout container = findViewById(R.id.video_preview_container);
        ImageView thumbnail = findViewById(R.id.video_thumbnail);
        Glide.with(this).load(uri).centerCrop().into(thumbnail);
        container.setVisibility(View.VISIBLE);
    }

    private void verificarCompletitudTotal() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        List<String> faltantes = new ArrayList<>();

        // Verificar pasos anteriores
        if (!prefs.getBoolean("paso1_completo", false)) faltantes.add("• Faltan datos del Paso 1.");
        if (!prefs.getBoolean("paso2_completo", false)) faltantes.add("• Faltan fotos del Paso 2.");
        if (!prefs.getBoolean("paso3_completo", false)) faltantes.add("• Faltan documentos del Paso 3.");
        if (!prefs.getBoolean("paso4_completo", false)) faltantes.add("• Faltan datos del Paso 4 (Galería y Cuestionario).");

        // Verificar campos de este paso (Paso 5)
        boolean pagoOk = prefs.getBoolean("metodo_pago_completo", false);
        ivPagoCheck.setVisibility(pagoOk ? View.VISIBLE : View.GONE);
        if (!pagoOk) faltantes.add("• Falta configurar el método de pago.");

        boolean disponibilidadOk = prefs.getBoolean("disponibilidad_completa", false);
        ivDisponibilidadCheck.setVisibility(disponibilidadOk ? View.VISIBLE : View.GONE);
        if (!disponibilidadOk) faltantes.add("• Falta configurar tu disponibilidad.");

        boolean perrosOk = prefs.getBoolean("perros_completo", false);
        ivPerrosCheck.setVisibility(perrosOk ? View.VISIBLE : View.GONE);
        if (!perrosOk) faltantes.add("• Falta especificar los tipos de perros que manejas.");

        if (prefs.getString("videoPresentacionUri", null) == null) {
            faltantes.add("• Falta grabar el video de presentación.");
        }
        if (zonaCentro == null) {
            faltantes.add("• Falta seleccionar tu zona de servicio en el mapa.");
        }

        if (faltantes.isEmpty()) {
            tvValidationMessages.setVisibility(View.GONE);
            btnGuardar.setEnabled(true);
        } else {
            tvValidationMessages.setText(String.join("\n", faltantes));
            tvValidationMessages.setVisibility(View.VISIBLE);
            btnGuardar.setEnabled(false);
        }
    }

    private void completarRegistro() {
        btnGuardar.setEnabled(false);
        btnGuardar.setText("Registrando...");
        tvValidationMessages.setVisibility(View.GONE);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // Paso 1 Data
        String nombre = prefs.getString("nombre", "");
        String apellido = prefs.getString("apellido", "");
        String cedula = prefs.getString("cedula", "");
        String fechaNacStr = prefs.getString("fecha_nacimiento", "");
        String domicilio = prefs.getString("domicilio", "");
        String telefono = prefs.getString("telefono", "");
        String email = prefs.getString("email", "");
        String password = prefs.getString("password", "");

        // Paso 2 Data
        String selfieUriStr = prefs.getString("selfieUri", null);
        String fotoPerfilUriStr = prefs.getString("fotoPerfilUri", null);

        // Paso 3 Data
        String antecedentesUriStr = prefs.getString("antecedentesUri", null);
        String medicoUriStr = prefs.getString("medicoUri", null);

        // Paso 4 Data
        String galeriaUrisStr = prefs.getString("galeria_paseos_uris", "");
        boolean quizAprobado = prefs.getBoolean("quiz_aprobado", false);

        // Paso 5 Data
        boolean metodoPagoCompleto = prefs.getBoolean("metodo_pago_completo", false);
        boolean disponibilidadCompleta = prefs.getBoolean("disponibilidad_completa", false);
        boolean perrosCompleto = prefs.getBoolean("perros_completo", false);
        String videoPresentacionUriStr = prefs.getString("videoPresentacionUri", null);
        double zonaLat = prefs.getFloat("zonaLat", 0);
        double zonaLng = prefs.getFloat("zonaLng", 0);

        // 1. Crear usuario en Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String uid = mAuth.getCurrentUser().getUid();
                        // 2. Subir archivos a Firebase Storage
                        uploadFiles(uid, nombre, apellido, cedula, fechaNacStr, domicilio, telefono, email,
                                selfieUriStr, fotoPerfilUriStr, antecedentesUriStr, medicoUriStr, galeriaUrisStr,
                                quizAprobado, metodoPagoCompleto, disponibilidadCompleta, perrosCompleto,
                                videoPresentacionUriStr, zonaLat, zonaLng);
                    } else {
                        // Si la creación de usuario falla, mostrar error
                        btnGuardar.setEnabled(true);
                        btnGuardar.setText("Reintentar Registro");
                        String errorMessage = task.getException().getMessage();
                        if (errorMessage.contains("email address is already in use")) {
                            mostrarError("El correo electrónico ya está registrado.");
                        } else {
                            mostrarError("Error al crear usuario: " + errorMessage);
                        }
                    }
                });
    }

    private void uploadFiles(String uid, String nombre, String apellido, String cedula, String fechaNacStr, String domicilio, String telefono, String email,
                             String selfieUriStr, String fotoPerfilUriStr, String antecedentesUriStr, String medicoUriStr, String galeriaUrisStr,
                             boolean quizAprobado, boolean metodoPagoCompleto, boolean disponibilidadCompleta, boolean perrosCompleto,
                             String videoPresentacionUriStr, double zonaLat, double zonaLng) {

        Map<String, Uri> filesToUpload = new HashMap<>();
        if (selfieUriStr != null) filesToUpload.put("selfie", Uri.parse(selfieUriStr));
        if (fotoPerfilUriStr != null) filesToUpload.put("fotoPerfil", Uri.parse(fotoPerfilUriStr));
        if (antecedentesUriStr != null) filesToUpload.put("antecedentes", Uri.parse(antecedentesUriStr));
        if (medicoUriStr != null) filesToUpload.put("medico", Uri.parse(medicoUriStr));
        if (videoPresentacionUriStr != null) filesToUpload.put("videoPresentacion", Uri.parse(videoPresentacionUriStr));

        List<Uri> galeriaUris = new ArrayList<>();
        if (!galeriaUrisStr.isEmpty()) {
            for (String s : galeriaUrisStr.split(",")) {
                galeriaUris.add(Uri.parse(s));
            }
        }

        List<Task<Uri>> uploadTasks = new ArrayList<>();
        Map<String, String> downloadUrls = new HashMap<>();

        // Upload single files
        for (Map.Entry<String, Uri> entry : filesToUpload.entrySet()) {
            String fileType = entry.getKey();
            Uri fileUri = entry.getValue();
            try {
                InputStream inputStream = getContentResolver().openInputStream(fileUri);
                if (inputStream != null) {
                    StorageReference ref = storage.getReference().child("users").child(uid).child(fileType + "_" + System.currentTimeMillis());
                    Task<Uri> uploadTask = ref.putStream(inputStream).continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }
                        return ref.getDownloadUrl();
                    }).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            downloadUrls.put(fileType, task.getResult().toString());
                        } else {
                            Log.e("FirebaseStorage", "Upload failed for " + fileType, task.getException());
                        }
                    });
                    uploadTasks.add(uploadTask);
                } else {
                    Log.e("FirebaseStorage", "InputStream is null for " + fileType);
                }
            } catch (Exception e) {
                Log.e("FirebaseStorage", "Error opening stream for " + fileType, e);
            }
        }

        // Upload gallery files
        AtomicInteger galeriaCount = new AtomicInteger(0);
        for (Uri galeriaUri : galeriaUris) {
            try {
                InputStream inputStream = getContentResolver().openInputStream(galeriaUri);
                if (inputStream != null) {
                    StorageReference ref = storage.getReference().child("users").child(uid).child("galeria_" + galeriaCount.incrementAndGet() + "_" + System.currentTimeMillis());
                    Task<Uri> uploadTask = ref.putStream(inputStream).continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }
                        return ref.getDownloadUrl();
                    }).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Store gallery URLs in a list or comma-separated string
                            String currentGaleriaUrls = downloadUrls.getOrDefault("galeria", "");
                            if (!currentGaleriaUrls.isEmpty()) {
                                downloadUrls.put("galeria", currentGaleriaUrls + "," + task.getResult().toString());
                            } else {
                                downloadUrls.put("galeria", task.getResult().toString());
                            }
                        }
                    });
                    uploadTasks.add(uploadTask);
                } else {
                    Log.e("FirebaseStorage", "InputStream is null for gallery file");
                }
            } catch (Exception e) {
                Log.e("FirebaseStorage", "Error opening stream for gallery file", e);
            }
        }

        Tasks.whenAllComplete(uploadTasks)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // 3. Guardar datos en Firestore
                        saveUserDataToFirestore(uid, nombre, apellido, cedula, fechaNacStr, domicilio, telefono, email,
                                quizAprobado, metodoPagoCompleto, disponibilidadCompleta, perrosCompleto,
                                zonaLat, zonaLng, downloadUrls);
                    } else {
                        btnGuardar.setEnabled(true);
                        btnGuardar.setText("Reintentar Registro");
                        mostrarError("Error al subir archivos: " + task.getException().getMessage());
                        // Optionally, delete the created user if file upload fails
                        mAuth.getCurrentUser().delete();
                    }
                });
    }

    private void saveUserDataToFirestore(String uid, String nombre, String apellido, String cedula, String fechaNacStr, String domicilio, String telefono, String email,
                                         boolean quizAprobado, boolean metodoPagoCompleto, boolean disponibilidadCompleta, boolean perrosCompleto,
                                         double zonaLat, double zonaLng, Map<String, String> downloadUrls) {

        Map<String, Object> userData = new HashMap<>();
        userData.put("nombre", nombre);
        userData.put("apellido", apellido);
        userData.put("cedula", cedula);
        userData.put("fechaNacimiento", fechaNacStr);
        userData.put("domicilio", domicilio);
        userData.put("telefono", telefono);
        userData.put("email", email);
        userData.put("rol", "paseador");
        userData.put("quizAprobado", quizAprobado);
        userData.put("metodoPagoCompleto", metodoPagoCompleto);
        userData.put("disponibilidadCompleta", disponibilidadCompleta);
        userData.put("perrosCompleto", perrosCompleto);
        userData.put("zonaServicioLat", zonaLat);
        userData.put("zonaServicioLng", zonaLng);
        userData.put("registroCompleto", true);
        userData.put("fechaRegistro", new Timestamp(new Date()));

        // Add download URLs
        userData.putAll(downloadUrls);

        db.collection("paseadores").document(uid).set(userData)
                .addOnSuccessListener(aVoid -> {
                    limpiarDatosTemporales();
                    mostrarMensajeFinalRegistro();
                })
                .addOnFailureListener(e -> {
                    btnGuardar.setEnabled(true);
                    btnGuardar.setText("Reintentar Registro");
                    mostrarError("Error al guardar datos en Firestore: " + e.getMessage());
                    // Optionally, delete the created user and uploaded files if Firestore save fails
                    mAuth.getCurrentUser().delete();
                });
    }

    private void mostrarError(String msg) {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Error")
                .setMessage(msg)
                .setPositiveButton("Entendido", null)
                .show();
    }

    private void saveState() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        if (zonaCentro != null) {
            editor.putFloat("zonaLat", (float) zonaCentro.latitude);
            editor.putFloat("zonaLng", (float) zonaCentro.longitude);
        }
        editor.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String videoUriString = prefs.getString("videoPresentacionUri", null);
        if (videoUriString != null) {
            mostrarPreviewVideo(Uri.parse(videoUriString));
        }
    }

    private void loadMapState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        float lat = prefs.getFloat("zonaLat", 0);
        float lng = prefs.getFloat("zonaLng", 0);
        if (lat != 0 && lng != 0) {
            zonaCentro = new LatLng(lat, lng);
            dibujarCirculo();
        }
    }

    private void limpiarDatosTemporales() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
    }

    private void mostrarMensajeFinalRegistro() {
        new AlertDialog.Builder(this)
                .setTitle("¡Registro Completado!")
                .setMessage("Tu perfil ha sido enviado para revisión. Te notificaremos cuando sea aprobado.")
                .setPositiveButton("Finalizar", (dialog, which) -> {
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}
