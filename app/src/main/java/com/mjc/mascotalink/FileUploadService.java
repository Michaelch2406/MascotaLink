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
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_STORAGE_PATH = "storage_path";
    public static final String EXTRA_FIRESTORE_COLLECTION = "firestore_collection";
    public static final String EXTRA_FIRESTORE_DOCUMENT = "firestore_document";
    public static final String EXTRA_FIRESTORE_FIELD = "firestore_field";

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
            String userId = intent.getStringExtra(EXTRA_USER_ID);
            String storagePath = intent.getStringExtra(EXTRA_STORAGE_PATH);
            String firestoreCollection = intent.getStringExtra(EXTRA_FIRESTORE_COLLECTION);
            String firestoreDocument = intent.getStringExtra(EXTRA_FIRESTORE_DOCUMENT);
            String firestoreField = intent.getStringExtra(EXTRA_FIRESTORE_FIELD);

            if (fileUri != null && userId != null) {
                startForeground(NOTIFICATION_ID, createNotification(0));
                uploadFile(fileUri, userId, storagePath, firestoreCollection, firestoreDocument, firestoreField, startId);
            }
        }
        return START_NOT_STICKY;
    }

    private void uploadFile(Uri fileUri, String userId, String storagePath, String firestoreCollection, String firestoreDocument, String firestoreField, int startId) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child(storagePath + "/" + userId + "/" + System.currentTimeMillis());

        storageRef.putFile(fileUri)
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    notificationManager.notify(NOTIFICATION_ID, createNotification((int) progress));
                })
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    updateFirestoreUrl(uri.toString(), firestoreCollection, firestoreDocument, firestoreField);
                    stopSelf(startId);
                }))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Upload failed", e);
                    // Optionally, show a failure notification
                    stopSelf(startId);
                });
    }

    private void updateFirestoreUrl(String downloadUrl, String collection, String document, String field) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> nestedData = new HashMap<>();
        nestedData.put(field, downloadUrl);
        data.put("perfil_profesional", nestedData); // Assuming the field is always in perfil_profesional

        FirebaseFirestore.getInstance().collection(collection).document(document)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Firestore updated successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Firestore update failed", e));
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
