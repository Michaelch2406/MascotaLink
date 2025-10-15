
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

import java.util.Objects;

public class PaseadorResultadoAdapter extends ListAdapter<PaseadorResultado, PaseadorResultadoAdapter.PaseadorViewHolder> {

    private OnItemClickListener listener;

    public PaseadorResultadoAdapter() {
        super(DIFF_CALLBACK);
    }

    // DiffUtil para calcular diferencias y animar cambios automáticamente
    private static final DiffUtil.ItemCallback<PaseadorResultado> DIFF_CALLBACK = new DiffUtil.ItemCallback<PaseadorResultado>() {
        @Override
        public boolean areItemsTheSame(@NonNull PaseadorResultado oldItem, @NonNull PaseadorResultado newItem) {
            // Los items son los mismos si sus IDs son iguales
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull PaseadorResultado oldItem, @NonNull PaseadorResultado newItem) {
            // El contenido es el mismo si todos estos campos no han cambiado
            return oldItem.getNombre().equals(newItem.getNombre())
                    && Objects.equals(oldItem.getFotoUrl(), newItem.getFotoUrl())
                    && oldItem.getCalificacion() == newItem.getCalificacion();
        }
    };

    @NonNull
    @Override
    public PaseadorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflar el layout del item que se creará en la FASE 4
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_paseador_resultado, parent, false);
        return new PaseadorViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull PaseadorViewHolder holder, int position) {
        PaseadorResultado currentPaseador = getItem(position);
        holder.bind(currentPaseador);
    }

    // Interfaz para manejar clicks en los items
    public interface OnItemClickListener {
        void onItemClick(PaseadorResultado paseador);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    // --- ViewHolder Interno --- //
    class PaseadorViewHolder extends RecyclerView.ViewHolder {
        private final ImageView avatarImageView;
        private final TextView nombreTextView;
        private final TextView calificacionTextView;
        private final TextView zonaTextView;
        private final TextView tarifaTextView;

        public PaseadorViewHolder(@NonNull View itemView) {
            super(itemView);
            // IDs del layout item_paseador_resultado.xml (FASE 4)
            avatarImageView = itemView.findViewById(R.id.paseador_avatar);
            nombreTextView = itemView.findViewById(R.id.paseador_nombre);
            calificacionTextView = itemView.findViewById(R.id.paseador_calificacion);
            zonaTextView = itemView.findViewById(R.id.paseador_zona);
            tarifaTextView = itemView.findViewById(R.id.paseador_tarifa);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(getItem(position));
                }
            });
        }

        public void bind(PaseadorResultado paseador) {
            // Carga de datos segura en la UI
            nombreTextView.setText(paseador.getNombre());
            calificacionTextView.setText(String.format("%.1f", paseador.getCalificacion()));
            zonaTextView.setText(paseador.getZonaPrincipal() != null ? paseador.getZonaPrincipal() : "Sin zona especificada");
            tarifaTextView.setText(String.format("$%.2f/hora", paseador.getTarifaPorHora()));

            // Carga de imagen robusta con Glide
            Glide.with(itemView.getContext())
                    .load(paseador.getFotoUrl()) // Glide maneja URLs nulas o vacías
                    .placeholder(R.drawable.bg_avatar_circle_skeleton) // Placeholder mientras carga
                    .error(R.drawable.ic_person) // Imagen si falla la carga
                    .circleCrop() // Para avatares circulares
                    .into(avatarImageView);
        }
    }
}
