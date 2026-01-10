package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.mjc.mascotalink.MyApplication;

import java.util.List;

public class GaleriaAdapter extends RecyclerView.Adapter<GaleriaAdapter.ViewHolder> {

    private final Context context;
    private final List<String> imageUrls;
    private OnImageClickListener listener;

    // NUEVO: Interface para clicks en imágenes
    public interface OnImageClickListener {
        void onImageClick(int position, String imageUrl);
    }

    public GaleriaAdapter(Context context, List<String> imageUrls) {
        this.context = context;
        this.imageUrls = imageUrls;
    }

    // NUEVO: Setter para el listener
    public void setOnImageClickListener(OnImageClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_galeria_foto, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);
        Glide.with(context)
                .load(MyApplication.getFixedUrl(imageUrl))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(400, 400)
                .centerCrop()
                .placeholder(R.drawable.foto_principal_mascota)
                .error(R.drawable.foto_principal_mascota)
                .thumbnail(0.1f)
                .into(holder.ivGaleriaFoto);

        // NUEVO: Click listener para abrir imagen en fullscreen
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onImageClick(position, imageUrl);
            }
        });

        preloadNextImages(position);
    }

    private void preloadNextImages(int position) {
        int preloadCount = 2;
        for (int i = 1; i <= preloadCount && (position + i) < imageUrls.size(); i++) {
            String url = MyApplication.getFixedUrl(imageUrls.get(position + i));
            Glide.with(context)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(400, 400)
                    .preload();
        }
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivGaleriaFoto;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGaleriaFoto = itemView.findViewById(R.id.iv_galeria_foto);
        }
    }
}
