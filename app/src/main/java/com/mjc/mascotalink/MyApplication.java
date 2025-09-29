package com.mjc.mascotalink;

import android.app.Application;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Centralized Firebase Emulator Setup
        String host = "192.168.0.147"; // Or retrieve from a config file

        try {
            FirebaseAuth.getInstance().useEmulator(host, 9099);
        } catch (IllegalStateException e) {
            // Emulator may already be set, ignore
        }

        try {
            FirebaseFirestore.getInstance().useEmulator(host, 8080);
        } catch (IllegalStateException e) {
            // Emulator may already be set, ignore
        }

        try {
            FirebaseStorage.getInstance().useEmulator(host, 9199);
        } catch (IllegalStateException e) {
            // Emulator may already be set, ignore
        }
    }
}
