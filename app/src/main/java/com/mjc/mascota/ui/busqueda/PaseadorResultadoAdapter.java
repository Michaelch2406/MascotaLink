package com.mjc.mascota.ui.busqueda;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView; // Changed from ImageButton to ImageView as per new layout
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.R;
import com.mjc.mascota.modelo.PaseadorResultado;
import com.mjc.mascotalink.MyApplication;

import java.util.Locale;
import java.util.Objects;

public class PaseadorResultadoAdapter extends ListAdapter<PaseadorResultado, PaseadorResultadoAdapter.PaseadorViewHolder> {

    private OnItemClickListener itemClickListener;
    private OnFavoritoToggleListener favoritoToggleListener;

    public PaseadorResultadoAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<PaseadorResultado> DIFF_CALLBACK = new DiffUtil.ItemCallback<PaseadorResultado>() {
        @Override
        public boolean areItemsTheSame(@NonNull PaseadorResultado oldItem, @NonNull PaseadorResultado newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull PaseadorResultado oldItem, @NonNull PaseadorResultado newItem) {
            return oldItem.getNombre().equals(newItem.getNombre())
                    && Objects.equals(oldItem.getFotoUrl(), newItem.getFotoUrl())
                    && oldItem.getCalificacion() == newItem.getCalificacion()
                    && oldItem.isFavorito() == newItem.isFavorito()
                    && oldItem.isEnLinea() == newItem.isEnLinea();
        }
    };

    @NonNull
    @Override
    public PaseadorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_paseador_resultado, parent, false);
        return new PaseadorViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull PaseadorViewHolder holder, int position) {
        PaseadorResultado currentPaseador = getItem(position);
        holder.bind(currentPaseador, itemClickListener, favoritoToggleListener);
    }

    public interface OnItemClickListener {
        void onItemClick(PaseadorResultado paseador);
    }

    public interface OnFavoritoToggleListener {
        void onFavoritoToggle(String paseadorId, boolean isChecked);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setOnFavoritoToggleListener(OnFavoritoToggleListener listener) {
        this.favoritoToggleListener = listener;
    }

    static class PaseadorViewHolder extends RecyclerView.ViewHolder {
        private final ImageView avatarImageView;
        private final TextView nombreTextView;
        private final TextView calificacionTextView; // Changed from RatingBar
        private final TextView totalResenasTextView;
        private final TextView zonaTextView;
        private final TextView tarifaTextView;
        private final ImageView favoritoButton; // Changed from ImageButton
        private final TextView badgeEnLinea;

        public PaseadorViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.iv_foto_paseador);
            nombreTextView = itemView.findViewById(R.id.tv_nombre_paseador);
            calificacionTextView = itemView.findViewById(R.id.tv_calificacion);
            totalResenasTextView = itemView.findViewById(R.id.paseador_total_resenas);
            zonaTextView = itemView.findViewById(R.id.tv_zona_paseador);
            tarifaTextView = itemView.findViewById(R.id.tv_precio);
            favoritoButton = itemView.findViewById(R.id.btn_favorito);
            badgeEnLinea = itemView.findViewById(R.id.badge_en_linea);
        }

        public void bind(PaseadorResultado paseador, OnItemClickListener itemListener, OnFavoritoToggleListener favListener) {
            Context context = itemView.getContext();
            nombreTextView.setText(paseador.getNombre());
            zonaTextView.setText(paseador.getZonaPrincipal() != null ? paseador.getZonaPrincipal() : "Sin zona");
            tarifaTextView.setText(String.format(Locale.getDefault(), "$%.2f", paseador.getTarifaPorHora()));
            calificacionTextView.setText(String.format(Locale.getDefault(), "%.1f", paseador.getCalificacion()));
            totalResenasTextView.setText(String.format(Locale.getDefault(), "(%d)", paseador.getTotalResenas()));

            Glide.with(context)
                    .load(MyApplication.getFixedUrl(paseador.getFotoUrl()))
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(avatarImageView);

            // Show/hide online badge
            badgeEnLinea.setVisibility(paseador.isEnLinea() ? View.VISIBLE : View.GONE);

            if (paseador.isFavorito()) {
                favoritoButton.setImageResource(R.drawable.ic_corazon_lleno); // Assumes red heart drawable
                favoritoButton.setColorFilter(null); // Clear tint to show red
                favoritoButton.setColorFilter(ContextCompat.getColor(context, R.color.red_error));
            } else {
                favoritoButton.setImageResource(R.drawable.ic_corazon);
                favoritoButton.setColorFilter(ContextCompat.getColor(context, R.color.gray_disabled));
            }

            itemView.setOnClickListener(v -> {
                if (itemListener != null) {
                    itemListener.onItemClick(paseador);
                }
            });

            favoritoButton.setOnClickListener(v -> {
                if (favListener != null) {
                    favListener.onFavoritoToggle(paseador.getId(), !paseador.isFavorito());
                }
            });
        }
    }
}
