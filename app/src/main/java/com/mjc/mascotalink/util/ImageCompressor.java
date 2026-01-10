package com.mjc.mascotalink.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Utilidad para comprimir imágenes antes de subirlas.
 * Reduce el tamaño manteniendo calidad visual aceptable.
 */
public class ImageCompressor {

    private static final Executor backgroundExecutor = Executors.newSingleThreadExecutor();
    
    private static final String TAG = "ImageCompressor";
    private static final int MAX_WIDTH = 1024;
    private static final int MAX_HEIGHT = 1024;
    private static final int QUALITY = 80; // 0-100
    private static final long MAX_SIZE_BYTES = 1024 * 1024; // 1MB

    /**
     * Callback para compresión asíncrona
     */
    public interface CompressionCallback {
        void onSuccess(File compressedFile);
        void onError(Exception error);
    }

    /**
     * Comprime una imagen de forma asíncrona en background thread.
     * RECOMENDADO: Usar este método en lugar de compressImage() para evitar bloquear UI.
     *
     * @param context Contexto de la aplicación
     * @param imageUri URI de la imagen original
     * @param callback Callback con resultado
     */
    public static void compressImageAsync(Context context, Uri imageUri, CompressionCallback callback) {
        backgroundExecutor.execute(() -> {
            try {
                File result = compressImage(context, imageUri);
                if (result != null) {
                    callback.onSuccess(result);
                } else {
                    callback.onError(new IOException("Compresión falló"));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * Comprime una imagen desde una URI (método síncrono).
     * NOTA: Este método bloquea el thread actual. Usar compressImageAsync() para UI thread.
     *
     * @param context Contexto de la aplicación
     * @param imageUri URI de la imagen original
     * @return File con la imagen comprimida, o null si falla
     */
    public static File compressImage(Context context, Uri imageUri) {
        try {
            // Leer la imagen original
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                Log.e(TAG, "No se pudo abrir el stream de la imagen");
                return null;
            }
            
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            
            if (originalBitmap == null) {
                Log.e(TAG, "No se pudo decodificar la imagen");
                return null;
            }
            
            // Corregir orientación si es necesario
            Bitmap rotatedBitmap = correctOrientation(context, imageUri, originalBitmap);

            // Redimensionar si es necesario
            Bitmap resizedBitmap = resizeBitmap(rotatedBitmap, MAX_WIDTH, MAX_HEIGHT);

            // Añadir fondo blanco si la imagen tiene transparencia
            Bitmap finalBitmap = addWhiteBackground(resizedBitmap);

            // Comprimir a JPEG
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int currentQuality = QUALITY;

            // Comprimir iterativamente hasta que sea menor a MAX_SIZE_BYTES
            do {
                outputStream.reset();
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream);
                currentQuality -= 5;
            } while (outputStream.size() > MAX_SIZE_BYTES && currentQuality > 50);

            byte[] compressedData = outputStream.toByteArray();
            outputStream.close();

            // Guardar en archivo temporal
            File tempFile = new File(context.getCacheDir(), "compressed_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(compressedData);
            fos.close();

            // Liberar memoria
            if (rotatedBitmap != originalBitmap) {
                originalBitmap.recycle();
            }
            resizedBitmap.recycle();
            if (finalBitmap != resizedBitmap) {
                finalBitmap.recycle();
            }
            
            Log.d(TAG, "Imagen comprimida: " + (compressedData.length / 1024) + " KB");
            return tempFile;
            
        } catch (IOException e) {
            Log.e(TAG, "Error comprimiendo imagen", e);
            return null;
        }
    }
    
    /**
     * Redimensiona un bitmap manteniendo la proporción.
     */
    private static Bitmap resizeBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap;
        }
        
        float ratio = Math.min(
            (float) maxWidth / width,
            (float) maxHeight / height
        );
        
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }
    
    /**
     * Corrige la orientación de la imagen según EXIF.
     */
    private static Bitmap correctOrientation(Context context, Uri imageUri, Bitmap bitmap) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) return bitmap;

            ExifInterface exif = new ExifInterface(inputStream);
            int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            );
            inputStream.close();

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return bitmap;
            }

            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        } catch (IOException e) {
            Log.e(TAG, "Error corrigiendo orientación", e);
            return bitmap;
        }
    }

    /**
     * Añade un fondo blanco a la imagen si tiene transparencia.
     * Crea un nuevo Bitmap blanco y dibuja la imagen original encima.
     */
    private static Bitmap addWhiteBackground(Bitmap sourceBitmap) {
        int width = sourceBitmap.getWidth();
        int height = sourceBitmap.getHeight();

        // Crear un nuevo Bitmap blanco con las mismas dimensiones
        Bitmap bitmapWithBackground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // Canvas para dibujar en el nuevo Bitmap
        Canvas canvas = new Canvas(bitmapWithBackground);

        // Llenar con color blanco
        canvas.drawColor(Color.WHITE);

        // Dibujar la imagen original encima
        canvas.drawBitmap(sourceBitmap, 0, 0, null);

        Log.d(TAG, "Fondo blanco añadido a la imagen");
        return bitmapWithBackground;
    }
}
