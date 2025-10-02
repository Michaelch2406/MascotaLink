package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuenoRegistroPaso3Activity extends AppCompatActivity {

    private static final String TAG = "DuenoRegistroPaso3";
    private static final String PREFS_DUENO = "WizardDueno";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private EditText addressEditText;
    private SwitchMaterial messagesSwitch;
    private Button saveButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dueno_registro_paso3);

        setupFirebase();
        setupViews();
        setupListeners();
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
        messagesSwitch = findViewById(R.id.messagesSwitch);
        saveButton = findViewById(R.id.saveButton);

        Button paymentMethodButton = findViewById(R.id.paymentMethodButton);
        paymentMethodButton.setOnClickListener(v -> {
            // Guardamos el estado actual antes de ir a otra pantalla
            saveDataToPrefs();
            Intent intent = new Intent(this, MetodoPagoActivity.class);
            startActivity(intent);
        });
    }

    private void setupListeners() {
        saveButton.setOnClickListener(v -> completarRegistroDueno());
    }

    private void loadDataFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_DUENO, MODE_PRIVATE);
        addressEditText.setText(prefs.getString("direccion_recogida", ""));
        messagesSwitch.setChecked(prefs.getBoolean("acepta_mensajes", true));
    }

    private void saveDataToPrefs() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_DUENO, MODE_PRIVATE).edit();
        editor.putString("direccion_recogida", addressEditText.getText().toString().trim());
        editor.putBoolean("acepta_mensajes", messagesSwitch.isChecked());
        editor.apply();
    }

    private void completarRegistroDueno() {
        saveDataToPrefs(); // Guardar los últimos datos de la UI
        String address = addressEditText.getText().toString().trim();
        if (TextUtils.isEmpty(address)) {
            addressEditText.setError("La dirección es requerida");
            addressEditText.requestFocus();
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

    private void subirArchivosYGuardarDatos(String uid) {
        SharedPreferences prefs = getSharedPreferences(PREFS_DUENO, MODE_PRIVATE);
        Map<String, Uri> filesToUpload = new HashMap<>();

        // Añadir archivos a la lista de subida
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
            String storagePath = key.equals("foto_perfil") ? "foto_de_perfil/" + uid : "selfie/" + uid;
            StorageReference ref = storage.getReference().child(storagePath);

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
            // Limpieza: eliminar usuario de Auth si falla la subida
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
        usuarioData.put("direccion", prefs.getString("direccion", ""));
        usuarioData.put("fecha_registro", FieldValue.serverTimestamp());
        usuarioData.put("activo", true);
        usuarioData.put("foto_perfil", urls.get("foto_perfil_url"));
        usuarioData.put("nombre_display", prefs.getString("nombre", "") + " " + prefs.getString("apellido", ""));
        usuarioData.put("perfil_ref", db.collection("duenos").document(uid));
        usuarioData.put("rol", "DUEÑO");
        usuarioData.put("selfie_url", urls.get("selfie_url"));

        // 2. Documento para la colección 'duenos'
        Map<String, Object> duenoData = new HashMap<>();
        duenoData.put("cedula", prefs.getString("cedula", ""));
        duenoData.put("direccion_recogida", prefs.getString("direccion_recogida", ""));
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
            prefs.edit().clear().apply(); // Limpiar datos temporales
            Toast.makeText(this, "¡Registro completado!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, DuenoRegistroPaso4Activity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
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
}