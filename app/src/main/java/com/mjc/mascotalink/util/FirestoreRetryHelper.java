package com.mjc.mascotalink.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

/**
 * Helper para manejar reintentos automáticos en operaciones críticas de Firestore
 * con backoff exponencial.
 *
 * Uso:
 * FirestoreRetryHelper.execute(
 *     () -> db.collection("test").document("doc").set(data),
 *     result -> Log.d(TAG, "Éxito"),
 *     error -> Log.e(TAG, "Error después de reintentos", error),
 *     3  // intentos máximos
 * );
 */
public class FirestoreRetryHelper {

    private static final String TAG = "FirestoreRetryHelper";
    private static final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Interfaz para operaciones de Firestore que pueden fallar
     */
    public interface FirestoreOperation<T> {
        Task<T> execute();
    }

    /**
     * Ejecuta una operación de Firestore con reintentos automáticos
     *
     * @param operation Operación a ejecutar
     * @param onSuccess Callback de éxito
     * @param onFailure Callback de fallo final (después de todos los reintentos)
     * @param maxRetries Número máximo de reintentos (por defecto 3)
     */
    public static <T> void execute(
            @NonNull FirestoreOperation<T> operation,
            @NonNull OnSuccessListener<T> onSuccess,
            @NonNull OnFailureListener onFailure,
            int maxRetries) {

        executeWithRetry(operation, onSuccess, onFailure, maxRetries, 0, 1000);
    }

    /**
     * Sobrecarga con reintentos por defecto (3)
     */
    public static <T> void execute(
            @NonNull FirestoreOperation<T> operation,
            @NonNull OnSuccessListener<T> onSuccess,
            @NonNull OnFailureListener onFailure) {

        execute(operation, onSuccess, onFailure, 3);
    }

    /**
     * Método interno recursivo para manejar los reintentos
     */
    private static <T> void executeWithRetry(
            @NonNull FirestoreOperation<T> operation,
            @NonNull OnSuccessListener<T> onSuccess,
            @NonNull OnFailureListener onFailure,
            int maxRetries,
            int currentAttempt,
            long delayMs) {

        Log.d(TAG, "Intento " + (currentAttempt + 1) + "/" + (maxRetries + 1));

        operation.execute()
                .addOnSuccessListener(result -> {
                    if (currentAttempt > 0) {
                        Log.d(TAG, " Éxito después de " + (currentAttempt + 1) + " intento(s)");
                    }
                    onSuccess.onSuccess(result);
                })
                .addOnFailureListener(error -> {
                    if (currentAttempt < maxRetries) {
                        // Calcular delay con backoff exponencial
                        long nextDelay = delayMs * 2; // 1s, 2s, 4s, 8s...
                        long cappedDelay = Math.min(nextDelay, 10000); // Máximo 10 segundos

                        Log.w(TAG, " Intento " + (currentAttempt + 1) + " falló. Reintentando en " +
                                   (cappedDelay/1000) + "s... Error: " + error.getMessage());

                        // Programar siguiente intento
                        handler.postDelayed(() -> {
                            executeWithRetry(operation, onSuccess, onFailure, maxRetries,
                                           currentAttempt + 1, cappedDelay);
                        }, cappedDelay);

                    } else {
                        // Agotar todos los reintentos
                        Log.e(TAG, " Operación falló después de " + (maxRetries + 1) +
                                  " intentos. Error: " + error.getMessage());
                        onFailure.onFailure(error);
                    }
                });
    }

    /**
     * Ejecuta operación crítica con 5 reintentos (para operaciones MUY importantes como pagos)
     */
    public static <T> void executeCritical(
            @NonNull FirestoreOperation<T> operation,
            @NonNull OnSuccessListener<T> onSuccess,
            @NonNull OnFailureListener onFailure) {

        execute(operation, onSuccess, onFailure, 5);
    }

    /**
     * Ejecuta operación de baja prioridad con 1 solo reintento
     */
    public static <T> void executeLowPriority(
            @NonNull FirestoreOperation<T> operation,
            @NonNull OnSuccessListener<T> onSuccess,
            @NonNull OnFailureListener onFailure) {

        execute(operation, onSuccess, onFailure, 1);
    }
}
