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

public class MetodoPagoActivity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";
    private AutoCompleteTextView etBanco;
    private EditText etCuenta;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metodo_pago);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etBanco = findViewById(R.id.et_banco);
        etCuenta = findViewById(R.id.et_numero_cuenta);
        Button btnGuardar = findViewById(R.id.btn_guardar_metodo);

        // Verificar que todos los elementos existen
        if (etBanco == null || etCuenta == null || btnGuardar == null) {
            Toast.makeText(this, "Error: Elementos de la interfaz no encontrados", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String[] bancos = getResources().getStringArray(R.array.bancos);
        etBanco.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, bancos));

        loadState(); // Load previously saved data

        btnGuardar.setOnClickListener(v -> guardarMetodoPago());
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String savedBank = prefs.getString("pago_banco", "");
        etCuenta.setText(prefs.getString("pago_cuenta", ""));

        if (!savedBank.isEmpty()) {
            etBanco.setText(savedBank);
        } else {
            // Si no hay nada guardado, seleccionar el primer banco de la lista por defecto
            String[] bancos = getResources().getStringArray(R.array.bancos);
            if (bancos.length > 0) {
                etBanco.setText(bancos[0]);
            }
        }

        // This is to ensure the dropdown shows the text
        if (etBanco.getAdapter() instanceof ArrayAdapter) {
            @SuppressWarnings("unchecked")
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) etBanco.getAdapter();
            adapter.getFilter().filter(null);
        }
    }

    private void guardarMetodoPago() {
        String banco = etBanco.getText().toString().trim();
        String cuenta = etCuenta.getText().toString().trim();

        if (!validateInputs(banco, cuenta)) {
            return;
        }

        // Save state to SharedPreferences
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putBoolean("metodo_pago_completo", true);
        editor.putString("pago_banco", banco);
        editor.putString("pago_cuenta", cuenta);
        editor.apply();

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
