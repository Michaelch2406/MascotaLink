package com.mjc.mascotalink.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class NotificacionesPreferences {
    private static final String TAG = "NotificacionesPrefs";
    private static final String PREF_NAME = "NotificacionesPreferences";

    // Keys
    private static final String KEY_NOTIFICACIONES_ENABLED = "notificaciones_enabled";
    private static final String KEY_RESERVAS_ENABLED = "notificaciones_reservas";
    private static final String KEY_MENSAJES_ENABLED = "notificaciones_mensajes";
    private static final String KEY_PASEOS_ENABLED = "notificaciones_paseos";
    private static final String KEY_PAGOS_ENABLED = "notificaciones_pagos";
    private static final String KEY_PASEADOR_CERCA_ENABLED = "notificaciones_paseador_cerca";

    private final SharedPreferences preferences;
    private final Context context;

    public NotificacionesPreferences(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // General notifications
    public boolean isNotificacionesEnabled() {
        return preferences.getBoolean(KEY_NOTIFICACIONES_ENABLED, true);
    }

    public void setNotificacionesEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_NOTIFICACIONES_ENABLED, enabled).apply();

        // Actualizar FCM topic subscription
        if (enabled) {
            subscribeToTopics();
        } else {
            unsubscribeFromTopics();
        }

        // Guardar en Firestore
        saveToFirestore();
    }

    // Specific notification types
    public boolean isReservasEnabled() {
        return preferences.getBoolean(KEY_RESERVAS_ENABLED, true);
    }

    public void setReservasEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_RESERVAS_ENABLED, enabled).apply();
        saveToFirestore();
    }

    public boolean isMensajesEnabled() {
        return preferences.getBoolean(KEY_MENSAJES_ENABLED, true);
    }

    public void setMensajesEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_MENSAJES_ENABLED, enabled).apply();
        saveToFirestore();
    }

    public boolean isPaseosEnabled() {
        return preferences.getBoolean(KEY_PASEOS_ENABLED, true);
    }

    public void setPaseosEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_PASEOS_ENABLED, enabled).apply();
        saveToFirestore();
    }

    public boolean isPagosEnabled() {
        return preferences.getBoolean(KEY_PAGOS_ENABLED, true);
    }

    public void setPagosEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_PAGOS_ENABLED, enabled).apply();
        saveToFirestore();
    }

    public boolean isPaseadorCercaEnabled() {
        return preferences.getBoolean(KEY_PASEADOR_CERCA_ENABLED, true);
    }

    public void setPaseadorCercaEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_PASEADOR_CERCA_ENABLED, enabled).apply();
        savePaseadorCercaToFirestore(enabled);
    }

    private void savePaseadorCercaToFirestore(boolean enabled) {
        String userId = getCurrentUserId();
        if (userId == null) return;

        FirebaseFirestore.getInstance()
                .collection("duenos")
                .document(userId)
                .update("notificaciones_paseador_cerca", enabled)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Paseador cerca preference saved"))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving paseador cerca preference", e));
    }

    // FCM Topics
    private void subscribeToTopics() {
        FirebaseMessaging messaging = FirebaseMessaging.getInstance();
        String userId = getCurrentUserId();

        if (userId != null) {
            messaging.subscribeToTopic("user_" + userId)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Subscribed to notifications"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error subscribing to notifications", e));
        }
    }

    private void unsubscribeFromTopics() {
        FirebaseMessaging messaging = FirebaseMessaging.getInstance();
        String userId = getCurrentUserId();

        if (userId != null) {
            messaging.unsubscribeFromTopic("user_" + userId)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Unsubscribed from notifications"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error unsubscribing from notifications", e));
        }
    }

    // Firestore sync
    private void saveToFirestore() {
        String userId = getCurrentUserId();
        if (userId == null) return;

        Map<String, Object> preferences = new HashMap<>();
        preferences.put("notificaciones_enabled", isNotificacionesEnabled());
        preferences.put("notificaciones_reservas", isReservasEnabled());
        preferences.put("notificaciones_mensajes", isMensajesEnabled());
        preferences.put("notificaciones_paseos", isPaseosEnabled());
        preferences.put("notificaciones_pagos", isPagosEnabled());
        preferences.put("notificaciones_paseador_cerca", isPaseadorCercaEnabled());

        FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(userId)
                .update("notificaciones_preferencias", preferences)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Preferences saved to Firestore"))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving preferences to Firestore", e));
    }

    public void loadFromFirestore(OnPreferencesLoadedListener listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.onLoaded();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> prefs = (Map<String, Object>) documentSnapshot.get("notificaciones_preferencias");
                        if (prefs != null) {
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putBoolean(KEY_NOTIFICACIONES_ENABLED, (Boolean) prefs.getOrDefault("notificaciones_enabled", true));
                            editor.putBoolean(KEY_RESERVAS_ENABLED, (Boolean) prefs.getOrDefault("notificaciones_reservas", true));
                            editor.putBoolean(KEY_MENSAJES_ENABLED, (Boolean) prefs.getOrDefault("notificaciones_mensajes", true));
                            editor.putBoolean(KEY_PASEOS_ENABLED, (Boolean) prefs.getOrDefault("notificaciones_paseos", true));
                            editor.putBoolean(KEY_PAGOS_ENABLED, (Boolean) prefs.getOrDefault("notificaciones_pagos", true));
                            editor.putBoolean(KEY_PASEADOR_CERCA_ENABLED, (Boolean) prefs.getOrDefault("notificaciones_paseador_cerca", true));
                            editor.apply();
                        }
                    }
                    listener.onLoaded();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading preferences from Firestore", e);
                    listener.onLoaded();
                });
    }

    private String getCurrentUserId() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    public interface OnPreferencesLoadedListener {
        void onLoaded();
    }
}
