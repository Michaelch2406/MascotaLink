package com.mjc.mascotalink.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;

import com.google.android.material.textfield.TextInputLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utilidades centralizadas para manejo de inputs, validación, sanitización y debouncing.
 * Uso global en toda la aplicación para evitar código duplicado.
 */
public class InputUtils {

    // ==================== CONSTANTES ====================
    private static final String TELEFONO_ECUADOR_REGEX = "^(\\+593[0-9]{9}|09[0-9]{8})$";
    private static final int CEDULA_LENGTH = 10;
    private static final int PASSWORD_MIN_LENGTH = 6;

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

    // ==================== VALIDACIONES ECUADOR ====================

    /**
     * Valida una cédula ecuatoriana usando el algoritmo de módulo 10
     * @param cedula Número de cédula (10 dígitos)
     * @return true si la cédula es válida
     */
    public static boolean isValidCedulaEcuador(String cedula) {
        if (cedula == null || !cedula.matches("\\d{" + CEDULA_LENGTH + "}")) {
            return false;
        }

        try {
            int provincia = Integer.parseInt(cedula.substring(0, 2));
            if (provincia < 1 || provincia > 24) {
                return false;
            }

            int[] coeficientes = {2, 1, 2, 1, 2, 1, 2, 1, 2};
            int suma = 0;

            for (int i = 0; i < 9; i++) {
                int producto = Character.getNumericValue(cedula.charAt(i)) * coeficientes[i];
                suma += (producto >= 10) ? producto - 9 : producto;
            }

            int digitoVerificador = (suma % 10 == 0) ? 0 : (10 - (suma % 10));
            return digitoVerificador == Character.getNumericValue(cedula.charAt(9));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Valida un número de teléfono ecuatoriano
     * Formatos válidos: +593XXXXXXXXX o 09XXXXXXXX
     * @param telefono Número de teléfono
     * @return true si el teléfono es válido
     */
    public static boolean isValidTelefonoEcuador(String telefono) {
        if (TextUtils.isEmpty(telefono)) {
            return false;
        }
        return telefono.trim().matches(TELEFONO_ECUADOR_REGEX);
    }

    /**
     * Limpia un teléfono ecuatoriano removiendo caracteres especiales
     * @param telefono Teléfono en cualquier formato válido
     * @return Teléfono limpio en formato nacional 09XXXXXXXX o el original si no es válido
     */
    public static String formatTelefonoEcuador(String telefono) {
        if (TextUtils.isEmpty(telefono)) {
            return "";
        }

        String cleaned = telefono.trim().replaceAll("[^0-9+]", "");

        if (cleaned.startsWith("09") && cleaned.length() == 10) {
            return cleaned;
        } else if (cleaned.startsWith("+593") && cleaned.length() == 13) {
            return "0" + cleaned.substring(4);
        }

        return telefono; // Retornar original si no coincide
    }

    // ==================== VALIDACIONES GENÉRICAS ====================

    /**
     * Valida un email usando el patrón de Android
     * @param email Dirección de correo
     * @return true si el email es válido
     */
    public static boolean isValidEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            return false;
        }
        return Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    /**
     * Valida que una contraseña cumpla requisitos mínimos
     * @param password Contraseña a validar
     * @return true si cumple con la longitud mínima (6 caracteres)
     */
    public static boolean isValidPassword(String password) {
        return isValidPassword(password, PASSWORD_MIN_LENGTH);
    }

    /**
     * Valida que una contraseña cumpla requisitos mínimos
     * @param password Contraseña a validar
     * @param minLength Longitud mínima requerida
     * @return true si cumple con la longitud mínima
     */
    public static boolean isValidPassword(String password, int minLength) {
        if (TextUtils.isEmpty(password)) {
            return false;
        }
        return password.trim().length() >= minLength;
    }

    /**
     * Verifica que un texto no esté vacío (null-safe)
     * @param text Texto a verificar
     * @return true si el texto tiene contenido
     */
    public static boolean isNotEmpty(String text) {
        return !TextUtils.isEmpty(text) && !text.trim().isEmpty();
    }

    /**
     * Verifica que un texto tenga una longitud dentro de un rango
     * @param text Texto a verificar
     * @param min Longitud mínima
     * @param max Longitud máxima
     * @return true si está dentro del rango
     */
    public static boolean isValidLength(String text, int min, int max) {
        if (TextUtils.isEmpty(text)) {
            return min == 0;
        }
        int length = text.trim().length();
        return length >= min && length <= max;
    }

    // ==================== FORMATEO DE TEXTO ====================

    /**
     * Capitaliza cada palabra de un texto (para nombres)
     * Ej: "juan carlos" -> "Juan Carlos"
     * @param text Texto a capitalizar
     * @return Texto con cada palabra capitalizada
     */
    public static String capitalizeWords(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        String[] words = text.trim().toLowerCase(Locale.getDefault()).split("\\s+");

        for (int i = 0; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
                if (i < words.length - 1) {
                    result.append(" ");
                }
            }
        }

        return result.toString();
    }

