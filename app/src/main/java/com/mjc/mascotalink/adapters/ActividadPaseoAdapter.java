package com.mjc.mascotalink.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.mjc.mascotalink.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ActividadPaseoAdapter extends RecyclerView.Adapter<ActividadPaseoAdapter.ViewHolder> {

    private List<Map<String, Object>> eventos = new ArrayList<>();

    public void setEventos(List<Map<String, Object>> eventos) {
        this.eventos = eventos;
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
        Map<String, Object> evento = eventos.get(position);
        String descripcion = (String) evento.get("descripcion");
        Timestamp timestamp = (Timestamp) evento.get("timestamp");
        String tipo = (String) evento.get("evento");

        holder.tvDescripcion.setText(descripcion != null ? descripcion : "Evento desconocido");

        if (timestamp != null) {
            Date date = timestamp.toDate();
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
            holder.tvTimestamp.setText(sdf.format(date));
        } else {
            holder.tvTimestamp.setText("");
        }

        // Set icon based on event type
        if (tipo != null) {
            switch (tipo) {
                case "PASEADOR_LLEGÓ":
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
                    holder.ivIcon.setImageResource(R.drawable.ic_evento_llegada); // Default
                    break;
            }
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
