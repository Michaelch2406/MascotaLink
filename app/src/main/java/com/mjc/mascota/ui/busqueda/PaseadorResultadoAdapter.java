package com.mjc.mascota.ui.busqueda;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.R;
import com.mjc.mascota.modelo.PaseadorResultado;

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
                    && oldItem.isFavorito() == newItem.isFavorito(); // Incluir el estado de favorito en la comparación
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

    // Interfaces para manejar clicks
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

    // --- ViewHolder Interno --- //
    static class PaseadorViewHolder extends RecyclerView.ViewHolder {
        private final ImageView avatarImageView;
        private final TextView nombreTextView;
        private final RatingBar ratingBar;
        private final TextView totalResenasTextView;
        private final TextView zonaTextView;
        private final TextView tarifaTextView;
        private final ImageButton favoritoButton;

        public PaseadorViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.paseador_avatar);
            nombreTextView = itemView.findViewById(R.id.paseador_nombre);
            ratingBar = itemView.findViewById(R.id.paseador_rating_bar);
            totalResenasTextView = itemView.findViewById(R.id.paseador_total_resenas);
            zonaTextView = itemView.findViewById(R.id.paseador_zona);
            tarifaTextView = itemView.findViewById(R.id.paseador_tarifa);
            favoritoButton = itemView.findViewById(R.id.btn_favorito_resultado);
        }

        public void bind(PaseadorResultado paseador, OnItemClickListener itemListener, OnFavoritoToggleListener favListener) {
            Context context = itemView.getContext();
            nombreTextView.setText(paseador.getNombre());
            zonaTextView.setText(paseador.getZonaPrincipal() != null ? paseador.getZonaPrincipal() : "Sin zona especificada");
            tarifaTextView.setText(String.format(Locale.getDefault(), "$%.2f/hora", paseador.getTarifaPorHora()));
            ratingBar.setRating((float) paseador.getCalificacion());
            totalResenasTextView.setText(String.format(Locale.getDefault(), "(%d)", paseador.getTotalResenas()));

            // Carga de imagen
            Glide.with(context)
                    .load(paseador.getFotoUrl())
                    .placeholder(R.drawable.bg_avatar_circle_skeleton)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(avatarImageView);

            // Actualizar estado del botón de favorito
            if (paseador.isFavorito()) {
                favoritoButton.setImageResource(R.drawable.ic_corazon_lleno);
                favoritoButton.setColorFilter(ContextCompat.getColor(context, R.color.colorError));
            } else {
                favoritoButton.setImageResource(R.drawable.ic_corazon);
                favoritoButton.setColorFilter(ContextCompat.getColor(context, R.color.gray_dark)); // O el color que prefieras para el estado "no favorito"
            }

            // Listeners
            itemView.setOnClickListener(v -> {
                if (itemListener != null) {
                    itemListener.onItemClick(paseador);
                }
            });

            favoritoButton.setOnClickListener(v -> {
                if (favListener != null) {
                    // Notificar el nuevo estado deseado
                    favListener.onFavoritoToggle(paseador.getId(), !paseador.isFavorito());
                }
            });
        }
    }
}