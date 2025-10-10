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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

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
        // Use the exact path provided by the activity
        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child(fullStoragePath);

        storageRef.putFile(fileUri)
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    notificationManager.notify(NOTIFICATION_ID, createNotification((int) progress));
                })
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    // Update the single, specific field in Firestore
                    updateFirestoreUrl(uri.toString(), firestoreCollection, firestoreDocument, firestoreField);
                    stopSelf(startId);
                }))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Upload failed", e);
                    stopSelf(startId);
                });
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
