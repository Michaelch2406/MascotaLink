package com.mjc.mascotalink.security;

import android.content.Context;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class CredentialManager {

    private static final String TAG = "CredentialManager";
    private static final String CRED_PREFS = "credential_prefs_encrypted";
    private static final String KEY_EMAIL = "cred_email";
    private static final String KEY_PASSWORD = "cred_password";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";

    private final EncryptedSharedPreferences credPrefs;

    public CredentialManager(Context context) {
        EncryptedSharedPreferences prefs = null;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            prefs = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    CRED_PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error initializing credential manager", e);
        }
        this.credPrefs = prefs;
    }

    public void saveCredentials(String email, String password) {
        if (credPrefs == null) return;
        try {
            credPrefs.edit()
                    .putString(KEY_EMAIL, email)
                    .putString(KEY_PASSWORD, password)
                    .putBoolean(KEY_BIOMETRIC_ENABLED, true)
                    .apply();
            Log.d(TAG, "Credenciales guardadas cifradas");
        } catch (Exception e) {
            Log.e(TAG, "Error saving credentials", e);
        }
    }

    public String[] getCredentials() {
        if (credPrefs == null) return null;
        try {
            String email = credPrefs.getString(KEY_EMAIL, null);
            String password = credPrefs.getString(KEY_PASSWORD, null);
            if (email != null && password != null) {
                Log.d(TAG, "Credenciales recuperadas");
                return new String[]{email, password};
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving credentials", e);
        }
        return null;
    }

    public boolean isBiometricEnabled() {
        if (credPrefs == null) return false;
        try {
            return credPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
        } catch (Exception e) {
            Log.e(TAG, "Error checking biometric flag", e);
            return false;
        }
    }

    public boolean canAutoLogin() {
        if (credPrefs == null) return false;
        try {
            boolean biometricEnabled = credPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
            String email = credPrefs.getString(KEY_EMAIL, null);
            String password = credPrefs.getString(KEY_PASSWORD, null);
            return biometricEnabled && email != null && password != null;
        } catch (Exception e) {
            Log.e(TAG, "Error checking auto-login", e);
            return false;
        }
    }

    public boolean hasStoredCredentials() {
        if (credPrefs == null) return false;
        try {
            String email = credPrefs.getString(KEY_EMAIL, null);
            String password = credPrefs.getString(KEY_PASSWORD, null);
            return email != null && password != null;
        } catch (Exception e) {
            Log.e(TAG, "Error checking stored credentials", e);
            return false;
        }
    }

    public void clearCredentials() {
        if (credPrefs == null) return;
        try {
            credPrefs.edit()
                    .remove(KEY_EMAIL)
                    .remove(KEY_PASSWORD)
                    .remove(KEY_BIOMETRIC_ENABLED)
                    .apply();
            Log.d(TAG, "Credenciales eliminadas");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing credentials", e);
        }
    }
}
