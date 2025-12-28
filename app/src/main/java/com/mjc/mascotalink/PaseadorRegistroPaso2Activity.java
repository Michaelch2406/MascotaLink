package com.mjc.mascotalink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.utils.InputUtils;

import java.util.ArrayList;
import java.util.List;

public class PaseadorRegistroPaso2Activity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";

    private ImageView previewFotoPerfil;
    private ImageView previewSelfie;
    private Button btnContinuarPaso3;
    private TextView tvValidationMessages;
    private Uri selfieUri;
    private Uri fotoPerfilUri;
    private View layoutSelfieEmpty;
    private View layoutFotoPerfilEmpty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso2);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Vistas
        previewFotoPerfil = findViewById(R.id.preview_foto_perfil);
        previewSelfie = findViewById(R.id.preview_selfie);
        btnContinuarPaso3 = findViewById(R.id.btn_continuar_paso3);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);
        View btnEliminarSelfie = findViewById(R.id.btn_eliminar_selfie);
        View btnEliminarFotoPerfil = findViewById(R.id.btn_eliminar_foto_perfil);
        Button btnElegirFoto = findViewById(R.id.btn_elegir_foto);
        Button btnTomarFoto = findViewById(R.id.btn_tomar_foto);
        
        layoutSelfieEmpty = findViewById(R.id.layout_selfie_empty);
        layoutFotoPerfilEmpty = findViewById(R.id.layout_foto_perfil_empty);

        // Listeners con SafeClickListener para prevenir doble-click
        findViewById(R.id.img_selfie_ilustracion).setOnClickListener(v -> activarCamaraSelfie());
        btnElegirFoto.setOnClickListener(v -> elegirFotoPerfil());
        btnTomarFoto.setOnClickListener(v -> tomarFotoPerfil());
        btnEliminarSelfie.setOnClickListener(v -> eliminarSelfie());
        btnEliminarFotoPerfil.setOnClickListener(v -> eliminarFotoPerfil());
        btnContinuarPaso3.setOnClickListener(InputUtils.createSafeClickListener(v -> continuarAlPaso3()));

        loadState();
        verificarCompletitudPaso2();
    }

    private void elegirFotoPerfil() {
        Intent galleryIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
        galleryIntent.setType("image/*");
        galleryIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        fotoPerfilGaleriaLauncher.launch(galleryIntent);
    }

    private void tomarFotoPerfil() {
        Intent intent = new Intent(this, SelfieActivity.class);
        intent.putExtra("front", true);
        intent.putExtra("tipo", "foto_perfil");
        fotoPerfilCamaraLauncher.launch(intent);
    }

    private void activarCamaraSelfie() {
        Intent intent = new Intent(this, SelfieActivity.class);
        intent.putExtra("front", true);
        selfieResultLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> selfieResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri tempUri = result.getData().getData();
                    if (tempUri != null) {
                        // Validar imagen antes de copiar
                        if (!InputUtils.isValidImageFile(this, tempUri, 5 * 1024 * 1024)) {
                            Toast.makeText(this, "La selfie no es válida o excede 5MB", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Uri permanentUri = FileStorageHelper.copyFileToInternalStorage(this, tempUri, "SELFIE_");
                        if (permanentUri != null) {
                            selfieUri = permanentUri;
                            mostrarPreviewSelfie(selfieUri);
                            saveState();
                            verificarCompletitudPaso2();
                        } else {
                            Toast.makeText(this, "Error al guardar la selfie.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    private final ActivityResultLauncher<Intent> fotoPerfilCamaraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri tempUri = result.getData().getData();
                    if (tempUri != null) {
                        // Validar imagen antes de copiar
                        if (!InputUtils.isValidImageFile(this, tempUri, 5 * 1024 * 1024)) {
                            Toast.makeText(this, "La foto de perfil no es válida o excede 5MB", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Uri permanentUri = FileStorageHelper.copyFileToInternalStorage(this, tempUri, "FOTO_PERFIL_");
                        if (permanentUri != null) {
                            fotoPerfilUri = permanentUri;
                            mostrarPreviewFotoPerfil(fotoPerfilUri);
                            saveState();
                            verificarCompletitudPaso2();
                        } else {
                            Toast.makeText(this, "Error al guardar la foto de perfil.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    private final ActivityResultLauncher<Intent> fotoPerfilGaleriaLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri tempUri = result.getData().getData();
                    if (tempUri != null) {
                        // Validar imagen antes de copiar
                        if (!InputUtils.isValidImageFile(this, tempUri, 5 * 1024 * 1024)) {
                            Toast.makeText(this, "La foto de perfil no es válida o excede 5MB", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Uri permanentUri = FileStorageHelper.copyFileToInternalStorage(this, tempUri, "FOTO_PERFIL_");
                        if (permanentUri != null) {
                            fotoPerfilUri = permanentUri;
                            mostrarPreviewFotoPerfil(fotoPerfilUri);
                            saveState();
                            verificarCompletitudPaso2();
                        } else {
                            Toast.makeText(this, "Error al guardar la foto de perfil.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    private void verificarCompletitudPaso2() {
        List<String> faltantes = new ArrayList<>();
        if (selfieUri == null) {
            faltantes.add("• Falta tomar la selfie");
        }
        if (fotoPerfilUri == null) {
            faltantes.add("• Falta seleccionar foto de perfil");
        }

        if (faltantes.isEmpty()) {
            tvValidationMessages.setVisibility(View.GONE);
            habilitarBotonContinuar();
            // Marcar paso 2 como completo
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit()
                .putBoolean("paso2_completo", true)
                .putLong("timestamp_paso2", System.currentTimeMillis())
                .apply();
        } else {
            tvValidationMessages.setText(String.join("\n", faltantes));
            tvValidationMessages.setVisibility(View.VISIBLE);
            ocultarBotonContinuar();
        }
    }

    private void saveState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        if (selfieUri != null) {
            ed.putString("selfieUri", selfieUri.toString());
        } else {
            ed.remove("selfieUri");
        }
        if (fotoPerfilUri != null) {
            ed.putString("fotoPerfilUri", fotoPerfilUri.toString());
        } else {
            ed.remove("fotoPerfilUri");
        }
        ed.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String s1 = prefs.getString("selfieUri", null);
        String s2 = prefs.getString("fotoPerfilUri", null);

        if (s2 != null) {
            try {
                fotoPerfilUri = Uri.parse(s2);
                mostrarPreviewFotoPerfil(fotoPerfilUri);
            } catch (Exception e) {
                prefs.edit().remove("fotoPerfilUri").apply();
                fotoPerfilUri = null;
                Toast.makeText(this, " Imagen anterior no disponible. Selecciona una nueva.", Toast.LENGTH_SHORT).show();
            }
        }

        if (s1 != null) {
            try {
                selfieUri = Uri.parse(s1);
                mostrarPreviewSelfie(selfieUri);
            } catch (Exception e) {
                prefs.edit().remove("selfieUri").apply();
                selfieUri = null;
                Toast.makeText(this, " Selfie anterior no disponible. Tómala de nuevo.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void mostrarPreviewSelfie(Uri uri) {
        try {
            Glide.with(this)
                .load(MyApplication.getFixedUrl(uri.toString()))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(false)
                .into(previewSelfie);
            layoutSelfieEmpty.setVisibility(View.GONE);
            findViewById(R.id.container_preview_selfie).setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo mostrar la preview de la selfie.", Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarPreviewFotoPerfil(Uri uri) {
        try {
            Glide.with(this)
                .load(MyApplication.getFixedUrl(uri.toString()))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(false)
                .into(previewFotoPerfil);
            layoutFotoPerfilEmpty.setVisibility(View.GONE);
            findViewById(R.id.container_preview_foto_perfil).setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo mostrar la preview de la foto.", Toast.LENGTH_SHORT).show();
        }
    }

    private void eliminarSelfie() {
        selfieUri = null;
        layoutSelfieEmpty.setVisibility(View.VISIBLE);
        findViewById(R.id.container_preview_selfie).setVisibility(View.GONE);
        saveState();
        verificarCompletitudPaso2();
    }

    private void eliminarFotoPerfil() {
        fotoPerfilUri = null;
        layoutFotoPerfilEmpty.setVisibility(View.VISIBLE);
        findViewById(R.id.container_preview_foto_perfil).setVisibility(View.GONE);
        saveState();
        verificarCompletitudPaso2();
    }

    private void continuarAlPaso3() {
        // Rate limiting ya integrado en SafeClickListener
        if (selfieUri != null && fotoPerfilUri != null) {
            // Ocultar teclado antes de procesar
            InputUtils.hideKeyboard(this);
            InputUtils.setButtonLoading(btnContinuarPaso3, true, "Procesando...");

            startActivity(new Intent(this, PaseadorRegistroPaso3Activity.class));
            InputUtils.setButtonLoading(btnContinuarPaso3, false);
        } else {
            verificarCompletitudPaso2();
        }
    }

    private void habilitarBotonContinuar() {
        if (btnContinuarPaso3 != null) {
            btnContinuarPaso3.setVisibility(Button.VISIBLE);
        }
    }

    private void ocultarBotonContinuar() {
        if (btnContinuarPaso3 != null) {
            btnContinuarPaso3.setVisibility(Button.GONE);
        }
    }
}
