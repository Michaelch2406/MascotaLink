package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;
import com.mjc.mascotalink.MyApplication;

import java.util.List;
import java.util.Locale;

public class HistorialPaseosAdapter extends RecyclerView.Adapter<HistorialPaseosAdapter.ViewHolder> {

    private Context context;
    private List<Paseo> paseos;
    private OnPaseoClickListener listener;
    private String userRole;

    public interface OnPaseoClickListener {
        void onPaseoClick(Paseo paseo);
    }

    public HistorialPaseosAdapter(Context context, List<Paseo> paseos, String userRole, OnPaseoClickListener listener) {
        this.context = context;
        this.paseos = paseos;
        this.userRole = userRole;
        this.listener = listener;
    }

    public void updateList(List<Paseo> newPaseos) {
        this.paseos = newPaseos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_historial_paseo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Paseo paseo = paseos.get(position);

        // Determinar qué nombre y foto mostrar según el rol del usuario
        String nombreMostrar;
        String fotoUrl;
        String subTitulo;

        if ("PASEADOR".equalsIgnoreCase(userRole)) {
            // El usuario es Paseador, mostrar datos del Dueño/Mascota
            nombreMostrar = paseo.getMascotaNombre() != null ? paseo.getMascotaNombre() : "Mascota";
            fotoUrl = paseo.getMascotaFoto();
            subTitulo = "Dueño: " + (paseo.getDuenoNombre() != null ? paseo.getDuenoNombre() : "Desconocido");
        } else {
            // El usuario es Dueño, mostrar datos del Paseador
            nombreMostrar = paseo.getPaseadorNombre() != null ? paseo.getPaseadorNombre() : "Paseador";
            fotoUrl = paseo.getPaseadorFoto();
            subTitulo = "Mascota: " + (paseo.getMascotaNombre() != null ? paseo.getMascotaNombre() : "Mascota");
        }

        holder.tvNombre.setText(nombreMostrar);
        holder.tvSubtitulo.setText(subTitulo);
        holder.tvFecha.setText(paseo.getFechaFormateada());
        
        String duracion = paseo.getDuracion_minutos() + " min";
        holder.tvDuracion.setText(duracion);
        
        String costo = String.format(Locale.US, "$%.2f", paseo.getCosto_total());
        holder.tvCosto.setText(costo);

        // Cargar imagen
        if (fotoUrl != null && !fotoUrl.isEmpty()) {
            Glide.with(context)
                    .load(MyApplication.getFixedUrl(fotoUrl))
                    .placeholder(R.drawable.ic_pet_placeholder) // Fallback genérico
                    .error(R.drawable.ic_pet_placeholder)
                    .centerCrop()
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_pet_placeholder);
        }

        // Configurar estado visual
        configurarEstado(holder, paseo.getEstado());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPaseoClick(paseo);
        });
    }

    private void configurarEstado(ViewHolder holder, String estado) {
        if (estado == null) estado = "";
        holder.chipEstado.setText(estado.toUpperCase());

        int colorRes;
        int bgRes; 

        switch (estado) {
            case "COMPLETADO":
            case "FINALIZADO":
                colorRes = R.color.green_700;
                bgRes = R.color.green_100;
                break;
            case "CANCELADO":
                colorRes = R.color.red_error;
                bgRes = R.color.red_100;
                break;
            case "RECHAZADO":
                colorRes = R.color.orange_500;
                bgRes = R.color.orange_100;
                break;
            default:
                colorRes = R.color.text_secondary;
                bgRes = R.color.gray_100;
                break;
        }
        
        holder.chipEstado.setChipBackgroundColorResource(bgRes);
        holder.chipEstado.setTextColor(ContextCompat.getColor(context, colorRes));
    }

    @Override
    public int getItemCount() {
        return paseos.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvSubtitulo, tvFecha, tvDuracion, tvCosto;
        Chip chipEstado;
        ImageView ivAvatar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tv_nombre);
            tvSubtitulo = itemView.findViewById(R.id.tv_subtitulo);
            tvFecha = itemView.findViewById(R.id.tv_fecha);
            chipEstado = itemView.findViewById(R.id.chip_estado);
            tvDuracion = itemView.findViewById(R.id.tv_duracion);
            tvCosto = itemView.findViewById(R.id.tv_costo);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
        }
    }
}
