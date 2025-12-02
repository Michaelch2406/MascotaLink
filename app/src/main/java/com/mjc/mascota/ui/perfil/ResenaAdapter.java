package com.mjc.mascota.ui.perfil;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.mjc.mascota.modelo.Resena;
import com.mjc.mascotalink.R;
import com.mjc.mascotalink.MyApplication;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ResenaAdapter extends RecyclerView.Adapter<ResenaAdapter.ResenaViewHolder> {

    private final Context context;
    private final List<Resena> resenas;

    public ResenaAdapter(Context context, List<Resena> resenas) {
        this.context = context;
        this.resenas = resenas;
    }

    @NonNull
    @Override
    public ResenaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_resena, parent, false);
        return new ResenaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResenaViewHolder holder, int position) {
        Resena resena = resenas.get(position);

        holder.tvAutor.setText(resena.getAutorNombre());
        holder.tvComentario.setText(resena.getComentario());
        holder.ratingBar.setRating(resena.getCalificacion());

        if (resena.getFecha() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy", new Locale("es", "ES"));
            holder.tvFecha.setText(sdf.format(resena.getFecha().toDate()));
        }

        Glide.with(context)
                .load(MyApplication.getFixedUrl(resena.getAutorFotoUrl()))
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(holder.ivAutor);
    }

    @Override
    public int getItemCount() {
        return resenas.size();
    }

    public void addResenas(List<Resena> nuevasResenas) {
        int startPosition = resenas.size();
        resenas.addAll(nuevasResenas);
        notifyItemRangeInserted(startPosition, nuevasResenas.size());
    }

    static class ResenaViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAutor;
        TextView tvAutor, tvComentario, tvFecha;
        RatingBar ratingBar;

        public ResenaViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAutor = itemView.findViewById(R.id.iv_autor_resena);
            tvAutor = itemView.findViewById(R.id.tv_autor_resena);
            tvComentario = itemView.findViewById(R.id.tv_comentario_resena);
            tvFecha = itemView.findViewById(R.id.tv_fecha_resena);
            ratingBar = itemView.findViewById(R.id.rating_bar_resena);
        }
    }
}
