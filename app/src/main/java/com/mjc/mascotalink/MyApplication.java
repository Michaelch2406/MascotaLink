package com.mjc.mascotalink;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
    private static final String EMULATOR_HOST = "192.168.0.147"; // tu emulador

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);

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
            auth.useEmulator(EMULATOR_HOST, 9099);
            auth.setLanguageCode("es");
            Log.d(TAG, "FirebaseAuth emulador conectado a " + EMULATOR_HOST + ":9099");
        } catch (IllegalStateException e) {
            Log.w(TAG, "FirebaseAuth emulador ya está configurado.");
        }

        try {
            FirebaseFirestore.getInstance().useEmulator(EMULATOR_HOST, 8080);
            Log.d(TAG, "Firestore emulador conectado a " + EMULATOR_HOST + ":8080");
        } catch (IllegalStateException e) {
            Log.w(TAG, "Firestore emulador ya está configurado.");
        }

        try {
            FirebaseStorage.getInstance().useEmulator(EMULATOR_HOST, 9199);
            Log.d(TAG, "FirebaseStorage emulador conectado a " + EMULATOR_HOST + ":9199");
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
        return EMULATOR_HOST.equals("localhost") || EMULATOR_HOST.startsWith("192.");
    }
}
