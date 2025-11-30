package com.mjc.mascotalink;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.List;

public class TiposPerrosActivity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";

    private MaterialCheckBox cbPequeno, cbMediano, cbGrande;
    private MaterialCheckBox cbCalmo, cbActivo, cbReactividadBaja, cbReactividadAlta;
    private TextView tvValidationMessages;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tipos_perros);

        // Custom Back Button
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        cbPequeno = findViewById(R.id.cb_pequeno);
        cbMediano = findViewById(R.id.cb_mediano);
        cbGrande = findViewById(R.id.cb_grande);
        cbCalmo = findViewById(R.id.cb_calmo);
        cbActivo = findViewById(R.id.cb_activo);
        cbReactividadBaja = findViewById(R.id.cb_reactividad_baja);
        cbReactividadAlta = findViewById(R.id.cb_reactividad_alta);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);
        Button btnGuardar = findViewById(R.id.btn_guardar_tipos);

        // Verificar que todos los elementos existen
        if (cbPequeno == null || cbMediano == null || cbGrande == null || 
            cbCalmo == null || cbActivo == null || cbReactividadBaja == null || 
            cbReactividadAlta == null || btnGuardar == null) {
            Toast.makeText(this, "Error: Elementos de la interfaz no encontrados", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadState(); // Load previously saved state

        btnGuardar.setOnClickListener(v -> guardarTiposPerros());
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        cbPequeno.setChecked(prefs.getBoolean("perros_pequeno", false));
        cbMediano.setChecked(prefs.getBoolean("perros_mediano", false));
        cbGrande.setChecked(prefs.getBoolean("perros_grande", false));
        cbCalmo.setChecked(prefs.getBoolean("perros_calmo", false));
        cbActivo.setChecked(prefs.getBoolean("perros_activo", false));
        cbReactividadBaja.setChecked(prefs.getBoolean("perros_reactividad_baja", false));
        cbReactividadAlta.setChecked(prefs.getBoolean("perros_reactividad_alta", false));
    }

    private void guardarTiposPerros() {
        List<String> tamanos = new ArrayList<>();
        if (cbPequeno.isChecked()) tamanos.add("pequeno");
        if (cbMediano.isChecked()) tamanos.add("mediano");
        if (cbGrande.isChecked()) tamanos.add("grande");

        List<String> temperamentos = new ArrayList<>();
        if (cbCalmo.isChecked()) temperamentos.add("calmo");
        if (cbActivo.isChecked()) temperamentos.add("activo");
        if (cbReactividadBaja.isChecked()) temperamentos.add("reactividad_baja");
        if (cbReactividadAlta.isChecked()) temperamentos.add("reactividad_alta");

        if (tamanos.isEmpty() || temperamentos.isEmpty()) {
            String error = "";
            if (tamanos.isEmpty()) error += "Debes seleccionar al menos un tamaño.\n";
            if (temperamentos.isEmpty()) error += "Debes seleccionar al menos un temperamento.";
            tvValidationMessages.setText(error.trim());
            tvValidationMessages.setVisibility(View.VISIBLE);
            return;
        }

        tvValidationMessages.setVisibility(View.GONE);

        // Save state to SharedPreferences
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putBoolean("perros_completo", true);
        editor.putBoolean("perros_pequeno", cbPequeno.isChecked());
        editor.putBoolean("perros_mediano", cbMediano.isChecked());
        editor.putBoolean("perros_grande", cbGrande.isChecked());
        editor.putBoolean("perros_calmo", cbCalmo.isChecked());
        editor.putBoolean("perros_activo", cbActivo.isChecked());
        editor.putBoolean("perros_reactividad_baja", cbReactividadBaja.isChecked());
        editor.putBoolean("perros_reactividad_alta", cbReactividadAlta.isChecked());
        editor.apply();

        Toast.makeText(this, "Preferencias guardadas", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}
