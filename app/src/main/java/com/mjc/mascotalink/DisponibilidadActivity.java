package com.mjc.mascotalink;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DisponibilidadActivity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";

    private final String[] diasSemana = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private final List<CheckBox> checkBoxes = new ArrayList<>();

    private EditText etHoraInicio, etHoraFin;
    private TextView tvValidationMessages;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disponibilidad);

        setupToolbar();
        setupViews();
        setupTimePickers();
        loadState();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupViews() {
        etHoraInicio = findViewById(R.id.et_hora_inicio);
        etHoraFin = findViewById(R.id.et_hora_fin);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);

        Button btnSeleccionarTodos = findViewById(R.id.btn_seleccionar_todos);
        Button btnDeseleccionarTodos = findViewById(R.id.btn_deseleccionar_todos);
        Button btnGuardar = findViewById(R.id.btn_guardar_disponibilidad);

        GridLayout gridLayout = findViewById(R.id.grid_dias);
        createDayCheckBoxes(gridLayout);

        btnSeleccionarTodos.setOnClickListener(v -> setAllCheckBoxes(true));
        btnDeseleccionarTodos.setOnClickListener(v -> setAllCheckBoxes(false));
        btnGuardar.setOnClickListener(v -> guardarDisponibilidad());
    }

    private void createDayCheckBoxes(GridLayout gridLayout) {
        gridLayout.removeAllViews();
        checkBoxes.clear();
        for (String dia : diasSemana) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(dia);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            checkBox.setLayoutParams(params);
            gridLayout.addView(checkBox);
            checkBoxes.add(checkBox);
        }
    }

    private void setupTimePickers() {
        etHoraInicio.setOnClickListener(v -> showTimePicker(etHoraInicio));
        etHoraFin.setOnClickListener(v -> showTimePicker(etHoraFin));
    }

    private void showTimePicker(EditText editText) {
        String currentTime = editText.getText().toString();
        int hour = 8, minute = 0;
        if (!TextUtils.isEmpty(currentTime) && currentTime.contains(":")) {
            try {
                String[] parts = currentTime.split(":");
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            } catch (Exception e) { /* Ignored */ }
        }

        new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {
            String formattedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour);
            editText.setText(formattedTime);
        }, hour, minute, true).show();
    }

    private void setAllCheckBoxes(boolean isChecked) {
        for (CheckBox checkBox : checkBoxes) {
            checkBox.setChecked(isChecked);
        }
    }

    private void guardarDisponibilidad() {
        List<String> diasSeleccionados = new ArrayList<>();
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isChecked()) {
                diasSeleccionados.add(diasSemana[i]);
            }
        }

        String inicio = etHoraInicio.getText().toString().trim();
        String fin = etHoraFin.getText().toString().trim();

        if (!validateInputs(diasSeleccionados, inicio, fin)) {
            return;
        }

        saveState(diasSeleccionados, inicio, fin);
        Toast.makeText(this, "Disponibilidad guardada", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void saveState(List<String> diasSeleccionados, String inicio, String fin) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putBoolean("disponibilidad_completa", true);
        editor.putStringSet("disponibilidad_dias", new HashSet<>(diasSeleccionados));
        editor.putString("disponibilidad_inicio", inicio);
        editor.putString("disponibilidad_fin", fin);
        editor.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        etHoraInicio.setText(prefs.getString("disponibilidad_inicio", "09:00"));
        etHoraFin.setText(prefs.getString("disponibilidad_fin", "17:00"));

        Set<String> diasGuardados = prefs.getStringSet("disponibilidad_dias", new HashSet<>());
        for (int i = 0; i < diasSemana.length; i++) {
            if (diasGuardados.contains(diasSemana[i])) {
                checkBoxes.get(i).setChecked(true);
            }
        }
    }

    private boolean validateInputs(List<String> dias, String inicio, String fin) {
        tvValidationMessages.setVisibility(View.GONE);
        List<String> errores = new ArrayList<>();

        if (dias.isEmpty()) {
            errores.add("• Debes seleccionar al menos un día.");
        }
        if (TextUtils.isEmpty(inicio)) {
            errores.add("• La hora de inicio es requerida.");
        }
        if (TextUtils.isEmpty(fin)) {
            errores.add("• La hora de fin es requerida.");
        }

        if (!TextUtils.isEmpty(inicio) && !TextUtils.isEmpty(fin)) {
            try {
                String[] inicioPartes = inicio.split(":");
                String[] finPartes = fin.split(":");
                int inicioMinutos = Integer.parseInt(inicioPartes[0]) * 60 + Integer.parseInt(inicioPartes[1]);
                int finMinutos = Integer.parseInt(finPartes[0]) * 60 + Integer.parseInt(finPartes[1]);

                if (finMinutos <= inicioMinutos) {
                    errores.add("• La hora de fin debe ser posterior a la de inicio.");
                }
            } catch (Exception e) {
                errores.add("• Formato de hora inválido.");
            }
        }

        if (!errores.isEmpty()) {
            tvValidationMessages.setText(String.join("\n", errores));
            tvValidationMessages.setVisibility(View.VISIBLE);
            return false;
        }
        return true;
    }
}