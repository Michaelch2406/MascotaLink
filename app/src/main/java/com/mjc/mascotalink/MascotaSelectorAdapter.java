package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.mjc.mascotalink.MyApplication;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.List;

public class MascotaSelectorAdapter extends RecyclerView.Adapter<MascotaSelectorAdapter.MascotaViewHolder> {

    private final Context context;
    private final List<Mascota> mascotaList;
    private int selectedPosition = -1;
    private OnMascotaSelectedListener listener;

    public interface OnMascotaSelectedListener {
        void onMascotaSelected(Mascota mascota, int position);
    }

    public MascotaSelectorAdapter(Context context, List<Mascota> mascotaList, OnMascotaSelectedListener listener) {
        this.context = context;
        this.mascotaList = mascotaList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MascotaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_mascota_selector, parent, false);
        return new MascotaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MascotaViewHolder holder, int position) {
        Mascota mascota = mascotaList.get(position);
        holder.tvNombre.setText(mascota.getNombre());

        // Cargar foto de la mascota
        Glide.with(context)
                .load(MyApplication.getFixedUrl(mascota.getFotoUrl()))
                .placeholder(R.drawable.ic_pet_placeholder)
                .circleCrop()
                .into(holder.ivFoto);

        // Cambiar color del borde según selección
        if (selectedPosition == position) {
            holder.ivFoto.setBorderColor(context.getResources().getColor(R.color.blue_primary));
            holder.ivFoto.setBorderWidth(8);
        } else {
            holder.ivFoto.setBorderColor(context.getResources().getColor(R.color.gray_light));
            holder.ivFoto.setBorderWidth(6);
        }

        holder.itemView.setOnClickListener(v -> {
            int previousPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);
            if (listener != null) {
                listener.onMascotaSelected(mascota, selectedPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mascotaList.size();
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    static class MascotaViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivFoto;
        TextView tvNombre;

        public MascotaViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFoto = itemView.findViewById(R.id.iv_mascota_foto);
            tvNombre = itemView.findViewById(R.id.tv_mascota_nombre);
        }
    }

    // Clase modelo Mascota
    public static class Mascota {
        private String id;
        private String nombre;
        private String fotoUrl;
        private boolean activo;

        public Mascota() {}

        public Mascota(String id, String nombre, String fotoUrl, boolean activo) {
            this.id = id;
            this.nombre = nombre;
            this.fotoUrl = fotoUrl;
            this.activo = activo;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getFotoUrl() { return fotoUrl; }
        public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

        public boolean isActivo() { return activo; }
        public void setActivo(boolean activo) { this.activo = activo; }
    }
}
