package com.mjc.mascotalink;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MascotaRegistroPaso2Activity extends AppCompatActivity {

    private ImageView arrowBack;
    private SwitchMaterial vacunasSwitch, desparasitacionSwitch;
    private TextInputEditText ultimaVisitaVetEditText, condicionesMedicasEditText, medicamentosActualesEditText, veterinarioNombreEditText, veterinarioTelefonoEditText;
    private TextInputLayout ultimaVisitaVetLayout, condicionesMedicasLayout, medicamentosActualesLayout, veterinarioNombreLayout, veterinarioTelefonoLayout;
    private Button siguienteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registro_paso2);

        arrowBack = findViewById(R.id.arrow_back);
        vacunasSwitch = findViewById(R.id.vacunasSwitch);
        desparasitacionSwitch = findViewById(R.id.desparasitacionSwitch);
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
        siguienteButton.setOnClickListener(v -> {
            if (validateFields()) {
                collectDataAndProceed();
            }
        });

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

        ultimaVisitaVetEditText.addTextChangedListener(textWatcher);
        condicionesMedicasEditText.addTextChangedListener(textWatcher);
        medicamentosActualesEditText.addTextChangedListener(textWatcher);
        veterinarioNombreEditText.addTextChangedListener(textWatcher);
        veterinarioTelefonoEditText.addTextChangedListener(textWatcher);

        vacunasSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> validateInputs());
        desparasitacionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> validateInputs());
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
        Intent intent = new Intent(this, MascotaRegistroPaso3Activity.class);
        intent.putExtras(getIntent().getExtras()); // Carry over all previous data

        // Add data from Paso 2
        intent.putExtra("vacunas_al_dia", vacunasSwitch.isChecked());
        intent.putExtra("desparasitacion_al_dia", desparasitacionSwitch.isChecked());

        String ultimaVisitaStr = ultimaVisitaVetEditText.getText().toString();
        if (!ultimaVisitaStr.isEmpty()) {
            try {
                // Using "es-ES" locale to correctly parse month names in Spanish
                SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
                Date date = sdf.parse(ultimaVisitaStr);
                if (date != null) {
                    intent.putExtra("ultima_visita_vet", date.getTime());
                }
            } catch (ParseException e) {
                e.printStackTrace();
                intent.putExtra("ultima_visita_vet", 0L); // Handle parse error
            }
        }

        intent.putExtra("condiciones_medicas", condicionesMedicasEditText.getText().toString().trim());
        intent.putExtra("medicamentos_actuales", medicamentosActualesEditText.getText().toString().trim());
        intent.putExtra("veterinario_nombre", veterinarioNombreEditText.getText().toString().trim());
        intent.putExtra("veterinario_telefono", veterinarioTelefonoEditText.getText().toString().trim());

        startActivity(intent);
    }
}