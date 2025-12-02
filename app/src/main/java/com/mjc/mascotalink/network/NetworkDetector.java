package com.mjc.mascotalink.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class NetworkDetector {
    private static final String TAG = "NetworkDetector";

    // Configuración de redes (OPCIONAL - Solo como fallback legacy)
    private static final Map<String, NetworkConfig> NETWORK_CONFIGS = new HashMap<>();

    static {
        // NOTA: Estas configuraciones son OPCIONALES y solo se usan como fallback
        // El sistema ahora detecta automáticamente Tailscale y Gateway

        // Casa - Red fija
        NETWORK_CONFIGS.put("INNO_FLIA_CHASIGUANO_5G",
            new NetworkConfig("Casa", "192.168.0.147", "192.168.0", false));

        // Escuela - Red fija
        NETWORK_CONFIGS.put("CABLESPEED APOLO",
            new NetworkConfig("Escuela", "192.168.1.86", "192.168.1", false));

        // Trabajo - Red fija
        NETWORK_CONFIGS.put("ESTUDIANTES_IST",
            new NetworkConfig("Trabajo", "10.10.0.142", "10.10.0", false));

        // Hotspot móvil
        NETWORK_CONFIGS.put("POCO X5 PRO 5G",
            new NetworkConfig("Hotspot", "10.246.204.132", "10.246.204", false));

        // Desktop hotspot - IP DINÁMICA, necesita detección
        NETWORK_CONFIGS.put("DESKTOP-EVP8AVD 0845",
            new NetworkConfig("Desktop", null, "192.168.137", true));
    }

    private static final String DEFAULT_HOST = "127.0.0.1"; // Localhost como último fallback
    private static String cachedDesktopIp = null; // Cache para la IP del desktop
    private static NetworkConfigManager configManager = null;
    
    /**
     * Inicializa el gestor de configuración
     */
    private static void initConfigManager(Context context) {
        if (configManager == null) {
            configManager = new NetworkConfigManager(context);
        }
    }

    /**
     * Detecta la red actual usando sistema HÍBRIDO de prioridades:
     * 1. Tailscale (si está activo y configurado)
     * 2. SSID conocido con caché (aprendizaje automático)
     * 3. Subred conocida (hardcoded para redes comunes)
     * 4. Configuración manual
     * 5. Auto-detección inteligente (sondeo de IPs comunes)
     * 6. Fallback (localhost)
     */
    @NonNull
    public static String detectCurrentHost(Context context) {
        initConfigManager(context);

        try {
            Log.d(TAG, "=== INICIANDO DETECCIÓN DE RED HÍBRIDA ===");

            String localIp = getLocalIpAddress(context);
            String ssid = getWifiSsid(context);

            // ===== PRIORIDAD 1: TAILSCALE =====
            if (configManager.shouldPreferTailscale()) {
                String tailscaleIp = detectTailscaleConnection(context);
                if (tailscaleIp != null) {
                    Log.i(TAG, "✅ [PRIORIDAD 1] Tailscale detectado: " + tailscaleIp);
                    return tailscaleIp;
                }
            }

            // ===== PRIORIDAD 2: CACHÉ DE IP POR SSID (APRENDIZAJE AUTOMÁTICO) =====
            if (ssid != null) {
                String cachedIp = getCachedIpForSsid(context, ssid);
                if (cachedIp != null) {
                    Log.i(TAG, "✅ [PRIORIDAD 2] IP en caché para SSID '" + ssid + "': " + cachedIp);
                    return cachedIp;
                }
            }

            // ===== PRIORIDAD 3: DETECCIÓN RÁPIDA POR SUBRED (HARDCODED) =====
            if (localIp != null) {
                Log.d(TAG, "IP Local detectada: " + localIp);
                String hardcodedIp = getHardcodedIpForSubnet(localIp);
                if (hardcodedIp != null) {
                    // Guardar en caché para próxima vez
                    if (ssid != null) {
                        cacheIpForSsid(context, ssid, hardcodedIp);
                    }
                    return hardcodedIp;
                }
            }

            // ===== PRIORIDAD 4: SSID CONOCIDO (LEGACY) =====
            String ssidBasedIp = detectBySSID(context);
            if (ssidBasedIp != null) {
                Log.i(TAG, "✅ [PRIORIDAD 4] Red conocida por SSID legacy: " + ssidBasedIp);
                if (ssid != null) {
                    cacheIpForSsid(context, ssid, ssidBasedIp);
                }
                return ssidBasedIp;
            }

            // ===== PRIORIDAD 5: CONFIGURACIÓN MANUAL =====
            String manualIp = configManager.getManualIp();
            if (manualIp != null && !manualIp.isEmpty()) {
                Log.i(TAG, "✅ [PRIORIDAD 5] IP manual configurada: " + manualIp);
                return manualIp;
            }

            // ===== PRIORIDAD 6: AUTO-DETECCIÓN INTELIGENTE (SONDEO) =====
            if (configManager.isAutoDetectEnabled() && localIp != null) {
                String detectedIp = smartDetectEmulatorIp(context, localIp);
                if (detectedIp != null) {
                    Log.i(TAG, "✅ [PRIORIDAD 6] IP detectada automáticamente: " + detectedIp);
                    // Guardar en caché para futuras conexiones
                    if (ssid != null) {
                        cacheIpForSsid(context, ssid, detectedIp);
                    }
                    return detectedIp;
                }
            }

            // ===== PRIORIDAD 7: FALLBACK =====
            Log.w(TAG, "⚠️ [FALLBACK] No se detectó ninguna red, usando: " + DEFAULT_HOST);
            return DEFAULT_HOST;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error al detectar red: " + e.getMessage(), e);
            return DEFAULT_HOST;
        }
    }

    /**
     * Obtiene la IP hardcodeada para una subred conocida
     */
    @Nullable
    private static String getHardcodedIpForSubnet(String localIp) {
        if (localIp.startsWith("192.168.0.")) {
            Log.i(TAG, "✅ [PRIORIDAD 3] Subred Casa (192.168.0.x) detectada. Usando: 192.168.0.147");
            return "192.168.0.147";
        } else if (localIp.startsWith("192.168.1.")) {
            Log.i(TAG, "✅ [PRIORIDAD 3] Subred Escuela (192.168.1.x) detectada. Usando: 192.168.1.86");
            return "192.168.1.86";
        } else if (localIp.startsWith("192.168.137.")) {
            Log.i(TAG, "✅ [PRIORIDAD 3] Subred Hotspot (192.168.137.x) detectada. Usando: 192.168.137.1");
            return "192.168.137.1";
        } else if (localIp.startsWith("10.10.0.")) {
            Log.i(TAG, "✅ [PRIORIDAD 3] Subred Trabajo (10.10.0.x) detectada. Usando: 10.10.0.142");
            return "10.10.0.142";
        }
        return null;
    }

    /**
     * Detección inteligente del emulador mediante sondeo de IPs comunes
     */
    @Nullable
    private static String smartDetectEmulatorIp(Context context, String localIp) {
        Log.d(TAG, "Iniciando detección inteligente de emulador...");

        // Extraer prefijo de subred (primeros 3 octetos)
        String[] parts = localIp.split("\\.");
        if (parts.length != 4) return null;

        String subnet = parts[0] + "." + parts[1] + "." + parts[2] + ".";

        // IPs comunes a probar (ordenadas por probabilidad)
        String[] commonIps = {
            subnet + "1",     // Gateway típico
            subnet + "147",   // Tu IP común
            subnet + "100",   // IP común
            subnet + "142",   // Tu IP de trabajo
            subnet + "2",     // Segundo gateway común
            subnet + "254"    // Último host común
        };

        // Intentar gateway primero
        String gateway = getGatewayIp(context);
        if (gateway != null && isEmulatorRunningAt(gateway)) {
            Log.i(TAG, "Emulador encontrado en gateway: " + gateway);
            return gateway;
        }

        // Probar IPs comunes
        for (String ip : commonIps) {
            if (ip.equals(localIp)) continue; // Saltar IP local del dispositivo

            if (isEmulatorRunningAt(ip)) {
                Log.i(TAG, "Emulador encontrado en: " + ip);
                return ip;
            }
        }

        Log.w(TAG, "No se pudo detectar el emulador automáticamente");
        return null;
    }

    /**
     * Verifica si el emulador de Firebase Storage está corriendo en una IP
     */
    private static boolean isEmulatorRunningAt(String ip) {
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(ip, 9199), 500); // 500ms timeout
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtiene la IP en caché para un SSID específico
     */
    @Nullable
    private static String getCachedIpForSsid(Context context, String ssid) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("network_cache", Context.MODE_PRIVATE);
        return prefs.getString("ip_" + ssid, null);
    }

    /**
     * Guarda la IP exitosa para un SSID (aprendizaje automático)
     */
    private static void cacheIpForSsid(Context context, String ssid, String ip) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("network_cache", Context.MODE_PRIVATE);
        prefs.edit().putString("ip_" + ssid, ip).apply();
        Log.d(TAG, "IP guardada en caché: SSID='" + ssid + "' → IP=" + ip);
    }

    /**
     * PRIORIDAD 1: Detecta si el dispositivo está conectado a Tailscale
     * y retorna la IP del servidor configurado
     */
    @Nullable
    private static String detectTailscaleConnection(Context context) {
        try {
            List<NetworkInterface> interfaces = Collections.list(
                NetworkInterface.getNetworkInterfaces());

            for (NetworkInterface intf : interfaces) {
                String name = intf.getName().toLowerCase();

                // Tailscale usa "tun0" o "tailscale0" en Android
                if (name.startsWith("tun") || name.contains("tailscale")) {
                    List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                    for (InetAddress addr : addrs) {
                        if (!addr.isLoopbackAddress()) {
                            String localIp = addr.getHostAddress();

                            // Verificar si es IP de Tailscale (rango CGNAT 100.64.0.0/10)
                            if (localIp != null && localIp.startsWith("100.")) {
                                String serverIp = configManager.getTailscaleServerIp();
                                Log.d(TAG, "Tailscale activo - IP local: " + localIp +
                                      ", IP servidor: " + serverIp);
                                return serverIp;
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Tailscale no detectado en interfaces de red");
        } catch (SocketException e) {
            Log.e(TAG, "Error detectando Tailscale: " + e.getMessage(), e);
        }

        return null;
    }

    /**
     * PRIORIDAD 2: Detecta la IP del gateway automáticamente
     */
    @Nullable
    private static String detectGatewayIp(Context context) {
        return getGatewayIp(context);
    }

    /**
     * PRIORIDAD 3: Detección por SSID (sistema legacy)
     */
    @Nullable
    private static String detectBySSID(Context context) {
        String ssid = getWifiSsid(context);
        if (ssid != null) {
            NetworkConfig config = NETWORK_CONFIGS.get(ssid);
            if (config != null) {
                // Si es una red con IP dinámica, detectarla
                if (config.isDynamic) {
                    String detectedIp = detectDesktopIp(context);
                    if (detectedIp != null) {
                        cachedDesktopIp = detectedIp;
                        return detectedIp;
                    } else if (cachedDesktopIp != null) {
                        return cachedDesktopIp;
                    }
                }

                // Red con IP fija
                if (config.host != null) {
                    return config.host;
                }
            }
        }
        return null;
    }
    
    /**
     * Detecta la IP del desktop cuando está compartiendo internet
     * El gateway es la IP de la PC que comparte la conexión
     */
    @Nullable
    private static String detectDesktopIp(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            
            if (wifiManager != null) {
                DhcpInfo dhcp = wifiManager.getDhcpInfo();
                if (dhcp != null && dhcp.gateway != 0) {
                    // El gateway es la IP de la PC que comparte internet
                    String gatewayIp = intToIp(dhcp.gateway);
                    Log.d(TAG, "Gateway detectado (IP del Desktop): " + gatewayIp);
                    
                    // Verificar que sea una IP válida de red local
                    if (isValidLocalIp(gatewayIp)) {
                        // El emulador de Firebase debe conectarse al gateway
                        return gatewayIp;
                    }
                }
            }
            
            // Método alternativo: Obtener el gateway desde NetworkInterface
            String localIp = getLocalIpAddress(context);
            if (localIp != null && localIp.startsWith("192.168.137.")) {
                // En Windows hotspot, el gateway típicamente es 192.168.137.1
                String gateway = "192.168.137.1";
                Log.d(TAG, "Usando gateway estándar de Windows hotspot: " + gateway);
                return gateway;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error al detectar IP del Desktop: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Convierte un entero de IP a String formato x.x.x.x
     */
    private static String intToIp(int ip) {
        return String.format("%d.%d.%d.%d",
            (ip & 0xff),
            (ip >> 8 & 0xff),
            (ip >> 16 & 0xff),
            (ip >> 24 & 0xff));
    }
    
    /**
     * Verifica si una IP es válida y de red local/VPN
     * Incluye rangos privados RFC1918 + Tailscale CGNAT
     */
    private static boolean isValidLocalIp(String ip) {
        if (ip == null || ip.equals("0.0.0.0") || ip.equals("127.0.0.1")) {
            return false;
        }

        // Rangos de IP privadas RFC1918
        if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
            return true;
        }

        // Rango 172.16.0.0 - 172.31.255.255
        if (ip.startsWith("172.")) {
            try {
                String[] parts = ip.split("\\.");
                int secondOctet = Integer.parseInt(parts[1]);
                if (secondOctet >= 16 && secondOctet <= 31) {
                    return true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error validando IP 172.x: " + ip);
            }
        }

        // ✅ Rango CGNAT de Tailscale: 100.64.0.0/10 (100.64.0.0 - 100.127.255.255)
        if (ip.startsWith("100.")) {
            try {
                String[] parts = ip.split("\\.");
                int secondOctet = Integer.parseInt(parts[1]);
                if (secondOctet >= 64 && secondOctet <= 127) {
                    Log.d(TAG, "IP Tailscale válida: " + ip);
                    return true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error validando IP Tailscale: " + ip);
            }
        }

        return false;
    }
    
    /**
     * Obtiene el SSID de la red WiFi actual (compatible con Android 9+)
     */
    @Nullable
    private static String getWifiSsid(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            
            if (wifiManager == null) {
                Log.e(TAG, "WifiManager no disponible");
                return null;
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ConnectivityManager cm = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
                
                if (cm != null) {
                    Network network = cm.getActiveNetwork();
                    if (network != null) {
                        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                        if (capabilities != null && 
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            
                            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                            if (wifiInfo != null) {
                                String ssid = wifiInfo.getSSID();
                                return cleanSsid(ssid);
                            }
                        }
                    }
                }
            } else {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    String ssid = wifiInfo.getSSID();
                    return cleanSsid(ssid);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permisos insuficientes para obtener SSID. Necesitas: " +
                  "ACCESS_FINE_LOCATION y ACCESS_WIFI_STATE", e);
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener SSID: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Limpia el SSID removiendo comillas y prefijos
     */
    @Nullable
    private static String cleanSsid( @Nullable String ssid) {
        if (ssid == null || ssid.equals("<unknown ssid>") || ssid.equals("")) {
            return null;
        }
        
        // Remover comillas dobles y simples que Android puede agregar
        ssid = ssid.replace("\"", "").replace("'", "");
        
        // Remover espacios al inicio/final
        ssid = ssid.trim();
        
        return ssid;
    }
    
    /**
     * Obtiene la dirección IP local del dispositivo
     */
    @Nullable
    private static String getLocalIpAddress(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    int ipInt = wifiInfo.getIpAddress();
                    if (ipInt != 0) {
                        String ip = String.format("%d.%d.%d.%d",
                            (ipInt & 0xff),
                            (ipInt >> 8 & 0xff),
                            (ipInt >> 16 & 0xff),
                            (ipInt >> 24 & 0xff));
                        
                        if (!ip.equals("0.0.0.0")) {
                            return ip;
                        }
                    }
                }
            }
            
            List<NetworkInterface> interfaces = Collections.list(
                NetworkInterface.getNetworkInterfaces());
            
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener IP local: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Obtiene el tipo de conexión actual
     */
    @NonNull
    private static String getConnectionType(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (cm != null) {
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                    if (capabilities != null) {
                        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            return "WiFi";
                        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            return "Móvil";
                        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            return "Ethernet";
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener tipo de conexión: " + e.getMessage(), e);
        }
        
        return "Desconocido";
    }
    
    /**
     * Clase interna para configuración de red
     */
    private static class NetworkConfig {
        final String name;
        final String host;
        final String ipPrefix;
        final boolean isDynamic;
        
        NetworkConfig(String name, String host, String ipPrefix, boolean isDynamic) {
            this.name = name;
            this.host = host;
            this.ipPrefix = ipPrefix;
            this.isDynamic = isDynamic;
        }
    }
    
    /**
     * Obtiene información del gateway (IP de la PC que comparte internet)n     */
    @Nullable
    public static String getGatewayIp(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            
            if (wifiManager != null) {
                android.net.DhcpInfo dhcp = wifiManager.getDhcpInfo();
                if (dhcp != null && dhcp.gateway != 0) {
                    return intToIp(dhcp.gateway);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener gateway: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Información detallada de la red actual (para debugging)
     */
    public static String getNetworkInfo(Context context) {
        initConfigManager(context);

        StringBuilder info = new StringBuilder();
        info.append("=== INFORMACIÓN DE RED COMPLETA ===\n\n");

        // Información de configuración
        info.append("--- Configuración ---\n");
        info.append(configManager.getConfigInfo()).append("\n");

        // Información de red actual
        info.append("--- Red Actual ---\n");
        info.append("SSID: ").append(getWifiSsid(context)).append("\n");
        info.append("IP Local: ").append(getLocalIpAddress(context)).append("\n");
        info.append("Gateway: ").append(getGatewayIp(context)).append("\n");
        info.append("Tipo: ").append(getConnectionType(context)).append("\n");

        // Detección de Tailscale
        String tailscale = detectTailscaleConnection(context);
        info.append("Tailscale: ").append(tailscale != null ? "Activo (" + tailscale + ")" : "Inactivo").append("\n");

        // Host final detectado
        info.append("\n--- Resultado ---\n");
        info.append("Host seleccionado: ").append(detectCurrentHost(context)).append("\n");

        if (cachedDesktopIp != null) {
            info.append("IP Desktop cacheada: ").append(cachedDesktopIp).append("\n");
        }

        return info.toString();
    }
    
    /**
     * Limpia el cache de IP del desktop (útil para forzar nueva detección)
     */
    public static void clearCache() {
        cachedDesktopIp = null;
        Log.d(TAG, "Cache de IP del Desktop limpiado");
    }

    /**
     * Obtiene el gestor de configuración de red
     * Útil para configurar IPs manualmente desde la app
     */
    public static NetworkConfigManager getConfigManager(Context context) {
        initConfigManager(context);
        return configManager;
    }

    /**
     * Configura manualmente la IP del servidor Tailscale
     */
    public static void setTailscaleServerIp(Context context, String ip) {
        initConfigManager(context);
        configManager.setTailscaleServerIp(ip);
        Log.i(TAG, "IP de Tailscale actualizada a: " + ip);
    }

    /**
     * Configura manualmente una IP personalizada
     */
    public static void setManualIp(Context context, String ip) {
        initConfigManager(context);
        configManager.setManualIp(ip);
        Log.i(TAG, "IP manual configurada: " + ip);
    }

    /**
     * Limpia la configuración manual y resetea a detección automática
     */
    public static void resetToAutoDetect(Context context) {
        initConfigManager(context);
        configManager.clearManualIp();
        configManager.setAutoDetectEnabled(true);
        clearCache();
        Log.i(TAG, "Configuración reseteada a auto-detección");
    }

    /**
     * Verifica si Tailscale está activo en este momento
     */
    public static boolean isTailscaleActive(Context context) {
        return detectTailscaleConnection(context) != null;
    }

    /**
     * Corrige URLs del emulador de Firebase Storage reemplazando la IP original
     * por la IP actual del emulador.
     * Útil cuando las imágenes se subieron con una IP (ej. red trabajo) y se intentan ver
     * desde otra red (ej. casa), ya que la URL guardada tiene la IP antigua "quemada".
     */
    public static String fixEmulatorUrl(String url, Context context) {
        if (url == null || url.isEmpty()) return url;

        // Corregir URLs malformadas (http:/ en lugar de http://)
        if (url.startsWith("http:/") && !url.startsWith("http://")) {
            url = url.replace("http:/", "http://");
            Log.w(TAG, "URL malformada corregida: agregada barra faltante");
        }

        // Verificar si es una URL del emulador (puerto 9199 por defecto de Storage)
        if (url.contains(":9199")) {
            try {
                // Extraer el host actual
                String currentHost = detectCurrentHost(context);

                // Encontrar la parte de la IP en la URL: http://[IP]:9199/...
                int protocolEnd = url.indexOf("://");
                if (protocolEnd != -1) {
                    int ipStart = protocolEnd + 3;
                    int portStart = url.indexOf(":9199", ipStart);

                    if (portStart != -1) {
                        String oldIp = url.substring(ipStart, portStart);

                        // Solo reemplazamos si la IP es diferente
                        if (!oldIp.equals(currentHost)) {
                            String fixedUrl = url.replace("http://" + oldIp + ":9199", "http://" + currentHost + ":9199");
                            Log.d(TAG, "URL de emulador corregida: " + oldIp + " -> " + currentHost);
                            return fixedUrl;
                        } else {
                            Log.v(TAG, "URL ya tiene la IP correcta: " + currentHost);
                        }
                    }
                } else {
                    Log.w(TAG, "URL del emulador sin formato correcto: " + url);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error corrigiendo URL de emulador: " + e.getMessage(), e);
            }
        }
        return url;
    }
}
