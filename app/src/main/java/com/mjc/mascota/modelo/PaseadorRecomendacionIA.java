package com.mjc.mascota.modelo;

import java.util.List;

/**
 * Modelo para paseadores recomendados por IA (Gemini)
 * Extiende PaseadorResultado y agrega campos específicos de la recomendación
 */
public class PaseadorRecomendacionIA extends PaseadorResultado {

    private String razonIA;  // Razón principal de la recomendación generada por Gemini
    private int matchScore;  // Score de match (0-100)
    private List<String> razones;  // Lista de razones adicionales para mostrar en chips
    private String especialidad;  // Especialidad del paseador (ej: "Especialista en Perros Grandes")

    // Constructor vacío necesario para Firebase
    public PaseadorRecomendacionIA() {
        super();
    }

    // Constructor desde PaseadorResultado existente
    public PaseadorRecomendacionIA(PaseadorResultado base) {
        super(base);
    }

    // Constructor completo
    public PaseadorRecomendacionIA(PaseadorResultado base, String razonIA, int matchScore,
                                   List<String> razones, String especialidad) {
        super(base);
        this.razonIA = razonIA;
        this.matchScore = matchScore;
        this.razones = razones;
        this.especialidad = especialidad;
    }

    // Getters
    public String getRazonIA() {
        return razonIA;
    }

    public int getMatchScore() {
        return matchScore;
    }

    public List<String> getRazones() {
        return razones;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    // Setters
    public void setRazonIA(String razonIA) {
        this.razonIA = razonIA;
    }

    public void setMatchScore(int matchScore) {
        this.matchScore = matchScore;
    }

    public void setRazones(List<String> razones) {
        this.razones = razones;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }
}
