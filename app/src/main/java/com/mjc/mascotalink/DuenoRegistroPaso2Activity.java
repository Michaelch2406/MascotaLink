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
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.utils.InputUtils;

import android.content.SharedPreferences;
import android.util.Log;

public class DuenoRegistroPaso2Activity extends AppCompatActivity {

    private static final String TAG = "DuenoRegistroPaso2";
    private static final long RATE_LIMIT_MS = 1000;

    private ImageView previewFotoPerfil;
    private ImageView previewSelfie;
    private Button btnContinuarPaso3;
    private TextView tvValidationMessages;
    private View cardSelfiePlaceholder;
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
        cardSelfiePlaceholder = findViewById(R.id.card_selfie_placeholder);
    }

    private void setupListeners() {
        // Listeners para selfie
        findViewById(R.id.img_selfie_ilustracion).setOnClickListener(v -> tomarSelfie());
        findViewById(R.id.btn_tomar_selfie).setOnClickListener(v -> tomarSelfie());
        findViewById(R.id.btn_eliminar_selfie).setOnClickListener(v -> {
            selfieUri = null;
            updateUI();
        });

        // Listeners para foto de perfil
        findViewById(R.id.btn_elegir_foto).setOnClickListener(v -> elegirFotoPerfil());
        findViewById(R.id.btn_tomar_foto).setOnClickListener(v -> tomarFotoPerfil());
        findViewById(R.id.btn_eliminar_foto_perfil).setOnClickListener(v -> {
            fotoPerfilUri = null;
            updateUI();
        });

        // Continuar
        btnContinuarPaso3.setOnClickListener(
            InputUtils.createSafeClickListener(v -> guardarUrisYContinuar())
        );
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
        // Configuración optimizada de Glide con caché
        RequestOptions glideOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .centerCrop();

        // Selfie
        if (selfieUri != null) {
            Glide.with(this)
                    .load(MyApplication.getFixedUrl(selfieUri.toString()))
                    .apply(glideOptions)
                    .into(previewSelfie);
            findViewById(R.id.container_preview_selfie).setVisibility(View.VISIBLE);
            if (cardSelfiePlaceholder != null) {
                cardSelfiePlaceholder.setVisibility(View.GONE);
            }
        } else {
            findViewById(R.id.container_preview_selfie).setVisibility(View.GONE);
            if (cardSelfiePlaceholder != null) {
                cardSelfiePlaceholder.setVisibility(View.VISIBLE);
            }
        }

        // Foto de perfil
        if (fotoPerfilUri != null) {
            Glide.with(this)
                    .load(MyApplication.getFixedUrl(fotoPerfilUri.toString()))
                    .apply(glideOptions)
                    .into(previewFotoPerfil);
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

        // Validar archivos de imagen antes de procesarlos
        if (!InputUtils.isValidImageFile(this, selfieUri, 5 * 1024 * 1024)) {
            Toast.makeText(this, "La selfie no es válida o excede 5MB", Toast.LENGTH_LONG).show();
            return;
        }
        if (!InputUtils.isValidImageFile(this, fotoPerfilUri, 5 * 1024 * 1024)) {
            Toast.makeText(this, "La foto de perfil no es válida o excede 5MB", Toast.LENGTH_LONG).show();
            return;
        }

        // Ocultar teclado antes de procesar
        InputUtils.hideKeyboard(this);
        InputUtils.setButtonLoading(btnContinuarPaso3, true, "Guardando...");

        try {
            // Copiamos los archivos a almacenamiento interno para asegurar que los URIs persistan
            Uri persistentSelfieUri = FileStorageHelper.copyFileToInternalStorage(this, selfieUri, "selfie_dueno_");
            Uri persistentFotoPerfilUri = FileStorageHelper.copyFileToInternalStorage(this, fotoPerfilUri, "fotoperfil_dueno_");

            if (persistentSelfieUri == null || persistentFotoPerfilUri == null) {
                Log.e(TAG, "Error al copiar archivos a almacenamiento interno");
                Toast.makeText(this, "Error al guardar las imágenes localmente.", Toast.LENGTH_LONG).show();
                InputUtils.setButtonLoading(btnContinuarPaso3, false);
                return;
            }

            SharedPreferences.Editor editor = getSharedPreferences("WizardDueno", MODE_PRIVATE).edit();
            editor.putString("selfieUri", persistentSelfieUri.toString());
            editor.putString("fotoPerfilUri", persistentFotoPerfilUri.toString());
            editor.apply();

            Log.d(TAG, "Imágenes guardadas correctamente");
            Toast.makeText(this, "Imágenes listas para registrar.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, DuenoRegistroPaso3Activity.class));
        } catch (Exception e) {
            Log.e(TAG, "Error al guardar URIs", e);
            Toast.makeText(this, "Error al procesar las imágenes. Intenta nuevamente.", Toast.LENGTH_LONG).show();
            InputUtils.setButtonLoading(btnContinuarPaso3, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity destruida");
    }
}
