package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil; // Added import

import com.bumptech.glide.Glide;
import com.mjc.mascotalink.utils.PaseoDiffCallback; // Added import

import java.util.List;
import java.util.ArrayList; // Added import

import com.mjc.mascotalink.utils.ReservaEstadoValidator;

import de.hdodenhof.circleimageview.CircleImageView;

public class PaseosAdapter extends RecyclerView.Adapter<PaseosAdapter.PaseoViewHolder> {

    private Context context;
    private List<PaseosActivity.Paseo> paseosList;
    private OnPaseoClickListener listener;
    private String userRole;

    public interface OnPaseoClickListener {
        void onPaseoClick(PaseosActivity.Paseo paseo);
        void onVerUbicacionClick(PaseosActivity.Paseo paseo);
        void onContactarClick(PaseosActivity.Paseo paseo);
        void onCalificarClick(PaseosActivity.Paseo paseo);
        void onVerMotivoClick(PaseosActivity.Paseo paseo);
        void onProcesarPagoClick(PaseosActivity.Paseo paseo);
    }

    public PaseosAdapter(Context context, List<PaseosActivity.Paseo> paseosList, OnPaseoClickListener listener, String userRole) {
        this.context = context;
        this.paseosList = new ArrayList<>(paseosList); // Create a copy
        this.listener = listener;
        this.userRole = userRole;
    }
    
    public void updateList(List<PaseosActivity.Paseo> newList) {
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
        PaseosActivity.Paseo paseo = paseosList.get(position);
        if (paseo == null) return;

        holder.tvFecha.setText(paseo.getFechaFormateada() != null ? paseo.getFechaFormateada() : "");

        // Lógica basada en el rol
        if (userRole != null && userRole.equalsIgnoreCase("PASEADOR")) {
            // Vista para el Paseador
            holder.tvPaseadorNombre.setText(paseo.getDuenoNombre() != null ? paseo.getDuenoNombre() : "Dueño no asignado");
            if (paseo.getMascotaFoto() != null && !paseo.getMascotaFoto().isEmpty()) {
                Glide.with(context)
                        .load(paseo.getMascotaFoto())
                        .placeholder(R.drawable.ic_pet_placeholder)
                        .error(R.drawable.ic_pet_placeholder)
                        .circleCrop()
                        .into(holder.ivPaseadorFoto);
            } else {
                holder.ivPaseadorFoto.setImageResource(R.drawable.ic_pet_placeholder);
            }
        } else {
            // Vista para el Dueño (comportamiento original)
            holder.tvPaseadorNombre.setText(paseo.getPaseadorNombre() != null ? paseo.getPaseadorNombre() : "Paseador no asignado");
            if (paseo.getPaseadorFoto() != null && !paseo.getPaseadorFoto().isEmpty()) {
                Glide.with(context)
                        .load(paseo.getPaseadorFoto())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(holder.ivPaseadorFoto);
            } else {
                holder.ivPaseadorFoto.setImageResource(R.drawable.ic_person);
            }
        }


        String mascotaHora = "Mascota: " +
                (paseo.getMascotaNombre() != null ? paseo.getMascotaNombre() : "Cargando...") +
                ", " + (paseo.getHoraFormateada() != null ? paseo.getHoraFormateada() : "");
        holder.tvMascotaHora.setText(mascotaHora);

        int duracionMinutos = (int) paseo.getDuracion_minutos(); // Corrected getter
        String duracionTexto = formatearDuracion(duracionMinutos);
        holder.tvDuracion.setText("Duración: " + duracionTexto);

        double costo = paseo.getCosto_total(); // Corrected getter
        holder.tvCosto.setText(String.format("$%.2f", costo));

        String tipoReserva = paseo.getTipo_reserva(); // Corrected getter
        if (tipoReserva != null) {
            String tipoTexto = tipoReserva.equals("PUNTUAL") ? "UN DÍA" :
                    tipoReserva.equals("SEMANAL") ? "SEMANA" :
                            tipoReserva.equals("MENSUAL") ? "MES" : tipoReserva;
            holder.tvTipoReserva.setText(tipoTexto);
        } else {
             holder.tvTipoReserva.setText("RESERVA");
        }

        String estado = paseo.getEstado();
        holder.chipEstado.setText(estado != null ? estado : "DESCONOCIDO");
        establecerColorEstado(holder.chipEstado, estado);

        float opacidad = 1.0f;
        if (estado != null) {
            if (estado.equals("COMPLETADO")) opacidad = 0.8f;
            else if (estado.equals("CANCELADO")) opacidad = 0.6f;
        }
        holder.itemView.setAlpha(opacidad);

        configurarBotonesAccion(holder, estado, paseo);


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

    private String formatearDuracion(int minutos) {
        int horas = minutos / 60;
        int mins = minutos % 60;

        if (horas > 0 && mins > 0) {
            return horas + "h " + mins + "m";
        } else if (horas > 0) {
            return horas + (horas == 1 ? " hora" : " horas");
        } else {
            return mins + (mins == 1 ? " minuto" : " minutos");
        }
    }

    private void establecerColorEstado(com.google.android.material.chip.Chip chip, String estado) {
        int colorFondo;
        int colorTexto;

        if (estado != null) {
            switch (estado) {
                case "EN_CURSO":
                    colorFondo = 0xFF4CAF50; // Verde
                    colorTexto = 0xFFFFFFFF; // Blanco
                    break;
                case "CONFIRMADO":
                    colorFondo = 0xFF12A3ED; // Azul
                    colorTexto = 0xFFFFFFFF; // Blanco
                    break;
                case "COMPLETADO":
                    colorFondo = 0xFF81C784; // Verde claro
                    colorTexto = 0xFFFFFFFF; // Blanco
                    break;
                case "CANCELADO":
                    colorFondo = 0xFFF44336; // Rojo
                    colorTexto = 0xFFFFFFFF; // Blanco
                    break;
                default:
                    colorFondo = 0xFF9CA3AF; // Gris
                    colorTexto = 0xFFFFFFFF; // Blanco
                    break;
            }
        } else {
            colorFondo = 0xFF9CA3AF;
            colorTexto = 0xFFFFFFFF;
        }

        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(colorFondo));
        chip.setTextColor(colorTexto);
    }

