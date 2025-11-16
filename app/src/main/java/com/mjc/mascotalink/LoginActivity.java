package com.mjc.mascotalink;

import android.content.Intent;
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
import android.Manifest; // Added for Manifest.permission
import android.content.pm.PackageManager; // Added for PackageManager
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat; // Added for ActivityCompat
import androidx.core.content.ContextCompat; // Added for ContextCompat

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.security.EncryptedPreferencesHelper;
import com.mjc.mascotalink.security.SessionManager;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001; // Added for permission request

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private CheckBox cbRemember;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (checkForSavedSession()) {
            return; // Auto-login in progress
        }

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
        setupEmailField();
        setupPasswordField();
        setupClickListeners();
        checkAndRequestLocationPermission(); // Call the new permission check method
    }

    private void checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Permission has already been granted
            Log.d(TAG, "ACCESS_FINE_LOCATION permission already granted.");
        }
    }

    private boolean checkForSavedSession() {
        try {
            EncryptedPreferencesHelper prefs = EncryptedPreferencesHelper.getInstance(this);
            boolean rememberDevice = prefs.getBoolean("remember_device", false);
            if (!rememberDevice) {
                rememberDevice = prefs.getBoolean("recordar_sesion", false);
            }

            String userId = prefs.getString("user_id", null);
            if (userId == null) {
                userId = prefs.getString("uid", null);
            }

            String rol = prefs.getString("rol", null);
            String verificacionEstado = prefs.getString("verificacion_estado", null);

            if (rememberDevice && userId != null && rol != null && FirebaseAuth.getInstance().getCurrentUser() != null) {
                Log.d(TAG, "Sesión guardada encontrada. Redirigiendo...");
                redirigirSegunRol(rol, verificacionEstado);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "checkForSavedSession: error accediendo prefs cifradas", e);
        }
        return false;
    }

    private void bindViews() {
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        cbRemember = findViewById(R.id.cb_remember);
        progressBar = findViewById(R.id.pb_loading);
    }

    private void setupClickListeners() {
        Button btnLogin = findViewById(R.id.btn_login);
        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password);
        TextView tvRegisterLink = findViewById(R.id.tv_register_link);

        btnLogin.setOnClickListener(v -> realizarLogin());
        tvForgotPassword.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
        tvRegisterLink.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, SeleccionRolActivity.class)));
    }

    private void setupEmailField() {
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 0 && !Patterns.EMAIL_ADDRESS.matcher(s.toString().trim()).matches()) {
                    tilEmail.setError("Formato de correo inválido");
                } else {
                    tilEmail.setError(null);
                }
            }
        });
    }

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

    private void realizarLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (!validarCampos(email, password)) {
            return;
        }

        mostrarProgressBar(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    mostrarProgressBar(false);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            verificarRolYRedirigir(user.getUid());
                        }
                    } else {
                        manejarErrorLogin(task.getException());
                    }
                });
    }

    private void verificarRolYRedirigir(String uid) {
        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String rol = documentSnapshot.getString("rol");
                        if (rol == null) {
                            mostrarError("Rol no definido para este usuario.");
                            return;
                        }

                        if ("PASEADOR".equalsIgnoreCase(rol)) {
                            db.collection("paseadores").document(uid).get()
                                    .addOnSuccessListener(paseadorDoc -> {
                                        if (paseadorDoc.exists()) {
                                            String verificacionEstado = paseadorDoc.getString("verificacion_estado");
                                            handleSessionAndRedirect(uid, rol, verificacionEstado);
                                        } else {
                                            mostrarError("Error de consistencia de datos del paseador.");
                                        }
                                    })
                                    .addOnFailureListener(e -> mostrarError("Error al verificar estado del paseador: " + e.getMessage()));
                        } else if ("DUEÑO".equalsIgnoreCase(rol)) {
                            db.collection("duenos").document(uid).get()
                                    .addOnSuccessListener(duenoDoc -> {
                                        if (duenoDoc.exists()) {
                                            String verificacionEstado = duenoDoc.getString("verificacion_estado");
                                            handleSessionAndRedirect(uid, rol, verificacionEstado);
                                        } else {
                                            mostrarError("Error de consistencia de datos del dueño.");
                                        }
                                    })
                                    .addOnFailureListener(e -> mostrarError("Error al verificar estado del dueño: " + e.getMessage()));
                        } else { // Otros roles como ADMIN
                            handleSessionAndRedirect(uid, rol, null);
                        }
                    } else {
                        mostrarError("Usuario no encontrado en la base de datos");
                    }
                })
                .addOnFailureListener(e -> mostrarError("Error al verificar datos de usuario: " + e.getMessage()));
    }

    private void handleSessionAndRedirect(String uid, String rol, String verificacionEstado) {
        SessionManager sessionManager = new SessionManager(this);
        sessionManager.createSession(uid);
        if (cbRemember.isChecked()) {
            guardarPreferenciasLogin(uid, rol, verificacionEstado);
        } else {
            try {
                EncryptedPreferencesHelper prefs = EncryptedPreferencesHelper.getInstance(this);
                if (prefs != null) {
                    prefs.remove("remember_device");
                    prefs.remove("recordar_sesion");
                    prefs.remove("user_id");
                    prefs.remove("uid");
                    prefs.remove("rol");
                    prefs.remove("verificacion_estado");
                    prefs.remove("email");
                    prefs.remove("token");
                }
            } catch (Exception e) {
                Log.e(TAG, "handleSessionAndRedirect: error limpiando prefs cifradas", e);
            }
        }
        redirigirSegunRol(rol, verificacionEstado);
    }

    private void redirigirSegunRol(String rol, String estadoVerificacion) {
        if (rol == null) {
            mostrarError("Rol de usuario no válido");
            return;
        }

        Intent intent = null;
        String normalizedRol = rol.toUpperCase(); // Normalizar a mayúsculas

        switch (normalizedRol) {
            case "PASEADOR":
                if ("APROBADO".equals(estadoVerificacion)) {
                    intent = new Intent(this, PerfilPaseadorActivity.class);
                } else if ("PENDIENTE".equals(estadoVerificacion)){
                    mostrarMensaje("Tu perfil está en revisión. Te notificaremos cuando sea aprobado.");
                } else {
                    mostrarMensaje("Tu perfil fue rechazado o está inactivo. Contacta a soporte.");
                }
                break;
            case "DUEÑO":
                if ("APROBADO".equals(estadoVerificacion)) {
                    intent = new Intent(this, PerfilDuenoActivity.class);
                } else if ("PENDIENTE".equals(estadoVerificacion)) {
                    mostrarMensaje("Tu perfil de dueño está en revisión. Te notificaremos cuando sea aprobado.");
                } else {
                    mostrarMensaje("Tu perfil de dueño fue rechazado o está inactivo. Contacta a soporte.");
                }
                break;
            case "ADMIN":
                intent = new Intent(this, MainActivity.class);
                break;
            default:
                mostrarError("Rol de usuario desconocido: '" + rol + "'"); // Mostrar el rol problemático
                break;
        }

        if (intent != null) {
            startActivity(intent);
            finish();
        }
    }

    private void guardarPreferenciasLogin(String uid, String rol, String verificacionEstado) {
        try {
            EncryptedPreferencesHelper prefs = EncryptedPreferencesHelper.getInstance(this);
            prefs.putBoolean("remember_device", true);
            prefs.putBoolean("recordar_sesion", true);
            prefs.putString("user_id", uid);
            prefs.putString("uid", uid);
            prefs.putString("rol", rol);
            if (verificacionEstado != null) {
                prefs.putString("verificacion_estado", verificacionEstado);
            }

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                String email = currentUser.getEmail();
                if (email != null) {
                    prefs.putString("email", email);
                }
                currentUser.getIdToken(false)
                        .addOnSuccessListener(tokenResult -> {
                            String token = tokenResult.getToken();
                            if (token != null) {
                                prefs.putString("token", token);
                            }
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "guardarPreferenciasLogin: no se pudo guardar token", e));
            }
        } catch (Exception e) {
            Log.e(TAG, "guardarPreferenciasLogin: error guardando prefs cifradas", e);
        }
    }

    private boolean validarCampos(String email, String password) {
        boolean esValido = true;
        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("El correo es obligatorio");
            esValido = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Formato de correo inválido");
            esValido = false;
        } else {
            tilEmail.setError(null);
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("La contraseña es obligatoria");
            esValido = false;
        } else if (password.length() < 6) {
            tilPassword.setError("La contraseña debe tener al menos 6 caracteres");
            esValido = false;
        } else {
            tilPassword.setError(null);
        }
        if (!esValido) {
            mostrarError("Por favor corrige los errores antes de continuar");
        }
        return esValido;
    }

    private void manejarErrorLogin(Exception exception) {
        String mensajeError;
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            mensajeError = "Credenciales inválidas. Verifica tu correo y contraseña.";
            tilEmail.setError("Correo o contraseña incorrectos");
            tilPassword.setError("Correo o contraseña incorrectos");
        } else if (exception instanceof FirebaseAuthInvalidUserException) {
            mensajeError = "No existe una cuenta con este correo electrónico.";
            tilEmail.setError("Este correo no está registrado");
        } else if (exception instanceof FirebaseNetworkException) {
            mensajeError = "Error de conexión. Verifica tu internet.";
        } else if (exception != null && exception.getMessage() != null && exception.getMessage().contains("too-many-requests")) {
            mensajeError = "Demasiados intentos fallidos. Espera unos minutos.";
        } else {
            mensajeError = "Error desconocido. Inténtalo de nuevo.";
            Log.e(TAG, "Error de login no manejado", exception);
        }
        mostrarError(mensajeError);
    }

    private void mostrarProgressBar(boolean mostrar) {
        progressBar.setVisibility(mostrar ? View.VISIBLE : View.GONE);
    }

    private void mostrarMensaje(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void mostrarError(String msg) {
        if (msg.length() > 100) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(msg)
                .setPositiveButton("Entendido", null)
                .show();
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "ACCESS_FINE_LOCATION permission granted by user.");
                // Optionally, you could re-check the SSID here if needed,
                // but MyApplication.onCreate() already handles it on app startup.
            } else {
                Log.w(TAG, "ACCESS_FINE_LOCATION permission denied by user.");
                mostrarMensaje("Permiso de ubicación denegado. La detección automática de red para emuladores podría no funcionar.");
            }
        }
    }
}
