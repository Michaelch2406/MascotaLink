package com.mjc.mascotalink;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.mjc.mascotalink.MyApplication;

import android.content.SharedPreferences;

public class DuenoRegistroPaso2Activity extends AppCompatActivity {

    private ImageView previewFotoPerfil;
    private ImageView previewSelfie;
    private Button btnContinuarPaso3;
    private TextView tvValidationMessages;
    private Uri selfieUri;
    private Uri fotoPerfilUri;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dueno_registro_paso2);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindViews();
        setupListeners();
        loadUrisFromPrefs(); // Cargar URIs guardadas al iniciar
        updateUI();
    }

    private void bindViews() {
        previewFotoPerfil = findViewById(R.id.preview_foto_perfil);
        previewSelfie = findViewById(R.id.preview_selfie);
        btnContinuarPaso3 = findViewById(R.id.btn_continuar_paso3);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);
    }

    private void setupListeners() {
        findViewById(R.id.img_selfie_ilustracion).setOnClickListener(v -> tomarSelfie());
        findViewById(R.id.btn_elegir_foto).setOnClickListener(v -> elegirFotoPerfil());
        findViewById(R.id.btn_tomar_foto).setOnClickListener(v -> tomarFotoPerfil());
        findViewById(R.id.btn_eliminar_selfie).setOnClickListener(v -> {
            selfieUri = null;
            updateUI();
        });
        findViewById(R.id.btn_eliminar_foto_perfil).setOnClickListener(v -> {
            fotoPerfilUri = null;
            updateUI();
        });
        btnContinuarPaso3.setOnClickListener(v -> guardarUrisYContinuar());
    }

    private void loadUrisFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("WizardDueno", MODE_PRIVATE);
        String selfieUriString = prefs.getString("selfieUri", null);
        String fotoPerfilUriString = prefs.getString("fotoPerfilUri", null);

        if (selfieUriString != null) {
            selfieUri = Uri.parse(selfieUriString);
        }
        if (fotoPerfilUriString != null) {
            fotoPerfilUri = Uri.parse(fotoPerfilUriString);
        }
    }

    private void tomarSelfie() {
        Intent intent = new Intent(this, SelfieActivity.class);
        intent.putExtra("front", true);
        selfieLauncher.launch(intent);
    }

    private void elegirFotoPerfil() {
        galeriaLauncher.launch("image/*");
    }

    private void tomarFotoPerfil() {
        Intent intent = new Intent(this, SelfieActivity.class);
        intent.putExtra("front", true);
        fotoPerfilLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> selfieLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selfieUri = result.getData().getData();
                    updateUI();
                }
            });

    private final ActivityResultLauncher<String> galeriaLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    fotoPerfilUri = uri;
                    updateUI();
                }
            });

    private final ActivityResultLauncher<Intent> fotoPerfilLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    fotoPerfilUri = result.getData().getData();
                    updateUI();
                }
            });

    private void updateUI() {
        // Selfie
        if (selfieUri != null) {
            Glide.with(this).load(MyApplication.getFixedUrl(selfieUri.toString())).into(previewSelfie);
            findViewById(R.id.container_preview_selfie).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.container_preview_selfie).setVisibility(View.GONE);
        }

        // Foto de perfil
        if (fotoPerfilUri != null) {
            Glide.with(this).load(MyApplication.getFixedUrl(fotoPerfilUri.toString())).into(previewFotoPerfil);
            findViewById(R.id.container_preview_foto_perfil).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.container_preview_foto_perfil).setVisibility(View.GONE);
        }

        checkCompletitud();
    }

    private void checkCompletitud() {
        StringBuilder faltantes = new StringBuilder();
        if (selfieUri == null) faltantes.append("• Falta tomar la selfie de verificación.\n");
        if (fotoPerfilUri == null) faltantes.append("• Falta seleccionar o tomar una foto de perfil.\n");

        if (faltantes.length() == 0) {
            tvValidationMessages.setVisibility(View.GONE);
            btnContinuarPaso3.setVisibility(View.VISIBLE);
        } else {
            tvValidationMessages.setText(faltantes.toString().trim());
            tvValidationMessages.setVisibility(View.VISIBLE);
            btnContinuarPaso3.setVisibility(View.GONE);
        }
    }

    private void guardarUrisYContinuar() {
        if (selfieUri == null || fotoPerfilUri == null) {
            checkCompletitud();
            return;
        }

        // Copiamos los archivos a almacenamiento interno para asegurar que los URIs persistan
        Uri persistentSelfieUri = FileStorageHelper.copyFileToInternalStorage(this, selfieUri, "selfie_dueno_");
        Uri persistentFotoPerfilUri = FileStorageHelper.copyFileToInternalStorage(this, fotoPerfilUri, "fotoperfil_dueno_");

        if (persistentSelfieUri == null || persistentFotoPerfilUri == null) {
            Toast.makeText(this, "Error al guardar las imágenes localmente.", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = getSharedPreferences("WizardDueno", MODE_PRIVATE).edit();
        editor.putString("selfieUri", persistentSelfieUri.toString());
        editor.putString("fotoPerfilUri", persistentFotoPerfilUri.toString());
        editor.apply();

        Toast.makeText(this, "Imágenes listas para registrar.", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, DuenoRegistroPaso3Activity.class));
    }
}
