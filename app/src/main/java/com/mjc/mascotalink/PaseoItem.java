package com.mjc.mascotalink;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Clase auxiliar para manejar tanto reservas individuales como agrupadas
 * en PaseosAdapter
 */
public class PaseoItem {
    private boolean esGrupo;
    private Paseo paseoIndividual;  // Para reservas simples
    private List<Paseo> paseoGrupo;  // Para reservas agrupadas
    private String grupoReservaId;

    // Constructor para reserva individual
    public PaseoItem(Paseo paseo) {
        this.esGrupo = false;
        this.paseoIndividual = paseo;
        this.paseoGrupo = null;
        this.grupoReservaId = null;
    }

    // Constructor para grupo de reservas
    public PaseoItem(List<Paseo> paseos, String grupoId) {
        this.esGrupo = true;
        this.paseoIndividual = null;
        this.paseoGrupo = new ArrayList<>(paseos);
        this.grupoReservaId = grupoId;

        // Ordenar por fecha
        Collections.sort(this.paseoGrupo, (p1, p2) -> {
            if (p1.getFecha() == null || p2.getFecha() == null) return 0;
            return p1.getFecha().compareTo(p2.getFecha());
        });
    }

    public boolean esGrupo() {
        return esGrupo;
    }

    public Paseo getPaseoIndividual() {
        return paseoIndividual;
    }

    public List<Paseo> getPaseoGrupo() {
        return paseoGrupo;
    }

    public String getGrupoReservaId() {
        return grupoReservaId;
    }

    // Métodos de conveniencia para obtener datos del grupo
    public int getCantidadDias() {
        return esGrupo ? paseoGrupo.size() : 1;
    }

    public double getCostoTotal() {
        if (!esGrupo) {
            return paseoIndividual != null ? paseoIndividual.getCosto_total() : 0.0;
        }
        double total = 0.0;
        for (Paseo p : paseoGrupo) {
            total += p.getCosto_total();
        }
        return total;
    }

