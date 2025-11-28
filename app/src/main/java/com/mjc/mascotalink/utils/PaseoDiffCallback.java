package com.mjc.mascotalink.utils;

import androidx.recyclerview.widget.DiffUtil;
import com.mjc.mascotalink.Paseo;
import java.util.List;
import java.util.Objects;

public class PaseoDiffCallback extends DiffUtil.Callback {

    private final List<Paseo> oldList;
    private final List<Paseo> newList;

    public PaseoDiffCallback(List<Paseo> oldList, List<Paseo> newList) {
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
        Paseo oldItem = oldList.get(oldItemPosition);
        Paseo newItem = newList.get(newItemPosition);

        // Compara los campos visibles relevantes para decidir si redibujar
        return Objects.equals(oldItem.getEstado(), newItem.getEstado()) &&
               Objects.equals(oldItem.getFecha(), newItem.getFecha()) &&
               Objects.equals(oldItem.getPaseadorNombre(), newItem.getPaseadorNombre()) &&
               Objects.equals(oldItem.getMascotaNombre(), newItem.getMascotaNombre()) &&
               oldItem.getCosto_total() == newItem.getCosto_total();
    }
}
