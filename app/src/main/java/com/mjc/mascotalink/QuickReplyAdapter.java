package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter para mostrar plantillas de mensajes rápidos.
 */
public class QuickReplyAdapter extends RecyclerView.Adapter<QuickReplyAdapter.ViewHolder> {

    private Context context;
    private List<QuickReply> quickReplies;
    private OnQuickReplyClickListener listener;

    public interface OnQuickReplyClickListener {
        void onQuickReplyClick(String message);
    }

    public static class QuickReply {
        String emoji;
        String text;

        public QuickReply(String emoji, String text) {
            this.emoji = emoji;
            this.text = text;
        }
    }

    public QuickReplyAdapter(Context context, OnQuickReplyClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.quickReplies = new ArrayList<>();
        initializeQuickReplies();
    }

    private void initializeQuickReplies() {
        quickReplies.add(new QuickReply("🚶", "Voy en camino"));
        quickReplies.add(new QuickReply("📍", "Llegué a tu dirección"));
        quickReplies.add(new QuickReply("🐕", "Iniciando el paseo"));
        quickReplies.add(new QuickReply("🏠", "Estamos de regreso"));
        quickReplies.add(new QuickReply("✅", "El paseo terminó exitosamente"));
        quickReplies.add(new QuickReply("💧", "Le di agua a tu mascota"));
        quickReplies.add(new QuickReply("🎾", "Jugamos en el parque"));
        quickReplies.add(new QuickReply("😊", "Tu mascota se portó muy bien"));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_quick_reply, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuickReply reply = quickReplies.get(position);
        holder.bind(reply);
    }

    @Override
    public int getItemCount() {
        return quickReplies.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView tvEmoji;
        TextView tvText;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_quick_reply);
            tvEmoji = itemView.findViewById(R.id.tv_emoji);
            tvText = itemView.findViewById(R.id.tv_text);
        }

        void bind(QuickReply reply) {
            tvEmoji.setText(reply.emoji);
            tvText.setText(reply.text);

            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onQuickReplyClick(reply.emoji + " " + reply.text);
                }
            });
        }
    }
}
