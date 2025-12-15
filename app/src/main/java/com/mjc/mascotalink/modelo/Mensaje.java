package com.mjc.mascotalink.modelo;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Mensaje implements ChatItem {
    private String id;
    private String id_remitente;
    private String id_destinatario;
    private String texto;
    @ServerTimestamp
    private Date timestamp;
    private boolean leido;
    private boolean entregado;
    private String tipo; // "texto", "imagen", "ubicacion"
    private Date fecha_eliminacion;
    
    // Campos para mensajes de imagen
    private String imagen_url;
    
    // Campos para mensajes de ubicación
    private Double latitud;
    private Double longitud;

    public Mensaje() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIdRemitente() { return id_remitente; }
    public void setIdRemitente(String id_remitente) { this.id_remitente = id_remitente; }

    public String getIdDestinatario() { return id_destinatario; }
    public void setIdDestinatario(String id_destinatario) { this.id_destinatario = id_destinatario; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public boolean isLeido() { return leido; }
    public void setLeido(boolean leido) { this.leido = leido; }

    public boolean isEntregado() { return entregado; }
    public void setEntregado(boolean entregado) { this.entregado = entregado; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Date getFechaEliminacion() { return fecha_eliminacion; }
    public void setFechaEliminacion(Date fecha_eliminacion) { this.fecha_eliminacion = fecha_eliminacion; }

    public String getImagenUrl() { return imagen_url; }
    public void setImagenUrl(String imagen_url) { this.imagen_url = imagen_url; }
    
    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }
    
    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }
    
    @Override
    public int getType() {
        return ChatItem.TYPE_MESSAGE;
    }
}
