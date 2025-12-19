package com.mjc.mascotalink.utils;

import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_ACEPTADO;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_CANCELADO;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_COMPLETADO;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_CONFIRMADO;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_EN_CURSO;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_LISTO_PARA_INICIAR;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_PENDIENTE_ACEPTACION;
import static com.mjc.mascotalink.utils.ReservaEstadoValidator.ESTADO_RECHAZADO;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReservaEstadoValidatorEdgeCasesTest {

    @Test
    public void canTransition_conNulos_retornaFalse() {
        assertFalse(ReservaEstadoValidator.canTransition(null, ESTADO_ACEPTADO));
        assertFalse(ReservaEstadoValidator.canTransition(ESTADO_PENDIENTE_ACEPTACION, null));
        assertFalse(ReservaEstadoValidator.canTransition(null, null));
    }

    @Test
    public void canTransition_conEstadoActualDesconocido_retornaFalse() {
        assertFalse(ReservaEstadoValidator.canTransition("DESCONOCIDO", ESTADO_ACEPTADO));
        assertFalse(ReservaEstadoValidator.canTransition("DESCONOCIDO", "OTRO"));
    }

    @Test
    public void canTransition_noPermiteSaltosDeFlujo() {
        assertFalse(ReservaEstadoValidator.canTransition(ESTADO_PENDIENTE_ACEPTACION, ESTADO_CONFIRMADO));
        assertFalse(ReservaEstadoValidator.canTransition(ESTADO_ACEPTADO, ESTADO_EN_CURSO));
        assertFalse(ReservaEstadoValidator.canTransition(ESTADO_CONFIRMADO, ESTADO_COMPLETADO));
        assertFalse(ReservaEstadoValidator.canTransition(ESTADO_LISTO_PARA_INICIAR, ESTADO_COMPLETADO));
    }

    @Test
    public void isTerminal_reconoceSoloEstadosTerminales() {
        assertTrue(ReservaEstadoValidator.isTerminal(ESTADO_RECHAZADO));
        assertTrue(ReservaEstadoValidator.isTerminal(ESTADO_CANCELADO));
        assertTrue(ReservaEstadoValidator.isTerminal(ESTADO_COMPLETADO));

        assertFalse(ReservaEstadoValidator.isTerminal(ESTADO_PENDIENTE_ACEPTACION));
        assertFalse(ReservaEstadoValidator.isTerminal(ESTADO_ACEPTADO));
        assertFalse(ReservaEstadoValidator.isTerminal(ESTADO_CONFIRMADO));
        assertFalse(ReservaEstadoValidator.isTerminal(ESTADO_LISTO_PARA_INICIAR));
        assertFalse(ReservaEstadoValidator.isTerminal(ESTADO_EN_CURSO));
    }
}

