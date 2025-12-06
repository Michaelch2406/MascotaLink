package com.mjc.mascotalink.upload;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Centraliza la encolado de subidas en WorkManager con backoff, restricciones
 * de red/batería y persistencia ligera para reintentos explícitos.
 */
public final class UploadScheduler {

    private static final String TAG = "UploadScheduler";
    private static final String STORE_NAME = "upload_queue_store";
    private static final String KEY_WORK_NAME = "work_name";
    private static final String KEY_FILE_URIS = "file_uris";
    private static final String KEY_STORAGE_PATHS = "storage_paths";
    private static final String KEY_COLLECTION = "firestore_collection";
    private static final String KEY_DOCUMENT = "firestore_document";
    private static final String KEY_FIELD = "firestore_field";
    private static final String KEY_DELETE_TEMP = "delete_temp";
    public static final String TAG_FILE_UPLOAD = "FILE_UPLOAD";

    private UploadScheduler() {
    }

    public static void enqueueSingle(@NonNull Context context,
                                     @NonNull Uri fileUri,
                                     @NonNull String fullStoragePath,
                                     @NonNull String collection,
                                     @NonNull String document,
                                     @NonNull String field) {
        List<Uri> uris = new ArrayList<>();
        uris.add(fileUri);
        List<String> paths = new ArrayList<>();
        paths.add(fullStoragePath);
        enqueueBatch(context, uris, paths, collection, document, field);
    }

