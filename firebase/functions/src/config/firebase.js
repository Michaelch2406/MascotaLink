const admin = require("firebase-admin");
const { getFirestore, FieldValue, Timestamp, setLogFunction } = require('firebase-admin/firestore');

// ============================================================================
// CONFIGURACIÓN DEL EMULADOR (Desarrollo)
// ============================================================================

// Determinar si estamos en modo desarrollo
// NODE_ENV no debe estar seteado a 'production' para usar emuladores
const isDevelopment = process.env.NODE_ENV !== 'production';

if (isDevelopment) {
  // Configurar hosts de TODOS los emuladores con valores por defecto
  process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || 'localhost:8080';
  process.env.FIREBASE_AUTH_EMULATOR_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || 'localhost:9099';
  process.env.FIREBASE_STORAGE_EMULATOR_HOST = process.env.FIREBASE_STORAGE_EMULATOR_HOST || 'localhost:9199';
  process.env.FIREBASE_DATABASE_EMULATOR_HOST = process.env.FIREBASE_DATABASE_EMULATOR_HOST || 'localhost:9000';
  process.env.PUBSUB_EMULATOR_HOST = process.env.PUBSUB_EMULATOR_HOST || 'localhost:8085';
  // Para Cloud Functions: se usa cuando se ejecutan funciones en el emulador
  // process.env.FIREBASE_FUNCTIONS_HOST = process.env.FIREBASE_FUNCTIONS_HOST || 'localhost:5001';

  console.log('🔧 Modo desarrollo detectado (NODE_ENV !== "production")');
  console.log('📡 Configurando Firebase Emulators:');
  console.log(`   • Firestore: ${process.env.FIRESTORE_EMULATOR_HOST}`);
  console.log(`   • Auth: ${process.env.FIREBASE_AUTH_EMULATOR_HOST}`);
  console.log(`   • Storage: ${process.env.FIREBASE_STORAGE_EMULATOR_HOST}`);
  console.log(`   • Realtime DB: ${process.env.FIREBASE_DATABASE_EMULATOR_HOST}`);
  console.log(`   • Pub/Sub: ${process.env.PUBSUB_EMULATOR_HOST}`);
}

// ============================================================================
// VERIFICACIÓN DE SEGURIDAD: Prevenir modo emulador en producción
// ============================================================================
const emulatorVars = [
  'FIRESTORE_EMULATOR_HOST',
  'FIREBASE_AUTH_EMULATOR_HOST',
  'FIREBASE_STORAGE_EMULATOR_HOST',
  'FIREBASE_DATABASE_EMULATOR_HOST',
  'PUBSUB_EMULATOR_HOST'
];

const hasEmulatorVars = emulatorVars.some(varName => process.env[varName]);

if (hasEmulatorVars && process.env.NODE_ENV === 'production') {
  console.error('❌ FATAL: Modo emulador detectado en PRODUCCIÓN. Esto no es seguro.');
  console.error('   Variables de emulador detectadas:');
  emulatorVars.forEach(varName => {
    if (process.env[varName]) {
      console.error(`     • ${varName}=${process.env[varName]}`);
    }
  });
  console.error('   Remueve estas variables del ambiente de producción.');
  throw new Error('Modo emulador detectado en ambiente de producción. Abortando inicialización.');
}

// ============================================================================
// INICIALIZACIÓN DE FIREBASE
// ============================================================================
if (!admin.apps.length) {
    admin.initializeApp();
}

const db = admin.firestore();

// ============================================================================
// CONFIGURACIÓN DE FIRESTORE PARA MEJOR CONECTIVIDAD
// ============================================================================

// Configurar opciones para mejor confiabilidad y reintentos
const settings = {
  projectId: admin.apps[0].options?.projectId || process.env.GCLOUD_PROJECT || 'mascotalink-2d9da',
  // Límites aumentados para operaciones en lote
  maxConcurrentLimitedOperations: 100,
  // Configuración de conexión gRPC para mejor estabilidad
  ignoreUndefinedProperties: false,
};

// Aplicar configuración de Firestore con reintentos
try {
  db.settings(settings);
  if (process.env.NODE_ENV !== 'production') {
    console.log('✅ Firestore configurado con opciones optimizadas');
  }
} catch (error) {
  console.warn('⚠️  No se pudieron aplicar configuraciones de Firestore:', error.message);
  // Continuar de todas formas - Firestore usará valores por defecto
}

// ============================================================================
// FUNCIÓN DE LOGGING (Deshabilitada en producción por rendimiento)
// ============================================================================
if (process.env.NODE_ENV !== 'production' && process.env.DEBUG_FIRESTORE === 'true') {
  setLogFunction(console.log);
  console.log('🔍 Logging de debug de Firestore habilitado');
}

module.exports = { admin, db, FieldValue, Timestamp };
