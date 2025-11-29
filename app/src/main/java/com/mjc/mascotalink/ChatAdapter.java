package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mjc.mascotalink.modelo.ChatItem;
import com.mjc.mascotalink.modelo.DateSeparator;
import com.mjc.mascotalink.modelo.Mensaje;
import com.mjc.mascotalink.util.TimeUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 0;
    private static final int VIEW_TYPE_RECEIVED = 1;
    private static final int VIEW_TYPE_DATE_SEPARATOR = 2;

    private Context context;
    private List<ChatItem> items;
    private String currentUserId;

    public ChatAdapter(Context context, String currentUserId) {
        this.context = context;
        this.currentUserId = currentUserId;
        this.items = new ArrayList<>();
    }

    /**
     * Agrega un mensaje al final de la lista, insertando separador de fecha si es necesario.
     */
    public void agregarMensaje(Mensaje mensaje) {
        if (mensaje == null) return;
        
        // Verificar si necesitamos agregar un separador de fecha
        if (!items.isEmpty()) {
            ChatItem lastItem = items.get(items.size() - 1);
            
            // Obtener la fecha del último mensaje (no separador)
            Mensaje lastMessage = getLastMessage();
            
            if (lastMessage != null && mensaje.getTimestamp() != null && lastMessage.getTimestamp() != null) {
                // Si las fechas son diferentes, insertar separador
                if (!TimeUtils.isSameDay(lastMessage.getTimestamp(), mensaje.getTimestamp())) {
                    String dateText = TimeUtils.getDateSeparatorText(mensaje.getTimestamp());
                    DateSeparator separator = new DateSeparator(dateText, mensaje.getTimestamp());
                    items.add(separator);
                    notifyItemInserted(items.size() - 1);
                }
            }
        } else if (mensaje.getTimestamp() != null) {
            // Primer mensaje, agregar separador
            String dateText = TimeUtils.getDateSeparatorText(mensaje.getTimestamp());
            DateSeparator separator = new DateSeparator(dateText, mensaje.getTimestamp());
            items.add(separator);
            notifyItemInserted(items.size() - 1);
        }
        
        // Agregar el mensaje
        items.add(mensaje);
        notifyItemInserted(items.size() - 1);
    }
    
    /**
     * Establece la lista completa de mensajes, procesando e insertando separadores de fecha.
     */
    public void setMensajes(List<Mensaje> mensajes) {
        items.clear();
        
        if (mensajes == null || mensajes.isEmpty()) {
            notifyDataSetChanged();
            return;
        }
        
        items = procesarMensajesConSeparadores(mensajes);
        notifyDataSetChanged();
    }

    /**
     * Agrega mensajes al inicio (para paginación), insertando separadores si es necesario.
     */
    public void agregarMensajesAlInicio(List<Mensaje> nuevos) {
        if (nuevos == null || nuevos.isEmpty()) return;
        
        // Procesar los nuevos mensajes con separadores
        List<ChatItem> nuevosItems = procesarMensajesConSeparadores(nuevos);
        
        // Si ya hay items, verificar si necesitamos separador entre los nuevos y los existentes
        if (!items.isEmpty() && !nuevosItems.isEmpty()) {
            ChatItem lastNewItem = nuevosItems.get(nuevosItems.size() - 1);
            ChatItem firstExistingItem = items.get(0);
            
            if (lastNewItem instanceof Mensaje && firstExistingItem instanceof Mensaje) {
                Mensaje lastNew = (Mensaje) lastNewItem;
                Mensaje firstExisting = (Mensaje) firstExistingItem;
                
                if (lastNew.getTimestamp() != null && firstExisting.getTimestamp() != null) {
                    if (!TimeUtils.isSameDay(lastNew.getTimestamp(), firstExisting.getTimestamp())) {
                        // Agregar separador entre los nuevos y existentes
                        String dateText = TimeUtils.getDateSeparatorText(firstExisting.getTimestamp());
                        DateSeparator separator = new DateSeparator(dateText, firstExisting.getTimestamp());
                        nuevosItems.add(separator);
                    }
                }
            }
        }
        
        items.addAll(0, nuevosItems);
        notifyItemRangeInserted(0, nuevosItems.size());
    }
    
    /**
     * Procesa una lista de mensajes e inserta separadores de fecha donde sea necesario.
     */
    private List<ChatItem> procesarMensajesConSeparadores(List<Mensaje> mensajes) {
        List<ChatItem> result = new ArrayList<>();
        
        if (mensajes == null || mensajes.isEmpty()) {
            return result;
        }
        
        Mensaje previousMessage = null;
        
        for (Mensaje mensaje : mensajes) {
            if (mensaje.getTimestamp() == null) {
                result.add(mensaje);
                continue;
            }
            
            // Verificar si necesitamos agregar separador
            if (previousMessage == null) {
                // Primer mensaje, agregar separador
                String dateText = TimeUtils.getDateSeparatorText(mensaje.getTimestamp());
                DateSeparator separator = new DateSeparator(dateText, mensaje.getTimestamp());
                result.add(separator);
            } else if (previousMessage.getTimestamp() != null) {
                // Comparar fechas
                if (!TimeUtils.isSameDay(previousMessage.getTimestamp(), mensaje.getTimestamp())) {
                    String dateText = TimeUtils.getDateSeparatorText(mensaje.getTimestamp());
                    DateSeparator separator = new DateSeparator(dateText, mensaje.getTimestamp());
                    result.add(separator);
                }
            }
            
            result.add(mensaje);
            previousMessage = mensaje;
        }
        
        return result;
    }
    
    /**
     * Obtiene el último mensaje (no separador) de la lista.
     */
    private Mensaje getLastMessage() {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i) instanceof Mensaje) {
                return (Mensaje) items.get(i);
            }
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        ChatItem item = items.get(position);
        
        if (item instanceof DateSeparator) {
            return VIEW_TYPE_DATE_SEPARATOR;
        }
        
        Mensaje mensaje = (Mensaje) item;
        if (mensaje.getId_remitente() != null && mensaje.getId_remitente().equals(currentUserId)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_DATE_SEPARATOR) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_fecha_separador, parent, false);
            return new DateSeparatorHolder(view);
        } else if (viewType == VIEW_TYPE_SENT) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_mensaje_enviado, parent, false);
            return new SentMessageHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_mensaje_recibido, parent, false);
            return new ReceivedMessageHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = items.get(position);
        
        if (holder instanceof DateSeparatorHolder) {
            DateSeparator separator = (DateSeparator) item;
            ((DateSeparatorHolder) holder).bind(separator);
        } else {
            Mensaje mensaje = (Mensaje) item;
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            String time = mensaje.getTimestamp() != null ? sdf.format(mensaje.getTimestamp()) : "";

            if (holder instanceof SentMessageHolder) {
                ((SentMessageHolder) holder).bind(mensaje, time);
            } else if (holder instanceof ReceivedMessageHolder) {
                ((ReceivedMessageHolder) holder).bind(mensaje, time);
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
    
    /**
     * ViewHolder para separadores de fecha.
     */
    static class DateSeparatorHolder extends RecyclerView.ViewHolder {
        TextView tvFecha;
        
        DateSeparatorHolder(View itemView) {
            super(itemView);
            tvFecha = itemView.findViewById(R.id.tv_fecha_separador);
        }
        
        void bind(DateSeparator separator) {
            tvFecha.setText(separator.getDateText());
        }
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

            if (mensaje.isLeido()) {
                ivEstado.setImageResource(R.drawable.ic_check_double);
                ivEstado.setColorFilter(android.graphics.Color.parseColor("#2196F3"));
            } else if (mensaje.isEntregado()) {
                ivEstado.setImageResource(R.drawable.ic_check_double);
                ivEstado.setColorFilter(android.graphics.Color.parseColor("#E0E0E0"));
            } else {
                ivEstado.setImageResource(R.drawable.ic_check_single);
                ivEstado.setColorFilter(android.graphics.Color.parseColor("#E0E0E0"));
            }
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
