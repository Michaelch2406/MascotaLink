package com.mjc.mascotalink;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class FileStorageHelper {

    private static final String TAG = "FileStorageHelper";
    private static final String SHARED_DIR = "shared_files";

    /**
     * Creates a new file in the app's private storage and returns a content URI for it.
     *
     * @param context The context.
     * @param prefix  A prefix for the filename (e.g., "IMG_").
     * @param extension The file extension (e.g., ".jpg").
     * @return A content URI for the new file, or null on error.
     */
    public static Uri createNewFileUri(Context context, String prefix, String extension) {
        try {
            File sharedDir = new File(context.getFilesDir(), SHARED_DIR);
            if (!sharedDir.exists()) {
                if (!sharedDir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory: " + sharedDir.getAbsolutePath());
                    return null;
                }
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis());
            String fileName = prefix + timeStamp + extension;
            File file = new File(sharedDir, fileName);

            // Return the content URI using the FileProvider
            return FileProvider.getUriForFile(context, com.mjc.mascotalink.BuildConfig.APPLICATION_ID + ".provider", file);
        } catch (Exception e) {
            Log.e(TAG, "Error creating new file URI", e);
            return null;
        }
    }

    /**
     * Copies a file from a given content URI to the app's private storage.
     *
     * @param context The context.
     * @param sourceUri The content URI of the file to copy.
     * @param prefix A prefix for the new filename.
     * @return A content URI for the copied file, or null on error.
     */
    public static Uri copyFileToInternalStorage(Context context, Uri sourceUri, String prefix) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri)) {
            if (inputStream == null) {
                Log.e(TAG, "Failed to get InputStream for URI: " + sourceUri);
                return null;
            }

            String extension = getExtensionFromUri(context, sourceUri);
            File sharedDir = new File(context.getFilesDir(), SHARED_DIR);
            if (!sharedDir.exists()) {
                if (!sharedDir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory: " + sharedDir.getAbsolutePath());
                    return null;
                }
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis());
            String fileName = prefix + timeStamp + extension;
            File destinationFile = new File(sharedDir, fileName);

            try (OutputStream outputStream = new FileOutputStream(destinationFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }

            // Return the content URI for the new file
            return FileProvider.getUriForFile(context, com.mjc.mascotalink.BuildConfig.APPLICATION_ID + ".provider", destinationFile);

        } catch (IOException e) {
            Log.e(TAG, "Error copying file from URI: " + sourceUri, e);
            return null;
        }
    }

    private static String getExtensionFromUri(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType != null) {
            if (mimeType.equals("image/jpeg")) return ".jpg";
            if (mimeType.equals("image/png")) return ".png";
            if (mimeType.equals("video/mp4")) return ".mp4";
            if (mimeType.equals("application/pdf")) return ".pdf";
        }
        // Fallback to getting extension from path
        String path = uri.getPath();
        if (path != null) {
            int lastDot = path.lastIndexOf('.');
            if (lastDot >= 0) {
                return path.substring(lastDot);
            }
        }
        return ".dat"; // Default extension
    }
}
