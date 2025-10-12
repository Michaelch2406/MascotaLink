package com.mjc.mascotalink;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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

    private static final String TAG = "EditarPerfilMascota";
    private static final int PICK_IMAGE_REQUEST = 1;
    private Uri imageUri;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    // IDs and Data
    private String duenoId;
    private String mascotaId;
    private String duenoNombre;
    private String duenoApellido;
    private String oldPhotoUrl;

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
        loadInitialData();
    }

    private void initViews() {
        ivBack = findViewById(R.id.iv_back);
        ivAvatarMascota = findViewById(R.id.iv_avatar_mascota);
        etNombre = findViewById(R.id.et_nombre);
        etRaza = findViewById(R.id.et_raza);
        chipGroupSexo = findViewById(R.id.chipgroup_sexo);
        etFechaNacimiento = findViewById(R.id.et_fecha_nacimiento);
        chipGroupTamano = findViewById(R.id.chipgroup_tamano);
        etPeso = findViewById(R.id.et_peso);
        switchEsterilizado = findViewById(R.id.switch_esterilizado);
        switchVacunas = findViewById(R.id.switch_vacunas);
        switchDesparasitacion = findViewById(R.id.switch_desparasitacion);
        etCondicionesMedicas = findViewById(R.id.et_condiciones_medicas);
        etMedicamentos = findViewById(R.id.et_medicamentos);
        etUltimaVisitaVet = findViewById(R.id.et_ultima_visita_vet);
        etVeterinarioNombre = findViewById(R.id.et_veterinario_nombre);
        etVeterinarioTelefono = findViewById(R.id.et_veterinario_telefono);
        chipGroupEnergia = findViewById(R.id.chipgroup_energia);
        etConPersonas = findViewById(R.id.et_con_personas);
        etConOtrosPerros = findViewById(R.id.et_con_otros_perros);
        etConOtrosAnimales = findViewById(R.id.et_con_otros_animales);
        etHabitosCorrea = findViewById(R.id.et_habitos_correa);
        etComandosConocidos = findViewById(R.id.et_comandos_conocidos);
        etMiedosFobias = findViewById(R.id.et_miedos_fobias);
        etManiasHabitos = findViewById(R.id.et_manias_habitos);
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
        etFechaNacimiento.setOnClickListener(v -> showDatePickerDialog(fechaNacimientoCalendar, etFechaNacimiento));
        etUltimaVisitaVet.setOnClickListener(v -> showDatePickerDialog(ultimaVisitaVetCalendar, etUltimaVisitaVet));

        TextWatcher textWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { validateForm(); }
        };
        etNombre.addTextChangedListener(textWatcher);
        etRaza.addTextChangedListener(textWatcher);
    }

    private void loadInitialData() {
        // First, get owner data
        db.collection("usuarios").document(duenoId).get().addOnSuccessListener(duenoDoc -> {
            if (duenoDoc.exists()) {
                duenoNombre = duenoDoc.getString("nombre");
                duenoApellido = duenoDoc.getString("apellido");
                // After getting owner data, load pet data
                loadMascotaData();
            } else {
                Toast.makeText(this, "Error: No se encontraron datos del dueño.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error al cargar perfil del dueño.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadMascotaData() {
        db.collection("duenos").document(duenoId).collection("mascotas").document(mascotaId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
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

                        oldPhotoUrl = document.getString("foto_principal_url");
                        if (oldPhotoUrl != null && !oldPhotoUrl.isEmpty()) {
                            Glide.with(this).load(oldPhotoUrl).circleCrop().into(ivAvatarMascota);
                        }

                        // Load nested maps
                        loadMapData(document, "salud");
                        loadMapData(document, "comportamiento");
                        loadMapData(document, "instrucciones");

                        validateForm();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error al cargar datos de la mascota: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void loadMapData(DocumentSnapshot document, String mapKey) {
        if (!document.contains(mapKey)) return;
        Map<String, Object> map = (Map<String, Object>) document.get(mapKey);
        if (map == null) return;

        switch (mapKey) {
            case "salud":
                switchVacunas.setChecked(map.containsKey("vacunas_al_dia") && Boolean.TRUE.equals(map.get("vacunas_al_dia")));
                switchDesparasitacion.setChecked(map.containsKey("desparasitacion_aldia") && Boolean.TRUE.equals(map.get("desparasitacion_aldia")));
                etCondicionesMedicas.setText((String) map.get("condiciones_medicas"));
                etMedicamentos.setText((String) map.get("medicamentos_actuales"));
                etVeterinarioNombre.setText((String) map.get("veterinario_nombre"));
                etVeterinarioTelefono.setText((String) map.get("veterinario_telefono"));
                if (map.containsKey("ultima_visita_vet")) {
                    Timestamp ts = (Timestamp) map.get("ultima_visita_vet");
                    if (ts != null) {
                        ultimaVisitaVetCalendar.setTime(ts.toDate());
                        updateDateInView(etUltimaVisitaVet, ultimaVisitaVetCalendar.getTime());
                    }
                }
                break;
            case "comportamiento":
                setChipGroupValue(chipGroupEnergia, (String) map.get("nivel_energia"));
                etConPersonas.setText((String) map.get("con_personas"));
                etConOtrosPerros.setText((String) map.get("con_otros_perros"));
                etConOtrosAnimales.setText((String) map.get("con_otros_animales"));
                etHabitosCorrea.setText((String) map.get("habitos_correa"));
                etComandosConocidos.setText((String) map.get("comandos_conocidos"));
                etMiedosFobias.setText((String) map.get("miedos_fobias"));
                etManiasHabitos.setText((String) map.get("manias_habitos"));
                break;
            case "instrucciones":
                etRutinaPaseo.setText((String) map.get("rutina_paseo"));
                etTipoCorreaArnes.setText((String) map.get("tipo_correa_arnes"));
                etRecompensas.setText((String) map.get("recompensas"));
                etInstruccionesEmergencia.setText((String) map.get("instrucciones_emergencia"));
                etNotasAdicionales.setText((String) map.get("notas_adicionales"));
                break;
        }
    }

    private void saveMascotaData() {
        if (!btnGuardar.isEnabled()) return;
        btnGuardar.setEnabled(false);
        Toast.makeText(this, "Guardando...", Toast.LENGTH_SHORT).show();

        // If a new image was selected, start the delete/upload process.
        // Otherwise, just save the text data.
        if (imageUri != null) {
            deleteOldPhoto().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    uploadNewPhoto();
                } else {
                    Log.e(TAG, "Failed to delete old photo", task.getException());
                    // Still try to upload the new one
                    uploadNewPhoto();
                }
            });
        } else {
            saveFirestoreData(null); // No new photo URL
        }
    }

    private Task<Void> deleteOldPhoto() {
        if (TextUtils.isEmpty(oldPhotoUrl)) {
            // No old photo to delete, return a successful task immediately
            return Tasks.forResult(null);
        }
        // Get reference from URL and delete
        StorageReference oldPhotoRef = storage.getReferenceFromUrl(oldPhotoUrl);
        return oldPhotoRef.delete();
    }

    private void uploadNewPhoto() {
        String mascotaNombre = Objects.requireNonNull(etNombre.getText()).toString().trim();
        String fileName = String.format(Locale.ROOT, "%s_%s_%s_%s_mascota.jpg",
                duenoId, duenoNombre, duenoApellido, mascotaNombre);

        StorageReference fileReference = storage.getReference("foto_perfil_mascota/" + fileName);

        fileReference.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> fileReference.getDownloadUrl().addOnSuccessListener(uri -> {
                    String newPhotoUrl = uri.toString();
                    saveFirestoreData(newPhotoUrl);
                }))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al subir nueva foto: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnGuardar.setEnabled(true);
                });
    }

    private void saveFirestoreData(String newPhotoUrl) {
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
        
        // Only update photo URL if a new one was provided
        if (newPhotoUrl != null) {
            mascotaData.put("foto_principal_url", newPhotoUrl);
        }

        // Maps
        Map<String, Object> saludMap = new HashMap<>();
        saludMap.put("vacunas_al_dia", switchVacunas.isChecked());
        saludMap.put("desparasitacion_aldia", switchDesparasitacion.isChecked());
        saludMap.put("condiciones_medicas", Objects.requireNonNull(etCondicionesMedicas.getText()).toString());
        saludMap.put("medicamentos_actuales", Objects.requireNonNull(etMedicamentos.getText()).toString());
        saludMap.put("ultima_visita_vet", new Timestamp(ultimaVisitaVetCalendar.getTime()));
        saludMap.put("veterinario_nombre", Objects.requireNonNull(etVeterinarioNombre.getText()).toString());
        saludMap.put("veterinario_telefono", Objects.requireNonNull(etVeterinarioTelefono.getText()).toString());
        mascotaData.put("salud", saludMap);

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

    // --- Unchanged Helper Methods ---
    private void showDatePickerDialog(Calendar calendar, TextInputEditText editText) {
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(year, month, dayOfMonth);
            updateDateInView(editText, calendar.getTime());
            validateForm();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void validateForm() {
        boolean isNombreValid = !Objects.requireNonNull(etNombre.getText()).toString().trim().isEmpty();
        boolean isRazaValid = !Objects.requireNonNull(etRaza.getText()).toString().trim().isEmpty();
        btnGuardar.setEnabled(isNombreValid && isRazaValid);
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
            validateForm();
        }
    }

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
        if (selectedId != -1) {
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