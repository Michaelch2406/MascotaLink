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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class NetworkDetector {
    private static final String TAG = "NetworkDetector";
    
    // Configuración de redes
    private static final Map<String, NetworkConfig> NETWORK_CONFIGS = new HashMap<>();
    
    static {
        // Casa - Red fija
        NETWORK_CONFIGS.put("INNO_FLIA_CHASIGUANO_5G", 
            new NetworkConfig("Casa", "192.168.0.147", "192.168.0", false));
        
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
    
    private static final String DEFAULT_HOST = "10.246.204.132"; // Hotspot como default
    private static String cachedDesktopIp = null; // Cache para la IP del desktop
    
    /**
     * Detecta la red actual usando múltiples métodos para mayor precisión
     */
    @NonNull
    public static String detectCurrentHost(Context context) {
        try {
            // Método 1: Por SSID
            String ssid = getWifiSsid(context);
            if (ssid != null) {
                NetworkConfig config = NETWORK_CONFIGS.get(ssid);
                if (config != null) {
                    // Si es una red con IP dinámica, detectarla
                    if (config.isDynamic) {
                        String detectedIp = detectDesktopIp(context);
                        if (detectedIp != null) {
                            cachedDesktopIp = detectedIp;
                            Log.i(TAG, "✓ Red Desktop detectada con IP dinámica: " + detectedIp);
                            return detectedIp;
                        } else if (cachedDesktopIp != null) {
                            Log.i(TAG, "✓ Usando IP Desktop cacheada: " + cachedDesktopIp);
                            return cachedDesktopIp;
                        } else {
                            Log.w(TAG, "No se pudo detectar IP del Desktop, usando última conocida");
                            return "10.10.0.142"; // Fallback a tu última IP conocida
                        }
                    }
                    
                    // Red con IP fija
                    Log.i(TAG, "✓ Red detectada por SSID: " + config.name + 
                          " (" + ssid + ") -> " + config.host);
                    return config.host;
                }
                Log.w(TAG, "SSID desconocido: " + ssid);
            }
            
            // Método 2: Por rango de IP local
            String localIp = getLocalIpAddress(context);
            if (localIp != null) {
                Log.d(TAG, "IP local detectada: " + localIp);
                
                // Si estamos en el rango de Windows hotspot (192.168.137.x)
                if (localIp.startsWith("192.168.137.")) {
                    String desktopIp = detectDesktopIp(context);
                    if (desktopIp != null) {
                        cachedDesktopIp = desktopIp;
                        Log.i(TAG, "✓ Desktop hotspot detectado por IP, host: " + desktopIp);
                        return desktopIp;
                    } else if (cachedDesktopIp != null) {
                        Log.i(TAG, "✓ Usando IP Desktop cacheada: " + cachedDesktopIp);
                        return cachedDesktopIp;
                    }
                }
                
                // Buscar en otras configuraciones
                for (NetworkConfig config : NETWORK_CONFIGS.values()) {
                    if (!config.isDynamic && localIp.startsWith(config.ipPrefix)) {
                        Log.i(TAG, "✓ Red detectada por IP: " + config.name + 
                              " (IP: " + localIp + ") -> " + config.host);
                        return config.host;
                    }
                }
                Log.w(TAG, "IP local no coincide con redes conocidas: " + localIp);
            }
            
            // Método 3: Verificar tipo de conexión
            String connectionType = getConnectionType(context);
            Log.i(TAG, "Tipo de conexión: " + connectionType);
            
        } catch (Exception e) {
            Log.e(TAG, "Error al detectar red: " + e.getMessage(), e);
        }
        
        Log.w(TAG, "Usando host por defecto: " + DEFAULT_HOST);
        return DEFAULT_HOST;
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
     * Verifica si una IP es válida y de red local
     */
    private static boolean isValidLocalIp(String ip) {
        if (ip == null || ip.equals("0.0.0.0")) {
            return false;
        }
        
        // Rangos de IP privadas
        return ip.startsWith("192.168.") || 
               ip.startsWith("10.") || 
               ip.startsWith("172.16.") ||
               ip.startsWith("172.17.") ||
               ip.startsWith("172.18.") ||
               ip.startsWith("172.19.") ||
               ip.startsWith("172.20.") ||
               ip.startsWith("172.21.") ||
               ip.startsWith("172.22.") ||
               ip.startsWith("172.23.") ||
               ip.startsWith("172.24.") ||
               ip.startsWith("172.25.") ||
               ip.startsWith("172.26.") ||
               ip.startsWith("172.27.") ||
               ip.startsWith("172.28.") ||
               ip.startsWith("172.29.") ||
               ip.startsWith("172.30.") ||
               ip.startsWith("172.31.");
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
        StringBuilder info = new StringBuilder();
        info.append("=== INFORMACIÓN DE RED ===\n");
        info.append("SSID: ").append(getWifiSsid(context)).append("\n");
        info.append("IP Local: ").append(getLocalIpAddress(context)).append("\n");
        info.append("Gateway (IP PC): ").append(getGatewayIp(context)).append("\n");
        info.append("Tipo: ").append(getConnectionType(context)).append("\n");
        info.append("Host detectado: ").append(detectCurrentHost(context)).append("\n");
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
}
