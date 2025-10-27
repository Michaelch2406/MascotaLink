package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class VerifyEmailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email);

        String email = getIntent().getStringExtra("email");

        TextView textViewEmail = findViewById(R.id.text_view_email);
        if (email != null && !email.isEmpty()) {
            textViewEmail.setText("Hemos enviado un enlace de recuperación a:\n" + email);
        }

        Button buttonBackToLogin = findViewById(R.id.button_back_to_login);
        buttonBackToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(VerifyEmailActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
