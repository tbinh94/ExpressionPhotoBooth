# Admin AI Insights Integration

This project now includes an AI Insights card in `AdminOverviewFragment`.

## Client Flow

1. Dashboard loads stats from `AdminStatsRepository`.
2. Fragment prepares a snapshot for selected range (3M/6M/12M).
3. App calls Firebase Callable Function: `adminAiInsights`.
4. If function fails or times out, app uses local rule-based fallback insights.

## Payload Sent To Callable Function

```json
{
  "rangeMonths": 6,
  "languageTag": "vi-VN",
  "statsSnapshot": {
    "usersByMonth": { "2026-01": 120, "2026-02": 140 },
    "imageDownloadsByMonth": { "2026-01": 320, "2026-02": 310 },
    "reviewScoreByMonth": { "2026-01": 4.3, "2026-02": 4.4 },
    "aiRatio": { "withAI": 80, "withoutAI": 40 }
  }
}
```

## Expected Function Response

```json
{
  "summary": "Users grow steadily while downloads fluctuate.",
  "insights": [
    "New accounts increased 16.7% month-over-month.",
    "Image downloads fell 3.1% in latest month.",
    "Review score remains above 4.0."
  ],
  "recommendations": [
    {
      "title": "Promote top frames",
      "action": "Pin high-performing frames on Home this week"
    }
  ],
  "confidence": 0.84
}
```

## Minimal Firebase Function Prompt

- Role: Product analytics assistant for mobile dashboard.
- Requirements:
  - Use only provided numbers.
  - Return JSON only.
  - Max 3 insights and 2 recommendations.
  - Include confidence in `[0,1]`.

## Notes

- API key must stay in backend only.
- Android app does not contain any model key.
- Current implementation already handles fallback to internal insights.

## Backend Setup (Firebase Functions)

1. Install dependencies:

```powershell
Set-Location "D:\Mobile\functions"
npm install
```

2. Run local harness (no cloud call, quick validation):

```powershell
Set-Location "D:\Mobile\functions"
npm run test:harness
```

3. Start local Functions emulator:

```powershell
Set-Location "D:\Mobile"
$env:GEMINI_API_KEY="your_api_key"
firebase emulators:start --only functions
```

4. Deploy callable function:

```powershell
Set-Location "D:\Mobile"
$env:GEMINI_API_KEY="your_api_key"
firebase deploy --only functions:adminAiInsights --project <your-project-id>
```

## Backend Behavior Summary

- Function name: `adminAiInsights` (callable).
- Validates and normalizes incoming payload.
- Builds a stable cache key from payload hash.
- Reads cache from Firestore collection `admin_ai_insights_cache`.
- Calls Gemini only when cache is missing/expired.
- Normalizes AI output to strict shape used by Android client.
- Falls back to rule-based insights if model is unavailable.

## Optional Runtime Flags

- `GEMINI_MODEL` (default: `gemini-1.5-flash`)
- `ADMIN_AI_CACHE_TTL_MS` (default: 900000)
- `ADMIN_AI_REQUIRE_ADMIN` (`true` to enforce custom admin claim)


