package com.mjc.mascotalink.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.mjc.mascotalink.R;
import com.mjc.mascotalink.modelo.PaseoActividad;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ActividadPaseoAdapter extends RecyclerView.Adapter<ActividadPaseoAdapter.ViewHolder> {

    private final AsyncListDiffer<PaseoActividad> differ;

    /**
     * DiffUtil.ItemCallback para comparar eventos de actividad del paseo
     */
    private static final DiffUtil.ItemCallback<PaseoActividad> DIFF_CALLBACK = new DiffUtil.ItemCallback<PaseoActividad>() {
        @Override
        public boolean areItemsTheSame(@NonNull PaseoActividad oldItem, @NonNull PaseoActividad newItem) {
            // Comparar por timestamp + evento (pueden haber múltiples eventos del mismo tipo)
            return Objects.equals(oldItem.getDate(), newItem.getDate()) &&
                   Objects.equals(oldItem.getEvento(), newItem.getEvento());
        }

        @Override
        public boolean areContentsTheSame(@NonNull PaseoActividad oldItem, @NonNull PaseoActividad newItem) {
            return Objects.equals(oldItem.getEvento(), newItem.getEvento()) &&
                   Objects.equals(oldItem.getDescripcion(), newItem.getDescripcion()) &&
                   Objects.equals(oldItem.getDate(), newItem.getDate());
        }
    };

    public ActividadPaseoAdapter() {
        this.differ = new AsyncListDiffer<>(this, DIFF_CALLBACK);
    }

    public void setEventos(List<PaseoActividad> eventos) {
        differ.submitList(eventos != null ? new ArrayList<>(eventos) : new ArrayList<>());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_evento_actividad, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PaseoActividad evento = differ.getCurrentList().get(position);
        
        holder.tvDescripcion.setText(evento.getDescripcion() != null ? evento.getDescripcion() : "");

        if (evento.getDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
            holder.tvTimestamp.setText(sdf.format(evento.getDate()));
        } else {
            holder.tvTimestamp.setText("");
        }

        String tipo = evento.getEvento();
        if (tipo != null) {
            switch (tipo) {
                case "PASEADOR_LLEGÓ":
                case "PASEADOR_LLEGO": // Handle missing accent
                    holder.ivIcon.setImageResource(R.drawable.ic_evento_llegada);
                    break;
                case "PASEO_INICIADO":
                    holder.ivIcon.setImageResource(R.drawable.ic_evento_inicio);
                    break;
                case "FOTO_SUBIDA":
                    holder.ivIcon.setImageResource(R.drawable.ic_evento_foto);
                    break;
                case "NOTA_AGREGADA":
                    holder.ivIcon.setImageResource(R.drawable.ic_evento_nota);
                    break;
                default:
                    holder.ivIcon.setImageResource(R.drawable.ic_evento_llegada); 
                    break;
            }
        } else {
             holder.ivIcon.setImageResource(R.drawable.ic_evento_llegada);
        }
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvDescripcion;
        TextView tvTimestamp;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvDescripcion = itemView.findViewById(R.id.tv_descripcion);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
        }
    }
}
