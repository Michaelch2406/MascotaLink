package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class MascotaDetalleAdapter extends RecyclerView.Adapter<MascotaDetalleAdapter.ViewHolder> {

    public static class MascotaDetalle {
        private String nombre;
        private String raza;
        private Integer edad;
        private Double peso;
        private String notas;

        public MascotaDetalle() {}

        public MascotaDetalle(String nombre, String raza, Integer edad, Double peso, String notas) {
            this.nombre = nombre;
            this.raza = raza;
            this.edad = edad;
            this.peso = peso;
            this.notas = notas;
        }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getRaza() { return raza; }
        public void setRaza(String raza) { this.raza = raza; }
        public Integer getEdad() { return edad; }
        public void setEdad(Integer edad) { this.edad = edad; }
        public Double getPeso() { return peso; }
        public void setPeso(Double peso) { this.peso = peso; }
        public String getNotas() { return notas; }
        public void setNotas(String notas) { this.notas = notas; }
    }

    private final Context context;
    private final List<MascotaDetalle> mascotas;

    public MascotaDetalleAdapter(Context context, List<MascotaDetalle> mascotas) {
        this.context = context;
        this.mascotas = mascotas;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_mascota_detalle_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MascotaDetalle mascota = mascotas.get(position);

        // Nombre
        holder.tvNombre.setText(mascota.getNombre() != null ? mascota.getNombre() : "Mascota " + (position + 1));

        // Raza
        holder.tvRaza.setText(mascota.getRaza() != null ? mascota.getRaza() : "No especificada");

        // Edad
        if (mascota.getEdad() != null) {
            holder.tvEdad.setText(mascota.getEdad() + (mascota.getEdad() == 1 ? " año" : " años"));
        } else {
            holder.tvEdad.setText("No especificada");
        }

        // Peso
        if (mascota.getPeso() != null) {
            holder.tvPeso.setText(String.format(Locale.US, "%.1f kg", mascota.getPeso()));
        } else {
            holder.tvPeso.setText("No especificado");
        }

        // Notas
        if (mascota.getNotas() != null && !mascota.getNotas().isEmpty()) {
            holder.tvNotas.setText(mascota.getNotas());
        } else {
            holder.tvNotas.setText("Sin notas adicionales");
        }
    }

    @Override
    public int getItemCount() {
        return mascotas.size();
    }

    public void updateList(List<MascotaDetalle> nuevasMascotas) {
        mascotas.clear();
        mascotas.addAll(nuevasMascotas);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre;
        TextView tvRaza;
        TextView tvEdad;
        TextView tvPeso;
        TextView tvNotas;

        ViewHolder(View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tv_mascota_nombre);
            tvRaza = itemView.findViewById(R.id.tv_mascota_raza);
            tvEdad = itemView.findViewById(R.id.tv_mascota_edad);
            tvPeso = itemView.findViewById(R.id.tv_mascota_peso);
            tvNotas = itemView.findViewById(R.id.tv_mascota_notas);
        }
    }
}
