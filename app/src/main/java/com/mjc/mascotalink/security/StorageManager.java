package com.mjc.mascotalink.security;

import android.util.Log;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class StorageManager {

    private static final String TAG = "StorageManager";
    private final FirebaseStorage storage;

    public StorageManager() {
        this.storage = FirebaseStorage.getInstance();
    }

    public void getTemporaryDownloadUrl(String filePath, OnUrlReady callback) {
        if (callback == null) {
            Log.w(TAG, "getTemporaryDownloadUrl: callback is null");
            return;
        }

        if (filePath == null || filePath.isEmpty()) {
            callback.onError("Ruta inválida");
            return;
        }

        StorageReference ref = storage.getReference().child(filePath);
        ref.getDownloadUrl()
                .addOnSuccessListener(uri -> callback.onUrlReady(uri.toString()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getTemporaryDownloadUrl", e);
                    callback.onError(e.getMessage());
                });
    }

    public void uploadUserData(String userId, String fileName, byte[] data, OnUploadComplete callback) {
        if (callback == null) {
            return;
        }

        if (userId == null || userId.isEmpty()) {
            callback.onError("Usuario no autenticado");
            return;
        }

        if (fileName == null || fileName.isEmpty()) {
            callback.onError("Archivo inválido");
            return;
        }

        if (data == null || data.length == 0) {
            callback.onError("Datos vacíos");
            return;
        }

        String path = "usuarios/" + userId + "/datos/" + fileName;
        StorageReference ref = storage.getReference().child(path);

        ref.putBytes(data)
                .addOnSuccessListener(taskSnapshot -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "uploadUserData failed", e);
                    callback.onError(e.getMessage());
                });
    }

    public interface OnUrlReady {
        void onUrlReady(String url);
        void onError(String error);
    }

    public interface OnUploadComplete {
        void onSuccess();
        void onError(String error);
    }
}
