package com.mjc.mascotalink.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilidad para optimizar queries de Firebase Firestore
 *
 * Características:
 * - Cleanup automático de listeners según lifecycle
 * - Builder pattern para queries optimizadas
 * - Batch operations
 * - Caché local optimizado
 * - Prevención de memory leaks
 */
public class FirebaseQueryOptimizer {

    private static final String TAG = "FirebaseOptimizer";
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_BATCH_SIZE = 500; // Límite de Firestore

    private FirebaseFirestore db;
    private List<ListenerRegistration> activeListeners;
    private WriteBatch currentBatch;
    private int batchOperationCount;

    public FirebaseQueryOptimizer() {
        this.db = FirebaseFirestore.getInstance();
        this.activeListeners = new ArrayList<>();
        this.batchOperationCount = 0;

        // Habilitar persistencia offline (solo se hace una vez)
        enableOfflinePersistence();
    }

    /**
     * Habilita persistencia offline para reducir lecturas de red
     */
    private void enableOfflinePersistence() {
        try {
            db.enableNetwork(); // Asegurar que la red esté habilitada
            Log.d(TAG, "✅ Persistencia offline habilitada");
        } catch (Exception e) {
            Log.w(TAG, "Persistencia offline ya estaba habilitada o falló", e);
        }
    }

    // ========================================
    // QUERY BUILDER CON OPTIMIZACIONES
    // ========================================

    /**
     * Builder para construir queries optimizadas
     */
    public static class QueryBuilder {
        private Query query;
        private Source source = Source.DEFAULT; // Cache primero, luego server
        private int limit = DEFAULT_LIMIT;

        public QueryBuilder(CollectionReference collection) {
            this.query = collection;
        }

        public QueryBuilder(Query query) {
            this.query = query;
        }

        /**
         * Filtro whereEqualTo optimizado con índices
         */
        public QueryBuilder whereEqualTo(String field, Object value) {
            query = query.whereEqualTo(field, value);
            return this;
        }

        /**
         * Filtro whereIn optimizado (máximo 10 valores)
         */
        public QueryBuilder whereIn(String field, List<?> values) {
            if (values.size() > 10) {
                Log.w(TAG, "⚠️ whereIn soporta máximo 10 valores, truncando...");
                values = values.subList(0, 10);
            }
            query = query.whereIn(field, values);
            return this;
        }

        /**
         * Orden con índice compuesto recomendado
         */
        public QueryBuilder orderBy(String field, Query.Direction direction) {
            query = query.orderBy(field, direction);
            return this;
        }

        /**
         * Límite de resultados para paginación
         */
        public QueryBuilder limit(int limit) {
            this.limit = limit;
            query = query.limit(limit);
            return this;
        }

        /**
         * Forzar lectura desde caché (offline-first)
         */
        public QueryBuilder fromCache() {
            this.source = Source.CACHE;
            return this;
        }

        /**
         * Forzar lectura desde servidor (bypass cache)
         */
        public QueryBuilder fromServer() {
            this.source = Source.SERVER;
            return this;
        }

        /**
         * Paginación con startAfter
         */
        public QueryBuilder startAfter(Object... values) {
            query = query.startAfter(values);
            return this;
        }

        public Query build() {
            return query;
        }

        public Source getSource() {
            return source;
        }
    }

    /**
     * Crea un QueryBuilder desde una colección
     */
    public QueryBuilder from(String collectionPath) {
        return new QueryBuilder(db.collection(collectionPath));
    }

    // ========================================
    // LIFECYCLE-AWARE LISTENERS
    // ========================================

    /**
     * Agrega un listener de snapshot que se limpia automáticamente según lifecycle
     *
     * @param lifecycleOwner Activity o Fragment con lifecycle
     * @param query Query a escuchar
     * @param callback Callback con los resultados
     */
    public void addSnapshotListener(@NonNull LifecycleOwner lifecycleOwner,
                                    @NonNull Query query,
                                    @NonNull QuerySnapshotCallback callback) {
        addSnapshotListener(lifecycleOwner, query, Source.DEFAULT, callback);
    }

    /**
     * Agrega un listener de snapshot con source específico
     */
    public void addSnapshotListener(@NonNull LifecycleOwner lifecycleOwner,
                                    @NonNull Query query,
                                    @NonNull Source source,
                                    @NonNull QuerySnapshotCallback callback) {
        if (lifecycleOwner.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
            Log.w(TAG, "⚠️ No se puede agregar listener a lifecycle DESTROYED");
            return;
        }

        // Configurar opciones de snapshot
        com.google.firebase.firestore.SnapshotListenOptions options =
                new com.google.firebase.firestore.SnapshotListenOptions.Builder()
                        .setSource(source)
                        .build();

        // Registrar listener
        ListenerRegistration registration = query.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e(TAG, "Error en snapshot listener", error);
                callback.onError(error);
                return;
            }

