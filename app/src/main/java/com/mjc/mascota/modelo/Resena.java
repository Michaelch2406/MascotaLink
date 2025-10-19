package com.mjc.mascota.modelo;

import com.google.firebase.Timestamp;

public class Resena {
    private String id;
    private String autorNombre;
    private String autorFotoUrl;
    private float calificacion;
    private String comentario;
    private Timestamp fecha;

    public Resena() {}

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAutorNombre() {
        return autorNombre;
    }

    public void setAutorNombre(String autorNombre) {
        this.autorNombre = autorNombre;
    }

    public String getAutorFotoUrl() {
        return autorFotoUrl;
    }

    public void setAutorFotoUrl(String autorFotoUrl) {
        this.autorFotoUrl = autorFotoUrl;
    }

    public float getCalificacion() {
        return calificacion;
    }

    public void setCalificacion(float calificacion) {
        this.calificacion = calificacion;
    }

    public String getComentario() {
        return comentario;
    }

    public void setComentario(String comentario) {
        this.comentario = comentario;
    }

    public Timestamp getFecha() {
        return fecha;
    }

    public void setFecha(Timestamp fecha) {
        this.fecha = fecha;
    }
}
