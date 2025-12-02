package com.mjc.mascotalink;

import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.firebase.storage.FirebaseStorage;
import com.mjc.mascotalink.MyApplication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import android.util.Log;

public class PaseadorRegistroPaso4Activity extends AppCompatActivity {

    private static final String TAG = "PaseadorPaso4";

    private static final String PREFS = "WizardPaseador";

    private final ArrayList<Uri> localUris = new ArrayList<>();
    private ImageView img1, img2, img3;
    private ImageView ivQuizCompletedCheck;
    private EditText etExperiencia, etMotivacion;
    private Button btnQuiz, btnGuardar;
    private TextView tvValidationMessages;

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    private final ActivityResultLauncher<Intent> galeriaLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        this::handleGaleriaResult
    );

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        this::handleCameraResult
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso4);

        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        setupViews();
        setupListeners();
        loadState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        verificarCompletitudPaso4();
    }

    private void setupViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        img1 = findViewById(R.id.placeholder_img1);
        img2 = findViewById(R.id.placeholder_img2);
        img3 = findViewById(R.id.placeholder_img3);
        ivQuizCompletedCheck = findViewById(R.id.iv_quiz_completed_check);
        btnQuiz = findViewById(R.id.btn_comenzar_quiz);
        btnGuardar = findViewById(R.id.btn_guardar_continuar);
        tvValidationMessages = findViewById(R.id.tv_validation_messages);
        etExperiencia = findViewById(R.id.et_experiencia_general);
        etMotivacion = findViewById(R.id.et_motivacion);
    }

    private void setupListeners() {
        findViewById(R.id.btn_galeria).setOnClickListener(v -> abrirGaleria());
        findViewById(R.id.btn_camara).setOnClickListener(v -> abrirCamara());
        btnQuiz.setOnClickListener(v -> iniciarCuestionario());
        btnGuardar.setOnClickListener(v -> guardarYContinuar());

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                saveState();
                verificarCompletitudPaso4();
            }
        };

        etExperiencia.addTextChangedListener(textWatcher);
        etMotivacion.addTextChangedListener(textWatcher);
    }

    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*,video/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        galeriaLauncher.launch(intent);
    }

    private void abrirCamara() {
        Intent intent = new Intent(this, VideoRecordActivity.class); // Usar la actividad unificada
        cameraLauncher.launch(intent);
    }

    private void handleGaleriaResult(androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            ClipData clipData = result.getData().getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    if (localUris.size() < 3) {
                        Uri sourceUri = clipData.getItemAt(i).getUri();
                        // Use a more unique prefix to avoid overwriting files copied in the same second
                        String uniquePrefix = "GALERIA_" + System.nanoTime() + "_";
                        Uri copiedUri = FileStorageHelper.copyFileToInternalStorage(this, sourceUri, uniquePrefix);
                        if (copiedUri != null) {
                            localUris.add(copiedUri);
                        } else {
                            Toast.makeText(this, "Error al copiar archivo de galería.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            } else if (result.getData().getData() != null) {
                if (localUris.size() < 3) {
                    Uri sourceUri = result.getData().getData();
                    Uri copiedUri = FileStorageHelper.copyFileToInternalStorage(this, sourceUri, "GALERIA_");
                    if (copiedUri != null) {
                        localUris.add(copiedUri);
                    } else {
                        Toast.makeText(this, "Error al copiar archivo de galería.", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            if (localUris.size() >= 3) {
                Toast.makeText(this, "Límite de 3 archivos alcanzado.", Toast.LENGTH_SHORT).show();
            }

            saveState();
            actualizarVistaGaleria();
            verificarCompletitudPaso4();
        }
    }

    private void handleCameraResult(androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri tempUri = result.getData().getData();
            if (tempUri != null && localUris.size() < 3) {
                Uri permanentUri = FileStorageHelper.copyFileToInternalStorage(this, tempUri, "VIDEO_");
                if (permanentUri != null) {
                    localUris.add(permanentUri);
                    saveState();
                    actualizarVistaGaleria();
                    verificarCompletitudPaso4();
                } else {
                    Toast.makeText(this, "Error al guardar el video.", Toast.LENGTH_SHORT).show();
                }
            } else if (localUris.size() >= 3) {
                Toast.makeText(this, "Límite de 3 archivos alcanzado.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void actualizarVistaGaleria() {
        ImageView[] placeholders = {img1, img2, img3};
        ImageView[] playIcons = {findViewById(R.id.play_icon1), findViewById(R.id.play_icon2), findViewById(R.id.play_icon3)};

        for (int i = 0; i < placeholders.length; i++) {
            if (i < localUris.size()) {
                Uri uri = localUris.get(i);
                placeholders[i].setAlpha(1.0f);
                Glide.with(this).load(MyApplication.getFixedUrl(uri.toString())).centerCrop().into(placeholders[i]);

                String mimeType = getContentResolver().getType(uri);
                playIcons[i].setVisibility(mimeType != null && mimeType.startsWith("video/") ? View.VISIBLE : View.GONE);
            } else {
                placeholders[i].setImageResource(R.drawable.galeria_paseos_foto1); // Placeholder
                placeholders[i].setAlpha(0.5f);
                playIcons[i].setVisibility(View.GONE);
            }
        }
    }

    private void verificarCompletitudPaso4() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean quizAprobado = prefs.getBoolean("quiz_aprobado", false);
        String experiencia = etExperiencia.getText().toString().trim();
        String motivacion = etMotivacion.getText().toString().trim();

        Log.d(TAG, "verificarCompletitudPaso4: quizAprobado=" + quizAprobado + ", experiencia=" + experiencia + ", motivacion=" + motivacion);

        List<String> faltantes = new ArrayList<>();
        if (localUris.isEmpty()) {
            faltantes.add("• Sube al menos una foto o video de tus paseos.");
        }
        if (!quizAprobado) {
            faltantes.add("• Completa y aprueba el cuestionario de conocimientos.");
        }
        if (etExperiencia.getText().toString().trim().isEmpty()) {
            faltantes.add("• Ingresa tu experiencia general.");
        }
        if (etMotivacion.getText().toString().trim().isEmpty()) {
            faltantes.add("• Ingresa tu motivación.");
        }

        // Actualizar UI del Quiz
        if (quizAprobado) {
            ivQuizCompletedCheck.setVisibility(View.VISIBLE);
            btnQuiz.setText("Resultados");
        } else {
            ivQuizCompletedCheck.setVisibility(View.GONE);
            btnQuiz.setText("Comenzar");
        }

        if (faltantes.isEmpty()) {
            tvValidationMessages.setVisibility(View.GONE);
            btnGuardar.setEnabled(true);
            prefs.edit().putBoolean("paso4_completo", true).apply();
        } else {
            tvValidationMessages.setText(String.join("\n", faltantes));
            tvValidationMessages.setVisibility(View.VISIBLE);
            btnGuardar.setEnabled(false);
            prefs.edit().putBoolean("paso4_completo", false).apply();
        }
    }

    private void iniciarCuestionario() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean quizAprobado = prefs.getBoolean("quiz_aprobado", false);
        Intent intent = quizAprobado ? new Intent(this, QuizResultsActivity.class) : new Intent(this, QuizActivity.class);
        startActivity(intent);
    }

    private void guardarYContinuar() {
        if (btnGuardar.isEnabled()) {
            startActivity(new Intent(this, PaseadorRegistroPaso5Activity.class));
        } else {
            verificarCompletitudPaso4();
        }
    }

    private void saveState() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        List<String> uriStrings = localUris.stream().map(Uri::toString).collect(Collectors.toList());
        editor.putString("galeria_paseos_uris", String.join(",", uriStrings));
        editor.putString("experiencia_general", etExperiencia.getText().toString());
        editor.putString("motivacion", etMotivacion.getText().toString());
        editor.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        etExperiencia.setText(prefs.getString("experiencia_general", ""));
        etMotivacion.setText(prefs.getString("motivacion", ""));
        String uriString = prefs.getString("galeria_paseos_uris", "");
        if (!uriString.isEmpty()) {
            localUris.clear();
            List<String> uriStrings = new ArrayList<>(Arrays.asList(uriString.split(",")));
            for (String s : uriStrings) {
                try {
                    localUris.add(Uri.parse(s));
                } catch (Exception e) {
                    // Ignorar URI inválida
                }
            }
            actualizarVistaGaleria();
        }
    }
}
