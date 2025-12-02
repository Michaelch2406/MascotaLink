package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.mjc.mascotalink.modelo.PaseadorFavorito;
import com.mjc.mascotalink.MyApplication;
import java.util.Locale;
import de.hdodenhof.circleimageview.CircleImageView;

public class FavoritosAdapter extends ListAdapter<PaseadorFavorito, FavoritosAdapter.FavoritoViewHolder> {

    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onQuitarFavoritoClick(String paseadorId);
        void onPaseadorClick(String paseadorId);
    }

    public FavoritosAdapter(OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<PaseadorFavorito> DIFF_CALLBACK = new DiffUtil.ItemCallback<PaseadorFavorito>() {
        @Override
        public boolean areItemsTheSame(@NonNull PaseadorFavorito oldItem, @NonNull PaseadorFavorito newItem) {
            return oldItem.getPaseadorId().equals(newItem.getPaseadorId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull PaseadorFavorito oldItem, @NonNull PaseadorFavorito newItem) {
            return oldItem.getNombre_display().equals(newItem.getNombre_display())
                    && oldItem.getFoto_perfil_url().equals(newItem.getFoto_perfil_url())
                    && oldItem.getCalificacion_promedio().equals(newItem.getCalificacion_promedio());
        }
    };

    @NonNull
    @Override
    public FavoritoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_paseador_favorito, parent, false);
        return new FavoritoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FavoritoViewHolder holder, int position) {
        PaseadorFavorito favorito = getItem(position);
        holder.bind(favorito, listener);
    }

    static class FavoritoViewHolder extends RecyclerView.ViewHolder {
        private final CircleImageView ivAvatar;
        private final TextView tvNombre;
        private final RatingBar ratingBar;
        private final TextView tvPrecio;
        private final FrameLayout btnQuitarFavorito;

        public FavoritoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar_favorito);
            tvNombre = itemView.findViewById(R.id.tv_nombre_favorito);
            ratingBar = itemView.findViewById(R.id.rating_bar_favorito);
            tvPrecio = itemView.findViewById(R.id.tv_precio_favorito);
            btnQuitarFavorito = itemView.findViewById(R.id.btn_quitar_favorito);
        }

        public void bind(final PaseadorFavorito favorito, final OnItemClickListener listener) {
            tvNombre.setText(favorito.getNombre_display());

            if (favorito.getFoto_perfil_url() != null && !favorito.getFoto_perfil_url().isEmpty()) {
                Glide.with(itemView.getContext())
                     .load(MyApplication.getFixedUrl(favorito.getFoto_perfil_url()))
                     .placeholder(R.drawable.ic_person)
                     .into(ivAvatar);
            }

            if (favorito.getCalificacion_promedio() != null) {
                ratingBar.setRating(favorito.getCalificacion_promedio().floatValue());
            } else {
                ratingBar.setRating(0f);
            }

            if (favorito.getPrecio_hora() != null) {
                tvPrecio.setText(String.format(Locale.getDefault(), "$%.2f/hora", favorito.getPrecio_hora()));
            } else {
                tvPrecio.setText("");
            }

            btnQuitarFavorito.setOnClickListener(v -> listener.onQuitarFavoritoClick(favorito.getPaseadorId()));
            itemView.setOnClickListener(v -> listener.onPaseadorClick(favorito.getPaseadorId()));
        }
    }
}
