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

import java.util.ArrayList;
import java.util.List;

public class PaseosAdapter extends RecyclerView.Adapter<PaseosAdapter.PaseoViewHolder> {

    private Context context;
    private List<Paseo> paseosList;
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
        this.paseosList = new ArrayList<>(paseosList);
        this.listener = listener;
        this.userRole = userRole;
    }
    
    public void updateList(List<Paseo> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new PaseoDiffCallback(this.paseosList, newList));
        this.paseosList.clear();
        this.paseosList.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public PaseoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_paseo, parent, false);
        return new PaseoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PaseoViewHolder holder, int position) {
        Paseo paseo = paseosList.get(position);
        if (paseo == null) return;

        // 1. Fecha y Hora
        holder.tvFecha.setText(paseo.getFechaFormateada() != null ? paseo.getFechaFormateada() : "");
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
                    .load(url)
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
            if (estado.equals("EN_CURSO")) {
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

    @Override
    public int getItemCount() {
        return paseosList.size();
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