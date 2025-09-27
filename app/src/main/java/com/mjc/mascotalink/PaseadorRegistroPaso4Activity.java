package com.mjc.mascotalink;

import android.app.AlertDialog;
import android.content.ClipData;
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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

public class PaseadorRegistroPaso4Activity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";
    private static final String TAG = "Paso4Paseador";

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private FirebaseFirestore db;

    private final List<String> urlsGaleria = new ArrayList<>();

    private ImageView img1, img2, img3; // para refrescar placeholders cuando subamos algo

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso4);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        db = FirebaseFirestore.getInstance();

        Button btnGaleria = findViewById(R.id.btn_galeria);
        Button btnCamara = findViewById(R.id.btn_camara);
        Button btnQuiz = findViewById(R.id.btn_comenzar_quiz);
        Button btnGuardar = findViewById(R.id.btn_guardar_continuar);

        // Referencias visuales de placeholders (si quieres actualizarlas dinámicamente)
        img1 = findViewById(R.id.placeholder_img1);
        img2 = findViewById(R.id.placeholder_img2);
        img3 = findViewById(R.id.placeholder_img3);

        btnGaleria.setOnClickListener(v -> abrirGaleria());
        btnCamara.setOnClickListener(v -> abrirCamara());
        btnQuiz.setOnClickListener(v -> iniciarCuestionario());
        btnGuardar.setOnClickListener(v -> guardarYContinuar());
    }

    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        galeriaLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> galeriaLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ClipData clipData = result.getData().getClipData();
                    if (clipData != null) {
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            Uri uri = clipData.getItemAt(i).getUri();
                            subirArchivoGaleriaPaseos(uri);
                        }
                    } else {
                        Uri uri = result.getData().getData();
                        if (uri != null) subirArchivoGaleriaPaseos(uri);
                    }
                }
            });

    private void abrirCamara() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setItems(new String[]{"Tomar Foto", "Grabar Video"}, (dialog, which) -> {
            if (which == 0) tomarFoto(); else grabarVideo();
        });
        builder.show();
    }

    private final ActivityResultLauncher<Intent> cameraFotoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) subirArchivoGaleriaPaseos(uri);
                }
            });

    private final ActivityResultLauncher<Intent> cameraVideoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) subirArchivoGaleriaPaseos(uri);
                }
            });

    private void tomarFoto() {
        // Reutilizamos SelfieActivity con cámara trasera para foto simple
        Intent intent = new Intent(this, SelfieActivity.class);
        intent.putExtra("front", false);
        cameraFotoLauncher.launch(intent);
    }

    private void grabarVideo() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(android.provider.MediaStore.EXTRA_DURATION_LIMIT, 60);
        cameraVideoLauncher.launch(intent);
    }

    private void subirArchivoGaleriaPaseos(Uri archivoUri) {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_LONG).show();
            return;
        }
        String uid = mAuth.getCurrentUser().getUid();
        String fileName = uid + "_paseo_" + System.currentTimeMillis();

        String mimeType = getContentResolver().getType(archivoUri);
        String extension = (mimeType != null && mimeType.startsWith("image/")) ? ".jpg" : ".mp4";

        StorageReference ref = storage.getReference().child("galeria_paseos/" + fileName + extension);
        ref.putFile(archivoUri).addOnSuccessListener(ts ->
                ref.getDownloadUrl().addOnSuccessListener(uri -> {
                    agregarUrlGaleriaPaseos(uri.toString());
                    actualizarVistaGaleria();
                })
        ).addOnFailureListener(e -> Toast.makeText(this, "Error al subir archivo", Toast.LENGTH_SHORT).show());
    }

    private void agregarUrlGaleriaPaseos(String url) {
        urlsGaleria.add(url);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        List<String> prev = new ArrayList<>(urlsGaleria);
        prefs.edit().putString("galeria_paseos_urls", String.join(",", prev)).apply();
    }

    private void actualizarVistaGaleria() {
        // Placeholder: podríamos cargar la última imagen en uno de los ImageView si es imagen
        // Para mantener la maqueta, no tocamos los placeholders si es video.
        // Esta función queda lista para ampliarse con Glide/Picasso.
    }

    private void iniciarCuestionario() {
        Intent intent = new Intent(this, QuizActivity.class);
        startActivity(intent);
    }

    private void guardarYContinuar() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_LONG).show();
        }
        String uid = mAuth.getCurrentUser().getUid();

        // Escritura mínima: persistir galería en el documento del paseador
        db.collection("paseadores").document(uid)
                .update("galeria_paseos_urls", urlsGaleria,
                        "ultima_actualizacion", FieldValue.serverTimestamp())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show();
                    // Navegar al Paso 5
                    startActivity(new Intent(this, PaseadorRegistroPaso5Activity.class));
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
