package com.mjc.mascotalink.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mjc.mascotalink.R;
import com.mjc.mascotalink.modelo.PaseoActividad;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ActividadPaseoAdapter extends RecyclerView.Adapter<ActividadPaseoAdapter.ViewHolder> {

    private List<PaseoActividad> eventos = new ArrayList<>();

    public void setEventos(List<PaseoActividad> eventos) {
        this.eventos = eventos != null ? eventos : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_evento_actividad, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PaseoActividad evento = eventos.get(position);
        
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
        return eventos.size();
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
