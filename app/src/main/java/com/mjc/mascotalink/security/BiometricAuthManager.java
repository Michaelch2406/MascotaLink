package com.mjc.mascotalink.security;

import android.content.Context;
import android.util.Log;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class BiometricAuthManager {

    private static final String TAG = "BiometricAuthManager";
    private final Context context;
    private final BiometricManager biometricManager;

    public BiometricAuthManager(Context context) {
        this.context = context.getApplicationContext();
        this.biometricManager = BiometricManager.from(context);
    }

    public boolean isBiometricAvailable() {
        int canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
        );
        return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public void showBiometricPrompt(
            FragmentActivity activity,
            String title,
            String subtitle,
            BiometricPromptCallback callback
    ) {
        if (!isBiometricAvailable()) {
            callback.onBiometricError("Biometría no disponible");
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(context);

        BiometricPrompt.AuthenticationCallback authCallback =
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Log.d(TAG, "Autenticación biométrica exitosa");
                        callback.onBiometricSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Log.e(TAG, "Error biométrico: " + errString);
                        callback.onBiometricError(errString.toString());
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Log.w(TAG, "Fallo biométrico (intentar de nuevo)");
                        callback.onBiometricFailed();
                    }
                };

        BiometricPrompt prompt = new BiometricPrompt(activity, executor, authCallback);
        BiometricPrompt.PromptInfo promptInfo =
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setNegativeButtonText("Cancelar")
                        .setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        .build();

        prompt.authenticate(promptInfo);
    }

    public interface BiometricPromptCallback {
        void onBiometricSuccess();
        void onBiometricError(String error);
        void onBiometricFailed();
    }

    public void showBiometricPromptAutomatic(
            FragmentActivity activity,
            BiometricPromptCallback callback
    ) {
        if (!isBiometricAvailable()) {
            callback.onBiometricError("Biometría no disponible");
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(context);

        BiometricPrompt.AuthenticationCallback authCallback =
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Log.d(TAG, "Biometría automática exitosa");
                        callback.onBiometricSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Log.w(TAG, "Biometría error automático: " + errString);
                        callback.onBiometricError(errString.toString());
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Log.w(TAG, "Fallo biométrico automático");
                        callback.onBiometricFailed();
                    }
                };

        BiometricPrompt prompt = new BiometricPrompt(activity, executor, authCallback);
        BiometricPrompt.PromptInfo promptInfo =
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Inicia sesión")
                        .setSubtitle("Presiona tu dedo en el sensor")
                        .setDescription("O usa email y contraseña")
                        .setNegativeButtonText("Usar email")
                        .setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        .build();

        prompt.authenticate(promptInfo);
    }
}
