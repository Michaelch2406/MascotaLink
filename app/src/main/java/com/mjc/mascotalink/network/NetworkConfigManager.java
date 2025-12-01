package com.mjc.mascotalink.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.Nullable;

/**
 * Gestor de configuración de red persistente
 * Permite configuración manual de IPs y preferencias de conexión
 */
public class NetworkConfigManager {
    private static final String TAG = "NetworkConfigManager";
    private static final String PREFS_NAME = "network_config";

    // Keys para SharedPreferences
    private static final String KEY_MANUAL_IP = "manual_server_ip";
    private static final String KEY_TAILSCALE_SERVER_IP = "tailscale_server_ip";
    private static final String KEY_PREFER_TAILSCALE = "prefer_tailscale";
    private static final String KEY_AUTO_DETECT = "auto_detect_enabled";

    // IP por defecto de Tailscale (tu laptop)
    private static final String DEFAULT_TAILSCALE_IP = "100.88.138.23";

    private final SharedPreferences prefs;

    public NetworkConfigManager(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Obtiene la IP del servidor Tailscale configurada
     */
    @Nullable
    public String getTailscaleServerIp() {
        return prefs.getString(KEY_TAILSCALE_SERVER_IP, DEFAULT_TAILSCALE_IP);
    }

    /**
     * Configura la IP del servidor Tailscale
     */
    public void setTailscaleServerIp(String ip) {
        prefs.edit().putString(KEY_TAILSCALE_SERVER_IP, ip).apply();
        Log.d(TAG, "IP de Tailscale configurada: " + ip);
    }

    /**
     * Obtiene la IP manual configurada por el usuario
     */
    @Nullable
    public String getManualIp() {
        return prefs.getString(KEY_MANUAL_IP, null);
    }

    /**
     * Configura una IP manual
     */
    public void setManualIp(String ip) {
        prefs.edit().putString(KEY_MANUAL_IP, ip).apply();
        Log.d(TAG, "IP manual configurada: " + ip);
    }

    /**
     * Limpia la configuración manual
     */
    public void clearManualIp() {
        prefs.edit().remove(KEY_MANUAL_IP).apply();
        Log.d(TAG, "IP manual eliminada");
    }

    /**
     * Verifica si se debe preferir Tailscale sobre otras conexiones
     */
    public boolean shouldPreferTailscale() {
        return prefs.getBoolean(KEY_PREFER_TAILSCALE, true);
    }

    /**
     * Configura preferencia de Tailscale
     */
    public void setPreferTailscale(boolean prefer) {
        prefs.edit().putBoolean(KEY_PREFER_TAILSCALE, prefer).apply();
        Log.d(TAG, "Preferencia Tailscale: " + prefer);
    }

    /**
     * Verifica si la detección automática está habilitada
     */
    public boolean isAutoDetectEnabled() {
        return prefs.getBoolean(KEY_AUTO_DETECT, true);
    }

    /**
     * Habilita/deshabilita detección automática
     */
    public void setAutoDetectEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_DETECT, enabled).apply();
        Log.d(TAG, "Detección automática: " + enabled);
    }

    /**
     * Resetea toda la configuración a valores por defecto
     */
    public void resetToDefaults() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Configuración reseteada a valores por defecto");
    }

    /**
     * Obtiene información de configuración actual (para debugging)
     */
    public String getConfigInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== CONFIGURACIÓN DE RED ===\n");
        info.append("Auto-detección: ").append(isAutoDetectEnabled() ? "Sí" : "No").append("\n");
        info.append("Preferir Tailscale: ").append(shouldPreferTailscale() ? "Sí" : "No").append("\n");
        info.append("IP Tailscale: ").append(getTailscaleServerIp()).append("\n");

        String manualIp = getManualIp();
        info.append("IP Manual: ").append(manualIp != null ? manualIp : "No configurada").append("\n");

        return info.toString();
    }
}
