// Config & Init (Implicitly happens when requiring modules)
require('./src/config/firebase');

// HTTP Functions
const { recomendarPaseadores } = require('./src/http/recommendations');
const { migrarPaseadoresSearch, recalcularContadores, migrarCampoEsPrimerDia, migrarResenasADesnormalizado } = require('./src/http/migrations');
const { websocket } = require('./src/http/websocket-func');
const { debugNotifyReady, debugNotifyOverdue, debugNotifyDelayed, debugNotifyReminder5Min } = require('./src/http/debug');

// Triggers
const { onUsuarioWrite, onPaseadorWrite, notifyNearbyWalkerAvailable } = require('./src/triggers/users');
const { validatePaymentOnCreate, onPaymentConfirmed } = require('./src/triggers/payments');
const { sendChatNotification } = require('./src/triggers/chats');
const { 
  onNewReservation, 
  onReservationAccepted, 
  onWalkStarted, 
  onReservationCancelled, 
  onRequestCancellation, 
  onPaseoUpdate,
  onReservaStatusChange
} = require('./src/triggers/reservations');
const { desnormalizarResenasDuenos, desnormalizarResenasPaseadores } = require('./src/triggers/reviews');

// Scheduled Tasks
const { cleanupOldMessages } = require('./src/scheduled/chats-cleanup');
const { 
  checkWalkReminders, 
  transitionToInCourse, 
  sendReminder15MinBefore, 
  sendReminder5MinBefore, 
  notifyOverdueWalks, 
  notifyWalkReadyWindow, 
  notifyDelayedWalks 
} = require('./src/scheduled/walk-scheduler');
const { notifyNearbyWalkersScheduled } = require('./src/scheduled/marketing');
const { checkReservationTimeout } = require('./src/scheduled/timeout');

// Exports
exports.recomendarPaseadores = recomendarPaseadores;
exports.migrarPaseadoresSearch = migrarPaseadoresSearch;
exports.recalcularContadores = recalcularContadores;
exports.migrarCampoEsPrimerDia = migrarCampoEsPrimerDia;
exports.migrarResenasADesnormalizado = migrarResenasADesnormalizado;
exports.websocket = websocket;

// Debug Exports (Para local scheduler)
exports.debugNotifyReady = debugNotifyReady;
exports.debugNotifyOverdue = debugNotifyOverdue;
exports.debugNotifyDelayed = debugNotifyDelayed;
exports.debugNotifyReminder5Min = debugNotifyReminder5Min;

// Trigger Exports
exports.onUsuarioWrite = onUsuarioWrite;
exports.onPaseadorWrite = onPaseadorWrite;
exports.notifyNearbyWalkerAvailable = notifyNearbyWalkerAvailable;
exports.validatePaymentOnCreate = validatePaymentOnCreate;
exports.onPaymentConfirmed = onPaymentConfirmed;
exports.sendChatNotification = sendChatNotification;
exports.onNewReservation = onNewReservation;
exports.onReservationAccepted = onReservationAccepted;
exports.onWalkStarted = onWalkStarted;
exports.onReservationCancelled = onReservationCancelled;
exports.onRequestCancellation = onRequestCancellation;
exports.onPaseoUpdate = onPaseoUpdate;
exports.onReservaStatusChange = onReservaStatusChange;
exports.desnormalizarResenasDuenos = desnormalizarResenasDuenos;
exports.desnormalizarResenasPaseadores = desnormalizarResenasPaseadores;

// Scheduled Exports
exports.cleanupOldMessages = cleanupOldMessages;
exports.checkWalkReminders = checkWalkReminders;
exports.transitionToInCourse = transitionToInCourse;
exports.sendReminder15MinBefore = sendReminder15MinBefore;
exports.sendReminder5MinBefore = sendReminder5MinBefore;
exports.notifyOverdueWalks = notifyOverdueWalks;
exports.notifyWalkReadyWindow = notifyWalkReadyWindow;
exports.notifyDelayedWalks = notifyDelayedWalks;
exports.notifyNearbyWalkersScheduled = notifyNearbyWalkersScheduled;
exports.checkReservationTimeout = checkReservationTimeout;
