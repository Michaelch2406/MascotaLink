package com.mjc.mascotalink;

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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.security.EncryptedPreferencesHelper;

import java.util.HashMap;
import java.util.Map;

public class MetodoPagoActivity extends AppCompatActivity {

    private String PREFS = "WizardPaseador"; // default; can be overridden via Intent
    private AutoCompleteTextView etBanco;
    private EditText etCuenta;
    private EncryptedPreferencesHelper encryptedPrefs;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String metodoPagoId; // To distinguish between Create and Update

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metodo_pago);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etBanco = findViewById(R.id.et_banco);
        etCuenta = findViewById(R.id.et_numero_cuenta);
        Button btnGuardar = findViewById(R.id.btn_guardar_metodo);

        // Setup Bank AutoCompleteTextView
        String[] bancos = getResources().getStringArray(R.array.bancos);
        etBanco.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, bancos));

        // Check mode: Create (from wizard) or Update (from profile)
        metodoPagoId = getIntent().getStringExtra("metodo_pago_id");
        if (metodoPagoId != null && !metodoPagoId.isEmpty()) {
            // UPDATE MODE
            setTitle("Editar Método de Pago");
            loadMetodoPagoFromFirestore(metodoPagoId);
        } else {
            // CREATE MODE (for registration wizard)
            setTitle("Añadir Método de Pago");
        }
        String passedPrefs = getIntent().getStringExtra("prefs");
        if (passedPrefs != null && !passedPrefs.trim().isEmpty()) {
            PREFS = passedPrefs.trim();
        }
        encryptedPrefs = EncryptedPreferencesHelper.getInstance(this);
        loadStateFromPrefs();

        btnGuardar.setOnClickListener(v -> guardarMetodoPago());
    }

    private void loadMetodoPagoFromFirestore(String id) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Error: No hay sesión para cargar los datos.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        String uid = user.getUid();
        db.collection("usuarios").document(uid).collection("metodos_pago").document(id).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String banco = document.getString("banco");
                        String cuenta = document.getString("numero_cuenta");
                        etBanco.setText(banco, false); // false to not filter
                        etCuenta.setText(cuenta);
                    } else {
                        Toast.makeText(this, "Error: No se encontró el método de pago.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al cargar datos: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void loadStateFromPrefs() {
        if (encryptedPrefs == null) {
            return;
        }
        String savedBank = encryptedPrefs.getString(prefKey("pago_banco"), "");
        String savedAccount = encryptedPrefs.getString(prefKey("pago_cuenta"), "");
        etBanco.setText(savedBank, false);
        etCuenta.setText(savedAccount);
    }

    private void guardarMetodoPago() {
        String banco = etBanco.getText().toString().trim();
        String cuenta = etCuenta.getText().toString().trim();

        if (!validateInputs(banco, cuenta)) return;

        FirebaseUser user = mAuth.getCurrentUser();

        // UPDATE case (from profile)
        if (metodoPagoId != null && !metodoPagoId.isEmpty()) {
            if (user != null) {
                String uid = user.getUid();
                Map<String, Object> mp = new HashMap<>();
                mp.put("banco", banco);
                mp.put("numero_cuenta", cuenta);
                mp.put("fecha_actualizacion", Timestamp.now());

                db.collection("usuarios").document(uid).collection("metodos_pago").document(metodoPagoId)
                        .update(mp)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Método de pago actualizado", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Error al actualizar: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } else {
                Toast.makeText(this, "Error: No hay sesión de usuario para actualizar.", Toast.LENGTH_LONG).show();
            }
            return;
        }

        // CREATE case (registration wizard, no user yet)
        if (user == null) {
            if (encryptedPrefs != null) {
                encryptedPrefs.putBoolean(prefKey("metodo_pago_completo"), true);
                encryptedPrefs.putString(prefKey("pago_banco"), banco);
                encryptedPrefs.putString(prefKey("pago_cuenta"), cuenta);
                encryptedPrefs.putString(prefKey("selected_payment_method"), banco);
                encryptedPrefs.putString(prefKey("card_last_four"), getLastFourDigits(cuenta));
                encryptedPrefs.putString(prefKey("card_holder_name"), "");
            }
            // Plain prefs for paso 5 validation
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean("metodo_pago_completo", true)
                    .putString("pago_banco", banco)
                    .putString("pago_cuenta", cuenta)
                    .apply();

            Toast.makeText(this, "Método de pago guardado", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            // From PROFILE (logged-in user adding a NEW payment method) -> Save to Firestore
            String uid = user.getUid();
            Map<String, Object> mp = new HashMap<>();
            mp.put("banco", banco);
            mp.put("numero_cuenta", cuenta);
            mp.put("predeterminado", false); // Set to false, logic for default should be handled elsewhere
            mp.put("fecha_registro", Timestamp.now());

            db.collection("usuarios").document(uid).collection("metodos_pago")
                    .add(mp)
                    .addOnSuccessListener(ref -> {
                        Toast.makeText(this, "Nuevo método de pago añadido", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
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

    private String prefKey(String key) {
        return PREFS + "_" + key;
    }

    private String getLastFourDigits(String input) {
        if (input == null) {
            return "";
        }
        String clean = input.replaceAll("\\D", "");
        if (clean.length() <= 4) {
            return clean;
        }
        return clean.substring(clean.length() - 4);
    }
}
