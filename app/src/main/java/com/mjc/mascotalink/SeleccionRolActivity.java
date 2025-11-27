package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SeleccionRolActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seleccion_rol);

        View btnBack = findViewById(R.id.btn_back);
        View cardDueno = findViewById(R.id.card_dueno);
        View cardPaseador = findViewById(R.id.card_paseador);

        btnBack.setOnClickListener(v -> finish());

        cardDueno.setOnClickListener(v -> {
            startActivity(new Intent(this, DuenoRegistroPaso1Activity.class));
        });

        cardPaseador.setOnClickListener(v -> {
            startActivity(new Intent(this, PaseadorRegistroPaso1Activity.class));
        });
    }
}