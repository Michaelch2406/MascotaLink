package com.mjc.mascotalink.views;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.MyApplication;
import com.mjc.mascotalink.R;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Custom view para mostrar avatares superpuestos (overlapping avatars)
 * Similar a WhatsApp grupos, Slack, etc.
 * Muestra hasta 3 avatares, si hay más muestra "+N"
 */
public class OverlappingAvatarsView extends FrameLayout {

    private static final int MAX_VISIBLE_AVATARS = 3;
    private static final int OVERLAP_PERCENTAGE = 40; // 40% de superposición
    private static final int AVATAR_SIZE_DP = 40;
    private static final int BORDER_WIDTH_DP = 2;

    private int avatarSizePx;
    private int borderWidthPx;
    private int overlapPx;

    public OverlappingAvatarsView(@NonNull Context context) {
        super(context);
        init();
    }

    public OverlappingAvatarsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OverlappingAvatarsView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        avatarSizePx = dpToPx(AVATAR_SIZE_DP);
        borderWidthPx = dpToPx(BORDER_WIDTH_DP);
        overlapPx = (avatarSizePx * OVERLAP_PERCENTAGE) / 100;
    }

    /**
     * Establece las URLs de las imágenes a mostrar
     * @param imageUrls Lista de URLs de las fotos de mascotas
     */
    public void setImageUrls(List<String> imageUrls) {
        removeAllViews();

        if (imageUrls == null || imageUrls.isEmpty()) {
            setVisibility(GONE);
            return;
        }

        setVisibility(VISIBLE);

        int totalAvatars = imageUrls.size();
        int visibleCount = Math.min(totalAvatars, MAX_VISIBLE_AVATARS);

        for (int i = 0; i < visibleCount; i++) {
            boolean isLast = (i == visibleCount - 1);
            boolean hasMore = (totalAvatars > MAX_VISIBLE_AVATARS);

            if (isLast && hasMore) {
                // Mostrar "+N" en el último avatar
                int remaining = totalAvatars - MAX_VISIBLE_AVATARS + 1;
                addCounterAvatar(i, remaining);
            } else {
                // Mostrar imagen normal
                addImageAvatar(i, imageUrls.get(i));
            }
        }

        // Ajustar el ancho del contenedor
        int totalWidth = avatarSizePx + (visibleCount - 1) * (avatarSizePx - overlapPx);
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null) {
            params.width = totalWidth;
            params.height = avatarSizePx;
            setLayoutParams(params);
        }
    }

    /**
     * Muestra placeholders cuando no hay URLs disponibles
     * @param count Número de placeholders a mostrar
     */
    public void setPlaceholders(int count) {
        removeAllViews();

        if (count <= 0) {
            setVisibility(GONE);
            return;
        }

        setVisibility(VISIBLE);

        int visibleCount = Math.min(count, MAX_VISIBLE_AVATARS);

        for (int i = 0; i < visibleCount; i++) {
            boolean isLast = (i == visibleCount - 1);
            boolean hasMore = (count > MAX_VISIBLE_AVATARS);

            if (isLast && hasMore) {
                // Mostrar "+N"
                int remaining = count - MAX_VISIBLE_AVATARS + 1;
                addCounterAvatar(i, remaining);
            } else {
                // Mostrar placeholder
                addImageAvatar(i, null);
            }
        }

        // Ajustar el ancho del contenedor
        int totalWidth = avatarSizePx + (visibleCount - 1) * (avatarSizePx - overlapPx);
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null) {
            params.width = totalWidth;
            params.height = avatarSizePx;
            setLayoutParams(params);
        }
    }

    /**
     * Añade un avatar con imagen
     */
    private void addImageAvatar(int position, String imageUrl) {
        CircleImageView avatar = new CircleImageView(getContext());

        // Configurar layout params con margen para superposición
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                avatarSizePx, avatarSizePx
        );
        params.leftMargin = position * (avatarSizePx - overlapPx);
        params.gravity = Gravity.CENTER_VERTICAL;
        avatar.setLayoutParams(params);

        // Borde blanco para separar avatares
        avatar.setBorderWidth(borderWidthPx);
        avatar.setBorderColor(Color.WHITE);

        // Cargar imagen
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(getContext())
                    .load(MyApplication.getFixedUrl(imageUrl))
                    .placeholder(R.drawable.ic_pet_placeholder)
                    .error(R.drawable.ic_pet_placeholder)
                    .circleCrop()
                    .into(avatar);
        } else {
            // Placeholder
            avatar.setImageResource(R.drawable.ic_pet_placeholder);
        }

        // Elevación para que se vea el overlay correcto
        avatar.setElevation(dpToPx(MAX_VISIBLE_AVATARS - position));

        addView(avatar);
    }

    /**
     * Añade un avatar con contador "+N"
     */
    private void addCounterAvatar(int position, int count) {
        FrameLayout container = new FrameLayout(getContext());

        // Layout params
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                avatarSizePx, avatarSizePx
        );
        params.leftMargin = position * (avatarSizePx - overlapPx);
        params.gravity = Gravity.CENTER_VERTICAL;
        container.setLayoutParams(params);

        // Fondo circular con color
        CircleImageView background = new CircleImageView(getContext());
        FrameLayout.LayoutParams bgParams = new FrameLayout.LayoutParams(
                avatarSizePx, avatarSizePx
        );
        background.setLayoutParams(bgParams);
        background.setImageResource(R.drawable.ic_pet_placeholder);
        background.setColorFilter(getContext().getResources().getColor(R.color.gray_light));
        background.setBorderWidth(borderWidthPx);
        background.setBorderColor(Color.WHITE);
        container.addView(background);

        // Texto "+N"
        TextView textView = new TextView(getContext());
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.gravity = Gravity.CENTER;
        textView.setLayoutParams(textParams);
        textView.setText("+" + count);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textView.setTextColor(getContext().getResources().getColor(R.color.white));
        textView.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(textView);

        // Elevación
        container.setElevation(dpToPx(MAX_VISIBLE_AVATARS - position));

        addView(container);
    }

    /**
     * Convierte DP a pixels
     */
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getContext().getResources().getDisplayMetrics()
        );
    }
}
