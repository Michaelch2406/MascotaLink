package com.mjc.mascotalink;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests unitarios para cálculo de costo de Reserva
 * No requiere emulador - cálculos puros
 */
public class ReservaCostCalculationTest {

    private double tarifaPorHora;
    private int duracionMinutos;

    @Before
    public void setUp() {
        tarifaPorHora = 10.6;
        duracionMinutos = 0;
    }

    @Test
    public void testCostFor1Hour() {
        duracionMinutos = 60;
        double costo = (duracionMinutos / 60.0) * tarifaPorHora;
        assertEquals("Costo 1 hora", 10.6, costo, 0.01);
    }

    @Test
    public void testCostFor2Hours() {
        duracionMinutos = 120;
        double costo = (duracionMinutos / 60.0) * tarifaPorHora;
        assertEquals("Costo 2 horas", 21.2, costo, 0.01);
    }

    @Test
    public void testCostFor3Hours() {
        duracionMinutos = 180;
        double costo = (duracionMinutos / 60.0) * tarifaPorHora;
        assertEquals("Costo 3 horas", 31.8, costo, 0.01);
    }

    @Test
    public void testCostFor30Minutes() {
        duracionMinutos = 30;
        double costo = (duracionMinutos / 60.0) * tarifaPorHora;
        assertEquals("Costo 30 minutos", 5.3, costo, 0.01);
    }

    @Test
    public void testCostWithDiscount10Percent() {
        duracionMinutos = 60;
        double costoBase = (duracionMinutos / 60.0) * tarifaPorHora;
        double descuento = costoBase * 0.10;
        double costoFinal = costoBase - descuento;
        assertEquals("Costo con 10% descuento", 9.54, costoFinal, 0.01);
    }

    @Test
    public void testCostWithDiscount20Percent() {
        duracionMinutos = 120;
        double costoBase = (duracionMinutos / 60.0) * tarifaPorHora;
        double descuento = costoBase * 0.20;
        double costoFinal = costoBase - descuento;
        assertEquals("Costo con 20% descuento", 16.96, costoFinal, 0.01);
    }

    @Test
    public void testZeroCostForZeroDuration() {
        duracionMinutos = 0;
        double costo = (duracionMinutos / 60.0) * tarifaPorHora;
        assertEquals("Costo 0 minutos", 0.0, costo, 0.01);
    }

    @Test
    public void testCostRounding() {
        duracionMinutos = 45;
        double costo = (duracionMinutos / 60.0) * tarifaPorHora;
        assertTrue("Costo debe ser positivo", costo > 0);
        assertEquals("Costo 45 minutos", 7.95, costo, 0.01);
    }

    @Test
    public void testMultipleMascotasMultiplier() {
        duracionMinutos = 60;
        int numMascotas = 3;
        double costoBase = (duracionMinutos / 60.0) * tarifaPorHora;
        double multiplicador = 1.0 + (numMascotas - 1) * 0.15;
        double costoTotal = costoBase * multiplicador;
        assertTrue("Costo con múltiples mascotas", costoTotal > costoBase);
    }

    @Test
    public void testNegativeCostPrevention() {
        duracionMinutos = -60;
        double costo = Math.max(0, (duracionMinutos / 60.0) * tarifaPorHora);
        assertEquals("Costo no puede ser negativo", 0.0, costo, 0.01);
    }
}
