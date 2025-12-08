package com.mjc.mascotalink.utils;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.mjc.mascotalink.modelo.Bloqueo;
import com.mjc.mascotalink.modelo.HorarioDefault;
import com.mjc.mascotalink.modelo.HorarioEspecial;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helper para validar y gestionar la disponibilidad de paseadores
 * Implementa la lógica de validación en orden de prioridad según especificación
 */
public class DisponibilidadHelper {

    private static final String TAG = "DisponibilidadHelper";
    private final FirebaseFirestore db;

    // Resultado de validación de disponibilidad
    public static class ResultadoDisponibilidad {
        public boolean disponible;
        public String razon;
        public String tipoValidacion; // "switch_global", "bloqueo", "horario_especial", "horario_default", etc.

        public ResultadoDisponibilidad(boolean disponible, String razon, String tipoValidacion) {
            this.disponible = disponible;
            this.razon = razon;
            this.tipoValidacion = tipoValidacion;
        }

        public static ResultadoDisponibilidad disponible(String razon, String tipo) {
            return new ResultadoDisponibilidad(true, razon, tipo);
        }

        public static ResultadoDisponibilidad noDisponible(String razon, String tipo) {
            return new ResultadoDisponibilidad(false, razon, tipo);
        }
    }

    public DisponibilidadHelper() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Función central: Valida si un paseador está disponible en una fecha/hora específica
     *
     * @param paseadorId ID del paseador
     * @param fecha Fecha del paseo
     * @param horaInicio Hora de inicio en formato "HH:mm"
     * @param horaFin Hora de fin en formato "HH:mm"
     * @return Task con ResultadoDisponibilidad
     */
    public Task<ResultadoDisponibilidad> esPaseadorDisponible(
            String paseadorId,
            Date fecha,
            String horaInicio,
            String horaFin) {

        Log.d(TAG, String.format("Validando disponibilidad: paseador=%s, fecha=%s, hora=%s-%s",
                paseadorId, fecha, horaInicio, horaFin));

        // PASO 1: Verificar switch global
        return db.collection("usuarios").document(paseadorId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }

                    DocumentSnapshot usuario = task.getResult();
                    if (!usuario.exists()) {
                        return Tasks.forResult(
                            ResultadoDisponibilidad.noDisponible("Paseador no encontrado", "error")
                        );
                    }

                    // Verificar campo acepta_solicitudes
                    Boolean aceptaSolicitudes = usuario.getBoolean("acepta_solicitudes");
                    if (aceptaSolicitudes != null && !aceptaSolicitudes) {
                        return Tasks.forResult(
                            ResultadoDisponibilidad.noDisponible(
                                "Paseador pausó servicios temporalmente",
                                "switch_global"
                            )
                        );
                    }

                    // Continuar con PASO 2: Verificar bloqueos
                    return verificarBloqueos(paseadorId, fecha, horaInicio, horaFin);
                });
    }

    /**
     * PASO 2 y 3: Verificar bloqueos (día completo y parciales)
     */
    private Task<ResultadoDisponibilidad> verificarBloqueos(
            String paseadorId,
            Date fecha,
            String horaInicio,
            String horaFin) {

        // Convertir fecha a inicio y fin del día para query
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Timestamp inicioDia = new Timestamp(cal.getTime());

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        Timestamp finDia = new Timestamp(cal.getTime());

        return db.collection("paseadores").document(paseadorId)
                .collection("disponibilidad").document("bloqueos")
                .collection("items")
                .whereGreaterThanOrEqualTo("fecha", inicioDia)
                .whereLessThanOrEqualTo("fecha", finDia)
                .whereEqualTo("activo", true)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }

                    QuerySnapshot bloqueos = task.getResult();

                    // Verificar si hay bloqueos
                    for (DocumentSnapshot doc : bloqueos.getDocuments()) {
                        Bloqueo bloqueo = doc.toObject(Bloqueo.class);
                        if (bloqueo == null) continue;

                        // PASO 2: Bloqueo de día completo
                        if (Bloqueo.TIPO_DIA_COMPLETO.equals(bloqueo.getTipo())) {
                            String razon = bloqueo.getRazon() != null && !bloqueo.getRazon().isEmpty()
                                    ? bloqueo.getRazon()
                                    : "Día bloqueado";
                            return Tasks.forResult(
                                ResultadoDisponibilidad.noDisponible(razon, "bloqueo_dia_completo")
                            );
                        }

                        // PASO 3: Bloqueos parciales (mañana/tarde)
                        int horaInicioInt = convertirHoraAInt(horaInicio);
                        if (bloqueo.afectaHora(horaInicioInt)) {
                            String razon = bloqueo.getDescripcion();
                            return Tasks.forResult(
                                ResultadoDisponibilidad.noDisponible(razon, "bloqueo_parcial")
                            );
                        }
                    }

                    // No hay bloqueos, continuar con PASO 4: Horarios especiales
                    return verificarHorarioEspecial(paseadorId, fecha, horaInicio, horaFin);
                });
    }

    /**
     * PASO 4: Verificar horarios especiales para la fecha
     */
    private Task<ResultadoDisponibilidad> verificarHorarioEspecial(
            String paseadorId,
            Date fecha,
            String horaInicio,
            String horaFin) {

        // Convertir fecha a rango del día
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Timestamp inicioDia = new Timestamp(cal.getTime());

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        Timestamp finDia = new Timestamp(cal.getTime());

        return db.collection("paseadores").document(paseadorId)
                .collection("disponibilidad").document("horarios_especiales")
                .collection("items")
                .whereGreaterThanOrEqualTo("fecha", inicioDia)
                .whereLessThanOrEqualTo("fecha", finDia)
                .whereEqualTo("activo", true)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }

                    QuerySnapshot horarios = task.getResult();

                    if (horarios != null && !horarios.isEmpty()) {
                        // Hay horario especial para este día
                        HorarioEspecial horarioEspecial = horarios.getDocuments().get(0)
                                .toObject(HorarioEspecial.class);

                        if (horarioEspecial != null) {
                            boolean enRango = estaEnRango(horaInicio, horaFin,
                                    horarioEspecial.getHora_inicio(),
                                    horarioEspecial.getHora_fin());

                            if (enRango) {
                                return Tasks.forResult(
                                    ResultadoDisponibilidad.disponible(
                                        "Horario especial configurado",
                                        "horario_especial"
                                    )
                                );
                            } else {
                                String razon = String.format(
                                    "Fuera de horario especial (%s - %s)",
                                    horarioEspecial.getHora_inicio(),
                                    horarioEspecial.getHora_fin()
                                );
                                return Tasks.forResult(
                                    ResultadoDisponibilidad.noDisponible(razon, "horario_especial")
                                );
                            }
                        }
                    }

                    // No hay horario especial, continuar con PASO 5: Horario por defecto
                    return verificarHorarioDefault(paseadorId, fecha, horaInicio, horaFin);
                });
    }

    /**
     * PASO 5: Verificar horario por defecto según día de la semana
     */
    private Task<ResultadoDisponibilidad> verificarHorarioDefault(
            String paseadorId,
            Date fecha,
            String horaInicio,
            String horaFin) {

        return db.collection("paseadores").document(paseadorId)
                .collection("disponibilidad").document("horario_default")
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        // Error al obtener horario, asumir disponible
                        return ResultadoDisponibilidad.disponible(
                            "Sin configuración de horario",
                            "sin_configuracion"
                        );
                    }

                    DocumentSnapshot doc = task.getResult();
                    if (!doc.exists()) {
                        // No hay horario configurado, asumir disponible por defecto
                        return ResultadoDisponibilidad.disponible(
                            "Sin configuración de horario",
                            "sin_configuracion"
                        );
                    }

                    // Obtener día de la semana
                    String diaSemana = obtenerDiaSemana(fecha);

                    // Intentar obtener el horario para este día
                    Map<String, Object> diaData = (Map<String, Object>) doc.get(diaSemana);

                    if (diaData == null) {
                        return ResultadoDisponibilidad.noDisponible(
                            "No trabaja los " + capitalizarPrimeraLetra(diaSemana),
                            "horario_default"
                        );
                    }

                    Boolean disponible = (Boolean) diaData.get("disponible");
                    if (disponible == null || !disponible) {
                        return ResultadoDisponibilidad.noDisponible(
                            "No trabaja los " + capitalizarPrimeraLetra(diaSemana),
                            "horario_default"
                        );
                    }

                    String horaInicioDefault = (String) diaData.get("hora_inicio");
                    String horaFinDefault = (String) diaData.get("hora_fin");

                    if (horaInicioDefault == null || horaFinDefault == null) {
                        return ResultadoDisponibilidad.noDisponible(
                            "Horario no configurado correctamente",
                            "horario_default"
                        );
                    }

                    boolean enRango = estaEnRango(horaInicio, horaFin, horaInicioDefault, horaFinDefault);

                    if (enRango) {
                        return ResultadoDisponibilidad.disponible(
                            "Horario de trabajo habitual",
                            "horario_default"
                        );
                    } else {
                        String razon = String.format(
                            "Fuera de horario de trabajo (%s - %s)",
                            horaInicioDefault,
                            horaFinDefault
                        );
                        return ResultadoDisponibilidad.noDisponible(razon, "horario_default");
                    }
                });
    }

    /**
     * Verifica si un rango de horas está dentro de otro rango
     * @param horaInicio Hora inicio solicitada
     * @param horaFin Hora fin solicitada
     * @param rangoInicio Hora inicio del rango permitido
     * @param rangoFin Hora fin del rango permitido
     * @return true si está completamente dentro del rango
     */
    private boolean estaEnRango(String horaInicio, String horaFin, String rangoInicio, String rangoFin) {
        try {
            return horaInicio.compareTo(rangoInicio) >= 0 && horaFin.compareTo(rangoFin) <= 0;
        } catch (Exception e) {
            Log.e(TAG, "Error comparando horas", e);
            return false;
        }
    }

    /**
     * Obtiene el nombre del día de la semana en español
     */
    private String obtenerDiaSemana(Date fecha) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);
        int dia = cal.get(Calendar.DAY_OF_WEEK);

        switch (dia) {
            case Calendar.MONDAY: return "lunes";
            case Calendar.TUESDAY: return "martes";
            case Calendar.WEDNESDAY: return "miercoles";
            case Calendar.THURSDAY: return "jueves";
            case Calendar.FRIDAY: return "viernes";
            case Calendar.SATURDAY: return "sabado";
            case Calendar.SUNDAY: return "domingo";
            default: return "lunes";
        }
    }

    /**
     * Convierte hora en formato "HH:mm" a entero (solo hora)
     */
    private int convertirHoraAInt(String hora) {
        try {
            String[] partes = hora.split(":");
            return Integer.parseInt(partes[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Capitaliza la primera letra de una cadena
     */
    private String capitalizarPrimeraLetra(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }

    /**
     * Formatea una fecha para mostrar
     */
    public static String formatearFecha(Date fecha) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "ES"));
        return sdf.format(fecha);
    }

    /**
     * Obtiene sugerencias de horarios disponibles para una fecha
     * @param paseadorId ID del paseador
     * @param fecha Fecha a consultar
     * @return Task con lista de horarios sugeridos en formato "HH:mm"
     */
    public Task<List<String>> obtenerHorariosDisponibles(String paseadorId, Date fecha) {
        // TODO: Implementar generación de horarios disponibles
        // Similar a HorarioSelectorAdapter pero validando disponibilidad
        return Tasks.forResult(null);
    }

    /**
     * Obtiene el estado general de un día completo (para mostrar en calendario)
     * No valida una hora específica, sino el estado general del día
     *
     * @param paseadorId ID del paseador
     * @param fecha Fecha a consultar
     * @return Task con resultado: "disponible", "bloqueado", "parcial", "especial", "no_trabaja"
     */
    public Task<String> obtenerEstadoDia(String paseadorId, Date fecha) {
        // PASO 1: Verificar switch global
        return db.collection("usuarios").document(paseadorId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                        return Tasks.forResult("no_disponible");
                    }

                    DocumentSnapshot usuario = task.getResult();
                    Boolean aceptaSolicitudes = usuario.getBoolean("acepta_solicitudes");
                    if (aceptaSolicitudes != null && !aceptaSolicitudes) {
                        return Tasks.forResult("no_disponible");
                    }

                    // PASO 2 y 3: Verificar bloqueos
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(fecha);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    Timestamp inicioDia = new Timestamp(cal.getTime());

                    cal.set(Calendar.HOUR_OF_DAY, 23);
                    cal.set(Calendar.MINUTE, 59);
                    cal.set(Calendar.SECOND, 59);
                    Timestamp finDia = new Timestamp(cal.getTime());

                    return db.collection("paseadores").document(paseadorId)
                            .collection("disponibilidad").document("bloqueos")
                            .collection("items")
                            .whereGreaterThanOrEqualTo("fecha", inicioDia)
                            .whereLessThanOrEqualTo("fecha", finDia)
                            .whereEqualTo("activo", true)
                            .get()
                            .continueWithTask(bloqueosTask -> {
                                if (bloqueosTask.isSuccessful() && bloqueosTask.getResult() != null) {
                                    for (DocumentSnapshot doc : bloqueosTask.getResult().getDocuments()) {
                                        Bloqueo bloqueo = doc.toObject(Bloqueo.class);
                                        if (bloqueo != null) {
                                            if (Bloqueo.TIPO_DIA_COMPLETO.equals(bloqueo.getTipo())) {
                                                return Tasks.forResult("bloqueado");
                                            } else {
                                                return Tasks.forResult("parcial");
                                            }
                                        }
                                    }
                                }

                                // PASO 4: Verificar horarios especiales
                                return db.collection("paseadores").document(paseadorId)
                                        .collection("disponibilidad").document("horarios_especiales")
                                        .collection("items")
                                        .whereGreaterThanOrEqualTo("fecha", inicioDia)
                                        .whereLessThanOrEqualTo("fecha", finDia)
                                        .whereEqualTo("activo", true)
                                        .get()
                                        .continueWithTask(especialesTask -> {
                                            if (especialesTask.isSuccessful() &&
                                                especialesTask.getResult() != null &&
                                                !especialesTask.getResult().isEmpty()) {
                                                return Tasks.forResult("especial");
                                            }

                                            // PASO 5: Verificar horario default
                                            return db.collection("paseadores").document(paseadorId)
                                                    .collection("disponibilidad").document("horario_default")
                                                    .get()
                                                    .continueWith(defaultTask -> {
                                                        if (!defaultTask.isSuccessful() ||
                                                            defaultTask.getResult() == null ||
                                                            !defaultTask.getResult().exists()) {
                                                            return "no_trabaja";
                                                        }

                                                        DocumentSnapshot doc = defaultTask.getResult();
                                                        String diaSemana = obtenerDiaSemana(fecha);
                                                        Map<String, Object> diaData = (Map<String, Object>) doc.get(diaSemana);

                                                        if (diaData == null) {
                                                            return "no_trabaja";
                                                        }

                                                        Boolean disponible = (Boolean) diaData.get("disponible");
                                                        if (disponible == null || !disponible) {
                                                            return "no_trabaja";
                                                        }

                                                        return "disponible";
                                                    });
                                        });
                            });
                });
    }
}
