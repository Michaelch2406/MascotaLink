package com.mjc.mascotalink.modelo;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

/**
 * Modelo para bloqueos de disponibilidad (días libres, vacaciones, etc.)
 * Se guarda en: paseadores/{uid}/disponibilidad/bloqueos/{bloqueoId}
 */
public class Bloqueo {

    @DocumentId
    private String id;

    private Timestamp fecha; // Fecha específica del bloqueo
    private String tipo; // "dia_completo", "manana", "tarde"
    private String razon; // "Vacaciones", "Compromiso personal", etc.
    private boolean repetir; // Si se repite periódicamente
    private String frecuencia_repeticion; // null, "semanal", "mensual"

    @ServerTimestamp
    private Date fecha_creacion;

    private boolean activo; // Permite desactivar sin eliminar

    // Constructor vacío requerido para Firestore
    public Bloqueo() {
        this.activo = true;
    }

    // Constructor con parámetros principales
    public Bloqueo(Timestamp fecha, String tipo, String razon) {
        this.fecha = fecha;
        this.tipo = tipo;
        this.razon = razon;
        this.repetir = false;
        this.frecuencia_repeticion = null;
        this.activo = true;
    }

    // Constantes para tipos de bloqueo
    public static final String TIPO_DIA_COMPLETO = "dia_completo";
    public static final String TIPO_MANANA = "manana";
    public static final String TIPO_TARDE = "tarde";

    // Constantes para frecuencias
    public static final String FRECUENCIA_SEMANAL = "semanal";
    public static final String FRECUENCIA_MENSUAL = "mensual";

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

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getRazon() {
        return razon;
    }

    public void setRazon(String razon) {
        this.razon = razon;
    }

    public boolean isRepetir() {
        return repetir;
    }

    public void setRepetir(boolean repetir) {
        this.repetir = repetir;
    }

    public String getFrecuencia_repeticion() {
        return frecuencia_repeticion;
    }

    public void setFrecuencia_repeticion(String frecuencia_repeticion) {
        this.frecuencia_repeticion = frecuencia_repeticion;
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
     * Verifica si este bloqueo afecta a una hora específica
     * @param hora Hora a verificar (formato 24h, ej: 14 para 2 PM)
     * @return true si la hora está bloqueada
     */
    public boolean afectaHora(int hora) {
        if (!activo) return false;

        switch (tipo) {
            case TIPO_DIA_COMPLETO:
                return true;
            case TIPO_MANANA:
                return hora < 12;
            case TIPO_TARDE:
                return hora >= 12;
            default:
                return false;
        }
    }

    /**
     * Obtiene una descripción legible del bloqueo
     */
    public String getDescripcion() {
        StringBuilder desc = new StringBuilder();

        if (razon != null && !razon.isEmpty()) {
            desc.append(razon);
        } else {
            desc.append("Día bloqueado");
        }

        if (tipo.equals(TIPO_MANANA)) {
            desc.append(" (solo mañana)");
        } else if (tipo.equals(TIPO_TARDE)) {
            desc.append(" (solo tarde)");
        }

        return desc.toString();
    }
}
