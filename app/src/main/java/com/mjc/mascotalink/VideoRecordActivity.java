package com.mjc.mascotalink;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recording;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class VideoRecordActivity extends AppCompatActivity {

    private androidx.camera.view.PreviewView previewView;
    private ImageButton btnRecord;

    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private long startMs = 0L;

    private boolean audioPermissionGranted = false;

    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                audioPermissionGranted = Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO));
                // Try to start after permission
                startCamera();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_record);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        previewView = findViewById(R.id.preview_view);
        btnRecord = findViewById(R.id.btn_record);

        requestPermsAndStart();

        btnRecord.setOnClickListener(v -> {
            if (activeRecording == null) {
                startRecording();
            } else {
                stopRecording();
            }
        });
    }

    private void requestPermsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
        } else {
            permLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
        cameraProvider.bindToLifecycle(this, selector, preview, videoCapture);
    }

    @SuppressWarnings("MissingPermission")
    private void startRecording() {
        File file = new File(getCacheDir(), "record_" + System.currentTimeMillis() + ".mp4");
        FileOutputOptions output = new FileOutputOptions.Builder(file).build();

        PendingRecording pending = videoCapture.getOutput().prepareRecording(this, output);
        if (audioPermissionGranted) {
            pending.withAudioEnabled();
        }
        activeRecording = pending.start(ContextCompat.getMainExecutor(this), recordEvent -> {});
        startMs = System.currentTimeMillis();
        btnRecord.setSelected(true);

        // Auto-stop at 60s
        new CountDownTimer(60_000, 60_000) {
            @Override public void onTick(long l) {}
            @Override public void onFinish() { if (activeRecording != null) stopRecording(); }
        }.start();
    }

    private void stopRecording() {
        if (activeRecording == null) return;
        activeRecording.stop();
        activeRecording.close();
        activeRecording = null;
        btnRecord.setSelected(false);

        long elapsed = (System.currentTimeMillis() - startMs) / 1000L;
        if (elapsed < 30L || elapsed > 60L) {
            Toast.makeText(this, "El video debe durar entre 30 y 60 segundos", Toast.LENGTH_LONG).show();
            return;
        }

        // Return the last file created in cache directory
        // In a robust version we'd track the exact file; here we scan by lastModified
        File dir = getCacheDir();
        File latest = null;
        for (File f : dir.listFiles()) {
            if (f.getName().startsWith("record_") && f.getName().endsWith(".mp4")) {
                if (latest == null || f.lastModified() > latest.lastModified()) latest = f;
            }
        }
        if (latest != null) {
            Intent data = new Intent();
            data.setData(Uri.fromFile(latest));
            setResult(RESULT_OK, data);
            finish();
        }
    }
}
