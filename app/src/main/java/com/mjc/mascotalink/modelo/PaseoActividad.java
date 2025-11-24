package com.mjc.mascotalink.modelo;

import com.google.firebase.Timestamp;
import java.util.Date;

public class PaseoActividad {
    private String evento;
    private String descripcion;
    private Timestamp timestamp;

    public PaseoActividad() {
        // Constructor vacÃ­o para Firebase
    }

    public PaseoActividad(String evento, String descripcion, Timestamp timestamp) {
        this.evento = evento;
        this.descripcion = descripcion;
        this.timestamp = timestamp;
    }

    public String getEvento() {
        return evento;
    }

    public void setEvento(String evento) {
        this.evento = evento;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public Date getDate() {
        return timestamp != null ? timestamp.toDate() : new Date();
    }
}
