package com.mjc.mascotalink.utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Utilidades para generación y manejo de calendarios
 * Centraliza la lógica de generación de fechas para evitar duplicación
 */
public class CalendarioUtils {

    /**
     * Genera la lista de fechas para un mes específico, incluyendo días vacíos al inicio
     * para alinear correctamente con un calendario que comienza en Lunes (sistema europeo)
     *
     * @param mes Calendar del mes a generar
     * @return Lista de fechas del mes, con nulls para días vacíos al inicio
     */
    public static List<Date> generarFechasDelMes(Calendar mes) {
        List<Date> fechas = new ArrayList<>();
        Calendar cal = (Calendar) mes.clone();

        // Primer día del mes
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int primerDiaSemana = cal.get(Calendar.DAY_OF_WEEK); // 1=Dom, 2=Lun, 3=Mar, etc.

        // Calcular días vacíos al inicio (para alinear con día de la semana)
        // El header del calendario empieza en Lunes (sistema europeo), no en Domingo
        // Mapeo: Lunes=0, Martes=1, Miércoles=2, Jueves=3, Viernes=4, Sábado=5, Domingo=6
        int diasVacios = primerDiaSemana - 2; // Ajustar de sistema americano a europeo
        if (diasVacios < 0) diasVacios = 6; // Si es domingo (1), va a la posición 6

        for (int i = 0; i < diasVacios; i++) {
            fechas.add(null); // Días vacíos (del mes anterior)
        }

        // Agregar todos los días del mes
        int diasDelMes = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int dia = 1; dia <= diasDelMes; dia++) {
            cal.set(Calendar.DAY_OF_MONTH, dia);
            fechas.add(cal.getTime());
        }

        return fechas;
    }

    /**
     * Normaliza una fecha eliminando horas, minutos, segundos y milisegundos
     * Útil para comparaciones de fechas ignorando la hora
     *
     * @param fecha Fecha a normalizar
     * @return Fecha normalizada (solo año, mes, día)
     */
    public static Date normalizarFecha(Date fecha) {
        if (fecha == null) return null;

        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * Obtiene el nombre del día de la semana en minúsculas
     * Compatible con los nombres usados en Firebase
     *
     * @param fecha Fecha de la que obtener el día de la semana
     * @return Nombre del día en español: "lunes", "martes", etc.
     */
    public static String obtenerNombreDiaSemana(Date fecha) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);
        return obtenerNombreDiaSemana(cal);
    }

    /**
     * Obtiene el nombre del día de la semana en minúsculas
     * Compatible con los nombres usados en Firebase
     *
     * @param calendar Calendar del que obtener el día de la semana
     * @return Nombre del día en español: "lunes", "martes", etc.
     */
    public static String obtenerNombreDiaSemana(Calendar calendar) {
        int diaSemana = calendar.get(Calendar.DAY_OF_WEEK);
        switch (diaSemana) {
            case Calendar.MONDAY:
                return "lunes";
            case Calendar.TUESDAY:
                return "martes";
            case Calendar.WEDNESDAY:
                return "miercoles";
            case Calendar.THURSDAY:
                return "jueves";
            case Calendar.FRIDAY:
                return "viernes";
            case Calendar.SATURDAY:
                return "sabado";
            case Calendar.SUNDAY:
                return "domingo";
            default:
                return "lunes"; // Fallback
        }
    }
}
