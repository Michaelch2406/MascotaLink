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

public class MascotaSelectorAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MASCOTA = 0;
    private static final int VIEW_TYPE_ADD_BUTTON = 1;

    private final Context context;
    private final List<Mascota> mascotaList;
    private int selectedPosition = -1;
    private OnMascotaSelectedListener listener;

    public interface OnMascotaSelectedListener {
        void onMascotaSelected(Mascota mascota, int position);
        void onAddMascotaClicked();
    }

    public MascotaSelectorAdapter(Context context, List<Mascota> mascotaList, OnMascotaSelectedListener listener) {
        this.context = context;
        this.mascotaList = mascotaList;
        this.listener = listener;
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

            if (selectedPosition == position) {
                mascotaHolder.ivFoto.setBorderColor(context.getResources().getColor(R.color.blue_primary));
                mascotaHolder.ivFoto.setBorderWidth(8);
            } else {
                mascotaHolder.ivFoto.setBorderColor(context.getResources().getColor(R.color.gray_light));
                mascotaHolder.ivFoto.setBorderWidth(6);
            }

            mascotaHolder.itemView.setOnClickListener(v -> {
                int previousPosition = selectedPosition;
                selectedPosition = mascotaHolder.getAdapterPosition();
                notifyItemChanged(previousPosition);
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onMascotaSelected(mascota, selectedPosition);
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
        int previousPosition = this.selectedPosition;
        this.selectedPosition = position;

        if (previousPosition != -1) {
            notifyItemChanged(previousPosition);
        }
        if (position != -1) {
            notifyItemChanged(position);
        }
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
