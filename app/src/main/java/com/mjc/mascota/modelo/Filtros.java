package com.mjc.mascota.modelo;

import java.util.List;

public class Filtros {

    private String orden;
    private float minPrecio;
    private float maxPrecio;
    private float minCalificacion;
    private float maxDistancia;
    private List<String> tamanosMascota;

    public Filtros() {
        // Valores por defecto
        this.orden = "Distancia (más cercano)";
        this.minPrecio = 0;
        this.maxPrecio = 100;
        this.minCalificacion = 0;
        this.maxDistancia = 50;
        this.tamanosMascota = null;
    }

    // Getters y Setters

    public String getOrden() {
        return orden;
    }

    public void setOrden(String orden) {
        this.orden = orden;
    }

    public float getMinPrecio() {
        return minPrecio;
    }

    public void setMinPrecio(float minPrecio) {
        this.minPrecio = minPrecio;
    }

    public float getMaxPrecio() {
        return maxPrecio;
    }

    public void setMaxPrecio(float maxPrecio) {
        this.maxPrecio = maxPrecio;
    }

    public float getMinCalificacion() {
        return minCalificacion;
    }

    public void setMinCalificacion(float minCalificacion) {
        this.minCalificacion = minCalificacion;
    }

    public float getMaxDistancia() {
        return maxDistancia;
    }

    public void setMaxDistancia(float maxDistancia) {
        this.maxDistancia = maxDistancia;
    }

    public List<String> getTamanosMascota() {
        return tamanosMascota;
    }

    public void setTamanosMascota(List<String> tamanosMascota) {
        this.tamanosMascota = tamanosMascota;
    }
}
