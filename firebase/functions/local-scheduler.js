/**
 * Local Scheduler para Firebase Functions
 *
 * Este script simula Cloud Scheduler en el emulador local de Firebase.
 * Llama automáticamente a las funciones HTTP de notificaciones cada minuto.
 *
 * Uso:
 * 1. Inicia el emulador: firebase emulators:start
 * 2. En otra terminal: node local-scheduler.js
 */

const PROJECT_ID = "mascotalink-2d9da";
const REGION = "us-central1";
const EMULATOR_HOST = "127.0.0.1";
const EMULATOR_PORT = 5001;

// URLs de las funciones
const FUNCTIONS = {
  debugNotifyReady: `http://${EMULATOR_HOST}:${EMULATOR_PORT}/${PROJECT_ID}/${REGION}/debugNotifyReady`,
  debugNotifyDelayed: `http://${EMULATOR_HOST}:${EMULATOR_PORT}/${PROJECT_ID}/${REGION}/debugNotifyDelayed`,
  debugNotifyReminder5Min: `http://${EMULATOR_HOST}:${EMULATOR_PORT}/${PROJECT_ID}/${REGION}/debugNotifyReminder5Min`,
  debugNotifyOverdue: `http://${EMULATOR_HOST}:${EMULATOR_PORT}/${PROJECT_ID}/${REGION}/debugNotifyOverdue`
};

// Intervalo en milisegundos (60000ms = 1 minuto, igual que en producción)
const INTERVAL = 60000;

console.log("=".repeat(70));
console.log("Local Scheduler para Firebase Functions");
console.log("=".repeat(70));
console.log(`Proyecto: ${PROJECT_ID}`);
console.log(`Emulador: ${EMULATOR_HOST}:${EMULATOR_PORT}`);
console.log(`Intervalo: ${INTERVAL / 1000} segundos\n`);
console.log("Funciones a ejecutar:");
Object.keys(FUNCTIONS).forEach(name => {
  console.log(`  - ${name}`);
});
console.log("\nPresiona Ctrl+C para detener\n");
console.log("=".repeat(70));

/**
 * Llama a una función HTTP
 */
async function callFunction(name, url) {
  const startTime = Date.now();
  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json'
      }
    });

    const data = await response.json();
    const duration = Date.now() - startTime;

    if (response.ok) {
      const sent = data.result?.success || data.sent || 0;
      console.log(`✓ ${name}: OK (${duration}ms) - Enviadas: ${sent}`);
      if (data.message) {
        console.log(`  └─ ${data.message}`);
      }
    } else {
      console.error(`✗ ${name}: ERROR (${duration}ms)`);
      console.error(`  └─ ${data.error || 'Unknown error'}`);
    }
  } catch (error) {
    const duration = Date.now() - startTime;
    console.error(`✗ ${name}: FAILED (${duration}ms)`);
    console.error(`  └─ ${error.message}`);
    console.error(`  └─ ¿Está corriendo el emulador de Firebase?`);
  }
}

/**
 * Ejecuta todas las funciones
 */
async function runScheduledJobs() {
  const timestamp = new Date().toLocaleString('es-EC', {
    timeZone: 'America/Guayaquil',
    hour12: false
  });

  console.log(`\n[${timestamp}] Ejecutando funciones scheduled...`);
  console.log("-".repeat(70));

  // Ejecutar funciones en paralelo
  await Promise.all([
    callFunction('debugNotifyReady', FUNCTIONS.debugNotifyReady),
    callFunction('debugNotifyDelayed', FUNCTIONS.debugNotifyDelayed),
    callFunction('debugNotifyReminder5Min', FUNCTIONS.debugNotifyReminder5Min),
    callFunction('debugNotifyOverdue', FUNCTIONS.debugNotifyOverdue)
  ]);

  console.log("-".repeat(70));
}

/**
 * Inicia el scheduler
 */
function startScheduler() {
  // Ejecutar inmediatamente al iniciar
  runScheduledJobs().catch(error => {
    console.error("Error en ejecución inicial:", error);
  });

  // Programar ejecuciones periódicas
  setInterval(() => {
    runScheduledJobs().catch(error => {
      console.error("Error en ejecución periódica:", error);
    });
  }, INTERVAL);
}

// Manejo de señales de terminación
process.on('SIGINT', () => {
  console.log("\n\n" + "=".repeat(70));
  console.log("Scheduler detenido. ¡Hasta luego!");
  console.log("=".repeat(70));
  process.exit(0);
});

process.on('SIGTERM', () => {
  console.log("\nScheduler detenido por SIGTERM");
  process.exit(0);
});

// Iniciar el scheduler
startScheduler();
