package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalendarioAdapter extends BaseAdapter {

    private Context context;
    private List<Date> dates;
    private Calendar currentMonth;
    private int selectedPosition = -1;
    private OnDateSelectedListener listener;

    // Para DisponibilidadActivity
    private Set<Date> diasDisponibles = new HashSet<>();
    private Set<Date> diasBloqueados = new HashSet<>();
    private Set<Date> diasParciales = new HashSet<>();
    private Set<Date> fechasSeleccionadas = new HashSet<>();
    private boolean seleccionMultiple = false;
    private boolean esVistaPaseador = false; // true = DisponibilidadActivity, false = ReservaActivity

    public interface OnDateSelectedListener {
        void onDateSelected(Date date, int position);
    }

    public CalendarioAdapter(Context context, List<Date> dates, Calendar currentMonth, OnDateSelectedListener listener) {
        this.context = context;
        this.dates = dates;
        this.currentMonth = currentMonth;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return dates.size();
    }

    @Override
    public Object getItem(int position) {
        return dates.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView tvDia;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_dia_calendario, parent, false);
        }

        tvDia = (TextView) convertView;
        Date date = dates.get(position);

        if (date == null) {
            // Día vacío (de otro mes)
            tvDia.setText("");
            tvDia.setEnabled(false);
            tvDia.setSelected(false);
            tvDia.setTextColor(context.getResources().getColor(android.R.color.transparent));
        } else {
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            tvDia.setText(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)));

            // Verificar si es día pasado
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            boolean isPast = date.before(today.getTime());
            Date normalizedDate = normalizarFecha(date);
            boolean isBloqueado = diasBloqueados.contains(normalizedDate);
            boolean isParcial = diasParciales.contains(normalizedDate);
            boolean isDisponible = diasDisponibles.contains(normalizedDate);
            boolean isSeleccionado = seleccionMultiple ? fechasSeleccionadas.contains(normalizedDate) : (selectedPosition == position);

            if (isPast) {
                // Día pasado: gris opaco, deshabilitado
                tvDia.setTextColor(context.getResources().getColor(R.color.gray_disabled));
                tvDia.setBackgroundResource(android.R.color.transparent);
                tvDia.setEnabled(false);
            } else if (isSeleccionado) {
                // Día seleccionado: fondo azul, texto blanco (tiene prioridad visual)
                tvDia.setBackgroundResource(R.drawable.bg_calendario_seleccionado);
                tvDia.setTextColor(context.getResources().getColor(android.R.color.white));
                tvDia.setEnabled(true);
            } else if (isBloqueado) {
                // Día bloqueado: fondo rojo claro
                tvDia.setBackgroundColor(context.getResources().getColor(R.color.calendario_bloqueado));
                tvDia.setTextColor(context.getResources().getColor(android.R.color.black));
                tvDia.setEnabled(true);
            } else if (isParcial) {
                // Día parcial: fondo amarillo
                tvDia.setBackgroundColor(context.getResources().getColor(R.color.calendario_parcial));
                tvDia.setTextColor(context.getResources().getColor(android.R.color.black));
                tvDia.setEnabled(true);
            } else if (isDisponible || diasDisponibles.isEmpty() || seleccionMultiple) {
                // Día disponible: fondo verde claro, texto negro
                // Si diasDisponibles está vacío, mostrar todos como disponibles (backward compatibility)
                // Si es selección múltiple (DialogBloquearDias), permitir seleccionar cualquier día futuro
                tvDia.setBackgroundColor(context.getResources().getColor(R.color.calendario_disponible));
                tvDia.setTextColor(context.getResources().getColor(android.R.color.black));
                tvDia.setEnabled(true);
            } else {
                // Día NO trabajado (no está en horario estándar): gris claro
                // SOLO en ReservaActivity (selección simple)
                tvDia.setTextColor(context.getResources().getColor(R.color.gray_disabled));
                tvDia.setBackgroundResource(android.R.color.transparent);
                tvDia.setEnabled(false);
            }
        }

        // Click listener
        tvDia.setOnClickListener(v -> {
            if (date != null && tvDia.isEnabled()) {
                Date normalizedDate = normalizarFecha(date);
                boolean esBloqueado = diasBloqueados.contains(normalizedDate);
                boolean esParcial = diasParciales.contains(normalizedDate);

                // Si el día está bloqueado completamente Y es vista de CLIENTE (no paseador)
                if (esBloqueado && !seleccionMultiple && !esVistaPaseador) {
                    android.widget.Toast.makeText(context,
                        "Día no disponible - El paseador bloqueó este día completo",
                        android.widget.Toast.LENGTH_SHORT).show();
                    return; // No ejecutar el callback
                }

                // Si el día está parcialmente bloqueado Y es vista de CLIENTE
                if (esParcial && !seleccionMultiple && !esVistaPaseador) {
                    android.widget.Toast.makeText(context,
                        "⚠️ Disponibilidad limitada - Solo algunas horas disponibles",
                        android.widget.Toast.LENGTH_SHORT).show();
                }

                if (seleccionMultiple) {
                    // Modo selección múltiple
                    if (fechasSeleccionadas.contains(normalizedDate)) {
                        fechasSeleccionadas.remove(normalizedDate);
                    } else {
                        fechasSeleccionadas.add(normalizedDate);
                    }
                    notifyDataSetChanged();
                } else {
                    // Modo selección simple
                    selectedPosition = position;
                    notifyDataSetChanged();
                }

                if (listener != null) {
                    listener.onDateSelected(date, position);
                }
            }
        });

        return convertView;
    }

    private Date normalizarFecha(Date fecha) {
        if (fecha == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public void updateDates(List<Date> newDates, Calendar newMonth) {
        this.dates = newDates;
        this.currentMonth = newMonth;
        this.selectedPosition = -1;
        notifyDataSetChanged();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    // Métodos para DisponibilidadActivity
    public void setDiasDisponibles(Set<Date> diasDisponibles) {
        this.diasDisponibles = diasDisponibles != null ? diasDisponibles : new HashSet<>();
        notifyDataSetChanged();
    }

    public void setDiasBloqueados(Set<Date> diasBloqueados) {
        this.diasBloqueados = diasBloqueados != null ? diasBloqueados : new HashSet<>();
        notifyDataSetChanged();
    }

    public void setDiasParciales(Set<Date> diasParciales) {
        this.diasParciales = diasParciales != null ? diasParciales : new HashSet<>();
        notifyDataSetChanged();
    }

    public Date getFecha(int position) {
        if (position >= 0 && position < dates.size()) {
            return dates.get(position);
        }
        return null;
    }

    // Métodos para DialogBloquearDiasFragment
    public void setSeleccionMultiple(boolean seleccionMultiple) {
        this.seleccionMultiple = seleccionMultiple;
        if (!seleccionMultiple) {
            fechasSeleccionadas.clear();
        }
        notifyDataSetChanged();
    }

    public void setFechasSeleccionadas(Set<Date> fechasSeleccionadas) {
        this.fechasSeleccionadas = fechasSeleccionadas != null ? fechasSeleccionadas : new HashSet<>();
        notifyDataSetChanged();
    }

    public Set<Date> getFechasSeleccionadas() {
        return new HashSet<>(fechasSeleccionadas);
    }

    public void setListener(OnDateSelectedListener listener) {
        this.listener = listener;
    }

    public void setEsVistaPaseador(boolean esVistaPaseador) {
        this.esVistaPaseador = esVistaPaseador;
    }
}
