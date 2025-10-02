package com.mjc.mascotalink;

import android.content.Intent;
import android.content.SharedPreferences;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class DuenoRegistroPaso2Activity extends AppCompatActivity {

    private static final String PREFS = "WizardDueno";

    private ImageView previewFotoPerfil;
    private ImageView previewSelfie;
    private Button btnContinuarPaso3;
    private TextView tvValidationMessages;
    private Uri selfieUri;
    private Uri fotoPerfilUri;

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dueno_registro_paso2);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        db = FirebaseFirestore.getInstance();

        // Vistas
        previewFotoPerfil = findViewById(R.id.preview_foto_perfil);
        previewSelfie = findViewById(R.id.preview_selfie);
        btnContinuarPaso3 = findViewById(R.id.btn_continuar_paso3);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);
        Button btnEliminarSelfie = findViewById(R.id.btn_eliminar_selfie);
        Button btnEliminarFotoPerfil = findViewById(R.id.btn_eliminar_foto_perfil);
        Button btnElegirFoto = findViewById(R.id.btn_elegir_foto);
        Button btnTomarFoto = findViewById(R.id.btn_tomar_foto);

        // Listeners
        findViewById(R.id.img_selfie_ilustracion).setOnClickListener(v -> activarCamaraSelfie());
        btnElegirFoto.setOnClickListener(v -> elegirFotoPerfil());
        btnTomarFoto.setOnClickListener(v -> tomarFotoPerfil());
        btnEliminarSelfie.setOnClickListener(v -> eliminarSelfie());
        btnEliminarFotoPerfil.setOnClickListener(v -> eliminarFotoPerfil());
        btnContinuarPaso3.setOnClickListener(v -> subirYContinuar());

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
        StringBuilder faltantes = new StringBuilder();
        if (selfieUri == null) faltantes.append("• Falta tomar la selfie\n");
        if (fotoPerfilUri == null) faltantes.append("• Falta seleccionar foto de perfil\n");

        if (faltantes.length() == 0) {
            tvValidationMessages.setVisibility(View.GONE);
            btnContinuarPaso3.setVisibility(View.VISIBLE);
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit().putBoolean("paso2_completo", true).apply();
        } else {
            tvValidationMessages.setText(faltantes.toString().trim());
            tvValidationMessages.setVisibility(View.VISIBLE);
            btnContinuarPaso3.setVisibility(View.GONE);
        }
    }

    private void saveState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString("selfieUri", selfieUri != null ? selfieUri.toString() : null);
        ed.putString("fotoPerfilUri", fotoPerfilUri != null ? fotoPerfilUri.toString() : null);
        ed.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String s1 = prefs.getString("selfieUri", null);
        String s2 = prefs.getString("fotoPerfilUri", null);
        if (s1 != null) try { selfieUri = Uri.parse(s1); mostrarPreviewSelfie(selfieUri);} catch (Exception ignored) {}
        if (s2 != null) try { fotoPerfilUri = Uri.parse(s2); mostrarPreviewFotoPerfil(fotoPerfilUri);} catch (Exception ignored) {}
    }

    private void mostrarPreviewSelfie(Uri uri) { Glide.with(this).load(uri).into(previewSelfie); findViewById(R.id.container_preview_selfie).setVisibility(View.VISIBLE); }
    private void mostrarPreviewFotoPerfil(Uri uri) { Glide.with(this).load(uri).into(previewFotoPerfil); findViewById(R.id.container_preview_foto_perfil).setVisibility(View.VISIBLE); }

    private void eliminarSelfie() { selfieUri = null; findViewById(R.id.container_preview_selfie).setVisibility(View.GONE); saveState(); verificarCompletitudPaso2(); }
    private void eliminarFotoPerfil() { fotoPerfilUri = null; findViewById(R.id.container_preview_foto_perfil).setVisibility(View.GONE); saveState(); verificarCompletitudPaso2(); }

    private void subirYContinuar() {
        if (mAuth.getCurrentUser() == null) { Toast.makeText(this, "Sesión inválida", Toast.LENGTH_SHORT).show(); return; }
        String uid = mAuth.getCurrentUser().getUid();
        if (selfieUri == null || fotoPerfilUri == null) { verificarCompletitudPaso2(); return; }

        btnContinuarPaso3.setEnabled(false);

        StorageReference root = storage.getReference();
        StorageReference selfieRef = root.child("usuarios/" + uid + "/selfie.jpg");
        StorageReference fotoRef = root.child("usuarios/" + uid + "/foto_perfil.jpg");

        selfieRef.putFile(selfieUri)
                .addOnSuccessListener(taskSnapshot -> selfieRef.getDownloadUrl()
                        .addOnSuccessListener(selfieUrl -> {
                            fotoRef.putFile(fotoPerfilUri)
                                    .addOnSuccessListener(ts -> fotoRef.getDownloadUrl()
                                            .addOnSuccessListener(fotoUrl -> {
                                                db.collection("usuarios").document(uid)
                                                        .update("selfie_url", selfieUrl.toString(),
                                                                "foto_perfil", fotoUrl.toString())
                                                        .addOnSuccessListener(aVoid -> {
                                                            Toast.makeText(this, "Imágenes guardadas", Toast.LENGTH_SHORT).show();
                                                            startActivity(new Intent(this, DuenoRegistroPaso3Activity.class));
                                                            finish();
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            btnContinuarPaso3.setEnabled(true);
                                                            Toast.makeText(this, "Error guardando URLs: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                        });
                                            })
                                            .addOnFailureListener(e -> {
                                                btnContinuarPaso3.setEnabled(true);
                                                Toast.makeText(this, "Error obteniendo URL de foto: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            }))
                                    .addOnFailureListener(e -> {
                                        btnContinuarPaso3.setEnabled(true);
                                        Toast.makeText(this, "Error subiendo foto de perfil: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    });
                        })
                        .addOnFailureListener(e -> {
                            btnContinuarPaso3.setEnabled(true);
                            Toast.makeText(this, "Error obteniendo URL de selfie: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }))
                .addOnFailureListener(e -> {
                    btnContinuarPaso3.setEnabled(true);
                    Toast.makeText(this, "Error subiendo selfie: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