    public String getRangoFechas() {
        if (!esGrupo || paseoGrupo.isEmpty()) {
            return paseoIndividual != null ? paseoIndividual.getFechaFormateada() : "";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", new Locale("es", "ES"));
        Date fechaInicio = paseoGrupo.get(0).getFecha();
        Date fechaFin = paseoGrupo.get(paseoGrupo.size() - 1).getFecha();

        if (fechaInicio == null || fechaFin == null) {
            return getCantidadDias() + " días";
        }

        return sdf.format(fechaInicio) + " - " + sdf.format(fechaFin);
    }

    // Obtener el primer paseo (para datos comunes como estado, paseador, etc.)
    public Paseo getPrimerPaseo() {
        if (!esGrupo) {
            return paseoIndividual;
        }
        return paseoGrupo.isEmpty() ? null : paseoGrupo.get(0);
    }

    // Obtener el ID de reserva para navegación (usa el primer paseo del grupo)
    public String getReservaId() {
        Paseo primero = getPrimerPaseo();
        return primero != null ? primero.getReservaId() : null;
    }

    /**
     * Verifica si la reserva es de tipo SEMANAL
     */
    public boolean esSemanal() {
        Paseo paseo = getPrimerPaseo();
        return paseo != null && "SEMANAL".equalsIgnoreCase(paseo.getTipo_reserva());
    }

    /**
     * Verifica si la reserva es de tipo MENSUAL
     */
    public boolean esMensual() {
        Paseo paseo = getPrimerPaseo();
        return paseo != null && "MENSUAL".equalsIgnoreCase(paseo.getTipo_reserva());
    }

    /**
     * Obtiene el número de días completados en un grupo
     */
    public int getDiasCompletados() {
        if (!esGrupo || paseoGrupo == null) {
            Paseo paseo = getPaseoIndividual();
            if (paseo != null && "COMPLETADO".equalsIgnoreCase(paseo.getEstado())) {
                return 1;
            }
            return 0;
        }

        int completados = 0;
        for (Paseo p : paseoGrupo) {
            if ("COMPLETADO".equalsIgnoreCase(p.getEstado())) {
                completados++;
            }
        }
        return completados;
    }

    /**
     * Verifica si hay días parcialmente completados en un grupo
     */
    public boolean esCompletadoParcial() {
        if (!esGrupo) return false;
        int completados = getDiasCompletados();
        return completados > 0 && completados < getCantidadDias();
    }

    /**
     * Verifica si todos los días del grupo están completados
     */
    public boolean esCompletadoTotal() {
        int completados = getDiasCompletados();
        return completados > 0 && completados == getCantidadDias();
    }

    /**
     * Obtiene el texto de progreso para mostrar (ej: "1/3 completados")
     */
    public String getTextoProgreso() {
        if (!esGrupo) return null;
        int completados = getDiasCompletados();
        if (completados == 0) return null;
        return completados + "/" + getCantidadDias() + " completados";
    }

    /**
     * Obtiene el badge de tipo de reserva (para SEMANAL/MENSUAL)
     */
    public String getBadgeTipoReserva() {
        Paseo paseo = getPrimerPaseo();
        if (paseo == null) return null;

        String tipo = paseo.getTipo_reserva();
        if ("SEMANAL".equalsIgnoreCase(tipo)) {
            return "🔁 Servicio Semanal";
        } else if ("MENSUAL".equalsIgnoreCase(tipo)) {
            return "🔁 Servicio Mensual";
        }
        return null;
    }

    /**
     * Obtiene el estado efectivo para el grupo completo.
     * Para grupos: si hay días pendientes, el grupo está pendiente.
     * Solo cuando TODOS están completados, el grupo está completado.
     * Esto es importante para filtrar entre "Programados" e "Historial"
     */
    public String getEstadoEfectivo() {
        if (!esGrupo) {
            Paseo paseo = getPaseoIndividual();
            return paseo != null ? paseo.getEstado() : null;
        }

        // Para grupos, revisar todos los estados
        if (paseoGrupo == null || paseoGrupo.isEmpty()) return null;

        boolean tieneCompletados = false;
        boolean tienePendientes = false;
        boolean tieneCancelados = false;
        String primerEstado = null;

        for (Paseo p : paseoGrupo) {
            String estado = p.getEstado();
            if (primerEstado == null) primerEstado = estado;

            if ("COMPLETADO".equalsIgnoreCase(estado)) {
                tieneCompletados = true;
            } else if ("CANCELADO".equalsIgnoreCase(estado)) {
                tieneCancelados = true;
            } else {
                tienePendientes = true;
            }
        }

        // Si todos están cancelados, el grupo está cancelado
        if (tieneCancelados && !tieneCompletados && !tienePendientes) {
            return "CANCELADO";
        }

        // Si todos están completados, el grupo está completado
        if (tieneCompletados && !tienePendientes && !tieneCancelados) {
            return "COMPLETADO";
        }

        // Si hay al menos un día pendiente, el grupo sigue activo
        // Retornar el estado del primer paseo no completado
        for (Paseo p : paseoGrupo) {
            String estado = p.getEstado();
            if (!"COMPLETADO".equalsIgnoreCase(estado) && !"CANCELADO".equalsIgnoreCase(estado)) {
                return estado;
            }
        }

        // Por defecto, retornar el estado del primer paseo
        return primerEstado;
    }

    /**
     * Método estático para convertir una lista de Paseo en una lista de PaseoItem,
     * agrupando las reservas que tienen el mismo grupo_reserva_id
     */
    public static List<PaseoItem> agruparReservas(List<Paseo> paseos) {
        List<PaseoItem> items = new ArrayList<>();
        java.util.Map<String, List<Paseo>> grupos = new java.util.HashMap<>();

        for (Paseo paseo : paseos) {
            Boolean esGrupo = paseo.getEs_grupo();
            String grupoId = paseo.getGrupo_reserva_id();

            // Si es parte de un grupo, agregar al mapa de grupos
            if (esGrupo != null && esGrupo && grupoId != null && !grupoId.isEmpty()) {
                if (!grupos.containsKey(grupoId)) {
                    grupos.put(grupoId, new ArrayList<>());
                }
                grupos.get(grupoId).add(paseo);
            } else {
                // Reserva individual, agregar directamente
                items.add(new PaseoItem(paseo));
            }
        }

        // Convertir los grupos en PaseoItems
        for (java.util.Map.Entry<String, List<Paseo>> entry : grupos.entrySet()) {
            items.add(new PaseoItem(entry.getValue(), entry.getKey()));
        }

        // Ordenar los items por fecha (más reciente primero)
        Collections.sort(items, (item1, item2) -> {
            Paseo p1 = item1.getPrimerPaseo();
            Paseo p2 = item2.getPrimerPaseo();
            if (p1 == null || p1.getFecha() == null) return 1;
            if (p2 == null || p2.getFecha() == null) return -1;
            // Orden descendente (más reciente primero)
            return p2.getFecha().compareTo(p1.getFecha());
        });

        return items;
    }
}
