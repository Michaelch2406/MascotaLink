package com.mjc.mascotalink.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mjc.mascotalink.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter para mostrar configuraciones activas (bloqueos y horarios especiales)
 */
public class ConfiguracionesAdapter extends RecyclerView.Adapter<ConfiguracionesAdapter.ViewHolder> {

    private Context context;
    private List<ConfiguracionItem> items;

    public static class ConfiguracionItem {
        public String id;
        public String titulo;
        public String descripcion;
        public String tipo; // "bloqueo" o "horario_especial"

        public ConfiguracionItem(String id, String titulo, String descripcion, String tipo) {
            this.id = id;
            this.titulo = titulo;
            this.descripcion = descripcion;
            this.tipo = tipo;
        }
    }

    public ConfiguracionesAdapter(Context context) {
        this.context = context;
        this.items = new ArrayList<>();
    }

    public void setItems(List<ConfiguracionItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_configuracion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConfiguracionItem item = items.get(position);

        holder.tvTitulo.setText(item.titulo);
        holder.tvDescripcion.setText(item.descripcion);

        // Icono según tipo
        if ("bloqueo".equals(item.tipo)) {
            holder.tvIcono.setText("🚫");
        } else if ("horario_especial".equals(item.tipo)) {
            holder.tvIcono.setText("⏰");
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIcono, tvTitulo, tvDescripcion;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcono = itemView.findViewById(R.id.tvIcono);
            tvTitulo = itemView.findViewById(R.id.tvTitulo);
            tvDescripcion = itemView.findViewById(R.id.tvDescripcion);
        }
    }
}
