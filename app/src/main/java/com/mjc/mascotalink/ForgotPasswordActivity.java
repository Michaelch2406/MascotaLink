package com.mjc.mascotalink;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private TextInputLayout tilEmail;
    private TextInputEditText etEmail;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Configurar emulator antes de usar Firebase
        String host = "192.168.0.147";
        FirebaseAuth authInstance = FirebaseAuth.getInstance();
        authInstance.useEmulator(host, 9099);
        mAuth = authInstance;

        tilEmail = findViewById(R.id.til_email);
        etEmail = findViewById(R.id.et_email);
        progressBar = findViewById(R.id.pb_loading);

        Button btnSend = findViewById(R.id.btn_send);
        Button btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> enviarEmailRecuperacion());
    }

    private void enviarEmailRecuperacion() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("El correo es requerido");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Formato de correo inválido");
            return;
        }
        tilEmail.setError(null);
        mostrarLoading(true);
        mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            mostrarLoading(false);
            if (task.isSuccessful()) {
                Toast.makeText(this, "Se ha enviado un correo de recuperación a " + email, Toast.LENGTH_LONG).show();
                finish();
            } else {
                String msg = task.getException() != null ? task.getException().getMessage() : "Error al enviar correo";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void mostrarLoading(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
