package com.mjc.mascotalink.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades para prevenir memory leaks en Android
 *
 * Características:
 * - WeakReference wrappers para Context
 * - Auto-cleanup de callbacks y listeners
 * - Handler con WeakReference
 * - Lifecycle observers automáticos
 */
public class LifecycleHelper {

    private static final String TAG = "LifecycleHelper";

    /**
     * Wrapper para Context con WeakReference
     * Previene memory leaks cuando se mantienen referencias a Context
     */
    public static class WeakContext {
        private final WeakReference<Context> contextRef;

        public WeakContext(Context context) {
            this.contextRef = new WeakReference<>(context);
        }

        public Context get() {
            return contextRef.get();
        }

        public boolean isValid() {
            return contextRef.get() != null;
        }
    }

    /**
     * Handler con WeakReference
     * Previene memory leaks de handlers que mantienen referencias a Activities
     */
    public static class WeakHandler extends Handler {
        private final WeakReference<Callback> callbackRef;

        public WeakHandler(Callback callback) {
            super(Looper.getMainLooper());
            this.callbackRef = new WeakReference<>(callback);
        }

        @Override
        public void handleMessage(@NonNull android.os.Message msg) {
            Callback callback = callbackRef.get();
            if (callback != null) {
                callback.handleMessage(msg);
            } else {
                Log.w(TAG, "⚠️ Callback ya fue garbage collected");
            }
        }

        public interface Callback {
            void handleMessage(android.os.Message msg);
        }
    }

    /**
     * Cleanup automático de recursos según lifecycle
     */
    public static class LifecycleCleanup implements LifecycleEventObserver {
        private final List<Runnable> cleanupTasks = new ArrayList<>();
        private boolean isDestroyed = false;

        /**
         * Registra una tarea de cleanup
         */
        public void registerCleanup(Runnable task) {
            if (!isDestroyed) {
                cleanupTasks.add(task);
            } else {
                Log.w(TAG, "⚠️ Lifecycle ya destruido, ejecutando cleanup inmediatamente");
                task.run();
            }
        }

        /**
         * Ejecuta todas las tareas de cleanup
         */
        private void executeCleanup() {
            Log.d(TAG, "🧹 Ejecutando " + cleanupTasks.size() + " tareas de cleanup");
            for (Runnable task : cleanupTasks) {
                try {
                    task.run();
                } catch (Exception e) {
                    Log.e(TAG, "Error en tarea de cleanup", e);
                }
            }
            cleanupTasks.clear();
            isDestroyed = true;
        }

        @Override
        public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
            if (event == Lifecycle.Event.ON_DESTROY) {
                executeCleanup();
                source.getLifecycle().removeObserver(this);
            }
        }

        /**
         * Helper para registrar múltiples cleanups
         */
        public static LifecycleCleanup attach(LifecycleOwner owner) {
            LifecycleCleanup cleanup = new LifecycleCleanup();
            owner.getLifecycle().addObserver(cleanup);
            return cleanup;
        }
    }

    /**
     * Callback con auto-cleanup
     * Se desregistra automáticamente cuando el lifecycle se destruye
     */
    public static abstract class LifecycleCallback<T> {
        private final WeakReference<T> targetRef;
        private boolean isActive = true;

        public LifecycleCallback(T target, LifecycleOwner owner) {
            this.targetRef = new WeakReference<>(target);

            // Auto-cleanup
            owner.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    isActive = false;
                    onCleanup();
                }
            });
        }

        protected T getTarget() {
            return isActive ? targetRef.get() : null;
        }

        protected boolean isActive() {
            return isActive && targetRef.get() != null;
        }

        /**
         * Override este método para implementar lógica de cleanup
         */
        protected void onCleanup() {
            // Override en subclases si es necesario
        }
    }

    /**
     * Runnable con WeakReference
     * Previene memory leaks de Runnables posted a Handlers
     */
    public static class WeakRunnable implements Runnable {
        private final WeakReference<Runnable> runnableRef;

        public WeakRunnable(Runnable runnable) {
            this.runnableRef = new WeakReference<>(runnable);
        }

        @Override
        public void run() {
            Runnable runnable = runnableRef.get();
            if (runnable != null) {
                runnable.run();
            } else {
                Log.w(TAG, "⚠️ Runnable ya fue garbage collected");
            }
        }
    }

    /**
     * Detector simple de memory leaks (para debug)
     */
    public static class LeakDetector {
        private static final List<WeakReference<?>> trackedObjects = new ArrayList<>();

        /**
         * Trackea un objeto para detectar leaks
         */
        public static <T> void track(T object, String tag) {
            trackedObjects.add(new WeakReference<>(object));
            Log.d(TAG, "📍 Tracking: " + tag + " (total: " + trackedObjects.size() + ")");
        }

        /**
         * Fuerza garbage collection y reporta objetos que aún existen
         */
        public static void checkLeaks() {
            System.gc();
            System.runFinalization();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int aliveCount = 0;
            List<WeakReference<?>> toRemove = new ArrayList<>();

            for (WeakReference<?> ref : trackedObjects) {
                if (ref.get() != null) {
                    aliveCount++;
                } else {
                    toRemove.add(ref);
                }
            }

            trackedObjects.removeAll(toRemove);

            if (aliveCount > 0) {
                Log.w(TAG, "⚠️ Posibles leaks: " + aliveCount + " objetos aún en memoria");
            } else {
                Log.d(TAG, "✅ No se detectaron leaks");
            }
        }
    }

    /**
     * Helper para evitar crashes por Context null
     */
    public static boolean isContextValid(Context context) {
        if (context == null) {
            Log.w(TAG, "⚠️ Context es null");
            return false;
        }

        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                Log.w(TAG, "⚠️ Activity ya está finishing/destroyed");
                return false;
            }
        }

        return true;
    }

    /**
     * Post un Runnable de forma segura con WeakReference
     */
    public static void postSafely(Handler handler, Runnable runnable, long delayMillis) {
        if (handler != null && runnable != null) {
            handler.postDelayed(new WeakRunnable(runnable), delayMillis);
        }
    }

    /**
     * Remueve callbacks de forma segura
     */
    public static void removeCallbacksSafely(Handler handler, Runnable runnable) {
        if (handler != null) {
            handler.removeCallbacks(runnable);
        }
    }
}
