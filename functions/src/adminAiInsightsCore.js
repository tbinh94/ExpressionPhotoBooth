const crypto = require("node:crypto");

const DEFAULT_RANGE_MONTHS = 6;

function clampNumber(value, min, max, fallback) {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return fallback;
  }
  return Math.max(min, Math.min(max, number));
}

function normalizeLanguageTag(languageTag) {
  const text = String(languageTag || "en-US").trim();
  return text.length > 0 ? text : "en-US";
}

function isVietnamese(languageTag) {
  return normalizeLanguageTag(languageTag).toLowerCase().startsWith("vi");
}

function ensureMonthNumberMap(input) {
  const output = {};
  if (!input || typeof input !== "object") {
    return output;
  }

  const sortedKeys = Object.keys(input).sort();
  for (const key of sortedKeys) {
    const value = Number(input[key]);
    output[key] = Number.isFinite(value) ? value : 0;
  }
  return output;
}

function validateRequestPayload(raw) {
  const data = raw && typeof raw === "object" ? raw : {};
  const statsSnapshot = data.statsSnapshot && typeof data.statsSnapshot === "object"
    ? data.statsSnapshot
    : {};

  return {
    rangeMonths: clampNumber(data.rangeMonths, 1, 24, DEFAULT_RANGE_MONTHS),
    languageTag: normalizeLanguageTag(data.languageTag),
    statsSnapshot: {
      usersByMonth: ensureMonthNumberMap(statsSnapshot.usersByMonth),
      imageDownloadsByMonth: ensureMonthNumberMap(statsSnapshot.imageDownloadsByMonth),
      reviewScoreByMonth: ensureMonthNumberMap(statsSnapshot.reviewScoreByMonth),
      aiRatio: {
        withAI: clampNumber(
          statsSnapshot.aiRatio && statsSnapshot.aiRatio.withAI,
          0,
          Number.MAX_SAFE_INTEGER,
          0
        ),
        withoutAI: clampNumber(
          statsSnapshot.aiRatio && statsSnapshot.aiRatio.withoutAI,
          0,
          Number.MAX_SAFE_INTEGER,
          0
        )
      }
    }
  };
}

