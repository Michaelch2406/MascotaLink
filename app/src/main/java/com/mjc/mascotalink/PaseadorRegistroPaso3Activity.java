package com.mjc.mascotalink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.appbar.MaterialToolbar;
import com.mjc.mascotalink.MyApplication;

import java.util.ArrayList;
import java.util.List;

public class PaseadorRegistroPaso3Activity extends AppCompatActivity {

    private static final String PREFS = "WizardPaseador";

    private TextView tvValidationMessages;
    private Button btnContinuarPaso4;

    private Uri antecedentesUri;
    private Uri medicoUri;

    private long lastClickTime = 0;

    private final ActivityResultLauncher<Intent> antecedentesLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> handleActivityResult(result, "antecedentes")
    );

    private final ActivityResultLauncher<Intent> medicoLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> handleActivityResult(result, "medico")
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paseador_registro_paso3);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Vistas
        tvValidationMessages = findViewById(R.id.tv_validation_messages);
        btnContinuarPaso4 = findViewById(R.id.btn_enviar_verificacion); // ID del XML

        // Listeners
        findViewById(R.id.btn_subir_antecedentes).setOnClickListener(v -> selectFile("antecedentes"));
        findViewById(R.id.btn_subir_medico).setOnClickListener(v -> selectFile("medico"));
        findViewById(R.id.btn_eliminar_antecedentes).setOnClickListener(v -> eliminarDocumento("antecedentes"));
        findViewById(R.id.btn_eliminar_medico).setOnClickListener(v -> eliminarDocumento("medico"));
        btnContinuarPaso4.setOnClickListener(v -> continuarAlPaso4());

        loadState();
        verificarCompletitudPaso3();
    }

    private void selectFile(String tipo) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        if ("antecedentes".equals(tipo)) {
            antecedentesLauncher.launch(intent);
        } else {
            medicoLauncher.launch(intent);
        }
    }

    private void handleActivityResult(androidx.activity.result.ActivityResult result, String tipo) {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri sourceUri = result.getData().getData();
            if (sourceUri != null) {
                Uri copiedUri = FileStorageHelper.copyFileToInternalStorage(this, sourceUri, tipo.toUpperCase() + "_");
                if (copiedUri != null) {
                    if ("antecedentes".equals(tipo)) {
                        antecedentesUri = copiedUri;
                    } else {
                        medicoUri = copiedUri;
                    }
                    saveState();
                    mostrarPreview(tipo, copiedUri);
                    verificarCompletitudPaso3();
                } else {
                    Toast.makeText(this, "Error al copiar el archivo.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void mostrarPreview(String tipo, Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        boolean isPdf = mimeType != null && mimeType.equals("application/pdf");

        View container = tipo.equals("antecedentes") ? findViewById(R.id.container_preview_antecedentes) : findViewById(R.id.container_preview_medico);
        ImageView imgPreview = tipo.equals("antecedentes") ? findViewById(R.id.preview_antecedentes_img) : findViewById(R.id.preview_medico_img);
        View pdfPreview = tipo.equals("antecedentes") ? findViewById(R.id.preview_antecedentes_pdf) : findViewById(R.id.preview_medico_pdf);
        TextView pdfName = tipo.equals("antecedentes") ? findViewById(R.id.tv_antecedentes_pdf_nombre) : findViewById(R.id.tv_medico_pdf_nombre);

        container.setVisibility(View.VISIBLE);
        if (isPdf) {
            imgPreview.setVisibility(View.GONE);
            pdfPreview.setVisibility(View.VISIBLE);
            pdfName.setText(getFileName(uri));
        } else { // Es imagen
            pdfPreview.setVisibility(View.GONE);
            imgPreview.setVisibility(View.VISIBLE);
            Glide.with(this)
                .load(MyApplication.getFixedUrl(uri.toString()))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(false)
                .into(imgPreview);
        }
    }

    private void eliminarDocumento(String tipo) {
        if ("antecedentes".equals(tipo)) {
            antecedentesUri = null;
            findViewById(R.id.container_preview_antecedentes).setVisibility(View.GONE);
        } else {
            medicoUri = null;
            findViewById(R.id.container_preview_medico).setVisibility(View.GONE);
        }
        saveState();
        verificarCompletitudPaso3();
    }

    private void verificarCompletitudPaso3() {
        List<String> faltantes = new ArrayList<>();
        if (antecedentesUri == null) {
            faltantes.add("• Falta el certificado de antecedentes");
        }
        if (medicoUri == null) {
            faltantes.add("• Falta el certificado médico");
        }

        if (faltantes.isEmpty()) {
            tvValidationMessages.setVisibility(View.GONE);
            btnContinuarPaso4.setEnabled(true);
            // Marcar paso 3 como completo
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit()
                .putBoolean("paso3_completo", true)
                .putLong("timestamp_paso3", System.currentTimeMillis())
                .apply();
        } else {
            tvValidationMessages.setText(String.join("\n", faltantes));
            tvValidationMessages.setVisibility(View.VISIBLE);
            btnContinuarPaso4.setEnabled(false);
        }
    }

    private void continuarAlPaso4() {
        // Rate limiting: prevenir doble click
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < 1000) {
            return;
        }
        lastClickTime = currentTime;

        if (antecedentesUri != null && medicoUri != null) {
            btnContinuarPaso4.setEnabled(false);
            startActivity(new Intent(this, PaseadorRegistroPaso4Activity.class));
            btnContinuarPaso4.setEnabled(true);
        } else {
            verificarCompletitudPaso3(); // Muestra los errores en el TextView
        }
    }

    private void saveState() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        if (antecedentesUri != null) {
            editor.putString("antecedentesUri", antecedentesUri.toString());
        } else {
            editor.remove("antecedentesUri");
        }
        if (medicoUri != null) {
            editor.putString("medicoUri", medicoUri.toString());
        } else {
            editor.remove("medicoUri");
        }
        editor.apply();
    }

    private void loadState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String antecedentesUriStr = prefs.getString("antecedentesUri", null);
        String medicoUriStr = prefs.getString("medicoUri", null);

        if (antecedentesUriStr != null) {
            try {
                antecedentesUri = Uri.parse(antecedentesUriStr);
                mostrarPreview("antecedentes", antecedentesUri);
            } catch (Exception e) {
                antecedentesUri = null;
                Toast.makeText(this, "No se pudo cargar el certificado de antecedentes.", Toast.LENGTH_SHORT).show();
            }
        }

        if (medicoUriStr != null) {
            try {
                medicoUri = Uri.parse(medicoUriStr);
                mostrarPreview("medico", medicoUri);
            } catch (Exception e) {
                medicoUri = null;
                Toast.makeText(this, "No se pudo cargar el certificado médico.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}
