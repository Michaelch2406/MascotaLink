package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mjc.mascotalink.security.CredentialManager;
import com.mjc.mascotalink.security.EncryptedPreferencesHelper;
import com.mjc.mascotalink.security.SessionManager;
import com.mjc.mascotalink.PaseosActivity;
import com.mjc.mascotalink.SolicitudesActivity;
import com.mjc.mascotalink.SolicitudDetalleActivity;
import com.mjc.mascotalink.PaseoEnCursoActivity;
import java.util.HashMap;
import java.util.Map;
import com.mjc.mascotalink.ConfirmarPagoActivity;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final long DEBOUNCE_DELAY = 300; // ms
    private static final int MAX_LOGIN_ATTEMPTS = 3;
    private static final long LOCKOUT_DURATION = 60000; // 1 minuto

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private Button btnLogin;
    private CheckBox cbRemember;
    private ProgressBar progressBar;
    private CredentialManager credentialMgr;

    // Memory leak prevention: Referencias a listeners
    private TextWatcher emailWatcher;
    private TextWatcher passwordWatcher;
    private Handler debounceHandler;

    // Rate limiting
    private int loginAttempts = 0;
    private long lastFailedAttemptTime = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Iniciando LoginActivity");

        try {
            mAuth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();
            if (mAuth != null) {
                mAuth.setLanguageCode("es");
            }
            credentialMgr = new CredentialManager(this);
            debounceHandler = new Handler(Looper.getMainLooper());
        } catch (Exception e) {
            Log.e(TAG, "Error configurando Firebase o seguridad: ", e);
            mostrarError("Error de configuración: " + e.getMessage());
            finish();
            return;
        }

        setContentView(R.layout.activity_login);
        bindViews();

        if (checkForSavedSession()) {
            return;
        }

        setupEmailField();
        setupPasswordField();
        setupClickListeners();
        checkAndRequestLocationPermission();
    }

    private boolean checkForSavedSession() {
        try {
            EncryptedPreferencesHelper prefs = EncryptedPreferencesHelper.getInstance(this);
            boolean rememberDevice = prefs.getBoolean("remember_device", false);
            if (!rememberDevice) {
                rememberDevice = prefs.getBoolean("recordar_sesion", false);
            }

            String tempUserId = prefs.getString("user_id", null);
            if (tempUserId == null) {
                tempUserId = prefs.getString("uid", null);
            }
            final String userId = tempUserId;

            String rol = prefs.getString("rol", null);
            String verificacionEstado = prefs.getString("verificacion_estado", null);

            if (rememberDevice && userId != null && rol != null) {
                if (mAuth.getCurrentUser() != null) {
                    Log.d(TAG, "Sesión de Firebase activa y guardada encontrada. Redirigiendo...");
                    updateFcmTokenForCurrentUser();
                    if (redirigirSegunRol(rol, verificacionEstado)) {
                        return true;
                    } else {
                        // Usuario está en revisión o rechazado. Limpiar sesión y mostrar login.
                        limpiarSesion();
                        mAuth.signOut();
                        return false;
                    }
                } else {
                    Log.d(TAG, "Firebase session is null, attempting re-authentication with saved credentials...");
                    String[] creds = credentialMgr.getCredentials();
                    if (creds != null && creds.length == 2 && creds[0] != null && creds[1] != null) {
                        mostrarProgressBar(true);
                        mAuth.signInWithEmailAndPassword(creds[0], creds[1])
                                .addOnCompleteListener(this, task -> {
                                    mostrarProgressBar(false);
                                    if (task.isSuccessful()) {
                                        FirebaseUser user = mAuth.getCurrentUser();
                                        if (user != null && user.getUid().equals(userId)) {
                                            Log.d(TAG, "Re-authentication successful. Redirigiendo...");
                                            updateFcmTokenForCurrentUser();
                                            // Reconectar WebSocket después de re-autenticación
                                            com.mjc.mascotalink.network.SocketManager.getInstance(LoginActivity.this).connect();
                                            db.collection("usuarios").document(user.getUid()).get()
                                                    .addOnSuccessListener(documentSnapshot -> {
                                                        if (documentSnapshot.exists()) {
                                                            String reAuthRol = documentSnapshot.getString("rol");
                                                            if ("PASEADOR".equalsIgnoreCase(reAuthRol)) {
                                                                db.collection("paseadores").document(user.getUid()).get()
                                                                        .addOnSuccessListener(paseadorDoc -> {
                                                                            String reAuthVerificacionEstado = paseadorDoc.getString("verificacion_estado");
                                                                            if (!redirigirSegunRol(reAuthRol, reAuthVerificacionEstado)) {
                                                                                handleAsyncLoginFailure();
                                                                            }
                                                                        }).addOnFailureListener(e -> {
                                                                            Log.e(TAG, "Error re-fetching paseador verification status", e);
                                                                            handleAsyncLoginFailure();
                                                                        });
                                                            } else if ("DUEÑO".equalsIgnoreCase(reAuthRol)) {
                                                                db.collection("duenos").document(user.getUid()).get()
                                                                        .addOnSuccessListener(duenoDoc -> {
                                                                            String reAuthVerificacionEstado = duenoDoc.getString("verificacion_estado");
                                                                            if (!redirigirSegunRol(reAuthRol, reAuthVerificacionEstado)) {
                                                                                handleAsyncLoginFailure();
                                                                            }
                                                                        }).addOnFailureListener(e -> {
                                                                            Log.e(TAG, "Error re-fetching dueno verification status", e);
                                                                            handleAsyncLoginFailure();
                                                                        });
                                                            } else {
                                                                if (!redirigirSegunRol(reAuthRol, null)) {
                                                                    handleAsyncLoginFailure();
                                                                }
                                                            }
                                                        } else {
                                                            Log.w(TAG, "User profile not found after re-authentication. Falling back to normal login.");
                                                            handleAsyncLoginFailure();
                                                            mostrarError("Perfil de usuario no encontrado. Inicia sesión de nuevo.");
                                                        }
                                                    }).addOnFailureListener(e -> {
                                                        Log.e(TAG, "Error re-fetching user role after re-authentication", e);
                                                        handleAsyncLoginFailure();
                                                        mostrarError("Error al cargar perfil. Inicia sesión de nuevo.");
                                                    });
                                        } else {
                                            Log.w(TAG, "Re-authentication successful, but UID mismatch or user null. Clearing credentials.");
                                            handleAsyncLoginFailure();
                                        }
                                    } else {
                                        Log.w(TAG, "Re-authentication failed: " + task.getException().getMessage());
                                        handleAsyncLoginFailure();
                                        mostrarError("Sesión expirada o credenciales no válidas. Por favor, inicia sesión de nuevo.");
                                    }
                                });
                        return true;
                    } else {
                        Log.d(TAG, "Remember device true, but no credentials found in CredentialManager. Falling back to normal login.");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "checkForSavedSession: error accediendo prefs cifradas o durante re-autenticación", e);
            if (credentialMgr != null) {
                credentialMgr.clearCredentials();
            }
        }
        return false;
    }

    private void handleAsyncLoginFailure() {
        limpiarSesion();
        mAuth.signOut();
        // Reiniciar actividad para limpiar estado UI y listeners
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void limpiarSesion() {
        if (credentialMgr != null) credentialMgr.clearCredentials();
        try {
            EncryptedPreferencesHelper prefs = EncryptedPreferencesHelper.getInstance(this);
            prefs.remove("remember_device");
            prefs.remove("recordar_sesion");
            prefs.remove("user_id");
            prefs.remove("uid");
            prefs.remove("rol");
            prefs.remove("verificacion_estado");
            prefs.remove("email");
            prefs.remove("token");
        } catch (Exception e) {
            Log.e(TAG, "limpiarSesion: error limpiando prefs", e);
        }
    }

    private void bindViews() {
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        cbRemember = findViewById(R.id.cb_remember);
        progressBar = findViewById(R.id.pb_loading);
    }

    private void setupClickListeners() {
        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password);
        TextView tvRegisterLink = findViewById(R.id.tv_register_link);

        btnLogin.setOnClickListener(v -> {
            v.setEnabled(false);
            realizarLogin();
        });
        tvForgotPassword.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
        tvRegisterLink.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, SeleccionRolActivity.class)));
    }

    private void setupEmailField() {
        emailWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                // Debouncing: Cancelar validación pendiente
                debounceHandler.removeCallbacksAndMessages(null);
                debounceHandler.postDelayed(() -> {
                    String email = s.toString().trim();
                    if (email.length() > 0 && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        tilEmail.setError("Formato de correo inválido");
                    } else {
                        tilEmail.setError(null);
                    }
                }, DEBOUNCE_DELAY);
            }
        };
        etEmail.addTextChangedListener(emailWatcher);
    }

    private void setupPasswordField() {
        passwordWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                // Debouncing: Cancelar validación pendiente
                debounceHandler.removeCallbacksAndMessages(null);
                debounceHandler.postDelayed(() -> {
                    if (s.length() > 0 && s.length() < 6) {
                        tilPassword.setError("La contraseña debe tener al menos 6 caracteres");
                    } else {
                        tilPassword.setError(null);
                    }
                }, DEBOUNCE_DELAY);
            }
        };
        etPassword.addTextChangedListener(passwordWatcher);
    }

    private void realizarLogin() {
        // Rate limiting: Verificar si está bloqueado
        if (isLoginLockedOut()) {
            long remainingTime = (lastFailedAttemptTime + LOCKOUT_DURATION - System.currentTimeMillis()) / 1000;
            mostrarError("Demasiados intentos fallidos. Espera " + remainingTime + " segundos.");
            btnLogin.setEnabled(true);
            return;
        }

        String email = sanitizeInput(etEmail.getText() != null ? etEmail.getText().toString() : "");
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (!validarCampos(email, password)) {
            btnLogin.setEnabled(true);
            return;
        }

        mostrarProgressBar(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    mostrarProgressBar(false);
                    if (task.isSuccessful()) {
                        // Reset intentos en login exitoso
                        loginAttempts = 0;
                        lastFailedAttemptTime = 0;

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
        Task<com.google.firebase.firestore.DocumentSnapshot> userTask = db.collection("usuarios").document(uid).get();
        Task<com.google.firebase.firestore.DocumentSnapshot> paseadorTask = db.collection("paseadores").document(uid).get();
        Task<com.google.firebase.firestore.DocumentSnapshot> duenoTask = db.collection("duenos").document(uid).get();

        Tasks.whenAllSuccess(userTask, paseadorTask, duenoTask).addOnSuccessListener(results -> {
            com.google.firebase.firestore.DocumentSnapshot userDoc = (com.google.firebase.firestore.DocumentSnapshot) results.get(0);
            com.google.firebase.firestore.DocumentSnapshot paseadorDoc = (com.google.firebase.firestore.DocumentSnapshot) results.get(1);
            com.google.firebase.firestore.DocumentSnapshot duenoDoc = (com.google.firebase.firestore.DocumentSnapshot) results.get(2);

            if (!userDoc.exists()) {
                mostrarError("Usuario no encontrado en la base de datos");
                btnLogin.setEnabled(true);
                return;
            }

            String rol = userDoc.getString("rol");
            if (rol == null) {
                mostrarError("Rol no definido para este usuario.");
                btnLogin.setEnabled(true);
                return;
            }

            updateFcmTokenForCurrentUser();
            com.mjc.mascotalink.network.SocketManager.getInstance(LoginActivity.this).connect();

            String verificacionEstado = null;

            if ("PASEADOR".equalsIgnoreCase(rol)) {
                if (paseadorDoc.exists()) {
                    verificacionEstado = paseadorDoc.getString("verificacion_estado");
                } else {
                    mostrarError("Error de consistencia de datos del paseador.");
                    btnLogin.setEnabled(true);
                    return;
                }
            } else if ("DUEÑO".equalsIgnoreCase(rol)) {
                if (duenoDoc.exists()) {
                    verificacionEstado = duenoDoc.getString("verificacion_estado");
                } else {
                    mostrarError("Error de consistencia de datos del dueño.");
                    btnLogin.setEnabled(true);
                    return;
                }
            }

            handleSessionAndRedirect(uid, rol, verificacionEstado);

        }).addOnFailureListener(e -> {
            mostrarError("Error al verificar datos de usuario: " + e.getMessage());
            btnLogin.setEnabled(true);
        });
    }

    private void handleSessionAndRedirect(String uid, String rol, String verificacionEstado) {
        SessionManager sessionManager = new SessionManager(this);
        sessionManager.createSession(uid);
        com.mjc.mascotalink.notifications.FcmTokenSyncWorker.enqueueNow(this);
        com.mjc.mascotalink.util.UnreadBadgeManager.start(uid);

        if (cbRemember.isChecked()) {
            guardarPreferenciasLogin(uid, rol, verificacionEstado);
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null && currentUser.getEmail() != null) {
                String userEmail = currentUser.getEmail();
                String userPassword = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
                credentialMgr.saveCredentials(userEmail, userPassword);
            }
        } else {
            limpiarSesion();
        }

        com.mjc.mascotalink.util.BottomNavManager.saveUserRole(this, rol);
        redirigirSegunRol(rol, verificacionEstado);
    }

    private boolean redirigirSegunRol(String rol, String estadoVerificacion) {
        if (rol == null) {
            mostrarError("Rol de usuario no válido");
            return false;
        }

        Intent intent = null;
        String normalizedRol = rol.toUpperCase();

        String clickAction = getIntent().getStringExtra("click_action");

        if (clickAction != null && "APROBADO".equals(estadoVerificacion)) {
            switch (clickAction) {
                case "OPEN_WALKS_ACTIVITY":
                    intent = new Intent(this, PaseosActivity.class);
                    break;
                case "OPEN_REQUESTS_ACTIVITY":
                    intent = new Intent(this, SolicitudesActivity.class);
                    break;
                case "OPEN_REQUEST_DETAILS":
                    intent = new Intent(this, SolicitudDetalleActivity.class);
                    break;
                case "OPEN_PAYMENT_CONFIRMATION":
                    intent = new Intent(this, ConfirmarPagoActivity.class);
                    if (getIntent().hasExtra("reservaId")) {
                        intent.putExtra("reserva_id", getIntent().getStringExtra("reservaId"));
                    }
                    break;
                case "OPEN_CURRENT_WALK_ACTIVITY":
                    intent = new Intent(this, PaseoEnCursoActivity.class);
                    break;
            }

            if (intent != null) {
                if (getIntent().getExtras() != null) {
                    intent.putExtras(getIntent().getExtras());
                }
            }
        }

        if (intent == null) {
            // Lógica unificada: Todos los roles aprobados van al Home (MainActivity)
            if ("APROBADO".equals(estadoVerificacion)) {
                intent = new Intent(this, MainActivity.class);
            } else if ("PENDIENTE".equals(estadoVerificacion)) {
                mostrarMensaje("Tu perfil está en revisión. Te notificaremos cuando sea aprobado.");
            } else if ("RECHAZADO".equals(estadoVerificacion)) {
                mostrarMensaje("Tu perfil fue rechazado o está inactivo. Contacta a soporte.");
            } else if ("ADMIN".equals(normalizedRol)) {
                intent = new Intent(this, MainActivity.class);
            } else {
                // Caso fallback para usuarios legacy sin campo verificacion_estado
                // Asumimos aprobado si no hay estado explícito negativo
                Log.w(TAG, "Estado verificación nulo para rol: " + normalizedRol + ". Asumiendo aprobado por compatibilidad.");
                intent = new Intent(this, MainActivity.class);
            }
        }

        if (intent != null) {
            startActivity(intent);
            finish();
            return true;
        }
        return false;
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
        loginAttempts++;
        lastFailedAttemptTime = System.currentTimeMillis();

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
        btnLogin.setEnabled(true);
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

    private void updateFcmTokenForCurrentUser() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                            return;
                        }
                        String token = task.getResult();

                        Map<String, Object> tokenMap = new HashMap<>();
                        tokenMap.put("fcmToken", token);

                        db.collection("usuarios").document(currentUser.getUid())
                                .update(tokenMap)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token successfully updated"))
                                .addOnFailureListener(e -> Log.e(TAG, "Error updating FCM token", e));
                    });
        } else {
            Log.d(TAG, "No user logged in, cannot save FCM token.");
        }
    }

    private void checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "ACCESS_FINE_LOCATION permission already granted.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "ACCESS_FINE_LOCATION permission granted by user.");
            } else {
                Log.w(TAG, "ACCESS_FINE_LOCATION permission denied by user.");
                mostrarMensaje("Permiso de ubicación denegado. La detección automática de red para emuladores podría no funcionar.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (emailWatcher != null && etEmail != null) {
            etEmail.removeTextChangedListener(emailWatcher);
        }
        if (passwordWatcher != null && etPassword != null) {
            etPassword.removeTextChangedListener(passwordWatcher);
        }
        if (debounceHandler != null) {
            debounceHandler.removeCallbacksAndMessages(null);
        }
    }

    private String sanitizeInput(String input) {
        if (input == null) return "";
        return input.trim()
                .replaceAll("[<>\"']", "")
                .substring(0, Math.min(input.trim().length(), 254));
    }

    private boolean isLoginLockedOut() {
        if (loginAttempts < MAX_LOGIN_ATTEMPTS) {
            return false;
        }
        long elapsedTime = System.currentTimeMillis() - lastFailedAttemptTime;
        if (elapsedTime >= LOCKOUT_DURATION) {
            loginAttempts = 0;
            lastFailedAttemptTime = 0;
            return false;
        }
        return true;
    }
}
