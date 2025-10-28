
package com.mjc.mascotalink.network;

import com.mjc.mascotalink.MyApplication; // Import MyApplication

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

public class APIClient {
    // La IP debe apuntar a la IP de tu máquina en la red local, no a localhost.
    // El puerto 8000 es el que usa Uvicorn/FastAPI por defecto.
    // private static final String BASE_URL = "http://192.168.0.147:8000/"; // Removed
    private static Retrofit retrofit;

    public static Retrofit getRetrofit() {
        if (retrofit == null) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            // Dynamically get the base URL from MyApplication
            String dynamicBaseUrl = "http://" + MyApplication.getCurrentEmulatorHost() + ":8000/";

            retrofit = new Retrofit.Builder()
                    .baseUrl(dynamicBaseUrl) // Used dynamic URL here
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static AuthService getAuthService() {
        return getRetrofit().create(AuthService.class);
    }
}
