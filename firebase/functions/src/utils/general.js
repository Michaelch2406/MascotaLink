const getIdValue = (value) => (value && typeof value === "object" && value.id ? value.id : value);

// Helper para logs de debug (solo en desarrollo)
// Production detection: check NODE_ENV or if running in Cloud Functions (GCLOUD_PROJECT is automatically set)
const isProduction = process.env.NODE_ENV === 'production' ||
                     (process.env.GCLOUD_PROJECT && process.env.GCLOUD_PROJECT !== 'demo' && !process.env.FIRESTORE_EMULATOR_HOST);

function logDebug(message) {
  if (!isProduction) {
    console.log(message);
  }
}

/**
 * Calculates distance between two geographical points using the Haversine formula.
 * @param {number} lat1 Latitude of point 1
 * @param {number} lon1 Longitude of point 1
 * @param {number} lat2 Latitude of point 2
 * @param {number} lon2 Longitude of point 2
 * @returns {number} Distance in kilometers
 */
function calculateDistance(lat1, lon1, lat2, lon2) {
  const R = 6371; // Radius of Earth in kilometers
  const dLat = (lat2 - lat1) * (Math.PI / 180);
  const dLon = (lon2 - lon1) * (Math.PI / 180);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * (Math.PI / 180)) * Math.cos(lat2 * (Math.PI / 180)) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  const distance = R * c; // Distance in km
  return distance;
}

/**
 * Formatea nombres de mascotas para notificaciones
 * Soporta múltiples mascotas con formato legible
 * @param {Array<string>} mascotasNombres - Array de nombres de mascotas
 * @returns {string} Nombres formateados ("Max", "Max y Mia", "Max, Mia y 1 más")
 */
function formatearNombresMascotas(mascotasNombres) {
  if (!mascotasNombres || mascotasNombres.length === 0) {
    return "tus mascotas";
  }

  if (mascotasNombres.length === 1) {
    return mascotasNombres[0];  // "Max"
  }

  if (mascotasNombres.length === 2) {
    return mascotasNombres.join(" y ");  // "Max y Mia"
  }

  // 3 o más: "Max, Mia y 1 más"
  const primeros = mascotasNombres.slice(0, 2).join(", ");
  const restantes = mascotasNombres.length - 2;
  return `${primeros} y ${restantes} más`;
}

module.exports = { 
  getIdValue, 
  isProduction, 
  logDebug, 
  calculateDistance, 
  formatearNombresMascotas 
};
