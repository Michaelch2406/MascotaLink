package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.modelo.Chat;
import com.mjc.mascotalink.util.TimeUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class MensajesAdapter extends RecyclerView.Adapter<MensajesAdapter.ViewHolder> {

    private Context context;
    private List<Chat> chats;
    private OnChatClickListener listener;

    public interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    public MensajesAdapter(Context context, OnChatClickListener listener) {
        this.context = context;
        this.chats = new ArrayList<>();
        this.listener = listener;
    }

    public void actualizarConversaciones(List<Chat> nuevosChats) {
        this.chats = nuevosChats;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_conversacion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Chat chat = chats.get(position);

        holder.tvNombre.setText(chat.getNombreOtroUsuario());
        holder.tvUltimoMensaje.setText(chat.getUltimo_mensaje());

        if (chat.getUltimo_timestamp() != null) {
            holder.tvHora.setText(TimeUtils.getRelativeTimeString(chat.getUltimo_timestamp()));
        } else {
            holder.tvHora.setText("");
        }

        if (chat.getFotoOtroUsuario() != null && !chat.getFotoOtroUsuario().isEmpty()) {
            Glide.with(context)
                    .load(chat.getFotoOtroUsuario())
                    .placeholder(R.drawable.ic_user_placeholder)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }

        if ("online".equals(chat.getEstadoOtroUsuario())) {
            holder.viewEstadoOnline.setVisibility(View.VISIBLE);
        } else {
            holder.viewEstadoOnline.setVisibility(View.GONE);
        }
        
        // Badge logic would go here if mapped correctly from 'mensajes_no_leidos'
        // For now, hide it or handle if implemented
        holder.badgeNoLeidos.setVisibility(View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onChatClick(chat);
        });
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvUltimoMensaje, tvHora, badgeNoLeidos;
        CircleImageView ivAvatar;
        View viewEstadoOnline;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tv_nombre);
            tvUltimoMensaje = itemView.findViewById(R.id.tv_ultimo_mensaje);
            tvHora = itemView.findViewById(R.id.tv_hora);
            badgeNoLeidos = itemView.findViewById(R.id.badge_no_leidos);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            viewEstadoOnline = itemView.findViewById(R.id.view_estado_online);
        }
    }
}
