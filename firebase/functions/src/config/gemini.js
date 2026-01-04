const { defineString } = require("firebase-functions/params");
const { GoogleGenerativeAI } = require("@google/generative-ai");

// Define Gemini API Key usando el nuevo sistema de parámetros
const geminiApiKey = defineString("GEMINI_API_KEY");

let genAI = null;
let model = null;

function initializeGemini() {
  if (model) return model; // Ya inicializado

  const apiKey = geminiApiKey.value();

  if (!apiKey) {
    console.error("GEMINI_API_KEY is not set. Gemini AI features will be disabled.");
    return null;
  }

  genAI = new GoogleGenerativeAI(apiKey);
  model = genAI.getGenerativeModel({
    model: "gemini-2.5-flash-lite",
    generationConfig: {
      temperature: 0.1,
      topP: 0.95,
      topK: 40,
      maxOutputTokens: 2048,
    }
  });
  console.log("Gemini AI initialized successfully with model: gemini-2.5-flash-lite (temperature: 0.1)");
  return model;
}

function getModel() {
    return model || initializeGemini();
}

module.exports = { initializeGemini, getModel, geminiApiKey };
