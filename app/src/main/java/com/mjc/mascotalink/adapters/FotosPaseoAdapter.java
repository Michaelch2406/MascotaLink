package com.mjc.mascotalink.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.MyApplication;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter encargado de mostrar la galería de fotos del paseo en curso.
 */
public class FotosPaseoAdapter extends RecyclerView.Adapter<FotosPaseoAdapter.FotoViewHolder> {

    public interface OnFotoInteractionListener {
        void onFotoClick(String url);

        void onFotoLongClick(String url);
    }

    private final Context context;
    private final List<String> fotos = new ArrayList<>();
    private final OnFotoInteractionListener listener;

    public FotosPaseoAdapter(@NonNull Context context,
                             @NonNull OnFotoInteractionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void submitList(List<String> nuevasFotos) {
        fotos.clear();
        if (nuevasFotos != null && !nuevasFotos.isEmpty()) {
            fotos.addAll(nuevasFotos);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_foto_paseo, parent, false);
        return new FotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FotoViewHolder holder, int position) {
        String url = fotos.get(position);

        Glide.with(context)
                .load(MyApplication.getFixedUrl(url))
                .placeholder(R.drawable.ic_pet_placeholder)
                .error(R.drawable.ic_pet_placeholder)
                .into(holder.imageView);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFotoClick(url);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onFotoLongClick(url);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return fotos.size();
    }

    static class FotoViewHolder extends RecyclerView.ViewHolder {
        final com.google.android.material.imageview.ShapeableImageView imageView;

        FotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_foto_paseo);
        }
    }
}

