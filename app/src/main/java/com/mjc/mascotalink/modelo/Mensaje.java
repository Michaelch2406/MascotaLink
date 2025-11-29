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
    private String tipo;
    private Date fecha_eliminacion;

    public Mensaje() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getId_remitente() { return id_remitente; }
    public void setId_remitente(String id_remitente) { this.id_remitente = id_remitente; }

    public String getId_destinatario() { return id_destinatario; }
    public void setId_destinatario(String id_destinatario) { this.id_destinatario = id_destinatario; }

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

    public Date getFecha_eliminacion() { return fecha_eliminacion; }
    public void setFecha_eliminacion(Date fecha_eliminacion) { this.fecha_eliminacion = fecha_eliminacion; }
    
    @Override
    public int getType() {
        return ChatItem.TYPE_MESSAGE;
    }
}
