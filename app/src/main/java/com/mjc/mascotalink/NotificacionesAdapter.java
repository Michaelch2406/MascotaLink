package com.mjc.mascotalink;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mjc.mascotalink.modelo.Notificacion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NotificacionesAdapter extends RecyclerView.Adapter<NotificacionesAdapter.NotificacionViewHolder> {

    private List<Notificacion> notificaciones = new ArrayList<>();
    private OnNotificacionClickListener listener;

    public interface OnNotificacionClickListener {
        void onNotificacionClick(Notificacion notificacion);
    }

    public NotificacionesAdapter(OnNotificacionClickListener listener) {
        this.listener = listener;
    }

    public void setNotificaciones(List<Notificacion> notificaciones) {
        this.notificaciones = notificaciones;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificacionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notificacion, parent, false);
        return new NotificacionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificacionViewHolder holder, int position) {
        Notificacion notificacion = notificaciones.get(position);
        holder.bind(notificacion);
    }

    @Override
    public int getItemCount() {
        return notificaciones.size();
    }

    class NotificacionViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivIcon;
        private View viewUnreadIndicator;
        private TextView tvTitulo;
        private TextView tvMensaje;
        private TextView tvTiempo;

        public NotificacionViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            viewUnreadIndicator = itemView.findViewById(R.id.view_unread_indicator);
            tvTitulo = itemView.findViewById(R.id.tv_titulo);
            tvMensaje = itemView.findViewById(R.id.tv_mensaje);
            tvTiempo = itemView.findViewById(R.id.tv_tiempo);
        }

        public void bind(Notificacion notificacion) {
            tvTitulo.setText(notificacion.getTitulo());
            tvMensaje.setText(notificacion.getMensaje());

            // Mostrar indicador de no leída
            viewUnreadIndicator.setVisibility(notificacion.isLeida() ? View.GONE : View.VISIBLE);

            // Configurar icono según tipo
            int iconRes = R.drawable.ic_notifications;
            int iconTint = R.color.blue_primary;

            if (notificacion.getTipo() != null) {
                switch (notificacion.getTipo()) {
                    case "RESERVA":
                        iconRes = R.drawable.ic_calendar;
                        iconTint = R.color.blue_primary;
                        break;
                    case "MENSAJE":
                        iconRes = R.drawable.ic_chat;
                        iconTint = R.color.green;
                        break;
                    case "PASEO":
                        iconRes = R.drawable.ic_walk;
                        iconTint = R.color.orange;
                        break;
                    case "PAGO":
                        iconRes = R.drawable.ic_payment;
                        iconTint = R.color.green;
                        break;
                    case "SISTEMA":
                        iconRes = R.drawable.ic_info;
                        iconTint = R.color.text_secondary;
                        break;
                }
            }

            ivIcon.setImageResource(iconRes);
            ivIcon.setColorFilter(itemView.getContext().getColor(iconTint));

            // Calcular tiempo transcurrido
            if (notificacion.getFecha() != null) {
                tvTiempo.setText(getTimeAgo(notificacion.getFecha().toDate().getTime()));
            }

            // Click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificacionClick(notificacion);
                }
            });
        }

        private String getTimeAgo(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            long days = TimeUnit.MILLISECONDS.toDays(diff);

            if (minutes < 1) {
                return "Ahora";
            } else if (minutes < 60) {
                return "Hace " + minutes + " min";
            } else if (hours < 24) {
                return "Hace " + hours + (hours == 1 ? " hora" : " horas");
            } else if (days < 7) {
                return "Hace " + days + (days == 1 ? " día" : " días");
            } else {
                return "Hace más de una semana";
            }
        }
    }
}
