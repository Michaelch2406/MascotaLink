package com.mjc.mascotalink.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ReservaEstadoValidator {

    public static final String ESTADO_PENDIENTE_ACEPTACION = "PENDIENTE_ACEPTACION";
    public static final String ESTADO_ACEPTADO = "ACEPTADO";
    public static final String ESTADO_CONFIRMADO = "CONFIRMADO";
    public static final String ESTADO_RECHAZADO = "RECHAZADO";
    public static final String ESTADO_CANCELADO = "CANCELADO";
    public static final String ESTADO_EN_CURSO = "EN_CURSO";
    public static final String ESTADO_COMPLETADO = "COMPLETADO";

    public static final String ESTADO_PAGO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_PAGO_CONFIRMADO = "CONFIRMADO";

    private static final Map<String, Set<String>> VALID_TRANSITIONS;

    static {
        Map<String, Set<String>> transitions = new HashMap<>();
        transitions.put(ESTADO_PENDIENTE_ACEPTACION, createSet(ESTADO_ACEPTADO, ESTADO_RECHAZADO, ESTADO_CANCELADO));
        transitions.put(ESTADO_ACEPTADO, createSet(ESTADO_CONFIRMADO, ESTADO_CANCELADO));
        transitions.put(ESTADO_CONFIRMADO, createSet(ESTADO_EN_CURSO, ESTADO_COMPLETADO, ESTADO_CANCELADO));
        transitions.put(ESTADO_EN_CURSO, createSet(ESTADO_COMPLETADO, ESTADO_CANCELADO));
        transitions.put(ESTADO_COMPLETADO, Collections.emptySet());
        transitions.put(ESTADO_RECHAZADO, Collections.emptySet());
        transitions.put(ESTADO_CANCELADO, Collections.emptySet());
        VALID_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    private static Set<String> createSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    private ReservaEstadoValidator() {
        // Helper class; prevent instantiation.
    }

    public static boolean canTransition(String currentState, String nextState) {
        if (currentState == null || nextState == null) {
            return false;
        }
        Set<String> allowed = VALID_TRANSITIONS.get(currentState);
        return allowed != null && allowed.contains(nextState);
    }

    public static boolean canPay(String currentState) {
        return ESTADO_ACEPTADO.equals(currentState);
    }

    public static boolean isTerminal(String state) {
        return ESTADO_RECHAZADO.equals(state) || ESTADO_CANCELADO.equals(state) || ESTADO_COMPLETADO.equals(state);
    }

    public static boolean isPagoCompletado(String estadoPago) {
        return ESTADO_PAGO_CONFIRMADO.equals(estadoPago);
    }
}
