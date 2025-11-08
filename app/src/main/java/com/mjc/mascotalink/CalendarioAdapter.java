package com.mjc.mascotalink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class CalendarioAdapter extends BaseAdapter {

    private Context context;
    private List<Date> dates;
    private Calendar currentMonth;
    private int selectedPosition = -1;
    private OnDateSelectedListener listener;

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

            if (isPast) {
                // Día pasado: deshabilitado (el selector de color y fondo se encargarán)
                tvDia.setEnabled(false);
                tvDia.setSelected(false);
            } else if (selectedPosition == position) {
                // Día seleccionado (el selector de color y fondo se encargarán)
                tvDia.setEnabled(true);
                tvDia.setSelected(true);
            } else {
                // Día disponible (el selector de color y fondo se encargarán)
                tvDia.setEnabled(true);
                tvDia.setSelected(false);
            }
        }

        // Click listener
        tvDia.setOnClickListener(v -> {
            if (date != null && tvDia.isEnabled()) {
                int previousPosition = selectedPosition;
                selectedPosition = position;
                notifyDataSetChanged();
                if (listener != null) {
                    listener.onDateSelected(date, position);
                }
            }
        });

        return convertView;
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
}
