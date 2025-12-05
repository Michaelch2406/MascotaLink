package com.mjc.mascotalink.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.MyApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapter encargado de mostrar la galería de fotos del paseo en curso.
 * Optimizado con DiffUtil para actualizaciones eficientes.
 */
public class FotosPaseoAdapter extends RecyclerView.Adapter<FotosPaseoAdapter.FotoViewHolder> {

    public interface OnFotoInteractionListener {
        void onFotoClick(String url);

        void onFotoLongClick(String url);
    }

    private final Context context;
    private final AsyncListDiffer<String> differ;
    private final OnFotoInteractionListener listener;

    /**
     * DiffUtil.ItemCallback para comparar URLs de fotos
     */
    private static final DiffUtil.ItemCallback<String> DIFF_CALLBACK = new DiffUtil.ItemCallback<String>() {
        @Override
        public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
            return Objects.equals(oldItem, newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
            return Objects.equals(oldItem, newItem);
        }
    };

    public FotosPaseoAdapter(@NonNull Context context,
                             @NonNull OnFotoInteractionListener listener) {
        this.context = context;
        this.differ = new AsyncListDiffer<>(this, DIFF_CALLBACK);
        this.listener = listener;
    }

    public void submitList(List<String> nuevasFotos) {
        differ.submitList(nuevasFotos != null ? new ArrayList<>(nuevasFotos) : new ArrayList<>());
    }

    @NonNull
    @Override
    public FotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_foto_paseo, parent, false);
        return new FotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FotoViewHolder holder, int position) {
        String url = differ.getCurrentList().get(position);

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
        return differ.getCurrentList().size();
    }

    static class FotoViewHolder extends RecyclerView.ViewHolder {
        final com.google.android.material.imageview.ShapeableImageView imageView;

        FotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_foto_paseo);
        }
    }
}

