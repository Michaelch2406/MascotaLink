package com.mjc.mascotalink.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.CalendarContract;
import android.util.Log;

import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.TimeZone;

public class GoogleCalendarHelper {

    private static final String TAG = "GoogleCalendarHelper";

    public interface CalendarCallback {
        void onSuccess();
        void onError(String message);
    }

    public static void addWalkToCalendar(
            Activity activity,
            String nombreMascota,
            String nombrePaseador,
            String direccion,
            Timestamp horaInicio,
            Timestamp horaFin,
            String reservaId,
            int duracionMinutos
    ) {
        try {
            long startMillis = horaInicio.toDate().getTime();
            long endMillis = horaFin != null ? horaFin.toDate().getTime() : startMillis + (duracionMinutos * 60 * 1000L);

            Intent intent = new Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, "Paseo de " + nombreMascota)
                    .putExtra(CalendarContract.Events.DESCRIPTION, 
                            "Paseo con " + nombrePaseador + "\n" +
                            "Duración: " + duracionMinutos + " minutos")
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, direccion)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                    .putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID())
                    .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);

            addReminders(intent);

            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
                Log.d(TAG, "Evento de calendario abierto para reserva: " + reservaId);
            } else {
                Log.e(TAG, "No hay app de calendario disponible");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al agregar evento al calendario", e);
        }
    }

    public static void addWalkToCalendarForWalker(
            Activity activity,
            String nombreMascota,
            String nombreDueno,
            String direccion,
            Timestamp horaInicio,
            Timestamp horaFin,
            String reservaId,
            int duracionMinutos,
            String notas
    ) {
        try {
            long startMillis = horaInicio.toDate().getTime();
            long endMillis = horaFin != null ? horaFin.toDate().getTime() : startMillis + (duracionMinutos * 60 * 1000L);

            StringBuilder description = new StringBuilder();
            description.append("Cliente: ").append(nombreDueno).append("\n");
            description.append("Mascota: ").append(nombreMascota).append("\n");
            description.append("Duración: ").append(duracionMinutos).append(" minutos");
            if (notas != null && !notas.isEmpty()) {
                description.append("\n\nNotas: ").append(notas);
            }

            Intent intent = new Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, "Paseo - " + nombreMascota + " (" + nombreDueno + ")")
                    .putExtra(CalendarContract.Events.DESCRIPTION, description.toString())
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, direccion)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                    .putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID())
                    .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);

            addReminders(intent);

            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
                Log.d(TAG, "Evento de calendario (paseador) abierto para reserva: " + reservaId);
            } else {
                Log.e(TAG, "No hay app de calendario disponible");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al agregar evento al calendario (paseador)", e);
        }
    }

    private static void addReminders(Intent intent) {
        intent.putExtra(CalendarContract.Reminders.MINUTES, 60);
        intent.putExtra(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
    }

    public static void addRecurringWalksToCalendar(
            Activity activity,
            String nombreMascota,
            String nombrePaseador,
            String direccion,
            Timestamp primeraFecha,
            int duracionMinutos,
            int cantidadDias,
            String grupoReservaId
    ) {
        try {
            long startMillis = primeraFecha.toDate().getTime();
            long endMillis = startMillis + (duracionMinutos * 60 * 1000L);

            Intent intent = new Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, "Paseo de " + nombreMascota + " (Serie)")
                    .putExtra(CalendarContract.Events.DESCRIPTION,
                            "Paseo con " + nombrePaseador + "\n" +
                            "Serie de " + cantidadDias + " días\n" +
                            "Grupo ID: " + grupoReservaId + "\n" +
                            "Duración: " + duracionMinutos + " minutos")
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, direccion)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                    .putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID())
                    .putExtra(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=" + cantidadDias)
                    .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);

            addReminders(intent);

            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
                Log.d(TAG, "Evento recurrente de calendario abierto para grupo: " + grupoReservaId);
            } else {
                Log.e(TAG, "No hay app de calendario disponible");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al agregar evento recurrente al calendario", e);
        }
    }

    public static boolean isCalendarAvailable(Context context) {
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI);
        return intent.resolveActivity(context.getPackageManager()) != null;
    }
}
