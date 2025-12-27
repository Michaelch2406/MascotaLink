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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MascotaSelectorAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MASCOTA = 0;
    private static final int VIEW_TYPE_ADD_BUTTON = 1;

    private final Context context;
    private final List<Mascota> mascotaList;
    private Set<Integer> selectedPositions;
    private OnMascotaSelectedListener listener;

    public interface OnMascotaSelectedListener {
        void onMascotasSelected(List<Mascota> mascotas);
        void onAddMascotaClicked();
    }

    public MascotaSelectorAdapter(Context context, List<Mascota> mascotaList, OnMascotaSelectedListener listener) {
        this.context = context;
        this.mascotaList = mascotaList;
        this.listener = listener;
        this.selectedPositions = new HashSet<>();
    }

    @Override
    public int getItemViewType(int position) {
        return position == mascotaList.size() ? VIEW_TYPE_ADD_BUTTON : VIEW_TYPE_MASCOTA;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ADD_BUTTON) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_mascota_add_button, parent, false);
            return new AddButtonViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_mascota_selector, parent, false);
            return new MascotaViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MascotaViewHolder) {
            MascotaViewHolder mascotaHolder = (MascotaViewHolder) holder;
            Mascota mascota = mascotaList.get(position);
            mascotaHolder.tvNombre.setText(mascota.getNombre());

            Glide.with(context)
                    .load(MyApplication.getFixedUrl(mascota.getFotoUrl()))
                    .placeholder(R.drawable.ic_pet_placeholder)
                    .circleCrop()
                    .into(mascotaHolder.ivFoto);

            boolean isSelected = selectedPositions.contains(position);
            if (isSelected) {
                mascotaHolder.ivFoto.setBorderColor(context.getResources().getColor(R.color.blue_primary));
                mascotaHolder.ivFoto.setBorderWidth(8);
            } else {
                mascotaHolder.ivFoto.setBorderColor(context.getResources().getColor(R.color.gray_light));
                mascotaHolder.ivFoto.setBorderWidth(6);
            }

            mascotaHolder.itemView.setOnClickListener(v -> {
                int clickedPosition = mascotaHolder.getAdapterPosition();
                if (selectedPositions.contains(clickedPosition)) {
                    selectedPositions.remove(clickedPosition);
                } else {
                    selectedPositions.add(clickedPosition);
                }
                notifyItemChanged(clickedPosition);

                if (listener != null) {
                    listener.onMascotasSelected(getSelectedMascotas());
                }
            });
        } else if (holder instanceof AddButtonViewHolder) {
            AddButtonViewHolder addButtonHolder = (AddButtonViewHolder) holder;
            addButtonHolder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAddMascotaClicked();
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mascotaList.size() + 1;
    }

    public void setSelectedPosition(int position) {
        selectedPositions.clear();
        if (position != -1 && position < mascotaList.size()) {
            selectedPositions.add(position);
        }
        notifyDataSetChanged();
    }

    public List<Mascota> getSelectedMascotas() {
        List<Mascota> selected = new ArrayList<>();
        for (int position : selectedPositions) {
            if (position < mascotaList.size()) {
                selected.add(mascotaList.get(position));
            }
        }
        return selected;
    }

    public void clearSelection() {
        selectedPositions.clear();
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

    static class AddButtonViewHolder extends RecyclerView.ViewHolder {
        public AddButtonViewHolder(@NonNull View itemView) {
            super(itemView);
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
