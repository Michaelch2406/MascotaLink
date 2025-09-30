package com.mjc.mascotalink;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);

        // Initialize App Check
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance());
        Log.d(TAG, "Firebase App Check with Play Integrity initialized.");

        // Centralized Firebase Emulator Setup
        String host = "192.168.0.147"; // Or retrieve from a config file

        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            auth.useEmulator(host, 9099);
            auth.setLanguageCode("es"); // Set language code globally
            Log.d(TAG, "FirebaseAuth emulator connected to " + host + ":9099");
        } catch (IllegalStateException e) {
            Log.w(TAG, "FirebaseAuth emulator already set up.");
        }

        try {
            FirebaseFirestore.getInstance().useEmulator(host, 8080);
            Log.d(TAG, "Firestore emulator connected to " + host + ":8080");
        } catch (IllegalStateException e) {
            Log.w(TAG, "Firestore emulator already set up.");
        }

        try {
            FirebaseStorage.getInstance().useEmulator(host, 9199);
            Log.d(TAG, "FirebaseStorage emulator connected to " + host + ":9199");
        } catch (IllegalStateException e) {
            Log.w(TAG, "FirebaseStorage emulator already set up.");
        }
    }
}
