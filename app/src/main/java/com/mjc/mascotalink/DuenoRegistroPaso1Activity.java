package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DuenoRegistroPaso1Activity extends AppCompatActivity {



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

        configurarTerminosYCondiciones();

        // Configurar listener para el botón de continuar
        btnRegistrarse.setOnClickListener(v -> intentarRegistro());

        // Configurar TextWatcher para validación en tiempo real
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                actualizarBoton();
            }
        };

        etNombre.addTextChangedListener(textWatcher);
        etApellido.addTextChangedListener(textWatcher);
        etTelefono.addTextChangedListener(textWatcher);
        etCorreo.addTextChangedListener(textWatcher);
        etDireccion.addTextChangedListener(textWatcher);
        etCedula.addTextChangedListener(textWatcher);
        etPassword.addTextChangedListener(textWatcher);

        cbTerminos.setOnCheckedChangeListener((buttonView, isChecked) -> actualizarBoton());

        // Llamada inicial para establecer el estado del botón
        actualizarBoton();
    }

    private void configurarTerminosYCondiciones() {
        String textoTerminos = "Acepto los <a href='#'>Términos y Condiciones</a> y la <a href='#'>Política de Privacidad</a>";
        cbTerminos.setText(Html.fromHtml(textoTerminos, Html.FROM_HTML_MODE_LEGACY));
        cbTerminos.setMovementMethod(LinkMovementMethod.getInstance());

        cbTerminos.setOnClickListener(v -> {
            if (!cbTerminos.isChecked()) {
                mostrarDialogoTerminos();
            }
        });
    }

    private void mostrarDialogoTerminos() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setPadding(50, 50, 50, 50);
        textView.setTextSize(14);
        textView.setText(getTextoTerminosCompleto());
        scrollView.addView(textView);

        builder.setTitle("Términos y Condiciones")
                .setView(scrollView)
                .setPositiveButton("Aceptar", (dialog, which) -> {
                    cbTerminos.setChecked(true);
                    actualizarBoton();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> cbTerminos.setChecked(false))
                .setCancelable(false)
                .show();
    }

    private String getTextoTerminosCompleto() {
        return "TÉRMINOS Y CONDICIONES DE USO - MascotaLink (Dueños)\n\n" +
                "1. ACEPTACIÓN DE TÉRMINOS\n" +
                "Al registrarte como dueño de mascota en MascotaLink, aceptas cumplir con estos términos y condiciones.\n\n" +
                "2. RESPONSABILIDADES DEL DUEÑO\n" +
                "- Proporcionar información precisa y completa sobre tu mascota.\n" +
                "- Asegurarte de que tu mascota esté al día con sus vacunas y tratamientos.\n" +
                "- Informar al paseador sobre cualquier comportamiento o necesidad especial.\n\n" +
                "3. PAGOS Y CANCELACIONES\n" +
                "- Realizar los pagos de los servicios a través de la plataforma.\n" +
                "- Respetar las políticas de cancelación de los paseadores.\n\n" +
                "4. CÓDIGO DE CONDUCTA\n" +
                "- Tratar a los paseadores con respeto y profesionalismo.\n" +
                "- No solicitar ni realizar pagos fuera de la plataforma.\n\n" +
                "5. PRIVACIDAD Y DATOS\n" +
                "- Aceptas que la información de tu perfil sea visible para los paseadores.\n" +
                "- MascotaLink se compromete a proteger tus datos según la política de privacidad.\n\n" +
                "6. LIMITACIÓN DE RESPONSABILIDAD\n" +
                "MascotaLink actúa como intermediario y no se hace responsable de incidentes ocurridos durante los paseos.\n\n" +
                "Al marcar esta casilla, confirmas que has leído, entendido y aceptas cumplir con todos estos términos y condiciones.\n\n" +
                "Fecha de última actualización: Octubre 2025";
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
        if (!camposValidosBasicos()) {
            toast("Por favor, corrige los campos marcados en rojo.");
            // Podrías incluso hacer scroll hasta el primer campo con error
            return;
        }
        if (!cbTerminos.isChecked()) {
            toast("Debes aceptar los términos y condiciones para continuar.");
            return;
        }

        // Guardar todos los datos en SharedPreferences
        SharedPreferences.Editor editor = getSharedPreferences("WizardDueno", MODE_PRIVATE).edit();
        editor.putString("nombre", etNombre.getText().toString().trim());
        editor.putString("apellido", etApellido.getText().toString().trim());
        editor.putString("telefono", etTelefono.getText().toString().trim());
        editor.putString("correo", etCorreo.getText().toString().trim());
        editor.putString("direccion", etDireccion.getText().toString().trim());
        editor.putString("cedula", etCedula.getText().toString().trim());
        if (etPassword.getText() != null) {
            editor.putString("password", etPassword.getText().toString());
        }
        editor.putBoolean("acepta_terminos", cbTerminos.isChecked());
        editor.apply();

        toast("Paso 1 completado. Continúa con el siguiente paso.");

        // Navegar al siguiente paso
        startActivity(new Intent(this, DuenoRegistroPaso2Activity.class));
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
