package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.imageview.ShapeableImageView;
import com.mjc.mascotalink.utils.PaseoDiffCallback;
import com.mjc.mascotalink.utils.ReservaEstadoValidator;
import com.mjc.mascotalink.Paseo;
import com.mjc.mascotalink.MyApplication;

import java.util.ArrayList;
import java.util.List;

public class PaseosAdapter extends RecyclerView.Adapter<PaseosAdapter.PaseoViewHolder> {

    private static final int VIEW_TYPE_INDIVIDUAL = 0;
    private static final int VIEW_TYPE_GRUPO = 1;

    private Context context;
    private List<PaseoItem> paseoItems;  // Cambiado de List<Paseo> a List<PaseoItem>
    private OnPaseoClickListener listener;
    private String userRole;

    public interface OnPaseoClickListener {
        void onPaseoClick(Paseo paseo);
        void onVerUbicacionClick(Paseo paseo);
        void onContactarClick(Paseo paseo);
        void onCalificarClick(Paseo paseo);
        void onVerMotivoClick(Paseo paseo);
        void onProcesarPagoClick(Paseo paseo);
    }

    public PaseosAdapter(Context context, List<Paseo> paseosList, OnPaseoClickListener listener, String userRole) {
        this.context = context;
        // Agrupar reservas antes de almacenarlas
        this.paseoItems = PaseoItem.agruparReservas(paseosList);
        this.listener = listener;
        this.userRole = userRole;
    }

    public void updateList(List<Paseo> newList) {
        // Agrupar la nueva lista
        List<PaseoItem> newItems = PaseoItem.agruparReservas(newList);
        // TODO: Implementar DiffUtil para PaseoItem si es necesario
        this.paseoItems.clear();
        this.paseoItems.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        PaseoItem item = paseoItems.get(position);
        return item.esGrupo() ? VIEW_TYPE_GRUPO : VIEW_TYPE_INDIVIDUAL;
    }

    @Override
    public int getItemCount() {
        return paseoItems.size();
    }

