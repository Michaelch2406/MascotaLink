package com.mjc.mascotalink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class PaseadorRegistroPaso2Activity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    private ImageView previewFotoPerfil;
    private ImageView previewSelfie;
    private Button btnEliminarSelfie, btnEliminarFotoPerfil;
    private Button btnContinuarPaso3;
    private Uri selfieUri;
    private Uri fotoPerfilUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso2);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        // Firebase emuladores
        String host = "192.168.0.147";
        mAuth = FirebaseAuth.getInstance();
        mAuth.useEmulator(host, 9099);
        storage = FirebaseStorage.getInstance();
        storage.useEmulator(host, 9199);

        previewFotoPerfil = findViewById(R.id.preview_foto_perfil);
        previewSelfie = findViewById(R.id.preview_selfie);
        btnEliminarSelfie = findViewById(R.id.btn_eliminar_selfie);
        btnEliminarFotoPerfil = findViewById(R.id.btn_eliminar_foto_perfil);
        btnContinuarPaso3 = findViewById(R.id.btn_continuar_paso3);
        
        findViewById(R.id.img_selfie_ilustracion).setOnClickListener(v -> activarCamaraSelfie());
        Button btnElegirFoto = findViewById(R.id.btn_elegir_foto);
        Button btnTomarFoto = findViewById(R.id.btn_tomar_foto);
        Button btnActivarCamara = findViewById(R.id.btn_activar_camara);

        loadState();
        
        // Verificar estado inicial del botón de continuar
        verificarCompletitudPaso2();

        btnElegirFoto.setOnClickListener(v -> elegirFotoPerfil());
        btnTomarFoto.setOnClickListener(v -> tomarFotoPerfil());
        btnActivarCamara.setOnClickListener(v -> activarCamaraSelfie());
        
        // Configurar botones de eliminar
        btnEliminarSelfie.setOnClickListener(v -> eliminarSelfie());
        btnEliminarFotoPerfil.setOnClickListener(v -> eliminarFotoPerfil());
        
        // Configurar botón de continuar
        btnContinuarPaso3.setOnClickListener(v -> continuarAlPaso3());
    }

    private void elegirFotoPerfil() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.setType("image/*");
        fotoPerfilGaleriaLauncher.launch(galleryIntent);
    }

    private void tomarFotoPerfil() {
        // Usar cámara frontal para foto de perfil también
        Intent intent = new Intent(this, SelfieActivity.class);
        intent.putExtra("front", true); // Cambiar a cámara frontal
        intent.putExtra("tipo", "foto_perfil"); // Indicar que es para foto de perfil
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
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        selfieUri = uri;
                        // Mostrar preview de la selfie
                        mostrarPreviewSelfie(uri);
                        saveState();
                        Toast.makeText(this, "✅ Selfie capturada y guardada", Toast.LENGTH_SHORT).show();
                        
                        // Verificar si podemos continuar al paso 3
                        verificarCompletitudPaso2();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> fotoPerfilGaleriaLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        fotoPerfilUri = uri;
                        mostrarPreviewFotoPerfil(uri);
                        saveState();
                        Toast.makeText(this, "✅ Foto de perfil seleccionada", Toast.LENGTH_SHORT).show();
                        
                        // Verificar si podemos continuar al paso 3
                        verificarCompletitudPaso2();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> fotoPerfilCamaraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        fotoPerfilUri = uri;
                        mostrarPreviewFotoPerfil(uri);
                        saveState();
                        Toast.makeText(this, "✅ Foto de perfil tomada", Toast.LENGTH_SHORT).show();
                        
                        // Verificar si podemos continuar al paso 3
                        verificarCompletitudPaso2();
                    }
                }
            });

    private void verificarCompletitudPaso2() {
        if (selfieUri != null && fotoPerfilUri != null) {
            // Marcar paso 2 como completo
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit()
                .putBoolean("paso2_completo", true)
                .putLong("timestamp_paso2", System.currentTimeMillis())
                .apply();
            
            Toast.makeText(this, "✅ ¡Paso 2 completado! Puedes continuar al paso 3", Toast.LENGTH_SHORT).show();
            
            // Habilitar botón de continuar
            habilitarBotonContinuar();
            
        } else {
            String mensaje = "";
            if (selfieUri == null) mensaje += "• Falta tomar la selfie\n";
            if (fotoPerfilUri == null) mensaje += "• Falta seleccionar foto de perfil\n";
            
            Toast.makeText(this, "⚠️ Faltan elementos:\n" + mensaje, Toast.LENGTH_LONG).show();
            
            // Ocultar botón de continuar
            ocultarBotonContinuar();
        }
    }

    private void saveState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        if (selfieUri != null) ed.putString("selfieUri", selfieUri.toString());
        if (fotoPerfilUri != null) ed.putString("fotoPerfilUri", fotoPerfilUri.toString());
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
                Log.d("Paso2", "Foto de perfil cargada: " + fotoPerfilUri.toString());
            } catch (SecurityException e) {
                // Error de permisos - limpiar URI inválida
                prefs.edit().remove("fotoPerfilUri").apply();
                fotoPerfilUri = null;
                Toast.makeText(this, "⚠️ Imagen anterior no disponible. Selecciona una nueva foto de perfil.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                // Otros errores - limpiar URI inválida
                prefs.edit().remove("fotoPerfilUri").apply();
                fotoPerfilUri = null;
                Log.e("Paso2", "Error cargando foto de perfil: " + e.getMessage());
            }
        }
        
        if (s1 != null) {
            try {
                selfieUri = Uri.parse(s1);
                mostrarPreviewSelfie(selfieUri);
                Log.d("Paso2", "Selfie cargada: " + selfieUri.toString());
            } catch (SecurityException e) {
                // Error de permisos - limpiar URI inválida
                prefs.edit().remove("selfieUri").apply();
                selfieUri = null;
                Toast.makeText(this, "⚠️ Selfie anterior no disponible. Toma una nueva selfie.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                // Otros errores - limpiar URI inválida
                prefs.edit().remove("selfieUri").apply();
                selfieUri = null;
                Log.e("Paso2", "Error cargando selfie: " + e.getMessage());
            }
        }
        
        Log.d("Paso2", "Estado cargado - Selfie: " + (selfieUri != null) + ", FotoPerfil: " + (fotoPerfilUri != null));
    }

    // Método eliminado - ahora verificamos las URIs locales en lugar de URLs de Firebase
    
    private void mostrarPreviewSelfie(Uri uri) {
        try {
            previewSelfie.setImageURI(uri);
            findViewById(R.id.container_preview_selfie).setVisibility(ImageView.VISIBLE);
        } catch (SecurityException e) {
            // Error de permisos - continuar sin preview
            Toast.makeText(this, "⚠️ No se puede mostrar preview, pero la selfie fue guardada", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void mostrarPreviewFotoPerfil(Uri uri) {
        try {
            previewFotoPerfil.setImageURI(uri);
            findViewById(R.id.container_preview_foto_perfil).setVisibility(ImageView.VISIBLE);
        } catch (SecurityException e) {
            // Error de permisos - continuar sin preview
            Toast.makeText(this, "⚠️ No se puede mostrar preview, pero la foto fue guardada", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void eliminarSelfie() {
        selfieUri = null;
        findViewById(R.id.container_preview_selfie).setVisibility(ImageView.GONE);
        
        // Limpiar de SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().remove("selfieUri").apply();
        
        Toast.makeText(this, "✅ Selfie eliminada. Puedes tomar una nueva.", Toast.LENGTH_SHORT).show();
        
        // Verificar completitud
        verificarCompletitudPaso2();
    }
    
    private void eliminarFotoPerfil() {
        fotoPerfilUri = null;
        findViewById(R.id.container_preview_foto_perfil).setVisibility(ImageView.GONE);
        
        // Limpiar de SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().remove("fotoPerfilUri").apply();
        
        Toast.makeText(this, "✅ Foto de perfil eliminada. Puedes seleccionar una nueva.", Toast.LENGTH_SHORT).show();
        
        // Verificar completitud
        verificarCompletitudPaso2();
    }
    
    private void continuarAlPaso3() {
        if (selfieUri != null && fotoPerfilUri != null) {
            startActivity(new Intent(this, PaseadorRegistroPaso3Activity.class));
        } else {
            Toast.makeText(this, "⚠️ Asegúrate de completar ambas fotos antes de continuar", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void habilitarBotonContinuar() {
        if (btnContinuarPaso3 != null) {
            btnContinuarPaso3.setVisibility(Button.VISIBLE);
            btnContinuarPaso3.setEnabled(true);
        }
    }
    
    private void ocultarBotonContinuar() {
        if (btnContinuarPaso3 != null) {
            btnContinuarPaso3.setVisibility(Button.GONE);
            btnContinuarPaso3.setEnabled(false);
        }
    }
}
