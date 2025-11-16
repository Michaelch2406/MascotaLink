package com.mjc.mascotalink.security;

import android.content.Context;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class EncryptedPreferencesHelper {

    private static final String TAG = "EncryptedPrefsHelper";
    private static EncryptedPreferencesHelper instance;
    private final EncryptedSharedPreferences prefs;

    private EncryptedPreferencesHelper(Context context) {
        EncryptedSharedPreferences tempPrefs = null;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            tempPrefs = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    "encrypted_app_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error initializing encrypted prefs", e);
        }
        this.prefs = tempPrefs;
    }

    public static synchronized EncryptedPreferencesHelper getInstance(Context context) {
        if (instance == null) {
            instance = new EncryptedPreferencesHelper(context.getApplicationContext());
        }
        return instance;
    }

    private boolean hasPrefs() {
        return prefs != null;
    }

    public void putString(String key, String value) {
        if (!hasPrefs()) {
            Log.w(TAG, "Prefs not initialized, skipping putString");
            return;
        }
        try {
            prefs.edit().putString(key, value).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error putting string: " + key, e);
        }
    }

    public void putInt(String key, int value) {
        if (!hasPrefs()) return;
        try {
            prefs.edit().putInt(key, value).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error putting int: " + key, e);
        }
    }

    public void putBoolean(String key, boolean value) {
        if (!hasPrefs()) return;
        try {
            prefs.edit().putBoolean(key, value).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error putting boolean: " + key, e);
        }
    }

    public void putLong(String key, long value) {
        if (!hasPrefs()) return;
        try {
            prefs.edit().putLong(key, value).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error putting long: " + key, e);
        }
    }

    public void putFloat(String key, float value) {
        if (!hasPrefs()) return;
        try {
            prefs.edit().putFloat(key, value).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error putting float: " + key, e);
        }
    }

    public String getString(String key, String defaultValue) {
        if (!hasPrefs()) return defaultValue;
        try {
            return prefs.getString(key, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "Error getting string: " + key, e);
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        if (!hasPrefs()) return defaultValue;
        try {
            return prefs.getInt(key, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "Error getting int: " + key, e);
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        if (!hasPrefs()) return defaultValue;
        try {
            return prefs.getBoolean(key, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "Error getting boolean: " + key, e);
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        if (!hasPrefs()) return defaultValue;
        try {
            return prefs.getLong(key, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "Error getting long: " + key, e);
            return defaultValue;
        }
    }

    public float getFloat(String key, float defaultValue) {
        if (!hasPrefs()) return defaultValue;
        try {
            return prefs.getFloat(key, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "Error getting float: " + key, e);
            return defaultValue;
        }
    }

    public void remove(String key) {
        if (!hasPrefs()) return;
        try {
            prefs.edit().remove(key).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error removing key: " + key, e);
        }
    }

    public void clear() {
        if (!hasPrefs()) return;
        try {
            prefs.edit().clear().apply();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing prefs", e);
        }
    }

    public boolean contains(String key) {
        if (!hasPrefs()) return false;
        try {
            return prefs.contains(key);
        } catch (Exception e) {
            Log.e(TAG, "Error checking contains: " + key, e);
            return false;
        }
    }
}
