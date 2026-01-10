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

import com.mjc.mascotalink.upload.UploadScheduler;

/**
 * Conserva la API existente (startService con ACTION_UPLOAD) pero delega
 * todo el trabajo a WorkManager para obtener reintentos, constraints y
 * persistencia automáticos.
 */
public class FileUploadService extends Service {

    private static final String TAG = "FileUploadService";
    private static final String CHANNEL_ID = "file_upload_channel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_UPLOAD = "com.mjc.mascotalink.ACTION_UPLOAD";
    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_FULL_STORAGE_PATH = "full_storage_path";
    public static final String EXTRA_FIRESTORE_COLLECTION = "firestore_collection";
    public static final String EXTRA_FIRESTORE_DOCUMENT = "firestore_document";
    public static final String EXTRA_FIRESTORE_FIELD = "firestore_field";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Subiendo archivo..."));

        if (intent != null && ACTION_UPLOAD.equals(intent.getAction())) {
            Uri fileUri = intent.getParcelableExtra(EXTRA_FILE_URI);
            String fullStoragePath = intent.getStringExtra(EXTRA_FULL_STORAGE_PATH);
            String firestoreCollection = intent.getStringExtra(EXTRA_FIRESTORE_COLLECTION);
            String firestoreDocument = intent.getStringExtra(EXTRA_FIRESTORE_DOCUMENT);
            String firestoreField = intent.getStringExtra(EXTRA_FIRESTORE_FIELD);

            if (fileUri != null && fullStoragePath != null && firestoreCollection != null
                    && firestoreDocument != null && firestoreField != null) {
                UploadScheduler.enqueueSingle(this, fileUri, fullStoragePath,
                        firestoreCollection, firestoreDocument, firestoreField);
                Log.d(TAG, "Upload encolado en WorkManager");
            } else {
                Log.w(TAG, "Extras incompletos, no se encola upload");
            }
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Subida de archivos",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notificaciones de subida de archivos a Firebase Storage");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String title) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText("Subiendo archivo a la nube...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setProgress(0, 0, true)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
