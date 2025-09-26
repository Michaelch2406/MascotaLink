package com.mjc.mascotalink;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class SelfieActivity extends AppCompatActivity {

    private static final String TAG = "SelfieActivity";

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private boolean useFront = true;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera(); else finishWithCancel();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selfie);

        useFront = getIntent().getBooleanExtra("front", true);

        previewView = findViewById(R.id.previewView);
        Button btnCapturar = findViewById(R.id.btn_capturar);
        btnCapturar.setOnClickListener(v -> capturar());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(useFront ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                        .build();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error iniciando cámara", e);
                finishWithCancel();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capturar() {
        if (imageCapture == null) return;
        File outDir = new File(getCacheDir(), "captures");
        if (!outDir.exists()) outDir.mkdirs();
        String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis());
        File outFile = new File(outDir, name + ".jpg");

        ImageCapture.OutputFileOptions opts = new ImageCapture.OutputFileOptions.Builder(outFile).build();
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Intent data = new Intent();
                data.setData(Uri.fromFile(outFile));
                setResult(RESULT_OK, data);
                finish();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Error capturando imagen", exception);
                finishWithCancel();
            }
        });
    }

    private void finishWithCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }
}
