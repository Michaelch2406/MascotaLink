
package com.mjc.mascota.ui.busqueda;

import java.util.HashMap;
import java.util.Map;

public class PaseadorMarkersCache {
    private static PaseadorMarkersCache instance;
    private final Map<String, PaseadorMarker> cache;

    private PaseadorMarkersCache() {
        cache = new HashMap<>();
    }

    public static synchronized PaseadorMarkersCache getInstance() {
        if (instance == null) {
            instance = new PaseadorMarkersCache();
        }
        return instance;
    }

    public void addPaseadorMarker(PaseadorMarker marker) {
        cache.put(marker.getPaseadorId(), marker);
    }

    public PaseadorMarker getPaseadorMarker(String paseadorId) {
        return cache.get(paseadorId);
    }

    public void clear() {
        cache.clear();
    }

    public Map<String, PaseadorMarker> getAllPaseadorMarkers() {
        return new HashMap<>(cache);
    }
}
