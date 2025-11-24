package com.mjc.mascotalink.utils;

import androidx.recyclerview.widget.DiffUtil;
import com.mjc.mascotalink.SolicitudesActivity.Solicitud;
import java.util.List;
import java.util.Objects;

public class SolicitudDiffCallback extends DiffUtil.Callback {

    private final List<Solicitud> oldList;
    private final List<Solicitud> newList;

    public SolicitudDiffCallback(List<Solicitud> oldList, List<Solicitud> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        // Compara por ID único (reservaId)
        return Objects.equals(oldList.get(oldItemPosition).getReservaId(), newList.get(newItemPosition).getReservaId());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        Solicitud oldItem = oldList.get(oldItemPosition);
        Solicitud newItem = newList.get(newItemPosition);

        // Compara los campos visibles relevantes
        return Objects.equals(oldItem.getDuenoNombre(), newItem.getDuenoNombre()) &&
               Objects.equals(oldItem.getMascotaRaza(), newItem.getMascotaRaza()) &&
               Objects.equals(oldItem.getHoraInicio(), newItem.getHoraInicio()) &&
               Objects.equals(oldItem.getFechaCreacion(), newItem.getFechaCreacion());
    }
}
