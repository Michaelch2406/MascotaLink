package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuenoRegistroPaso3Activity extends AppCompatActivity {

    private static final String TAG = "DuenoRegistroPaso3";
    private static final String PREFS_DUENO = "WizardDueno";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private SwitchMaterial messagesSwitch;
    private Button saveButton;

    // Variables para la dirección de recogida (Paso 3)
    private String direccionRecogida;
    private GeoPoint ubicacionRecogida;
    private AutocompleteSupportFragment autocompleteFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dueno_registro_paso3);

        setupFirebase();
        setupViews();
        setupListeners();
        setupPlacesAutocomplete();
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

        messagesSwitch = findViewById(R.id.messagesSwitch);
        saveButton = findViewById(R.id.saveButton);

        Button paymentMethodButton = findViewById(R.id.paymentMethodButton);
        paymentMethodButton.setOnClickListener(v -> {
            saveDataToPrefs(); // Guardar estado actual antes de cambiar de actividad
            Intent intent = new Intent(this, MetodoPagoActivity.class);
            intent.putExtra("prefs", PREFS_DUENO);
            startActivity(intent);
        });
    }

    private void setupListeners() {
        saveButton.setOnClickListener(v -> completarRegistroDueno());
    }

    private void setupPlacesAutocomplete() {
        // Initialization is now done in MyApplication.java
        autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment_address);

        if (autocompleteFragment != null) {
            autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG));
            autocompleteFragment.setHint("Actualizar dirección de recogida...");
            autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                @Override
                public void onPlaceSelected(@NonNull Place place) {
                    direccionRecogida = place.getAddress();
                    if (place.getLatLng() != null) {
                        ubicacionRecogida = new GeoPoint(place.getLatLng().latitude, place.getLatLng().longitude);
                    }
                    Log.i(TAG, "Place Updated: " + direccionRecogida);
                }

                @Override
                public void onError(@NonNull Status status) {
                    Log.e(TAG, "An error occurred: " + status);
                }
            });
        }
    }

    private void loadDataFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_DUENO, MODE_PRIVATE);
        // Cargar la dirección del paso 1 como valor inicial
        direccionRecogida = prefs.getString("domicilio", "");
        float lat = prefs.getFloat("domicilio_lat", 0);
        float lng = prefs.getFloat("domicilio_lng", 0);
        if (lat != 0 && lng != 0) {
            ubicacionRecogida = new GeoPoint(lat, lng);
        }

        if (autocompleteFragment != null && !TextUtils.isEmpty(direccionRecogida)) {
            autocompleteFragment.setText(direccionRecogida);
        }

        messagesSwitch.setChecked(prefs.getBoolean("acepta_mensajes", true));
    }

    private void saveDataToPrefs() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_DUENO, MODE_PRIVATE).edit();
        // Guardar la dirección de recogida (potencialmente actualizada) y sus coordenadas
        editor.putString("direccion_recogida", direccionRecogida);
        if (ubicacionRecogida != null) {
            editor.putFloat("ubicacion_recogida_lat", (float) ubicacionRecogida.getLatitude());
            editor.putFloat("ubicacion_recogida_lng", (float) ubicacionRecogida.getLongitude());
        }
        editor.putBoolean("acepta_mensajes", messagesSwitch.isChecked());
        editor.apply();
    }

    private void completarRegistroDueno() {
        saveDataToPrefs(); // Guardar los últimos cambios antes de registrar

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
                    Log.d(TAG, "Usuario Dueño creado en Auth con UID: " + uid);
                    subirArchivosYGuardarDatos(uid);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al crear usuario dueño en Auth", e);
                    mostrarError("Error al crear usuario: " + e.getMessage());
                    saveButton.setEnabled(true);
                    saveButton.setText("Guardar");
                });
    }

    private String getFileExtension(Uri uri) {
        ContentResolver cR = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        String type = cR.getType(uri);
        return mime.getExtensionFromMimeType(type);
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

    private void guardarDatosEnFirestore(String uid, Map<String, Object> urls) {
        SharedPreferences prefs = getSharedPreferences(PREFS_DUENO, MODE_PRIVATE);

        // 1. Documento para la colección 'usuarios'
        Map<String, Object> usuarioData = new HashMap<>();
        usuarioData.put("nombre", prefs.getString("nombre", ""));
        usuarioData.put("apellido", prefs.getString("apellido", ""));
        usuarioData.put("cedula", prefs.getString("cedula", ""));
        usuarioData.put("correo", prefs.getString("correo", ""));
        usuarioData.put("telefono", prefs.getString("telefono", ""));
        usuarioData.put("direccion", prefs.getString("domicilio", "")); // Dirección principal del Paso 1
        float lat = prefs.getFloat("domicilio_lat", 0);
        float lng = prefs.getFloat("domicilio_lng", 0);
        if (lat != 0 && lng != 0) {
            usuarioData.put("direccion_coordenadas", new GeoPoint(lat, lng)); // Coordenadas del Paso 1
        }
        usuarioData.put("fecha_registro", FieldValue.serverTimestamp());
        usuarioData.put("activo", true);
        usuarioData.put("foto_perfil", urls.get("foto_perfil_url"));
        usuarioData.put("nombre_display", prefs.getString("nombre", "") + " " + prefs.getString("apellido", ""));
        usuarioData.put("perfil_ref", db.collection("duenos").document(uid));
        usuarioData.put("rol", "DUEÑO");
        usuarioData.put("selfie_url", urls.get("selfie_url"));

        // 2. Documento para la colección 'duenos'
        Map<String, Object> duenoData = new HashMap<>();
        duenoData.put("direccion_recogida", direccionRecogida); // Dirección de recogida de este paso (Paso 3)
        duenoData.put("ubicacion_recogida", ubicacionRecogida); // Coordenadas de recogida de este paso (Paso 3)
        duenoData.put("acepta_terminos", prefs.getBoolean("acepta_terminos", false));
        duenoData.put("verificacion_estado", "PENDIENTE");
        duenoData.put("verificacion_fecha", FieldValue.serverTimestamp());
        duenoData.put("ultima_actualizacion", FieldValue.serverTimestamp());
        duenoData.put("acepta_mensajes", prefs.getBoolean("acepta_mensajes", true));

        // 3. Escritura atómica en lote
        db.runBatch(batch -> {
            batch.set(db.collection("usuarios").document(uid), usuarioData);
            batch.set(db.collection("duenos").document(uid), duenoData);
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Registro de dueño completado en Firestore.");
            guardarMetodoDePago(uid, prefs);
            prefs.edit().clear().apply(); // Limpiar datos temporales

            new AlertDialog.Builder(this)
                .setTitle("¡Registro Exitoso!")
                .setMessage("Tu cuenta ha sido creada y está en proceso de revisión. Recibirás una notificación cuando sea aprobada.")
                .setPositiveButton("Entendido", (dialog, which) -> {
                    Intent intent = new Intent(this, MascotaRegistroPaso1Activity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al guardar datos de dueño en Firestore", e);
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