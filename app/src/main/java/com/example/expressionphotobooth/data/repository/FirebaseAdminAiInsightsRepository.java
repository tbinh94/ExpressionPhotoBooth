package com.example.expressionphotobooth.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.expressionphotobooth.BuildConfig;
import com.example.expressionphotobooth.R;
import com.example.expressionphotobooth.domain.model.AdminAiInsightRequest;
import com.example.expressionphotobooth.domain.model.AdminAiInsights;
import com.example.expressionphotobooth.domain.repository.AdminAiInsightsRepository;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.BlockThreshold;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.GenerationConfig;
import com.google.ai.client.generativeai.type.HarmCategory;
import com.google.ai.client.generativeai.type.RequestOptions;
import com.google.ai.client.generativeai.type.SafetySetting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirebaseAdminAiInsightsRepository implements AdminAiInsightsRepository {
    private static final String TAG = "AiInsightsRepo";
    
    // Sử dụng BuildConfig để bảo mật API Key
    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    private static final String MODEL_NAME = "gemini-flash-latest"; // Cập nhật bản latest chuẩn nhất
    private static final int MAX_RETRIES_PER_MODEL = 2;
    private static final long RETRY_DELAY_MS = 1800L;

    private final Context appContext;
    private final GenerativeModelFutures model;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public FirebaseAdminAiInsightsRepository(Context context) {
        this.appContext = context.getApplicationContext();

        GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
        configBuilder.temperature = 0.2f; 
        configBuilder.maxOutputTokens = 4096;
        GenerationConfig config = configBuilder.build();

        List<SafetySetting> safetySettings = new ArrayList<>();
        safetySettings.add(new SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH));
        safetySettings.add(new SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH));

        Content systemInstruction = new Content.Builder()
                .addText("Analyze photobooth stats. Output ONLY raw JSON matching structure. No markdown. Language: user's.")
                .build();

        GenerativeModel gm = new GenerativeModel(
                MODEL_NAME,
                GEMINI_API_KEY,
                config,
                safetySettings,
                new RequestOptions(),
                null,
                null,
                systemInstruction
        );
        this.model = GenerativeModelFutures.from(gm);
    }

    @Override
    public void fetchInsights(AdminAiInsightRequest request, Callback callback) {
        if (request == null) {
            callback.onError("Missing insight request payload.");
            return;
        }

        // Logic Prompt (Pipeline 3): Flexible, Realistic, JSON only, No markdown
        String prompt = buildPrompt(request);
        
        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        // Async Call (Pipeline 4)
        ListenableFuture<GenerateContentResponse> responseFuture = model.generateContent(content);

        Futures.addCallback(responseFuture, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String fullText = result.getText();
                mainHandler.post(() -> {
                    try {
                        // JSON Extraction (Pipeline 4)
                        AdminAiInsights parsed = parseGeminiResponse(fullText, request);
                        callback.onSuccess(parsed);
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error: " + e.getMessage());
                        callback.onSuccess(buildFallbackInsights(request));
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.e(TAG, "Gemini connection failed. Error: " + t.getMessage());
                if (t.getMessage() != null && t.getMessage().contains("API key not valid")) {
                    Log.e(TAG, "LỖI: API KEY CỦA BẠN ĐANG BỊ BÁO LÀ KHÔNG HỢP LỆ!");
                }
                mainHandler.post(() -> callback.onSuccess(buildFallbackInsights(request)));
            }
        }, executor);
    }

    private String buildPrompt(AdminAiInsightRequest request) {
        StringBuilder stats = new StringBuilder();
        Map<String, Object> snapshot = buildStatsSnapshot(request);
        return "Stats: " + gson.toJson(snapshot) + "\nLang: " + request.getLanguageTag();
    }

    private Map<String, Object> buildStatsSnapshot(AdminAiInsightRequest request) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("usersByMonth", request.getUsersByMonth());
        snapshot.put("imageDownloadsByMonth", request.getImageDownloadsByMonth());
        snapshot.put("reviewScoreByMonth", request.getReviewScoreByMonth());

        Map<String, Integer> aiRatio = new HashMap<>();
        aiRatio.put("withAI", request.getAiRegisteredUsers());
        aiRatio.put("withoutAI", request.getAiNotRegisteredUsers());
        snapshot.put("aiRatio", aiRatio);
        return snapshot;
    }

    private AdminAiInsights parseGeminiResponse(String rawText, AdminAiInsightRequest request) {
        String jsonString = extractJson(rawText);
        
        // Parse using JSONObject according to requirement
        try {
            JSONObject json = new JSONObject(jsonString);
            String summary = json.optString("summary", "");
            
            List<String> insights = new ArrayList<>();
            org.json.JSONArray insArray = json.optJSONArray("insights");
            if (insArray != null) {
                for (int i = 0; i < insArray.length(); i++) {
                    insights.add(insArray.getString(i));
                }
            }

            List<String> recommendations = new ArrayList<>();
            org.json.JSONArray recArray = json.optJSONArray("recommendations");
            if (recArray != null) {
                for (int i = 0; i < recArray.length(); i++) {
                    JSONObject obj = recArray.getJSONObject(i);
                    String title = obj.optString("title", "");
                    String action = obj.optString("action", "");
                    recommendations.add(title + ": " + action);
                }
            }

            double confidence = json.optDouble("confidence", 0.7);
            String source = isVietnamese(request.getLanguageTag()) ? "AI Dashboard (Chuyên sâu)" : "AI Dashboard (Deep Analysis)";

            return new AdminAiInsights(summary, insights, recommendations, true, source, confidence);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI JSON", e);
        }
    }

    // JSON Extraction (Pipeline 4)
    private String extractJson(String input) {
        if (input == null || input.isEmpty()) return "{}";
        // Lọc chuỗi JSON sạch, bỏ markdown blocks nếu AI cố tình trả về
        String cleaned = input.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1")
                             .replaceAll("(?s)```\\s*(.*?)\\s*```", "$1")
                             .trim();
        int start = cleaned.indexOf("{");
        int end = cleaned.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    private AdminAiInsights buildFallbackInsights(AdminAiInsightRequest request) {
        boolean vi = isVietnamese(request.getLanguageTag());
        String summary = vi
                ? "Báo cáo tóm tắt được tạo theo quy tắc nội bộ (AI không phản hồi)."
                : "Summary generated by internal rules (AI failed to respond).";

        List<String> insights = new ArrayList<>();
        insights.add(vi ? "Hệ thống: Vui lòng kiểm tra lại kết nối mạng hoặc API Key." : "System: Please check your network or API Key.");

        return new AdminAiInsights(summary, insights, new ArrayList<>(), false, "Dự phòng", 0.55d);
    }

    private boolean isVietnamese(String tag) {
        return tag != null && tag.toLowerCase().startsWith("vi");
    }
}
