const {
  buildPrompt,
  generateFallbackInsights,
  normalizeModelResponse,
  validateRequestPayload
} = require("../src/adminAiInsightsCore");

function runHarness() {
  const requestPayload = validateRequestPayload({
    rangeMonths: 6,
    languageTag: "vi-VN",
    statsSnapshot: {
      usersByMonth: { "2026-01": 120, "2026-02": 140, "2026-03": 168 },
      imageDownloadsByMonth: { "2026-01": 310, "2026-02": 360, "2026-03": 330 },
      reviewScoreByMonth: { "2026-01": 4.2, "2026-02": 4.4, "2026-03": 4.3 },
      aiRatio: { withAI: 82, withoutAI: 38 }
    }
  });

  const fakeModelResponse = {
    summary: "Nguoi dung tang on dinh, luot tai anh bien dong nhe.",
    insights: [
      "Tai khoan moi tang 20.0% (140 -> 168).",
      "Luot tai anh giam 8.3% (360 -> 330).",
      "Diem danh gia duy tri tren 4.0."
    ],
    recommendations: [
      {
        title: "Day frame theo mua",
        action: "Ghim frame top tai Home trong 7 ngay",
        expectedImpact: "Tang luot tai"
      }
    ],
    confidence: 0.86
  };

  const normalized = normalizeModelResponse(fakeModelResponse, requestPayload.languageTag);
  const fallback = generateFallbackInsights(requestPayload, "HARNESS_TEST");

  console.log("=== Prompt Preview ===");
  console.log(buildPrompt(requestPayload));

  console.log("\n=== Normalized Model Output ===");
  console.log(JSON.stringify(normalized, null, 2));

  console.log("\n=== Fallback Output ===");
  console.log(JSON.stringify(fallback, null, 2));
}

runHarness();

