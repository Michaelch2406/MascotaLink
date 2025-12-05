package com.mjc.mascotalink;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.mjc.mascotalink.util.ImageCompressor;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class FileUploadService extends Service {

    private static final String TAG = "FileUploadService";
    public static final String ACTION_UPLOAD = "com.mjc.mascotalink.ACTION_UPLOAD";
    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_FULL_STORAGE_PATH = "full_storage_path"; // Changed from storage_path + user_id
    public static final String EXTRA_FIRESTORE_COLLECTION = "firestore_collection";
    public static final String EXTRA_FIRESTORE_DOCUMENT = "firestore_document";
    public static final String EXTRA_FIRESTORE_FIELD = "firestore_field"; // The exact field to update, e.g., "perfil_profesional.video_presentacion_url"

    private static final String CHANNEL_ID = "FileUploadChannel";
    private static final int NOTIFICATION_ID = 1;

    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_UPLOAD.equals(intent.getAction())) {
            Uri fileUri = intent.getParcelableExtra(EXTRA_FILE_URI);
            String fullStoragePath = intent.getStringExtra(EXTRA_FULL_STORAGE_PATH);
            String firestoreCollection = intent.getStringExtra(EXTRA_FIRESTORE_COLLECTION);
            String firestoreDocument = intent.getStringExtra(EXTRA_FIRESTORE_DOCUMENT);
            String firestoreField = intent.getStringExtra(EXTRA_FIRESTORE_FIELD);

            if (fileUri != null && fullStoragePath != null && firestoreCollection != null && firestoreDocument != null && firestoreField != null) {
                startForeground(NOTIFICATION_ID, createNotification(0));
                uploadFile(fileUri, fullStoragePath, firestoreCollection, firestoreDocument, firestoreField, startId);
            }
        }
        return START_NOT_STICKY;
    }

    private void uploadFile(Uri fileUri, String fullStoragePath, String firestoreCollection, String firestoreDocument, String firestoreField, int startId) {
        // Detectar tipo de archivo
        String mimeType = getMimeType(fileUri);
        boolean isImage = mimeType != null && mimeType.startsWith("image/");

        Log.d(TAG, "Subiendo archivo - Tipo: " + mimeType + ", Es imagen: " + isImage);

        // Si es imagen, comprimirla primero
        if (isImage) {
            uploadImageWithCompression(fileUri, fullStoragePath, firestoreCollection, firestoreDocument, firestoreField, startId);
        } else {
            // Para videos y otros archivos, subir directamente
            uploadFileDirect(fileUri, fullStoragePath, firestoreCollection, firestoreDocument, firestoreField, startId, null);
        }
    }

    /**
     * Sube una imagen con compresión automática
     */
    private void uploadImageWithCompression(Uri imageUri, String fullStoragePath, String firestoreCollection,
                                           String firestoreDocument, String firestoreField, int startId) {
        Log.d(TAG, "Comprimiendo imagen antes de subir...");

        // Comprimir imagen en segundo plano
        new Thread(() -> {
            File compressedFile = ImageCompressor.compressImage(this, imageUri);

            if (compressedFile != null && compressedFile.exists()) {
                Uri compressedUri = Uri.fromFile(compressedFile);
                long originalSize = new File(imageUri.getPath() != null ? imageUri.getPath() : "").length();
                long compressedSize = compressedFile.length();

                Log.d(TAG, String.format("✅ Imagen comprimida: %.1f KB -> %.1f KB (%.0f%% reducción)",
                        originalSize / 1024f,
                        compressedSize / 1024f,
                        (1 - (compressedSize / (float) originalSize)) * 100
                ));

                // Subir imagen comprimida
                uploadFileDirect(compressedUri, fullStoragePath, firestoreCollection, firestoreDocument, firestoreField, startId, compressedFile);
            } else {
                Log.w(TAG, "Compresión falló, subiendo imagen original");
                uploadFileDirect(imageUri, fullStoragePath, firestoreCollection, firestoreDocument, firestoreField, startId, null);
            }
        }).start();
    }

    /**
     * Sube archivo directamente a Firebase Storage
     */
    private void uploadFileDirect(Uri fileUri, String fullStoragePath, String firestoreCollection,
                                 String firestoreDocument, String firestoreField, int startId, File tempFileToDelete) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child(fullStoragePath);

        storageRef.putFile(fileUri)
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    notificationManager.notify(NOTIFICATION_ID, createNotification((int) progress));
                })
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    // Update the single, specific field in Firestore
                    updateFirestoreUrl(uri.toString(), firestoreCollection, firestoreDocument, firestoreField);

                    // Limpiar archivo temporal si existe
                    cleanupTempFile(tempFileToDelete);

                    stopSelf(startId);
                }))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Upload failed", e);

                    // Limpiar archivo temporal si existe
                    cleanupTempFile(tempFileToDelete);

                    stopSelf(startId);
                });
    }

    /**
     * Obtiene el MIME type de un archivo
     */
    private String getMimeType(Uri uri) {
        String mimeType = null;

        // Intentar obtener desde ContentResolver
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            mimeType = getContentResolver().getType(uri);
        }

        // Fallback: obtener desde extensión de archivo
        if (mimeType == null) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (extension != null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            }
        }

        return mimeType;
    }

    /**
     * Limpia archivos temporales de compresión
     */
    private void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            boolean deleted = tempFile.delete();
            Log.d(TAG, "Archivo temporal " + (deleted ? "eliminado" : "no pudo ser eliminado"));
        }
    }

    private void updateFirestoreUrl(String downloadUrl, String collection, String document, String field) {
        // This is now a simple, safe update of a single field.
        FirebaseFirestore.getInstance().collection(collection).document(document)
                .update(field, downloadUrl)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Firestore updated successfully with video URL."))
                .addOnFailureListener(e -> Log.e(TAG, "Firestore update with video URL failed", e));
    }

    private Notification createNotification(int progress) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Subiendo video")
                .setContentText(progress + "%")
                .setSmallIcon(R.drawable.ic_walk) // Replace with your app icon
                .setOngoing(true)
                .setProgress(100, progress, false)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "File Upload Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(serviceChannel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