    public static void enqueueBatch(@NonNull Context context,
                                    @NonNull List<Uri> fileUris,
                                    @NonNull List<String> storagePaths,
                                    @NonNull String collection,
                                    @NonNull String document,
                                    @NonNull String field) {
        if (fileUris.isEmpty() || storagePaths.isEmpty() || fileUris.size() != storagePaths.size()) {
            Log.w(TAG, "Payload inválido para subir: tamaños no coinciden");
            return;
        }

        String workName = buildWorkName(storagePaths, document, field);
        Data input = buildInputData(workName, fileUris, storagePaths, collection, document, field);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FileUploadWorker.class)
                .setConstraints(defaultConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(TAG_FILE_UPLOAD)
                .addTag(workName)
                .setInputData(input)
                .build();

        persistPayload(context, workName, input);
        WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request);
        Log.d(TAG, "Upload encolado en WorkManager: " + workName + " (" + fileUris.size() + " archivo/s)");
    }

    public static void retryPendingUploads(@NonNull Context context) {
        Map<String, Data> pending = readPersistedPayloads(context);
        if (pending.isEmpty()) return;

        for (Map.Entry<String, Data> entry : pending.entrySet()) {
            String workName = entry.getKey();
            Data input = entry.getValue();
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FileUploadWorker.class)
                    .setConstraints(defaultConstraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .addTag(TAG_FILE_UPLOAD)
                    .addTag(workName)
                    .setInputData(input)
                    .build();
            WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request);
            Log.d(TAG, "Reintentando upload pendiente: " + workName);
        }
    }

    static void markCompleted(@NonNull Context context, @NonNull String workName) {
        context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(workName)
                .apply();
    }

    private static Data buildInputData(String workName,
                                       List<Uri> fileUris,
                                       List<String> storagePaths,
                                       String collection,
                                       String document,
                                       String field) {
        Data.Builder builder = new Data.Builder();
        builder.putString(KEY_WORK_NAME, workName);
        builder.putStringArray(KEY_FILE_URIS, toStringArray(fileUris));
        builder.putStringArray(KEY_STORAGE_PATHS, storagePaths.toArray(new String[0]));
        builder.putString(KEY_COLLECTION, collection);
        builder.putString(KEY_DOCUMENT, document);
        builder.putString(KEY_FIELD, field);
        builder.putBoolean(KEY_DELETE_TEMP, true);
        return builder.build();
    }

    private static Constraints defaultConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
    }

    private static String[] toStringArray(List<Uri> uris) {
        String[] arr = new String[uris.size()];
        for (int i = 0; i < uris.size(); i++) {
            arr[i] = uris.get(i).toString();
        }
        return arr;
    }

    private static void persistPayload(Context context, String workName, Data data) {
        try {
            JSONObject json = new JSONObject();
            json.put(KEY_COLLECTION, data.getString(KEY_COLLECTION));
            json.put(KEY_DOCUMENT, data.getString(KEY_DOCUMENT));
            json.put(KEY_FIELD, data.getString(KEY_FIELD));
            json.put(KEY_DELETE_TEMP, data.getBoolean(KEY_DELETE_TEMP, true));
            json.put(KEY_WORK_NAME, data.getString(KEY_WORK_NAME));

            JSONArray files = new JSONArray();
            for (String s : data.getStringArray(KEY_FILE_URIS)) {
                files.put(s);
            }
            JSONArray paths = new JSONArray();
            for (String s : data.getStringArray(KEY_STORAGE_PATHS)) {
                paths.put(s);
            }
            json.put(KEY_FILE_URIS, files);
            json.put(KEY_STORAGE_PATHS, paths);

            context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(workName, json.toString())
                    .apply();
        } catch (Exception e) {
            Log.w(TAG, "No se pudo persistir payload de upload: " + e.getMessage());
        }
    }

    private static Map<String, Data> readPersistedPayloads(Context context) {
        Map<String, ?> all = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE).getAll();
        Map<String, Data> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            Object val = entry.getValue();
            if (!(val instanceof String)) continue;
            Data parsed = parseData((String) val);
            if (parsed != null) {
                result.put(entry.getKey(), parsed);
            }
        }
        return result;
    }

    private static Data parseData(String raw) {
        try {
            JSONObject json = new JSONObject(raw);
            JSONArray fileArr = json.optJSONArray(KEY_FILE_URIS);
            JSONArray pathArr = json.optJSONArray(KEY_STORAGE_PATHS);
            if (fileArr == null || pathArr == null) return null;

            List<String> files = new ArrayList<>();
            for (int i = 0; i < fileArr.length(); i++) {
                files.add(fileArr.getString(i));
            }
            List<String> paths = new ArrayList<>();
            for (int i = 0; i < pathArr.length(); i++) {
                paths.add(pathArr.getString(i));
            }

            Data.Builder builder = new Data.Builder();
            builder.putStringArray(KEY_FILE_URIS, files.toArray(new String[0]));
            builder.putStringArray(KEY_STORAGE_PATHS, paths.toArray(new String[0]));
            builder.putString(KEY_COLLECTION, json.optString(KEY_COLLECTION));
            builder.putString(KEY_DOCUMENT, json.optString(KEY_DOCUMENT));
            builder.putString(KEY_FIELD, json.optString(KEY_FIELD));
            builder.putBoolean(KEY_DELETE_TEMP, json.optBoolean(KEY_DELETE_TEMP, true));
            builder.putString(KEY_WORK_NAME, json.optString(KEY_WORK_NAME));
            return builder.build();
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildWorkName(List<String> storagePaths, String document, String field) {
        String first = storagePaths.get(0);
        String hash = Integer.toHexString(TextUtils.join(",", storagePaths).hashCode());
        return "upload_" + document + "_" + field + "_" + hash + "_" + first.hashCode();
    }

    static String keyFileUris() {
        return KEY_FILE_URIS;
    }

    static String keyStoragePaths() {
        return KEY_STORAGE_PATHS;
    }

    static String keyCollection() {
        return KEY_COLLECTION;
    }

    static String keyDocument() {
        return KEY_DOCUMENT;
    }

    static String keyField() {
        return KEY_FIELD;
    }

    static String keyDeleteTemp() {
        return KEY_DELETE_TEMP;
    }

    static String keyWorkName() {
        return KEY_WORK_NAME;
    }
}
