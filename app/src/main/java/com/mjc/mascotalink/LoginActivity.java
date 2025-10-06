package com.mjc.mascotalink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    // Cambia a true si vas a usar los Emuladores de Firebase (y asegúrate de tenerlos corriendo)
    private static final boolean USE_FIREBASE_EMULATORS = true;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private CheckBox cbRemember;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d(TAG, "onCreate: Iniciando LoginActivity");
        
        try {
            mAuth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();
            if (mAuth != null) {
                mAuth.setLanguageCode("es");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error configurando Firebase: ", e);
            mostrarError("Error de configuración: " + e.getMessage());
        }

        bindViews();
        setupUI();
        setupEmailField();
        setupPasswordField();
        setupRememberMe();
        setupClickListeners();
        setupGoogleLogin();
    }

    private void bindViews() {
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        cbRemember = findViewById(R.id.cb_remember);
        progressBar = findViewById(R.id.pb_loading);
    }

    private void setupUI() {
        // Placeholder para personalizaciones adicionales si se requieren más adelante
        cbRemember.setText("Recordarme en este dispositivo");
    }

    private void setupClickListeners() {
        Button btnLogin = findViewById(R.id.btn_login);
        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password);
        TextView tvRegisterLink = findViewById(R.id.tv_register_link);

        btnLogin.setOnClickListener(v -> realizarLogin());

        // Abrir Activity dedicada para recuperación de contraseña
        tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        // Abrir Activity de selección/registro
        tvRegisterLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SeleccionRolActivity.class);
            startActivity(intent);
        });
    }

    // Email validación en tiempo real
    private void setupEmailField() {
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String email = s.toString().trim();
                if (email.length() > 0 && !isValidEmail(email)) {
                    tilEmail.setError("⚠️ Formato de correo inválido. Ejemplo: usuario@gmail.com");
                } else {
                    tilEmail.setError(null);
                }
            }
        });
    }

    // Password validación en tiempo real
    private void setupPasswordField() {
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String password = s.toString().trim();
                if (password.length() > 0 && password.length() < 6) {
                    tilPassword.setError("⚠️ La contraseña debe tener al menos 6 caracteres");
                } else {
                    tilPassword.setError(null);
                }
            }
        });
    }

    private void setupRememberMe() {
        SharedPreferences prefs = getSharedPreferences("MascotaLink", MODE_PRIVATE);
        boolean recordarSesion = prefs.getBoolean("recordar_sesion", false);
        if (recordarSesion) {
            String emailGuardado = prefs.getString("email_guardado", "");
            etEmail.setText(emailGuardado);
            cbRemember.setChecked(true);
        }
    }

    private void setupGoogleLogin() {
        Button btnGoogleLogin = findViewById(R.id.btn_google_login);
        btnGoogleLogin.setText("Continuar con Google");
        btnGoogleLogin.setOnClickListener(v -> mostrarMensaje("Función disponible próximamente"));
    }

    private void realizarLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        Log.d(TAG, "Intentando login con email: " + email);
        
        if (!validarCampos(email, password)) {
            Log.w(TAG, "Validación de campos fallida");
            return;
        }

        mostrarProgressBar(true);
        Log.d(TAG, "Iniciando autenticación con Firebase...");

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    mostrarProgressBar(false);
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Login exitoso");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Log.d(TAG, "Usuario autenticado: " + user.getUid());
                            verificarRolYRedirigir(user.getUid());
                        }
                    } else {
                        Log.e(TAG, "Error en login", task.getException());
                        manejarErrorLogin(task.getException());
                    }
                });
    }

    private void verificarRolYRedirigir(String uid) {
        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String rol = documentSnapshot.getString("rol");
                        if (cbRemember.isChecked()) {
                            guardarPreferenciasLogin(uid, rol);
                        }

                        if ("PASEADOR".equals(rol)) {
                            // Si es paseador, necesitamos consultar su estado de verificación en la colección 'paseadores'
                            db.collection("paseadores").document(uid).get()
                                    .addOnSuccessListener(paseadorDoc -> {
                                        if (paseadorDoc.exists()) {
                                            String verificacionEstado = paseadorDoc.getString("verificacion_estado");
                                            redirigirSegunRol(rol, verificacionEstado);
                                        } else {
                                            // Esto sería un estado inconsistente, un usuario con rol PASEADOR sin documento en paseadores
                                            mostrarError("Error de consistencia de datos del paseador.");
                                        }
                                    })
                                    .addOnFailureListener(e -> mostrarError("Error al verificar estado del paseador: " + e.getMessage()));
                        } else {
                            // Para otros roles (DUENO, ADMIN), redirigir directamente
                            redirigirSegunRol(rol, null);
                        }
                    } else {
                        mostrarError("Usuario no encontrado en la base de datos");
                    }
                })
                .addOnFailureListener(e -> mostrarError("Error al verificar datos de usuario: " + e.getMessage()));
    }

    private void redirigirSegunRol(String rol, String estadoVerificacion) {
        if (rol == null) {
            mostrarError("Rol de usuario no válido");
            return;
        }

        Intent intent = null;

        switch (rol) {
            case "PASEADOR":
                if ("APROBADO".equals(estadoVerificacion)) {
                    intent = new Intent(this, PerfilPaseadorActivity.class);
                } else if ("PENDIENTE".equals(estadoVerificacion)) {
                    mostrarMensaje("Tu perfil está en revisión. Te notificaremos cuando sea aprobado.");
                } else { // RECHAZADO u otro estado
                    mostrarMensaje("Tu perfil fue rechazado o está inactivo. Contacta a soporte.");
                }
                break;
            case "DUENO":
                intent = new Intent(this, PerfilDuenoActivity.class);
                break;
            case "ADMIN":
                intent = new Intent(this, MainActivity.class);
                break;
            default:
                mostrarError("Rol de usuario desconocido.");
                break;
        }

        if (intent != null) {
            startActivity(intent);
            finish(); // Cierra LoginActivity para que el usuario no pueda volver atrás
        }
        // Si el intent es null (ej. paseador pendiente), el usuario se queda en la pantalla de login
    }

    private boolean validarCampos(String email, String password) {
        boolean esValido = true;

        // Validar email
        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("⚠️ El correo electrónico es obligatorio");
            esValido = false;
        } else if (!isValidEmail(email)) {
            tilEmail.setError("⚠️ Formato de correo inválido. Ejemplo: usuario@gmail.com");
            esValido = false;
        } else {
            tilEmail.setError(null);
        }

        // Validar contraseña
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("⚠️ La contraseña es obligatoria");
            esValido = false;
        } else if (password.length() < 6) {
            tilPassword.setError("⚠️ La contraseña debe tener al menos 6 caracteres");
            esValido = false;
        } else {
            tilPassword.setError(null);
        }

        // Mostrar mensaje general si hay errores
        if (!esValido) {
            mostrarError("⚠️ Por favor corrige los errores antes de continuar");
        }

        return esValido;
    }

    private void manejarErrorLogin(Exception exception) {
        String mensajeError = "❌ Error desconocido";
        
        Log.e(TAG, "Error detallado en login: ", exception);
        
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            mensajeError = "⚠️ Credenciales inválidas. Verifica tu correo y contraseña.";
            // También mostrar error en los campos
            tilEmail.setError("⚠️ Correo o contraseña incorrectos");
            tilPassword.setError("⚠️ Correo o contraseña incorrectos");
            Log.e(TAG, "Error: Credenciales inválidas");
        } else if (exception instanceof FirebaseAuthInvalidUserException) {
            mensajeError = "⚠️ No existe una cuenta con este correo electrónico.";
            tilEmail.setError("⚠️ Este correo no está registrado");
            Log.e(TAG, "Error: Usuario no existe");
        } else if (exception instanceof FirebaseNetworkException) {
            mensajeError = "⚠️ Error de conexión. Verifica tu internet e inténtalo nuevamente.";
            Log.e(TAG, "Error de red: " + exception.getMessage());
        } else if (exception != null && exception.getMessage() != null) {
            String msg = exception.getMessage();
            Log.e(TAG, "Error con mensaje: " + msg);
            
            // Traducir errores comunes
            if (msg.contains("Cleartext HTTP traffic") || msg.contains("not permitted")) {
                mensajeError = "⚠️ Error de configuración de red. Contacta al soporte técnico.";
                Log.e(TAG, "Error de cleartext traffic detectado");
            } else if (msg.contains("too-many-requests")) {
                mensajeError = "⚠️ Demasiados intentos fallidos. Espera unos minutos antes de intentar nuevamente.";
            } else if (msg.contains("user-disabled")) {
                mensajeError = "⚠️ Esta cuenta ha sido deshabilitada. Contacta al soporte técnico.";
            } else {
                mensajeError = "❌ Error: " + msg;
            }
        }
        
        Log.e(TAG, "Mensaje final de error: " + mensajeError);
        mostrarError(mensajeError);
    }

    private void guardarPreferenciasLogin(String uid, String rol) {
        SharedPreferences prefs = getSharedPreferences("MascotaLink", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("recordar_sesion", true);
        editor.putString("email_guardado", etEmail.getText() != null ? etEmail.getText().toString().trim() : "");
        editor.putString("uid", uid);
        if (rol != null) editor.putString("rol", rol);
        editor.apply();
    }

    private void mostrarProgressBar(boolean mostrar) {
        if (progressBar != null) {
            progressBar.setVisibility(mostrar ? View.VISIBLE : View.GONE);
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void mostrarMensaje(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void mostrarError(String msg) {
        // Si el mensaje es muy largo, usar AlertDialog en lugar de Toast
        if (msg.length() > 100 || msg.contains("\n")) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⚠️ Error")
                .setMessage(msg)
                .setPositiveButton("Entendido", null)
                .show();
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }
}
