# Guía de Configuración de Red - MascotaLink

## 📋 Descripción General

El nuevo sistema de detección de red es **híbrido y automático**, diseñado para funcionar en **cualquier red** sin necesidad de configurar IPs manualmente para cada SSID. Soporta:

- ✅ **Tailscale** - VPN mesh para acceso remoto desde cualquier ubicación
- ✅ **Auto-detección de Gateway** - Detecta automáticamente el servidor en redes locales
- ✅ **Configuración Manual** - Fallback para casos especiales
- ✅ **SSID Legacy** - Mantiene compatibilidad con configuraciones anteriores

---

## 🎯 Sistema de Prioridades

El sistema detecta automáticamente la mejor conexión en este orden:

### 1️⃣ **PRIORIDAD 1: Tailscale**
- **¿Cuándo se usa?** Cuando Tailscale está activo en el dispositivo
- **Ventaja:** Funciona desde **cualquier ubicación** (casa, trabajo, cafetería, etc.)
- **IP del servidor:** `100.88.138.23` (tu laptop con Firebase Emulator)
- **Cómo funciona:** Detecta la interfaz `tun0` con IP en rango `100.64.0.0/10`

### 2️⃣ **PRIORIDAD 2: Gateway Local (Auto-detección)**
- **¿Cuándo se usa?** En redes WiFi normales cuando Tailscale no está activo
- **Ventaja:** **No requiere configuración**, funciona automáticamente
- **Cómo funciona:** Detecta el gateway de la red (IP del router/PC que comparte internet)
- **Ejemplo:** En red `192.168.1.x`, detecta `192.168.1.1` como servidor

### 3️⃣ **PRIORIDAD 3: SSID Conocido (Legacy)**
- **¿Cuándo se usa?** Si el SSID está en la lista de configuraciones hardcodeadas
- **Ventaja:** Mantiene compatibilidad con configuración anterior
- **Ejemplo:** Red "INNO_FLIA_CHASIGUANO_5G" → `192.168.0.147`

### 4️⃣ **PRIORIDAD 4: Configuración Manual**
- **¿Cuándo se usa?** Si el usuario configuró manualmente una IP
- **Ventaja:** Control total para casos especiales
- **Cómo configurar:** Usar `NetworkConfigActivity` o código

### 5️⃣ **PRIORIDAD 5: Fallback**
- **¿Cuándo se usa?** Si ninguno de los anteriores funciona
- **IP por defecto:** `127.0.0.1` (localhost)

---

## 🚀 Uso Rápido

### Opción A: Sin hacer nada (Recomendado)
El sistema funciona automáticamente:
- ✅ Si tienes **Tailscale activo** → Usa `100.88.138.23`
- ✅ Si estás en **WiFi normal** → Detecta el gateway automáticamente
- ✅ **No necesitas agregar SSIDs manualmente**

### Opción B: Configurar desde la app
1. Abre `NetworkConfigActivity` en tu app
2. Verás el estado actual de la red
3. Puedes ajustar:
   - IP de Tailscale
   - IP manual personalizada
   - Activar/desactivar auto-detección
   - Preferir o no Tailscale

### Opción C: Configurar desde código
```java
// Configurar IP de Tailscale
NetworkDetector.setTailscaleServerIp(context, "100.88.138.23");

// Configurar IP manual
NetworkDetector.setManualIp(context, "192.168.1.86");

// Resetear a auto-detección
NetworkDetector.resetToAutoDetect(context);

// Verificar si Tailscale está activo
boolean isActive = NetworkDetector.isTailscaleActive(context);

// Obtener información completa de red
String info = NetworkDetector.getNetworkInfo(context);
Log.d("Network", info);
```

---

## 🔧 Casos de Uso Comunes

### ✅ Caso 1: Trabajar desde casa (WiFi)
**Situación:** Estás en casa con WiFi `192.168.1.x`, laptop en `192.168.1.86`

**Solución Automática:**
- El sistema detecta el gateway (`192.168.1.1` o `192.168.1.86`)
- Firebase Emulator funciona automáticamente
- **No necesitas configurar nada**

---

### ✅ Caso 2: Trabajar desde otro lugar con Tailscale
**Situación:** Estás en una cafetería/universidad, quieres conectarte a tu laptop en casa

**Solución:**
1. Activa Tailscale en tu teléfono y laptop
2. La app detecta automáticamente que Tailscale está activo
3. Se conecta a `100.88.138.23` (tu laptop)
4. **Funciona desde cualquier ubicación**

---

### ✅ Caso 3: Cambiar de red frecuentemente
**Situación:** Te mueves entre casa, trabajo, cafetería

**Solución:**
- **Con Tailscale:** Siempre funciona (recomendado)
- **Sin Tailscale:** El gateway se detecta automáticamente en cada red
- **No necesitas configurar cada red manualmente**

---

### ✅ Caso 4: Red con IP específica (caso especial)
**Situación:** Necesitas forzar una IP específica

**Solución:**
```java
// Opción 1: Desde código
NetworkDetector.setManualIp(context, "192.168.1.86");

// Opción 2: Desde la UI
// Abre NetworkConfigActivity → Configuración Manual → Ingresa IP → Guardar
```

