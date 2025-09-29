package com.mjc.mascotalink;

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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class DisponibilidadActivity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";
    private AutoCompleteTextView etDia;
    private EditText etHoraInicio, etHoraFin;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disponibilidad);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Firebase emuladores
        String host = "192.168.0.147";
        mAuth = FirebaseAuth.getInstance();
        mAuth.useEmulator(host, 9099);
        db = FirebaseFirestore.getInstance();
        db.useEmulator(host, 8080);

        etDia = findViewById(R.id.et_dia);
        etHoraInicio = findViewById(R.id.et_hora_inicio);
        etHoraFin = findViewById(R.id.et_hora_fin);
        Button btnGuardar = findViewById(R.id.btn_guardar_disponibilidad);

        String[] dias = getResources().getStringArray(R.array.dias_semana);
        etDia.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dias));

        btnGuardar.setOnClickListener(v -> guardarDisponibilidad());
    }

    private void guardarDisponibilidad() {
        String dia = etDia.getText().toString().trim();
        String inicio = etHoraInicio.getText().toString().trim();
        String fin = etHoraFin.getText().toString().trim();

        if (!validateInputs(dia, inicio, fin)) {
            return;
        }

        // Simulando guardado exitoso para el flujo del wizard
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean("disponibilidad_completa", true).apply();

        Toast.makeText(this, "Disponibilidad guardada", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
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

        return valid;
    }
}
