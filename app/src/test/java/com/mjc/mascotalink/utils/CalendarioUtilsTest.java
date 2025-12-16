package com.mjc.mascotalink.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class CalendarioUtilsTest {

    @Test
    public void generarFechasDelMes_cuandoMesEmpiezaLunes_noAgregaNulos() {
        Calendar mayo2023 = calendarOf(2023, Calendar.MAY, 15);

        List<Date> fechas = CalendarioUtils.generarFechasDelMes(mayo2023);

        assertEquals(0, countLeadingNulls(fechas));
        assertEquals(31, countNonNulls(fechas));

        assertNotNull(fechas.get(0));
        Calendar firstDay = Calendar.getInstance();
        firstDay.setTime(fechas.get(0));
        assertEquals(1, firstDay.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.MAY, firstDay.get(Calendar.MONTH));
        assertEquals(2023, firstDay.get(Calendar.YEAR));
    }

    @Test
    public void generarFechasDelMes_cuandoMesEmpiezaDomingo_agregaSeisNulos() {
        Calendar enero2023 = calendarOf(2023, Calendar.JANUARY, 10);

        List<Date> fechas = CalendarioUtils.generarFechasDelMes(enero2023);

        assertEquals(6, countLeadingNulls(fechas));
        assertEquals(31, countNonNulls(fechas));

        for (int i = 0; i < 6; i++) {
            assertNull(fechas.get(i));
        }
        assertNotNull(fechas.get(6));
    }

    @Test
    public void generarFechasDelMes_febreroBisiesto_sizeEsDiasMasNulosIniciales() {
        Calendar febrero2024 = calendarOf(2024, Calendar.FEBRUARY, 10);

        List<Date> fechas = CalendarioUtils.generarFechasDelMes(febrero2024);

        assertEquals(3, countLeadingNulls(fechas)); // 01/02/2024 fue jueves
        assertEquals(29, countNonNulls(fechas));
        assertEquals(32, fechas.size());

        Date ultimoDia = fechas.get(fechas.size() - 1);
        Calendar lastDay = Calendar.getInstance();
        lastDay.setTime(ultimoDia);
        assertEquals(29, lastDay.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.FEBRUARY, lastDay.get(Calendar.MONTH));
        assertEquals(2024, lastDay.get(Calendar.YEAR));
    }

    @Test
    public void normalizarFecha_eliminaHoraMinutosSegundosYMillis() {
        Calendar cal = calendarOf(2025, Calendar.JULY, 10);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 123);

        Date normalizada = CalendarioUtils.normalizarFecha(cal.getTime());

        Calendar normalized = Calendar.getInstance();
        normalized.setTime(normalizada);
        assertEquals(2025, normalized.get(Calendar.YEAR));
        assertEquals(Calendar.JULY, normalized.get(Calendar.MONTH));
        assertEquals(10, normalized.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, normalized.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, normalized.get(Calendar.MINUTE));
        assertEquals(0, normalized.get(Calendar.SECOND));
        assertEquals(0, normalized.get(Calendar.MILLISECOND));
    }

    @Test
    public void obtenerNombreDiaSemana_mapeaConstantesCalendar() {
        Calendar cal = Calendar.getInstance();

        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        assertEquals("lunes", CalendarioUtils.obtenerNombreDiaSemana(cal));

        cal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY);
        assertEquals("miercoles", CalendarioUtils.obtenerNombreDiaSemana(cal));

        cal.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
        assertEquals("sabado", CalendarioUtils.obtenerNombreDiaSemana(cal));

        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        assertEquals("domingo", CalendarioUtils.obtenerNombreDiaSemana(cal));
    }

    private static Calendar calendarOf(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private static int countLeadingNulls(List<Date> dates) {
        int count = 0;
        for (Date d : dates) {
            if (d == null) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private static int countNonNulls(List<Date> dates) {
        int count = 0;
        for (Date d : dates) {
            if (d != null) {
                count++;
            }
        }
        return count;
    }
}

