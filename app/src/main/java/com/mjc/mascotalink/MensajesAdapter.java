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
import com.mjc.mascotalink.MyApplication;

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
        if (chat.getUltimo_timestamp() != null) {
            holder.tvHora.setText(TimeUtils.getRelativeTimeString(chat.getUltimo_timestamp()));
        } else {
            holder.tvHora.setText("");
        }

        if (chat.getFotoOtroUsuario() != null && !chat.getFotoOtroUsuario().isEmpty()) {
            Glide.with(context)
                    .load(MyApplication.getFixedUrl(chat.getFotoOtroUsuario()))
                    .placeholder(R.drawable.ic_user_placeholder)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }

                // Lógica para estado "Escribiendo...", "Online" y "No leídos"
                String estado = chat.getEstadoOtroUsuario();
                int unreadCount = chat.getMensajesNoLeidosCount();
        
                if ("escribiendo".equals(estado)) {
                    holder.tvUltimoMensaje.setText("Escribiendo...");
                    holder.tvUltimoMensaje.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.green_success));
                    holder.tvUltimoMensaje.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
                    holder.viewEstadoOnline.setVisibility(View.VISIBLE);
                } else {
                    holder.tvUltimoMensaje.setText(chat.getUltimo_mensaje());
                    
                    if (unreadCount > 0) {
                        // Estilo NO LEÍDO: Negrita y color oscuro
                        holder.tvUltimoMensaje.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.black));
                        holder.tvUltimoMensaje.setTypeface(null, android.graphics.Typeface.BOLD);
                    } else {
                        // Estilo LEÍDO: Normal y color gris
                        holder.tvUltimoMensaje.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary));
                        holder.tvUltimoMensaje.setTypeface(null, android.graphics.Typeface.NORMAL);
                    }
        
                    if ("online".equals(estado)) {
                        holder.viewEstadoOnline.setVisibility(View.VISIBLE);
                    } else {
                        holder.viewEstadoOnline.setVisibility(View.GONE);
                    }
                }
        
                // Badge de no leídos (siempre visible si hay > 0, independiente del estado escribiendo)
                if (unreadCount > 0) {
                    holder.badgeNoLeidos.setVisibility(View.VISIBLE);
                    holder.badgeNoLeidos.setText(String.valueOf(unreadCount));
                } else {
                    holder.badgeNoLeidos.setVisibility(View.GONE);
                }
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