---

## 📱 Cómo acceder a NetworkConfigActivity

### Opción 1: Agregar botón en tu app (Recomendado para debugging)
```java
// En tu MainActivity o SettingsActivity
Button btnNetworkConfig = findViewById(R.id.btnNetworkConfig);
btnNetworkConfig.setOnClickListener(v -> {
    Intent intent = new Intent(this, NetworkConfigActivity.class);
    startActivity(intent);
});
```

### Opción 2: Intent directo desde Logcat
```bash
adb shell am start -n com.mjc.mascotalink/.NetworkConfigActivity
```

---

## 🔍 Debugging

### Ver información de red en Logcat
```java
String info = NetworkDetector.getNetworkInfo(context);
Log.d("NetworkDebug", info);
```

**Salida esperada:**
```
=== INFORMACIÓN DE RED COMPLETA ===

--- Configuración ---
Auto-detección: Sí
Preferir Tailscale: Sí
IP Tailscale: 100.88.138.23
IP Manual: No configurada

--- Red Actual ---
SSID: Mi_WiFi
IP Local: 192.168.1.25
Gateway: 192.168.1.1
Tipo: WiFi
Tailscale: Activo (100.88.138.23)

--- Resultado ---
Host seleccionado: 100.88.138.23
```

---

## 🌐 Configuración de Tailscale

### Paso 1: Instalar Tailscale
- **Android:** [Google Play Store](https://play.google.com/store/apps/details?id=com.tailscale.ipn)
- **Laptop:** [tailscale.com/download](https://tailscale.com/download)

### Paso 2: Conectar ambos dispositivos
1. Inicia sesión con la misma cuenta en ambos dispositivos
2. En la laptop, ejecuta Firebase Emulator:
   ```bash
   firebase emulators:start
   ```
3. Verifica las IPs de Tailscale:
   - **Laptop:** `100.88.138.23` (donde corre Firebase)
   - **Teléfono:** `100.119.26.115` (tu dispositivo Android)

### Paso 3: Configurar la app (Automático)
La app ya tiene configurado `100.88.138.23` por defecto. Si tu IP de Tailscale es diferente:

```java
NetworkDetector.setTailscaleServerIp(context, "TU_IP_TAILSCALE");
```

---

## ⚙️ Configuración Avanzada

### Deshabilitar auto-detección (forzar manual)
```java
NetworkConfigManager config = NetworkDetector.getConfigManager(context);
config.setAutoDetectEnabled(false);
config.setManualIp("192.168.1.86");
```

### No preferir Tailscale (usar gateway primero)
```java
NetworkConfigManager config = NetworkDetector.getConfigManager(context);
config.setPreferTailscale(false);
```

### Resetear toda la configuración
```java
NetworkDetector.resetToAutoDetect(context);
```

---

## 🆘 Solución de Problemas

### ❌ Problema: "Sin conexión" en una red nueva
**Causa:** El gateway no se detectó correctamente

**Solución:**
1. Abre `NetworkConfigActivity`
2. Ve a "Configuración Manual"
3. Ingresa la IP de tu laptop manualmente
4. Guarda

---

### ❌ Problema: Tailscale no se detecta
**Causa:** Interfaz de red no reconocida

**Verificar:**
```java
boolean isActive = NetworkDetector.isTailscaleActive(context);
Log.d("Tailscale", "Activo: " + isActive);
```

**Solución:**
- Asegúrate de que Tailscale esté **conectado** (no solo instalado)
- Verifica que la IP empiece con `100.` en el rango CGNAT

---

### ❌ Problema: No funciona en ninguna red
**Solución de emergencia:**
```java
// Forzar IP manualmente
NetworkDetector.setManualIp(context, "IP_DE_TU_LAPTOP");

// Deshabilitar auto-detección si causa problemas
NetworkConfigManager config = NetworkDetector.getConfigManager(context);
config.setAutoDetectEnabled(false);
```

---

## 📊 Comparación: Antes vs Ahora

| Aspecto | ❌ Antes | ✅ Ahora |
|---------|---------|----------|
| **Configuración por red** | Manual, agregar SSID + IP | Automática |
| **Cambiar de red** | Agregar cada red nueva | Funciona automáticamente |
| **Tailscale** | No soportado | ✅ Soportado |
| **Trabajo remoto** | Solo en redes configuradas | ✅ Desde cualquier lugar |
| **Mantenimiento** | Alto (agregar cada red) | Mínimo |

---

## 🎉 Resumen

1. **Para uso normal:** No hagas nada, todo funciona automáticamente
2. **Para Tailscale:** Activa Tailscale y funciona desde cualquier lugar
3. **Para debugging:** Usa `NetworkConfigActivity` para ver/ajustar configuración
4. **Para casos especiales:** Configura IP manual desde código o UI

**¿Dudas?** Revisa los logs con `NetworkDetector.getNetworkInfo(context)`

---

**Fecha:** 2025-01-30
**Versión:** 1.0
**Autor:** Sistema de Red Híbrido - MascotaLink
