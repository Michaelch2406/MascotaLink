package com.mjc.mascotalink;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.mjc.mascotalink.ui.camera.OverlayView;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SelfieActivity extends AppCompatActivity {

    private static final String TAG = "SelfieActivity";
    private static final float CENTER_TOLERANCE = 0.18f;
    private static final float MIN_FACE_SIZE_RATIO = 0.22f;
    private static final float MAX_FACE_SIZE_RATIO = 0.60f;
    private static final float EYE_OPEN_THRESHOLD = 0.5f;
    private static final float EYE_CLOSED_THRESHOLD = 0.35f;
    private static final float SMILE_THRESHOLD = 0.7f;
    private static final float TURN_THRESHOLD_DEG = 12f;
    private static final long STABLE_REQUIRED_MS = 1500L;
    private static final float LUMA_DARK_THRESHOLD = 40f;
    private static final float EDGE_LOW_THRESHOLD = 10f;

    private static final int CHALLENGE_SMILE = 1;
    private static final int CHALLENGE_BLINK = 2;
    private static final int CHALLENGE_TURN = 3;
    private static final long CHALLENGE_TIMEOUT_MS = 9000L;

    private PreviewView previewView;
    private OverlayView overlayView;
    private MaterialButton captureButton;
    private TextView statusText;
    private View pulseBadge;
    private ImageView challengeIcon;
    private TextView challengeTitle;
    private TextView challengeHint;
    private TextView challengeStatus;
    private TextView challengeStepText;
    private ProgressBar challengeProgress;
    private int overlayProgress = 0;

    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;

    private boolean readyToCapture = false;
    private String lastStatus = "";
    private String lastChallengeStatus = "";
    private boolean isStable = false;
    private long stableSinceMs = 0L;
    private boolean autoShotTriggered = false;

    private int[] challengeSequence = new int[2];
    private boolean[] challengeDone = new boolean[2];
    private int currentStepIndex = 0;
    private boolean challengeCompleted = false;
    private boolean livenessPassed = false;
    private long challengeStartMs = 0L;
    private boolean wasEyeOpen = true;
    private float baseYaw = 0f;
    private boolean hasBaseYaw = false;
    private boolean lastEyesOpen = false;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Permiso de camara requerido para continuar", Toast.LENGTH_LONG).show();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selfie_camera);

        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        captureButton = findViewById(R.id.btnCapture);
        statusText = findViewById(R.id.tvStatus);
        pulseBadge = findViewById(R.id.badgePulse);
        challengeIcon = findViewById(R.id.imgChallenge);
        challengeTitle = findViewById(R.id.tvChallengeTitle);
        challengeHint = findViewById(R.id.tvChallengeHint);
        challengeStatus = findViewById(R.id.tvChallengeStatus);
        challengeStepText = findViewById(R.id.tvChallengeStep);
        challengeProgress = findViewById(R.id.progressChallenge);
        ImageButton closeButton = findViewById(R.id.btnClose);

        closeButton.setOnClickListener(v -> finish());
        captureButton.setEnabled(false);
        captureButton.setOnClickListener(v -> {
            if (readyToCapture) {
                takePhoto();
            }
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        initDetector();
        initChallengeSequence();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void initDetector() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setMinFaceSize(0.2f)
                .build();
        faceDetector = FaceDetection.getClient(options);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindUseCases();
            } catch (Exception e) {
                Log.e(TAG, "No se pudo iniciar la camara", e);
                Toast.makeText(this, "No se pudo iniciar la camara", Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases() {
        if (cameraProvider == null) {
            return;
        }
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetRotation(previewView.getDisplay().getRotation())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        QualityMetrics quality = computeQuality(imageProxy);
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );
        int imageWidth = imageProxy.getWidth();
        int imageHeight = imageProxy.getHeight();

        faceDetector.process(image)
                .addOnSuccessListener(faces -> handleFaces(faces, imageWidth, imageHeight, quality))
                .addOnFailureListener(e -> Log.e(TAG, "Error procesando rostro", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void handleFaces(List<Face> faces, int imageWidth, int imageHeight, QualityMetrics quality) {
        if (faces == null || faces.isEmpty()) {
            if (!livenessPassed) {
                resetChallenge();
                updateStability(false);
                lastEyesOpen = false;
                updateUi(false, getString(R.string.selfie_status_position), false, challengeStatusText());
            }
            return;
        }
        if (faces.size() > 1) {
            if (!livenessPassed) {
                resetChallenge();
                updateStability(false);
                lastEyesOpen = false;
                updateUi(false, getString(R.string.selfie_status_one_face), false, challengeStatusText());
            }
            return;
        }

        if (livenessPassed) {
            lastEyesOpen = true;
            updateUi(true, getString(R.string.selfie_status_ready), true, getString(R.string.selfie_challenge_done));
            return;
        }

        Face face = faces.get(0);
        Rect bounds = face.getBoundingBox();
        float widthRatio = (float) bounds.width() / (float) imageWidth;
        float centerX = (float) bounds.centerX() / (float) imageWidth;
        float centerY = (float) bounds.centerY() / (float) imageHeight;

        boolean sizeOk = widthRatio >= MIN_FACE_SIZE_RATIO && widthRatio <= MAX_FACE_SIZE_RATIO;
        boolean centered = Math.abs(centerX - 0.5f) <= CENTER_TOLERANCE && Math.abs(centerY - 0.5f) <= CENTER_TOLERANCE;
        boolean eyesOpen = areEyesOpen(face);
        lastEyesOpen = eyesOpen;

        if (!sizeOk) {
            updateUi(false, widthRatio < MIN_FACE_SIZE_RATIO ? getString(R.string.selfie_status_move_closer) : getString(R.string.selfie_status_move_back), false, challengeStatusText());
            updateStability(false);
            return;
        }
        if (!centered) {
            updateUi(false, getString(R.string.selfie_status_center), false, challengeStatusText());
            updateStability(false);
            return;
        }
        if (currentStep() != CHALLENGE_BLINK && !eyesOpen) {
            updateUi(false, getString(R.string.selfie_status_open_eyes), false, challengeStatusText());
            updateStability(false);
            return;
        }

        if (quality != null) {
            if (quality.lumaMean < LUMA_DARK_THRESHOLD) {
                updateUi(false, getString(R.string.selfie_status_dark), false, challengeStatusText());
                updateStability(false);
                return;
            }
            if (quality.edgeScore < EDGE_LOW_THRESHOLD) {
                updateUi(false, getString(R.string.selfie_status_blurry), false, challengeStatusText());
                updateStability(false);
                return;
            }
        }

        boolean stableOk = updateStability(true);
        String statusMsg = stableOk ? getString(R.string.selfie_status_ready) : getString(R.string.selfie_status_hold_still);

        boolean challengeOk = stableOk && evaluateChallenge(face);
        updateUi(stableOk, statusMsg, challengeOk, challengeStatusText());
    }

    private boolean areEyesOpen(Face face) {
        Float left = face.getLeftEyeOpenProbability();
        Float right = face.getRightEyeOpenProbability();
        if (left == null || right == null) {
            return false;
        }
        return left >= EYE_OPEN_THRESHOLD && right >= EYE_OPEN_THRESHOLD;
    }

    private void updateUi(boolean faceReady, String message, boolean challengeOk, String challengeMsg) {
        boolean overallReady = faceReady && challengeOk;
        if (readyToCapture == overallReady && message.equals(lastStatus) && challengeMsg.equals(lastChallengeStatus)) {
            return;
        }
        readyToCapture = overallReady;
        lastStatus = message;
        lastChallengeStatus = challengeMsg;
        overlayProgress = computeOverlayProgress(faceReady, challengeOk);

        runOnUiThread(() -> {
            String uiMessage = (faceReady && !challengeOk) ? challengeMsg : message;
            captureButton.setEnabled(overallReady);
            statusText.setText(uiMessage);
            int faceColor = ContextCompat.getColor(this,
                    overallReady ? R.color.green_success : (faceReady ? R.color.blue_primary : R.color.red_error));
            
            // Mantener texto blanco para contraste, colorear el badge
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            if (pulseBadge.getBackground() != null) {
                pulseBadge.getBackground().setTint(faceColor);
            }

            // Actualizar borde del botón de captura
            captureButton.setStrokeColor(android.content.res.ColorStateList.valueOf(
                    overallReady ? faceColor : ContextCompat.getColor(this, R.color.gray_light)
            ));

            pulseBadge.setVisibility(overallReady ? View.VISIBLE : View.VISIBLE); // Siempre visible para indicar estado
            overlayView.setReady(overallReady);
            overlayView.setProgress(overlayProgress);
            challengeStatus.setText(challengeMsg);
            int statusColor = ContextCompat.getColor(this,
                    challengeOk ? R.color.green_success : R.color.colorPrimary);
            challengeStatus.setTextColor(statusColor);
            challengeIcon.setColorFilter(statusColor);
            challengeProgress.setProgress(challengeOk ? challengeProgress.getMax() : computeProgress());

            if (overallReady && lastEyesOpen && !autoShotTriggered) {
                autoShotTriggered = true;
                takePhoto();
            }
        });
    }

    private void takePhoto() {
        if (imageCapture == null) {
            return;
        }
        captureButton.setEnabled(false);
        statusText.setText(getString(R.string.selfie_capturing));

        File photoFile;
        try {
            photoFile = createSelfieFile();
        } catch (IOException e) {
            Log.e(TAG, "No se pudo crear el archivo de selfie", e);
            Toast.makeText(this, "No se pudo guardar la foto", Toast.LENGTH_LONG).show();
            captureButton.setEnabled(true);
            return;
        }

        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(options, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Uri resultUri = FileProvider.getUriForFile(
                        SelfieActivity.this,
                        BuildConfig.APPLICATION_ID + ".provider",
                        photoFile
                );
                runOnUiThread(() -> finishWithSuccess(resultUri));
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Error capturando selfie", exception);
                runOnUiThread(() -> {
                    Toast.makeText(SelfieActivity.this, "Vuelve a intentarlo", Toast.LENGTH_LONG).show();
                    captureButton.setEnabled(true);
                });
            }
        });
    }

    private void initChallengeSequence() {
        // Secuencia clara: primero sonrisa, luego parpadeo
        challengeSequence = new int[]{CHALLENGE_SMILE, CHALLENGE_BLINK};
        challengeDone = new boolean[challengeSequence.length];
        currentStepIndex = 0;
        challengeCompleted = false;
        hasBaseYaw = false;
        wasEyeOpen = true;
        challengeStartMs = SystemClock.elapsedRealtime();
        challengeProgress.setProgress(0);
        updateChallengeLabels();
    }

    private boolean evaluateChallenge(Face face) {
        long now = SystemClock.elapsedRealtime();
        if (challengeStartMs == 0L) {
            challengeStartMs = now;
        }

        if (!challengeCompleted && now - challengeStartMs > CHALLENGE_TIMEOUT_MS) {
            Log.d(TAG, "Challenge timeout, avanzando");
            advanceChallenge();
            return false;
        }

        boolean stepCompleted = detectCurrentStep(face);
        if (stepCompleted) {
            challengeDone[currentStepIndex] = true;
            if (currentStepIndex == challengeSequence.length - 1) {
                challengeCompleted = true;
                livenessPassed = true;
                Log.d(TAG, "Secuencia de reto completada");
            } else {
                currentStepIndex++;
                challengeStartMs = SystemClock.elapsedRealtime();
                wasEyeOpen = true;
                hasBaseYaw = false;
                updateChallengeLabels();
            }
        } else {
            updateChallengeLabels();
        }
        return challengeCompleted;
    }

    private boolean detectCurrentStep(Face face) {
        int step = currentStep();
        if (step == CHALLENGE_SMILE) {
            return detectSmile(face);
        } else if (step == CHALLENGE_BLINK) {
            return detectBlink(face);
        } else {
            return detectTurn(face);
        }
    }

    private boolean detectSmile(Face face) {
        Float smileProb = face.getSmilingProbability();
        return smileProb != null && smileProb >= SMILE_THRESHOLD;
    }

    private boolean detectBlink(Face face) {
        Float left = face.getLeftEyeOpenProbability();
        Float right = face.getRightEyeOpenProbability();
        if (left == null && right == null) {
            return false;
        }
        boolean openNow = (left != null && left >= EYE_OPEN_THRESHOLD) || (right != null && right >= EYE_OPEN_THRESHOLD);
        boolean closedNow = (left != null && left <= EYE_CLOSED_THRESHOLD) || (right != null && right <= EYE_CLOSED_THRESHOLD);
        if (openNow) {
            wasEyeOpen = true;
        }
        if (wasEyeOpen && closedNow) {
            wasEyeOpen = false;
            return true;
        }
        return false;
    }

    private boolean detectTurn(Face face) {
        float yaw = face.getHeadEulerAngleY();
        if (!hasBaseYaw) {
            baseYaw = yaw;
            hasBaseYaw = true;
            return false;
        }
        return Math.abs(yaw - baseYaw) >= TURN_THRESHOLD_DEG;
    }

    private void advanceChallenge() {
        if (currentStepIndex < challengeSequence.length - 1) {
            currentStepIndex++;
        } else {
            initChallengeSequence();
            return;
        }
        challengeStartMs = SystemClock.elapsedRealtime();
        wasEyeOpen = true;
        hasBaseYaw = false;
        Arrays.fill(challengeDone, false);
        updateChallengeLabels();
    }

    private void resetChallenge() {
        Arrays.fill(challengeDone, false);
        currentStepIndex = 0;
        challengeCompleted = false;
        livenessPassed = false;
        wasEyeOpen = true;
        hasBaseYaw = false;
        challengeStartMs = SystemClock.elapsedRealtime();
        autoShotTriggered = false;
        runOnUiThread(() -> {
            challengeProgress.setProgress(0);
            updateChallengeLabels();
        });
    }

    private void updateChallengeLabels() {
        runOnUiThread(() -> {
            int step = currentStep();
            String stepLabel = getString(R.string.selfie_challenge_step, currentStepIndex + 1, challengeSequence.length);
            challengeStepText.setText(stepLabel);

            if (step == CHALLENGE_SMILE) {
                challengeTitle.setText(getString(R.string.selfie_challenge_smile_title));
                challengeHint.setText(getString(R.string.selfie_challenge_smile_hint));
                challengeIcon.setImageResource(android.R.drawable.ic_menu_edit);
            } else if (step == CHALLENGE_BLINK) {
                challengeTitle.setText(getString(R.string.selfie_challenge_blink_title));
                challengeHint.setText(getString(R.string.selfie_challenge_blink_hint));
                challengeIcon.setImageResource(android.R.drawable.ic_lock_idle_alarm);
            } else {
                challengeTitle.setText(getString(R.string.selfie_challenge_turn_title));
                challengeHint.setText(getString(R.string.selfie_challenge_turn_hint));
                challengeIcon.setImageResource(android.R.drawable.ic_menu_rotate);
            }
            String status = challengeStatusText();
            int color = ContextCompat.getColor(this, challengeCompleted ? R.color.green_success : R.color.gray_light);
            challengeStatus.setText(status);
            challengeStatus.setTextColor(color);
            challengeIcon.setColorFilter(color);
        });
    }

    private boolean updateStability(boolean aligned) {
        long now = SystemClock.elapsedRealtime();
        if (!aligned) {
            stableSinceMs = 0L;
            isStable = false;
            return false;
        }
        if (stableSinceMs == 0L) {
            stableSinceMs = now;
        }
        isStable = (now - stableSinceMs) >= STABLE_REQUIRED_MS;
        return isStable;
    }

    private int computeProgress() {
        if (challengeStartMs == 0L) {
            return 0;
        }
        long elapsed = SystemClock.elapsedRealtime() - challengeStartMs;
        int progress = (int) Math.min(100, (elapsed * 100) / CHALLENGE_TIMEOUT_MS);
        return Math.max(0, progress);
    }

    private int computeOverlayProgress(boolean faceReady, boolean challengeOk) {
        int faceScore = faceReady ? 40 : 20;
        int stabilityScore = isStable ? 20 : 0;
        int stepScore;
        if (challengeOk) {
            stepScore = 40;
        } else {
            int completed = completedSteps();
            stepScore = (int) ((completed / (float) challengeSequence.length) * 40);
            stepScore = Math.max(stepScore, (int) (computeProgress() * 0.4f));
        }
        int total = faceScore + stabilityScore + stepScore;
        return Math.max(0, Math.min(100, total));
    }

    private String challengeStatusText() {
        long elapsed = challengeStartMs == 0L ? 0L : SystemClock.elapsedRealtime() - challengeStartMs;
        if (!challengeCompleted && elapsed > (CHALLENGE_TIMEOUT_MS * 0.7)) {
            return getString(R.string.selfie_challenge_timeout);
        }
        if (challengeCompleted) {
            return getString(R.string.selfie_challenge_done);
        }
        int step = currentStep();
        if (step == CHALLENGE_SMILE) {
            return getString(R.string.selfie_challenge_smile_hint);
        } else if (step == CHALLENGE_BLINK) {
            return getString(R.string.selfie_challenge_blink_hint);
        } else {
            return getString(R.string.selfie_challenge_turn_hint);
        }
    }

    private int currentStep() {
        return challengeSequence[Math.min(currentStepIndex, challengeSequence.length - 1)];
    }

    private int completedSteps() {
        int count = 0;
        for (boolean done : challengeDone) {
            if (done) {
                count++;
            }
        }
        return count;
    }

    private File createSelfieFile() throws IOException {
        String customDir = getIntent().getStringExtra("selfieDirName");
        String dirName = customDir != null ? customDir : "selfie_session";
        File dir = new File(getFilesDir(), "selfie/" + dirName);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("No se pudo crear directorio para selfies");
        }
        File target = new File(dir, "selfie.jpg");
        if (target.exists() && !target.delete()) {
            throw new IOException("No se pudo reemplazar selfie anterior");
        }
        if (!target.createNewFile()) {
            throw new IOException("No se pudo crear archivo selfie");
        }
        return target;
    }

    private void finishWithSuccess(Uri uri) {
        Intent data = new Intent();
        data.setData(uri);
        data.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (faceDetector != null) {
            faceDetector.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        autoShotTriggered = false;
    }

    private QualityMetrics computeQuality(ImageProxy proxy) {
        try {
            ImageProxy.PlaneProxy[] planes = proxy.getPlanes();
            if (planes == null || planes.length == 0) {
                return null;
            }
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int width = proxy.getWidth();
            int height = proxy.getHeight();
            int step = 16;
            double sum = 0;
            int count = 0;
            double gradient = 0;
            int gradCount = 0;

            for (int y = 0; y < height; y += step) {
                int rowOffset = y * rowStride;
                for (int x = 0; x < width; x += step) {
                    int index = rowOffset + x;
                    if (index >= buffer.limit()) {
                        continue;
                    }
                    int luma = buffer.get(index) & 0xFF;
                    sum += luma;
                    count++;
                    if (x + step < width) {
                        int idxNext = rowOffset + x + step;
                        if (idxNext < buffer.limit()) {
                            int lumaNext = buffer.get(idxNext) & 0xFF;
                            gradient += Math.abs(luma - lumaNext);
                            gradCount++;
                        }
                    }
                }
            }
            if (count == 0) {
                return null;
            }
            QualityMetrics qm = new QualityMetrics();
            qm.lumaMean = (float) (sum / count);
            qm.edgeScore = gradCount == 0 ? 0f : (float) (gradient / gradCount);
            return qm;
        } catch (Exception e) {
            Log.e(TAG, "Error calculando métricas de calidad", e);
            return null;
        }
    }

    private static class QualityMetrics {
        float lumaMean;
        float edgeScore;
    }
}