    /**
     * Trim null-safe que retorna cadena vacía si es null
     * @param text Texto a procesar
     * @return Texto sin espacios al inicio/fin o cadena vacía
     */
    public static String trimSafe(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * Retorna el texto o un valor por defecto si es null/vacío
     * @param text Texto a verificar
     * @param defaultValue Valor por defecto
     * @return El texto si tiene contenido, sino el valor por defecto
     */
    public static String getOrDefault(String text, String defaultValue) {
        return isNotEmpty(text) ? text.trim() : defaultValue;
    }

    // ==================== TEXTWATCHER SIMPLIFICADO ====================

    /**
     * Interface funcional para simplificar TextWatcher cuando solo necesitas afterTextChanged
     */
    public interface OnTextChangedListener {
        void onTextChanged(String text);
    }

    /**
     * Crea un TextWatcher simplificado que solo ejecuta afterTextChanged
     * @param listener Listener a ejecutar cuando cambia el texto
     * @return TextWatcher configurado
     */
    public static TextWatcher createSimpleTextWatcher(OnTextChangedListener listener) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (listener != null) {
                    listener.onTextChanged(s.toString());
                }
            }
        };
    }

    /**
     * Crea un TextWatcher con debouncing integrado
     * @param debounceKey Clave única para el debounce
     * @param delayMillis Delay en milisegundos
     * @param listener Listener a ejecutar
     * @return TextWatcher con debouncing
     */
    public static TextWatcher createDebouncedTextWatcher(String debounceKey, long delayMillis, OnTextChangedListener listener) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                debounce(debounceKey, delayMillis, () -> {
                    if (listener != null) {
                        listener.onTextChanged(s.toString());
                    }
                });
            }
        };
    }

    // ==================== CLICK LISTENER CON RATE LIMITING ====================

    /**
     * Interface funcional para clicks seguros
     */
    public interface SafeClickListener {
        void onSafeClick(View view);
    }

    /**
     * Crea un OnClickListener con rate limiting integrado
     * @param cooldownMillis Tiempo de espera entre clicks
     * @param listener Listener a ejecutar
     * @return OnClickListener con protección contra doble-click
     */
    public static View.OnClickListener createSafeClickListener(long cooldownMillis, SafeClickListener listener) {
        return new View.OnClickListener() {
            private long lastClickTime = 0;

            @Override
            public void onClick(View v) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime >= cooldownMillis) {
                    lastClickTime = currentTime;
                    if (listener != null) {
                        listener.onSafeClick(v);
                    }
                }
            }
        };
    }

    /**
     * Crea un OnClickListener con rate limiting de 1 segundo (valor por defecto)
     * @param listener Listener a ejecutar
     * @return OnClickListener con protección contra doble-click
     */
    public static View.OnClickListener createSafeClickListener(SafeClickListener listener) {
        return createSafeClickListener(1000, listener);
    }

    // ==================== VALIDACIONES ESPECÍFICAS DOMINIO ====================

    /**
     * Valida que una fecha de nacimiento cumpla con la edad mínima
     * @param fechaNacimiento Fecha en formato dd/MM/yyyy
     * @param edadMinima Edad mínima requerida
     * @return true si cumple con la edad mínima
     */
    public static boolean isValidAge(String fechaNacimiento, int edadMinima) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            Date birthDate = sdf.parse(fechaNacimiento);
            if (birthDate == null) return false;

            Calendar today = Calendar.getInstance();
            Calendar birth = Calendar.getInstance();
            birth.setTime(birthDate);

            int age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
                age--;
            }

            return age >= edadMinima;
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Valida que un peso esté en un rango válido (kg)
     * @param pesoStr Peso como string
     * @param min Peso mínimo (ej: 0.5 kg)
     * @param max Peso máximo (ej: 100 kg)
     * @return true si el peso es válido
     */
    public static boolean isValidPeso(String pesoStr, double min, double max) {
        if (TextUtils.isEmpty(pesoStr)) {
            return false;
        }
        try {
            double peso = Double.parseDouble(pesoStr.trim());
            return peso >= min && peso <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== HELPERS PARA UI ====================

    /**
     * Muestra u oculta error en TextInputLayout de forma simplificada
     * @param layout TextInputLayout
     * @param errorMessage Mensaje de error (null para limpiar)
     */
    public static void setError(TextInputLayout layout, String errorMessage) {
        if (layout == null) return;
        layout.setError(errorMessage);
        layout.setErrorEnabled(errorMessage != null);
    }

    /**
     * Limpia el error de un TextInputLayout
     * @param layout TextInputLayout a limpiar
     */
    public static void clearError(TextInputLayout layout) {
        setError(layout, null);
    }

    // ==================== VALIDACIONES ADICIONALES ====================

    /**
     * Valida que un nombre solo contenga letras, espacios y caracteres latinos
     * @param name Nombre a validar
     * @param minLength Longitud mínima
     * @param maxLength Longitud máxima
     * @return true si es válido
     */
    public static boolean isValidName(String name, int minLength, int maxLength) {
        if (!isNotEmpty(name)) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.length() < minLength || trimmed.length() > maxLength) {
            return false;
        }
        // Permite letras (incluyendo acentuadas), espacios y apóstrofes
        return trimmed.matches("[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ' ]+");
    }

    /**
     * Valida que un nombre solo contenga letras (rango por defecto 2-50)
     * @param name Nombre a validar
     * @return true si es válido
     */
    public static boolean isValidName(String name) {
        return isValidName(name, 2, 50);
    }

    // ==================== UI HELPERS AVANZADOS ====================

    /**
     * Oculta el teclado desde una Activity
     * @param activity Activity actual
     */
    public static void hideKeyboard(Activity activity) {
        if (activity == null) return;
        View view = activity.getCurrentFocus();
        if (view != null) {
            hideKeyboard(view);
        }
    }

    /**
     * Oculta el teclado desde una View específica
     * @param view View con foco del teclado
     */
    public static void hideKeyboard(View view) {
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Cambia el estado de un botón a modo carga
     * @param button Botón a modificar
     * @param loading true para mostrar estado cargando, false para restaurar
     * @param loadingText Texto a mostrar durante carga (ej: "Cargando...")
     */
    public static void setButtonLoading(Button button, boolean loading, String loadingText) {
        if (button == null) return;

        if (loading) {
            // Guardar texto original en el tag para restaurar después
            if (button.getTag() == null) {
                button.setTag(button.getText().toString());
            }
            button.setText(loadingText);
            button.setEnabled(false);
        } else {
            // Restaurar texto original del tag
            Object originalText = button.getTag();
            if (originalText != null) {
                button.setText(originalText.toString());
                button.setTag(null);
            }
            button.setEnabled(true);
        }
    }

    /**
     * Cambia el estado de un botón a modo carga (texto por defecto "Cargando...")
     * @param button Botón a modificar
     * @param loading true para mostrar estado cargando, false para restaurar
     */
    public static void setButtonLoading(Button button, boolean loading) {
        setButtonLoading(button, loading, "Cargando...");
    }

    // ==================== VALIDACIÓN DE ARCHIVOS ====================

    /**
     * Valida que un archivo de imagen sea válido
     * @param context Contexto de la aplicación
     * @param uri URI del archivo
     * @param maxSizeBytes Tamaño máximo en bytes (ej: 5MB = 5 * 1024 * 1024)
     * @return true si es una imagen válida dentro del tamaño permitido
     */
    public static boolean isValidImageFile(Context context, Uri uri, long maxSizeBytes) {
        if (context == null || uri == null) {
            return false;
        }

        try {
            // Verificar tipo MIME
            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType == null || !mimeType.startsWith("image/")) {
                return false;
            }

            // Verificar tamaño del archivo
            if (maxSizeBytes > 0) {
                android.database.Cursor cursor = context.getContentResolver()
                        .query(uri, null, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                            if (sizeIndex != -1) {
                                long fileSize = cursor.getLong(sizeIndex);
                                if (fileSize > maxSizeBytes) {
                                    return false;
                                }
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Valida que un archivo de imagen sea válido (tamaño máximo por defecto: 5MB)
     * @param context Contexto de la aplicación
     * @param uri URI del archivo
     * @return true si es una imagen válida
     */
    public static boolean isValidImageFile(Context context, Uri uri) {
        return isValidImageFile(context, uri, 5 * 1024 * 1024); // 5MB por defecto
    }

    /**
     * Obtiene la extensión de un archivo desde su URI
     * @param context Contexto de la aplicación
     * @param uri URI del archivo
     * @return Extensión del archivo (ej: "jpg", "png") o null si no se puede determinar
     */
    public static String getFileExtension(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }

        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType != null) {
            return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        }

        // Fallback: intentar obtener desde el path
        String path = uri.getPath();
        if (path != null && path.contains(".")) {
            return path.substring(path.lastIndexOf(".") + 1).toLowerCase();
        }

        return null;
    }
}
