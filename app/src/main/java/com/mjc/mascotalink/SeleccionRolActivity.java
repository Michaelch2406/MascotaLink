package com.mjc.mascotalink;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SeleccionRolActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seleccion_rol);

        Button btnDueno = findViewById(R.id.btn_dueno);
        Button btnPaseador = findViewById(R.id.btn_paseador);

        btnDueno.setOnClickListener(v ->
                Toast.makeText(this, "Registro de Dueño próximamente", Toast.LENGTH_SHORT).show());
        btnPaseador.setOnClickListener(v ->
                Toast.makeText(this, "Registro de Paseador próximamente", Toast.LENGTH_SHORT).show());
    }
}
