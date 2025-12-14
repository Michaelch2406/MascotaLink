package com.mjc.mascotalink;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.GeoPoint; // Added import

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import java.util.List;

public class Paseo implements Serializable {
    private String reservaId;
    private String paseadorNombre, paseadorFoto, duenoNombre, mascotaNombre, mascotaFoto;
    @PropertyName("id_mascota") private String idMascota;
    private transient DocumentReference id_dueno;
    private transient DocumentReference id_paseador;
    private Date fecha;
    private Date hora_inicio;
    private String estado, razonCancelacion;
    private String tipo_reserva;
    private String estado_pago;
    private double costo_total;
    private long duracion_minutos;
    private String id_pago, transaction_id, metodo_pago, notas;
    private Date fecha_pago, fecha_creacion, fecha_respuesta;
    private Double tarifa_confirmada;
    private Boolean hasTransitionedToInCourse, reminderSent;
    private Date fecha_inicio_paseo;
    private Date fecha_fin_paseo;
    private String timeZone;

    // Campos para reservas agrupadas (múltiples días específicos)
    private String grupo_reserva_id;  // ID único que vincula reservas del mismo grupo
    private Boolean es_grupo;         // true si esta reserva es parte de un grupo

    // Nuevos campos para evitar warnings de Firestore y soportar historial completo
    private long tiempo_total_minutos;
    // Acepta GeoPoint o Map para evitar errores de deserialización mezclada en Firestore
    private List<Object> ubicaciones;
    private GeoPoint ubicacion_actual; // Changed type
    private Date ultima_actualizacion;
    private String motivo_rechazo;
    // Campos adicionales que llegan en reservas completadas
    private Double distancia_acumulada_metros;
    private Double distancia_km;
    private Boolean dueno_viendo_mapa;

    public Paseo() {}

    // Getters & Setters
    public String getReservaId() { return reservaId; }
    public void setReservaId(String id) { this.reservaId = id; }
    public String getEstado() { return estado; }
    public void setEstado(String e) { this.estado = e; }
    public String getEstado_pago() { return estado_pago; }
    public void setEstado_pago(String estado_pago) { this.estado_pago = estado_pago; }
    public double getCosto_total() { return costo_total; }
    public void setCosto_total(double costo_total) { this.costo_total = costo_total; }
    public long getDuracion_minutos() { return duracion_minutos; }
    public void setDuracion_minutos(long duracion_minutos) { this.duracion_minutos = duracion_minutos; }
    public String getTipo_reserva() { return tipo_reserva; }
    public void setTipo_reserva(String tipo_reserva) { this.tipo_reserva = tipo_reserva; }
    public Date getFecha() { return fecha; }
    public void setFecha(Date fecha) { this.fecha = fecha; }
    public Date getHora_inicio() { return hora_inicio; }
    public void setHora_inicio(Date hora_inicio) { this.hora_inicio = hora_inicio; }
    public String getPaseadorNombre() { return paseadorNombre; }
    public void setPaseadorNombre(String n) { this.paseadorNombre = n; }
    public String getPaseadorFoto() { return paseadorFoto; }
    public void setPaseadorFoto(String f) { this.paseadorFoto = f; }
    public String getDuenoNombre() { return duenoNombre; }
    public void setDuenoNombre(String n) { this.duenoNombre = n; }
    public String getMascotaNombre() { return mascotaNombre; }
    public void setMascotaNombre(String n) { this.mascotaNombre = n; }
    public String getMascotaFoto() { return mascotaFoto; }
    public void setMascotaFoto(String f) { this.mascotaFoto = f; }
    public String getRazonCancelacion() { return razonCancelacion; }
    public void setRazonCancelacion(String razonCancelacion) { this.razonCancelacion = razonCancelacion; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }

    // Getters y Setters para nuevos campos
    public long getTiempo_total_minutos() { return tiempo_total_minutos; }
    public void setTiempo_total_minutos(long tiempo_total_minutos) { this.tiempo_total_minutos = tiempo_total_minutos; }
    public List<Object> getUbicaciones() { return ubicaciones; }
    public void setUbicaciones(List<Object> ubicaciones) { this.ubicaciones = ubicaciones; }
    public GeoPoint getUbicacion_actual() { return ubicacion_actual; } // Updated getter
    public void setUbicacion_actual(GeoPoint ubicacion_actual) { this.ubicacion_actual = ubicacion_actual; } // Updated setter
    public Date getUltima_actualizacion() { return ultima_actualizacion; }
    public void setUltima_actualizacion(Date ultima_actualizacion) { this.ultima_actualizacion = ultima_actualizacion; }
    public String getMotivo_rechazo() { return motivo_rechazo; }
    public void setMotivo_rechazo(String motivo_rechazo) { this.motivo_rechazo = motivo_rechazo; }
    public Double getDistancia_acumulada_metros() { return distancia_acumulada_metros; }
    public void setDistancia_acumulada_metros(Double distancia_acumulada_metros) { this.distancia_acumulada_metros = distancia_acumulada_metros; }
    public Double getDistancia_km() { return distancia_km; }
    public void setDistancia_km(Double distancia_km) { this.distancia_km = distancia_km; }
    public Boolean getDueno_viendo_mapa() { return dueno_viendo_mapa; }
    public void setDueno_viendo_mapa(Boolean dueno_viendo_mapa) { this.dueno_viendo_mapa = dueno_viendo_mapa; }
    
