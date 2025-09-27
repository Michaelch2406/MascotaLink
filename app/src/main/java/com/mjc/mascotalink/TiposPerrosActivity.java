package com.mjc.mascotalink;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TiposPerrosActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private CheckBox cbPequeno, cbMediano, cbGrande;
    private CheckBox cbCalmo, cbActivo, cbReactividadBaja, cbReactividadAlta;
    private Button btnGuardar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tipos_perros);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        cbPequeno = findViewById(R.id.cb_pequeno);
        cbMediano = findViewById(R.id.cb_mediano);
        cbGrande = findViewById(R.id.cb_grande);
        cbCalmo = findViewById(R.id.cb_calmo);
        cbActivo = findViewById(R.id.cb_activo);
        cbReactividadBaja = findViewById(R.id.cb_reactividad_baja);
        cbReactividadAlta = findViewById(R.id.cb_reactividad_alta);
        btnGuardar = findViewById(R.id.btn_guardar_tipos);

        btnGuardar.setOnClickListener(v -> guardarTiposPerros());
    }

    private void guardarTiposPerros() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_LONG).show();
            return;
        }
        
        String uid = mAuth.getCurrentUser().getUid();

        List<String> tamanos = new ArrayList<>();
        if (cbPequeno.isChecked()) tamanos.add("pequeno");
        if (cbMediano.isChecked()) tamanos.add("mediano");
        if (cbGrande.isChecked()) tamanos.add("grande");

        List<String> temperamentos = new ArrayList<>();
        if (cbCalmo.isChecked()) temperamentos.add("calmo");
        if (cbActivo.isChecked()) temperamentos.add("activo");
        if (cbReactividadBaja.isChecked()) temperamentos.add("reactividad_baja");
        if (cbReactividadAlta.isChecked()) temperamentos.add("reactividad_alta");

        // Validar que al menos una opción de tamaño esté seleccionada
        if (tamanos.isEmpty()) {
            Toast.makeText(this, "Debes seleccionar al menos un tamaño de perro", Toast.LENGTH_LONG).show();
            return;
        }
        
        // Validar que al menos una opción de temperamento esté seleccionada
        if (temperamentos.isEmpty()) {
            Toast.makeText(this, "Debes seleccionar al menos un tipo de temperamento", Toast.LENGTH_LONG).show();
            return;
        }

        Map<String, Object> manejoPerros = new HashMap<>();
        manejoPerros.put("tamanos", tamanos);
        manejoPerros.put("temperamentos", temperamentos);

        btnGuardar.setEnabled(false);
        btnGuardar.setText("Guardando...");

        db.collection("paseadores").document(uid)
                .update("manejo_perros", manejoPerros)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Preferencias guardadas exitosamente", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnGuardar.setEnabled(true);
                    btnGuardar.setText("Guardar Preferencias");
                    Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
