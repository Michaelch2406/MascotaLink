package com.mjc.mascotalink;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditarPerfilPaseadorActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;

    private EditText etNombre, etApellido, etMotivacion, etVideoUrl, etAnosExperiencia, etInicioExperiencia, etTiposPerros;
    private Button btnGuardarCambios;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editar_perfil_paseador);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        initViews();
        setupToolbar();
        loadPaseadorData();
        setupListeners();
    }

    private void initViews() {
        etNombre = findViewById(R.id.et_nombre);
        etApellido = findViewById(R.id.et_apellido);
        etMotivacion = findViewById(R.id.et_motivacion);
        etVideoUrl = findViewById(R.id.et_video_url);
        etAnosExperiencia = findViewById(R.id.et_anos_experiencia);
        etInicioExperiencia = findViewById(R.id.et_inicio_experiencia);
        etTiposPerros = findViewById(R.id.et_tipos_perros);
        btnGuardarCambios = findViewById(R.id.btn_guardar_cambios);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadPaseadorData() {
        if (currentUserId == null) {
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("usuarios").document(currentUserId).get().addOnSuccessListener(userDoc -> {
            if (userDoc.exists()) {
                etNombre.setText(userDoc.getString("nombre"));
                etApellido.setText(userDoc.getString("apellido"));
            }
        }).addOnFailureListener(e -> Toast.makeText(EditarPerfilPaseadorActivity.this, "Error al cargar datos de usuario.", Toast.LENGTH_SHORT).show());

        db.collection("paseadores").document(currentUserId).get().addOnSuccessListener(paseadorDoc -> {
            if (paseadorDoc.exists()) {
                Map<String, Object> perfilProfesional = (Map<String, Object>) paseadorDoc.get("perfil_profesional");
                if (perfilProfesional != null) {
                    etMotivacion.setText((String) perfilProfesional.get("motivacion"));
                    etVideoUrl.setText((String) perfilProfesional.get("video_presentacion_url"));
                    etAnosExperiencia.setText(String.valueOf(perfilProfesional.get("anos_experiencia")));
                    etInicioExperiencia.setText((String) perfilProfesional.get("inicio_experiencia"));
                }

                Map<String, Object> manejoPerros = (Map<String, Object>) paseadorDoc.get("manejo_perros");
                if (manejoPerros != null) {
                    List<String> tamanos = (List<String>) manejoPerros.get("tamanos");
                    if (tamanos != null && !tamanos.isEmpty()) {
                        etTiposPerros.setText(android.text.TextUtils.join(", ", tamanos));
                    }
                }
            }
        }).addOnFailureListener(e -> Toast.makeText(EditarPerfilPaseadorActivity.this, "Error al cargar datos de paseador.", Toast.LENGTH_SHORT).show());
    }

    private void setupListeners() {
        btnGuardarCambios.setOnClickListener(v -> savePaseadorData());
    }

    private void savePaseadorData() {
        if (currentUserId == null) return;

        // Actualizar colección 'usuarios'
        Map<String, Object> userUpdates = new HashMap<>();
        userUpdates.put("nombre", etNombre.getText().toString());
        userUpdates.put("apellido", etApellido.getText().toString());

        db.collection("usuarios").document(currentUserId).update(userUpdates)
                .addOnSuccessListener(aVoid -> Toast.makeText(EditarPerfilPaseadorActivity.this, "Datos de usuario actualizados.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(EditarPerfilPaseadorActivity.this, "Error al actualizar datos de usuario: " + e.getMessage(), Toast.LENGTH_SHORT).show());

        // Actualizar colección 'paseadores'
        Map<String, Object> paseadorUpdates = new HashMap<>();

        Map<String, Object> perfilProfesionalUpdates = new HashMap<>();
        perfilProfesionalUpdates.put("motivacion", etMotivacion.getText().toString());
        perfilProfesionalUpdates.put("video_presentacion_url", etVideoUrl.getText().toString());
        try {
            perfilProfesionalUpdates.put("anos_experiencia", Long.parseLong(etAnosExperiencia.getText().toString()));
        } catch (NumberFormatException e) {
            perfilProfesionalUpdates.put("anos_experiencia", 0L);
        }
        perfilProfesionalUpdates.put("inicio_experiencia", etInicioExperiencia.getText().toString());
        paseadorUpdates.put("perfil_profesional", perfilProfesionalUpdates);

        Map<String, Object> manejoPerrosUpdates = new HashMap<>();
        List<String> tamanosList = Arrays.asList(etTiposPerros.getText().toString().split(",\\s*"));
        manejoPerrosUpdates.put("tamanos", tamanosList);
        paseadorUpdates.put("manejo_perros", manejoPerrosUpdates);

        db.collection("paseadores").document(currentUserId).update(paseadorUpdates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(EditarPerfilPaseadorActivity.this, "Perfil de paseador actualizado.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(EditarPerfilPaseadorActivity.this, "Error al actualizar perfil de paseador: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
