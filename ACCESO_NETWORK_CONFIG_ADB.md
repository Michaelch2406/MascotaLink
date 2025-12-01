# 📱 Acceso a Configuración de Red por ADB

## 🎯 ¿Qué es esto?

Esta pantalla **NetworkConfigActivity** solo es accesible mediante **ADB (Android Debug Bridge)**. No aparece ningún botón en la app para usuarios finales, solo tú como desarrollador puedes acceder.

---

## 📋 **Requisitos Previos**

### **1. Habilitar Depuración USB en el teléfono**

**Pasos:**
1. Ve a **Ajustes** → **Acerca del teléfono**
2. Toca **7 veces** en "Número de compilación"
3. Se activarán las **Opciones de desarrollador**
4. Ve a **Ajustes** → **Sistema** → **Opciones de desarrollador**
5. Activa **Depuración USB**

---

### **2. Instalar ADB en tu PC (Si no lo tienes)**

#### **Opción A: Android Studio (Ya lo tienes)**
Si usas Android Studio, ADB ya está instalado en:
```
C:\Users\USUARIO\AppData\Local\Android\Sdk\platform-tools\
```

#### **Opción B: Descargar solo ADB (Más ligero)**
1. Descarga: https://developer.android.com/tools/releases/platform-tools
2. Extrae en: `C:\adb\`
3. Agrega `C:\adb\` al PATH de Windows

---

### **3. Conectar el dispositivo**

#### **Opción A: Por USB (Recomendado)**
1. Conecta el teléfono por USB
2. En el teléfono, aparecerá: **"¿Permitir depuración USB?"**
3. Marca **"Permitir siempre desde esta PC"**
4. Toca **Aceptar**

#### **Opción B: Por WiFi (Sin cable)**
1. Conecta el teléfono por USB primero
2. Ejecuta:
   ```cmd
   adb tcpip 5555
   ```
3. Desconecta el USB
4. En el teléfono, ve a **Ajustes** → **Acerca del teléfono** → **Estado**
5. Anota la **Dirección IP** (ej: `192.168.0.25`)
6. En la PC, ejecuta:
   ```cmd
   adb connect 192.168.0.25:5555
   ```
7. Ahora funciona por WiFi (sin cable)

---

## 🚀 **Método 1: Comando Manual (Rápido)**

### **Paso 1: Verificar que ADB reconoce el dispositivo**
```cmd
adb devices
```

**Salida esperada:**
```
List of devices attached
ABC123DEF456    device
```

Si dice **"unauthorized"**, acepta la depuración USB en el teléfono.

---

### **Paso 2: Abrir NetworkConfigActivity**
```cmd
adb shell am start -n com.mjc.mascotalink/.NetworkConfigActivity
```

**¡Listo!** La pantalla de configuración de red se abrirá automáticamente.

---

## 🎯 **Método 2: Script Automático (Más Fácil)**

He creado un script `.bat` para Windows que hace todo automáticamente.

### **Uso del Script:**

1. **Doble clic** en `abrir_network_config.bat`
2. La pantalla se abre automáticamente

**Eso es todo.**

---

## 📊 **Comandos Útiles de ADB**

### **Ver logs en tiempo real (Logcat):**
```cmd
adb logcat -s NetworkDetector MyApplication
```

### **Ver solo logs de red:**
```cmd
adb logcat | findstr "NetworkDetector"
```

### **Limpiar logs y ver solo nuevos:**
```cmd
adb logcat -c && adb logcat -s NetworkDetector
```

### **Reinstalar la app:**
```cmd
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### **Desinstalar la app:**
```cmd
adb uninstall com.mjc.mascotalink
```

### **Ver la actividad actual:**
```cmd
adb shell dumpsys activity activities | findstr "mFocusedActivity"
```

### **Cerrar la app:**
```cmd
adb shell am force-stop com.mjc.mascotalink
```

---

## 🔧 **Solución de Problemas**

### **❌ Problema: "adb no se reconoce como comando"**

**Causa:** ADB no está en el PATH

