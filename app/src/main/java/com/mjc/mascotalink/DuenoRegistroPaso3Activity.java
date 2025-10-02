package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class DuenoRegistroPaso3Activity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private EditText etDireccionRecogida;
    private Switch swMensajes;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dueno_registro_paso3);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etDireccionRecogida = findViewById(R.id.et_direccion_recogida);
        swMensajes = findViewById(R.id.sw_mensajes);
        Button btnMetodo = findViewById(R.id.btn_ingresar_metodo);
        Button btnGuardar = findViewById(R.id.btn_guardar);

        // Abrir pantalla de método de pago reutilizable
        btnMetodo.setOnClickListener(v -> {
            Intent i = new Intent(this, MetodoPagoActivity.class);
            i.putExtra("prefs", "WizardDueno"); // Para guardar en prefs del dueño
            startActivity(i);
        });

        btnGuardar.setOnClickListener(v -> guardarConfiguracion());
    }

    private void guardarConfiguracion() {
        if (mAuth.getCurrentUser() == null) { Toast.makeText(this, "Sesión inválida", Toast.LENGTH_SHORT).show(); return; }
        String uid = mAuth.getCurrentUser().getUid();

        String direccion = etDireccionRecogida.getText().toString().trim();
        boolean aceptaMensajes = swMensajes.isChecked();

        if (TextUtils.isEmpty(direccion)) { etDireccionRecogida.setError("Requerido"); return; }

        Map<String, Object> updates = new HashMap<>();
        updates.put("direccion_recogida", direccion);
        updates.put("acepta_mensajes", aceptaMensajes);

        findViewById(R.id.btn_guardar).setEnabled(false);

        db.collection("usuarios").document(uid).update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, DuenoRegistroPaso4Activity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    findViewById(R.id.btn_guardar).setEnabled(true);
                    Toast.makeText(this, "Error guardando configuración: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
