package com.mjc.mascotalink.notifications;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.TimeUnit;

/**
 * Sincroniza el token FCM en Firestore sin depender solo de onNewToken.
 */
public class FcmTokenSyncWorker extends Worker {

    private static final String TAG = "FcmTokenSyncWorker";
    private static final String UNIQUE_WORK = "fcm_token_sync";

    public FcmTokenSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueueNow(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FcmTokenSyncWorker.class)
                .setConstraints(constraints)
                .setInitialDelay(0, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Log.d(TAG, "Sin usuario autenticado, nada que sincronizar");
                return Result.success();
            }

            String token = Tasks.await(FirebaseMessaging.getInstance().getToken());
            if (token == null || token.isEmpty()) {
                Log.w(TAG, "Token FCM vacio, reintentar");
                return Result.retry();
            }

            Tasks.await(
                    FirebaseFirestore.getInstance()
                            .collection("usuarios")
                            .document(user.getUid())
                            .update("fcmToken", token)
            );

            Log.d(TAG, "Token FCM sincronizado para uid=" + user.getUid());
            return Result.success();
        } catch (Exception e) {
            Log.w(TAG, "Error sincronizando token, reintentando: " + e.getMessage());
            return Result.retry();
        }
    }
}
