# Firebase Functions - Admin AI Insights

This folder provides a production-ready callable function:

- `adminAiInsights`

## Features

- Input normalization and schema-safe payload handling.
- Gemini-based JSON insight generation.
- Firestore cache (`admin_ai_insights_cache`) with TTL.
- Rule-based fallback when AI API fails.
- Optional admin authorization gate.

## Environment variables

Set these before deploy (or use secrets injection in your CI/runtime):

- `GEMINI_API_KEY` (required for AI mode)
- `GEMINI_MODEL` (optional, default: `gemini-1.5-flash`)
- `ADMIN_AI_CACHE_TTL_MS` (optional, default: 900000)
- `ADMIN_AI_REQUIRE_ADMIN` (optional: `true` / `false`)

## Local setup

```powershell
Set-Location "D:\Mobile\functions"
npm install
npm run test:harness
```

## Emulator run

```powershell
Set-Location "D:\Mobile"
$env:GEMINI_API_KEY="your_api_key"
firebase emulators:start --only functions
```

## Deploy

```powershell
Set-Location "D:\Mobile"
$env:GEMINI_API_KEY="your_api_key"
firebase deploy --only functions:adminAiInsights --project <your-project-id>
```

## Callable contract

Request shape:

```json
{
  "rangeMonths": 6,
  "languageTag": "vi-VN",
  "statsSnapshot": {
    "usersByMonth": { "2026-01": 120 },
    "imageDownloadsByMonth": { "2026-01": 320 },
    "reviewScoreByMonth": { "2026-01": 4.3 },
    "aiRatio": { "withAI": 80, "withoutAI": 40 }
  }
}
```

Response shape:

```json
{
  "summary": "...",
  "insights": ["..."],
  "recommendations": [{ "title": "...", "action": "..." }],
  "confidence": 0.84,
  "meta": {
    "source": "gemini",
    "model": "gemini-1.5-flash",
    "cached": false
  }
}
```

