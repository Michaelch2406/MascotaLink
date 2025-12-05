package com.mjc.mascotalink.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

/**
 * Configuración global de Glide para Walki
 *
 * Optimizaciones implementadas:
 * - Memory cache optimizado (50MB)
 * - Disk cache configurado (250MB)
 * - Formato RGB_565 por defecto (50% menos memoria)
 * - Caching strategy configurado
 * - Bitmap pool optimizado
 */
@GlideModule
public class WalkiGlideModule extends AppGlideModule {

    private static final String TAG = "WalkiGlideModule";

    // Tamaños de cache optimizados para la app
    private static final int DISK_CACHE_SIZE = 250 * 1024 * 1024; // 250MB
    private static final int MEMORY_CACHE_SIZE = 50 * 1024 * 1024; // 50MB

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // === MEMORY CACHE ===
        // Configurar cache de memoria a 50MB (optimizado para 40+ actividades)
        MemorySizeCalculator calculator = new MemorySizeCalculator.Builder(context)
                .setMemoryCacheScreens(2f) // Mantener 2 pantallas en memoria
                .setBitmapPoolScreens(3f)  // Pool de 3 pantallas
                .build();

        builder.setMemoryCache(new LruResourceCache(MEMORY_CACHE_SIZE));

        // === DISK CACHE ===
        // Configurar cache de disco a 250MB con nombre personalizado
        builder.setDiskCache(new InternalCacheDiskCacheFactory(
                context,
                "walki_image_cache",  // Nombre del directorio
                DISK_CACHE_SIZE
        ));

        // === DECODE FORMAT ===
        // RGB_565: 2 bytes por pixel vs ARGB_8888: 4 bytes por pixel
        // Ahorro de 50% en memoria, imperceptible para fotos de perfil/mascotas
        builder.setDefaultRequestOptions(
                new RequestOptions()
                        .format(DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache la imagen procesada
                        .encodeQuality(80) // Calidad de compresión al guardar
        );

        // === LOGGING ===
        // Solo en debug
        if (android.util.Log.isLoggable(TAG, Log.DEBUG)) {
            builder.setLogLevel(Log.DEBUG);
        } else {
            builder.setLogLevel(Log.ERROR);
        }

        Log.d(TAG, "✅ Glide configurado - Memory: " + (MEMORY_CACHE_SIZE / 1024 / 1024) +
                "MB, Disk: " + (DISK_CACHE_SIZE / 1024 / 1024) + "MB");
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        // Aquí se pueden registrar componentes personalizados como:
        // - Custom ModelLoaders para Firebase Storage URLs
        // - Custom decoders
        // - Custom encoders
        // Por ahora usamos configuración por defecto
    }

    @Override
    public boolean isManifestParsingEnabled() {
        // Desactivar parsing de manifest para evitar overhead
        // Todas las configuraciones se hacen programáticamente aquí
        return false;
    }
}
