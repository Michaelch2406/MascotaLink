package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.R;

import java.util.List;

public class GalleryPreviewAdapter extends RecyclerView.Adapter<GalleryPreviewAdapter.GalleryViewHolder> {

    private final Context context;
    private List<String> imageUrls;

    public GalleryPreviewAdapter(Context context, List<String> imageUrls) {
        this.context = context;
        this.imageUrls = imageUrls;
    }

    @NonNull
    @Override
    public GalleryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_gallery_preview, parent, false);
        return new GalleryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);
        Glide.with(context)
                .load(imageUrl)
                .centerCrop()
                .placeholder(R.drawable.galeria_paseos_foto1) // Use a placeholder
                .into(holder.ivGalleryThumbnail);
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    public void setImageUrls(List<String> newImageUrls) {
        this.imageUrls = newImageUrls;
        notifyDataSetChanged();
    }

    static class GalleryViewHolder extends RecyclerView.ViewHolder {
        ImageView ivGalleryThumbnail;

        public GalleryViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGalleryThumbnail = itemView.findViewById(R.id.iv_gallery_thumbnail);
        }
    }
}