    private void configurarBotonesAccion(PaseoViewHolder holder, String estado, final PaseosActivity.Paseo paseo) {
        holder.layoutBotonesAccion.setVisibility(View.VISIBLE);
        holder.btnAccion1.setVisibility(View.VISIBLE);
        holder.btnAccion2.setVisibility(View.VISIBLE);


        if (estado != null) {
            if (estado.equals("EN_CURSO")) {
                holder.btnAccion1.setText("Ver Ubicación 📍");
                holder.btnAccion2.setText("PASEADOR".equalsIgnoreCase(userRole) ? "Contactar Dueño 💬" : "Contactar Paseador 💬");
                holder.btnAccion1.setOnClickListener(v -> listener.onVerUbicacionClick(paseo));
                holder.btnAccion2.setOnClickListener(v -> listener.onContactarClick(paseo));
            }
            else if (estado.equals("COMPLETADO")) {
                if("PASEADOR".equalsIgnoreCase(userRole)) {
                    holder.layoutBotonesAccion.setVisibility(View.GONE);
                } else {
                    holder.btnAccion1.setText("Calificar ⭐");
                    holder.btnAccion2.setText("Ver Feedback");
                    holder.btnAccion1.setOnClickListener(v -> listener.onCalificarClick(paseo));
                    holder.btnAccion2.setOnClickListener(v -> Toast.makeText(context, "Próximamente: Ver feedback", Toast.LENGTH_SHORT).show());
                }
            }
            else if (estado.equals("CANCELADO")) {
                holder.btnAccion1.setText("Ver Motivo ℹ️");
                holder.btnAccion2.setVisibility(View.GONE);
                holder.btnAccion1.setOnClickListener(v -> listener.onVerMotivoClick(paseo));
            }
            else {
                holder.layoutBotonesAccion.setVisibility(View.GONE);
            }
        } else {
            holder.layoutBotonesAccion.setVisibility(View.GONE);
        }
    }


    @Override
    public int getItemCount() {
        return paseosList.size();
    }

    static class PaseoViewHolder extends RecyclerView.ViewHolder {
        TextView tvFecha;
        TextView tvPaseadorNombre;
        TextView tvMascotaHora;
        CircleImageView ivPaseadorFoto;
        LinearLayout llBotones;
        TextView btnVerUbicacion;
        TextView btnContactar;
        TextView btnCalificar;
        TextView btnVerMotivo;
        TextView tvDuracion;
        TextView tvCosto;
        TextView tvTipoReserva;
        com.google.android.material.chip.Chip chipEstado;
        LinearLayout layoutBotonesAccion;
        Button btnAccion1;
        Button btnAccion2;

        public PaseoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFecha = itemView.findViewById(R.id.tv_fecha);
            tvPaseadorNombre = itemView.findViewById(R.id.tv_paseador_nombre);
            tvMascotaHora = itemView.findViewById(R.id.tv_mascota_hora);
            ivPaseadorFoto = itemView.findViewById(R.id.iv_paseador_foto);
            llBotones = itemView.findViewById(R.id.ll_botones);
            btnVerUbicacion = itemView.findViewById(R.id.btn_ver_ubicacion);
            btnContactar = itemView.findViewById(R.id.btn_contactar);
            btnCalificar = itemView.findViewById(R.id.btn_calificar);
            btnVerMotivo = itemView.findViewById(R.id.btn_ver_motivo);
            tvDuracion = itemView.findViewById(R.id.tvDuracion);
            tvCosto = itemView.findViewById(R.id.tvCosto);
            tvTipoReserva = itemView.findViewById(R.id.tvTipoReserva);
            chipEstado = itemView.findViewById(R.id.chipEstado);
            layoutBotonesAccion = itemView.findViewById(R.id.layoutBotonesAccion);
            btnAccion1 = itemView.findViewById(R.id.btnAccion1);
            btnAccion2 = itemView.findViewById(R.id.btnAccion2);
        }
    }
}