function stableStringify(value) {
  if (value === null || typeof value !== "object") {
    return JSON.stringify(value);
  }

  if (Array.isArray(value)) {
    return `[${value.map((item) => stableStringify(item)).join(",")}]`;
  }

  const keys = Object.keys(value).sort();
  const serialized = keys.map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`);
  return `{${serialized.join(",")}}`;
}

function buildCacheKey(payload) {
  const raw = stableStringify(payload);
  return crypto.createHash("sha256").update(raw).digest("hex");
}

function buildPrompt(payload) {
  return [
    "You are a product analytics assistant for a mobile photobooth app.",
    "Analyze input metrics and return JSON only.",
    "Rules:",
    "1) Use only provided numbers.",
    "2) Output keys exactly: summary, insights, recommendations, confidence.",
    "3) insights: array of max 3 strings with concrete numbers.",
    "4) recommendations: array of max 2 objects with title and action.",
    "5) confidence must be in range [0,1].",
    "6) Language must follow languageTag in the input.",
    "Input JSON:",
    JSON.stringify(payload)
  ].join("\n");
}

function extractFirstJsonObject(rawText) {
  if (!rawText || typeof rawText !== "string") {
    return null;
  }

  const text = rawText.trim();
  if (text.startsWith("{")) {
    return text;
  }

  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) {
    return null;
  }

  return text.slice(start, end + 1);
}

function toStringArray(input, limit) {
  if (!Array.isArray(input)) {
    return [];
  }

  const output = [];
  for (const item of input) {
    if (typeof item === "string") {
      const trimmed = item.trim();
      if (trimmed) {
        output.push(trimmed);
      }
    }
    if (output.length >= limit) {
      break;
    }
  }
  return output;
}

function normalizeRecommendations(input, languageTag) {
  const vi = isVietnamese(languageTag);
  const fallbackTitle = vi ? "Toi uu tai nghiem" : "Optimize experience";
  const fallbackAction = vi ? "Theo doi xu huong 2 tuan lien tiep" : "Monitor trends for two weeks";

  if (!Array.isArray(input)) {
    return [{ title: fallbackTitle, action: fallbackAction }];
  }

  const output = [];
  for (const item of input) {
    if (!item || typeof item !== "object") {
      continue;
    }

    const title = String(item.title || "").trim();
    const action = String(item.action || "").trim();
    const expectedImpact = String(item.expectedImpact || "").trim();

    if (!title && !action) {
      continue;
    }

    output.push({
      title: title || fallbackTitle,
      action: action || fallbackAction,
      expectedImpact
    });

    if (output.length >= 2) {
      break;
    }
  }

  return output.length > 0 ? output : [{ title: fallbackTitle, action: fallbackAction }];
}

function normalizeModelResponse(rawModelResponse, languageTag) {
  const vi = isVietnamese(languageTag);
  const fallbackSummary = vi
    ? "Khong nhan duoc phan hoi AI hop le, da chuyen sang che do du phong."
    : "AI response is unavailable, switched to fallback mode.";

  const source = rawModelResponse && typeof rawModelResponse === "object" ? rawModelResponse : {};

  const summary = String(source.summary || "").trim() || fallbackSummary;
  const insights = toStringArray(source.insights, 3);
  const recommendations = normalizeRecommendations(source.recommendations, languageTag);
  const confidence = clampNumber(source.confidence, 0, 1, 0.7);

  return {
    summary,
    insights: insights.length > 0
      ? insights
      : [
          vi
            ? "Chua du du lieu de tao nhan dinh xu huong tin cay."
            : "Not enough data to generate a reliable trend insight."
        ],
    recommendations,
    confidence
  };
}

function getLastTwoValues(mapObject) {
  const keys = Object.keys(mapObject || {}).sort();
  if (keys.length < 2) {
    return null;
  }

  const prevKey = keys[keys.length - 2];
  const currKey = keys[keys.length - 1];
  return {
    previousLabel: prevKey,
    currentLabel: currKey,
    previous: Number(mapObject[prevKey]) || 0,
    current: Number(mapObject[currKey]) || 0
  };
}

function trendLine(labelVi, labelEn, mapObject, languageTag, isScore) {
  const vi = isVietnamese(languageTag);
  const data = getLastTwoValues(mapObject);

  if (!data) {
    return vi
      ? `${labelVi}: chua du du lieu de so sanh thang gan nhat.`
      : `${labelEn}: not enough monthly points for latest comparison.`;
  }

  const delta = data.current - data.previous;
  if (isScore) {
    return vi
      ? `${labelVi}: thay doi ${delta.toFixed(2)} diem (${data.previous.toFixed(2)} -> ${data.current.toFixed(2)}).`
      : `${labelEn}: changed ${delta.toFixed(2)} points (${data.previous.toFixed(2)} -> ${data.current.toFixed(2)}).`;
  }

  const pct = data.previous === 0 ? 0 : (delta * 100) / data.previous;
  return vi
    ? `${labelVi}: ${delta >= 0 ? "tang" : "giam"} ${Math.abs(pct).toFixed(1)}% (${data.previous} -> ${data.current}).`
    : `${labelEn}: ${delta >= 0 ? "up" : "down"} ${Math.abs(pct).toFixed(1)}% (${data.previous} -> ${data.current}).`;
}

function generateFallbackInsights(payload, reason) {
  const languageTag = normalizeLanguageTag(payload && payload.languageTag);
  const vi = isVietnamese(languageTag);
  const snapshot = payload && payload.statsSnapshot ? payload.statsSnapshot : {};

  const totalAi = Number(snapshot.aiRatio && snapshot.aiRatio.withAI || 0)
    + Number(snapshot.aiRatio && snapshot.aiRatio.withoutAI || 0);
  const aiPct = totalAi > 0
    ? (Number(snapshot.aiRatio.withAI || 0) * 100) / totalAi
    : 0;

  return {
    summary: vi
      ? `Phan tich du phong dang duoc su dung (${reason || "NO_MODEL"}).`
      : `Fallback analytics is active (${reason || "NO_MODEL"}).`,
    insights: [
      trendLine("Tai khoan moi", "New accounts", snapshot.usersByMonth, languageTag, false),
      trendLine("Luot tai anh", "Image downloads", snapshot.imageDownloadsByMonth, languageTag, false),
      trendLine("Diem danh gia", "Review score", snapshot.reviewScoreByMonth, languageTag, true)
    ],
    recommendations: [
      {
        title: vi ? "Toi uu frame noi bat" : "Promote top frames",
        action: vi
          ? "Dat cac frame co hieu suat cao len dau Home trong 7 ngay."
          : "Pin top-performing frames on Home for the next 7 days.",
        expectedImpact: vi ? "Ky vong cai thien luot tai anh" : "Expected to improve image downloads"
      },
      {
        title: vi ? "Theo doi review tieu cuc" : "Track low reviews",
        action: vi
          ? "Tong hop review <3 sao moi tuan va phan hoi som."
          : "Review low-score feedback weekly and respond early.",
        expectedImpact: vi ? "On dinh diem danh gia trung binh" : "Stabilize average review score"
      }
    ],
    confidence: 0.55
  };
}

module.exports = {
  buildCacheKey,
  buildPrompt,
  extractFirstJsonObject,
  generateFallbackInsights,
  normalizeModelResponse,
  normalizeLanguageTag,
  validateRequestPayload
};

