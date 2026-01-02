package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.mjc.mascotalink.MyApplication;
import java.util.List;

public class MascotaAdapter extends RecyclerView.Adapter<MascotaAdapter.MascotaViewHolder> {

    private final Context context;
    private final List<Pet> petList;

    public MascotaAdapter(Context context, List<Pet> petList) {
        this.context = context;
        this.petList = petList;
    }

    @NonNull
    @Override
    public MascotaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_mascota_registrada, parent, false);
        return new MascotaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MascotaViewHolder holder, int position) {
        Pet pet = petList.get(position);
        holder.tvPetName.setText(pet.getName());

        // Mostrar la raza o texto por defecto
        String breed = pet.getBreed();
        if (breed != null && !breed.isEmpty() && !breed.equals("Sin raza")) {
            holder.tvPetBreed.setText(breed);
            holder.tvPetBreed.setVisibility(View.VISIBLE);
        } else {
            holder.tvPetBreed.setVisibility(View.GONE);
        }

        // Cargar imagen con Glide
        Glide.with(context)
                .load(MyApplication.getFixedUrl(pet.getAvatarUrl()))
                .placeholder(R.drawable.foto_principal_mascota)
                .error(R.drawable.foto_principal_mascota)
                .centerCrop()
                .into(holder.ivAvatar);
    }

    @Override
    public int getItemCount() {
        return petList.size();
    }

    static class MascotaViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivAvatar;
        TextView tvPetName;
        TextView tvPetBreed;

        public MascotaViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvPetName = itemView.findViewById(R.id.tvPetName);
            tvPetBreed = itemView.findViewById(R.id.tvPetBreed);
        }
    }
}
