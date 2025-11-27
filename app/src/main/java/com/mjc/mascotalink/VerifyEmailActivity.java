package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast; // Import Toast

import androidx.appcompat.app.AppCompatActivity;

public class VerifyEmailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email);

        String email = getIntent().getStringExtra("email");

        TextView tvDescription = findViewById(R.id.tv_description);
        Button btnCheckVerification = findViewById(R.id.btn_check_verification);
        Button btnResendEmail = findViewById(R.id.btn_resend_email);
        Button btnBackToLogin = findViewById(R.id.btn_back_login);

        if (email != null && !email.isEmpty()) {
            tvDescription.setText("Hemos enviado un enlace de recuperación a:\n" + email + "\n\nPor favor, revisa tu bandeja de entrada.");
        }

        // Botón principal: Simplemente cierra o lleva al login, asumiendo que el usuario va a su app de correo
        btnCheckVerification.setOnClickListener(v -> {
            // En un flujo de recuperación de contraseña, el usuario hace click en el link del correo
            // y eso abre la app (Deep Link) o una web.
            // Este botón es solo para salir de esta pantalla.
            navigateToLogin();
        });

        // Botón reenviar: Como usamos backend propio, lo ideal sería llamar a tu API.
        // Por ahora, para no romper nada sin tener el endpoint a mano, redirigimos atrás.
        btnResendEmail.setOnClickListener(v -> {
            Toast.makeText(this, "Por favor, solicita el correo nuevamente.", Toast.LENGTH_LONG).show();
            finish(); // Vuelve a la pantalla anterior para pedirlo de nuevo
        });

        btnBackToLogin.setOnClickListener(v -> navigateToLogin());
    }

    private void navigateToLogin() {
        Intent intent = new Intent(VerifyEmailActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}