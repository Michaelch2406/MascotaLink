package com.mjc.mascotalink;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.mjc.mascotalink.upload.UploadScheduler;

/**
 * Conserva la API existente (startService con ACTION_UPLOAD) pero delega
 * todo el trabajo a WorkManager para obtener reintentos, constraints y
 * persistencia automáticos.
 */
public class FileUploadService extends Service {

    private static final String TAG = "FileUploadService";
    public static final String ACTION_UPLOAD = "com.mjc.mascotalink.ACTION_UPLOAD";
    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_FULL_STORAGE_PATH = "full_storage_path";
    public static final String EXTRA_FIRESTORE_COLLECTION = "firestore_collection";
    public static final String EXTRA_FIRESTORE_DOCUMENT = "firestore_document";
    public static final String EXTRA_FIRESTORE_FIELD = "firestore_field";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
