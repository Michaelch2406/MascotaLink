package com.mjc.mascotalink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.mjc.mascotalink.utils.InputUtils;

public class SeleccionRolActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seleccion_rol);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        View cardDueno = findViewById(R.id.card_dueno);
        View cardPaseador = findViewById(R.id.card_paseador);

        cardDueno.setOnClickListener(InputUtils.createSafeClickListener(v -> {
            startActivity(new Intent(this, DuenoRegistroPaso1Activity.class));
        }));

        cardPaseador.setOnClickListener(InputUtils.createSafeClickListener(v -> {
            startActivity(new Intent(this, PaseadorRegistroPaso1Activity.class));
        }));
    }
}