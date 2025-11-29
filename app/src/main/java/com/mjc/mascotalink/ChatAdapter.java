package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mjc.mascotalink.modelo.Mensaje;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    private Context context;
    private List<Mensaje> mensajes;
    private String currentUserId;

    public ChatAdapter(Context context, String currentUserId) {
        this.context = context;
        this.currentUserId = currentUserId;
        this.mensajes = new ArrayList<>();
    }

    public void agregarMensaje(Mensaje mensaje) {
        this.mensajes.add(mensaje);
        notifyItemInserted(mensajes.size() - 1);
    }
    
    public void setMensajes(List<Mensaje> mensajes) {
        this.mensajes = mensajes;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Mensaje mensaje = mensajes.get(position);
        if (mensaje.getId_remitente() != null && mensaje.getId_remitente().equals(currentUserId)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_mensaje_enviado, parent, false);
            return new SentMessageHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_mensaje_recibido, parent, false);
            return new ReceivedMessageHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Mensaje mensaje = mensajes.get(position);
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        String time = mensaje.getTimestamp() != null ? sdf.format(mensaje.getTimestamp()) : "";

        if (holder.getItemViewType() == VIEW_TYPE_SENT) {
            ((SentMessageHolder) holder).bind(mensaje, time);
        } else {
            ((ReceivedMessageHolder) holder).bind(mensaje, time);
        }
    }

    @Override
    public int getItemCount() {
        return mensajes.size();
    }

    static class SentMessageHolder extends RecyclerView.ViewHolder {
        TextView tvMensaje, tvHora;
        ImageView ivEstado;

        SentMessageHolder(View itemView) {
            super(itemView);
            tvMensaje = itemView.findViewById(R.id.tv_mensaje);
            tvHora = itemView.findViewById(R.id.tv_hora);
            ivEstado = itemView.findViewById(R.id.iv_estado);
        }

        void bind(Mensaje mensaje, String time) {
            tvMensaje.setText(mensaje.getTexto());
            tvHora.setText(time);
            // Logic for ivEstado (check/double check) can be added here
        }
    }

    static class ReceivedMessageHolder extends RecyclerView.ViewHolder {
        TextView tvMensaje, tvHora;

        ReceivedMessageHolder(View itemView) {
            super(itemView);
            tvMensaje = itemView.findViewById(R.id.tv_mensaje);
            tvHora = itemView.findViewById(R.id.tv_hora);
        }

        void bind(Mensaje mensaje, String time) {
            tvMensaje.setText(mensaje.getTexto());
            tvHora.setText(time);
        }
    }
}
