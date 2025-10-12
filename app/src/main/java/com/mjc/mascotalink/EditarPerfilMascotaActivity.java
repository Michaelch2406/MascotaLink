package com.mjc.mascotalink;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class EditarPerfilMascotaActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private Uri imageUri;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    // IDs
    private String duenoId;
    private String mascotaId;

    // UI Components
    private ImageView ivBack, ivAvatarMascota;
    private TextInputEditText etNombre, etRaza, etPeso;
    private ChipGroup chipGroupSexo, chipGroupTamano, chipGroupEnergia;
    private TextInputEditText etFechaNacimiento, etUltimaVisitaVet;
    private SwitchMaterial switchEsterilizado, switchVacunas, switchDesparasitacion;
    private TextInputEditText etCondicionesMedicas, etMedicamentos, etVeterinarioNombre, etVeterinarioTelefono;
    private TextInputEditText etConPersonas, etConOtrosPerros, etConOtrosAnimales, etHabitosCorrea, etComandosConocidos, etMiedosFobias, etManiasHabitos;
    private TextInputEditText etRutinaPaseo, etTipoCorreaArnes, etRecompensas, etInstruccionesEmergencia, etNotasAdicionales;
    private Button btnGuardar;

    private Calendar fechaNacimientoCalendar = Calendar.getInstance();
    private Calendar ultimaVisitaVetCalendar = Calendar.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editar_perfil_mascota);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        duenoId = getIntent().getStringExtra("dueno_id");
        mascotaId = getIntent().getStringExtra("mascota_id");

        if (duenoId == null || mascotaId == null) {
            Toast.makeText(this, "Error: IDs no proporcionados.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        setupListeners();
        loadMascotaData();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        ivAvatarMascota = findViewById(R.id.iv_avatar_mascota);

        // Basic Info
        etNombre = findViewById(R.id.et_nombre);
        etRaza = findViewById(R.id.et_raza);
        chipGroupSexo = findViewById(R.id.chipgroup_sexo);
        etFechaNacimiento = findViewById(R.id.et_fecha_nacimiento);
        chipGroupTamano = findViewById(R.id.chipgroup_tamano);
        etPeso = findViewById(R.id.et_peso);
        switchEsterilizado = findViewById(R.id.switch_esterilizado);

        // Salud
        switchVacunas = findViewById(R.id.switch_vacunas);
        switchDesparasitacion = findViewById(R.id.switch_desparasitacion);
        etCondicionesMedicas = findViewById(R.id.et_condiciones_medicas);
        etMedicamentos = findViewById(R.id.et_medicamentos);
        etUltimaVisitaVet = findViewById(R.id.et_ultima_visita_vet);
        etVeterinarioNombre = findViewById(R.id.et_veterinario_nombre);
        etVeterinarioTelefono = findViewById(R.id.et_veterinario_telefono);

        // Comportamiento
        chipGroupEnergia = findViewById(R.id.chipgroup_energia);
        etConPersonas = findViewById(R.id.et_con_personas);
        etConOtrosPerros = findViewById(R.id.et_con_otros_perros);
        etConOtrosAnimales = findViewById(R.id.et_con_otros_animales);
        etHabitosCorrea = findViewById(R.id.et_habitos_correa);
        etComandosConocidos = findViewById(R.id.et_comandos_conocidos);
        etMiedosFobias = findViewById(R.id.et_miedos_fobias);
        etManiasHabitos = findViewById(R.id.et_manias_habitos);

        // Instrucciones
        etRutinaPaseo = findViewById(R.id.et_rutina_paseo);
        etTipoCorreaArnes = findViewById(R.id.et_tipo_correa_arnes);
        etRecompensas = findViewById(R.id.et_recompensas);
        etInstruccionesEmergencia = findViewById(R.id.et_instrucciones_emergencia);
        etNotasAdicionales = findViewById(R.id.et_notas_adicionales);

        btnGuardar = findViewById(R.id.btn_guardar);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());
        btnGuardar.setOnClickListener(v -> saveMascotaData());

        ivAvatarMascota.setOnClickListener(v -> openFileChooser());

        // Date Pickers
        etFechaNacimiento.setOnClickListener(v -> showDatePickerDialog(fechaNacimientoCalendar, etFechaNacimiento));
        etUltimaVisitaVet.setOnClickListener(v -> showDatePickerDialog(ultimaVisitaVetCalendar, etUltimaVisitaVet));

        // Validation listener
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                validateForm();
            }
        };

        etNombre.addTextChangedListener(textWatcher);
        etRaza.addTextChangedListener(textWatcher);
    }

    private void showDatePickerDialog(Calendar calendar, TextInputEditText editText) {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
                    editText.setText(sdf.format(calendar.getTime()));
                    validateForm();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void validateForm() {
        boolean isNombreValid = !etNombre.getText().toString().trim().isEmpty();
        boolean isRazaValid = !etRaza.getText().toString().trim().isEmpty();
        // Add more validation as needed
        btnGuardar.setEnabled(isNombreValid && isRazaValid);
    }

    private void loadMascotaData() {
        db.collection("duenos").document(duenoId).collection("mascotas").document(mascotaId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        // Populate Basic Info
                        etNombre.setText(document.getString("nombre"));
                        etRaza.setText(document.getString("raza"));
                        etPeso.setText(document.contains("peso") ? String.valueOf(document.getDouble("peso")) : "");
                        setChipGroupValue(chipGroupSexo, document.getString("sexo"));
                        setChipGroupValue(chipGroupTamano, document.getString("tamano"));
                        switchEsterilizado.setChecked(document.contains("esterilizado") && Boolean.TRUE.equals(document.getBoolean("esterilizado")));

                        if (document.contains("fecha_nacimiento")) {
                            Timestamp ts = document.getTimestamp("fecha_nacimiento");
                            if (ts != null) {
                                fechaNacimientoCalendar.setTime(ts.toDate());
                                updateDateInView(etFechaNacimiento, fechaNacimientoCalendar.getTime());
                            }
                        }

                        String fotoUrl = document.getString("foto_principal_url");
                        if (fotoUrl != null && !fotoUrl.isEmpty()) {
                            Glide.with(this).load(fotoUrl).circleCrop().into(ivAvatarMascota);
                        }

                        // Populate Salud
                        if (document.contains("salud")) {
                            Map<String, Object> salud = (Map<String, Object>) document.get("salud");
                            if (salud != null) {
                                switchVacunas.setChecked(salud.containsKey("vacunas_al_dia") && Boolean.TRUE.equals(salud.get("vacunas_al_dia")));
                                switchDesparasitacion.setChecked(salud.containsKey("desparasitacion_aldia") && Boolean.TRUE.equals(salud.get("desparasitacion_aldia")));
                                etCondicionesMedicas.setText((String) salud.get("condiciones_medicas"));
                                etMedicamentos.setText((String) salud.get("medicamentos_actuales"));
                                etVeterinarioNombre.setText((String) salud.get("veterinario_nombre"));
                                etVeterinarioTelefono.setText((String) salud.get("veterinario_telefono"));
                                if (salud.containsKey("ultima_visita_vet")) {
                                    Timestamp ts = (Timestamp) salud.get("ultima_visita_vet");
                                    if (ts != null) {
                                        ultimaVisitaVetCalendar.setTime(ts.toDate());
                                        updateDateInView(etUltimaVisitaVet, ultimaVisitaVetCalendar.getTime());
                                    }
                                }
                            }
                        }

                        // Populate Comportamiento
                        if (document.contains("comportamiento")) {
                            Map<String, Object> comp = (Map<String, Object>) document.get("comportamiento");
                            if (comp != null) {
                                setChipGroupValue(chipGroupEnergia, (String) comp.get("nivel_energia"));
                                etConPersonas.setText((String) comp.get("con_personas"));
                                etConOtrosPerros.setText((String) comp.get("con_otros_perros"));
                                etConOtrosAnimales.setText((String) comp.get("con_otros_animales"));
                                etHabitosCorrea.setText((String) comp.get("habitos_correa"));
                                etComandosConocidos.setText((String) comp.get("comandos_conocidos"));
                                etMiedosFobias.setText((String) comp.get("miedos_fobias"));
                                etManiasHabitos.setText((String) comp.get("manias_habitos"));
                            }
                        }

                        // Populate Instrucciones
                        if (document.contains("instrucciones")) {
                            Map<String, Object> inst = (Map<String, Object>) document.get("instrucciones");
                            if (inst != null) {
                                etRutinaPaseo.setText((String) inst.get("rutina_paseo"));
                                etTipoCorreaArnes.setText((String) inst.get("tipo_correa_arnes"));
                                etRecompensas.setText((String) inst.get("recompensas"));
                                etInstruccionesEmergencia.setText((String) inst.get("instrucciones_emergencia"));
                                etNotasAdicionales.setText((String) inst.get("notas_adicionales"));
                            }
                        }
                        validateForm(); // Validate after loading data
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error al cargar datos: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void saveMascotaData() {
        btnGuardar.setEnabled(false); // Disable button to prevent multiple clicks

        if (imageUri != null) {
            uploadImageAndSaveData();
        } else {
            saveData(null);
        }
    }

    private void uploadImageAndSaveData() {
        StorageReference fileReference = storage.getReference("foto_perfil_mascota/" + mascotaId);
        fileReference.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> fileReference.getDownloadUrl().addOnSuccessListener(uri -> {
                    String fotoUrl = uri.toString();
                    saveData(fotoUrl);
                }))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al subir imagen: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnGuardar.setEnabled(true);
                });
    }

    private void saveData(String fotoUrl) {
        Map<String, Object> mascotaData = new HashMap<>();
        mascotaData.put("nombre", Objects.requireNonNull(etNombre.getText()).toString());
        mascotaData.put("raza", Objects.requireNonNull(etRaza.getText()).toString());
        mascotaData.put("sexo", getChipGroupValue(chipGroupSexo));
        mascotaData.put("fecha_nacimiento", new Timestamp(fechaNacimientoCalendar.getTime()));
        mascotaData.put("tamano", getChipGroupValue(chipGroupTamano));
        try {
            mascotaData.put("peso", Double.parseDouble(Objects.requireNonNull(etPeso.getText()).toString()));
        } catch (NumberFormatException e) {
            mascotaData.put("peso", 0.0);
        }
        mascotaData.put("esterilizado", switchEsterilizado.isChecked());
        if (fotoUrl != null) {
            mascotaData.put("foto_principal_url", fotoUrl);
        }

        // Salud Map
        Map<String, Object> saludMap = new HashMap<>();
        saludMap.put("vacunas_al_dia", switchVacunas.isChecked());
        saludMap.put("desparasitacion_aldia", switchDesparasitacion.isChecked());
        saludMap.put("condiciones_medicas", Objects.requireNonNull(etCondicionesMedicas.getText()).toString());
        saludMap.put("medicamentos_actuales", Objects.requireNonNull(etMedicamentos.getText()).toString());
        saludMap.put("ultima_visita_vet", new Timestamp(ultimaVisitaVetCalendar.getTime()));
        saludMap.put("veterinario_nombre", Objects.requireNonNull(etVeterinarioNombre.getText()).toString());
        saludMap.put("veterinario_telefono", Objects.requireNonNull(etVeterinarioTelefono.getText()).toString());
        mascotaData.put("salud", saludMap);

        // Comportamiento Map
        Map<String, Object> compMap = new HashMap<>();
        compMap.put("nivel_energia", getChipGroupValue(chipGroupEnergia));
        compMap.put("con_personas", Objects.requireNonNull(etConPersonas.getText()).toString());
        compMap.put("con_otros_perros", Objects.requireNonNull(etConOtrosPerros.getText()).toString());
        compMap.put("con_otros_animales", Objects.requireNonNull(etConOtrosAnimales.getText()).toString());
        compMap.put("habitos_correa", Objects.requireNonNull(etHabitosCorrea.getText()).toString());
        compMap.put("comandos_conocidos", Objects.requireNonNull(etComandosConocidos.getText()).toString());
        compMap.put("miedos_fobias", Objects.requireNonNull(etMiedosFobias.getText()).toString());
        compMap.put("manias_habitos", Objects.requireNonNull(etManiasHabitos.getText()).toString());
        mascotaData.put("comportamiento", compMap);

        // Instrucciones Map
        Map<String, Object> instMap = new HashMap<>();
        instMap.put("rutina_paseo", Objects.requireNonNull(etRutinaPaseo.getText()).toString());
        instMap.put("tipo_correa_arnes", Objects.requireNonNull(etTipoCorreaArnes.getText()).toString());
        instMap.put("recompensas", Objects.requireNonNull(etRecompensas.getText()).toString());
        instMap.put("instrucciones_emergencia", Objects.requireNonNull(etInstruccionesEmergencia.getText()).toString());
        instMap.put("notas_adicionales", Objects.requireNonNull(etNotasAdicionales.getText()).toString());
        mascotaData.put("instrucciones", instMap);

        db.collection("duenos").document(duenoId).collection("mascotas").document(mascotaId)
                .set(mascotaData, SetOptions.merge()) // Use merge to be safe
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Perfil de mascota actualizado", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnGuardar.setEnabled(true);
                });
    }

    private void openFileChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            Glide.with(this).load(imageUri).circleCrop().into(ivAvatarMascota);
            validateForm(); // A new photo might make the form valid
        }
    }

    // --- Helper Methods ---
    private void setChipGroupValue(ChipGroup chipGroup, String value) {
        if (value == null) return;
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            if (chip.getText().toString().equalsIgnoreCase(value)) {
                chip.setChecked(true);
                break;
            }
        }
    }

    private String getChipGroupValue(ChipGroup chipGroup) {
        int selectedId = chipGroup.getCheckedChipId();
        if (selectedId != View.NO_ID) {
            Chip selectedChip = chipGroup.findViewById(selectedId);
            return selectedChip.getText().toString();
        }
        return null;
    }

    private void updateDateInView(TextInputEditText editText, Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
        editText.setText(sdf.format(date));
    }
}
