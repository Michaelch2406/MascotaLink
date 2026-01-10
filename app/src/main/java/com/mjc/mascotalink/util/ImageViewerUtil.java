package com.mjc.mascotalink.util;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import com.mjc.mascotalink.GaleriaActivity;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.R;

import java.util.ArrayList;

/**
 * Utility para mostrar imágenes en fullscreen con zoom.
 * Proporciona una forma consistente de mostrar fotos con capacidad de expandir y hacer zoom.
 */
public class ImageViewerUtil {

    /**
     * Muestra una imagen en fullscreen con capacidad de zoom (pinch-to-zoom, double-tap, etc).
     *
     * @param context Contexto de la aplicación
     * @param imageUrl URL de la imagen a mostrar
     */
    public static void showFullscreenImage(Context context, String imageUrl) {
        if (context == null || imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        // Crear dialog con tema fullscreen negro
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Inflar layout del dialog
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_fullscreen_image, null);
        dialog.setContentView(dialogView);

        // Configurar ventana en fullscreen
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                           WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        }

        // Cargar imagen con Glide en PhotoView
        PhotoView photoView = dialogView.findViewById(R.id.iv_fullscreen);
        Glide.with(context)
                .load(MyApplication.getFixedUrl(imageUrl))
                .into(photoView);

        // Configurar botón cerrar
        View closeButton = dialogView.findViewById(R.id.iv_close);
        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Muestra múltiples imágenes en galería fullscreen usando ViewPager2.
     * Abre GaleriaActivity con las URLs proporcionadas.
     *
     * @param context Contexto de la aplicación
     * @param imageUrls Lista de URLs de imágenes
     * @param startPosition Posición inicial (0-indexed)
     */
    public static void showGallery(Context context, ArrayList<String> imageUrls, int startPosition) {
        if (context == null || imageUrls == null || imageUrls.isEmpty()) {
            return;
        }

        Intent intent = new Intent(context, GaleriaActivity.class);
        intent.putStringArrayListExtra("imageUrls", imageUrls);
        // Nota: GaleriaActivity podría extenderse para soportar posición inicial
        context.startActivity(intent);
    }

    /**
     * Muestra múltiples imágenes en galería fullscreen (sobrecarga sin posición inicial).
     *
     * @param context Contexto de la aplicación
     * @param imageUrls Lista de URLs de imágenes
     */
    public static void showGallery(Context context, ArrayList<String> imageUrls) {
        showGallery(context, imageUrls, 0);
    }
}
