package com.mjc.mascotalink;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class PaseadorResultadoAdapter extends RecyclerView.Adapter<PaseadorResultadoAdapter.ViewHolder> {

    private final Context context;
    private final List<PaseadorResultado> paseadores;

    public PaseadorResultadoAdapter(Context context, List<PaseadorResultado> paseadores) {
        this.context = context;
        this.paseadores = paseadores;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_paseador_resultado, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PaseadorResultado paseador = paseadores.get(position);

        // Avatar del paseador
        Glide.with(context)
                .load(paseador.getFotoUrl())
                .placeholder(R.drawable.paseador_mascota)
                .circleCrop()
                .into(holder.ivAvatar);

        // Nombre
        holder.tvNombre.setText(paseador.getNombre());

        // Zona de servicio
        String zonaTexto = "Zona: " + (paseador.getZonaServicio() != null ? paseador.getZonaServicio() : "No especificada");
        holder.tvZona.setText(zonaTexto);

        // Experiencia
        String experienciaTexto = paseador.getAnosExperiencia() + " año" + 
                                   (paseador.getAnosExperiencia() != 1 ? "s" : "") + " de experiencia";
        holder.tvExperiencia.setText(experienciaTexto);

        // Calificación
        holder.tvCalificacion.setText(String.format("%.1f", paseador.getCalificacion()));
        holder.tvNumeroResenas.setText("(" + paseador.getNumeroResenas() + ")");

        // Precio
        holder.tvPrecio.setText(String.format("$%.0f/hora", paseador.getTarifaPorHora()));

        // Disponibilidad
        if (paseador.isDisponible()) {
            holder.viewDisponibilidad.setBackgroundTintList(
                context.getResources().getColorStateList(R.color.green_success, null));
            holder.chipDisponibilidad.setText("Disponible");
            holder.chipDisponibilidad.setBackgroundTintList(
                context.getResources().getColorStateList(R.color.green_success, null));
        } else {
            holder.viewDisponibilidad.setBackgroundTintList(
                context.getResources().getColorStateList(R.color.gray_disabled, null));
            holder.chipDisponibilidad.setText("Ocupado");
            holder.chipDisponibilidad.setBackgroundTintList(
                context.getResources().getColorStateList(R.color.gray_disabled, null));
        }

        // Click listener
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PerfilPaseadorActivity.class);
            intent.putExtra("paseador_id", paseador.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return paseadores.size();
    }

    public void actualizarLista(List<PaseadorResultado> nuevaLista) {
        paseadores.clear();
        paseadores.addAll(nuevaLista);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        View viewDisponibilidad;
        TextView tvNombre, tvZona, tvExperiencia;
        TextView tvCalificacion, tvNumeroResenas, tvPrecio, chipDisponibilidad;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar_paseador);
            viewDisponibilidad = itemView.findViewById(R.id.view_disponibilidad);
            tvNombre = itemView.findViewById(R.id.tv_nombre_paseador);
            tvZona = itemView.findViewById(R.id.tv_zona_servicio);
            tvExperiencia = itemView.findViewById(R.id.tv_experiencia);
            tvCalificacion = itemView.findViewById(R.id.tv_calificacion);
            tvNumeroResenas = itemView.findViewById(R.id.tv_numero_resenas);
            tvPrecio = itemView.findViewById(R.id.tv_precio);
            chipDisponibilidad = itemView.findViewById(R.id.chip_disponibilidad);
        }
    }
}
