package com.mjc.mascotalink;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mjc.mascotalink.modelo.ConfirmPasswordResetRequest;
import com.mjc.mascotalink.modelo.ConfirmPasswordResetResponse;
import com.mjc.mascotalink.modelo.ValidateTokenResponse;
import com.mjc.mascotalink.network.APIClient;
import com.mjc.mascotalink.network.AuthService;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ResetPasswordActivity";
    private TextInputEditText etPassword, etConfirmPassword;
    private Button btnResetPassword;
    private ProgressBar progressBar;
    private AuthService authService;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        // Initialize views with new IDs
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnResetPassword = findViewById(R.id.btn_reset_password);
        progressBar = findViewById(R.id.progressBar);

        // Initialize service
        authService = APIClient.getAuthService();

        // Get token from deep link
        Uri data = getIntent().getData();
        if (data != null) {
            token = data.getQueryParameter("token");
            if (token == null || token.isEmpty()) {
                Log.e(TAG, "El token del deep link es nulo o vacío.");
                mostrarError("Token inválido o no encontrado.", true);
                return;
            }
            // Validate token with backend
            validarToken();
        } else {
            Log.e(TAG, "No se recibieron datos en el Intent (deep link). ");
            mostrarError("No se recibió el enlace de recuperación.", true);
        }

        // Set listeners
        btnResetPassword.setOnClickListener(v -> confirmarCambioDePassword());
    }

    private void validarToken() {
        mostrarCargando(true);

        authService.validateToken(token).enqueue(new Callback<ValidateTokenResponse>() {
            @Override
            public void onResponse(Call<ValidateTokenResponse> call, Response<ValidateTokenResponse> response) {
                mostrarCargando(false);
                if (response.isSuccessful()) {
                    ValidateTokenResponse respuesta = response.body();
                    if (respuesta != null && respuesta.isValid()) {
                        Log.i(TAG, "Validación de token exitosa.");
                        // Token válido, el usuario puede proceder.
                    } else {
                        String mensaje = respuesta != null ? respuesta.getMessage() : "Token expirado o inválido";
                        mostrarError(mensaje, true);
                    }
                } else {
                     mostrarError("Error al validar el token: " + response.code(), true);
                }
            }

            @Override
            public void onFailure(Call<ValidateTokenResponse> call, Throwable t) {
                mostrarCargando(false);
                String msg = (t instanceof IOException) ? "Error de conexión." : "Error inesperado: " + t.getMessage();
                mostrarError(msg, true);
            }
        });
    }

    private void confirmarCambioDePassword() {
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        // Validate fields
        if (password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Las contraseñas no coinciden.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 8) {
            Toast.makeText(this, "La contraseña debe tener al menos 8 caracteres.", Toast.LENGTH_SHORT).show();
            return;
        }

        mostrarCargando(true);

        // Create request
        ConfirmPasswordResetRequest request = new ConfirmPasswordResetRequest(token, password, confirmPassword);

        // Call backend
        authService.confirmPasswordReset(request).enqueue(new Callback<ConfirmPasswordResetResponse>() {
            @Override
            public void onResponse(Call<ConfirmPasswordResetResponse> call, Response<ConfirmPasswordResetResponse> response) {
                mostrarCargando(false);
                if (response.isSuccessful()) {
                    ConfirmPasswordResetResponse respuesta = response.body();
                    if (respuesta != null && respuesta.isSuccess()) {
                        mostrarExito();
                    } else {
                        String mensaje = respuesta != null ? respuesta.getMessage() : "Error desconocido al actualizar.";
                        mostrarError(mensaje, false);
                    }
                } else {
                    mostrarError("Error: " + response.code(), false);
                }
            }

            @Override
            public void onFailure(Call<ConfirmPasswordResetResponse> call, Throwable t) {
                mostrarCargando(false);
                String msg = (t instanceof IOException) ? "Error de conexión." : "Error inesperado: " + t.getMessage();
                mostrarError(msg, false);
            }
        });
    }

    private void mostrarCargando(boolean mostrar) {
        progressBar.setVisibility(mostrar ? View.VISIBLE : View.GONE);
        btnResetPassword.setEnabled(!mostrar);
        btnResetPassword.setAlpha(mostrar ? 0.5f : 1.0f);
        etPassword.setEnabled(!mostrar);
        etConfirmPassword.setEnabled(!mostrar);
    }

    private void mostrarError(String mensaje, boolean finalizar) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(mensaje)
                .setPositiveButton("Aceptar", (dialog, which) -> {
                    if (finalizar) finish();
                })
                .setCancelable(!finalizar)
                .show();
    }

    private void mostrarExito() {
        new AlertDialog.Builder(this)
                .setTitle("¡Contraseña Actualizada!")
                .setMessage("Tu contraseña ha sido restablecida correctamente. Ahora puedes iniciar sesión con tu nueva clave.")
                .setPositiveButton("Ir al Login", (dialog, which) -> {
                    Intent intent = new Intent(ResetPasswordActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}