package com.mjc.mascotalink.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Gestiona el caché de datos del usuario actual para evitar consultas repetidas a Firebase.
 * Los datos se guardan en SharedPreferences con una duración de 5 minutos.
 */
public class UserCacheManager {
    private static final String TAG = "UserCacheManager";
    private static final String PREFS_NAME = "MascotaLinkUserCache";

    // Keys para SharedPreferences
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ROLE = "role";
    private static final String KEY_NOMBRE = "nombre";
    private static final String KEY_FOTO_URL = "foto_url";
    private static final String KEY_CACHE_TIME = "cache_time";

    // Duración del caché: 5 minutos
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000;

    /**
     * Datos cacheados del usuario
     */
    public static class UserData {
        public String userId;
        public String role;
        public String nombre;
        public String fotoUrl;

        public UserData(String userId, String role, String nombre, String fotoUrl) {
            this.userId = userId;
            this.role = role;
            this.nombre = nombre;
            this.fotoUrl = fotoUrl;
        }

        public boolean isValid() {
            return userId != null && !userId.isEmpty();
        }
    }

    /**
     * Obtiene los datos del usuario desde el caché si están disponibles y no han expirado.
     * @param context Contexto de la aplicación
     * @param userId ID del usuario a buscar
     * @return UserData si existe en caché y es válido, null en caso contrario
     */
    @Nullable
    public static UserData getUserData(@NonNull Context context, @NonNull String userId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Verificar si el userId coincide
        String cachedUserId = prefs.getString(KEY_USER_ID, null);
        if (!userId.equals(cachedUserId)) {
            return null;
        }

        // Verificar si el caché ha expirado
        long cacheTime = prefs.getLong(KEY_CACHE_TIME, 0);
        long currentTime = System.currentTimeMillis();
        if (currentTime - cacheTime > CACHE_DURATION_MS) {
            Log.d(TAG, "Cache expirado para usuario: " + userId);
            return null;
        }

        // Obtener datos del caché
        String role = prefs.getString(KEY_ROLE, null);
        String nombre = prefs.getString(KEY_NOMBRE, null);
        String fotoUrl = prefs.getString(KEY_FOTO_URL, null);

        UserData userData = new UserData(userId, role, nombre, fotoUrl);

        if (userData.isValid()) {
            Log.d(TAG, "Datos de usuario obtenidos del caché: " + userId);
            return userData;
        }

        return null;
    }

    /**
     * Guarda los datos del usuario en el caché.
     * @param context Contexto de la aplicación
     * @param userData Datos del usuario a guardar
     */
    public static void saveUserData(@NonNull Context context, @NonNull UserData userData) {
        if (!userData.isValid()) {
            Log.w(TAG, "Intento de guardar datos de usuario inválidos");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(KEY_USER_ID, userData.userId);
        editor.putString(KEY_ROLE, userData.role);
        editor.putString(KEY_NOMBRE, userData.nombre);
        editor.putString(KEY_FOTO_URL, userData.fotoUrl);
        editor.putLong(KEY_CACHE_TIME, System.currentTimeMillis());

        editor.apply();
        Log.d(TAG, "Datos de usuario guardados en caché: " + userData.userId);
    }

    /**
     * Obtiene solo el rol del usuario desde el caché.
     * @param context Contexto de la aplicación
     * @param userId ID del usuario
     * @return Rol del usuario o null si no está en caché
     */
    @Nullable
    public static String getUserRole(@NonNull Context context, @NonNull String userId) {
        UserData userData = getUserData(context, userId);
        return userData != null ? userData.role : null;
    }

    /**
     * Limpia el caché del usuario.
     * @param context Contexto de la aplicación
     */
    public static void clearCache(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        Log.d(TAG, "Caché de usuario limpiado");
    }

    /**
     * Carga los datos del usuario desde Firebase y los guarda en caché.
     * @param context Contexto de la aplicación
     * @param userId ID del usuario
     * @param callback Callback con los datos cargados o null si hay error
     */
    public static void loadAndCacheUserData(@NonNull Context context, @NonNull String userId, @NonNull UserDataCallback callback) {
        // Primero intentar obtener del caché
        UserData cachedData = getUserData(context, userId);
        if (cachedData != null) {
            Log.d(TAG, "Retornando datos desde caché");
            callback.onUserDataLoaded(cachedData);
            return;
        }

        // Si no está en caché, consultar Firebase
        Log.d(TAG, "Consultando Firebase para usuario: " + userId);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("usuarios").document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String role = documentSnapshot.getString("rol");
                    String nombre = documentSnapshot.getString("nombre_display");
                    String fotoUrl = documentSnapshot.getString("foto_perfil");

                    UserData userData = new UserData(userId, role, nombre, fotoUrl);
                    saveUserData(context, userData);
                    callback.onUserDataLoaded(userData);
                } else {
                    Log.w(TAG, "Usuario no encontrado en Firebase: " + userId);
                    callback.onUserDataLoaded(null);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error al cargar datos de usuario desde Firebase", e);
                callback.onUserDataLoaded(null);
            });
    }

    /**
     * Actualiza un campo específico del caché sin recargar desde Firebase.
     * Útil cuando se sabe que un campo cambió localmente.
     */
    public static void updateField(@NonNull Context context, @NonNull String userId, @NonNull String field, @Nullable String value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String cachedUserId = prefs.getString(KEY_USER_ID, null);

        if (!userId.equals(cachedUserId)) {
            return; // No es el usuario cacheado
        }

        SharedPreferences.Editor editor = prefs.edit();

        switch (field) {
            case "role":
                editor.putString(KEY_ROLE, value);
                // También actualizar en BottomNavManager para compatibilidad
                BottomNavManager.saveUserRole(context, value);
                break;
            case "nombre":
                editor.putString(KEY_NOMBRE, value);
                break;
            case "foto_url":
                editor.putString(KEY_FOTO_URL, value);
                break;
            default:
                Log.w(TAG, "Campo no reconocido para actualizar: " + field);
                return;
        }

        editor.apply();
        Log.d(TAG, "Campo actualizado en caché: " + field);
    }

    /**
     * Callback para obtener datos del usuario
     */
    public interface UserDataCallback {
        void onUserDataLoaded(@Nullable UserData userData);
    }

    /**
     * Carga solo el rol del usuario de manera optimizada.
     * Útil para mantener compatibilidad con código existente.
     */
    public static void loadUserRole(@NonNull Context context, @NonNull String userId, @NonNull RoleCallback callback) {
        // Primero intentar desde caché
        String cachedRole = getUserRole(context, userId);
        if (cachedRole != null) {
            callback.onRoleLoaded(cachedRole);
            return;
        }

        // Intentar desde BottomNavManager (caché antiguo)
        String role = BottomNavManager.getUserRole(context);
        if (role != null) {
            callback.onRoleLoaded(role);
            return;
        }

        // Si no está en ningún caché, cargar desde Firebase
        loadAndCacheUserData(context, userId, userData -> {
            if (userData != null && userData.role != null) {
                callback.onRoleLoaded(userData.role);
            } else {
                callback.onRoleLoaded(null);
            }
        });
    }

    /**
     * Callback para obtener solo el rol
     */
    public interface RoleCallback {
        void onRoleLoaded(@Nullable String role);
    }
}
