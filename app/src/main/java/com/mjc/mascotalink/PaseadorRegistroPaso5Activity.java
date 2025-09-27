package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class PaseadorRegistroPaso5Activity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "Paso5Paseador";
    private static final String PREFS = "WizardPaseador";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private Button btnMetodoPago, btnGrabarVideo, btnGuardar;
    private LinearLayout rowDisponibilidad, rowTiposPerros;

    private GoogleMap mMap;
    private Circle zonaCircle;
    private double zonaLat = 0.0, zonaLng = 0.0, zonaRadioKm = 5.0;

    private String videoPresentacionUrl = null;
    private FusedLocationProviderClient fusedLocation;
    private boolean locationPermissionGranted = false;
    private final ActivityResultLauncher<String[]> locationPermsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fine = Boolean.TRUE.equals(result.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false));
                boolean coarse = Boolean.TRUE.equals(result.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false));
                locationPermissionGranted = fine || coarse;
                if (locationPermissionGranted) {
                    centerOnUserIfPossible();
                    enableMyLocationIfPossible();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso5);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        btnMetodoPago = findViewById(R.id.btn_metodo_pago);
        btnGrabarVideo = findViewById(R.id.btn_grabar_video);
        btnGuardar = findViewById(R.id.btn_guardar_continuar);
        rowDisponibilidad = findViewById(R.id.row_disponibilidad);
        rowTiposPerros = findViewById(R.id.row_tipos_perros);

        btnMetodoPago.setOnClickListener(v -> configurarMetodoPago());
        btnGrabarVideo.setOnClickListener(v -> grabarVideoPresentacion());
        rowDisponibilidad.setOnClickListener(v -> configurarDisponibilidad());
        rowTiposPerros.setOnClickListener(v -> configurarTiposPerros());
        btnGuardar.setOnClickListener(v -> completarRegistro());

        fusedLocation = LocationServices.getFusedLocationProviderClient(this);
        setupMap();

        // Solicitar permisos de ubicación al entrar a Paso 5
        requestLocationPermission();
    }

    private void setupMap() {
        FrameLayout container = findViewById(R.id.map_container);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_container);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getSupportFragmentManager().beginTransaction().replace(R.id.map_container, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapClickListener(latLng -> {
            if (zonaCircle != null) zonaCircle.remove();
            zonaLat = latLng.latitude;
            zonaLng = latLng.longitude;
            CircleOptions opts = new CircleOptions()
                    .center(latLng)
                    .radius(zonaRadioKm * 1000)
                    .strokeColor(Color.parseColor("#2680EB"))
                    .strokeWidth(2f)
                    .fillColor(Color.parseColor("#302680EB"));
            zonaCircle = mMap.addCircle(opts);
            guardarZonaServicio(zonaLat, zonaLng, zonaRadioKm);
        });
        centerOnUserIfPossible();
        enableMyLocationIfPossible();
    }

    private void requestLocationPermission() {
        locationPermissionGranted = false;
        locationPermsLauncher.launch(new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void centerOnUserIfPossible() {
        if (!locationPermissionGranted) return;
        try {
            fusedLocation.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null && mMap != null) {
                    LatLng here = new LatLng(loc.getLatitude(), loc.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(here, 12f));
                }
            });
        } catch (SecurityException ignored) {}
    }

    private void enableMyLocationIfPossible() {
        if (mMap == null) return;
        try {
            if (locationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
            }
        } catch (SecurityException ignored) {}
    }

    private void guardarZonaServicio(double lat, double lng, double radioKm) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> zona = new HashMap<>();
        zona.put("nombre", "Zona de Servicio");
        Map<String, Object> centro = new HashMap<>();
        centro.put("lat", lat);
        centro.put("lng", lng);
        zona.put("ubicacion_centro", centro); // Alternativa sin GeoPoint para emulador
        zona.put("radio_km", radioKm);
        zona.put("activo", true);
        db.collection("paseadores").document(uid)
                .collection("zonas_servicio").add(zona);
    }

    private void configurarMetodoPago() {
        Intent intent = new Intent(this, MetodoPagoActivity.class);
        metodoPagoLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> metodoPagoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // Método de pago guardado silenciosamente
            });

    private void configurarDisponibilidad() {
        Intent intent = new Intent(this, DisponibilidadActivity.class);
        disponibilidadLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> disponibilidadLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // Disponibilidad guardada silenciosamente
            });

    private void configurarTiposPerros() {
        Intent intent = new Intent(this, TiposPerrosActivity.class);
        tiposPerrosLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> tiposPerrosLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // Preferencias de perros guardadas silenciosamente
            });

    private final ActivityResultLauncher<Intent> videoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        subirVideoPresentacion(uri);
                    }
                }
            });

    private void grabarVideoPresentacion() {
        Intent intent = new Intent(this, VideoRecordActivity.class);
        videoLauncher.launch(intent);
    }

    private boolean validarDuracionVideo(Uri videoUri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, videoUri);
            String dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            retriever.release();
            long ms = Long.parseLong(dur);
            long seg = ms / 1000;
            return seg >= 30 && seg <= 60;
        } catch (Exception e) {
            return false;
        }
    }

    private void subirVideoPresentacion(Uri uri) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        String name = uid + "_presentacion_" + System.currentTimeMillis() + ".mp4";
        StorageReference ref = storage.getReference().child("videos_presentacion/" + name);
        ref.putFile(uri).addOnSuccessListener(t ->
                ref.getDownloadUrl().addOnSuccessListener(url -> {
                    videoPresentacionUrl = url.toString();
                })
        ).addOnFailureListener(e -> Toast.makeText(this, "Error al subir video", Toast.LENGTH_SHORT).show());
    }

    private boolean validarCamposCompletos() {
        // Requisitos mínimos: tener zona seleccionada y (opcional) video
        return zonaLat != 0.0 || zonaLng != 0.0; // al menos una selección en mapa
    }

    private void completarRegistro() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_LONG).show();
            return;
        }
        if (!validarCamposCompletos()) {
            Toast.makeText(this, "Por favor selecciona una zona de servicio en el mapa", Toast.LENGTH_LONG).show();
            return;
        }
        String uid = mAuth.getCurrentUser().getUid();

        // Cargar todos los datos del wizard desde SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        
        // Cargar URLs de galería de Paso 4
        String csv = prefs.getString("galeria_paseos_urls", "");
        List<String> galeria = new ArrayList<>();
        if (csv != null && !csv.isEmpty()) {
            for (String s : csv.split(",")) {
                if (!s.trim().isEmpty()) galeria.add(s.trim());
            }
        }

        // Crear documento completo del paseador
        Map<String, Object> paseadorData = new HashMap<>();
        
        // Datos básicos del paso 1
        paseadorData.put("nombre", prefs.getString("nombre", ""));
        paseadorData.put("apellido", prefs.getString("apellido", ""));
        paseadorData.put("correo", prefs.getString("email", ""));
        paseadorData.put("telefono", prefs.getString("telefono", ""));
        paseadorData.put("direccion", prefs.getString("domicilio", ""));
        paseadorData.put("cedula", prefs.getString("cedula", ""));
        
        // Fecha de nacimiento
        String fechaStr = prefs.getString("fecha_nacimiento", "");
        if (!fechaStr.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date fecha = sdf.parse(fechaStr);
                if (fecha != null) {
                    paseadorData.put("fecha_nacimiento", new Timestamp(fecha));
                }
            } catch (Exception e) {
                // Ignorar error de fecha
            }
        }
        
        // URLs de imágenes del paso 2
        paseadorData.put("selfie_url", prefs.getString("selfieUrl", ""));
        paseadorData.put("foto_perfil_url", prefs.getString("fotoPerfilUrl", ""));
        
        // URLs de documentos del paso 3
        paseadorData.put("certificado_antecedentes_url", prefs.getString("antecedentesUrl", ""));
        paseadorData.put("certificado_medico_url", prefs.getString("medicoUrl", ""));
        
        // Galería del paso 4
        paseadorData.put("galeria_paseos_urls", galeria);
        
        // Video de presentación del paso 5
        if (videoPresentacionUrl != null) {
            paseadorData.put("video_presentacion_url", videoPresentacionUrl);
        }
        
        // Zona de servicio
        Map<String, Object> zona = new HashMap<>();
        zona.put("lat", zonaLat);
        zona.put("lng", zonaLng);
        zona.put("radio_km", zonaRadioKm);
        paseadorData.put("zona_servicio", zona);
        
        // Estado del registro
        paseadorData.put("verificacion_estado", "PENDIENTE");
        paseadorData.put("acepto_terminos", true);
        paseadorData.put("fecha_aceptacion_terminos", FieldValue.serverTimestamp());
        paseadorData.put("ultima_actualizacion", FieldValue.serverTimestamp());
        paseadorData.put("fecha_registro", FieldValue.serverTimestamp());
        
        // Campos iniciales
        paseadorData.put("calificacion_promedio", 0.0);
        paseadorData.put("num_servicios_completados", 0);
        paseadorData.put("activo", true);

        // Guardar en Firestore
        db.collection("paseadores").document(uid)
                .set(paseadorData, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    // Limpiar datos temporales
                    limpiarDatosTemporales();
                    mostrarMensajeFinalRegistro();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error al completar registro", Toast.LENGTH_SHORT).show());
    }
    
    private void limpiarDatosTemporales() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    private void mostrarMensajeFinalRegistro() {
        new AlertDialog.Builder(this)
                .setTitle("¡Registro Completado!")
                .setMessage("Tu perfil de paseador ha sido enviado para revisión. Te notificaremos cuando tu perfil sea aprobado.")
                .setPositiveButton("Finalizar", (d, w) -> {
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}
