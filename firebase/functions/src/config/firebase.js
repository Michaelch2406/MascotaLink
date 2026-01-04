const admin = require("firebase-admin");
const { getFirestore, FieldValue, Timestamp } = require('firebase-admin/firestore');

if (!admin.apps.length) {
    admin.initializeApp();
}

const db = admin.firestore();

module.exports = { admin, db, FieldValue, Timestamp };
