
package com.mjc.mascotalink.network;

import android.util.Log;

import com.mjc.mascotalink.MyApplication;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

/**
 * Cliente API optimizado con:
 * - Timeouts reducidos (15s connect, 20s read/write)
 * - Caché HTTP (10MB, 15 min para datos estáticos)
 * - Retry automático con backoff exponencial (3 intentos)
 * - Compresión GZIP automática
 */
public class APIClient {
    private static final String TAG = "APIClient";
    private static Retrofit retrofit;

    // Timeouts optimizados
    private static final int CONNECT_TIMEOUT = 15; // Reducido de 30s
    private static final int READ_TIMEOUT = 20;    // Reducido de 30s
    private static final int WRITE_TIMEOUT = 20;   // Reducido de 30s

    // Configuración de caché
    private static final int CACHE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int CACHE_MAX_AGE = 15 * 60; // 15 minutos

    // Configuración de retry
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY = 1000; // 1 segundo

    public static Retrofit getRetrofit() {
        if (retrofit == null) {
            // Logging interceptor (solo en debug)
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            // Configurar caché HTTP
            File cacheDir = new File(MyApplication.getAppContext().getCacheDir(), "http_cache");
            Cache cache = new Cache(cacheDir, CACHE_SIZE);

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    // Interceptors
                    .addInterceptor(loggingInterceptor)
                    .addInterceptor(new RetryInterceptor())
                    .addNetworkInterceptor(new CacheInterceptor())

                    // Caché
                    .cache(cache)

                    // Timeouts optimizados
                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)

                    // Pool de conexiones (para reutilización)
                    .connectionPool(new okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))

                    .build();

            // Dynamically get the base URL from MyApplication
            String dynamicBaseUrl = "http://" + MyApplication.getCurrentEmulatorHost(MyApplication.getAppContext()) + ":8000/";

            retrofit = new Retrofit.Builder()
                    .baseUrl(dynamicBaseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static AuthService getAuthService() {
        return getRetrofit().create(AuthService.class);
    }

    /**
     * Interceptor de caché HTTP
     * Cachea respuestas GET por 15 minutos
     */
    private static class CacheInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());

            // Solo cachear GET requests exitosos
            if (chain.request().method().equals("GET") && response.isSuccessful()) {
                CacheControl cacheControl = new CacheControl.Builder()
                        .maxAge(CACHE_MAX_AGE, TimeUnit.SECONDS)
                        .build();

                return response.newBuilder()
                        .header("Cache-Control", cacheControl.toString())
                        .removeHeader("Pragma") // Remover header conflictivo
                        .build();
            }

            return response;
        }
    }

    /**
     * Interceptor de retry con backoff exponencial
     * Reintenta automáticamente requests fallidos (max 3 veces)
     */
    private static class RetryInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request request = chain.request();
            Response response = null;
            IOException lastException = null;

            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                try {
                    if (attempt > 0) {
                        // Backoff exponencial: 1s, 2s, 4s
                        long delay = INITIAL_RETRY_DELAY * (1L << (attempt - 1));
                        Log.d(TAG, "Retry intento " + attempt + "/" + MAX_RETRIES +
                                  " después de " + delay + "ms");
                        Thread.sleep(delay);
                    }

                    response = chain.proceed(request);

                    // Si la respuesta es exitosa, retornar
                    if (response.isSuccessful()) {
                        if (attempt > 0) {
                            Log.d(TAG, "✅ Request exitoso después de " + attempt + " reintentos");
                        }
                        return response;
                    }

                    // Si es error 4xx (client error), no reintentar
                    if (response.code() >= 400 && response.code() < 500) {
                        Log.w(TAG, "Error 4xx, no reintentando: " + response.code());
                        return response;
                    }

                    // Error 5xx o de red, reintentar
                    if (response.code() >= 500) {
                        Log.w(TAG, "Error 5xx, reintentando: " + response.code());
                        response.close(); // Cerrar respuesta antes de reintentar
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrumpido", e);
                } catch (IOException e) {
                    lastException = e;
                    Log.w(TAG, "⚠️ Request falló (intento " + (attempt + 1) + "/" +
                              (MAX_RETRIES + 1) + "): " + e.getMessage());

                    // En el último intento, lanzar la excepción
                    if (attempt == MAX_RETRIES) {
                        throw e;
                    }
                }
            }

            // Si llegamos aquí, todos los reintentos fallaron
            if (lastException != null) {
                throw lastException;
            }

            // Fallback: retornar última respuesta (aunque sea error)
            return response;
        }
    }
}
