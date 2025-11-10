package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.SolicitudesActivity.Solicitud;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class SolicitudesAdapter extends RecyclerView.Adapter<SolicitudesAdapter.SolicitudViewHolder> {

    private Context context;
    private List<Solicitud> solicitudesList;
    private OnSolicitudClickListener listener;

    public interface OnSolicitudClickListener {
        void onSolicitudClick(Solicitud solicitud);
        void onAceptarClick(Solicitud solicitud);
    }

    public SolicitudesAdapter(Context context, List<Solicitud> solicitudesList, OnSolicitudClickListener listener) {
        this.context = context;
        this.solicitudesList = solicitudesList;
        this.listener = listener;
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

        // Fecha y hora
        if (solicitud.getFechaCreacion() != null && solicitud.getHoraInicio() != null) {
            String fechaHora = formatearFechaHora(solicitud.getFechaCreacion(), solicitud.getHoraInicio());
            holder.tvFechaHora.setText(fechaHora);
        } else {
            holder.tvFechaHora.setText("Fecha no disponible");
        }

        // Nombre del dueño
        holder.tvDuenoNombre.setText(solicitud.getDuenoNombre() != null ? solicitud.getDuenoNombre() : "Usuario desconocido");

        // Raza de la mascota
        holder.tvMascotaRaza.setText(solicitud.getMascotaRaza() != null ? solicitud.getMascotaRaza() : "Mascota");

        // Foto del dueño
        if (solicitud.getDuenoFotoUrl() != null && !solicitud.getDuenoFotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(solicitud.getDuenoFotoUrl())
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(holder.ivDuenoFoto);
        } else {
            holder.ivDuenoFoto.setImageResource(R.drawable.ic_person);
        }

        // Click en botón Aceptar
        holder.btnAceptar.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAceptarClick(solicitud);
            }
        });

        // Click en toda la tarjeta
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSolicitudClick(solicitud);
            }
        });
    }

    private String formatearFechaHora(Date fechaCreacion, Date horaInicio) {
        try {
            // Formato para fecha: "12 de junio" (usando fechaCreacion para mostrar cuándo se creó la solicitud)
            SimpleDateFormat sdfFecha = new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES"));
            String fechaStr = sdfFecha.format(fechaCreacion);

            // Formato para hora: "10:00 AM" (usando horaInicio para mostrar la hora del paseo)
            SimpleDateFormat sdfHora = new SimpleDateFormat("h:mm a", Locale.US);
            String horaStr = sdfHora.format(horaInicio);

            return fechaStr + " · " + horaStr;
        } catch (Exception e) {
            return "Fecha no disponible";
        }
    }

    @Override
    public int getItemCount() {
        return solicitudesList.size();
    }

    static class SolicitudViewHolder extends RecyclerView.ViewHolder {
        TextView tvFechaHora;
        TextView tvDuenoNombre;
        TextView tvMascotaRaza;
        CircleImageView ivDuenoFoto;
        Button btnAceptar;

        public SolicitudViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFechaHora = itemView.findViewById(R.id.tv_fecha_hora);
            tvDuenoNombre = itemView.findViewById(R.id.tv_dueno_nombre);
            tvMascotaRaza = itemView.findViewById(R.id.tv_mascota_raza);
            ivDuenoFoto = itemView.findViewById(R.id.iv_dueno_foto);
            btnAceptar = itemView.findViewById(R.id.btn_aceptar);
        }
    }
}