**Solución:**
1. Busca dónde está ADB:
   - Android Studio: `C:\Users\USUARIO\AppData\Local\Android\Sdk\platform-tools\`
   - Descarga manual: Donde lo extrajiste

2. Usa la ruta completa:
   ```cmd
   C:\Users\USUARIO\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am start -n com.mjc.mascotalink/.NetworkConfigActivity
   ```

3. O agrega al PATH:
   - Ve a **Panel de Control** → **Sistema** → **Configuración avanzada**
   - **Variables de entorno**
   - Edita **Path** → **Nuevo** → Agrega la ruta de ADB

---

### **❌ Problema: "error: no devices/emulators found"**

**Causa:** El dispositivo no está conectado o la depuración USB no está habilitada

**Solución:**
1. Conecta el USB
2. Habilita depuración USB
3. Ejecuta: `adb devices`
4. Acepta en el teléfono si aparece el diálogo

---

### **❌ Problema: "error: device unauthorized"**

**Causa:** No aceptaste la depuración USB en el teléfono

**Solución:**
1. En el teléfono, aparecerá: **"¿Permitir depuración USB?"**
2. Marca **"Permitir siempre"**
3. Toca **Aceptar**
4. Ejecuta de nuevo: `adb devices`

---

### **❌ Problema: "Activity not started, unable to resolve Intent"**

**Causa:** La app no está instalada o el nombre del paquete es incorrecto

**Solución:**
1. Verifica que la app esté instalada:
   ```cmd
   adb shell pm list packages | findstr mascotalink
   ```

2. Si no aparece, instálala:
   ```cmd
   adb install -r app-debug.apk
   ```

---

## 🎓 **Ejemplos de Uso Completo**

### **Ejemplo 1: Abrir NetworkConfig cuando la app está cerrada**
```cmd
adb shell am start -n com.mjc.mascotalink/.NetworkConfigActivity
```

La app se abre directamente en NetworkConfigActivity.

---

### **Ejemplo 2: Abrir NetworkConfig cuando la app ya está abierta**
```cmd
adb shell am start -n com.mjc.mascotalink/.NetworkConfigActivity
```

Cambia a NetworkConfigActivity sin cerrar la app.

---

### **Ejemplo 3: Ver logs mientras configuras**

**Terminal 1:**
```cmd
adb shell am start -n com.mjc.mascotalink/.NetworkConfigActivity
```

**Terminal 2:**
```cmd
adb logcat -s NetworkDetector NetworkConfigManager
```

Verás los logs en tiempo real mientras configuras.

---

### **Ejemplo 4: Workflow completo de debugging**
```cmd
# 1. Conectar dispositivo
adb devices

# 2. Limpiar logs anteriores
adb logcat -c

# 3. Abrir NetworkConfig
adb shell am start -n com.mjc.mascotalink/.NetworkConfigActivity

# 4. Ver logs en tiempo real
adb logcat -s NetworkDetector MyApplication

# 5. Cuando termines, cerrar la app
adb shell am force-stop com.mjc.mascotalink
```

---

## 📱 **Acceso Rápido desde Android Studio**

Si usas Android Studio, puedes crear una **Run Configuration**:

1. Ve a **Run** → **Edit Configurations**
2. Clic en **+** → **Android App**
3. Nombre: "Network Config"
4. Module: app
5. En **General** → **Launch Options**:
   - Launch: **Specified Activity**
   - Activity: `com.mjc.mascotalink.NetworkConfigActivity`
6. Clic en **Apply** → **OK**

Ahora puedes ejecutar **"Network Config"** desde el botón de play.

---

## 🔐 **Seguridad**

### **¿Es seguro?**
✅ **SÍ** - Solo funciona con depuración USB habilitada
✅ **SÍ** - El usuario debe aceptar manualmente la depuración
✅ **SÍ** - No aparece ningún botón en la app publicada

### **¿Los usuarios finales pueden acceder?**
❌ **NO** - Necesitan:
1. Habilitar opciones de desarrollador (tocar 7 veces)
2. Habilitar depuración USB
3. Conectar por USB a una PC con ADB
4. Saber el nombre exacto del paquete y Activity

**Es prácticamente imposible para un usuario normal.**

---

## 📝 **Resumen**

### **Acceso Simple (3 pasos):**
1. Conecta el teléfono por USB
2. Ejecuta: `adb shell am start -n com.mjc.mascotalink/.NetworkConfigActivity`
3. La pantalla se abre

### **Acceso con Script:**
1. Doble clic en `abrir_network_config.bat`
2. Listo

---

**¿Dudas?** Revisa la sección de **Solución de Problemas** arriba.
