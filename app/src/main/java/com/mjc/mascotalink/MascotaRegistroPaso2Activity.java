package com.mjc.mascotalink;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.mjc.mascotalink.utils.InputUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MascotaRegistroPaso2Activity extends AppCompatActivity {

    private static final String TAG = "MascotaRegistroPaso2";
    private static final long DEBOUNCE_DELAY_MS = 500;
    private static final long RATE_LIMIT_MS = 1000;

    private ImageView arrowBack;
    private SwitchMaterial vacunasSwitch, desparasitacionSwitch, esterilizadoSwitch;
    private TextInputEditText ultimaVisitaVetEditText, condicionesMedicasEditText, medicamentosActualesEditText, veterinarioNombreEditText, veterinarioTelefonoEditText;
    private TextInputLayout ultimaVisitaVetLayout, condicionesMedicasLayout, medicamentosActualesLayout, veterinarioNombreLayout, veterinarioTelefonoLayout;
    private Button siguienteButton;

    private TextWatcher validationTextWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registro_paso2);

        arrowBack = findViewById(R.id.arrow_back);
        vacunasSwitch = findViewById(R.id.vacunasSwitch);
        desparasitacionSwitch = findViewById(R.id.desparasitacionSwitch);
        esterilizadoSwitch = findViewById(R.id.esterilizadoSwitch);
        ultimaVisitaVetEditText = findViewById(R.id.ultimaVisitaVetEditText);
        condicionesMedicasEditText = findViewById(R.id.condicionesMedicasEditText);
        medicamentosActualesEditText = findViewById(R.id.medicamentosActualesEditText);
        veterinarioNombreEditText = findViewById(R.id.veterinarioNombreEditText);
        veterinarioTelefonoEditText = findViewById(R.id.veterinarioTelefonoEditText);
        siguienteButton = findViewById(R.id.siguienteButton);

        // Assuming layouts are siblings to the EditTexts, will need to adjust if not
        // This part of the code might need resource ID adjustments in the XML
        // For now, proceeding without direct layout references for setError

        setupListeners();
        validateInputs();
    }

    private void setupListeners() {
        arrowBack.setOnClickListener(v -> finish());
        ultimaVisitaVetEditText.setOnClickListener(v -> showDatePickerDialog());
        siguienteButton.setOnClickListener(InputUtils.createSafeClickListener(v -> {
            if (validateFields()) {
                collectDataAndProceed();
            }
        }));

        validationTextWatcher = InputUtils.createDebouncedTextWatcher(
            "validacion_mascota_paso2",
            DEBOUNCE_DELAY_MS,
            text -> validateInputs()
        );

        ultimaVisitaVetEditText.addTextChangedListener(validationTextWatcher);
        condicionesMedicasEditText.addTextChangedListener(validationTextWatcher);
        medicamentosActualesEditText.addTextChangedListener(validationTextWatcher);
        veterinarioNombreEditText.addTextChangedListener(validationTextWatcher);
        veterinarioTelefonoEditText.addTextChangedListener(validationTextWatcher);

        vacunasSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> validateInputs());
        desparasitacionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> validateInputs());
        esterilizadoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> validateInputs());
    }

    private void validateInputs() {
        boolean allFilled = !ultimaVisitaVetEditText.getText().toString().trim().isEmpty()
                && !condicionesMedicasEditText.getText().toString().trim().isEmpty()
                && !medicamentosActualesEditText.getText().toString().trim().isEmpty()
                && !veterinarioNombreEditText.getText().toString().trim().isEmpty()
                && !veterinarioTelefonoEditText.getText().toString().trim().isEmpty();
        siguienteButton.setEnabled(allFilled);
    }

    private boolean validateFields() {
        boolean isValid = true;
        if (ultimaVisitaVetEditText.getText().toString().trim().isEmpty()) {
            // Cannot set error on a non-TextInputLayout view directly without a wrapper
            ultimaVisitaVetEditText.setError("Selecciona una fecha");
            isValid = false;
        }
        if (condicionesMedicasEditText.getText().toString().trim().isEmpty()) {
            condicionesMedicasEditText.setError("Describe las condiciones médicas");
            isValid = false;
        }
        if (medicamentosActualesEditText.getText().toString().trim().isEmpty()) {
            medicamentosActualesEditText.setError("Describe los medicamentos actuales");
            isValid = false;
        }
        if (veterinarioNombreEditText.getText().toString().trim().isEmpty()) {
            veterinarioNombreEditText.setError("Ingresa el nombre del veterinario");
            isValid = false;
        }
        if (veterinarioTelefonoEditText.getText().toString().trim().isEmpty()) {
            veterinarioTelefonoEditText.setError("Ingresa el teléfono del veterinario");
            isValid = false;
        } else if (veterinarioTelefonoEditText.getText().length() < 10) {
            veterinarioTelefonoEditText.setError("El teléfono debe tener 10 dígitos");
            isValid = false;
        }
        return isValid;
    }

    private void showDatePickerDialog() {
        final Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    String selectedDate = String.format(Locale.getDefault(), "%d de %s de %d", dayOfMonth, getMonthName(month), year);
                    ultimaVisitaVetEditText.setText(selectedDate);
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private String getMonthName(int month) {
        String[] monthNames = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
        return monthNames[month];
    }

    private void collectDataAndProceed() {
        try {
            Intent intent = new Intent(this, MascotaRegistroPaso3Activity.class);
            intent.putExtras(getIntent().getExtras());

            // Sanitizar inputs
            String condicionesSanitizadas = InputUtils.sanitizeInput(condicionesMedicasEditText.getText().toString().trim());
            String medicamentosSanitizados = InputUtils.sanitizeInput(medicamentosActualesEditText.getText().toString().trim());
            String veterinarioNombreSanitizado = InputUtils.sanitizeInput(veterinarioNombreEditText.getText().toString().trim());
            String veterinarioTelefonoSanitizado = InputUtils.sanitizeInput(veterinarioTelefonoEditText.getText().toString().trim());

            intent.putExtra("vacunas_al_dia", vacunasSwitch.isChecked());
            intent.putExtra("desparasitacion_al_dia", desparasitacionSwitch.isChecked());
            intent.putExtra("esterilizado", esterilizadoSwitch.isChecked());

            String ultimaVisitaStr = ultimaVisitaVetEditText.getText().toString();
            if (!ultimaVisitaStr.isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
                    Date date = sdf.parse(ultimaVisitaStr);
                    if (date != null) {
                        intent.putExtra("ultima_visita_vet", date.getTime());
                    }
                } catch (ParseException e) {
                    Log.e(TAG, "Error al parsear fecha de última visita veterinaria", e);
                    intent.putExtra("ultima_visita_vet", 0L);
                }
            }

            intent.putExtra("condiciones_medicas", condicionesSanitizadas);
            intent.putExtra("medicamentos_actuales", medicamentosSanitizados);
            intent.putExtra("veterinario_nombre", veterinarioNombreSanitizado);
            intent.putExtra("veterinario_telefono", veterinarioTelefonoSanitizado);

            Log.d(TAG, "Datos del paso 2 recolectados correctamente");
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error al recolectar datos", e);
            Toast.makeText(this, "Error al procesar los datos. Intenta nuevamente.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Limpiar TextWatchers para prevenir memory leaks
        if (validationTextWatcher != null) {
            if (ultimaVisitaVetEditText != null) ultimaVisitaVetEditText.removeTextChangedListener(validationTextWatcher);
            if (condicionesMedicasEditText != null) condicionesMedicasEditText.removeTextChangedListener(validationTextWatcher);
            if (medicamentosActualesEditText != null) medicamentosActualesEditText.removeTextChangedListener(validationTextWatcher);
            if (veterinarioNombreEditText != null) veterinarioNombreEditText.removeTextChangedListener(validationTextWatcher);
            if (veterinarioTelefonoEditText != null) veterinarioTelefonoEditText.removeTextChangedListener(validationTextWatcher);
        }

        // Cancelar debounces pendientes
        InputUtils.cancelDebounce("validacion_mascota_paso2");

        Log.d(TAG, "Activity destruida y recursos limpiados");
    }
}