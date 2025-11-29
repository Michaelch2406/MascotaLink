package com.mjc.mascotalink.modelo;

/**
 * Interfaz base para items del chat (mensajes y separadores de fecha).
 */
public interface ChatItem {
    int TYPE_MESSAGE = 0;
    int TYPE_DATE_SEPARATOR = 1;
    
    int getType();
}
