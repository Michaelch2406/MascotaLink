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


import com.bumptech.glide.Glide;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class PaseosAdapter extends RecyclerView.Adapter<PaseosAdapter.PaseoViewHolder> {

    private Context context;
    private List<PaseosActivity.Paseo> paseosList;
    private OnPaseoClickListener listener;

    public interface OnPaseoClickListener {
        void onPaseoClick(PaseosActivity.Paseo paseo);
        void onVerUbicacionClick(PaseosActivity.Paseo paseo);
        void onContactarClick(PaseosActivity.Paseo paseo);
        void onCalificarClick(PaseosActivity.Paseo paseo);
        void onVerMotivoClick(PaseosActivity.Paseo paseo);
    }

    public PaseosAdapter(Context context, List<PaseosActivity.Paseo> paseosList, OnPaseoClickListener listener) {
        this.context = context;
        this.paseosList = paseosList;
        this.listener = listener;
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

        // Fecha
        holder.tvFecha.setText(paseo.getFechaFormateada());

        // Nombre paseador
        holder.tvPaseadorNombre.setText(paseo.getPaseadorNombre() != null ? paseo.getPaseadorNombre() : "Cargando...");

        // Mascota y hora
        String mascotaHora = "Mascota: " +
                (paseo.getMascotaNombre() != null ? paseo.getMascotaNombre() : "Cargando...") +
                ", " + paseo.getHoraFormateada();
        holder.tvMascotaHora.setText(mascotaHora);

        // Imagen del paseador
        if (paseo.getPaseadorFoto() != null && !paseo.getPaseadorFoto().isEmpty()) {
            Glide.with(context)
                    .load(paseo.getPaseadorFoto())
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(holder.ivPaseadorFoto);
        } else {
            holder.ivPaseadorFoto.setImageResource(R.drawable.ic_person);
        }

        // 1. MOSTRAR DURACIÓN (convertir minutos a horas/minutos)
        int duracionMinutos = paseo.getDuracionMinutos();
        String duracionTexto = formatearDuracion(duracionMinutos);
        holder.tvDuracion.setText("Duración: " + duracionTexto);

        // 2. MOSTRAR COSTO (formatear como moneda)
        double costo = paseo.getCostoTotal();
        holder.tvCosto.setText(String.format("$%.2f", costo));

        // 3. MOSTRAR TIPO DE RESERVA
        String tipoReserva = paseo.getTipoReserva();
        if (tipoReserva != null) {
            String tipoTexto = tipoReserva.equals("PUNTUAL") ? "UN DÍA" :
                    tipoReserva.equals("SEMANAL") ? "SEMANA" :
                            tipoReserva.equals("MENSUAL") ? "MES" : tipoReserva;
            holder.tvTipoReserva.setText(tipoTexto);
        }

        // 4. MOSTRAR ESTADO Y ESTABLECER COLOR
        String estado = paseo.getEstado();
        holder.chipEstado.setText(estado != null ? estado : "DESCONOCIDO");
        establecerColorEstado(holder.chipEstado, estado);

        // 5. APLICAR OPACIDAD SEGÚN ESTADO
        float opacidad = 1.0f;
        if (estado != null) {
            if (estado.equals("COMPLETADO")) opacidad = 0.8f;
            else if (estado.equals("CANCELADO")) opacidad = 0.6f;
        }
        holder.itemView.setAlpha(opacidad);

        // 6. CONFIGURAR BOTONES SEGÚN ESTADO
        configurarBotonesAccion(holder, estado);


        // Click en card
        holder.itemView.setOnClickListener(v -> listener.onPaseoClick(paseo));

    }

    // Método para formatear duración (minutos → horas y minutos)
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

    // Método para establecer color del chip según estado
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

    // Método para configurar botones según estado
    private void configurarBotonesAccion(PaseoViewHolder holder, String estado) {
        holder.layoutBotonesAccion.setVisibility(View.VISIBLE);

        if (estado != null) {
            if (estado.equals("EN_CURSO")) {
                // Mostrar: Ver Ubicación + Contactar
                holder.btnAccion1.setText("Ver Ubicación 📍");
                holder.btnAccion2.setText("Contactar 💬");

                holder.btnAccion1.setOnClickListener(v -> {
                    Toast.makeText(v.getContext(), "Abriendo ubicación en tiempo real...", Toast.LENGTH_SHORT).show();
                    // TODO: Implementar lógica para abrir mapa/ubicación
                });

                holder.btnAccion2.setOnClickListener(v -> {
                    Toast.makeText(v.getContext(), "Abriendo chat con paseador...", Toast.LENGTH_SHORT).show();
                    // TODO: Implementar lógica para abrir mensajería
                });
            }
            else if (estado.equals("COMPLETADO")) {
                // Mostrar: Calificar + Ver Feedback
                holder.btnAccion1.setText("Calificar ⭐");
                holder.btnAccion2.setText("Ver Feedback");

                holder.btnAccion1.setOnClickListener(v -> {
                    Toast.makeText(v.getContext(), "Abriendo calificación...", Toast.LENGTH_SHORT).show();
                    // TODO: Implementar diálogo de calificación
                });

                holder.btnAccion2.setOnClickListener(v -> {
                    Toast.makeText(v.getContext(), "Abriendo feedback...", Toast.LENGTH_SHORT).show();
                    // TODO: Implementar visualización de feedback
                });
            }
            else if (estado.equals("CANCELADO")) {
                // Mostrar solo: Ver Motivo
                holder.btnAccion1.setText("Ver Motivo ℹ️");
                holder.btnAccion2.setVisibility(View.GONE);

                holder.btnAccion1.setOnClickListener(v -> {
                    Toast.makeText(v.getContext(), "Motivo de cancelación...", Toast.LENGTH_SHORT).show();
                    // TODO: Implementar diálogo con motivo
                });
            }
            else if (estado.equals("CONFIRMADO")) {
                // Ocultar botones para paseos confirmados (aún no iniciados)
                holder.layoutBotonesAccion.setVisibility(View.GONE);
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
