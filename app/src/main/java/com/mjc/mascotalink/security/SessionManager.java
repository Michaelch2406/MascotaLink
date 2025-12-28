package com.mjc.mascotalink.security;

import android.content.Context;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String SESSION_PREF = "session_prefs";
    private static final String SESSION_TOKEN_KEY = "session_token";
    private static final String SESSION_USER_KEY = "session_user";

    private final Context context;
    private final EncryptedSharedPreferences encryptedSharedPrefs;

    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.encryptedSharedPrefs = initEncryptedPrefs();
    }

    private EncryptedSharedPreferences initEncryptedPrefs() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    SESSION_PREF,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error initializing encrypted prefs", e);
            return null;
        }
    }

    private boolean hasPrefs() {
        return encryptedSharedPrefs != null;
    }

    private void persistSession(String token, String userId) {
        if (!hasPrefs()) {
            return;
        }

        encryptedSharedPrefs.edit()
                .putString(SESSION_TOKEN_KEY, token)
                .putString(SESSION_USER_KEY, userId)
                .apply();
    }

    private void clearStoredSession() {
        if (!hasPrefs()) {
            return;
        }

        encryptedSharedPrefs.edit()
                .remove(SESSION_TOKEN_KEY)
                .remove(SESSION_USER_KEY)
                .apply();
    }

    public void createSession(String userId) {
        if (!hasPrefs() || userId == null) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "createSession: no authenticated user");
            clearStoredSession();
            return;
        }

        user.getIdToken(false)
                .addOnSuccessListener(tokenResult -> {
                    String token = tokenResult.getToken();
                    if (token != null) {
                        persistSession(token, userId);
                    } else {
                        Log.w(TAG, "createSession: token result was null");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "createSession: unable to fetch token", e));
    }

    public boolean isSessionValid() {
        if (!hasPrefs()) {
            return false;
        }
        // Session is valid if the token key exists. Infinite duration.
        return encryptedSharedPrefs.contains(SESSION_TOKEN_KEY);
    }

    public String getSessionToken() {
        if (!hasPrefs()) {
            return null;
        }

        if (isSessionValid()) {
            return encryptedSharedPrefs.getString(SESSION_TOKEN_KEY, null);
        }

        return null;
    }

    public Task<Boolean> validateAndRefreshToken() {
        TaskCompletionSource<Boolean> taskSource = new TaskCompletionSource<>();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            clearStoredSession();
            taskSource.setResult(false);
            return taskSource.getTask();
        }

        user.getIdToken(true)
                .addOnSuccessListener(tokenResult -> {
                    String token = tokenResult.getToken();
                    if (token != null) {
                        persistSession(token, encryptedSharedPrefs.getString(SESSION_USER_KEY, ""));
                        taskSource.setResult(true);
                    } else {
                        clearStoredSession();
                        taskSource.setResult(false);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "validateAndRefreshToken: failed to refresh", e);
                    clearStoredSession();
                    taskSource.setResult(false);
                });

        return taskSource.getTask();
    }

    public void clearSession() {
        clearStoredSession();
    }
}
