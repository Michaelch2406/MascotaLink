package com.mjc.mascotalink;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gestor centralizado para los datos del cuestionario de registro de paseadores.
 * Maneja la persistencia y recuperación de todos los datos del flujo de registro.
 */
public class QuestionnaireDataManager {
    
    private static final String PREFS_NAME = "WizardPaseador";
    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;
    
    public QuestionnaireDataManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.editor = prefs.edit();
    }
    
    // ==================== DISPONIBILIDAD ====================
    
    public void saveAvailability(List<String> days, String startTime, String endTime) {
        editor.putBoolean("disponibilidad_completa", true);
        
        Set<String> daysSet = new HashSet<>(days);
        editor.putStringSet("disponibilidad_dias", daysSet);
        
        editor.putString("disponibilidad_inicio", startTime);
        editor.putString("disponibilidad_fin", endTime);
        
        String summary = String.join(", ", days) + " de " + startTime + " a " + endTime;
        editor.putString("disponibilidad_resumen", summary);
        
        editor.apply();
    }
    
    public AvailabilityData getAvailability() {
        if (!prefs.getBoolean("disponibilidad_completa", false)) {
            return null;
        }
        
        Set<String> daysSet = prefs.getStringSet("disponibilidad_dias", new HashSet<>());
        List<String> days = new ArrayList<>(daysSet);
        String startTime = prefs.getString("disponibilidad_inicio", "08:00");
        String endTime = prefs.getString("disponibilidad_fin", "18:00");
        String summary = prefs.getString("disponibilidad_resumen", "");
        
        return new AvailabilityData(days, startTime, endTime, summary);
    }
    
    public boolean isAvailabilityComplete() {
        return prefs.getBoolean("disponibilidad_completa", false);
    }
    
    // ==================== TIPOS DE PERROS ====================
    
    public void saveDogTypes(boolean pequeno, boolean mediano, boolean grande,
                           boolean calmo, boolean activo, boolean reactividadBaja, boolean reactividadAlta) {
        editor.putBoolean("perros_completo", true);
        editor.putBoolean("perros_pequeno", pequeno);
        editor.putBoolean("perros_mediano", mediano);
        editor.putBoolean("perros_grande", grande);
        editor.putBoolean("perros_calmo", calmo);
        editor.putBoolean("perros_activo", activo);
        editor.putBoolean("perros_reactividad_baja", reactividadBaja);
        editor.putBoolean("perros_reactividad_alta", reactividadAlta);
        editor.apply();
    }
    
    public DogTypesData getDogTypes() {
        if (!prefs.getBoolean("perros_completo", false)) {
            return null;
        }
        
        return new DogTypesData(
            prefs.getBoolean("perros_pequeno", false),
            prefs.getBoolean("perros_mediano", false),
            prefs.getBoolean("perros_grande", false),
            prefs.getBoolean("perros_calmo", false),
            prefs.getBoolean("perros_activo", false),
            prefs.getBoolean("perros_reactividad_baja", false),
            prefs.getBoolean("perros_reactividad_alta", false)
        );
    }
    
    public boolean isDogTypesComplete() {
        return prefs.getBoolean("perros_completo", false);
    }
    
    // ==================== MÉTODO DE PAGO ====================
    
    public void savePaymentMethod(String bank, String accountNumber) {
        editor.putBoolean("metodo_pago_completo", true);
        editor.putString("pago_banco", bank);
        editor.putString("pago_cuenta", accountNumber);
        editor.apply();
    }
    
    public PaymentMethodData getPaymentMethod() {
        if (!prefs.getBoolean("metodo_pago_completo", false)) {
            return null;
        }
        
        return new PaymentMethodData(
            prefs.getString("pago_banco", ""),
            prefs.getString("pago_cuenta", "")
        );
    }
    
    public boolean isPaymentMethodComplete() {
        return prefs.getBoolean("metodo_pago_completo", false);
    }
    
    // ==================== VIDEO DE PRESENTACIÓN ====================
    
    public void saveVideo(String videoUri) {
        editor.putBoolean("video_presentacion_completo", true);
        editor.putString("video_presentacion_uri", videoUri);
        editor.putLong("video_presentacion_timestamp", System.currentTimeMillis());
        editor.apply();
    }
    
    public VideoData getVideo() {
        if (!prefs.getBoolean("video_presentacion_completo", false)) {
            return null;
        }
        
        return new VideoData(
            prefs.getString("video_presentacion_uri", ""),
            prefs.getLong("video_presentacion_timestamp", 0)
        );
    }
    
    public boolean isVideoComplete() {
        return prefs.getBoolean("video_presentacion_completo", false);
    }
    
    // ==================== ZONAS DE SERVICIO ====================
    
    public void saveServiceZones(List<ServiceZone> zones) {
        editor.putBoolean("zonas_servicio_completo", true);
        
        Set<String> zonesSet = new HashSet<>();
        for (ServiceZone zone : zones) {
            String zoneStr = zone.latitude + "," + zone.longitude + "," + 
                           zone.radius + "," + zone.address;
            zonesSet.add(zoneStr);
        }
        editor.putStringSet("zonas_servicio", zonesSet);
        editor.apply();
    }
    
    public List<ServiceZone> getServiceZones() {
        Set<String> zonesSet = prefs.getStringSet("zonas_servicio", new HashSet<>());
        List<ServiceZone> zones = new ArrayList<>();
        
        for (String zoneStr : zonesSet) {
            try {
                String[] parts = zoneStr.split(",", 4);
                if (parts.length >= 4) {
                    double lat = Double.parseDouble(parts[0]);
                    double lon = Double.parseDouble(parts[1]);
                    float radius = Float.parseFloat(parts[2]);
                    String address = parts[3];
                    
                    zones.add(new ServiceZone(lat, lon, radius, address));
                }
            } catch (NumberFormatException e) {
                // Ignorar entradas inválidas
            }
        }
        
        return zones;
    }
    
    public boolean isServiceZonesComplete() {
        return prefs.getBoolean("zonas_servicio_completo", false);
    }
    
    // ==================== ESTADO GENERAL ====================
    
    public QuestionnaireStatus getOverallStatus() {
        return new QuestionnaireStatus(
            isAvailabilityComplete(),
            isDogTypesComplete(),
            isPaymentMethodComplete(),
            isVideoComplete(),
            isServiceZonesComplete()
        );
    }
    
    public boolean isQuestionnaireComplete() {
        QuestionnaireStatus status = getOverallStatus();
        return status.isAvailabilityComplete() && 
               status.isDogTypesComplete() && 
               status.isPaymentMethodComplete() && 
               status.isVideoComplete() && 
               status.isServiceZonesComplete();
    }
    
    public void clearAllData() {
        editor.clear().apply();
    }
    
    // ==================== CLASES DE DATOS ====================
    
    public static class AvailabilityData {
        public final List<String> days;
        public final String startTime;
        public final String endTime;
        public final String summary;
        
        public AvailabilityData(List<String> days, String startTime, String endTime, String summary) {
            this.days = days;
            this.startTime = startTime;
            this.endTime = endTime;
            this.summary = summary;
        }
    }
    
    public static class DogTypesData {
        public final boolean pequeno, mediano, grande;
        public final boolean calmo, activo, reactividadBaja, reactividadAlta;
        
        public DogTypesData(boolean pequeno, boolean mediano, boolean grande,
                          boolean calmo, boolean activo, boolean reactividadBaja, boolean reactividadAlta) {
            this.pequeno = pequeno;
            this.mediano = mediano;
            this.grande = grande;
            this.calmo = calmo;
            this.activo = activo;
            this.reactividadBaja = reactividadBaja;
            this.reactividadAlta = reactividadAlta;
        }
        
        public List<String> getSizes() {
            List<String> sizes = new ArrayList<>();
            if (pequeno) sizes.add("Pequeño");
            if (mediano) sizes.add("Mediano");
            if (grande) sizes.add("Grande");
            return sizes;
        }
        
        public List<String> getTemperaments() {
            List<String> temps = new ArrayList<>();
            if (calmo) temps.add("Calmo");
            if (activo) temps.add("Activo");
            if (reactividadBaja) temps.add("Baja reactividad");
            if (reactividadAlta) temps.add("Alta reactividad");
            return temps;
        }
    }
    
    public static class PaymentMethodData {
        public final String bank;
        public final String accountNumber;
        
        public PaymentMethodData(String bank, String accountNumber) {
            this.bank = bank;
            this.accountNumber = accountNumber;
        }
    }
    
    public static class VideoData {
        public final String uri;
        public final long timestamp;
        
        public VideoData(String uri, long timestamp) {
            this.uri = uri;
            this.timestamp = timestamp;
        }
        
        public Uri getUri() {
            return Uri.parse(uri);
        }
    }
    
    public static class ServiceZone {
        public final double latitude;
        public final double longitude;
        public final float radius;
        public final String address;
        
        public ServiceZone(double latitude, double longitude, float radius, String address) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.radius = radius;
            this.address = address;
        }
    }
    
    public static class QuestionnaireStatus {
        private final boolean availabilityComplete;
        private final boolean dogTypesComplete;
        private final boolean paymentMethodComplete;
        private final boolean videoComplete;
        private final boolean serviceZonesComplete;
        
        public QuestionnaireStatus(boolean availabilityComplete, boolean dogTypesComplete, 
                                 boolean paymentMethodComplete, boolean videoComplete, 
                                 boolean serviceZonesComplete) {
            this.availabilityComplete = availabilityComplete;
            this.dogTypesComplete = dogTypesComplete;
            this.paymentMethodComplete = paymentMethodComplete;
            this.videoComplete = videoComplete;
            this.serviceZonesComplete = serviceZonesComplete;
        }
        
        public boolean isAvailabilityComplete() { return availabilityComplete; }
        public boolean isDogTypesComplete() { return dogTypesComplete; }
        public boolean isPaymentMethodComplete() { return paymentMethodComplete; }
        public boolean isVideoComplete() { return videoComplete; }
        public boolean isServiceZonesComplete() { return serviceZonesComplete; }
        
        public int getCompletedCount() {
            int count = 0;
            if (availabilityComplete) count++;
            if (dogTypesComplete) count++;
            if (paymentMethodComplete) count++;
            if (videoComplete) count++;
            if (serviceZonesComplete) count++;
            return count;
        }
        
        public int getTotalCount() {
            return 5;
        }
        
        public double getCompletionPercentage() {
            return (getCompletedCount() * 100.0) / getTotalCount();
        }
    }
}