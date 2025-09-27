package com.mjc.mascotalink;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PaseadorRegistroPaso1Activity extends AppCompatActivity {

    private static final String TAG = "Paso1Paseador";
    private static final String PREFS = "WizardPaseador";

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    private EditText etNombre, etApellido, etCedula, etFechaNac, etDomicilio, etTelefono, etEmail, etPassword;
    private TextInputLayout tilPassword;
    private CheckBox cbAceptaTerminos;
    private Button btnContinuar;
    private ProgressBar progressBar;

    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso1);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        // Firebase (Producción)
        // Firebase emuladores
        String host = "192.168.0.147";
        mAuth = FirebaseAuth.getInstance();
        mAuth.useEmulator(host, 9099);
        storage = FirebaseStorage.getInstance();
        storage.useEmulator(host, 9199);

        bindViews();
        wireDatePicker();
        loadSavedState();
        setupWatchers();
        configurarTerminosYCondiciones();

        btnContinuar.setOnClickListener(v -> onContinuar());
        
        // Configurar enlace para ir al login
        findViewById(R.id.tv_login_link).setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish(); // Cerrar esta actividad para evitar que el usuario regrese con el botón atrás
        });

        updateButtonEnabled();
    }

    private void bindViews() {
        etNombre = findViewById(R.id.et_nombre);
        etApellido = findViewById(R.id.et_apellido);
        etCedula = findViewById(R.id.et_cedula);
        etFechaNac = findViewById(R.id.et_fecha_nacimiento);
        etDomicilio = findViewById(R.id.et_domicilio);
        etTelefono = findViewById(R.id.et_telefono);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        tilPassword = findViewById(R.id.til_password);
        cbAceptaTerminos = findViewById(R.id.cb_acepta_terminos);
        btnContinuar = findViewById(R.id.btn_continuar);
        progressBar = new ProgressBar(this);
    }

    private void wireDatePicker() {
        etFechaNac.setInputType(InputType.TYPE_NULL);
        etFechaNac.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            DatePickerDialog dlg = new DatePickerDialog(this, (view, y, m, d) -> {
                String txt = String.format(Locale.getDefault(), "%02d/%02d/%04d", d, m + 1, y);
                etFechaNac.setText(txt);
                saveState();
                updateButtonEnabled();
            }, now.get(Calendar.YEAR) - 18, now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
            dlg.show();
        });
    }

    private void setupWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { saveState(); updateButtonEnabled(); }
        };
        
        // Watcher especial para email que limpia errores de "email ya registrado"
        TextWatcher emailWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Limpiar error de email duplicado cuando el usuario empiece a escribir
                if (etEmail.getError() != null && etEmail.getError().toString().contains("ya está registrado")) {
                    etEmail.setError(null);
                }
            }
            @Override public void afterTextChanged(Editable s) { saveState(); updateButtonEnabled(); }
        };
        
        // Watcher especial para contraseña
        TextWatcher passwordWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Limpiar error de contraseña débil cuando el usuario empiece a escribir
                if (tilPassword.getError() != null && tilPassword.getError().toString().contains("débil")) {
                    tilPassword.setError(null);
                }
            }
            @Override public void afterTextChanged(Editable s) { saveState(); updateButtonEnabled(); }
        };
        
        etNombre.addTextChangedListener(watcher);
        etApellido.addTextChangedListener(watcher);
        etCedula.addTextChangedListener(watcher);
        etFechaNac.addTextChangedListener(watcher);
        etDomicilio.addTextChangedListener(watcher);
        etTelefono.addTextChangedListener(watcher);
        etEmail.addTextChangedListener(emailWatcher);
        etPassword.addTextChangedListener(passwordWatcher);
    }

    private void configurarTerminosYCondiciones() {
        // Texto con enlace clickeable
        String textoTerminos = "Acepto los <a href='#'>Términos y Condiciones</a> y la <a href='#'>Política de Privacidad</a>";
        cbAceptaTerminos.setText(Html.fromHtml(textoTerminos, Html.FROM_HTML_MODE_LEGACY));
        cbAceptaTerminos.setMovementMethod(LinkMovementMethod.getInstance());
        
        // Listener para habilitar/deshabilitar botón
        cbAceptaTerminos.setOnCheckedChangeListener((buttonView, isChecked) -> {
            validarCamposYHabilitarBoton();
        });
        
        // Hacer el texto clickeable para mostrar términos completos
        cbAceptaTerminos.setOnClickListener(v -> {
            if (!cbAceptaTerminos.isChecked()) {
                // Si no está marcado, mostrar diálogo antes de marcar
                mostrarDialogoTerminos();
            }
        });
    }

    private void mostrarDialogoTerminos() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        // Crear ScrollView para texto largo
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setPadding(50, 50, 50, 50);
        textView.setTextSize(14);
        textView.setText(getTextoTerminosCompleto());
        scrollView.addView(textView);
        
        builder.setTitle("Términos y Condiciones")
            .setView(scrollView)
            .setPositiveButton("Aceptar", (dialog, which) -> {
                cbAceptaTerminos.setChecked(true);
                validarCamposYHabilitarBoton();
            })
            .setNegativeButton("Cancelar", (dialog, which) -> {
                cbAceptaTerminos.setChecked(false);
            })
            .setCancelable(false)
            .show();
    }
    
    private String getTextoTerminosCompleto() {
        return "TÉRMINOS Y CONDICIONES DE USO - MascotaLink\n\n" +
               "1. ACEPTACIÓN DE TÉRMINOS\n" +
               "Al registrarte como paseador en MascotaLink, aceptas cumplir con estos términos y condiciones.\n\n" +
               "2. RESPONSABILIDADES DEL PASEADOR\n" +
               "- Cuidar la seguridad y bienestar de las mascotas bajo tu responsabilidad\n" +
               "- Seguir las instrucciones específicas del dueño de la mascota\n" +
               "- Reportar cualquier incidente o emergencia inmediatamente\n" +
               "- Mantener actualizados tus documentos de verificación\n\n" +
               "3. VERIFICACIÓN Y DOCUMENTOS\n" +
               "- Proporcionar documentación veraz y actualizada\n" +
               "- Someterte al proceso de verificación de antecedentes\n" +
               "- Mantener vigentes los certificados médicos requeridos\n\n" +
               "4. CÓDIGO DE CONDUCTA\n" +
               "- Tratar a las mascotas con respeto y cuidado\n" +
               "- Comunicarte de manera profesional con los dueños\n" +
               "- Seguir todas las normas de seguridad establecidas\n\n" +
               "5. PRIVACIDAD Y DATOS\n" +
               "- Proteger la información personal de los clientes\n" +
               "- No compartir datos de contacto fuera de la plataforma\n" +
               "- Respetar la privacidad de los hogares visitados\n\n" +
               "6. TERMINACIÓN DEL SERVICIO\n" +
               "MascotaLink se reserva el derecho de suspender o terminar el acceso del paseador en caso de incumplimiento de estos términos.\n\n" +
               "Al marcar esta casilla, confirmas que has leído, entendido y aceptas cumplir con todos estos términos y condiciones.\n\n" +
               "Fecha de última actualización: Septiembre 2025";
    }

    private void onContinuar() {
        if (!cbAceptaTerminos.isChecked()) {
            Toast.makeText(this, "Debes aceptar los términos y condiciones para continuar", 
                          Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!validarCamposPantalla1()) {
            Toast.makeText(this, "⚠️ Por favor corrige los errores antes de continuar", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Solo validar que el email no esté en uso, pero NO crear la cuenta aún
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        showLoading(true);
        
        // Verificar si el email ya existe sin crear la cuenta
        mAuth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        if (task.getResult().getSignInMethods().isEmpty()) {
                            // Email disponible, guardar datos localmente y continuar
                            guardarDatosCompletos();
                            Toast.makeText(this, "✅ Datos guardados. Continuando...", Toast.LENGTH_SHORT).show();
                            
                            // Navegar a la siguiente pantalla
                            Intent intent = new Intent(this, PaseadorRegistroPaso2Activity.class);
                            startActivity(intent);
                        } else {
                            // Email ya está en uso
                            etEmail.setError("⚠️ Este correo ya está registrado");
                            etEmail.requestFocus();
                            mostrarError("⚠️ Este correo electrónico ya está registrado.\n\n💡 Opciones:\n• Usa otro correo electrónico\n• Ve a 'Iniciar Sesión' si ya tienes cuenta");
                        }
                    } else {
                        // Error en la verificación
                        Log.e(TAG, "Error verificando email: ", task.getException());
                        String errorMsg = obtenerMensajeErrorVerificacion(task.getException());
                        if (errorMsg != null) {
                            mostrarError(errorMsg);
                        }
                    }
                });
    }
    
    private void guardarDatosCompletos() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Guardar todos los datos del paso 1
        editor.putString("nombre", etNombre.getText().toString().trim());
        editor.putString("apellido", etApellido.getText().toString().trim());
        editor.putString("cedula", etCedula.getText().toString().trim());
        editor.putString("fecha_nacimiento", etFechaNac.getText().toString().trim());
        editor.putString("domicilio", etDomicilio.getText().toString().trim());
        editor.putString("telefono", etTelefono.getText().toString().trim());
        editor.putString("email", etEmail.getText().toString().trim());
        editor.putString("password", etPassword.getText().toString().trim());
        
        // Marcar que el paso 1 está completo y términos aceptados
        editor.putBoolean("paso1_completo", true);
        editor.putBoolean("acepto_terminos", true);
        editor.putLong("timestamp_paso1", System.currentTimeMillis());
        editor.putLong("fecha_aceptacion_terminos", System.currentTimeMillis());
        
        editor.apply();
        
        Log.d(TAG, "Datos del paso 1 guardados localmente");
    }
    
    private String obtenerMensajeErrorVerificacion(Exception exception) {
        if (exception == null) {
            return "⚠️ Error desconocido al verificar el correo electrónico";
        }
        
        String mensaje = exception.getMessage();
        if (mensaje == null) {
            return "⚠️ Error desconocido al verificar el correo electrónico";
        }
        
        Log.e(TAG, "Error verificación: " + mensaje);
        
        // Traducir errores comunes de verificación de email
        if (mensaje.contains("invalid-email")) {
            etEmail.setError("⚠️ Formato de correo inválido");
            etEmail.requestFocus();
            return "⚠️ El formato del correo electrónico no es válido.";
        } else if (mensaje.contains("network") || mensaje.contains("timeout")) {
            return "⚠️ Error de conexión. Verifica tu internet e inténtalo nuevamente.";
        } else if (mensaje.contains("too-many-requests")) {
            return "⚠️ Demasiados intentos. Espera unos minutos antes de intentar nuevamente.";
        } else if (mensaje.contains("UNAVAILABLE") || mensaje.contains("INTERNAL")) {
            return "⚠️ Servicio temporalmente no disponible. Inténtalo nuevamente en unos minutos.";
        } else {
            // Por ahora, permitir continuar si hay un error de verificación
            Log.w(TAG, "Error de verificación no crítico, permitiendo continuar: " + mensaje);
            guardarDatosCompletos();
            Toast.makeText(this, "✅ Datos guardados. Continuando...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, PaseadorRegistroPaso2Activity.class);
            startActivity(intent);
            return null; // No mostrar error
        }
    }

    // Los documentos de Firebase se crearán solo al final del proceso en el Paso 3
    // Estos métodos se han movido al PaseadorRegistroPaso3Activity

    private Date parseFecha() {
        String f = etFechaNac.getText().toString().trim();
        try { return sdf.parse(f); } catch (ParseException e) { return null; }
    }

    private boolean validarCamposPantalla1() {
        boolean ok = true;
        
        // Validar nombre
        String nombre = etNombre.getText().toString().trim();
        if (TextUtils.isEmpty(nombre)) {
            etNombre.setError("⚠️ El nombre es obligatorio");
            ok = false;
        } else if (nombre.length() < 2) {
            etNombre.setError("⚠️ El nombre debe tener al menos 2 caracteres");
            ok = false;
        } else if (!nombre.matches("[A-Za-zÁÉÍÓÚáéíóúÑñ ]+")) {
            etNombre.setError("⚠️ Solo se permiten letras y espacios");
            ok = false;
        } else {
            etNombre.setError(null);
        }

        // Validar apellido
        String apellido = etApellido.getText().toString().trim();
        if (TextUtils.isEmpty(apellido)) {
            etApellido.setError("⚠️ El apellido es obligatorio");
            ok = false;
        } else if (apellido.length() < 2) {
            etApellido.setError("⚠️ El apellido debe tener al menos 2 caracteres");
            ok = false;
        } else if (!apellido.matches("[A-Za-zÁÉÍÓÚáéíóúÑñ ]+")) {
            etApellido.setError("⚠️ Solo se permiten letras y espacios");
            ok = false;
        } else {
            etApellido.setError(null);
        }

        // Validar cédula
        String cedula = etCedula.getText().toString().trim();
        if (TextUtils.isEmpty(cedula)) {
            etCedula.setError("⚠️ La cédula es obligatoria");
            ok = false;
        } else if (cedula.length() != 10) {
            etCedula.setError("⚠️ La cédula debe tener exactamente 10 dígitos");
            ok = false;
        } else if (!cedula.matches("\\d{10}")) {
            etCedula.setError("⚠️ La cédula solo debe contener números");
            ok = false;
        } else if (!validarCedulaEcuador(cedula)) {
            etCedula.setError("⚠️ Cédula ecuatoriana inválida. Verifica el dígito verificador");
            ok = false;
        } else {
            etCedula.setError(null);
        }

        // Validar fecha de nacimiento
        Date fecha = parseFecha();
        if (fecha == null) {
            etFechaNac.setError("⚠️ Selecciona tu fecha de nacimiento");
            ok = false;
        } else if (!validarEdad(fecha)) {
            etFechaNac.setError("⚠️ Debes ser mayor de 18 años para registrarte como paseador");
            ok = false;
        } else {
            etFechaNac.setError(null);
        }

        // Validar domicilio
        String domicilio = etDomicilio.getText().toString().trim();
        if (TextUtils.isEmpty(domicilio)) {
            etDomicilio.setError("⚠️ El domicilio es obligatorio");
            ok = false;
        } else if (domicilio.length() < 10) {
            etDomicilio.setError("⚠️ Ingresa una dirección más detallada (mínimo 10 caracteres)");
            ok = false;
        } else {
            etDomicilio.setError(null);
        }

        // Validar teléfono
        String telefono = etTelefono.getText().toString().trim();
        if (TextUtils.isEmpty(telefono)) {
            etTelefono.setError("⚠️ El teléfono es obligatorio");
            ok = false;
        } else if (!telefono.matches("^(\\+593[0-9]{9}|09[0-9]{8})$")) {
            etTelefono.setError("⚠️ Formato inválido. Usa: +593XXXXXXXXX o 09XXXXXXXX");
            ok = false;
        } else {
            etTelefono.setError(null);
        }

        // Validar email
        String email = etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("⚠️ El correo electrónico es obligatorio");
            ok = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("⚠️ Formato de correo inválido. Ejemplo: usuario@dominio.com");
            ok = false;
        } else {
            etEmail.setError(null);
        }

        // Validar contraseña
        String pass = etPassword.getText().toString().trim();
        if (TextUtils.isEmpty(pass)) {
            tilPassword.setError("⚠️ La contraseña es obligatoria");
            ok = false;
        } else if (pass.length() < 6) {
            tilPassword.setError("⚠️ La contraseña debe tener al menos 6 caracteres");
            ok = false;
        } else if (!pass.matches(".*[A-Za-z].*")) {
            tilPassword.setError("⚠️ La contraseña debe contener al menos una letra");
            ok = false;
        } else {
            tilPassword.setError(null);
        }

        return ok;
    }

    private boolean validarEdad(Date nacimiento) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -18);
        Date min = cal.getTime();
        return nacimiento.before(min);
    }

    private boolean validarCedulaEcuador(String cedula) {
        if (cedula == null || !cedula.matches("\\d{10}")) {
            return false;
        }
        
        try {
            // Validar provincia (primeros 2 dígitos)
            int provincia = Integer.parseInt(cedula.substring(0, 2));
            if (provincia < 1 || provincia > 24) {
                return false;
            }
            
            // Validar tercer dígito (debe ser menor a 6 para personas naturales)
            int tercerDigito = Character.getNumericValue(cedula.charAt(2));
            if (tercerDigito >= 6) {
                return false;
            }
            
            // Algoritmo de validación del dígito verificador
            int[] coeficientes = {2, 1, 2, 1, 2, 1, 2, 1, 2};
            int suma = 0;
            
            for (int i = 0; i < 9; i++) {
                int digito = Character.getNumericValue(cedula.charAt(i));
                int producto = digito * coeficientes[i];
                
                // Si el producto es mayor o igual a 10, restar 9
                if (producto >= 10) {
                    producto -= 9;
                }
                
                suma += producto;
            }
            
            // Calcular dígito verificador
            int residuo = suma % 10;
            int digitoVerificador = (residuo == 0) ? 0 : (10 - residuo);
            
            // Comparar con el último dígito de la cédula
            int ultimoDigito = Character.getNumericValue(cedula.charAt(9));
            
            return digitoVerificador == ultimoDigito;
            
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void saveState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit()
                .putString("nombre", etNombre.getText().toString())
                .putString("apellido", etApellido.getText().toString())
                .putString("cedula", etCedula.getText().toString())
                .putString("fecha", etFechaNac.getText().toString())
                .putString("domicilio", etDomicilio.getText().toString())
                .putString("telefono", etTelefono.getText().toString())
                .putString("email", etEmail.getText().toString())
                .putString("password", etPassword.getText().toString())
                .apply();
    }

    private void loadSavedState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        etNombre.setText(prefs.getString("nombre", ""));
        etApellido.setText(prefs.getString("apellido", ""));
        etCedula.setText(prefs.getString("cedula", ""));
        etFechaNac.setText(prefs.getString("fecha", ""));
        etDomicilio.setText(prefs.getString("domicilio", ""));
        etTelefono.setText(prefs.getString("telefono", ""));
        etEmail.setText(prefs.getString("email", ""));
        etPassword.setText(prefs.getString("password", ""));
    }

    private void validarCamposYHabilitarBoton() {
        // Validar que todos los campos estén completos Y términos aceptados
        boolean camposCompletos = validarTodosLosCampos();
        boolean terminosAceptados = cbAceptaTerminos.isChecked();
        
        btnContinuar.setEnabled(camposCompletos && terminosAceptados);
        
        // Cambiar color del botón según estado
        if (btnContinuar.isEnabled()) {
            btnContinuar.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.blue_primary)));
        } else {
            btnContinuar.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.gray_disabled)));
        }
    }
    
    private boolean validarTodosLosCampos() {
        return !TextUtils.isEmpty(etNombre.getText()) &&
                !TextUtils.isEmpty(etApellido.getText()) &&
                etCedula.getText().toString().matches("\\d{10}") &&
                !TextUtils.isEmpty(etFechaNac.getText()) &&
                !TextUtils.isEmpty(etDomicilio.getText()) &&
                !TextUtils.isEmpty(etTelefono.getText()) &&
                Patterns.EMAIL_ADDRESS.matcher(etEmail.getText().toString()).matches() &&
                etPassword.getText().toString().length() >= 6;
    }
    
    // Método de compatibilidad para mantener las llamadas existentes
    private void updateButtonEnabled() {
        validarCamposYHabilitarBoton();
    }

    private void showLoading(boolean show) {
        // botón deshabilitado mientras se crea cuenta
        btnContinuar.setEnabled(!show);
    }
    
    private void mostrarError(String msg) {
        if (msg == null || msg.trim().isEmpty()) {
            return; // No mostrar errores vacíos
        }
        
        // Si el mensaje es muy largo, usar AlertDialog en lugar de Toast
        if (msg.length() > 100 || msg.contains("\n")) {
            new AlertDialog.Builder(this)
                .setTitle("⚠️ Error")
                .setMessage(msg)
                .setPositiveButton("Entendido", null)
                .show();
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }
}
