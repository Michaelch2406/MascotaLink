package com.mjc.mascota.ui.busqueda;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.mjc.mascotalink.R;
import com.mjc.mascota.modelo.PaseadorResultado;
import java.util.Locale;
import java.util.Objects;

public class PaseadorPopularAdapter extends ListAdapter<PaseadorResultado, PaseadorPopularAdapter.PaseadorPopularViewHolder> {

    private PaseadorResultadoAdapter.OnItemClickListener listener;

    public PaseadorPopularAdapter() {
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
                    && oldItem.getCalificacion() == newItem.getCalificacion();
        }
    };

    @NonNull
    @Override
    public PaseadorPopularViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_paseador_popular, parent, false);
        return new PaseadorPopularViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull PaseadorPopularViewHolder holder, int position) {
        PaseadorResultado currentPaseador = getItem(position);
        holder.bind(currentPaseador, listener);
    }

    public interface OnItemClickListener {
        void onItemClick(PaseadorResultado paseador);
    }

    public void setOnItemClickListener(PaseadorResultadoAdapter.OnItemClickListener listener) {
        this.listener = listener;
    }

    static class PaseadorPopularViewHolder extends RecyclerView.ViewHolder {
        private final ImageView avatarImageView;
        private final TextView nombreTextView;
        private final TextView calificacionTextView;
        private final TextView resenasTextView;

        public PaseadorPopularViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.iv_avatar_popular);
            nombreTextView = itemView.findViewById(R.id.tv_nombre_popular);
            calificacionTextView = itemView.findViewById(R.id.tv_calificacion_popular);
            resenasTextView = itemView.findViewById(R.id.tv_resenas_popular);
        }

        public void bind(PaseadorResultado paseador, final PaseadorResultadoAdapter.OnItemClickListener listener) {
            nombreTextView.setText(paseador.getNombre());
            calificacionTextView.setText(String.format(Locale.getDefault(), "%.1f", paseador.getCalificacion()));
            resenasTextView.setText(String.format(Locale.getDefault(), "(%d)", paseador.getTotalResenas()));

            Glide.with(itemView.getContext())
                    .load(paseador.getFotoUrl())
                    .placeholder(R.drawable.bg_avatar_circle_skeleton)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(avatarImageView);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(paseador);
                }
            });
        }
    }
}
