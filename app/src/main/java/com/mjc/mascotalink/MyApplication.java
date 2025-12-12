package com.mjc.mascotalink;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.Context;
import android.os.StrictMode;
import android.util.Log;

import com.google.android.libraries.places.api.Places;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.mjc.mascotalink.network.NetworkDetector;
import com.mjc.mascotalink.network.SocketManager;
import com.mjc.mascotalink.upload.UploadScheduler;
import com.mjc.mascotalink.notifications.FcmTokenSyncWorker;
import com.mjc.mascotalink.util.UnreadBadgeManager;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class MyApplication extends Application {

    private static final String TAG = "MyApplication";
    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();

        // Habilitar StrictMode en debug para detectar problemas de performance y memory leaks
        if (BuildConfig.DEBUG) {
            enableStrictMode();
        }

        appContext = getApplicationContext();
        FirebaseApp.initializeApp(this);

        // Detectar red actual automáticamente
        String emulatorHost = NetworkDetector.detectCurrentHost(this);

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
            auth.useEmulator(emulatorHost, 9099);
            auth.setLanguageCode("es");
            Log.d(TAG, "FirebaseAuth emulador conectado a " + emulatorHost + ":9099");
        } catch (IllegalStateException e) {
            Log.w(TAG, "FirebaseAuth emulador ya está configurado.");
        }

        try {
            FirebaseFirestore.getInstance().useEmulator(emulatorHost, 8080);
            Log.d(TAG, "Firestore emulador conectado a " + emulatorHost + ":8080");
        } catch (IllegalStateException e) {
            Log.w(TAG, "Firestore emulador ya está configurado.");
        }

        try {
            FirebaseStorage.getInstance().useEmulator(emulatorHost, 9199);
            Log.d(TAG, "FirebaseStorage emulador conectado a " + emulatorHost + ":9199");
        } catch (IllegalStateException e) {
            Log.w(TAG, "FirebaseStorage emulador ya está configurado.");
        }

        try {
            com.google.firebase.functions.FirebaseFunctions.getInstance().useEmulator(emulatorHost, 5001);
            Log.d(TAG, "Functions emulador conectado a " + emulatorHost + ":5001");
        } catch (IllegalStateException e) {
            Log.w(TAG, "Functions emulador ya está configurado.");
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

        // Para debugging: mostrar info completa
        if (BuildConfig.DEBUG) {
            Log.d(TAG, NetworkDetector.getNetworkInfo(this));
        }

        // Inicializar WebSocket
        SocketManager.getInstance(this).connect();

        // Reanudar subidas pendientes al arrancar (WorkManager ya las persistió)
        UploadScheduler.retryPendingUploads(this);

        // Sincronizar FCM y badges si ya hay sesión
        FirebaseAuth authInstance = FirebaseAuth.getInstance();
        if (authInstance.getCurrentUser() != null) {
            FcmTokenSyncWorker.enqueueNow(this);
            UnreadBadgeManager.start(authInstance.getCurrentUser().getUid());
        }
    }

    public static String getCurrentEmulatorHost(Context context) {
        return NetworkDetector.detectCurrentHost(context);
    }

    public static Context getAppContext() {
        return appContext;
    }

    /**
     * Helper estático para corregir URLs del emulador en toda la app.
     */
    public static String getFixedUrl(String url) {
        if (appContext == null) return url;
        return NetworkDetector.fixEmulatorUrl(url, appContext);
    }

    /**
     * Habilita StrictMode para detectar problemas de performance y memory leaks en debug
     *
     * StrictMode detecta:
     * - Operaciones de disco en el main thread
     * - Operaciones de red en el main thread
     * - Leaks de objetos (Activities, Services, etc.)
     * - Cursores sin cerrar
     * - Closeable sin cerrar
     */
    private void enableStrictMode() {
        Log.d(TAG, "🔍 Habilitando StrictMode para debug");

        // Thread Policy: detecta operaciones bloqueantes en el main thread
        StrictMode.ThreadPolicy threadPolicy = new StrictMode.ThreadPolicy.Builder()
                .detectAll() // Detectar todas las violaciones
                .penaltyLog() // Log en logcat
                //.penaltyFlashScreen() // Flash rojo en pantalla (visual)
                .build();

        // VM Policy: detecta leaks y problemas de recursos
        StrictMode.VmPolicy vmPolicy = new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects() // SQLite cursors sin cerrar
                .detectLeakedClosableObjects() // Streams, files sin cerrar
                .detectActivityLeaks() // Activities leakeadas
                .detectLeakedRegistrationObjects() // BroadcastReceivers sin unregister
                .penaltyLog() // Log en logcat
                // .penaltyDeath() // Crashea la app (muy estricto, usar solo en testing)
                .build();

        StrictMode.setThreadPolicy(threadPolicy);
        StrictMode.setVmPolicy(vmPolicy);

        Log.d(TAG, "✅ StrictMode habilitado - Se logearán violaciones en debug");
    }
}
