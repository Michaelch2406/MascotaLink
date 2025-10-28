package com.mjc.mascotalink;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.SupplicantState; // Added for SupplicantState

import android.util.Log;

import com.google.android.libraries.places.api.Places;
import com.google.firebase.FirebaseApp;
// import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
// import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";
    private static final String EMULATOR_HOST_CASA = "192.168.0.147";
    private static final String EMULATOR_HOST_TRABAJO = "10.10.0.142";
    // TODO: Reemplazar con los SSIDs reales de tus redes Wi-Fi
    private static final String SSID_CASA = "INNO_FLIA_CHASIGUANO_5G";
    private static final String SSID_TRABAJO = "ESTUDIANTES_IST";

    private static String currentEmulatorHost; // Made static

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);

        currentEmulatorHost = EMULATOR_HOST_CASA; // Default a casa
        String currentSsid = getCurrentWifiSsid();

        if (currentSsid != null) {
            if (currentSsid.equals(SSID_TRABAJO)) {
                currentEmulatorHost = EMULATOR_HOST_TRABAJO;
                Log.d(TAG, "Red de trabajo detectada. Usando EMULATOR_HOST_TRABAJO: " + currentEmulatorHost);
            } else if (currentSsid.equals(SSID_CASA)) {
                currentEmulatorHost = EMULATOR_HOST_CASA;
                Log.d(TAG, "Red de casa detectada. Usando EMULATOR_HOST_CASA: " + currentEmulatorHost);
            } else {
                Log.w(TAG, "SSID desconocido: " + currentSsid + ". Usando EMULATOR_HOST_CASA por defecto.");
            }
        } else {
            Log.e(TAG, "No se pudo obtener el SSID de la red Wi-Fi. Asegúrate de tener los permisos de ubicación y Wi-Fi.");
            Log.w(TAG, "Usando EMULATOR_HOST_CASA por defecto.");
        }

        /*
        // Initialize App Check
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance());
            Log.d(TAG, "Firebase App Check with Debug Provider initialized.");
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance());
            Log.d(TAG, "Firebase App Check with Play Integrity initialized.");
        }
        */

        // Setup centralizado de emuladores Firebase
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            auth.useEmulator(currentEmulatorHost, 9099);
            auth.setLanguageCode("es");
            Log.d(TAG, "FirebaseAuth emulador conectado a " + currentEmulatorHost + ":9099");
        } catch (IllegalStateException e) {
            Log.w(TAG, "FirebaseAuth emulador ya está configurado.");
        }

        try {
            FirebaseFirestore.getInstance().useEmulator(currentEmulatorHost, 8080);
            Log.d(TAG, "Firestore emulador conectado a " + currentEmulatorHost + ":8080");
        } catch (IllegalStateException e) {
            Log.w(TAG, "Firestore emulador ya está configurado.");
        }

        try {
            FirebaseStorage.getInstance().useEmulator(currentEmulatorHost, 9199);
            Log.d(TAG, "FirebaseStorage emulador conectado a " + currentEmulatorHost + ":9199");
        } catch (IllegalStateException e) {
            Log.w(TAG, "FirebaseStorage emulador ya está configurado.");
        }

        // Inicializa Google Places SDK
        try {
            ApplicationInfo app = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            String apiKey = app.metaData.getString("com.google.android.geo.API_KEY");
            if (apiKey != null && !apiKey.isEmpty()) {
                if (!Places.isInitialized()) {
                    Places.initialize(getApplicationContext(), apiKey);
                    Log.d(TAG, "Google Places SDK inicializado.");
                }
            } else {
                Log.e(TAG, "Google Places API Key no encontrada en el manifest.");
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "No se pudo cargar meta-data, NameNotFound: " + e.getMessage());
        }
    }

    // Método para detectar si usas el emulador (puedes personalizar la lógica)
    private boolean isUsingEmulator() {
        // Aquí puedes poner lógica adicional si cambias el host en desarrollo
        return EMULATOR_HOST_CASA.equals("localhost") || EMULATOR_HOST_CASA.startsWith("192."); // Adjusted to use a static host for this check
    }

    private String getCurrentWifiSsid() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                String ssid = wifiInfo.getSSID();
                if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    return ssid.substring(1, ssid.length() - 1); // Remove surrounding quotes
                }
                return ssid;
            }
        }
        return null;
    }

    public static String getCurrentEmulatorHost() {
        return currentEmulatorHost;
    }
}
