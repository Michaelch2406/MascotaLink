const admin = require("firebase-admin");
const { getFirestore, FieldValue, Timestamp } = require('firebase-admin/firestore');

// Configurar variables de entorno ANTES de inicializar Firebase
// Esto permite usar el emulador local durante desarrollo
if (process.env.NODE_ENV !== 'production') {
  process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || 'localhost:8080';
  process.env.FIREBASE_AUTH_EMULATOR_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || 'localhost:9099';
}

if (!admin.apps.length) {
    admin.initializeApp();
}

const db = admin.firestore();

module.exports = { admin, db, FieldValue, Timestamp };
