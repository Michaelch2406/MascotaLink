package com.mjc.mascota.ui.perfil;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.MyApplication;

import java.util.List;

public class GaleriaPerfilAdapter extends RecyclerView.Adapter<GaleriaPerfilAdapter.GaleriaViewHolder> {

    private final Context context;
    private final List<String> imageUrls;

    public GaleriaPerfilAdapter(Context context, List<String> imageUrls) {
        this.context = context;
        this.imageUrls = imageUrls;
    }

    @NonNull
    @Override
    public GaleriaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_galeria_perfil, parent, false);
        return new GaleriaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GaleriaViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);
        Glide.with(context)
                .load(MyApplication.getFixedUrl(imageUrl))
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    static class GaleriaViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public GaleriaViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_galeria_imagen);
        }
    }
}
