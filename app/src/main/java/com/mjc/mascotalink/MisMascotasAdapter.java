package com.mjc.mascotalink;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.utils.InputUtils;

import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Adapter para la lista completa de mascotas en MisMascotasActivity.
 * Muestra información detallada: avatar, nombre, raza, sexo, edad y peso.
 * Utiliza InputUtils para formateo de texto.
 */
public class MisMascotasAdapter extends RecyclerView.Adapter<MisMascotasAdapter.MascotaViewHolder> {

    private final Context context;
    private final List<MascotaCompleta> mascotaList;
    private OnMascotaClickListener listener;

    public interface OnMascotaClickListener {
        void onMascotaClick(MascotaCompleta mascota);
    }

    public MisMascotasAdapter(Context context, List<MascotaCompleta> mascotaList) {
        this.context = context;
        this.mascotaList = mascotaList;
    }

    public void setOnMascotaClickListener(OnMascotaClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public MascotaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_mascota_card, parent, false);
        return new MascotaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MascotaViewHolder holder, int position) {
        MascotaCompleta mascota = mascotaList.get(position);

        // Nombre capitalizado usando InputUtils
        String nombre = InputUtils.capitalizeWords(
                InputUtils.getOrDefault(mascota.getNombre(), "Sin nombre")
        );
        holder.tvNombre.setText(nombre);

        // Raza y sexo formateados
        String raza = InputUtils.capitalizeWords(
                InputUtils.getOrDefault(mascota.getRaza(), "Raza desconocida")
        );
        String sexo = formatearSexo(mascota.getSexo());
        holder.tvRazaSexo.setText(raza + " - " + sexo);

        // Detalles: edad y peso
        String detalles = formatearDetalles(mascota.getEdadAnios(), mascota.getPesoKg());
        holder.tvDetalles.setText(detalles);

        // Cargar imagen con Glide
        if (context instanceof android.app.Activity && !((android.app.Activity) context).isDestroyed()) {
            Glide.with(context)
                    .load(MyApplication.getFixedUrl(mascota.getFotoUrl()))
                    .placeholder(R.drawable.foto_principal_mascota)
                    .error(R.drawable.foto_principal_mascota)
                    .circleCrop()
                    .into(holder.ivAvatar);
        }

        // Click listener con rate limiting de InputUtils
        holder.cardMascota.setOnClickListener(
                InputUtils.createSafeClickListener(v -> {
                    if (listener != null) {
                        listener.onMascotaClick(mascota);
                    }
                })
        );
    }

    @Override
    public int getItemCount() {
        return mascotaList.size();
    }

    /**
     * Formatea el sexo de la mascota
     */
    private String formatearSexo(String sexo) {
        if (!InputUtils.isNotEmpty(sexo)) {
            return "N/A";
        }
        String sexoLower = sexo.toLowerCase(Locale.getDefault());
        if (sexoLower.equals("m") || sexoLower.equals("macho") || sexoLower.equals("male")) {
            return "Macho";
        } else if (sexoLower.equals("f") || sexoLower.equals("h") || sexoLower.equals("hembra") || sexoLower.equals("female")) {
            return "Hembra";
        }
        return InputUtils.capitalizeWords(sexo);
    }

    /**
     * Formatea los detalles de edad y peso
     */
    private String formatearDetalles(Integer edadAnios, Double pesoKg) {
        StringBuilder detalles = new StringBuilder();

        // Edad
        if (edadAnios != null && edadAnios > 0) {
            detalles.append(edadAnios);
            detalles.append(edadAnios == 1 ? " año" : " años");
        } else {
            detalles.append("Edad N/A");
        }

        detalles.append(" • ");

        // Peso
        if (pesoKg != null && pesoKg > 0) {
            if (pesoKg == Math.floor(pesoKg)) {
                detalles.append(String.format(Locale.getDefault(), "%.0f kg", pesoKg));
            } else {
                detalles.append(String.format(Locale.getDefault(), "%.1f kg", pesoKg));
            }
        } else {
            detalles.append("Peso N/A");
        }

        return detalles.toString();
    }

    /**
     * Actualiza la lista de mascotas
     */
    public void updateList(List<MascotaCompleta> nuevaLista) {
        this.mascotaList.clear();
        this.mascotaList.addAll(nuevaLista);
        notifyDataSetChanged();
    }

    static class MascotaViewHolder extends RecyclerView.ViewHolder {
        CardView cardMascota;
        CircleImageView ivAvatar;
        TextView tvNombre;
        TextView tvRazaSexo;
        TextView tvDetalles;
        ImageView ivChevron;

        public MascotaViewHolder(@NonNull View itemView) {
            super(itemView);
            cardMascota = itemView.findViewById(R.id.card_mascota);
            ivAvatar = itemView.findViewById(R.id.iv_mascota_avatar);
            tvNombre = itemView.findViewById(R.id.tv_mascota_nombre);
            tvRazaSexo = itemView.findViewById(R.id.tv_mascota_raza_sexo);
            tvDetalles = itemView.findViewById(R.id.tv_mascota_detalles);
            ivChevron = itemView.findViewById(R.id.iv_chevron);
        }
    }

    /**
     * Modelo de datos completo para mascota con todos los campos necesarios
     */
    public static class MascotaCompleta {
        private String id;
        private String nombre;
        private String raza;
        private String sexo;
        private Integer edadAnios;
        private Double pesoKg;
        private String fotoUrl;
        private String ownerId;

        public MascotaCompleta() {}

        // Getters y Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getRaza() { return raza; }
        public void setRaza(String raza) { this.raza = raza; }

        public String getSexo() { return sexo; }
        public void setSexo(String sexo) { this.sexo = sexo; }

        public Integer getEdadAnios() { return edadAnios; }
        public void setEdadAnios(Integer edadAnios) { this.edadAnios = edadAnios; }

        public Double getPesoKg() { return pesoKg; }
        public void setPesoKg(Double pesoKg) { this.pesoKg = pesoKg; }

        public String getFotoUrl() { return fotoUrl; }
        public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

        public String getOwnerId() { return ownerId; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    }
}
