package com.mjc.mascotalink;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class SeleccionRolActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seleccion_rol);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        Button btnDueno = findViewById(R.id.btn_select_dueno);
        Button btnPaseador = findViewById(R.id.btn_select_paseador);

        btnDueno.setOnClickListener(v -> {
                startActivity(new Intent(this, DuenoRegistroPaso1Activity.class));
        });
        btnPaseador.setOnClickListener(v -> {
                startActivity(new Intent(this, PaseadorRegistroPaso1Activity.class));
        });
    }
}
