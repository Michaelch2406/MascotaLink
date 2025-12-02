package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.imageview.ShapeableImageView;
import com.mjc.mascotalink.SolicitudesActivity.Solicitud;
import com.mjc.mascotalink.utils.SolicitudDiffCallback;
import com.mjc.mascotalink.MyApplication;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SolicitudesAdapter extends RecyclerView.Adapter<SolicitudesAdapter.SolicitudViewHolder> {

    private final Context context;
    private final List<Solicitud> solicitudesList;
    private final OnSolicitudClickListener listener;

    public interface OnSolicitudClickListener {
        void onSolicitudClick(Solicitud solicitud);
        void onAceptarClick(Solicitud solicitud);
    }

    public SolicitudesAdapter(Context context, List<Solicitud> solicitudesList, OnSolicitudClickListener listener) {
        this.context = context;
        this.solicitudesList = new ArrayList<>(solicitudesList); // Copy for DiffUtil
        this.listener = listener;
    }

    public void updateList(List<Solicitud> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SolicitudDiffCallback(this.solicitudesList, newList));
        this.solicitudesList.clear();
        this.solicitudesList.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public SolicitudViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_solicitud, parent, false);
        return new SolicitudViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SolicitudViewHolder holder, int position) {
        Solicitud solicitud = solicitudesList.get(position);
        if (solicitud == null) {
            return;
        }

        // Nombre de mascota (usamos raza como fallback) y dueño
        holder.tvNombreMascota.setText(solicitud.getMascotaRaza() != null ? solicitud.getMascotaRaza() : "Mascota");
        holder.tvNombreDueno.setText(solicitud.getDuenoNombre() != null ? solicitud.getDuenoNombre() : "Usuario desconocido");

        // Fecha y hora en campos separados
        holder.tvFecha.setText(solicitud.getFechaCreacion() != null ? formatearFecha(solicitud.getFechaCreacion()) : "Fecha no disponible");
        holder.tvHora.setText(solicitud.getHoraInicio() != null ? formatearHora(solicitud.getHoraInicio()) : "Hora no disponible");

        // Foto (ShapeableImageView del layout)
        if (solicitud.getDuenoFotoUrl() != null && !solicitud.getDuenoFotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(MyApplication.getFixedUrl(solicitud.getDuenoFotoUrl()))
                    .placeholder(R.drawable.ic_pet_placeholder)
                    .error(R.drawable.ic_pet_placeholder)
                    .centerCrop()
                    .into(holder.ivFotoMascota);
        } else {
            holder.ivFotoMascota.setImageResource(R.drawable.ic_pet_placeholder);
        }

        // Estado / precio (no se tienen en el modelo actual)
        holder.chipEstado.setText("Pendiente");
        holder.chipEstado.setVisibility(View.VISIBLE);
        holder.tvPrecio.setVisibility(View.GONE);

        // Botones de acción: solo usamos aceptar aquí; rechazo se oculta
        holder.btnRechazar.setVisibility(View.GONE);
        holder.btnAceptar.setVisibility(View.VISIBLE);
        holder.btnAceptar.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAceptarClick(solicitud);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSolicitudClick(solicitud);
            }
        });
    }

    private String formatearFecha(Date fechaCreacion) {
        try {
            SimpleDateFormat sdfFecha = new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES"));
            return sdfFecha.format(fechaCreacion);
        } catch (Exception e) {
            return "Fecha no disponible";
        }
    }

    private String formatearHora(Date horaInicio) {
        try {
            SimpleDateFormat sdfHora = new SimpleDateFormat("h:mm a", Locale.US);
            return sdfHora.format(horaInicio);
        } catch (Exception e) {
            return "Hora no disponible";
        }
    }

    @Override
    public int getItemCount() {
        return solicitudesList.size();
    }

    static class SolicitudViewHolder extends RecyclerView.ViewHolder {
        TextView tvFecha;
        TextView tvHora;
        TextView tvNombreDueno;
        TextView tvNombreMascota;
        TextView tvPrecio;
        Chip chipEstado;
        ShapeableImageView ivFotoMascota;
        MaterialButton btnAceptar;
        MaterialButton btnRechazar;

        SolicitudViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFecha = itemView.findViewById(R.id.tv_fecha);
            tvHora = itemView.findViewById(R.id.tv_hora);
            tvNombreDueno = itemView.findViewById(R.id.tv_nombre_dueno);
            tvNombreMascota = itemView.findViewById(R.id.tv_nombre_mascota);
            tvPrecio = itemView.findViewById(R.id.tv_precio);
            chipEstado = itemView.findViewById(R.id.chip_estado);
            ivFotoMascota = itemView.findViewById(R.id.iv_foto_mascota);
            btnAceptar = itemView.findViewById(R.id.btn_aceptar);
            btnRechazar = itemView.findViewById(R.id.btn_rechazar);
        }
    }
}
