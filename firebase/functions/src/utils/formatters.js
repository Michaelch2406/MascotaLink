/**
 * Helper to format time from Firestore Timestamp with Ecuador timezone
 * @param {admin.firestore.Timestamp} timestamp - Firestore timestamp
 * @returns {string} Formatted time (HH:mm)
 */
const formatTimeEcuador = (timestamp) => {
  if (!timestamp) return "Hora desconocida";
  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
  return date.toLocaleTimeString('es-ES', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: "America/Guayaquil"
  });
};

/**
 * Helper to format date from Firestore Timestamp with Ecuador timezone
 * @param {admin.firestore.Timestamp} timestamp - Firestore timestamp
 * @returns {string} Formatted date (dd/mm/yyyy)
 */
const formatDateEcuador = (timestamp) => {
  if (!timestamp) return "Fecha desconocida";
  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
  return date.toLocaleDateString("es-ES", {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: "America/Guayaquil"
  });
};

module.exports = { formatTimeEcuador, formatDateEcuador };
