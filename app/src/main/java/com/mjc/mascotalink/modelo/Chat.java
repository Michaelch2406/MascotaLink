package com.mjc.mascotalink.modelo;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Chat {
    private String chatId;
    private List<String> participantes;
    private String ultimo_mensaje;
    @ServerTimestamp
    private Date ultimo_timestamp;
    private Map<String, Long> mensajes_no_leidos;
    private Date fecha_creacion;
    private Map<String, String> estado_usuarios;
    private Map<String, Date> ultima_actividad;

    // Campos auxiliares para la UI (no en Firebase)
    private String nombreOtroUsuario;
    private String fotoOtroUsuario;
    private String estadoOtroUsuario;

    public Chat() {}

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public List<String> getParticipantes() { return participantes; }
    public void setParticipantes(List<String> participantes) { this.participantes = participantes; }

    public String getUltimo_mensaje() { return ultimo_mensaje; }
    public void setUltimo_mensaje(String ultimo_mensaje) { this.ultimo_mensaje = ultimo_mensaje; }

    public Date getUltimo_timestamp() { return ultimo_timestamp; }
    public void setUltimo_timestamp(Date ultimo_timestamp) { this.ultimo_timestamp = ultimo_timestamp; }

    public Map<String, Long> getMensajes_no_leidos() { return mensajes_no_leidos; }
    public void setMensajes_no_leidos(Map<String, Long> mensajes_no_leidos) { this.mensajes_no_leidos = mensajes_no_leidos; }

    public Date getFecha_creacion() { return fecha_creacion; }
    public void setFecha_creacion(Date fecha_creacion) { this.fecha_creacion = fecha_creacion; }

    public Map<String, String> getEstado_usuarios() { return estado_usuarios; }
    public void setEstado_usuarios(Map<String, String> estado_usuarios) { this.estado_usuarios = estado_usuarios; }

    public Map<String, Date> getUltima_actividad() { return ultima_actividad; }
    public void setUltima_actividad(Map<String, Date> ultima_actividad) { this.ultima_actividad = ultima_actividad; }

    // Getters y Setters auxiliares
    public String getNombreOtroUsuario() { return nombreOtroUsuario; }
    public void setNombreOtroUsuario(String nombreOtroUsuario) { this.nombreOtroUsuario = nombreOtroUsuario; }

    public String getFotoOtroUsuario() { return fotoOtroUsuario; }
    public void setFotoOtroUsuario(String fotoOtroUsuario) { this.fotoOtroUsuario = fotoOtroUsuario; }

    public String getEstadoOtroUsuario() { return estadoOtroUsuario; }
    public void setEstadoOtroUsuario(String estadoOtroUsuario) { this.estadoOtroUsuario = estadoOtroUsuario; }
}
