package com.mjc.mascotalink.modelo;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class PaseadorFavorito {

    @DocumentId
    private String paseadorId; // El ID del documento será el ID del paseador

    private String nombre_display;
    private String foto_perfil_url;
    private Double calificacion_promedio;
    private Double precio_hora;

    @ServerTimestamp
    private Date fecha_agregado;

    // Constructor vacío requerido para Firestore
    public PaseadorFavorito() {}

    // Getters
    public String getPaseadorId() {
        return paseadorId;
    }

    public String getNombre_display() {
        return nombre_display;
    }

    public String getFoto_perfil_url() {
        return foto_perfil_url;
    }

    public Double getCalificacion_promedio() {
        return calificacion_promedio;
    }

    public Double getPrecio_hora() {
        return precio_hora;
    }

    public Date getFecha_agregado() {
        return fecha_agregado;
    }

    // Setters (opcional, pero buena práctica)
    public void setPaseadorId(String paseadorId) {
        this.paseadorId = paseadorId;
    }
}
