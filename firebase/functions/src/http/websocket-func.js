const express = require("express");
const { createServer } = require("http");
const { Server } = require("socket.io");
const cors = require("cors");
const { onRequest } = require("firebase-functions/v2/https");
const { db } = require('../config/firebase');
// Adjust path to point to the file in root of functions dir
const { initializeSocketServer } = require("../../websocket"); 

const app = express();
app.use(cors({ origin: "*" }));

// Endpoint de salud
app.get("/health", (req, res) => {
  res.json({ status: "OK", service: "MascotaLink WebSocket Server" });
});

const httpServer = createServer(app);
const io = new Server(httpServer, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"],
  },
  pingTimeout: 60000,
  pingInterval: 25000,
  transports: ["websocket", "polling"],
});

// Inicializar servidor WebSocket
initializeSocketServer(io, db);

// Exportar como Cloud Function
exports.websocket = onRequest((req, res) => {
  httpServer.emit("request", req, res);
});
