package com.mjc.mascotalink;

import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_ACEPTADO;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_CANCELADO;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_CONFIRMADO;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_PENDIENTE_ACEPTACION;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_PAGO_CONFIRMADO;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_PAGO_PENDIENTE;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_RECHAZADO;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mjc.mascotalink.utils.ReservaEstadoValidator;

import org.junit.Test;

public class ReservaEstadoValidatorTest {

    @Test
    public void pendienteAceptacionPermiteAceptar() {
        assertTrue(ReservaEstadoValidator.canTransition(ESTADO_PENDIENTE_ACEPTACION, ESTADO_ACEPTADO));
    }

    @Test
    public void soloAceptadoPermitePago() {
        assertTrue(ReservaEstadoValidator.canPay(ESTADO_ACEPTADO));
        assertFalse(ReservaEstadoValidator.canPay(ESTADO_PENDIENTE_ACEPTACION));
    }

    @Test
    public void terminalesNoPermitenTransiciones() {
        assertFalse(ReservaEstadoValidator.canTransition(ESTADO_RECHAZADO, ESTADO_ACEPTADO));
        assertFalse(ReservaEstadoValidator.canTransition(ESTADO_CANCELADO, ESTADO_CONFIRMADO));
    }

    @Test
    public void pagoConfirmadoEsReconocido() {
        assertTrue(ReservaEstadoValidator.isPagoCompletado(ESTADO_PAGO_CONFIRMADO));
        assertFalse(ReservaEstadoValidator.isPagoCompletado(ESTADO_PAGO_PENDIENTE));
    }
}
