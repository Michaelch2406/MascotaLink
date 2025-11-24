package com.mjc.mascotalink.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.net.URLEncoder;

public class WhatsAppUtil {

    /**
     * Abre un chat de WhatsApp con el número proporcionado.
     * @param context Contexto de la aplicación.
     * @param numeroTelefono Número de teléfono (puede ser local 099... o internacional 593...).
     * @param mensaje Mensaje opcional a pre-llenar.
     */
    public static void abrirWhatsApp(Context context, String numeroTelefono, String mensaje) {
        if (numeroTelefono == null || numeroTelefono.isEmpty()) {
            Toast.makeText(context, "Número no válido", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Limpiar el número (quitar espacios, guiones, paréntesis, +)
        String numeroLimpio = numeroTelefono.replaceAll("[^0-9]", "");

        // 2. Lógica para Ecuador (Asumiendo contexto por coordenadas vistas anteriormente)
        // Si empieza con 0 (ej: 0991234567), quitar el 0 y agregar 593.
        if (numeroLimpio.startsWith("0") && numeroLimpio.length() == 10) {
            numeroLimpio = "593" + numeroLimpio.substring(1);
        } 
        // Si no empieza con 0 y no parece tener código de país (longitud < 10), es ambiguo, 
        // pero WhatsApp requiere código de país.
        // Si ya tiene 593... se deja igual.

        try {
            String url = "https://wa.me/" + numeroLimpio;
            if (mensaje != null && !mensaje.isEmpty()) {
                url += "?text=" + URLEncoder.encode(mensaje, "UTF-8");
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.setPackage("com.whatsapp"); // Intentar abrir explícitamente WhatsApp

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                // Intentar con WhatsApp Business si el normal no está
                intent.setPackage("com.whatsapp.w4b");
                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                } else {
                    // Fallback: Abrir en navegador (sin setPackage)
                    intent.setPackage(null);
                    context.startActivity(intent);
                }
            }
        } catch (Exception e) {
            Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}
