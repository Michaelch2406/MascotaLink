package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class PaseosAdapter extends RecyclerView.Adapter<PaseosAdapter.PaseoViewHolder> {

    private Context context;
    private List<PaseosActivity.Paseo> paseosList;
    private OnPaseoClickListener listener;

    public interface OnPaseoClickListener {
        void onPaseoClick(PaseosActivity.Paseo paseo);
        void onVerUbicacionClick(PaseosActivity.Paseo paseo);
        void onContactarClick(PaseosActivity.Paseo paseo);
        void onCalificarClick(PaseosActivity.Paseo paseo);
        void onVerMotivoClick(PaseosActivity.Paseo paseo);
    }

    public PaseosAdapter(Context context, List<PaseosActivity.Paseo> paseosList, OnPaseoClickListener listener) {
        this.context = context;
        this.paseosList = paseosList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PaseoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_paseo, parent, false);
        return new PaseoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PaseoViewHolder holder, int position) {
        PaseosActivity.Paseo paseo = paseosList.get(position);

        // Fecha
        holder.tvFecha.setText(paseo.getFechaFormateada());

        // Nombre paseador
        holder.tvPaseadorNombre.setText(paseo.getPaseadorNombre() != null ? paseo.getPaseadorNombre() : "Cargando...");

        // Mascota y hora
        String mascotaHora = "Mascota: " + 
                (paseo.getMascotaNombre() != null ? paseo.getMascotaNombre() : "Cargando...") + 
                ", " + paseo.getHoraFormateada();
        holder.tvMascotaHora.setText(mascotaHora);

        // Imagen del paseador
        if (paseo.getPaseadorFoto() != null && !paseo.getPaseadorFoto().isEmpty()) {
            Glide.with(context)
                    .load(paseo.getPaseadorFoto())
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(holder.ivPaseadorFoto);
        } else {
            holder.ivPaseadorFoto.setImageResource(R.drawable.ic_person);
        }

        // Configurar botones según estado
        configurarBotonesPorEstado(holder, paseo);

        // Click en card
        holder.itemView.setOnClickListener(v -> listener.onPaseoClick(paseo));

        // Aplicar opacidad según estado
        float alpha = 1.0f;
        if ("COMPLETADO".equals(paseo.getEstado())) {
            alpha = 0.8f;
        } else if ("CANCELADO".equals(paseo.getEstado())) {
            alpha = 0.6f;
        }
        holder.itemView.setAlpha(alpha);
    }

    private void configurarBotonesPorEstado(PaseoViewHolder holder, PaseosActivity.Paseo paseo) {
        // Ocultar todos los botones primero
        holder.btnVerUbicacion.setVisibility(View.GONE);
        holder.btnContactar.setVisibility(View.GONE);
        holder.btnCalificar.setVisibility(View.GONE);
        holder.btnVerMotivo.setVisibility(View.GONE);
        holder.llBotones.setVisibility(View.GONE);

        String estado = paseo.getEstado();

        if ("EN_CURSO".equals(estado)) {
            // Mostrar Ver Ubicación y Contactar
            holder.llBotones.setVisibility(View.VISIBLE);
            holder.btnVerUbicacion.setVisibility(View.VISIBLE);
            holder.btnContactar.setVisibility(View.VISIBLE);

            holder.btnVerUbicacion.setOnClickListener(v -> listener.onVerUbicacionClick(paseo));
            holder.btnContactar.setOnClickListener(v -> listener.onContactarClick(paseo));

        } else if ("COMPLETADO".equals(estado)) {
            // Mostrar Calificar Paseador
            holder.llBotones.setVisibility(View.VISIBLE);
            holder.btnCalificar.setVisibility(View.VISIBLE);

            holder.btnCalificar.setOnClickListener(v -> listener.onCalificarClick(paseo));

        } else if ("CANCELADO".equals(estado)) {
            // Mostrar Ver motivo
            holder.llBotones.setVisibility(View.VISIBLE);
            holder.btnVerMotivo.setVisibility(View.VISIBLE);

            holder.btnVerMotivo.setOnClickListener(v -> listener.onVerMotivoClick(paseo));

        } else if ("CONFIRMADO".equals(estado)) {
            // No mostrar botones
            holder.llBotones.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return paseosList.size();
    }

    static class PaseoViewHolder extends RecyclerView.ViewHolder {
        TextView tvFecha;
        TextView tvPaseadorNombre;
        TextView tvMascotaHora;
        CircleImageView ivPaseadorFoto;
        LinearLayout llBotones;
        TextView btnVerUbicacion;
        TextView btnContactar;
        TextView btnCalificar;
        TextView btnVerMotivo;

        public PaseoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFecha = itemView.findViewById(R.id.tv_fecha);
            tvPaseadorNombre = itemView.findViewById(R.id.tv_paseador_nombre);
            tvMascotaHora = itemView.findViewById(R.id.tv_mascota_hora);
            ivPaseadorFoto = itemView.findViewById(R.id.iv_paseador_foto);
            llBotones = itemView.findViewById(R.id.ll_botones);
            btnVerUbicacion = itemView.findViewById(R.id.btn_ver_ubicacion);
            btnContactar = itemView.findViewById(R.id.btn_contactar);
            btnCalificar = itemView.findViewById(R.id.btn_calificar);
            btnVerMotivo = itemView.findViewById(R.id.btn_ver_motivo);
        }
    }
}
