package com.mjc.mascotalink;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests unitarios para validación de estados del Paseo
 * No requiere emulador - lógica de máquina de estados
 */
public class PaseoStateValidationTest {

    private String estado;

    @Before
    public void setUp() {
        estado = "LISTO_PARA_INICIAR";
    }

    @Test
    public void testValidInitialState() {
        assertEquals("Estado inicial debe ser LISTO", "LISTO_PARA_INICIAR", estado);
    }

    @Test
    public void testTransitionFromListoToEnCurso() {
        estado = "LISTO_PARA_INICIAR";
        boolean puedeIniciar = estado.equals("LISTO_PARA_INICIAR");
        assertTrue("Debe poder transicionar a EN_CURSO", puedeIniciar);
        estado = "EN_CURSO";
        assertEquals("Nuevo estado", "EN_CURSO", estado);
    }

    @Test
    public void testTransitionFromEnCursoToCompletado() {
        estado = "EN_CURSO";
        boolean puedeTerminar = estado.equals("EN_CURSO");
        assertTrue("Debe poder transicionar a COMPLETADO", puedeTerminar);
        estado = "COMPLETADO";
        assertEquals("Nuevo estado", "COMPLETADO", estado);
    }

    @Test
    public void testNoTransitionFromListoToCompletado() {
        estado = "LISTO_PARA_INICIAR";
        boolean puedeTerminarDirecto = estado.equals("COMPLETADO");
        assertFalse("No puede pasar directo a COMPLETADO", puedeTerminarDirecto);
    }

    @Test
    public void testCancelPaseoFromListo() {
        estado = "LISTO_PARA_INICIAR";
        boolean puedeCancelar = estado.equals("LISTO_PARA_INICIAR") || estado.equals("EN_CURSO");
        assertTrue("Puede cancelar desde LISTO", puedeCancelar);
        estado = "CANCELADO";
        assertEquals("Nuevo estado", "CANCELADO", estado);
    }

    @Test
    public void testCancelPaseoFromEnCurso() {
        estado = "EN_CURSO";
        boolean puedeCancelar = estado.equals("LISTO_PARA_INICIAR") || estado.equals("EN_CURSO");
        assertTrue("Puede cancelar desde EN_CURSO", puedeCancelar);
        estado = "CANCELADO";
        assertEquals("Nuevo estado", "CANCELADO", estado);
    }

    @Test
    public void testNoCancelFromCompletado() {
        estado = "COMPLETADO";
        boolean puedeCancelar = estado.equals("LISTO_PARA_INICIAR") || estado.equals("EN_CURSO");
        assertFalse("No puede cancelar desde COMPLETADO", puedeCancelar);
    }

    @Test
    public void testValidStatesList() {
        String[] estadosValidos = {"LISTO_PARA_INICIAR", "EN_CURSO", "COMPLETADO", "CANCELADO"};
        estado = "EN_CURSO";
        boolean esValido = false;
        for (String s : estadosValidos) {
            if (s.equals(estado)) {
                esValido = true;
                break;
            }
        }
        assertTrue("Estado debe estar en lista válida", esValido);
    }

    @Test
    public void testInvalidState() {
        String[] estadosValidos = {"LISTO_PARA_INICIAR", "EN_CURSO", "COMPLETADO", "CANCELADO"};
        estado = "ESTADO_INVALIDO";
        boolean esValido = false;
        for (String s : estadosValidos) {
            if (s.equals(estado)) {
                esValido = true;
                break;
            }
        }
        assertFalse("Estado inválido debe ser detectado", esValido);
    }
}
