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

public class PaseadorPopularAdapter extends RecyclerView.Adapter<PaseadorPopularAdapter.ViewHolder> {

    private final Context context;
    private final List<PaseadorResultado> paseadores;

    public PaseadorPopularAdapter(Context context, List<PaseadorResultado> paseadores) {
        this.context = context;
        this.paseadores = paseadores;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_paseador_popular, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PaseadorResultado paseador = paseadores.get(position);

        // Avatar
        Glide.with(context)
                .load(paseador.getFotoUrl())
                .placeholder(R.drawable.paseador_mascota)
                .circleCrop()
                .into(holder.ivAvatar);

        // Nombre
        holder.tvNombre.setText(paseador.getNombre());

        // Calificación
        holder.tvCalificacion.setText(String.format("%.1f", paseador.getCalificacion()));
        holder.tvResenas.setText("(" + paseador.getNumeroResenas() + ")");

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

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvNombre, tvCalificacion, tvResenas;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar_popular);
            tvNombre = itemView.findViewById(R.id.tv_nombre_popular);
            tvCalificacion = itemView.findViewById(R.id.tv_calificacion_popular);
            tvResenas = itemView.findViewById(R.id.tv_resenas_popular);
        }
    }
}
