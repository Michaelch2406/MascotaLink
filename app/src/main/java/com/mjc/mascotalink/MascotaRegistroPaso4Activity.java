package com.mjc.mascotalink;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class MascotaRegistroPaso4Activity extends AppCompatActivity {

    private static final String PREFS = "MascotaWizard";

    private EditText etRutina, etCorrea, etRecompensas, etEmergencia, etAcceso, etNotas;
    private Button btnGuardar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mascota_registro_paso4);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        etRutina = findViewById(R.id.et_rutina);
        etCorrea = findViewById(R.id.et_correa);
        etRecompensas = findViewById(R.id.et_recompensas);
        etEmergencia = findViewById(R.id.et_emergencia);
        etAcceso = findViewById(R.id.et_acceso);
        etNotas = findViewById(R.id.et_notas);
        btnGuardar = findViewById(R.id.btn_guardar);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        btnGuardar.setOnClickListener(v -> guardarMascotaCompleta());
    }

    private void guardarMascotaCompleta() {
        if (mAuth.getCurrentUser() == null) { Toast.makeText(this, "Sesión inválida", Toast.LENGTH_SHORT).show(); return; }
        String duenoId = mAuth.getCurrentUser().getUid();

        // Paso 4 - Instrucciones
        Map<String, Object> instrucciones = new HashMap<>();
        instrucciones.put("rutina_paseo", etRutina.getText().toString().trim());
        instrucciones.put("tipo_correa_arnes", etCorrea.getText().toString().trim());
        instrucciones.put("recompensas", etRecompensas.getText().toString().trim());
        instrucciones.put("instrucciones_emergencia", etEmergencia.getText().toString().trim());
        instrucciones.put("acceso_vivienda", etAcceso.getText().toString().trim());
        instrucciones.put("notas_adicionales", etNotas.getText().toString().trim());

        // Paso 1
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String nombre = prefs.getString("nombre", "");
        String raza = prefs.getString("raza", "");
        String sexo = prefs.getString("sexo", "Macho");
        long fechaNac = prefs.getLong("fecha_nacimiento", 0);
        String tamano = prefs.getString("tamano", "");
        String pesoStr = prefs.getString("peso", "");
        String fotoUriStr = prefs.getString("foto_uri", "");

        // Paso 2 - Salud
        Map<String, Object> salud = new HashMap<>();
        salud.put("vacunas_al_dia", prefs.getBoolean("salud_vacunas_al_dia", false));
        salud.put("desparasitacion_aldia", prefs.getBoolean("salud_desparasitacion_aldia", false));
        long ultimaVet = prefs.getLong("salud_ultima_visita_vet", 0);
        salud.put("ultima_visita_vet", ultimaVet == 0 ? null : new Timestamp(new java.util.Date(ultimaVet)));
        salud.put("condiciones_medicas", prefs.getString("salud_condiciones_medicas", ""));
        salud.put("medicamentos_actuales", prefs.getString("salud_medicamentos_actuales", ""));
        salud.put("veterinario_nombre", prefs.getString("salud_veterinario_nombre", ""));
        salud.put("veterinario_telefono", prefs.getString("salud_veterinario_telefono", ""));

        // Paso 3 - Comportamiento
        Map<String, Object> comportamiento = new HashMap<>();
        comportamiento.put("nivel_energia", prefs.getString("comp_nivel_energia", ""));
        comportamiento.put("con_personas", prefs.getString("comp_con_personas", ""));
        comportamiento.put("con_otros_animales", prefs.getString("comp_con_otros_animales", ""));
        comportamiento.put("con_otros_perros", prefs.getString("comp_con_otros_perros", ""));
        comportamiento.put("habitos_correa", prefs.getString("comp_habitos_correa", ""));
        comportamiento.put("comandos_conocidos", prefs.getString("comp_comandos_conocidos", ""));
        comportamiento.put("miedos_fobias", prefs.getString("comp_miedos_fobias", ""));
        comportamiento.put("manias_habitos", prefs.getString("comp_manias_habitos", ""));

        // Documento mascota base
        Map<String, Object> mascota = new HashMap<>();
        mascota.put("nombre", nombre);
        mascota.put("raza", raza);
        mascota.put("sexo", sexo);
        mascota.put("fecha_nacimiento", fechaNac == 0 ? null : new Timestamp(new java.util.Date(fechaNac)));
        mascota.put("tamano", tamano);
        double peso = 0;
        try { peso = TextUtils.isEmpty(pesoStr) ? 0 : Double.parseDouble(pesoStr); } catch (Exception ignored) {}
        mascota.put("peso", peso);
        mascota.put("foto_principal_url", "");
        mascota.put("fecha_registro", FieldValue.serverTimestamp());
        mascota.put("ultima_actualizacion", FieldValue.serverTimestamp());
        mascota.put("salud", salud);
        mascota.put("comportamiento", comportamiento);
        mascota.put("instrucciones", instrucciones);

        btnGuardar.setEnabled(false);

        // Crear documento primero
        db.collection("duenos").document(duenoId)
                .collection("mascotas")
                .add(mascota)
                .addOnSuccessListener(docRef -> {
                    // Subir foto si existe y actualizar URL
                    if (!TextUtils.isEmpty(fotoUriStr)) {
                        Uri local = Uri.parse(fotoUriStr);
                        StorageReference ref = storage.getReference().child("duenos/"+duenoId+"/mascotas/"+docRef.getId()+"/foto_principal.jpg");
                        ref.putFile(local)
                                .continueWithTask(t -> ref.getDownloadUrl())
                                .addOnSuccessListener(uri -> actualizarFoto(docRef, uri.toString()))
                                .addOnFailureListener(e -> finalizarConError(e.getMessage()));
                    } else {
                        Toast.makeText(this, "Mascota registrada", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> finalizarConError(e.getMessage()));
    }

    private void actualizarFoto(DocumentReference docRef, String url) {
        docRef.update("foto_principal_url", url, "ultima_actualizacion", FieldValue.serverTimestamp())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Mascota registrada", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> finalizarConError(e.getMessage()));
    }

    private void finalizarConError(String msg) {
        btnGuardar.setEnabled(true);
        Toast.makeText(this, "Error: " + msg, Toast.LENGTH_LONG).show();
    }
}
