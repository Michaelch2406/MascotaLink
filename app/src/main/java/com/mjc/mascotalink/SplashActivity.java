package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    // Duración de la pantalla de carga en milisegundos
    private static final long SPLASH_DURATION = 2000; // 2 segundos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Usar Handler para retrasar la transición a LoginActivity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Crear un Intent para iniciar LoginActivity
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            
            // Cerrar SplashActivity para que no se pueda volver a ella con el botón atrás
            finish();
        }, SPLASH_DURATION);
    }
}