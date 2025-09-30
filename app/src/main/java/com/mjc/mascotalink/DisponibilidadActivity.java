package com.mjc.mascotalink;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DisponibilidadActivity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";
    
    // Días de la semana
    private CheckBox[] diasCheckBoxes = new CheckBox[7];
    private String[] diasSemana = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    
    // Horarios
    private EditText etHoraInicio, etHoraFin;
    private Button btnHorariosRapidos;
    
    // Botones de horarios rápidos
    private Button btnMañana, btnTarde, btnCompleto;
    
    private TextView tvValidationMessages;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disponibilidad);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        setupViews();
        setupTimePickers();
        setupQuickTimeButtons();
        setupDayCheckboxes();
        loadState();
    }

    private void setupViews() {
        // For now, use simple implementation with existing layout
        // TODO: Update layout to include day checkboxes and quick buttons
        
        // Horarios
        etHoraInicio = findViewById(R.id.et_hora_inicio);
        etHoraFin = findViewById(R.id.et_hora_fin);
        Button btnGuardar = findViewById(R.id.btn_guardar_disponibilidad);
        
        // Verificar que los elementos esenciales existen
        if (etHoraInicio == null || etHoraFin == null || btnGuardar == null) {
            Toast.makeText(this, "Error: Elementos de la interfaz no encontrados", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // AutoCompleteTextView for days (temporary solution)
        AutoCompleteTextView etDia = findViewById(R.id.et_dia);
        if (etDia != null) {
            etDia.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, diasSemana));
        }
        
        btnGuardar.setOnClickListener(v -> guardarDisponibilidadSimple());
    }

    private void setupTimePickers() {
        // Valores por defecto sugeridos
        etHoraInicio.setText("08:00");
        etHoraFin.setText("18:00");
        
        setupTimePicker(etHoraInicio);
        setupTimePicker(etHoraFin);
    }

    private void setupTimePicker(EditText editText) {
        editText.setFocusable(false);
        editText.setClickable(true);
        editText.setOnClickListener(v -> {
            // Parse current time from EditText or use default
            String currentTime = editText.getText().toString();
            int hour = 8, minute = 0;
            if (!TextUtils.isEmpty(currentTime) && currentTime.contains(":")) {
                try {
                    String[] parts = currentTime.split(":");
                    hour = Integer.parseInt(parts[0]);
                    minute = Integer.parseInt(parts[1]);
                } catch (Exception e) {
                    // Use defaults
                }
            }

            TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {
                String formattedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour);
                editText.setText(formattedTime);
            }, hour, minute, true);
            timePickerDialog.show();
        });
    }

    private void setupQuickTimeButtons() {
        // No quick time buttons in current layout - skip this step
        // TODO: Add quick time buttons to layout if needed
        
        // For now, just set default values
        if (TextUtils.isEmpty(etHoraInicio.getText())) {
            etHoraInicio.setText("08:00");
        }
        if (TextUtils.isEmpty(etHoraFin.getText())) {
            etHoraFin.setText("18:00");
        }
    }

    private void setupDayCheckboxes() {
        // Simple implementation - no checkboxes in current layout
        AutoCompleteTextView etDia = findViewById(R.id.et_dia);
        if (etDia != null && TextUtils.isEmpty(etDia.getText())) {
            etDia.setText("Lunes a Viernes");
        }
    }

    private void seleccionarDiasLaborales() {
        // Simple implementation
        AutoCompleteTextView etDia = findViewById(R.id.et_dia);
        if (etDia != null) {
            etDia.setText("Lunes a Viernes");
        }
    }

    private void seleccionarFinesDeSemana() {
        // Simple implementation
        AutoCompleteTextView etDia = findViewById(R.id.et_dia);
        if (etDia != null) {
            etDia.setText("Sábado y Domingo");
        }
    }

    private void seleccionarTodosLosDias() {
        // Simple implementation
        AutoCompleteTextView etDia = findViewById(R.id.et_dia);
        if (etDia != null) {
            etDia.setText("Todos los días");
        }
    }

    private void guardarDisponibilidadSimple() {
        String inicio = etHoraInicio.getText().toString().trim();
        String fin = etHoraFin.getText().toString().trim();
        
        AutoCompleteTextView etDia = findViewById(R.id.et_dia);
        String diaSeleccionado = etDia.getText().toString().trim();
        
        if (TextUtils.isEmpty(diaSeleccionado)) {
            etDia.setError("Selecciona un día");
            return;
        }
        
        if (TextUtils.isEmpty(inicio)) {
            etHoraInicio.setError("Hora de inicio requerida");
            return;
        }
        
        if (TextUtils.isEmpty(fin)) {
            etHoraFin.setError("Hora de fin requerida");
            return;
        }
        
        // Simple save - assume Monday to Friday by default
        List<String> diasDefault = Arrays.asList("Lunes", "Martes", "Miércoles", "Jueves", "Viernes");
        saveState(diasDefault, inicio, fin);

        Toast.makeText(this, "Disponibilidad guardada correctamente", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void saveState(List<String> diasSeleccionados, String inicio, String fin) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putBoolean("disponibilidad_completa", true);
        
        // Guardar días como un conjunto de strings
        Set<String> diasSet = new HashSet<>(diasSeleccionados);
        editor.putStringSet("disponibilidad_dias", diasSet);
        
        // Guardar horarios
        editor.putString("disponibilidad_inicio", inicio);
        editor.putString("disponibilidad_fin", fin);
        
        // Guardar como texto legible para mostrar en la UI principal
        String diasTexto = String.join(", ", diasSeleccionados);
        editor.putString("disponibilidad_resumen", diasTexto + " de " + inicio + " a " + fin);
        
        editor.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        
        // Cargar horarios
        etHoraInicio.setText(prefs.getString("disponibilidad_inicio", "08:00"));
        etHoraFin.setText(prefs.getString("disponibilidad_fin", "18:00"));
        
        // Cargar resumen de días (simple implementation)
        String resumen = prefs.getString("disponibilidad_resumen", "");
        AutoCompleteTextView etDia = findViewById(R.id.et_dia);
        if (etDia != null && !TextUtils.isEmpty(resumen)) {
            // Extract days from summary
            if (resumen.contains("de")) {
                String diasPart = resumen.split(" de ")[0];
                etDia.setText(diasPart);
            }
        } else {
            setupDayCheckboxes();
        }
    }

    private boolean validateInputs(List<String> dias, String inicio, String fin) {
        tvValidationMessages.setVisibility(View.GONE);
        List<String> errores = new ArrayList<>();

        if (dias.isEmpty()) {
            errores.add("• Debes seleccionar al menos un día");
        }
        
        if (TextUtils.isEmpty(inicio)) {
            errores.add("• La hora de inicio es requerida");
        }
        
        if (TextUtils.isEmpty(fin)) {
            errores.add("• La hora de fin es requerida");
        }

        // Validar que la hora de fin sea posterior a la de inicio
        if (!TextUtils.isEmpty(inicio) && !TextUtils.isEmpty(fin)) {
            try {
                String[] inicioPartes = inicio.split(":");
                String[] finPartes = fin.split(":");
                int inicioMinutos = Integer.parseInt(inicioPartes[0]) * 60 + Integer.parseInt(inicioPartes[1]);
                int finMinutos = Integer.parseInt(finPartes[0]) * 60 + Integer.parseInt(finPartes[1]);
                
                if (finMinutos <= inicioMinutos) {
                    errores.add("• La hora de fin debe ser posterior a la hora de inicio");
                }
                
                // Validar que el horario tenga al menos 2 horas
                if (finMinutos - inicioMinutos < 120) {
                    errores.add("• El horario debe tener al menos 2 horas de duración");
                }
                
            } catch (Exception e) {
                errores.add("• Formato de hora inválido");
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
