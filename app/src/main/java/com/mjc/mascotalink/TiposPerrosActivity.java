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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class TiposPerrosActivity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";

    private CheckBox cbPequeno, cbMediano, cbGrande;
    private CheckBox cbCalmo, cbActivo, cbReactividadBaja, cbReactividadAlta;
    private TextView tvValidationMessages;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tipos_perros);

        // Firebase emuladores
        String host = "192.168.0.147";
        mAuth = FirebaseAuth.getInstance();
        mAuth.useEmulator(host, 9099);
        db = FirebaseFirestore.getInstance();
        db.useEmulator(host, 8080);

        cbPequeno = findViewById(R.id.cb_pequeno);
        cbMediano = findViewById(R.id.cb_mediano);
        cbGrande = findViewById(R.id.cb_grande);
        cbCalmo = findViewById(R.id.cb_calmo);
        cbActivo = findViewById(R.id.cb_activo);
        cbReactividadBaja = findViewById(R.id.cb_reactividad_baja);
        cbReactividadAlta = findViewById(R.id.cb_reactividad_alta);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);
        Button btnGuardar = findViewById(R.id.btn_guardar_tipos);

        btnGuardar.setOnClickListener(v -> guardarTiposPerros());
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

        // Simulando guardado exitoso para el flujo del wizard
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean("perros_completo", true).apply();

        Toast.makeText(this, "Preferencias guardadas", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}
