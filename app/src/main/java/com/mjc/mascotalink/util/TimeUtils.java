package com.mjc.mascotalink.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utilidad para formatear timestamps como texto relativo amigable.
 * Ejemplos: "Ahora", "Hace 5 min", "Hace 2 horas", "Ayer", "27 Nov"
 */
public class TimeUtils {

    /**
     * Convierte un timestamp a texto relativo.
     * 
     * @param timestamp La fecha/hora a formatear
     * @return String con el tiempo relativo formateado
     */
    public static String getRelativeTimeString(Date timestamp) {
        if (timestamp == null) {
            return "";
        }

        long now = System.currentTimeMillis();
        long time = timestamp.getTime();
        long diff = now - time;

        // Si es en el futuro, mostrar hora
        if (diff < 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(timestamp);
        }

        long seconds = TimeUnit.MILLISECONDS.toSeconds(diff);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        long days = TimeUnit.MILLISECONDS.toDays(diff);

        // Menos de un minuto: "Ahora"
        if (seconds < 60) {
            return "Ahora";
        }

        // Menos de 60 minutos: "Hace X min"
        if (minutes < 60) {
            return "Hace " + minutes + " min";
        }

        // Menos de 24 horas: "Hace X horas"
        if (hours < 24) {
            return "Hace " + hours + (hours == 1 ? " hora" : " horas");
        }

        // Verificar si fue ayer
        if (isYesterday(timestamp)) {
            return "Ayer";
        }

        // Menos de 7 días: mostrar día de la semana
        if (days < 7) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE", new Locale("es", "ES"));
            return capitalize(sdf.format(timestamp));
        }

        // Más antiguo: mostrar fecha corta "DD MMM"
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", new Locale("es", "ES"));
        return sdf.format(timestamp);
    }

    /**
     * Verifica si una fecha corresponde a ayer.
     * 
     * @param date La fecha a verificar
     * @return true si la fecha es de ayer
     */
    private static boolean isYesterday(Date date) {
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        
        Calendar dateCalendar = Calendar.getInstance();
        dateCalendar.setTime(date);
        
        return yesterday.get(Calendar.YEAR) == dateCalendar.get(Calendar.YEAR) &&
               yesterday.get(Calendar.DAY_OF_YEAR) == dateCalendar.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Capitaliza la primera letra de un string.
     * 
     * @param str El string a capitalizar
     * @return String con la primera letra en mayúscula
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Obtiene el tiempo transcurrido en formato detallado.
     * Útil para mostrar "última vez activo hace X tiempo"
     * 
     * @param timestamp La fecha/hora a formatear
     * @return String con el tiempo transcurrido
     */
    public static String getDetailedTimeAgo(Date timestamp) {
        if (timestamp == null) {
            return "Hace mucho tiempo";
        }

        long now = System.currentTimeMillis();
        long time = timestamp.getTime();
        long diff = now - time;

        if (diff < 0) {
            return "Justo ahora";
        }

        long seconds = TimeUnit.MILLISECONDS.toSeconds(diff);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        long days = TimeUnit.MILLISECONDS.toDays(diff);

        if (seconds < 60) {
            return "Justo ahora";
        } else if (minutes < 60) {
            return "Hace " + minutes + (minutes == 1 ? " minuto" : " minutos");
        } else if (hours < 24) {
            return "Hace " + hours + (hours == 1 ? " hora" : " horas");
        } else if (days < 7) {
            return "Hace " + days + (days == 1 ? " día" : " días");
        } else if (days < 30) {
            long weeks = days / 7;
            return "Hace " + weeks + (weeks == 1 ? " semana" : " semanas");
        } else if (days < 365) {
            long months = days / 30;
            return "Hace " + months + (months == 1 ? " mes" : " meses");
        } else {
            long years = days / 365;
            return "Hace " + years + (years == 1 ? " año" : " años");
        }
    }
    
    /**
     * Obtiene el texto para un separador de fecha en el chat.
     * Ejemplos: "Hoy", "Ayer", "27 de Noviembre"
     * 
     * @param date La fecha del separador
     * @return String con el texto formateado para el separador
     */
    public static String getDateSeparatorText(Date date) {
        if (date == null) {
            return "";
        }
        
        Calendar dateCalendar = Calendar.getInstance();
        dateCalendar.setTime(date);
        
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        
        // Verificar si es hoy
        if (isSameDay(dateCalendar, today)) {
            return "Hoy";
        }
        
        // Verificar si es ayer
        if (isSameDay(dateCalendar, yesterday)) {
            return "Ayer";
        }
        
        // Formato completo: "27 de Noviembre"
        SimpleDateFormat sdf = new SimpleDateFormat("dd 'de' MMMM", new Locale("es", "ES"));
        return sdf.format(date);
    }
    
    /**
     * Verifica si dos calendarios representan el mismo día.
     */
    private static boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    /**
     * Verifica si dos fechas son del mismo día (sin considerar hora).
     */
    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        
        return isSameDay(cal1, cal2);
    }
}
