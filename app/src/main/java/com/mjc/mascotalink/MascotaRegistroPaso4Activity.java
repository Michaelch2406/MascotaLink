package com.mjc.mascotalink;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class MascotaRegistroPaso4Activity extends AppCompatActivity {

    private static final String TAG = "MascotaRegistroPaso4";

    private ImageView arrowBack;
    private TextInputEditText rutinaPaseoEditText, tipoCorreaArnesEditText, recompensasEditText, instruccionesEmergenciaEditText, notasAdicionalesEditText;
    private Button guardarButton;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    // New member variables for owner's name and surname
    private String duenoNombre;
    private String duenoApellido;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registro_paso4);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        arrowBack = findViewById(R.id.arrow_back);
        rutinaPaseoEditText = findViewById(R.id.rutinaPaseoEditText);
        tipoCorreaArnesEditText = findViewById(R.id.tipoCorreaArnesEditText);
        recompensasEditText = findViewById(R.id.recompensasEditText);
        instruccionesEmergenciaEditText = findViewById(R.id.instruccionesEmergenciaEditText);
        notasAdicionalesEditText = findViewById(R.id.notasAdicionalesEditText);
        guardarButton = findViewById(R.id.guardarButton);

        setupListeners();
        validateInputs();
        loadDuenoDataAndProceed(); // Load owner data before enabling save
    }

    private void setupListeners() {
        arrowBack.setOnClickListener(v -> finish());
        guardarButton.setOnClickListener(v -> guardarMascotaCompleta());

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                validateInputs();
            }
        };

        rutinaPaseoEditText.addTextChangedListener(textWatcher);
        tipoCorreaArnesEditText.addTextChangedListener(textWatcher);
        recompensasEditText.addTextChangedListener(textWatcher);
        instruccionesEmergenciaEditText.addTextChangedListener(textWatcher);
        notasAdicionalesEditText.addTextChangedListener(textWatcher);
    }

    private void validateInputs() {
        boolean allFilled = !rutinaPaseoEditText.getText().toString().trim().isEmpty()
                && !tipoCorreaArnesEditText.getText().toString().trim().isEmpty()
                && !recompensasEditText.getText().toString().trim().isEmpty()
                && !instruccionesEmergenciaEditText.getText().toString().trim().isEmpty()
                && !notasAdicionalesEditText.getText().toString().trim().isEmpty();
        // Only enable if owner data is loaded too
        guardarButton.setEnabled(allFilled && duenoNombre != null && duenoApellido != null);
    }

    private void loadDuenoDataAndProceed() {
        String currentDuenoId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (currentDuenoId == null) {
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("usuarios").document(currentDuenoId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                duenoNombre = documentSnapshot.getString("nombre");
                duenoApellido = documentSnapshot.getString("apellido");
                validateInputs(); // Re-validate after owner data is loaded
            } else {
                Toast.makeText(this, "Error: Datos del dueño no encontrados.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error al cargar datos del dueño: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private String getFileExtension(Uri uri) {
        ContentResolver cR = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getExtensionFromMimeType(cR.getType(uri));
    }

    private void guardarMascotaCompleta() {
        guardarButton.setEnabled(false); // Prevent double clicks
        String duenoId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        Intent intent = getIntent();
        String fotoUriString = intent.getStringExtra("foto_uri");
        String petName = intent.getStringExtra("nombre");

        if (fotoUriString == null) {
            mostrarErrorDialog("No se encontró la imagen de la mascota. Por favor, vuelve al paso 1.");
            guardarButton.setEnabled(true);
            return;
        }
        Uri fotoUri = Uri.parse(fotoUriString);

        String extension = getFileExtension(fotoUri);
        
        // New file name format
        String sanitizedDuenoNombre = duenoNombre != null ? duenoNombre.replaceAll("\\s", "") : "";
        String sanitizedDuenoApellido = duenoApellido != null ? duenoApellido.replaceAll("\\s", "") : "";
        String sanitizedPetName = petName != null ? petName.replaceAll("\\s", "") : "";

        String fileName = String.format(Locale.ROOT, "%s_%s_%s_%s_mascota.%s",
                duenoId, sanitizedDuenoNombre, sanitizedDuenoApellido, sanitizedPetName, extension);

        StorageReference storageRef = storage.getReference().child("foto_perfil_mascota/" + fileName);

        Log.d(TAG, "UID del dueño: " + duenoId);
        Log.d(TAG, "Ruta de Storage construida (MascotaRegistroPaso4): " + storageRef.getPath());

        storageRef.putFile(fotoUri)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String fotoUrl = uri.toString();
                    crearDocumentoMascota(duenoId, intent, fotoUrl);
                }))
                .addOnFailureListener(e -> {
                    Log.e("Storage", "Error al subir foto", e);
                    mostrarErrorDialog(getString(R.string.error_registro_mascota) + ": " + e.getMessage());
                    guardarButton.setEnabled(true);
                });
    }

    private void crearDocumentoMascota(String duenoId, Intent intent, String fotoUrl) {
        Map<String, Object> mascota = new HashMap<>();

        // Pantalla 1 - Información Básica
        mascota.put("nombre", intent.getStringExtra("nombre"));
        mascota.put("raza", intent.getStringExtra("raza"));
        mascota.put("sexo", intent.getStringExtra("sexo"));
        mascota.put("fecha_nacimiento", new com.google.firebase.Timestamp(new java.util.Date(intent.getLongExtra("fecha_nacimiento", 0))));
        mascota.put("tamano", intent.getStringExtra("tamano"));
        mascota.put("peso", intent.getDoubleExtra("peso", 0.0));
        mascota.put("foto_principal_url", fotoUrl);
        mascota.put("fecha_registro", FieldValue.serverTimestamp());
        mascota.put("ultima_actualizacion", FieldValue.serverTimestamp());
        mascota.put("activo", true); // FIX: Añadir el campo 'activo' por defecto

        // Pantalla 2 - Salud
        Map<String, Object> salud = new HashMap<>();
        salud.put("vacunas_al_dia", intent.getBooleanExtra("vacunas_al_dia", false));
        salud.put("desparasitacion_aldia", intent.getBooleanExtra("desparasitacion_al_dia", false));
        long ultimaVisita = intent.getLongExtra("ultima_visita_vet", 0);
        if (ultimaVisita != 0) {
            salud.put("ultima_visita_vet", new com.google.firebase.Timestamp(new java.util.Date(ultimaVisita)));
        } else {
            salud.put("ultima_visita_vet", null);
        }
        salud.put("condiciones_medicas", intent.getStringExtra("condiciones_medicas"));
        salud.put("medicamentos_actuales", intent.getStringExtra("medicamentos_actuales"));
        salud.put("veterinario_nombre", intent.getStringExtra("veterinario_nombre"));
        salud.put("veterinario_telefono", intent.getStringExtra("veterinario_telefono"));
        mascota.put("salud", salud);

        // Pantalla 3 - Comportamiento
        Map<String, Object> comportamiento = new HashMap<>();
        comportamiento.put("nivel_energia", intent.getStringExtra("nivel_energia"));
        comportamiento.put("con_personas", intent.getStringExtra("con_personas"));
        comportamiento.put("con_otros_animales", intent.getStringExtra("con_otros_animales"));
        comportamiento.put("con_otros_perros", intent.getStringExtra("con_otros_perros"));
        comportamiento.put("habitos_correa", intent.getStringExtra("habitos_correa"));
        comportamiento.put("comandos_conocidos", intent.getStringExtra("comandos_conocidos"));
        comportamiento.put("miedos_fobias", intent.getStringExtra("miedos_fobias"));
        comportamiento.put("manias_habitos", intent.getStringExtra("manias_habitos"));
        mascota.put("comportamiento", comportamiento);

        // Pantalla 4 - Instrucciones
        Map<String, Object> instrucciones = new HashMap<>();
        instrucciones.put("rutina_paseo", rutinaPaseoEditText.getText().toString().trim());
        instrucciones.put("tipo_correa_arnes", tipoCorreaArnesEditText.getText().toString().trim());
        instrucciones.put("recompensas", recompensasEditText.getText().toString().trim());
        instrucciones.put("instrucciones_emergencia", instruccionesEmergenciaEditText.getText().toString().trim());
        instrucciones.put("notas_adicionales", notasAdicionalesEditText.getText().toString().trim());
        mascota.put("instrucciones", instrucciones);

        db.collection("duenos").document(duenoId)
                .collection("mascotas")
                .add(mascota)
                .addOnSuccessListener(documentReference -> {
                    Log.d("Registro", "Mascota registrada: " + documentReference.getId());
                    mostrarMensajeExito();
                })
                .addOnFailureListener(e -> {
                    Log.e("Registro", "Error: " + e.getMessage());
                    mostrarErrorDialog(getString(R.string.error_registro_mascota) + ": " + e.getMessage());
                    guardarButton.setEnabled(true);
                });
    }

    private void mostrarMensajeExito() {
        Intent intent = new Intent(this, MascotaRegistradaActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void mostrarErrorDialog(String mensaje) {
        new AlertDialog.Builder(this)
                .setTitle("Error en el Registro")
                .setMessage(mensaje)
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}
