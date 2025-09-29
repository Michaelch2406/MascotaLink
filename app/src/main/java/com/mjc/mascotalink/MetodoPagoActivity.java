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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MetodoPagoActivity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";
    private AutoCompleteTextView etBanco;
    private EditText etCuenta;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metodo_pago);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Firebase emuladores
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etBanco = findViewById(R.id.et_banco);
        etCuenta = findViewById(R.id.et_numero_cuenta);
        Button btnGuardar = findViewById(R.id.btn_guardar_metodo);

        String[] bancos = getResources().getStringArray(R.array.bancos);
        etBanco.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, bancos));

        btnGuardar.setOnClickListener(v -> guardarMetodoPago());
    }

    private void guardarMetodoPago() {
        String banco = etBanco.getText().toString().trim();
        String cuenta = etCuenta.getText().toString().trim();

        if (!validateInputs(banco, cuenta)) {
            return;
        }

        // Simulando guardado exitoso para el flujo del wizard
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean("metodo_pago_completo", true).apply();

        Toast.makeText(this, "Método de pago guardado", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private boolean validateInputs(String banco, String cuenta) {
        etBanco.setError(null);
        etCuenta.setError(null);

        if (TextUtils.isEmpty(banco)) {
            etBanco.setError("Debes seleccionar un banco");
            return false;
        }

        if (TextUtils.isEmpty(cuenta)) {
            etCuenta.setError("El número de cuenta es requerido");
            return false;
        }

        return true;
    }
}
