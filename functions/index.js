const { onCall, HttpsError } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { GoogleGenAI } = require("@google/genai");

const {
  buildCacheKey,
  buildPrompt,
  extractFirstJsonObject,
  generateFallbackInsights,
  normalizeModelResponse,
  validateRequestPayload
} = require("./src/adminAiInsightsCore");

admin.initializeApp();
const firestore = getFirestore();

const CACHE_COLLECTION = "admin_ai_insights_cache";
const CACHE_TTL_MS = Number(process.env.ADMIN_AI_CACHE_TTL_MS || 15 * 60 * 1000);
const GEMINI_MODEL = process.env.GEMINI_MODEL || "gemini-2.0-flash";
const REQUIRE_ADMIN = process.env.ADMIN_AI_REQUIRE_ADMIN === "true";
const DEFAULT_MODEL_CANDIDATES = [
  "gemini-2.0-flash",
  "gemini-2.0-flash-lite",
  "gemini-1.5-flash-latest",
  "gemini-1.5-flash"
];

function extractErrorReason(error) {
  if (!error) {
    return "UNKNOWN_ERROR";
  }

  const message = String(error.message || "").trim();
  if (!message) {
    return "UNKNOWN_ERROR";
  }

  return message.length > 220 ? `${message.substring(0, 220)}...` : message;
}

function ensureAdminAccess(request) {
  if (!REQUIRE_ADMIN) {
    return;
  }

  const auth = request.auth;
  if (!auth || !auth.token) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }

  if (!auth.token.admin) {
    throw new HttpsError("permission-denied", "Admin access is required.");
  }
}

async function readValidCache(cacheKey) {
  const docRef = firestore.collection(CACHE_COLLECTION).doc(cacheKey);
  const snapshot = await docRef.get();

  if (!snapshot.exists) {
    return null;
  }

  const data = snapshot.data() || {};
  const expiresAtMillis = Number(data.expiresAtMillis || 0);
  if (expiresAtMillis <= Date.now()) {
    return null;
  }

  const response = data.response;
  if (!response || typeof response !== "object") {
    return null;
  }

  return {
    ...response,
    meta: {
      ...(response.meta || {}),
      cached: true,
      cacheKey,
      cacheExpiresAtMillis: expiresAtMillis
    }
  };
}

async function writeCache(cacheKey, response) {
  const expiresAtMillis = Date.now() + CACHE_TTL_MS;
  await firestore.collection(CACHE_COLLECTION).doc(cacheKey).set(
    {
      response,
      expiresAtMillis,
      updatedAt: FieldValue.serverTimestamp()
    },
    { merge: true }
  );
}

async function callGemini(payload) {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new Error("Missing GEMINI_API_KEY.");
  }

  const ai = new GoogleGenAI({ apiKey });
  const prompt = buildPrompt(payload);

  const modelCandidates = Array.from(new Set([
    GEMINI_MODEL,
    ...DEFAULT_MODEL_CANDIDATES
  ])).filter(Boolean);

  let lastError = null;
  for (const modelName of modelCandidates) {
    try {
      const result = await ai.models.generateContent({
        model: modelName,
        contents: prompt,
        config: {
          temperature: 0.2,
          topP: 0.9,
          maxOutputTokens: 900,
          responseMimeType: "application/json"
        }
      });

      let responseText = "";
      if (result && typeof result.text === "function") {
        responseText = result.text();
      } else if (result && typeof result.text === "string") {
        responseText = result.text;
      } else if (result && typeof result.outputText === "string") {
        responseText = result.outputText;
      }

      const jsonText = extractFirstJsonObject(responseText);
      if (!jsonText) {
        throw new Error("Model did not return JSON content.");
      }

      const parsed = JSON.parse(jsonText);
      return {
        insights: normalizeModelResponse(parsed, payload.languageTag),
        modelUsed: modelName
      };
    } catch (error) {
      lastError = error;
      logger.warn(`adminAiInsights model candidate failed: ${modelName}`);
    }
  }

  throw lastError || new Error("No Gemini model candidate succeeded.");
}

exports.adminAiInsights = onCall(
  {
    region: "asia-southeast1",
    timeoutSeconds: 30,
    memory: "256MiB"
  },
  async (request) => {
    ensureAdminAccess(request);

    const payload = validateRequestPayload(request.data);
    const cacheKey = buildCacheKey(payload);

    let cached = null;
    try {
      cached = await readValidCache(cacheKey);
    } catch (error) {
      logger.warn("adminAiInsights cache read failed, continuing without cache", error);
    }
    if (cached) {
      return cached;
    }

    let response;
    try {
      const aiResult = await callGemini(payload);
      response = aiResult.insights;
      response.meta = {
        source: "gemini",
        model: aiResult.modelUsed || GEMINI_MODEL,
        cached: false
      };
    } catch (error) {
      const reason = extractErrorReason(error);
      logger.error("adminAiInsights model error", error);
      response = generateFallbackInsights(payload, "MODEL_ERROR");
      response.meta = {
        source: "fallback",
        model: "rule-based",
        cached: false,
        reason
      };
    }

    try {
      await writeCache(cacheKey, response);
    } catch (error) {
      logger.warn("adminAiInsights cache write failed, response still returned", error);
    }
    return response;
  }
);

