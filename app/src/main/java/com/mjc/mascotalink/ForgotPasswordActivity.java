package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mjc.mascotalink.modelo.RequestPasswordResetRequest;
import com.mjc.mascotalink.modelo.RequestPasswordResetResponse;
import com.mjc.mascotalink.network.APIClient;
import com.mjc.mascotalink.network.AuthService;

import android.util.Log;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPasswordActivity";
    private TextInputEditText etEmail;
    private Button btnResetPassword;
    private Button btnBackLogin;
    private ProgressBar progressBar;
    private AuthService authService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize service
        authService = APIClient.getAuthService();

        // Initialize views
        etEmail = findViewById(R.id.et_email);
        progressBar = findViewById(R.id.progressBar);
        btnResetPassword = findViewById(R.id.btn_reset_password);
        btnBackLogin = findViewById(R.id.btn_back_login);

        // Configurar listeners
        btnResetPassword.setOnClickListener(v -> solicitarRecuperacion());
        
        btnBackLogin.setOnClickListener(v -> {
            Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void solicitarRecuperacion() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        // Validar email
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("El correo es requerido");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Formato de correo inválido");
            return;
        }
        etEmail.setError(null);

        // Mostrar indicador de carga
        mostrarLoading(true);

        // Crear solicitud
        RequestPasswordResetRequest request = new RequestPasswordResetRequest(email);

        // Realizar llamada al backend
        authService.requestPasswordReset(request).enqueue(new Callback<RequestPasswordResetResponse>() {
            @Override
            public void onResponse(Call<RequestPasswordResetResponse> call, Response<RequestPasswordResetResponse> response) {
                mostrarLoading(false);
                Log.d(TAG, "onResponse: Code: " + response.code());

                if (response.isSuccessful()) {
                    RequestPasswordResetResponse respuesta = response.body();
                    if (respuesta != null && respuesta.isSuccess()) {
                        Log.i(TAG, "Solicitud de recuperación exitosa para el email: " + email);
                        
                        // Ir a pantalla de verificación
                        Intent intent = new Intent(ForgotPasswordActivity.this, VerifyEmailActivity.class);
                        intent.putExtra("email", email);
                        startActivity(intent);
                        finish();
                    } else {
                        String mensaje = respuesta != null ? respuesta.getMessage() : "Error desconocido del servidor";
                        Log.w(TAG, "La respuesta del servidor no fue exitosa: " + mensaje);
                        mostrarError(mensaje);
                    }
                } else {
                    // Error HTTP (e.g., 404, 500)
                    Log.e(TAG, "Error en la respuesta HTTP: " + response.code());
                    mostrarError("Error en la respuesta del servidor: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<RequestPasswordResetResponse> call, Throwable t) {
                mostrarLoading(false);
                Log.e(TAG, "onFailure: Error de red o conexión", t);

                if (t instanceof IOException) {
                    mostrarError("Error de conexión. Verifica tu acceso a internet.");
                } else {
                    mostrarError("Error inesperado: " + t.getMessage());
                }
            }
        });
    }

    private void mostrarLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (btnResetPassword != null) {
            btnResetPassword.setEnabled(!show);
            btnResetPassword.setAlpha(show ? 0.5f : 1.0f);
        }
        if (etEmail != null) {
            etEmail.setEnabled(!show);
        }
    }
    
    private void mostrarError(String mensaje) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(mensaje)
                .setPositiveButton("Aceptar", null)
                .show();
    }
}