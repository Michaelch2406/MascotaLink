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
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class DisponibilidadActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private AutoCompleteTextView etDia;
    private EditText etHoraInicio, etHoraFin;
    private Button btnGuardar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disponibilidad);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etDia = findViewById(R.id.et_dia);
        etHoraInicio = findViewById(R.id.et_hora_inicio);
        etHoraFin = findViewById(R.id.et_hora_fin);
        btnGuardar = findViewById(R.id.btn_guardar_disponibilidad);

        String[] dias = new String[]{"Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"};
        etDia.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dias));

        btnGuardar.setOnClickListener(v -> guardarDisponibilidad());
    }

    private void guardarDisponibilidad() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_LONG).show();
            return;
        }
        String dia = etDia.getText().toString().trim();
        String inicio = etHoraInicio.getText().toString().trim();
        String fin = etHoraFin.getText().toString().trim();
        if (dia.isEmpty() || inicio.isEmpty() || fin.isEmpty()) {
            Toast.makeText(this, "Completa día y horas", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> disponibilidad = new HashMap<>();
        disponibilidad.put("dia_semana", dia);
        disponibilidad.put("hora_inicio", inicio);
        disponibilidad.put("hora_fin", fin);
        disponibilidad.put("activo", true);

        db.collection("paseadores").document(uid)
                .collection("disponibilidad")
                .add(disponibilidad)
                .addOnSuccessListener(r -> { setResult(RESULT_OK); finish(); })
                .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
