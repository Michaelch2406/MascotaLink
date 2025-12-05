package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.mjc.mascotalink.modelo.ChatItem;
import com.mjc.mascotalink.modelo.DateSeparator;
import com.mjc.mascotalink.modelo.Mensaje;
import com.mjc.mascotalink.util.TimeUtils;
import com.mjc.mascotalink.MyApplication;
import com.bumptech.glide.Glide;
import android.content.Intent;
import android.net.Uri;
import android.widget.ProgressBar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 0;
    private static final int VIEW_TYPE_RECEIVED = 1;
    private static final int VIEW_TYPE_DATE_SEPARATOR = 2;
    private static final int VIEW_TYPE_IMAGE_SENT = 3;
    private static final int VIEW_TYPE_IMAGE_RECEIVED = 4;
    private static final int VIEW_TYPE_LOCATION_SENT = 5;
    private static final int VIEW_TYPE_LOCATION_RECEIVED = 6;

    private Context context;
    private AsyncListDiffer<ChatItem> differ;
    private String currentUserId;
    private int lastPosition = -1;

    /**
     * DiffUtil.ItemCallback para calcular diferencias entre listas de ChatItem
     * Maneja tanto Mensaje como DateSeparator
     */
    private static final DiffUtil.ItemCallback<ChatItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<ChatItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChatItem oldItem, @NonNull ChatItem newItem) {
            // Verificar si son del mismo tipo
            if (oldItem.getType() != newItem.getType()) {
                return false;
            }

            // Comparar según tipo
            if (oldItem instanceof Mensaje && newItem instanceof Mensaje) {
                Mensaje oldMsg = (Mensaje) oldItem;
                Mensaje newMsg = (Mensaje) newItem;
                return Objects.equals(oldMsg.getId(), newMsg.getId());
            } else if (oldItem instanceof DateSeparator && newItem instanceof DateSeparator) {
                DateSeparator oldSep = (DateSeparator) oldItem;
                DateSeparator newSep = (DateSeparator) newItem;
                return Objects.equals(oldSep.getDateText(), newSep.getDateText());
            }

            return false;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatItem oldItem, @NonNull ChatItem newItem) {
            if (oldItem instanceof Mensaje && newItem instanceof Mensaje) {
                Mensaje oldMsg = (Mensaje) oldItem;
                Mensaje newMsg = (Mensaje) newItem;
                return Objects.equals(oldMsg.getTexto(), newMsg.getTexto()) &&
                       oldMsg.isLeido() == newMsg.isLeido() &&
                       oldMsg.isEntregado() == newMsg.isEntregado() &&
                       Objects.equals(oldMsg.getTipo(), newMsg.getTipo()) &&
                       Objects.equals(oldMsg.getImagen_url(), newMsg.getImagen_url());
            } else if (oldItem instanceof DateSeparator && newItem instanceof DateSeparator) {
                DateSeparator oldSep = (DateSeparator) oldItem;
                DateSeparator newSep = (DateSeparator) newItem;
                return Objects.equals(oldSep.getDateText(), newSep.getDateText());
            }

            return false;
        }
    };

    public ChatAdapter(Context context, String currentUserId) {
        this.context = context;
        this.currentUserId = currentUserId;
        this.differ = new AsyncListDiffer<>(this, DIFF_CALLBACK);
    }

    /**
     * Agrega un mensaje al final de la lista, insertando separador de fecha si es necesario.
     */
    public void agregarMensaje(Mensaje mensaje) {
        if (mensaje == null) return;

        List<ChatItem> currentList = new ArrayList<>(differ.getCurrentList());

        // Verificar si necesitamos agregar un separador de fecha
        if (!currentList.isEmpty()) {
            ChatItem lastItem = currentList.get(currentList.size() - 1);
            
            // Obtener la fecha del último mensaje (no separador)
            Mensaje lastMessage = getLastMessage();
            
            if (lastMessage != null && mensaje.getTimestamp() != null && lastMessage.getTimestamp() != null) {
                // Si las fechas son diferentes, insertar separador
                if (!TimeUtils.isSameDay(lastMessage.getTimestamp(), mensaje.getTimestamp())) {
                    String dateText = TimeUtils.getDateSeparatorText(mensaje.getTimestamp());
                    DateSeparator separator = new DateSeparator(dateText, mensaje.getTimestamp());
                    currentList.add(separator);
                }
            }
        } else if (mensaje.getTimestamp() != null) {
            // Primer mensaje, agregar separador
            String dateText = TimeUtils.getDateSeparatorText(mensaje.getTimestamp());
            DateSeparator separator = new DateSeparator(dateText, mensaje.getTimestamp());
            currentList.add(separator);
        }

        // Agregar el mensaje
        currentList.add(mensaje);
        differ.submitList(currentList);
    }
    
    /**
     * Establece la lista completa de mensajes, procesando e insertando separadores de fecha.
     */
    public void setMensajes(List<Mensaje> mensajes) {
        if (mensajes == null || mensajes.isEmpty()) {
            differ.submitList(new ArrayList<>());
            return;
        }

        List<ChatItem> newList = procesarMensajesConSeparadores(mensajes);
        differ.submitList(newList);
    }

    /**
     * Agrega mensajes al inicio (para paginación), insertando separadores si es necesario.
     */
    public void agregarMensajesAlInicio(List<Mensaje> nuevos) {
        if (nuevos == null || nuevos.isEmpty()) return;

        List<ChatItem> currentList = new ArrayList<>(differ.getCurrentList());

        // Procesar los nuevos mensajes con separadores
        List<ChatItem> nuevosItems = procesarMensajesConSeparadores(nuevos);

        // Si ya hay items, verificar si necesitamos separador entre los nuevos y los existentes
        if (!currentList.isEmpty() && !nuevosItems.isEmpty()) {
            ChatItem lastNewItem = nuevosItems.get(nuevosItems.size() - 1);
            ChatItem firstExistingItem = currentList.get(0);
            
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

        nuevosItems.addAll(currentList);
        differ.submitList(nuevosItems);
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
        List<ChatItem> currentList = differ.getCurrentList();
        for (int i = currentList.size() - 1; i >= 0; i--) {
            if (currentList.get(i) instanceof Mensaje) {
                return (Mensaje) currentList.get(i);
            }
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        ChatItem item = differ.getCurrentList().get(position);
        
        if (item instanceof DateSeparator) {
            return VIEW_TYPE_DATE_SEPARATOR;
        }
        
        Mensaje mensaje = (Mensaje) item;
        boolean isSent = mensaje.getId_remitente() != null && mensaje.getId_remitente().equals(currentUserId);
        String tipo = mensaje.getTipo() != null ? mensaje.getTipo() : "texto";
        
        // Determinar tipo según contenido y remitente
        if ("imagen".equals(tipo)) {
            return isSent ? VIEW_TYPE_IMAGE_SENT : VIEW_TYPE_IMAGE_RECEIVED;
        } else if ("ubicacion".equals(tipo)) {
            return isSent ? VIEW_TYPE_LOCATION_SENT : VIEW_TYPE_LOCATION_RECEIVED;
        } else {
            return isSent ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_DATE_SEPARATOR:
                view = LayoutInflater.from(context).inflate(R.layout.item_fecha_separador, parent, false);
                return new DateSeparatorHolder(view);
                
            case VIEW_TYPE_SENT:
                view = LayoutInflater.from(context).inflate(R.layout.item_mensaje_enviado, parent, false);
                return new SentMessageHolder(view);
                
            case VIEW_TYPE_RECEIVED:
                view = LayoutInflater.from(context).inflate(R.layout.item_mensaje_recibido, parent, false);
                return new ReceivedMessageHolder(view);
                
            case VIEW_TYPE_IMAGE_SENT:
                view = LayoutInflater.from(context).inflate(R.layout.item_mensaje_imagen_enviado, parent, false);
                return new ImageMessageHolder(view, true);
                
            case VIEW_TYPE_IMAGE_RECEIVED:
                view = LayoutInflater.from(context).inflate(R.layout.item_mensaje_imagen_recibido, parent, false);
                return new ImageMessageHolder(view, false);
                
            case VIEW_TYPE_LOCATION_SENT:
                view = LayoutInflater.from(context).inflate(R.layout.item_mensaje_ubicacion, parent, false);
                return new LocationMessageHolder(view, true);
                
            case VIEW_TYPE_LOCATION_RECEIVED:
                view = LayoutInflater.from(context).inflate(R.layout.item_mensaje_ubicacion, parent, false);
                return new LocationMessageHolder(view, false);
                
            default:
                view = LayoutInflater.from(context).inflate(R.layout.item_mensaje_recibido, parent, false);
                return new ReceivedMessageHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = differ.getCurrentList().get(position);
        
        // Aplicar animación solo a items nuevos
        setAnimation(holder.itemView, position);
        
        if (holder instanceof DateSeparatorHolder) {
            DateSeparator separator = (DateSeparator) item;
            ((DateSeparatorHolder) holder).bind(separator);
        } else if (holder instanceof ImageMessageHolder) {
            Mensaje mensaje = (Mensaje) item;
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            String time = mensaje.getTimestamp() != null ? sdf.format(mensaje.getTimestamp()) : "";
            ((ImageMessageHolder) holder).bind(mensaje, time);
        } else if (holder instanceof LocationMessageHolder) {
            Mensaje mensaje = (Mensaje) item;
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            String time = mensaje.getTimestamp() != null ? sdf.format(mensaje.getTimestamp()) : "";
            ((LocationMessageHolder) holder).bind(mensaje, time);
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
        return differ.getCurrentList().size();
    }
    
    /**
     * Aplica animación suave a los items cuando se muestran.
     */
    private void setAnimation(View viewToAnimate, int position) {
        // Si el item ya fue animado, no volver a animarlo
        if (position > lastPosition) {
            Animation animation = AnimationUtils.loadAnimation(context, R.anim.message_slide_in);
            viewToAnimate.startAnimation(animation);
            lastPosition = position;
        }
    }
    
    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        holder.itemView.clearAnimation();
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

            // Actualizar icono de estado con animación suave
            if (mensaje.isLeido()) {
                ivEstado.setImageResource(R.drawable.ic_check_double);
                ivEstado.setColorFilter(android.graphics.Color.parseColor("#2196F3"));
                ivEstado.setContentDescription("Leído");
                // Animación sutil al cambiar a leído
                ivEstado.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150)
                    .withEndAction(() -> ivEstado.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
                    .start();
            } else if (mensaje.isEntregado()) {
                ivEstado.setImageResource(R.drawable.ic_check_double);
                ivEstado.setColorFilter(android.graphics.Color.parseColor("#E0E0E0"));
                ivEstado.setContentDescription("Entregado");
            } else {
                ivEstado.setImageResource(R.drawable.ic_check_single);
                ivEstado.setColorFilter(android.graphics.Color.parseColor("#E0E0E0"));
                ivEstado.setContentDescription("Enviado");
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
    
    /**
     * ViewHolder para mensajes con imagen.
     */
    static class ImageMessageHolder extends RecyclerView.ViewHolder {
        ImageView ivImagen;
        TextView tvHora;
        ImageView ivEstado;
        ProgressBar progressUpload;
        boolean isSent;
        
        ImageMessageHolder(View itemView, boolean isSent) {
            super(itemView);
            this.isSent = isSent;
            ivImagen = itemView.findViewById(R.id.iv_imagen);
            tvHora = itemView.findViewById(R.id.tv_hora);
            progressUpload = itemView.findViewById(R.id.progress_upload);
            
            if (isSent) {
                ivEstado = itemView.findViewById(R.id.iv_estado);
            }
        }
        
        void bind(Mensaje mensaje, String time) {
            tvHora.setText(time);
            
            // Cargar imagen con Glide
            if (mensaje.getImagen_url() != null && !mensaje.getImagen_url().isEmpty()) {
                progressUpload.setVisibility(View.GONE);
                String fixedUrl = MyApplication.getFixedUrl(mensaje.getImagen_url());
                
                Glide.with(itemView.getContext())
                    .load(fixedUrl)
                    .placeholder(R.drawable.ic_gallery)
                    .error(R.drawable.ic_gallery)
                    .centerCrop()
                    .into(ivImagen);
                
                // Click para ver en fullscreen
                ivImagen.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(fixedUrl), "image/*");
                    itemView.getContext().startActivity(intent);
                });
            } else {
                progressUpload.setVisibility(View.VISIBLE);
            }
            
            // Actualizar estado si es mensaje enviado
            if (isSent && ivEstado != null) {
                if (mensaje.isLeido()) {
                    ivEstado.setImageResource(R.drawable.ic_check_double);
                    ivEstado.setColorFilter(android.graphics.Color.parseColor("#2196F3"));
                    ivEstado.setContentDescription("Leído");
                } else if (mensaje.isEntregado()) {
                    ivEstado.setImageResource(R.drawable.ic_check_double);
                    ivEstado.setColorFilter(android.graphics.Color.parseColor("#E0E0E0"));
                    ivEstado.setContentDescription("Entregado");
                } else {
                    ivEstado.setImageResource(R.drawable.ic_check_single);
                    ivEstado.setColorFilter(android.graphics.Color.parseColor("#E0E0E0"));
                    ivEstado.setContentDescription("Enviado");
                }
            }
        }
    }
    
    /**
     * ViewHolder para mensajes de ubicación.
     */
    static class LocationMessageHolder extends RecyclerView.ViewHolder {
        // Vistas para mensaje enviado
        LinearLayout llSentLocation;
        ImageView ivMapaSent;
        TextView tvUbicacionSent, tvHoraSent;
        ImageView ivEstadoSent;

        // Vistas para mensaje recibido
        LinearLayout llReceivedLocation;
        ImageView ivMapaReceived;
        TextView tvUbicacionReceived, tvHoraReceived;
        
        boolean isSent;
        
        LocationMessageHolder(View itemView, boolean isSent) {
            super(itemView);
            this.isSent = isSent;

            llSentLocation = itemView.findViewById(R.id.ll_sent_location);
            ivMapaSent = itemView.findViewById(R.id.iv_mapa_sent);
            tvUbicacionSent = itemView.findViewById(R.id.tv_ubicacion_sent);
            tvHoraSent = itemView.findViewById(R.id.tv_hora_sent);
            ivEstadoSent = itemView.findViewById(R.id.iv_estado_sent);

            llReceivedLocation = itemView.findViewById(R.id.ll_received_location);
            ivMapaReceived = itemView.findViewById(R.id.iv_mapa_received);
            tvUbicacionReceived = itemView.findViewById(R.id.tv_ubicacion_received);
            tvHoraReceived = itemView.findViewById(R.id.tv_hora_received);
        }
        
        void bind(Mensaje mensaje, String time) {
            if (isSent) {
                llSentLocation.setVisibility(View.VISIBLE);
                llReceivedLocation.setVisibility(View.GONE);

                tvHoraSent.setText(time);
                tvUbicacionSent.setText("📍 Ubicación compartida");

                setupMapClick(mensaje, ivMapaSent, itemView);
                updateMessageStatus(mensaje, ivEstadoSent);

            } else {
                llSentLocation.setVisibility(View.GONE);
                llReceivedLocation.setVisibility(View.VISIBLE);

                tvHoraReceived.setText(time);
                tvUbicacionReceived.setText("📍 Ubicación compartida");
                
                setupMapClick(mensaje, ivMapaReceived, itemView);
            }
        }
        
        private void setupMapClick(Mensaje mensaje, ImageView ivMapa, View itemView) {
            if (mensaje.getLatitud() != null && mensaje.getLongitud() != null) {
                double lat = mensaje.getLatitud();
                double lng = mensaje.getLongitud();

                String staticMapUrl = "https://maps.googleapis.com/maps/api/staticmap?" +
                    "center=" + lat + "," + lng +
                    "&zoom=15" +
                    "&size=400x200" +
                    "&markers=color:red%7C" + lat + "," + lng +
                    "&key=" + BuildConfig.MAPS_API_KEY;

                Glide.with(itemView.getContext())
                    .load(staticMapUrl)
                    .placeholder(R.drawable.ic_location)
                    .error(R.drawable.ic_location)
                    .into(ivMapa);

                itemView.setOnClickListener(v -> {
                    String uri = "geo:" + lat + "," + lng + "?q=" + lat + "," + lng;
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                    intent.setPackage("com.google.android.apps.maps");

                    if (intent.resolveActivity(itemView.getContext().getPackageManager()) != null) {
                        itemView.getContext().startActivity(intent);
                    } else {
                        String browserUri = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lng;
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(browserUri));
                        itemView.getContext().startActivity(browserIntent);
                    }
                });
            }
        }

        private void updateMessageStatus(Mensaje mensaje, ImageView ivEstado) {
            if (ivEstado == null) return;
            if (mensaje.isLeido()) {
                ivEstado.setImageResource(R.drawable.ic_check_double);
                ivEstado.setColorFilter(android.graphics.Color.parseColor("#2196F3"));
                ivEstado.setContentDescription("Leído");
            } else if (mensaje.isEntregado()) {
                ivEstado.setImageResource(R.drawable.ic_check_double);
                ivEstado.setColorFilter(android.graphics.Color.parseColor("#E0E0E0"));
                ivEstado.setContentDescription("Entregado");
            } else {
                ivEstado.setImageResource(R.drawable.ic_check_single);
                ivEstado.setColorFilter(android.graphics.Color.parseColor("#E0E0E0"));
                ivEstado.setContentDescription("Enviado");
            }
        }
    }
}
