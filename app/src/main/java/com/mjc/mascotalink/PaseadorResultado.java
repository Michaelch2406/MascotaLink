package com.mjc.mascotalink;

import java.util.List;

public class PaseadorResultado {
    private String id;
    private String nombre;
    private String fotoUrl;
    private double calificacion;
    private int numeroResenas;
    private String zonaServicio;
    private List<String> zonasServicio;
    private int anosExperiencia;
    private double tarifaPorHora;
    private boolean disponible;
    
    public PaseadorResultado() {
        // Constructor vacío requerido para Firebase
    }
    
    // Getters y Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getNombre() {
        return nombre;
    }
    
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
    
    public String getFotoUrl() {
        return fotoUrl;
    }
    
    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }
    
    public double getCalificacion() {
        return calificacion;
    }
    
    public void setCalificacion(double calificacion) {
        this.calificacion = calificacion;
    }
    
    public int getNumeroResenas() {
        return numeroResenas;
    }
    
    public void setNumeroResenas(int numeroResenas) {
        this.numeroResenas = numeroResenas;
    }
    
    public String getZonaServicio() {
        return zonaServicio;
    }
    
    public void setZonaServicio(String zonaServicio) {
        this.zonaServicio = zonaServicio;
    }
    
    public List<String> getZonasServicio() {
        return zonasServicio;
    }
    
    public void setZonasServicio(List<String> zonasServicio) {
        this.zonasServicio = zonasServicio;
    }
    
    public int getAnosExperiencia() {
        return anosExperiencia;
    }
    
    public void setAnosExperiencia(int anosExperiencia) {
        this.anosExperiencia = anosExperiencia;
    }
    
    public double getTarifaPorHora() {
        return tarifaPorHora;
    }
    
    public void setTarifaPorHora(double tarifaPorHora) {
        this.tarifaPorHora = tarifaPorHora;
    }
    
    public boolean isDisponible() {
        return disponible;
    }
    
    public void setDisponible(boolean disponible) {
        this.disponible = disponible;
    }

    private List<com.google.firebase.firestore.GeoPoint> zonasServicioGeoPoints;

    public List<com.google.firebase.firestore.GeoPoint> getZonasServicioGeoPoints() {
        return zonasServicioGeoPoints;
    }

    public void setZonasServicioGeoPoints(List<com.google.firebase.firestore.GeoPoint> zonasServicioGeoPoints) {
        this.zonasServicioGeoPoints = zonasServicioGeoPoints;
    }
}
