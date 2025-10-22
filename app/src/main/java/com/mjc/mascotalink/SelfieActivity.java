package com.mjc.mascotalink;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class SelfieActivity extends AppCompatActivity {

    private static final String TAG = "SelfieActivity";
    private Uri photoUri;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    launchCamera();
                } else {
                    Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
                    finishWithCancel();
                }
            }
    );

    private final ActivityResultLauncher<Uri> takePictureLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success) {
                    finishWithSuccess();
                } else {
                    // Si el usuario cancela, el archivo temporal se queda. Podemos borrarlo si es necesario.
                    Log.d(TAG, "Captura de imagen cancelada por el usuario.");
                    finishWithCancel();
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No se necesita un layout complejo, la cámara nativa se encargará de la UI.
        // setContentView(R.layout.activity_selfie);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        photoUri = createTempImageUri();
        if (photoUri != null) {
            takePictureLauncher.launch(photoUri);
        } else {
            Toast.makeText(this, "No se pudo crear el archivo para la foto", Toast.LENGTH_SHORT).show();
            finishWithCancel();
        }
    }

    private Uri createTempImageUri() {
        try {
            File sharedDir = new File(getFilesDir(), "shared_files");
            if (!sharedDir.exists()) {
                sharedDir.mkdirs();
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis());
            File photoFile = new File(sharedDir, "SELFIE_" + timeStamp + ".jpg");

            return FileProvider.getUriForFile(this, com.mjc.mascotalink.BuildConfig.APPLICATION_ID + ".provider", photoFile);
        } catch (Exception e) {
            Log.e(TAG, "Error al crear el URI del archivo de imagen", e);
            return null;
        }
    }

    private void finishWithSuccess() {
        Intent data = new Intent();
        data.setData(photoUri);
        data.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(RESULT_OK, data);
        finish();
    }

    private void finishWithCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }
}
