package com.mjc.mascotalink.upload;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.mjc.mascotalink.util.ImageCompressor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Worker responsable de subir uno o varios archivos a Firebase Storage
 * con compresión de imágenes y actualización del campo específico en Firestore.
 */
public class FileUploadWorker extends Worker {

    public FileUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data input = getInputData();
        String[] fileUris = input.getStringArray(UploadScheduler.keyFileUris());
        String[] storagePaths = input.getStringArray(UploadScheduler.keyStoragePaths());
        String collection = input.getString(UploadScheduler.keyCollection());
        String document = input.getString(UploadScheduler.keyDocument());
        String field = input.getString(UploadScheduler.keyField());
        boolean deleteTemp = input.getBoolean(UploadScheduler.keyDeleteTemp(), true);
        String workName = input.getString(UploadScheduler.keyWorkName());

        if (fileUris == null || storagePaths == null || collection == null || document == null || field == null) {
            return Result.failure();
        }
        if (fileUris.length == 0 || storagePaths.length == 0 || fileUris.length != storagePaths.length) {
            return Result.failure();
        }

        List<String> downloadUrls = new ArrayList<>();
        List<File> tempFiles = new ArrayList<>();

        try {
            for (int i = 0; i < fileUris.length; i++) {
                Uri originalUri = Uri.parse(fileUris[i]);
                String storagePath = storagePaths[i];

                Uri uploadUri = originalUri;
                File compressed = maybeCompressImage(originalUri);
                if (compressed != null) {
                    uploadUri = Uri.fromFile(compressed);
                    tempFiles.add(compressed);
                }

                StorageReference storageRef = FirebaseStorage.getInstance().getReference().child(storagePath);
                Tasks.await(storageRef.putFile(uploadUri));
                Uri downloadUri = Tasks.await(storageRef.getDownloadUrl());
                downloadUrls.add(downloadUri.toString());
            }

            Object updateValue = downloadUrls.size() == 1 ? downloadUrls.get(0) : downloadUrls;
            Tasks.await(
                    FirebaseFirestore.getInstance()
                            .collection(collection)
                            .document(document)
                            .update(field, updateValue)
            );

            if (workName != null) {
                UploadScheduler.markCompleted(getApplicationContext(), workName);
            }
            cleanupTemps(tempFiles, deleteTemp);
            return Result.success();
        } catch (Exception e) {
            cleanupTemps(tempFiles, deleteTemp);
            if (isNetworkError(e)) {
                return Result.retry();
            }
            // Para fallos no de red, se deja en pending store; retry permitirà reintentos manuales.
            return Result.retry();
        }
    }

    private File maybeCompressImage(Uri uri) {
        String mime = getMimeType(uri);
        if (mime != null && mime.startsWith("image/")) {
            File compressed = ImageCompressor.compressImage(getApplicationContext(), uri);
            if (compressed != null && compressed.exists()) {
                return compressed;
            }
        }
        return null;
    }

    private String getMimeType(Uri uri) {
        String mimeType = getApplicationContext().getContentResolver().getType(uri);
        if (mimeType == null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (ext != null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
            }
        }
        return mimeType;
    }

    private void cleanupTemps(List<File> files, boolean delete) {
        if (!delete) return;
        for (File f : files) {
            if (f != null && f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }
    
    private boolean isNetworkError(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof com.google.firebase.FirebaseNetworkException) return true;
            t = t.getCause();
        }
        return false;
    }
}