    @NonNull
    @Override
    public PaseoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_paseo, parent, false);
        return new PaseoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PaseoViewHolder holder, int position) {
        PaseoItem item = paseoItems.get(position);

        if (item.esGrupo()) {
            bindGrupoView(holder, item);
        } else {
            bindIndividualView(holder, item.getPaseoIndividual());
        }
    }

    /**
     * Vincula datos para una reserva agrupada (múltiples días)
     */
    private void bindGrupoView(PaseoViewHolder holder, PaseoItem item) {
        Paseo primerPaseo = item.getPrimerPaseo();
        if (primerPaseo == null) return;

        // 1. Fecha - Mostrar rango con progreso si aplica
        String fechaTexto = item.getRangoFechas() + " (" + item.getCantidadDias() + " días)";
        String progresoTexto = item.getTextoProgreso();
        if (progresoTexto != null) {
            fechaTexto += " • " + progresoTexto;
        }
        holder.tvFecha.setText(fechaTexto);
        holder.tvHoraInicioPaseo.setText(primerPaseo.getHoraFormateada() != null ? primerPaseo.getHoraFormateada() : "");

        // 2. Info Principal y Secundaria (igual que individual)
        String nombreMascota = primerPaseo.getMascotaNombre() != null ? primerPaseo.getMascotaNombre() : "Mascota";
        holder.tvNombrePrincipal.setText(nombreMascota);

        if (userRole != null && userRole.equalsIgnoreCase("PASEADOR")) {
            holder.tvNombreSecundario.setText("Dueño: " + (primerPaseo.getDuenoNombre() != null ? primerPaseo.getDuenoNombre() : "Desconocido"));
            cargarImagen(holder.ivFotoPerfil, primerPaseo.getMascotaFoto(), R.drawable.ic_pet_placeholder);
        } else {
            holder.tvNombreSecundario.setText("Paseador: " + (primerPaseo.getPaseadorNombre() != null ? primerPaseo.getPaseadorNombre() : "No asignado"));
            cargarImagen(holder.ivFotoPerfil, primerPaseo.getPaseadorFoto(), R.drawable.ic_person);
        }

        // 3. Detalles - Duración y Costo TOTAL del grupo
        int duracionMinutos = (int) primerPaseo.getDuracion_minutos();
        holder.tvDuracion.setText(formatearDuracion(duracionMinutos) + "/día");

        double costoTotal = item.getCostoTotal();
        holder.tvCosto.setText(String.format("$%.2f total", costoTotal));

        // 4. Estado (usar el estado efectivo del grupo)
        String estado = item.getEstadoEfectivo();
        holder.chipEstado.setText(estado != null ? estado : "DESCONOCIDO");
        establecerColorEstado(holder.chipEstado, estado);

        // 5. Opacidad para historial
        float opacidad = 1.0f;
        if (estado != null && (estado.equals("COMPLETADO") || estado.equals("CANCELADO"))) {
            opacidad = 0.9f;
        }
        holder.itemView.setAlpha(opacidad);

        // 6. Botones de Acción (usar el primer paseo)
        configurarBotonesAccion(holder, estado, primerPaseo);

        // Click en toda la tarjeta
        holder.itemView.setOnClickListener(v -> {
            boolean puedePagar = ReservaEstadoValidator.canPay(primerPaseo.getEstado()) &&
                    !ReservaEstadoValidator.isPagoCompletado(primerPaseo.getEstado_pago());
            if (userRole != null && !userRole.equalsIgnoreCase("PASEADOR") && puedePagar) {
                listener.onProcesarPagoClick(primerPaseo);
            } else {
                listener.onPaseoClick(primerPaseo);
            }
        });
    }

    /**
     * Vincula datos para una reserva individual
     */
    private void bindIndividualView(PaseoViewHolder holder, Paseo paseo) {
        if (paseo == null) return;

        // Obtener el item completo para verificar badges
        PaseoItem currentItem = null;
        for (PaseoItem item : paseoItems) {
            if (!item.esGrupo() && item.getPaseoIndividual() == paseo) {
                currentItem = item;
                break;
            }
        }

        // 1. Fecha y Hora con badge si es SEMANAL/MENSUAL
        String fechaTexto = paseo.getFechaFormateada() != null ? paseo.getFechaFormateada() : "";
        if (currentItem != null) {
            String badge = currentItem.getBadgeTipoReserva();
            if (badge != null) {
                fechaTexto = badge + " • " + fechaTexto;
            }
        }
        holder.tvFecha.setText(fechaTexto);
        holder.tvHoraInicioPaseo.setText(paseo.getHoraFormateada() != null ? paseo.getHoraFormateada() : "");

        // 2. Info Principal (Mascota) y Secundaria (Paseador/Dueño)
        String nombreMascota = paseo.getMascotaNombre() != null ? paseo.getMascotaNombre() : "Mascota";
        holder.tvNombrePrincipal.setText(nombreMascota);

        if (userRole != null && userRole.equalsIgnoreCase("PASEADOR")) {
            // Si soy Paseador, veo el nombre del Dueño
            holder.tvNombreSecundario.setText("Dueño: " + (paseo.getDuenoNombre() != null ? paseo.getDuenoNombre() : "Desconocido"));
            // Y veo la foto de la Mascota (o del dueño si prefieres, pero mascota es mejor visualmente)
            cargarImagen(holder.ivFotoPerfil, paseo.getMascotaFoto(), R.drawable.ic_pet_placeholder);
        } else {
            // Si soy Dueño, veo el nombre del Paseador
            holder.tvNombreSecundario.setText("Paseador: " + (paseo.getPaseadorNombre() != null ? paseo.getPaseadorNombre() : "No asignado"));
            // Y veo la foto del Paseador (importante para identificar quién viene)
            cargarImagen(holder.ivFotoPerfil, paseo.getPaseadorFoto(), R.drawable.ic_person);
        }

        // 3. Detalles
        int duracionMinutos = (int) paseo.getDuracion_minutos();
        holder.tvDuracion.setText(formatearDuracion(duracionMinutos));

        double costo = paseo.getCosto_total();
        holder.tvCosto.setText(String.format("$%.2f", costo));

        // 4. Estado (Chip)
        String estado = paseo.getEstado();
        holder.chipEstado.setText(estado != null ? estado : "DESCONOCIDO");
        establecerColorEstado(holder.chipEstado, estado);

        // 5. Opacidad para historial
        float opacidad = 1.0f;
        if (estado != null && (estado.equals("COMPLETADO") || estado.equals("CANCELADO"))) {
            opacidad = 0.9f; // Slightly faded but readable
        }
        holder.itemView.setAlpha(opacidad);

        // 6. Botones de Acción
        configurarBotonesAccion(holder, estado, paseo);

        // Click en toda la tarjeta
        holder.itemView.setOnClickListener(v -> {
            boolean puedePagar = ReservaEstadoValidator.canPay(paseo.getEstado()) &&
                    !ReservaEstadoValidator.isPagoCompletado(paseo.getEstado_pago());
            if (userRole != null && !userRole.equalsIgnoreCase("PASEADOR") && puedePagar) {
                listener.onProcesarPagoClick(paseo);
            } else {
                listener.onPaseoClick(paseo);
            }
        });
    }

    private void cargarImagen(ImageView imageView, String url, int placeholder) {
        if (url != null && !url.isEmpty()) {
            Glide.with(context)
                    .load(MyApplication.getFixedUrl(url))
                    .placeholder(placeholder)
                    .error(placeholder)
                    .circleCrop()
                    .into(imageView);
        } else {
            imageView.setImageResource(placeholder);
        }
    }

    private String formatearDuracion(int minutos) {
        int horas = minutos / 60;
        int mins = minutos % 60;
        if (horas > 0 && mins > 0) return horas + "h " + mins + "m";
        if (horas > 0) return horas + (horas == 1 ? " hora" : " horas");
        return mins + (mins == 1 ? " min" : " min");
    }

    private void establecerColorEstado(Chip chip, String estado) {
        int colorFondo;
        // Default colors
        if (estado == null) {
            colorFondo = 0xFF9CA3AF;
        } else {
            switch (estado) {
                case "LISTO_PARA_INICIAR":
                    colorFondo = 0xFFFF9800; // Naranja - Requiere atención
                    break;
                case "EN_CURSO":
                    colorFondo = 0xFF4CAF50; // Verde
                    break;
                case "CONFIRMADO":
                    colorFondo = 0xFF12A3ED; // Azul
                    break;
                case "COMPLETADO":
                    colorFondo = 0xFF81C784; // Verde claro
                    break;
                case "CANCELADO":
                    colorFondo = 0xFFF44336; // Rojo
                    break;
                default:
                    colorFondo = 0xFF9CA3AF; // Gris
                    break;
            }
        }
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(colorFondo));
        chip.setTextColor(0xFFFFFFFF); // Blanco
    }

    private void configurarBotonesAccion(PaseoViewHolder holder, String estado, final Paseo paseo) {
        holder.layoutBotones.setVisibility(View.VISIBLE);
        holder.btnAccion1.setVisibility(View.VISIBLE);
        holder.btnAccion2.setVisibility(View.VISIBLE);

        // Reset styles
        holder.btnAccion1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF12A3ED)); // Blue Primary
        holder.btnAccion1.setTextColor(0xFFFFFFFF); // White
        holder.btnAccion2.setVisibility(View.GONE); // Hide secondary by default

        if (estado != null) {
            if (estado.equals("LISTO_PARA_INICIAR")) {
                holder.btnAccion1.setText("Iniciar Paseo ▶");
                holder.btnAccion1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF9800)); // Naranja
                holder.btnAccion1.setOnClickListener(v -> listener.onVerUbicacionClick(paseo));

                holder.btnAccion2.setVisibility(View.VISIBLE);
                holder.btnAccion2.setText("Chat 💬");
                holder.btnAccion2.setOnClickListener(v -> listener.onContactarClick(paseo));
            }
            else if (estado.equals("EN_CURSO")) {
                holder.btnAccion1.setText("Ver Ubicación 📍");
                holder.btnAccion1.setOnClickListener(v -> listener.onVerUbicacionClick(paseo));

                holder.btnAccion2.setVisibility(View.VISIBLE);
                holder.btnAccion2.setText("Chat 💬");
                holder.btnAccion2.setOnClickListener(v -> listener.onContactarClick(paseo));
            }
            else if (estado.equals("ACEPTADO")) {
                if (userRole != null && !userRole.equalsIgnoreCase("PASEADOR")) {
                    boolean paymentPending = !ReservaEstadoValidator.isPagoCompletado(paseo.getEstado_pago());
                    if (paymentPending) {
                        holder.btnAccion1.setText("Pagar Ahora 💳");
                        holder.btnAccion1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50)); // Green
                        holder.btnAccion1.setOnClickListener(v -> listener.onProcesarPagoClick(paseo));
                    } else {
                        holder.btnAccion1.setText("Ver Detalles");
                        holder.btnAccion1.setOnClickListener(v -> listener.onPaseoClick(paseo));
                    }
                } else {
                    holder.btnAccion1.setText("Ver Detalles");
                    holder.btnAccion1.setOnClickListener(v -> listener.onPaseoClick(paseo));
                }
            }
            else if (estado.equals("COMPLETADO")) {
                holder.btnAccion1.setText("Calificar ⭐");
                holder.btnAccion1.setOnClickListener(v -> listener.onCalificarClick(paseo));
            }
            else if (estado.equals("CANCELADO")) {
                holder.btnAccion1.setText("Ver Motivo ℹ️");
                // Use outlined style or grey for secondary actions logic if needed, but keep simple for now
                holder.btnAccion1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF9CA3AF)); // Gray
                holder.btnAccion1.setOnClickListener(v -> listener.onVerMotivoClick(paseo));
            }
            else {
                holder.layoutBotones.setVisibility(View.GONE);
            }
        } else {
            holder.layoutBotones.setVisibility(View.GONE);
        }
    }

    static class PaseoViewHolder extends RecyclerView.ViewHolder {
        TextView tvFecha;
        TextView tvHoraInicioPaseo;
        TextView tvNombrePrincipal;
        TextView tvNombreSecundario;
        ShapeableImageView ivFotoPerfil;
        TextView tvDuracion;
        TextView tvCosto;
        Chip chipEstado;
        LinearLayout layoutBotones;
        MaterialButton btnAccion1;
        MaterialButton btnAccion2;

        public PaseoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFecha = itemView.findViewById(R.id.tv_fecha_paseo);
            tvHoraInicioPaseo = itemView.findViewById(R.id.tv_hora_inicio_paseo);
            tvNombrePrincipal = itemView.findViewById(R.id.tv_nombre_principal);
            tvNombreSecundario = itemView.findViewById(R.id.tv_nombre_secundario);
            ivFotoPerfil = itemView.findViewById(R.id.iv_foto_perfil_paseo);
            tvDuracion = itemView.findViewById(R.id.tv_duracion);
            tvCosto = itemView.findViewById(R.id.tv_costo);
            chipEstado = itemView.findViewById(R.id.chip_estado_paseo);
            layoutBotones = itemView.findViewById(R.id.layout_botones_accion);
            btnAccion1 = itemView.findViewById(R.id.btn_accion_1);
            btnAccion2 = itemView.findViewById(R.id.btn_accion_2);
        }
    }
}