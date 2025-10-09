package com.mjc.mascotalink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DuenoRegistroPaso3Activity extends AppCompatActivity {

    private static final String TAG = "DuenoRegistroPaso3";
    private static final String PREFS_DUENO = "WizardDueno";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private EditText addressEditText;
    private ImageView ivGeolocate;
    private ProgressBar pbGeolocate;
    private SwitchMaterial messagesSwitch;
    private Button saveButton;

    private String direccionRecogida;
    private GeoPoint ubicacionRecogida;

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> autocompleteLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dueno_registro_paso3);

        setupFirebase();
        setupViews();
        setupListeners();
        setupLocationServices();
        setupAutocompleteLauncher();
        loadDataFromPrefs();
    }

    private void setupFirebase() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
    }

    private void setupViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        addressEditText = findViewById(R.id.addressEditText);
        ivGeolocate = findViewById(R.id.iv_geolocate);
        pbGeolocate = findViewById(R.id.pb_geolocate);
        messagesSwitch = findViewById(R.id.messagesSwitch);
        saveButton = findViewById(R.id.saveButton);

        Button paymentMethodButton = findViewById(R.id.paymentMethodButton);
        paymentMethodButton.setOnClickListener(v -> {
            saveDataToPrefs();
            Intent intent = new Intent(this, MetodoPagoActivity.class);
            intent.putExtra("prefs", PREFS_DUENO);
            startActivity(intent);
        });
    }

    private void setupListeners() {
        saveButton.setOnClickListener(v -> completarRegistroDueno());
        addressEditText.setOnClickListener(v -> launchAutocomplete());
        ivGeolocate.setOnClickListener(v -> onGeolocateClick());
    }

    private void setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                fetchLocationAndFillAddress();
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupAutocompleteLauncher() {
        autocompleteLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Place place = Autocomplete.getPlaceFromIntent(result.getData());
                        direccionRecogida = place.getAddress();
                        if (place.getLatLng() != null) {
                            ubicacionRecogida = new GeoPoint(place.getLatLng().latitude, place.getLatLng().longitude);
                        }
                        addressEditText.setText(direccionRecogida);
                    } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR) {
                        Status status = Autocomplete.getStatusFromIntent(result.getData());
                        Log.e(TAG, "Autocomplete error: " + status.getStatusMessage());
                    }
                });
    }

    private void launchAutocomplete() {
        List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.ADDRESS, Place.Field.LAT_LNG);
        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .setCountry("EC")
                .build(this);
        autocompleteLauncher.launch(intent);
    }

    private void onGeolocateClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndFillAddress();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLocationAndFillAddress() {
        showGeolocateLoading(true);
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        String addressLine = addresses.get(0).getAddressLine(0);
                        direccionRecogida = addressLine;
                        ubicacionRecogida = new GeoPoint(location.getLatitude(), location.getLongitude());
                        addressEditText.setText(direccionRecogida);
                        Toast.makeText(this, "Dirección autocompletada.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No se pudo encontrar una dirección.", Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Geocoder service not available", e);
                }
            }
            showGeolocateLoading(false);
        }).addOnFailureListener(e -> {
            showGeolocateLoading(false);
            Toast.makeText(this, "No se pudo obtener la ubicación.", Toast.LENGTH_LONG).show();
        });
    }

    private void showGeolocateLoading(boolean isLoading) {
        pbGeolocate.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        ivGeolocate.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
    }

    private void loadDataFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_DUENO, MODE_PRIVATE);
        direccionRecogida = prefs.getString("domicilio", "");
        float lat = prefs.getFloat("domicilio_lat", 0);
        float lng = prefs.getFloat("domicilio_lng", 0);
        if (lat != 0 && lng != 0) {
            ubicacionRecogida = new GeoPoint(lat, lng);
        }
        addressEditText.setText(direccionRecogida);
        messagesSwitch.setChecked(prefs.getBoolean("acepta_mensajes", true));
    }

    private void saveDataToPrefs() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_DUENO, MODE_PRIVATE).edit();
        editor.putString("direccion_recogida", direccionRecogida);
        if (ubicacionRecogida != null) {
            editor.putFloat("ubicacion_recogida_lat", (float) ubicacionRecogida.getLatitude());
            editor.putFloat("ubicacion_recogida_lng", (float) ubicacionRecogida.getLongitude());
        }
        editor.putBoolean("acepta_mensajes", messagesSwitch.isChecked());
        editor.apply();
    }

    private void completarRegistroDueno() {
        saveDataToPrefs();
        if (TextUtils.isEmpty(direccionRecogida)) {
            Toast.makeText(this, "La dirección de recogida es requerida", Toast.LENGTH_SHORT).show();
            return;
        }

        saveButton.setEnabled(false);
        saveButton.setText("Registrando...");

        SharedPreferences prefs = getSharedPreferences(PREFS_DUENO, MODE_PRIVATE);
        String email = prefs.getString("correo", "");
        String password = prefs.getString("password", "");

        if (email.isEmpty() || password.isEmpty()) {
            mostrarError("El correo y la contraseña no se encontraron. Por favor, vuelve al paso 1.");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    subirArchivosYGuardarDatos(uid);
                })
                .addOnFailureListener(e -> {
                    mostrarError("Error al crear usuario: " + e.getMessage());
                    saveButton.setEnabled(true);
                    saveButton.setText("Guardar");
                });
    }

    private void subirArchivosYGuardarDatos(String uid) {
        SharedPreferences prefs = getSharedPreferences(PREFS_DUENO, MODE_PRIVATE);

        String nombre = prefs.getString("nombre", "").replaceAll("\\s", "");
        String apellido = prefs.getString("apellido", "").replaceAll("\\s", "");
        String cedula = prefs.getString("cedula", "");
        String userFolder = uid + "_" + cedula + "_" + nombre + "_" + apellido;

        Map<String, Uri> filesToUpload = new HashMap<>();
        addFileToUpload(filesToUpload, "foto_perfil", prefs.getString("fotoPerfilUri", null));
        addFileToUpload(filesToUpload, "selfie", prefs.getString("selfieUri", null));

        if (filesToUpload.isEmpty()) {
            Log.d(TAG, "No hay archivos para subir, guardando datos directamente.");
            guardarDatosEnFirestore(uid, new HashMap<>());
            return;
        }

        List<Task<Uri>> uploadTasks = new ArrayList<>();
        Map<String, Object> downloadUrls = new HashMap<>();

        for (Map.Entry<String, Uri> entry : filesToUpload.entrySet()) {
            String key = entry.getKey();
            Uri uri = entry.getValue();
            String extension = getFileExtension(uri);
            String folder = key.equals("foto_perfil") ? "foto_de_perfil" : "selfie";
            String fileName = key + "." + extension;
            
            StorageReference ref = storage.getReference().child(folder + "/" + userFolder + "/" + fileName);

            uploadTasks.add(
                ref.putFile(uri).continueWithTask(task -> {
                    if (!task.isSuccessful()) { throw task.getException(); }
                    return ref.getDownloadUrl();
                }).addOnSuccessListener(url -> {
                    downloadUrls.put(key + "_url", url.toString());
                })
            );
        }

        Tasks.whenAllSuccess(uploadTasks).addOnSuccessListener(results -> {
            Log.d(TAG, "Archivos de dueño subidos con éxito.");
            guardarDatosEnFirestore(uid, downloadUrls);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Fallo al subir archivos de dueño", e);
            mostrarError("Error al subir imágenes: " + e.getMessage());
            if (mAuth.getCurrentUser() != null) { mAuth.getCurrentUser().delete(); }
            saveButton.setEnabled(true);
            saveButton.setText("Guardar");
        });
    }

    private void addFileToUpload(Map<String, Uri> files, String key, String uriString) {
        if (uriString != null && !uriString.isEmpty()) {
            files.put(key, Uri.parse(uriString));
        }
    }

    private String getFileExtension(Uri uri) {
        ContentResolver cR = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        String type = cR.getType(uri);
        return mime.getExtensionFromMimeType(type);
    }

    private void guardarDatosEnFirestore(String uid, Map<String, Object> urls) {
        SharedPreferences prefs = getSharedPreferences(PREFS_DUENO, MODE_PRIVATE);

        Map<String, Object> usuarioData = new HashMap<>();
        usuarioData.put("nombre", prefs.getString("nombre", ""));
        usuarioData.put("apellido", prefs.getString("apellido", ""));
        usuarioData.put("cedula", prefs.getString("cedula", ""));
        usuarioData.put("correo", prefs.getString("correo", ""));
        usuarioData.put("telefono", prefs.getString("telefono", ""));
        usuarioData.put("direccion", prefs.getString("domicilio", ""));
        float lat = prefs.getFloat("domicilio_lat", 0);
        float lng = prefs.getFloat("domicilio_lng", 0);
        if (lat != 0 && lng != 0) {
            usuarioData.put("direccion_coordenadas", new GeoPoint(lat, lng));
        }
        usuarioData.put("fecha_registro", FieldValue.serverTimestamp());
        usuarioData.put("activo", true);
        usuarioData.put("foto_perfil", urls.get("foto_perfil_url"));
        usuarioData.put("nombre_display", prefs.getString("nombre", "") + " " + prefs.getString("apellido", ""));
        usuarioData.put("perfil_ref", db.collection("duenos").document(uid));
        usuarioData.put("rol", "DUEÑO");
        usuarioData.put("selfie_url", urls.get("selfie_url"));

        Map<String, Object> duenoData = new HashMap<>();
        duenoData.put("direccion_recogida", direccionRecogida);
        duenoData.put("ubicacion_recogida", ubicacionRecogida);
        duenoData.put("acepta_terminos", prefs.getBoolean("acepta_terminos", false));
        duenoData.put("verificacion_estado", "PENDIENTE");
        duenoData.put("verificacion_fecha", FieldValue.serverTimestamp());
        duenoData.put("ultima_actualizacion", FieldValue.serverTimestamp());
        duenoData.put("acepta_mensajes", messagesSwitch.isChecked());

        db.runBatch(batch -> {
            batch.set(db.collection("usuarios").document(uid), usuarioData);
            batch.set(db.collection("duenos").document(uid), duenoData);
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Registro de dueño completado en Firestore.");
            guardarMetodoDePago(uid, prefs);
            prefs.edit().clear().apply();
            new AlertDialog.Builder(this)
                .setTitle("¡Registro Exitoso!")
                .setMessage("Tu cuenta ha sido creada y está en proceso de revisión.")
                .setPositiveButton("Entendido", (dialog, which) -> {
                    Intent intent = new Intent(this, MascotaRegistroPaso1Activity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
        }).addOnFailureListener(e -> {
            mostrarError("Error final al guardar tu perfil: " + e.getMessage());
            if (mAuth.getCurrentUser() != null) { mAuth.getCurrentUser().delete(); }
            saveButton.setEnabled(true);
            saveButton.setText("Guardar");
        });
    }

    private void mostrarError(String msg) {
        new AlertDialog.Builder(this)
                .setTitle("Error en el Registro")
                .setMessage(msg)
                .setPositiveButton("Entendido", null)
                .show();
    }

    private void guardarMetodoDePago(String uid, SharedPreferences prefs) {
        boolean metodoPagoCompleto = prefs.getBoolean("metodo_pago_completo", false);
        if (!metodoPagoCompleto) {
            return; // No hay método de pago para guardar
        }

        String banco = prefs.getString("pago_banco", "");
        String cuenta = prefs.getString("pago_cuenta", "");

        if (banco.isEmpty() || cuenta.isEmpty()) {
            Log.w(TAG, "metodo_pago_completo era true, pero los datos del banco/cuenta están vacíos.");
            return;
        }

        Map<String, Object> metodoPagoData = new HashMap<>();
        metodoPagoData.put("banco", banco);
        metodoPagoData.put("numero_cuenta", cuenta);
        metodoPagoData.put("predeterminado", true); // El primer método siempre es el predeterminado
        metodoPagoData.put("fecha_registro", FieldValue.serverTimestamp());

        db.collection("usuarios").document(uid).collection("metodos_pago")
                .add(metodoPagoData)
                .addOnSuccessListener(documentReference -> Log.d(TAG, "Método de pago guardado con ID: " + documentReference.getId()))
                .addOnFailureListener(e -> Log.e(TAG, "Error al guardar método de pago", e));
    }
}
