package com.mjc.mascotalink.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilidades para manejo de inputs, validación, sanitización y debouncing
 */
public class InputUtils {

    // Debouncing con Handler
    private static final Map<String, Runnable> pendingRunnables = new HashMap<>();
    private static final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Sanitiza un string eliminando caracteres peligrosos para prevenir XSS
     */
    public static String sanitizeInput(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }

        // Elimina tags HTML y caracteres especiales peligrosos
        String sanitized = input.replaceAll("<script.*?>.*?</script>", "")
                .replaceAll("<.*?>", "")
                .replaceAll("javascript:", "")
                .replaceAll("on\\w+\\s*=", "");

        // Escapa caracteres HTML especiales
        sanitized = Html.escapeHtml(sanitized);

        return sanitized.trim();
    }

    /**
     * Ejecuta una acción con debouncing para evitar ejecuciones múltiples
     * @param key Identificador único para el debounce
     * @param delayMillis Delay en milisegundos
     * @param action Acción a ejecutar
     */
    public static void debounce(String key, long delayMillis, Runnable action) {
        // Cancela la ejecución pendiente anterior
        Runnable pending = pendingRunnables.get(key);
        if (pending != null) {
            handler.removeCallbacks(pending);
        }

        // Programa la nueva ejecución
        Runnable newRunnable = () -> {
            action.run();
            pendingRunnables.remove(key);
        };

        pendingRunnables.put(key, newRunnable);
        handler.postDelayed(newRunnable, delayMillis);
    }

    /**
     * Cancela todos los debounces pendientes
     */
    public static void cancelAllDebounces() {
        for (Runnable runnable : pendingRunnables.values()) {
            handler.removeCallbacks(runnable);
        }
        pendingRunnables.clear();
    }

    /**
     * Cancela un debounce específico
     */
    public static void cancelDebounce(String key) {
        Runnable pending = pendingRunnables.get(key);
        if (pending != null) {
            handler.removeCallbacks(pending);
            pendingRunnables.remove(key);
        }
    }

    /**
     * Rate limiting para prevenir clicks múltiples
     */
    public static class RateLimiter {
        private long lastClickTime = 0;
        private final long cooldownMillis;

        public RateLimiter(long cooldownMillis) {
            this.cooldownMillis = cooldownMillis;
        }

        public boolean shouldProcess() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < cooldownMillis) {
                return false;
            }
            lastClickTime = currentTime;
            return true;
        }

        public void reset() {
            lastClickTime = 0;
        }
    }
}
