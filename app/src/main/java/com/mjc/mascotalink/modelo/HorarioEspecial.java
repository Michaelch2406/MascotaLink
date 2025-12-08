package com.mjc.mascotalink.modelo;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

/**
 * Modelo para horarios especiales de días específicos
 * Se guarda en: paseadores/{uid}/disponibilidad/horarios_especiales/{horarioId}
 * Tiene prioridad sobre el horario por defecto
 */
public class HorarioEspecial {

    @DocumentId
    private String id;

    private Timestamp fecha; // Fecha específica para este horario
    private String hora_inicio; // Formato "HH:mm"
    private String hora_fin; // Formato "HH:mm"
    private String nota; // "Solo tarde este día", etc.
    private String descripcion; // Persisted for display (puede ser null, se calcula en getDescripcion())
    private int duracionMinutos; // Persisted for performance (puede ser 0, se calcula en getDuracionMinutos())

    @ServerTimestamp
    private Date fecha_creacion;

    private boolean activo; // Permite desactivar sin eliminar

    // Constructor vacío requerido para Firestore
    public HorarioEspecial() {
        this.activo = true;
    }

    // Constructor con parámetros principales
    public HorarioEspecial(Timestamp fecha, String hora_inicio, String hora_fin) {
        this.fecha = fecha;
        this.hora_inicio = hora_inicio;
        this.hora_fin = hora_fin;
        this.activo = true;
    }

    // Constructor completo
    public HorarioEspecial(Timestamp fecha, String hora_inicio, String hora_fin, String nota) {
        this.fecha = fecha;
        this.hora_inicio = hora_inicio;
        this.hora_fin = hora_fin;
        this.nota = nota;
        this.activo = true;
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Timestamp getFecha() {
        return fecha;
    }

    public void setFecha(Timestamp fecha) {
        this.fecha = fecha;
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

    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota;
    }

    public String getDescripcionPersistida() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public int getDuracionMinutosPersistida() {
        return duracionMinutos;
    }

    public void setDuracionMinutos(int duracionMinutos) {
        this.duracionMinutos = duracionMinutos;
    }

    public Date getFecha_creacion() {
        return fecha_creacion;
    }

    public void setFecha_creacion(Date fecha_creacion) {
        this.fecha_creacion = fecha_creacion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /**
     * Verifica si una hora específica está dentro del rango de este horario especial
     * @param hora Hora en formato "HH:mm"
     * @return true si la hora está dentro del rango
     */
    public boolean contieneHora(String hora) {
        if (!activo || hora_inicio == null || hora_fin == null || hora == null) {
            return false;
        }

        try {
            // Comparación simple de strings en formato HH:mm
            return hora.compareTo(hora_inicio) >= 0 && hora.compareTo(hora_fin) <= 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtiene una descripción legible del horario especial
     * Primero intenta obtener la persisted, si no existe la calcula dinámicamente
     */
    public String getDescripcion() {
        // Si hay descripción persistida, usarla
        if (descripcion != null && !descripcion.isEmpty()) {
            return descripcion;
        }

        // Si hay nota, usarla
        if (nota != null && !nota.isEmpty()) {
            return nota;
        }

        // Calcular dinámicamente
        return String.format("Horario especial: %s - %s", hora_inicio, hora_fin);
    }

    /**
     * Calcula la duración del horario en minutos
     * Primero intenta obtener la persistida, si no existe la calcula dinámicamente
     */
    public int getDuracionMinutos() {
        // Si hay duración persistida y es > 0, usarla
        if (duracionMinutos > 0) {
            return duracionMinutos;
        }

        // Calcular dinámicamente
        if (hora_inicio == null || hora_fin == null) return 0;

        try {
            String[] inicio = hora_inicio.split(":");
            String[] fin = hora_fin.split(":");

            int minutosInicio = Integer.parseInt(inicio[0]) * 60 + Integer.parseInt(inicio[1]);
            int minutosFin = Integer.parseInt(fin[0]) * 60 + Integer.parseInt(fin[1]);

            return minutosFin - minutosInicio;
        } catch (Exception e) {
            return 0;
        }
    }
}
