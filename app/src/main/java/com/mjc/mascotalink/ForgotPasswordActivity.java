package com.mjc.mascotalink;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText etEmail;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Toolbar back navigation
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.et_email);
        progressBar = findViewById(R.id.pb_loading);

        Button btnSend = findViewById(R.id.btn_send);

        btnSend.setOnClickListener(v -> enviarEmailRecuperacion());
    }

    private void enviarEmailRecuperacion() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("El correo es requerido");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Formato de correo inválido");
            return;
        }
        etEmail.setError(null);
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
