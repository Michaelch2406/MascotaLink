package com.mjc.mascotalink.modelo;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;
import java.util.Map;

/**
 * Modelo para horario por defecto (recurrente) del paseador
 * Estructura: horario_default documento con map para cada día de la semana
 */
public class HorarioDefault {

    private Map<String, DiaHorario> lunes;
    private Map<String, DiaHorario> martes;
    private Map<String, DiaHorario> miercoles;
    private Map<String, DiaHorario> jueves;
    private Map<String, DiaHorario> viernes;
    private Map<String, DiaHorario> sabado;
    private Map<String, DiaHorario> domingo;

    @ServerTimestamp
    private Date fecha_creacion;

    @ServerTimestamp
    private Date ultima_actualizacion;

    // Constructor vacío requerido para Firestore
    public HorarioDefault() {}

    // Clase interna para representar horario de un día
    public static class DiaHorario {
        private boolean disponible;
        private String hora_inicio; // Formato "HH:mm"
        private String hora_fin; // Formato "HH:mm"

        public DiaHorario() {}

        public DiaHorario(boolean disponible, String hora_inicio, String hora_fin) {
            this.disponible = disponible;
            this.hora_inicio = hora_inicio;
            this.hora_fin = hora_fin;
        }

        // Getters y Setters
        public boolean isDisponible() {
            return disponible;
        }

        public void setDisponible(boolean disponible) {
            this.disponible = disponible;
        }

        public String getHora_inicio() {
            return hora_inicio;
        }

        public void setHora_inicio(String hora_inicio) {
            this.hora_inicio = hora_inicio;
        }

        public String getHora_fin() {
            return hora_fin;
        }

        public void setHora_fin(String hora_fin) {
            this.hora_fin = hora_fin;
        }
    }

    // Getters y Setters para cada día
    public Map<String, DiaHorario> getLunes() {
        return lunes;
    }

    public void setLunes(Map<String, DiaHorario> lunes) {
        this.lunes = lunes;
    }

    public Map<String, DiaHorario> getMartes() {
        return martes;
    }

    public void setMartes(Map<String, DiaHorario> martes) {
        this.martes = martes;
    }

    public Map<String, DiaHorario> getMiercoles() {
        return miercoles;
    }

    public void setMiercoles(Map<String, DiaHorario> miercoles) {
        this.miercoles = miercoles;
    }

    public Map<String, DiaHorario> getJueves() {
        return jueves;
    }

    public void setJueves(Map<String, DiaHorario> jueves) {
        this.jueves = jueves;
    }

    public Map<String, DiaHorario> getViernes() {
        return viernes;
    }

    public void setViernes(Map<String, DiaHorario> viernes) {
        this.viernes = viernes;
    }

    public Map<String, DiaHorario> getSabado() {
        return sabado;
    }

    public void setSabado(Map<String, DiaHorario> sabado) {
        this.sabado = sabado;
    }

    public Map<String, DiaHorario> getDomingo() {
        return domingo;
    }

    public void setDomingo(Map<String, DiaHorario> domingo) {
        this.domingo = domingo;
    }

    public Date getFecha_creacion() {
        return fecha_creacion;
    }

    public void setFecha_creacion(Date fecha_creacion) {
        this.fecha_creacion = fecha_creacion;
    }

    public Date getUltima_actualizacion() {
        return ultima_actualizacion;
    }

    public void setUltima_actualizacion(Date ultima_actualizacion) {
        this.ultima_actualizacion = ultima_actualizacion;
    }

    /**
     * Obtiene el horario de un día específico
     * @param nombreDia Nombre del día en español (lunes, martes, etc.)
     * @return DiaHorario o null si el día no existe
     */
    public DiaHorario getHorarioPorDia(String nombreDia) {
        if (nombreDia == null) return null;

        String diaLower = nombreDia.toLowerCase();
        switch (diaLower) {
            case "lunes":
                return lunes != null ? lunes.get("horario") : null;
            case "martes":
                return martes != null ? martes.get("horario") : null;
            case "miercoles":
            case "miércoles":
                return miercoles != null ? miercoles.get("horario") : null;
            case "jueves":
                return jueves != null ? jueves.get("horario") : null;
            case "viernes":
                return viernes != null ? viernes.get("horario") : null;
            case "sabado":
            case "sábado":
                return sabado != null ? sabado.get("horario") : null;
            case "domingo":
                return domingo != null ? domingo.get("horario") : null;
            default:
                return null;
        }
    }
}
