package com.mjc.mascotalink.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mjc.mascotalink.Paseo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class PaseoDiffCallbackTest {

    @Test
    public void sizes_reflejanListasEntrada() {
        List<Paseo> oldList = Arrays.asList(paseo("r1", "ACEPTADO", 10.0));
        List<Paseo> newList = Arrays.asList(
                paseo("r1", "ACEPTADO", 10.0),
                paseo("r2", "CONFIRMADO", 20.0)
        );

        PaseoDiffCallback cb = new PaseoDiffCallback(oldList, newList);

        assertEquals(1, cb.getOldListSize());
        assertEquals(2, cb.getNewListSize());
    }

    @Test
    public void areItemsTheSame_comparaPorReservaId() {
        Paseo oldPaseo = paseo("r1", "ACEPTADO", 10.0);
        Paseo sameId = paseo("r1", "CONFIRMADO", 10.0);
        Paseo otherId = paseo("r2", "ACEPTADO", 10.0);

        PaseoDiffCallback cbSame = new PaseoDiffCallback(
                Arrays.asList(oldPaseo),
                Arrays.asList(sameId)
        );
        assertTrue(cbSame.areItemsTheSame(0, 0));

        PaseoDiffCallback cbOther = new PaseoDiffCallback(
                Arrays.asList(oldPaseo),
                Arrays.asList(otherId)
        );
        assertFalse(cbOther.areItemsTheSame(0, 0));
    }

    @Test
    public void areContentsTheSame_detectaCambiosEnCamposVisibles() {
        Date fecha = new Date(1700000000000L);

        Paseo base = paseo("r1", "ACEPTADO", 10.0);
        base.setFecha(fecha);
        base.setPaseadorNombre("Ana");
        base.setMascotaNombre("Luna");

        Paseo same = paseo("r1", "ACEPTADO", 10.0);
        same.setFecha(fecha);
        same.setPaseadorNombre("Ana");
        same.setMascotaNombre("Luna");

        Paseo estadoCambiado = paseo("r1", "CONFIRMADO", 10.0);
        estadoCambiado.setFecha(fecha);
        estadoCambiado.setPaseadorNombre("Ana");
        estadoCambiado.setMascotaNombre("Luna");

        Paseo costoCambiado = paseo("r1", "ACEPTADO", 11.0);
        costoCambiado.setFecha(fecha);
        costoCambiado.setPaseadorNombre("Ana");
        costoCambiado.setMascotaNombre("Luna");

        PaseoDiffCallback cbSame = new PaseoDiffCallback(Arrays.asList(base), Arrays.asList(same));
        assertTrue(cbSame.areContentsTheSame(0, 0));

        PaseoDiffCallback cbEstado = new PaseoDiffCallback(Arrays.asList(base), Arrays.asList(estadoCambiado));
        assertFalse(cbEstado.areContentsTheSame(0, 0));

        PaseoDiffCallback cbCosto = new PaseoDiffCallback(Arrays.asList(base), Arrays.asList(costoCambiado));
        assertFalse(cbCosto.areContentsTheSame(0, 0));
    }

    private static Paseo paseo(String reservaId, String estado, double costo) {
        Paseo p = new Paseo();
        p.setReservaId(reservaId);
        p.setEstado(estado);
        p.setCosto_total(costo);
        return p;
    }
}

