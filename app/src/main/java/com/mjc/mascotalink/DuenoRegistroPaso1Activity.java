package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DuenoRegistroPaso1Activity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private EditText etNombre, etApellido, etTelefono, etCorreo, etDireccion, etCedula;
    private TextInputLayout tilPassword;
    private TextInputEditText etPassword;
    private CheckBox cbTerminos;
    private Button btnRegistrarse;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dueno_registro_paso1);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etNombre = findViewById(R.id.et_nombre);
        etApellido = findViewById(R.id.et_apellido);
        etTelefono = findViewById(R.id.et_telefono);
        etCorreo = findViewById(R.id.et_correo);
        etDireccion = findViewById(R.id.et_direccion);
        etCedula = findViewById(R.id.et_cedula);
        tilPassword = findViewById(R.id.til_password);
        etPassword = findViewById(R.id.et_password);
        cbTerminos = findViewById(R.id.cb_terminos);
        btnRegistrarse = findViewById(R.id.btn_registrarse);

        btnRegistrarse.setOnClickListener(v -> intentarRegistro());

        actualizarBoton();
        View.OnFocusChangeListener watcher = (v, has) -> actualizarBoton();
        etNombre.setOnFocusChangeListener(watcher);
        etApellido.setOnFocusChangeListener(watcher);
        etTelefono.setOnFocusChangeListener(watcher);
        etCorreo.setOnFocusChangeListener(watcher);
        etDireccion.setOnFocusChangeListener(watcher);
        etCedula.setOnFocusChangeListener(watcher);
        etPassword.setOnFocusChangeListener(watcher);
        cbTerminos.setOnCheckedChangeListener((b, c) -> actualizarBoton());
    }

    private void actualizarBoton() {
        btnRegistrarse.setEnabled(camposValidosBasicos() && cbTerminos.isChecked());
    }

    private boolean camposValidosBasicos() {
        return !isEmpty(etNombre) && !isEmpty(etApellido)
                && validarCedula(etCedula.getText().toString().trim())
                && !isEmpty(etDireccion) && validarTelefono(etTelefono.getText().toString().trim())
                && validarEmail(etCorreo.getText().toString().trim())
                && (etPassword.getText() != null && etPassword.getText().toString().length() >= 6);
    }

    private boolean isEmpty(EditText e) { return TextUtils.isEmpty(e.getText()); }

    private boolean validarEmail(String email) { return Patterns.EMAIL_ADDRESS.matcher(email).matches(); }

    private boolean validarTelefono(String t) {
        return t.matches("^(\\+593[0-9]{9}|09[0-9]{8})$");
    }

    private boolean validarCedula(String c) {
        if (c == null || !c.matches("\\d{10}")) return false;
        try {
            int provincia = Integer.parseInt(c.substring(0, 2));
            if (provincia < 1 || provincia > 24) return false;
            int tercer = Character.getNumericValue(c.charAt(2));
            if (tercer >= 6) return false;
            int[] coef = {2,1,2,1,2,1,2,1,2};
            int suma = 0;
            for (int i=0;i<9;i++) {
                int d = Character.getNumericValue(c.charAt(i)) * coef[i];
                if (d >= 10) d -= 9;
                suma += d;
            }
            int dig = suma % 10 == 0 ? 0 : 10 - (suma % 10);
            return dig == Character.getNumericValue(c.charAt(9));
        } catch (Exception ex) { return false; }
    }

    private void intentarRegistro() {
        if (!cbTerminos.isChecked()) {
            toast("Debes aceptar los términos y condiciones");
            return;
        }
        if (!camposValidosBasicos()) {
            toast("Corrige los campos");
            return;
        }

        String email = etCorreo.getText().toString().trim();
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        btnRegistrarse.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(this, (AuthResult res) -> {
                    String uid = res.getUser().getUid();
                    crearDocsFirestore(uid, () -> {
                        toast("Cuenta creada");
                        startActivity(new Intent(this, DuenoRegistroPaso2Activity.class));
                        finish();
                    });
                })
                .addOnFailureListener(e -> {
                    btnRegistrarse.setEnabled(true);
                    toast("Error registrando: " + e.getMessage());
                });
    }

    private interface Done { void ok(); }

    private void crearDocsFirestore(String uid, Done done) {
        String nombre = etNombre.getText().toString().trim();
        String apellido = etApellido.getText().toString().trim();
        String telefono = etTelefono.getText().toString().trim();
        String correo = etCorreo.getText().toString().trim();
        String direccion = etDireccion.getText().toString().trim();
        String cedula = etCedula.getText().toString().trim();

        Map<String, Object> usuario = new HashMap<>();
        usuario.put("nombre", nombre);
        usuario.put("apellido", apellido);
        usuario.put("cedula", cedula);
        usuario.put("correo", correo);
        usuario.put("telefono", telefono);
        usuario.put("direccion", direccion);
        usuario.put("fecha_registro", Timestamp.now());
        usuario.put("activo", true);
        usuario.put("foto_perfil", "");
        usuario.put("selfie_url", "");
        usuario.put("nombre_display", String.format(Locale.getDefault(), "%s %s", nombre, apellido).trim());
        usuario.put("perfil_ref", "/duenos/" + uid);
        usuario.put("rol", "DUENO");

        Map<String, Object> dueno = new HashMap<>();
        dueno.put("cedula", cedula);
        dueno.put("direccion_recogida", direccion);
        dueno.put("acepta_terminos", true);
        dueno.put("verificacion_estado", "PENDIENTE");
        dueno.put("verificacion_fecha", Timestamp.now());
        dueno.put("ultima_actualizacion", Timestamp.now());

        DocumentReference userRef = db.collection("usuarios").document(uid);
        DocumentReference duenoRef = db.collection("duenos").document(uid);

        userRef.set(usuario)
                .continueWithTask(t -> duenoRef.set(dueno))
                .addOnSuccessListener(aVoid -> done.ok())
                .addOnFailureListener(e -> toast("Error guardando datos: " + e.getMessage()));
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
