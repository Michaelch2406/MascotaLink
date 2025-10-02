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
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MetodoPagoActivity extends AppCompatActivity {

    private String PREFS = "WizardPaseador"; // default; can be overridden via Intent
    private AutoCompleteTextView etBanco;
    private EditText etCuenta;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metodo_pago);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        // Dynamic prefs scope
        String passedPrefs = getIntent().getStringExtra("prefs");
        if (passedPrefs != null && !passedPrefs.trim().isEmpty()) {
            PREFS = passedPrefs.trim();
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etBanco = findViewById(R.id.et_banco);
        etCuenta = findViewById(R.id.et_numero_cuenta);
        Button btnGuardar = findViewById(R.id.btn_guardar_metodo);

        if (etBanco == null || etCuenta == null || btnGuardar == null) {
            Toast.makeText(this, "Error: Elementos de la interfaz no encontrados", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String[] bancos = getResources().getStringArray(R.array.bancos);
        etBanco.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, bancos));
        // Tipo de cuenta eliminado según requerimiento

        loadState();

        btnGuardar.setOnClickListener(v -> guardarMetodoPago());
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String savedBank = prefs.getString("pago_banco", "");
        etCuenta.setText(prefs.getString("pago_cuenta", ""));
        // Tipo de cuenta eliminado; no se carga

        if (!savedBank.isEmpty()) {
            etBanco.setText(savedBank);
        } else {
            String[] bancos = getResources().getStringArray(R.array.bancos);
            if (bancos.length > 0) etBanco.setText(bancos[0]);
        }
        // Sin tipo de cuenta

        if (etBanco.getAdapter() instanceof ArrayAdapter) {
            @SuppressWarnings("unchecked")
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) etBanco.getAdapter();
            adapter.getFilter().filter(null);
        }
    }

    private void guardarMetodoPago() {
        String banco = etBanco.getText().toString().trim();
        String cuenta = etCuenta.getText().toString().trim();

        if (!validateInputs(banco, cuenta)) return;

        // SharedPreferences
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putBoolean("metodo_pago_completo", true);
        editor.putString("pago_banco", banco);
        editor.putString("pago_cuenta", cuenta);
        // No se guarda tipo
        editor.apply();

        // Firestore subcollection for current user if logged in
        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            Map<String, Object> mp = new HashMap<>();
            mp.put("banco", banco);
            mp.put("numero_cuenta", cuenta);
            // sin campo tipo
            mp.put("predeterminado", true);
            mp.put("fecha_registro", Timestamp.now());

            db.collection("usuarios").document(uid).collection("metodos_pago")
                .add(mp)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Método de pago guardado", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Guardado local. Error en nube: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                });
        } else {
            Toast.makeText(this, "Método de pago guardado", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }
    }

    private boolean validateInputs(String banco, String cuenta) {
        etBanco.setError(null);
        etCuenta.setError(null);

        if (TextUtils.isEmpty(banco)) { etBanco.setError("Debes seleccionar un banco"); return false; }
        if (TextUtils.isEmpty(cuenta)) { etCuenta.setError("El número de cuenta es requerido"); return false; }
        return true;
    }
}
