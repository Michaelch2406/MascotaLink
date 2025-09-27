package com.mjc.mascotalink;

import android.os.Bundle;
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

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private AutoCompleteTextView etBanco;
    private EditText etCuenta;
    private Button btnGuardar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metodo_pago);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etBanco = findViewById(R.id.et_banco);
        etCuenta = findViewById(R.id.et_numero_cuenta);
        btnGuardar = findViewById(R.id.btn_guardar_metodo);

        String[] bancos = new String[]{"Banco Pichincha", "Produbanco", "Banco Guayaquil"};
        etBanco.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, bancos));

        btnGuardar.setOnClickListener(v -> guardarMetodoPago());
    }

    private void guardarMetodoPago() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_LONG).show();
            return;
        }
        String bancoSeleccionado = etBanco.getText().toString().trim();
        String numeroCuenta = etCuenta.getText().toString().trim();
        if (bancoSeleccionado.isEmpty() || numeroCuenta.isEmpty()) {
            Toast.makeText(this, "Completa banco y número de cuenta", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> metodoPago = new HashMap<>();
        metodoPago.put("banco", bancoSeleccionado);
        metodoPago.put("numero_cuenta", numeroCuenta);
        metodoPago.put("tipo", "Ahorros");
        metodoPago.put("predeterminado", true);
        metodoPago.put("fecha_registro", FieldValue.serverTimestamp());

        db.collection("usuarios").document(uid)
                .collection("metodos_pago")
                .add(metodoPago)
                .addOnSuccessListener(ref -> {
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
