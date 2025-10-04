package com.mjc.mascotalink;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MascotaRegistroPaso2Activity extends AppCompatActivity {

    private static final String PREFS = "MascotaWizard";

    private Switch swVacunas;
    private Switch swDesparasitacion;
    private EditText etUltimaVisita;
    private EditText etCondiciones;
    private EditText etMedicamentos;
    private EditText etVetNombre;
    private EditText etVetTelefono;
    private Button btnGuardar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registro_paso2);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        bindViews();
        loadState();
        setupDatePicker();
        setupListeners();
    }

    private void bindViews() {
        swVacunas = findViewById(R.id.sw_vacunas);
        swDesparasitacion = findViewById(R.id.sw_desparasitacion);
        etUltimaVisita = findViewById(R.id.et_ultima_visita);
        etCondiciones = findViewById(R.id.et_condiciones_medicas);
        etMedicamentos = findViewById(R.id.et_medicamentos);
        etVetNombre = findViewById(R.id.et_veterinario_nombre);
        etVetTelefono = findViewById(R.id.et_veterinario_telefono);
        btnGuardar = findViewById(R.id.btn_guardar);
    }

    private void setupDatePicker() {
        etUltimaVisita.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            DatePickerDialog dlg = new DatePickerDialog(this, (view, y, m, d) -> {
                Calendar sel = Calendar.getInstance();
                sel.set(y, m, d, 0,0,0);
                SimpleDateFormat df = new SimpleDateFormat("dd 'de' MMMM 'de' yyyy", new Locale("es"));
                etUltimaVisita.setText(df.format(sel.getTime()));
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putLong("salud_ultima_visita_vet", sel.getTimeInMillis()).apply();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            dlg.show();
        });
    }

    private void setupListeners() {
        CompoundButton.OnCheckedChangeListener chk = (buttonView, isChecked) -> saveState();
        swVacunas.setOnCheckedChangeListener(chk);
        swDesparasitacion.setOnCheckedChangeListener(chk);
        btnGuardar.setOnClickListener(v -> {
            if (!validVetPhone()) return;
            saveState();
            finish();
        });
    }

    private boolean validVetPhone() {
        String t = etVetTelefono.getText().toString().trim();
        if (TextUtils.isEmpty(t)) return true; // opcional
        if (!t.matches("^\n?\n?\n?\n?.{0,15}$")) return true; // permisivo, ya que formatos varían
        return true;
    }

    private void saveState() {
        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        ed.putBoolean("salud_vacunas_al_dia", swVacunas.isChecked());
        ed.putBoolean("salud_desparasitacion_aldia", swDesparasitacion.isChecked());
        ed.putString("salud_condiciones_medicas", etCondiciones.getText().toString().trim());
        ed.putString("salud_medicamentos_actuales", etMedicamentos.getText().toString().trim());
        ed.putString("salud_veterinario_nombre", etVetNombre.getText().toString().trim());
        ed.putString("salud_veterinario_telefono", etVetTelefono.getText().toString().trim());
        // fecha se guarda al seleccionarla
        ed.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        swVacunas.setChecked(prefs.getBoolean("salud_vacunas_al_dia", false));
        swDesparasitacion.setChecked(prefs.getBoolean("salud_desparasitacion_aldia", false));
        long f = prefs.getLong("salud_ultima_visita_vet", 0);
        if (f > 0) {
            SimpleDateFormat df = new SimpleDateFormat("dd 'de' MMMM 'de' yyyy", new Locale("es"));
            etUltimaVisita.setText(df.format(f));
        }
        etCondiciones.setText(prefs.getString("salud_condiciones_medicas", ""));
        etMedicamentos.setText(prefs.getString("salud_medicamentos_actuales", ""));
        etVetNombre.setText(prefs.getString("salud_veterinario_nombre", ""));
        etVetTelefono.setText(prefs.getString("salud_veterinario_telefono", ""));
    }
}
