
package com.mjc.mascota.ui.busqueda;

import com.google.android.gms.maps.model.LatLng;

public class PaseadorMarker {
    private String paseadorId;
    private String nombre;
    private LatLng ubicacion;
    private double calificacion;
    private String fotoUrl;
    private boolean disponible;
    private double distanciaKm;
    private boolean enPaseo; // Nuevo campo

    public PaseadorMarker(String paseadorId, String nombre, LatLng ubicacion, double calificacion, String fotoUrl, boolean disponible, double distanciaKm, boolean enPaseo) {
        this.paseadorId = paseadorId;
        this.nombre = nombre;
        this.ubicacion = ubicacion;
        this.calificacion = calificacion;
        this.fotoUrl = fotoUrl;
        this.disponible = disponible;
        this.distanciaKm = distanciaKm;
        this.enPaseo = enPaseo;
    }

    public String getPaseadorId() {
        return paseadorId;
    }

    public void setPaseadorId(String paseadorId) {
        this.paseadorId = paseadorId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public LatLng getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(LatLng ubicacion) {
        this.ubicacion = ubicacion;
    }

    public double getCalificacion() {
        return calificacion;
    }

    public void setCalificacion(double calificacion) {
        this.calificacion = calificacion;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public boolean isDisponible() {
        return disponible;
    }

    public void setDisponible(boolean disponible) {
        this.disponible = disponible;
    }

    public double getDistanciaKm() {
        return distanciaKm;
    }

    public void setDistanciaKm(double distanciaKm) {
        this.distanciaKm = distanciaKm;
    }

    public boolean isEnPaseo() {
        return enPaseo;
    }

    public void setEnPaseo(boolean enPaseo) {
        this.enPaseo = enPaseo;
    }
}