    @PropertyName("id_mascota") public String getIdMascota() { return idMascota; }
    @PropertyName("id_mascota") public void setIdMascota(String id) { this.idMascota = id; }
    
    public DocumentReference getId_dueno() { return id_dueno; }
    public void setId_dueno(DocumentReference ref) { this.id_dueno = ref; }
    public DocumentReference getId_paseador() { return id_paseador; }
    public void setId_paseador(DocumentReference ref) { this.id_paseador = ref; }

    public String getId_pago() { return id_pago; }
    public void setId_pago(String id_pago) { this.id_pago = id_pago; }
    public String getTransaction_id() { return transaction_id; }
    public void setTransaction_id(String transaction_id) { this.transaction_id = transaction_id; }
    public String getMetodo_pago() { return metodo_pago; }
    public void setMetodo_pago(String metodo_pago) { this.metodo_pago = metodo_pago; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Date getFecha_pago() { return fecha_pago; }
    public void setFecha_pago(Date fecha_pago) { this.fecha_pago = fecha_pago; }
    public Date getFecha_creacion() { return fecha_creacion; }
    public void setFecha_creacion(Date fecha_creacion) { this.fecha_creacion = fecha_creacion; }
    public Date getFecha_respuesta() { return fecha_respuesta; }
    public void setFecha_respuesta(Date fecha_respuesta) { this.fecha_respuesta = fecha_respuesta; }
    public Double getTarifa_confirmada() { return tarifa_confirmada; }
    public void setTarifa_confirmada(Double tarifa_confirmada) { this.tarifa_confirmada = tarifa_confirmada; }
    public Boolean getHasTransitionedToInCourse() { return hasTransitionedToInCourse; }
    public void setHasTransitionedToInCourse(Boolean hasTransitionedToInCourse) { this.hasTransitionedToInCourse = hasTransitionedToInCourse; }
    public Boolean getReminderSent() { return reminderSent; }
    public void setReminderSent(Boolean reminderSent) { this.reminderSent = reminderSent; }
    
    public Date getFecha_inicio_paseo() { return fecha_inicio_paseo; }
    public void setFecha_inicio_paseo(Date fecha_inicio_paseo) { this.fecha_inicio_paseo = fecha_inicio_paseo; }
    public Date getFecha_fin_paseo() { return fecha_fin_paseo; }
    public void setFecha_fin_paseo(Date fecha_fin_paseo) { this.fecha_fin_paseo = fecha_fin_paseo; }

    // Getters y Setters para reservas agrupadas
    public String getGrupo_reserva_id() { return grupo_reserva_id; }
    public void setGrupo_reserva_id(String grupo_reserva_id) { this.grupo_reserva_id = grupo_reserva_id; }
    public Boolean getEs_grupo() { return es_grupo; }
    public void setEs_grupo(Boolean es_grupo) { this.es_grupo = es_grupo; }

    public String getFechaFormateada() {
        if (fecha == null) return "";
        Calendar today = Calendar.getInstance();
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        
        Calendar paseoDate = Calendar.getInstance();
        if (timeZone != null && !timeZone.isEmpty()) {
             java.util.TimeZone tz = java.util.TimeZone.getTimeZone(timeZone);
             today.setTimeZone(tz);
             tomorrow.setTimeZone(tz);
             paseoDate.setTimeZone(tz);
        }
        paseoDate.setTime(fecha);
        
        if (today.get(Calendar.YEAR) == paseoDate.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == paseoDate.get(Calendar.DAY_OF_YEAR))
            return "Hoy";
        if (tomorrow.get(Calendar.YEAR) == paseoDate.get(Calendar.YEAR)
                && tomorrow.get(Calendar.DAY_OF_YEAR) == paseoDate.get(Calendar.DAY_OF_YEAR))
            return "Mañana";
            
        SimpleDateFormat sdf = new SimpleDateFormat("d 'de' MMMM", new Locale("es", "ES"));
        if (timeZone != null && !timeZone.isEmpty()) {
            sdf.setTimeZone(java.util.TimeZone.getTimeZone(timeZone));
        }
        return sdf.format(fecha);
    }

    public String getHoraFormateada() {
        if (hora_inicio == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
        if (timeZone != null && !timeZone.isEmpty()) {
            sdf.setTimeZone(java.util.TimeZone.getTimeZone(timeZone));
        }
        return sdf.format(hora_inicio);
    }
}
