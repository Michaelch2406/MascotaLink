package com.mjc.mascotalink;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HorarioSelectorAdapter extends RecyclerView.Adapter<HorarioSelectorAdapter.HorarioViewHolder> {

    private final Context context;
    private final List<Horario> horarioList;
    private int selectedPosition = -1;
    private OnHorarioSelectedListener listener;

    public interface OnHorarioSelectedListener {
        void onHorarioSelected(Horario horario, int position);
    }

    public HorarioSelectorAdapter(Context context, List<Horario> horarioList, OnHorarioSelectedListener listener) {
        this.context = context;
        this.horarioList = horarioList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HorarioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_horario_selector, parent, false);
        return new HorarioViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HorarioViewHolder holder, int position) {
        Horario horario = horarioList.get(position);
        holder.tvHorario.setText(horario.getHoraFormateada());

        // Aplicar estilos según estado
        if (!horario.isDisponible()) {
            // No disponible: el selector de color se encarga del texto.
            holder.tvHorario.setPaintFlags(holder.tvHorario.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.itemView.setEnabled(false);
            holder.itemView.setSelected(false);
            holder.tvCheckmark.setVisibility(View.GONE);
        } else if (selectedPosition == position) {
            // Seleccionado: los selectores de color de fondo y texto se encargan.
            holder.tvHorario.setPaintFlags(holder.tvHorario.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.itemView.setSelected(true);
            holder.itemView.setEnabled(true);
            holder.tvCheckmark.setVisibility(View.VISIBLE);
            holder.tvCheckmark.setTextColor(context.getResources().getColor(android.R.color.white)); // Checkmark blanco
        } else {
            // No seleccionado: los selectores de color de fondo y texto se encargan.
            holder.tvHorario.setPaintFlags(holder.tvHorario.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.itemView.setSelected(false);
            holder.itemView.setEnabled(true);
            holder.tvCheckmark.setVisibility(View.GONE);
            holder.tvCheckmark.setTextColor(context.getResources().getColor(R.color.blue_primary)); // Devolver checkmark a azul
        }

        // Click listener
        holder.itemView.setOnClickListener(v -> {
            if (horario.isDisponible()) {
                int previousPosition = selectedPosition;
                selectedPosition = holder.getAdapterPosition();
                notifyItemChanged(previousPosition);
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onHorarioSelected(horario, selectedPosition);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return horarioList.size();
    }

    public void setSelectedPosition(int position) {
        int previousPosition = this.selectedPosition;
        this.selectedPosition = position;

        if (previousPosition != -1 && previousPosition < horarioList.size()) {
            notifyItemChanged(previousPosition);
        }
        if (position != -1 && position < horarioList.size()) {
            notifyItemChanged(position);
        }
    }

    public void resetSelection() {
        int previousPosition = this.selectedPosition;
        this.selectedPosition = -1;

        if (previousPosition != -1 && previousPosition < horarioList.size()) {
            notifyItemChanged(previousPosition);
        }
    }

    static class HorarioViewHolder extends RecyclerView.ViewHolder {
        TextView tvHorario;
        TextView tvCheckmark;

        public HorarioViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHorario = itemView.findViewById(R.id.tv_horario);
            tvCheckmark = itemView.findViewById(R.id.tv_checkmark);
        }
    }

    // Clase modelo Horario
    public static class Horario {
        private String horaFormateada; // "10:00 AM"
        private int hora; // 10
        private int minutos; // 0
        private boolean disponible;
        private String disponibilidadEstado; // "DISPONIBLE", "LIMITADO", "NO_DISPONIBLE"
        private String razonNoDisponible; // Razón por la que no está disponible

        public Horario() {}

        public Horario(String horaFormateada, int hora, int minutos, boolean disponible) {
            this.horaFormateada = horaFormateada;
            this.hora = hora;
            this.minutos = minutos;
            this.disponible = disponible;
            this.disponibilidadEstado = disponible ? "DISPONIBLE" : "NO_DISPONIBLE";
        }

        public String getHoraFormateada() { return horaFormateada; }
        public void setHoraFormateada(String horaFormateada) { this.horaFormateada = horaFormateada; }

        public int getHora() { return hora; }
        public void setHora(int hora) { this.hora = hora; }

        public int getMinutos() { return minutos; }
        public void setMinutos(int minutos) { this.minutos = minutos; }

        public boolean isDisponible() { return disponible; }
        public void setDisponible(boolean disponible) { this.disponible = disponible; }

        public String getDisponibilidadEstado() { return disponibilidadEstado; }
        public void setDisponibilidadEstado(String estado) { this.disponibilidadEstado = estado; }

        public String getRazonNoDisponible() { return razonNoDisponible; }
        public void setRazonNoDisponible(String razonNoDisponible) { this.razonNoDisponible = razonNoDisponible; }
    }
}
