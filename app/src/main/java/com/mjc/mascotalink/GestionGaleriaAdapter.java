package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.MyApplication;

import java.util.ArrayList;
import java.util.List;

public class GestionGaleriaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ADD = 0;
    private static final int TYPE_IMAGE = 1;
    private static final int MAX_IMAGES = 10;

    private final Context context;
    private final List<String> imageUrls;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onAddClick();
        void onDeleteClick(int position, String imageUrl);
    }

    public GestionGaleriaAdapter(Context context, List<String> imageUrls, OnItemClickListener listener) {
        this.context = context;
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        // Si llegamos al límite de imágenes, ya no mostramos el botón de agregar
        if (imageUrls.size() >= MAX_IMAGES) {
            return TYPE_IMAGE;
        }
        // El último elemento siempre es "Agregar", a menos que estemos llenos
        return (position == imageUrls.size()) ? TYPE_ADD : TYPE_IMAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_galeria_gestion, parent, false);
        return new GaleriaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GaleriaViewHolder vh = (GaleriaViewHolder) holder;
        int viewType = getItemViewType(position);

        if (viewType == TYPE_ADD) {
            vh.cvAddPhoto.setVisibility(View.VISIBLE);
            vh.cvImage.setVisibility(View.GONE);
            vh.cvAddPhoto.setOnClickListener(v -> listener.onAddClick());
        } else {
            vh.cvAddPhoto.setVisibility(View.GONE);
            vh.cvImage.setVisibility(View.VISIBLE);
            
            String url = imageUrls.get(position);
            Glide.with(context).load(MyApplication.getFixedUrl(url)).centerCrop().into(vh.ivImage);
            
            vh.btnDelete.setOnClickListener(v -> listener.onDeleteClick(position, url));
        }
    }

    @Override
    public int getItemCount() {
        if (imageUrls.size() >= MAX_IMAGES) {
            return MAX_IMAGES;
        }
        return imageUrls.size() + 1; // +1 para el botón de agregar
    }

    static class GaleriaViewHolder extends RecyclerView.ViewHolder {
        CardView cvAddPhoto;
        CardView cvImage;
        ImageView ivImage;
        View btnDelete;

        public GaleriaViewHolder(@NonNull View itemView) {
            super(itemView);
            cvAddPhoto = itemView.findViewById(R.id.cv_add_photo);
            cvImage = itemView.findViewById(R.id.cv_image);
            ivImage = itemView.findViewById(R.id.iv_image);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}