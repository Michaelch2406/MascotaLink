package com.mjc.mascotalink.modelo;

import com.google.firebase.Timestamp;

public class Notificacion {
    private String id;
    private String titulo;
    private String mensaje;
    private String tipo; // RESERVA, MENSAJE, PASEO, PAGO, SISTEMA
    private String userId;
    private Timestamp fecha;
    private boolean leida;
    private String referenceId; // ID de la reserva, mensaje, etc.

    public Notificacion() {
        // Constructor vacío requerido por Firestore
    }

    public Notificacion(String id, String titulo, String mensaje, String tipo, String userId, Timestamp fecha, boolean leida, String referenceId) {
        this.id = id;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.tipo = tipo;
        this.userId = userId;
        this.fecha = fecha;
        this.leida = leida;
        this.referenceId = referenceId;
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Timestamp getFecha() {
        return fecha;
    }

    public void setFecha(Timestamp fecha) {
        this.fecha = fecha;
    }

    public boolean isLeida() {
        return leida;
    }

    public void setLeida(boolean leida) {
        this.leida = leida;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }
}
