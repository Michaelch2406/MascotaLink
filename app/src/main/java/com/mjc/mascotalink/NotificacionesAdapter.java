package com.mjc.mascotalink;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.mjc.mascotalink.modelo.Notificacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class NotificacionesAdapter extends RecyclerView.Adapter<NotificacionesAdapter.NotificacionViewHolder> {

    private AsyncListDiffer<Notificacion> differ;
    private OnNotificacionClickListener listener;

    public interface OnNotificacionClickListener {
        void onNotificacionClick(Notificacion notificacion);
    }

    /**
     * DiffUtil.ItemCallback para calcular diferencias entre listas de Notificacion
     */
    private static final DiffUtil.ItemCallback<Notificacion> DIFF_CALLBACK = new DiffUtil.ItemCallback<Notificacion>() {
        @Override
        public boolean areItemsTheSame(@NonNull Notificacion oldItem, @NonNull Notificacion newItem) {
            // Comparar por ID único
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Notificacion oldItem, @NonNull Notificacion newItem) {
            // Comparar contenido relevante
            return Objects.equals(oldItem.getTitulo(), newItem.getTitulo()) &&
                   Objects.equals(oldItem.getMensaje(), newItem.getMensaje()) &&
                   oldItem.isLeida() == newItem.isLeida() &&
                   Objects.equals(oldItem.getTipo(), newItem.getTipo());
        }
    };

    public NotificacionesAdapter(OnNotificacionClickListener listener) {
        this.differ = new AsyncListDiffer<>(this, DIFF_CALLBACK);
        this.listener = listener;
    }

    /**
     * Actualiza la lista con DiffUtil (calcula diferencias automáticamente)
     */
    public void setNotificaciones(List<Notificacion> notificaciones) {
        differ.submitList(notificaciones);
    }

    @NonNull
    @Override
    public NotificacionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notificacion, parent, false);
        return new NotificacionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificacionViewHolder holder, int position) {
        Notificacion notificacion = differ.getCurrentList().get(position);
        holder.bind(notificacion);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
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
                        iconRes = R.drawable.ic_messages;
                        iconTint = R.color.green_success;
                        break;
                    case "PASEO":
                        iconRes = R.drawable.ic_walk;
                        iconTint = R.color.orange_500;
                        break;
                    case "PAGO":
                        iconRes = R.drawable.ic_payment;
                        iconTint = R.color.green_success;
                        break;
                    case "SISTEMA":
                        iconRes = R.drawable.ic_notifications;
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
