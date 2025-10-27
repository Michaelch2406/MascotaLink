
package com.mjc.mascotalink;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.mjc.mascotalink.network.APIClient;
import com.mjc.mascotalink.network.AuthService;
import com.mjc.mascotalink.modelo.ConfirmPasswordResetRequest;
import com.mjc.mascotalink.modelo.ConfirmPasswordResetResponse;
import com.mjc.mascotalink.modelo.ValidateTokenResponse;

import android.util.Log;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ResetPasswordActivity";
    private EditText editTextPassword, editTextConfirmPassword;
    private Button buttonConfirm;
    private ProgressBar progressBar;
    private TextView textViewMessage;
    private AuthService authService;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        // Initialize views
        editTextPassword = findViewById(R.id.edit_text_password);
        editTextConfirmPassword = findViewById(R.id.edit_text_confirm_password);
        buttonConfirm = findViewById(R.id.button_confirm);
        progressBar = findViewById(R.id.progress_bar);
        textViewMessage = findViewById(R.id.text_view_message);

        // Initialize service
        authService = APIClient.getAuthService();

        // Get token from deep link
        Uri data = getIntent().getData();
        if (data != null) {
            token = data.getQueryParameter("token");
            if (token == null || token.isEmpty()) {
                Log.e(TAG, "El token del deep link es nulo o vacío.");
                Toast.makeText(this, "Token inválido o no encontrado.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            // Validate token with backend
            validarToken();
        } else {
            Log.e(TAG, "No se recibieron datos en el Intent (deep link). ");
            Toast.makeText(this, "No se recibió el enlace de recuperación.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Set listeners
        buttonConfirm.setOnClickListener(v -> confirmarCambioDePassword());
    }

    private void validarToken() {
        mostrarCargando(true, "Validando token...");

        authService.validateToken(token).enqueue(new Callback<ValidateTokenResponse>() {
            @Override
            public void onResponse(Call<ValidateTokenResponse> call, Response<ValidateTokenResponse> response) {
                mostrarCargando(false, "");
                Log.d(TAG, "validarToken onResponse: Code: " + response.code());

                if (response.isSuccessful()) {
                    ValidateTokenResponse respuesta = response.body();
                    if (respuesta != null && respuesta.isValid()) {
                        Log.i(TAG, "Validación de token exitosa.");
                        textViewMessage.setText("Token válido. Por favor, ingresa tu nueva contraseña.");
                    } else {
                        String mensaje = respuesta != null ? respuesta.getMessage() : "Token expirado o inválido";
                        Log.w(TAG, "El backend reporta que el token no es válido: " + mensaje);
                        Toast.makeText(ResetPasswordActivity.this, mensaje, Toast.LENGTH_LONG).show();
                        finish();
                    }
                } else {
                     Log.e(TAG, "Error en la respuesta HTTP de validarToken: " + response.code());
                     Toast.makeText(ResetPasswordActivity.this, "Error al validar el token: " + response.code(), Toast.LENGTH_LONG).show();
                     finish();
                }
            }

            @Override
            public void onFailure(Call<ValidateTokenResponse> call, Throwable t) {
                mostrarCargando(false, "");
                Log.e(TAG, "validarToken onFailure: Error de red o conexión", t);

                if (t instanceof IOException) {
                    Toast.makeText(ResetPasswordActivity.this, "Error de conexión. Verifica tu acceso a internet.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ResetPasswordActivity.this, "Error inesperado al validar: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
                finish();
            }
        });
    }

    private void confirmarCambioDePassword() {
        String password = editTextPassword.getText().toString();
        String confirmPassword = editTextConfirmPassword.getText().toString();

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

        mostrarCargando(true, "Actualizando contraseña...");

        // Create request
        ConfirmPasswordResetRequest request = new ConfirmPasswordResetRequest(token, password, confirmPassword);

        // Call backend
        authService.confirmPasswordReset(request).enqueue(new Callback<ConfirmPasswordResetResponse>() {
            @Override
            public void onResponse(Call<ConfirmPasswordResetResponse> call, Response<ConfirmPasswordResetResponse> response) {
                mostrarCargando(false, "");
                Log.d(TAG, "confirmarCambio onResponse: Code: " + response.code());

                if (response.isSuccessful()) {
                    ConfirmPasswordResetResponse respuesta = response.body();
                    if (respuesta != null && respuesta.isSuccess()) {
                        Log.i(TAG, "Contraseña actualizada exitosamente.");
                        Toast.makeText(ResetPasswordActivity.this, "Contraseña actualizada correctamente.", Toast.LENGTH_LONG).show();

                        // Go to LoginActivity
                        Intent intent = new Intent(ResetPasswordActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        String mensaje = respuesta != null ? respuesta.getMessage() : "Error desconocido al actualizar.";
                        Log.w(TAG, "El backend no pudo actualizar la contraseña: " + mensaje);
                        Toast.makeText(ResetPasswordActivity.this, mensaje, Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.e(TAG, "Error en la respuesta HTTP de confirmarCambio: " + response.code());
                    Toast.makeText(ResetPasswordActivity.this, "Error: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ConfirmPasswordResetResponse> call, Throwable t) {
                mostrarCargando(false, "");
                Log.e(TAG, "confirmarCambio onFailure: Error de red o conexión", t);

                if (t instanceof IOException) {
                    Toast.makeText(ResetPasswordActivity.this, "Error de conexión. Verifica tu acceso a internet.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ResetPasswordActivity.this, "Error inesperado al actualizar: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void mostrarCargando(boolean mostrar, String message) {
        if (mostrar) {
            progressBar.setVisibility(View.VISIBLE);
            textViewMessage.setText(message);
        } else {
            progressBar.setVisibility(View.GONE);
        }
        buttonConfirm.setEnabled(!mostrar);
        buttonConfirm.setAlpha(mostrar ? 0.5f : 1.0f);
        editTextPassword.setEnabled(!mostrar);
        editTextConfirmPassword.setEnabled(!mostrar);
    }
}
