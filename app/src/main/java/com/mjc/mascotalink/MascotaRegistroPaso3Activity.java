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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.mjc.mascotalink.utils.InputUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class MascotaRegistroPaso3Activity extends AppCompatActivity {

    private static final String TAG = "MascotaRegistroPaso3";
    private static final long DEBOUNCE_DELAY_MS = 500;
    private static final long RATE_LIMIT_MS = 2000;

    private ImageView arrowBack;
    private RadioGroup nivelEnergiaRadioGroup, conPersonasRadioGroup, conAnimalesRadioGroup, conPerrosRadioGroup, habitosCorreaRadioGroup;
    private TextInputEditText comandosConocidosEditText, miedosFobiasEditText, maniasHabitosEditText;
    private TextInputEditText rutinaPaseoEditText, tipoCorreaArnesEditText, recompensasEditText, instruccionesEmergenciaEditText, notasAdicionalesEditText;
    private Button guardarButton;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    private String duenoNombre;
    private String duenoApellido;

    private TextWatcher validationTextWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registro_paso3);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        arrowBack = findViewById(R.id.arrow_back);
        nivelEnergiaRadioGroup = findViewById(R.id.nivelEnergiaRadioGroup);
        conPersonasRadioGroup = findViewById(R.id.conPersonasRadioGroup);
        conAnimalesRadioGroup = findViewById(R.id.conAnimalesRadioGroup);
        conPerrosRadioGroup = findViewById(R.id.conPerrosRadioGroup);
        habitosCorreaRadioGroup = findViewById(R.id.habitosCorreaRadioGroup);
        comandosConocidosEditText = findViewById(R.id.comandosConocidosEditText);
        miedosFobiasEditText = findViewById(R.id.miedosFobiasEditText);
        maniasHabitosEditText = findViewById(R.id.maniasHabitosEditText);
        rutinaPaseoEditText = findViewById(R.id.rutinaPaseoEditText);
        tipoCorreaArnesEditText = findViewById(R.id.tipoCorreaArnesEditText);
        recompensasEditText = findViewById(R.id.recompensasEditText);
        instruccionesEmergenciaEditText = findViewById(R.id.instruccionesEmergenciaEditText);
        notasAdicionalesEditText = findViewById(R.id.notasAdicionalesEditText);
        guardarButton = findViewById(R.id.guardarButton);

        setupListeners();
        validateInputs();
        loadDuenoDataAndProceed();
    }

    private void setupListeners() {
        arrowBack.setOnClickListener(v -> finish());
        guardarButton.setOnClickListener(InputUtils.createSafeClickListener(RATE_LIMIT_MS, v -> guardarMascotaCompleta()));

        RadioGroup.OnCheckedChangeListener radioListener = (group, checkedId) -> validateInputs();
        nivelEnergiaRadioGroup.setOnCheckedChangeListener(radioListener);
        conPersonasRadioGroup.setOnCheckedChangeListener(radioListener);
        conAnimalesRadioGroup.setOnCheckedChangeListener(radioListener);
        conPerrosRadioGroup.setOnCheckedChangeListener(radioListener);
        habitosCorreaRadioGroup.setOnCheckedChangeListener(radioListener);

        validationTextWatcher = InputUtils.createDebouncedTextWatcher(
            "validacion_mascota_paso3",
            DEBOUNCE_DELAY_MS,
            text -> validateInputs()
        );
        comandosConocidosEditText.addTextChangedListener(validationTextWatcher);
        miedosFobiasEditText.addTextChangedListener(validationTextWatcher);
        maniasHabitosEditText.addTextChangedListener(validationTextWatcher);
        rutinaPaseoEditText.addTextChangedListener(validationTextWatcher);
        recompensasEditText.addTextChangedListener(validationTextWatcher);
        instruccionesEmergenciaEditText.addTextChangedListener(validationTextWatcher);
        tipoCorreaArnesEditText.addTextChangedListener(validationTextWatcher);
        notasAdicionalesEditText.addTextChangedListener(validationTextWatcher);
    }

    private void validateInputs() {
        boolean allFilled = nivelEnergiaRadioGroup.getCheckedRadioButtonId() != -1
                && conPersonasRadioGroup.getCheckedRadioButtonId() != -1
                && conAnimalesRadioGroup.getCheckedRadioButtonId() != -1
                && conPerrosRadioGroup.getCheckedRadioButtonId() != -1
                && habitosCorreaRadioGroup.getCheckedRadioButtonId() != -1
                && !comandosConocidosEditText.getText().toString().trim().isEmpty()
                && !miedosFobiasEditText.getText().toString().trim().isEmpty()
                && !maniasHabitosEditText.getText().toString().trim().isEmpty()
                && !rutinaPaseoEditText.getText().toString().trim().isEmpty()
                && !recompensasEditText.getText().toString().trim().isEmpty()
                && !instruccionesEmergenciaEditText.getText().toString().trim().isEmpty();
        guardarButton.setEnabled(allFilled && duenoNombre != null && duenoApellido != null);
    }

    private void loadDuenoDataAndProceed() {
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity en proceso de destrucción, operación cancelada");
            return;
        }

        String currentDuenoId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (currentDuenoId == null) {
            Log.e(TAG, "Usuario no autenticado");
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("usuarios").document(currentDuenoId).get().addOnSuccessListener(documentSnapshot -> {
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "Activity destruida después de cargar datos del dueño");
                return;
            }
            if (documentSnapshot.exists()) {
                duenoNombre = documentSnapshot.getString("nombre");
                duenoApellido = documentSnapshot.getString("apellido");
                Log.d(TAG, "Datos del dueño cargados correctamente");
                validateInputs();
            } else {
                Log.e(TAG, "Documento del dueño no existe");
                Toast.makeText(this, "Error: Datos del dueño no encontrados.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            if (isFinishing() || isDestroyed()) return;
            Log.e(TAG, "Error al cargar datos del dueño", e);
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
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity en proceso de destrucción, operación cancelada");
            return;
        }

        guardarButton.setEnabled(false);

        try {
            String duenoId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
            Intent intent = getIntent();
            String fotoUriString = intent.getStringExtra("foto_uri");
            String petName = intent.getStringExtra("nombre");

            if (fotoUriString == null) {
                Log.e(TAG, "URI de foto de mascota no encontrado");
                mostrarErrorDialog("No se encontró la imagen de la mascota. Por favor, vuelve al paso 1.");
                guardarButton.setEnabled(true);
                return;
            }
            Uri fotoUri = Uri.parse(fotoUriString);

            String extension = getFileExtension(fotoUri);

            String sanitizedDuenoNombre = duenoNombre != null ? duenoNombre.replaceAll("\\s", "") : "";
            String sanitizedDuenoApellido = duenoApellido != null ? duenoApellido.replaceAll("\\s", "") : "";
            String sanitizedPetName = petName != null ? petName.replaceAll("\\s", "") : "";

            String fileName = String.format(Locale.ROOT, "%s_%s_%s_%s_mascota.%s",
                    duenoId, sanitizedDuenoNombre, sanitizedDuenoApellido, sanitizedPetName, extension);

            StorageReference storageRef = storage.getReference().child("foto_perfil_mascota/" + fileName);

            Log.d(TAG, "UID del dueño: " + duenoId);
            Log.d(TAG, "Ruta de Storage construida: " + storageRef.getPath());

            storageRef.putFile(fotoUri)
                    .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        if (isFinishing() || isDestroyed()) {
                            Log.w(TAG, "Activity destruida después de subir foto");
                            return;
                        }
                        String fotoUrl = uri.toString();
                        Log.d(TAG, "Foto de mascota subida exitosamente");
                        crearDocumentoMascota(duenoId, intent, fotoUrl);
                    }))
                    .addOnFailureListener(e -> {
                        if (isFinishing() || isDestroyed()) return;
                        Log.e(TAG, "Error al subir foto de mascota", e);
                        mostrarErrorDialog("Error al subir la foto: " + e.getMessage());
                        guardarButton.setEnabled(true);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error al iniciar guardado de mascota", e);
            mostrarErrorDialog("Error inesperado. Por favor, intenta nuevamente.");
            guardarButton.setEnabled(true);
        }
    }

    private void crearDocumentoMascota(String duenoId, Intent intent, String fotoUrl) {
        try {
            String nombreSanitizado = InputUtils.sanitizeInput(intent.getStringExtra("nombre"));
            String razaSanitizada = InputUtils.sanitizeInput(intent.getStringExtra("raza"));
            String condicionesSanitizadas = InputUtils.sanitizeInput(intent.getStringExtra("condiciones_medicas"));
            String medicamentosSanitizados = InputUtils.sanitizeInput(intent.getStringExtra("medicamentos_actuales"));
            String veterinarioNombreSanitizado = InputUtils.sanitizeInput(intent.getStringExtra("veterinario_nombre"));
            String veterinarioTelefonoSanitizado = InputUtils.sanitizeInput(intent.getStringExtra("veterinario_telefono"));
            String comandosSanitizados = InputUtils.sanitizeInput(comandosConocidosEditText.getText().toString().trim());
            String miedosSanitizados = InputUtils.sanitizeInput(miedosFobiasEditText.getText().toString().trim());
            String maniasSanitizadas = InputUtils.sanitizeInput(maniasHabitosEditText.getText().toString().trim());
            String rutinaSanitizada = InputUtils.sanitizeInput(rutinaPaseoEditText.getText().toString().trim());
            String correaSanitizada = InputUtils.sanitizeInput(tipoCorreaArnesEditText.getText().toString().trim());
            String recompensasSanitizadas = InputUtils.sanitizeInput(recompensasEditText.getText().toString().trim());
            String instruccionesSanitizadas = InputUtils.sanitizeInput(instruccionesEmergenciaEditText.getText().toString().trim());
            String notasSanitizadas = InputUtils.sanitizeInput(notasAdicionalesEditText.getText().toString().trim());

            Map<String, Object> mascota = new HashMap<>();

            mascota.put("nombre", nombreSanitizado);
            mascota.put("raza", razaSanitizada);
            mascota.put("sexo", intent.getStringExtra("sexo"));
            mascota.put("fecha_nacimiento", new com.google.firebase.Timestamp(new java.util.Date(intent.getLongExtra("fecha_nacimiento", 0))));
            mascota.put("tamano", intent.getStringExtra("tamano"));
            mascota.put("peso", intent.getDoubleExtra("peso", 0.0));
            mascota.put("foto_principal_url", fotoUrl);
            mascota.put("fecha_registro", FieldValue.serverTimestamp());
            mascota.put("ultima_actualizacion", FieldValue.serverTimestamp());
            mascota.put("activo", true);

            Map<String, Object> salud = new HashMap<>();
            salud.put("esterilizado", intent.getBooleanExtra("esterilizado", false));
            salud.put("vacunas_al_dia", intent.getBooleanExtra("vacunas_al_dia", false));
            salud.put("desparasitacion_aldia", intent.getBooleanExtra("desparasitacion_al_dia", false));
            long ultimaVisita = intent.getLongExtra("ultima_visita_vet", 0);
            if (ultimaVisita != 0) {
                salud.put("ultima_visita_vet", new com.google.firebase.Timestamp(new java.util.Date(ultimaVisita)));
            } else {
                salud.put("ultima_visita_vet", null);
            }
            salud.put("condiciones_medicas", condicionesSanitizadas);
            salud.put("medicamentos_actuales", medicamentosSanitizados);
            salud.put("veterinario_nombre", veterinarioNombreSanitizado);
            salud.put("veterinario_telefono", veterinarioTelefonoSanitizado);
            mascota.put("salud", salud);

            Map<String, Object> comportamiento = new HashMap<>();
            comportamiento.put("nivel_energia", getSelectedRadioButtonText(nivelEnergiaRadioGroup));
            comportamiento.put("con_personas", getSelectedRadioButtonText(conPersonasRadioGroup));
            comportamiento.put("con_otros_animales", getSelectedRadioButtonText(conAnimalesRadioGroup));
            comportamiento.put("con_otros_perros", getSelectedRadioButtonText(conPerrosRadioGroup));
            comportamiento.put("habitos_correa", getSelectedRadioButtonText(habitosCorreaRadioGroup));
            comportamiento.put("comandos_conocidos", comandosSanitizados);
            comportamiento.put("miedos_fobias", miedosSanitizados);
            comportamiento.put("manias_habitos", maniasSanitizadas);
            mascota.put("comportamiento", comportamiento);

            Map<String, Object> instrucciones = new HashMap<>();
            instrucciones.put("rutina_paseo", rutinaSanitizada);
            instrucciones.put("tipo_correa_arnes", correaSanitizada);
            instrucciones.put("recompensas", recompensasSanitizadas);
            instrucciones.put("instrucciones_emergencia", instruccionesSanitizadas);
            instrucciones.put("notas_adicionales", notasSanitizadas);
            mascota.put("instrucciones", instrucciones);

            db.collection("duenos").document(duenoId)
                    .collection("mascotas")
                    .add(mascota)
                    .addOnSuccessListener(documentReference -> {
                        if (isFinishing() || isDestroyed()) {
                            Log.w(TAG, "Activity destruida después de registrar mascota");
                            return;
                        }
                        Log.d(TAG, "Mascota registrada exitosamente: " + documentReference.getId());
                        mostrarMensajeExito();
                    })
                    .addOnFailureListener(e -> {
                        if (isFinishing() || isDestroyed()) return;
                        Log.e(TAG, "Error al registrar mascota en Firestore", e);
                        mostrarErrorDialog("Error al registrar mascota: " + e.getMessage());
                        guardarButton.setEnabled(true);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error al crear documento de mascota", e);
            mostrarErrorDialog("Error al procesar los datos de la mascota.");
            guardarButton.setEnabled(true);
        }
    }

    private void mostrarMensajeExito() {
        boolean fromReserva = getIntent().getBooleanExtra("FROM_RESERVA", false);

        if (fromReserva) {
            Toast.makeText(this, "Mascota registrada exitosamente", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ReservaActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        } else {
            Intent intent = new Intent(this, MascotaRegistradaActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void mostrarErrorDialog(String mensaje) {
        new AlertDialog.Builder(this)
                .setTitle("Error en el Registro")
                .setMessage(mensaje)
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private String getSelectedRadioButtonText(RadioGroup radioGroup) {
        int selectedId = radioGroup.getCheckedRadioButtonId();
        if (selectedId != -1) {
            RadioButton selectedRadioButton = findViewById(selectedId);
            return selectedRadioButton.getText().toString();
        }
        return "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (validationTextWatcher != null) {
            if (comandosConocidosEditText != null) comandosConocidosEditText.removeTextChangedListener(validationTextWatcher);
            if (miedosFobiasEditText != null) miedosFobiasEditText.removeTextChangedListener(validationTextWatcher);
            if (maniasHabitosEditText != null) maniasHabitosEditText.removeTextChangedListener(validationTextWatcher);
            if (rutinaPaseoEditText != null) rutinaPaseoEditText.removeTextChangedListener(validationTextWatcher);
            if (tipoCorreaArnesEditText != null) tipoCorreaArnesEditText.removeTextChangedListener(validationTextWatcher);
            if (recompensasEditText != null) recompensasEditText.removeTextChangedListener(validationTextWatcher);
            if (instruccionesEmergenciaEditText != null) instruccionesEmergenciaEditText.removeTextChangedListener(validationTextWatcher);
            if (notasAdicionalesEditText != null) notasAdicionalesEditText.removeTextChangedListener(validationTextWatcher);
        }

        InputUtils.cancelDebounce("validacion_mascota_paso3");

        Log.d(TAG, "Activity destruida y recursos limpiados");
    }
}