package com.mjc.mascotalink;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.security.EncryptedPreferencesHelper;
import com.mjc.mascotalink.utils.InputUtils;

import java.util.HashMap;
import java.util.Map;

public class MetodoPagoActivity extends AppCompatActivity {

    private String PREFS = "WizardPaseador";
    private AutoCompleteTextView etBanco;
    private TextInputEditText etCuenta;
    private TextInputLayout tilBanco, tilCuenta;
    private EncryptedPreferencesHelper encryptedPrefs;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String metodoPagoId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metodo_pago);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Views
        etBanco = findViewById(R.id.et_banco);
        etCuenta = findViewById(R.id.et_cuenta);
        tilBanco = findViewById(R.id.til_banco);
        tilCuenta = findViewById(R.id.til_cuenta);
        Button btnGuardar = findViewById(R.id.btn_guardar_pago);
        Button btnCancelar = findViewById(R.id.btn_cancelar);

        // Setup Bank Dropdown
        String[] bancos = getResources().getStringArray(R.array.bancos);
        etBanco.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, bancos));

        // Mode Check
        metodoPagoId = getIntent().getStringExtra("metodo_pago_id");
        if (metodoPagoId != null && !metodoPagoId.isEmpty()) {
            // No title setter needed for custom layout header, it's static "Método de Pago"
            loadMetodoPagoFromFirestore(metodoPagoId);
        }

        String passedPrefs = getIntent().getStringExtra("prefs");
        if (passedPrefs != null && !passedPrefs.trim().isEmpty()) {
            PREFS = passedPrefs.trim();
        }
        encryptedPrefs = EncryptedPreferencesHelper.getInstance(this);
        
        // Only load from prefs if NOT updating an existing Firestore doc
        if (metodoPagoId == null) {
            loadStateFromPrefs();
        }

        // SafeClickListener para prevenir doble-click
        btnGuardar.setOnClickListener(InputUtils.createSafeClickListener(v -> guardarMetodoPago()));
        btnCancelar.setOnClickListener(InputUtils.createSafeClickListener(v -> finish()));
    }

    private void loadMetodoPagoFromFirestore(String id) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        
        db.collection("usuarios").document(user.getUid()).collection("metodos_pago").document(id).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        etBanco.setText(document.getString("banco"), false);
                        etCuenta.setText(document.getString("numero_cuenta"));
                    }
                });
    }

    private void loadStateFromPrefs() {
        if (encryptedPrefs == null) return;
        String savedBank = encryptedPrefs.getString(prefKey("pago_banco"), "");
        String savedAccount = encryptedPrefs.getString(prefKey("pago_cuenta"), "");

        if (InputUtils.isNotEmpty(savedBank)) etBanco.setText(savedBank, false);
        if (InputUtils.isNotEmpty(savedAccount)) etCuenta.setText(savedAccount);
    }

    private void guardarMetodoPago() {
        String banco = etBanco.getText().toString().trim();
        String cuenta = etCuenta.getText().toString().trim();

        if (!validateInputs(banco, cuenta)) return;

        FirebaseUser user = mAuth.getCurrentUser();

        // UPDATE case
        if (metodoPagoId != null && !metodoPagoId.isEmpty()) {
            if (user != null) {
                Map<String, Object> mp = new HashMap<>();
                mp.put("banco", banco);
                mp.put("numero_cuenta", cuenta);
                mp.put("fecha_actualizacion", Timestamp.now());

                db.collection("usuarios").document(user.getUid()).collection("metodos_pago").document(metodoPagoId)
                        .update(mp)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Actualizado correctamente", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        });
            }
            return;
        }

        // CREATE case (Wizard or New)
        if (user == null) {
            // Wizard flow (Local)
            if (encryptedPrefs != null) {
                encryptedPrefs.putBoolean(prefKey("metodo_pago_completo"), true);
                encryptedPrefs.putString(prefKey("pago_banco"), banco);
                encryptedPrefs.putString(prefKey("pago_cuenta"), cuenta);
            }
            // Legacy prefs fallback
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean("metodo_pago_completo", true)
                    .putString("pago_banco", banco)
                    .putString("pago_cuenta", cuenta)
                    .apply();

            Toast.makeText(this, "Guardado localmente", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            // Logged in -> Save to Firestore
            Map<String, Object> mp = new HashMap<>();
            mp.put("banco", banco);
            mp.put("numero_cuenta", cuenta);
            mp.put("predeterminado", false);
            mp.put("fecha_registro", Timestamp.now());

            db.collection("usuarios").document(user.getUid()).collection("metodos_pago")
                    .add(mp)
                    .addOnSuccessListener(ref -> {
                        Toast.makeText(this, "Método añadido", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    });
        }
    }

    private boolean validateInputs(String banco, String cuenta) {
        tilBanco.setError(null);
        tilCuenta.setError(null);

        if (!InputUtils.isNotEmpty(banco)) {
            tilBanco.setError("Selecciona un banco");
            return false;
        }
        if (!InputUtils.isNotEmpty(cuenta)) {
            tilCuenta.setError("Ingresa el número de cuenta");
            return false;
        }
        return true;
    }

    private String prefKey(String key) {
        return PREFS + "_" + key;
    }
}