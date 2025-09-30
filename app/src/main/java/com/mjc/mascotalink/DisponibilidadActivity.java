package com.mjc.mascotalink;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.Calendar;
import java.util.Locale;

public class DisponibilidadActivity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";
    private AutoCompleteTextView etDia;
    private EditText etHoraInicio, etHoraFin;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disponibilidad);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etDia = findViewById(R.id.et_dia);
        etHoraInicio = findViewById(R.id.et_hora_inicio);
        etHoraFin = findViewById(R.id.et_hora_fin);
        Button btnGuardar = findViewById(R.id.btn_guardar_disponibilidad);

        // Setup Dropdown for days
        String[] dias = getResources().getStringArray(R.array.dias_semana);
        etDia.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dias));

        // Setup TimePickers
        setupTimePicker(etHoraInicio);
        setupTimePicker(etHoraFin);

        loadState(); // Load previously saved data

        btnGuardar.setOnClickListener(v -> guardarDisponibilidad());
    }

    private void setupTimePicker(EditText editText) {
        editText.setFocusable(false);
        editText.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);

            TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {
                String formattedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour);
                editText.setText(formattedTime);
            }, hour, minute, true);
            timePickerDialog.show();
        });
    }

    private void guardarDisponibilidad() {
        String dia = etDia.getText().toString().trim();
        String inicio = etHoraInicio.getText().toString().trim();
        String fin = etHoraFin.getText().toString().trim();

        if (!validateInputs(dia, inicio, fin)) {
            return;
        }

        saveState(dia, inicio, fin);

        Toast.makeText(this, "Disponibilidad guardada", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void saveState(String dia, String inicio, String fin) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putBoolean("disponibilidad_completa", true);
        editor.putString("disponibilidad_dia", dia);
        editor.putString("disponibilidad_inicio", inicio);
        editor.putString("disponibilidad_fin", fin);
        editor.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        etDia.setText(prefs.getString("disponibilidad_dia", ""));
        etHoraInicio.setText(prefs.getString("disponibilidad_inicio", ""));
        etHoraFin.setText(prefs.getString("disponibilidad_fin", ""));
        // This is to ensure the dropdown shows the text
        if (etDia.getAdapter() != null) {
            ((ArrayAdapter<String>) etDia.getAdapter()).getFilter().filter(null);
        }
    }

    private boolean validateInputs(String dia, String inicio, String fin) {
        etDia.setError(null);
        etHoraInicio.setError(null);
        etHoraFin.setError(null);

        boolean valid = true;
        if (TextUtils.isEmpty(dia)) {
            etDia.setError("El día es requerido");
            valid = false;
        }
        if (TextUtils.isEmpty(inicio)) {
            etHoraInicio.setError("La hora de inicio es requerida");
            valid = false;
        }
        if (TextUtils.isEmpty(fin)) {
            etHoraFin.setError("La hora de fin es requerida");
            valid = false;
        }

        // Optional: Add validation for inicio < fin

        return valid;
    }
}
