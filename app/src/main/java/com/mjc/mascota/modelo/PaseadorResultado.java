package com.mjc.mascota.modelo;

import com.google.firebase.firestore.GeoPoint;
import java.util.List;

public class PaseadorResultado {
    private String id;
    private String nombre;
    private String fotoUrl;
    private double calificacion;
    private int totalResenas;
    private String zonaPrincipal;
    private List<String> zonasServicio;
    private boolean disponibleAhora;
    private int anosExperiencia;
    private double tarifaPorHora;
    private List<GeoPoint> zonasServicioGeoPoints;

    // Constructor vacío necesario para Firebase
    public PaseadorResultado() {}

    // Getters
    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getFotoUrl() { return fotoUrl; }
    public double getCalificacion() { return calificacion; }
    public int getTotalResenas() { return totalResenas; }
    public String getZonaPrincipal() { return zonaPrincipal; }
    public List<String> getZonasServicio() { return zonasServicio; }
    public boolean isDisponibleAhora() { return disponibleAhora; }
    public int getAnosExperiencia() { return anosExperiencia; }
    public double getTarifaPorHora() { return tarifaPorHora; }
    public List<GeoPoint> getZonasServicioGeoPoints() { return zonasServicioGeoPoints; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }
    public void setCalificacion(double calificacion) { this.calificacion = calificacion; }
    public void setTotalResenas(int totalResenas) { this.totalResenas = totalResenas; }
    public void setZonaPrincipal(String zonaPrincipal) { this.zonaPrincipal = zonaPrincipal; }
    public void setZonasServicio(List<String> zonasServicio) { this.zonasServicio = zonasServicio; }
    public void setDisponibleAhora(boolean disponibleAhora) { this.disponibleAhora = disponibleAhora; }
    public void setAnosExperiencia(int anosExperiencia) { this.anosExperiencia = anosExperiencia; }
    public void setTarifaPorHora(double tarifaPorHora) { this.tarifaPorHora = tarifaPorHora; }
    public void setZonasServicioGeoPoints(List<GeoPoint> zonasServicioGeoPoints) { this.zonasServicioGeoPoints = zonasServicioGeoPoints; }
}