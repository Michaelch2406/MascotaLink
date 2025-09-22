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
        
        // Configuración condicional de Firebase (Emuladores vs Producción)
        try {
            if (USE_FIREBASE_EMULATORS) {
                // Ajusta el host si usas dispositivo físico o un emulador diferente
                String host = "192.168.0.147"; // host del PC desde el emulador estándar de Android
                Log.d(TAG, "Usando Emuladores de Firebase en host: " + host);

                FirebaseAuth authInstance = FirebaseAuth.getInstance();
                authInstance.useEmulator(host, 9099);

                FirebaseFirestore firestoreInstance = FirebaseFirestore.getInstance();
                firestoreInstance.useEmulator(host, 8080);

                FirebaseStorage.getInstance().useEmulator(host, 9199);

                mAuth = authInstance;
                db = firestoreInstance;
                Log.d(TAG, "Emuladores configurados (Auth:9099, Firestore:8080)");
            } else {
                // Producción (proyecto real de Firebase definido por google-services.json)
                mAuth = FirebaseAuth.getInstance();
                db = FirebaseFirestore.getInstance();
                Log.d(TAG, "Usando Firebase en la nube (producción)");
            }
            // Opcional: fija idioma para evitar X-Firebase-Locale null y mostrar mensajes en español
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
                if (!isValidEmail(s.toString()) && s.length() > 0) {
                    tilEmail.setError("Formato de correo inválido");
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
                if (s.length() > 0 && s.length() < 6) {
                    tilPassword.setError("La contraseña debe tener al menos 6 caracteres");
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
        db.collection("usuarios").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String rol = documentSnapshot.getString("rol");
                        String verificacionEstado = obtenerEstadoVerificacion(documentSnapshot, rol);

                        if (cbRemember.isChecked()) {
                            guardarPreferenciasLogin(uid, rol);
                        }
                        redirigirSegunRol(rol, verificacionEstado);
                    } else {
                        mostrarError("Usuario no encontrado en la base de datos");
                    }
                })
                .addOnFailureListener(e -> mostrarError("Error al verificar datos: " + e.getMessage()));
    }

    private String obtenerEstadoVerificacion(DocumentSnapshot doc, String rol) {
        if (doc == null) return "";
        // Campos esperados: estado_verificacion para PASEADOR
        if ("PASEADOR".equals(rol)) {
            String estado = doc.getString("estado_verificacion");
            return estado != null ? estado : "PENDIENTE";
        }
        return ""; // otros roles no requieren estado
    }

    private void redirigirSegunRol(String rol, String estadoVerificacion) {
        // Para evitar dependencias con Activities no creadas, dejamos mensajes y ejemplo de navegación
        if (rol == null) {
            mostrarError("Rol de usuario no válido");
            return;
        }
        switch (rol) {
            case "PASEADOR":
                if ("PENDIENTE".equals(estadoVerificacion)) {
                    mostrarMensaje("Tu perfil está en revisión. Te notificaremos cuando sea aprobado.");
                    // startActivity(new Intent(this, PaseadorPendienteActivity.class));
                } else if ("APROBADO".equals(estadoVerificacion)) {
                    mostrarMensaje("Bienvenido(a), paseador");
                    // startActivity(new Intent(this, PaseadorMainActivity.class));
                } else {
                    mostrarMensaje("Tu perfil fue rechazado. Contacta soporte para más información.");
                    // startActivity(new Intent(this, PaseadorRechazadoActivity.class));
                    return;
                }
                break;
            case "DUENO":
                mostrarMensaje("Bienvenido(a), dueño");
                // startActivity(new Intent(this, DuenoMainActivity.class));
                break;
            case "ADMIN":
                mostrarMensaje("Bienvenido(a), admin");
                // startActivity(new Intent(this, AdminDashboardActivity.class));
                break;
            default:
                mostrarError("Rol de usuario no válido");
                return;
        }
        // finish(); // Descomenta cuando navegues a otra Activity real
    }

    private boolean validarCampos(String email, String password) {
        boolean esValido = true;

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("El correo es requerido");
            esValido = false;
        } else if (!isValidEmail(email)) {
            tilEmail.setError("Formato de correo inválido");
            esValido = false;
        } else {
            tilEmail.setError(null);
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("La contraseña es requerida");
            esValido = false;
        } else if (password.length() < 6) {
            tilPassword.setError("La contraseña debe tener al menos 6 caracteres");
            esValido = false;
        } else {
            tilPassword.setError(null);
        }

        return esValido;
    }

    private void manejarErrorLogin(Exception exception) {
        String mensajeError = "Error desconocido";
        
        Log.e(TAG, "Error detallado en login: ", exception);
        
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            mensajeError = "Credenciales inválidas. Verifica tu correo y contraseña.";
            Log.e(TAG, "Error: Credenciales inválidas");
        } else if (exception instanceof FirebaseAuthInvalidUserException) {
            mensajeError = "No existe una cuenta con este correo electrónico.";
            Log.e(TAG, "Error: Usuario no existe");
        } else if (exception instanceof FirebaseNetworkException) {
            mensajeError = "Error de conexión. Verifica tu internet y la configuración del emulador.";
            Log.e(TAG, "Error de red: " + exception.getMessage());
        } else if (exception != null && exception.getMessage() != null) {
            mensajeError = exception.getMessage();
            Log.e(TAG, "Error con mensaje: " + exception.getMessage());
            
            // Verificar si es el error de cleartext traffic
            if (exception.getMessage().contains("Cleartext HTTP traffic") || 
                exception.getMessage().contains("not permitted")) {
                mensajeError = "Error de configuración de red. Verifica la configuración del emulador Firebase.";
                Log.e(TAG, "Error de cleartext traffic detectado");
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
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