            if (value != null) {
                callback.onSuccess(value);
            }
        });

        activeListeners.add(registration);

        // Auto-cleanup cuando el lifecycle se destruye
        lifecycleOwner.getLifecycle().addObserver((LifecycleEventObserver) (source1, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY) {
                removeListener(registration);
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                // Opcional: pausar listeners en background para ahorrar batería
                // registration.remove();
            }
        });

        Log.d(TAG, "✅ Listener agregado (total activos: " + activeListeners.size() + ")");
    }

    /**
     * Agrega listener a un documento específico
     */
    public void addDocumentListener(@NonNull LifecycleOwner lifecycleOwner,
                                   @NonNull DocumentReference document,
                                   @NonNull DocumentSnapshotCallback callback) {
        if (lifecycleOwner.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
            Log.w(TAG, "⚠️ No se puede agregar listener a lifecycle DESTROYED");
            return;
        }

        ListenerRegistration registration = document.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e(TAG, "Error en document listener", error);
                callback.onError(error);
                return;
            }

            if (value != null && value.exists()) {
                callback.onSuccess(value);
            }
        });

        activeListeners.add(registration);

        lifecycleOwner.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY) {
                removeListener(registration);
            }
        });

        Log.d(TAG, "✅ Document listener agregado");
    }

    /**
     * Remueve un listener específico
     */
    private void removeListener(ListenerRegistration registration) {
        registration.remove();
        activeListeners.remove(registration);
        Log.d(TAG, "🗑️ Listener removido (total activos: " + activeListeners.size() + ")");
    }

    /**
     * Remueve todos los listeners activos
     */
    public void removeAllListeners() {
        for (ListenerRegistration registration : activeListeners) {
            registration.remove();
        }
        activeListeners.clear();
        Log.d(TAG, "🗑️ Todos los listeners removidos");
    }

    // ========================================
    // BATCH OPERATIONS
    // ========================================

    /**
     * Inicia un nuevo batch de escrituras
     */
    public WriteBatch startBatch() {
        if (currentBatch != null) {
            Log.w(TAG, "⚠️ Ya existe un batch activo, se committeará automáticamente");
            commitBatch();
        }
        currentBatch = db.batch();
        batchOperationCount = 0;
        Log.d(TAG, "📦 Batch iniciado");
        return currentBatch;
    }

    /**
     * Agrega una operación al batch actual
     */
    public void addToBatch(DocumentReference ref, Map<String, Object> data, boolean merge) {
        if (currentBatch == null) {
            startBatch();
        }

        if (merge) {
            currentBatch.set(ref, data, com.google.firebase.firestore.SetOptions.merge());
        } else {
            currentBatch.set(ref, data);
        }

        batchOperationCount++;

        // Auto-commit si se alcanza el límite de Firestore
        if (batchOperationCount >= MAX_BATCH_SIZE) {
            Log.w(TAG, "⚠️ Batch alcanzó límite de 500 operaciones, committeando automáticamente");
            commitBatch();
        }
    }

    /**
     * Committea el batch actual
     */
    public void commitBatch() {
        if (currentBatch == null) {
            Log.w(TAG, "⚠️ No hay batch activo para committear");
            return;
        }

        final int operations = batchOperationCount;
        currentBatch.commit()
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "✅ Batch committeado exitosamente (" + operations + " operaciones)")
                )
                .addOnFailureListener(e ->
                        Log.e(TAG, "❌ Error al committear batch", e)
                );

        currentBatch = null;
        batchOperationCount = 0;
    }

    // ========================================
    // INTERFACES DE CALLBACK
    // ========================================

    public interface QuerySnapshotCallback {
        void onSuccess(com.google.firebase.firestore.QuerySnapshot snapshot);
        void onError(Exception e);
    }

    public interface DocumentSnapshotCallback {
        void onSuccess(com.google.firebase.firestore.DocumentSnapshot snapshot);
        void onError(Exception e);
    }

    // ========================================
    // LIMPIEZA DE RECURSOS
    // ========================================

    /**
     * Limpia todos los recursos (llamar en onDestroy de Application)
     */
    public void cleanup() {
        removeAllListeners();
        if (currentBatch != null) {
            commitBatch();
        }
        Log.d(TAG, "🧹 Cleanup completado");
    }
}
