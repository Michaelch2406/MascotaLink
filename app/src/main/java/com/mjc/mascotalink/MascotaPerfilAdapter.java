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
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;

public class MascotaPerfilAdapter extends RecyclerView.Adapter<MascotaPerfilAdapter.ViewHolder> {

    private final Context context;
    private final List<Pet> petList;

    public MascotaPerfilAdapter(Context context, List<Pet> petList) {
        this.context = context;
        this.petList = petList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_mascota_perfil, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Pet pet = petList.get(position);
        holder.tvNombre.setText(pet.getName());
        holder.tvRaza.setText(pet.getBreed()); // Assuming Pet class has getBreed()

        Glide.with(context)
                .load(pet.getAvatarUrl()) // Assuming Pet class has getAvatarUrl()
                .placeholder(R.drawable.foto_principal_mascota)
                .circleCrop()
                .into(holder.ivMascota);

        // Set click listener for the entire item
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PerfilMascotaActivity.class);
            // Get current user ID
            String duenoId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
            intent.putExtra("dueno_id", duenoId);
            intent.putExtra("mascota_id", pet.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return petList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivMascota;
        TextView tvNombre;
        TextView tvRaza;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMascota = itemView.findViewById(R.id.iv_mascota);
            tvNombre = itemView.findViewById(R.id.tv_mascota_nombre);
            tvRaza = itemView.findViewById(R.id.tv_mascota_raza);
        }
    }
}
