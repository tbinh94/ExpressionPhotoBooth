package com.example.expressionphotobooth.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.expressionphotobooth.BuildConfig;
import com.example.expressionphotobooth.domain.model.AdminAiChatRequest;
import com.example.expressionphotobooth.domain.model.AdminAiChatResponse;
import com.example.expressionphotobooth.domain.repository.AdminAiChatRepository;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.BlockThreshold;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.GenerationConfig;
import com.google.ai.client.generativeai.type.HarmCategory;
import com.google.ai.client.generativeai.type.SafetySetting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirebaseAdminAiChatRepository implements AdminAiChatRepository {
    private static final String TAG = "AiChatRepo";
    private static final String MODEL_NAME = "gemini-flash-latest"; // Dùng bản Flash cho tốc độ phản hồi nhanh

    private final GenerativeModelFutures model;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public FirebaseAdminAiChatRepository() {
        // Lấy API Key từ BuildConfig (đã được cấu hình đọc từ local.properties)
        String apiKey = BuildConfig.GEMINI_API_KEY;

        GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
        configBuilder.temperature = 0.7f;
        configBuilder.maxOutputTokens = 150; // Giới hạn phản hồi ngắn để tiết kiệm token
        GenerationConfig config = configBuilder.build();

        List<SafetySetting> safetySettings = new ArrayList<>();
        safetySettings.add(new SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH));
        safetySettings.add(new SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH));

        GenerativeModel gm = new GenerativeModel(MODEL_NAME, apiKey, config, safetySettings);
        this.model = GenerativeModelFutures.from(gm);
    }

    @Override
    public void sendQuery(AdminAiChatRequest request, Callback callback) {
        String prompt = buildPrompt(request);
        
        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        ListenableFuture<GenerateContentResponse> responseFuture = model.generateContent(content);

        Futures.addCallback(responseFuture, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String fullText = result.getText();
                mainHandler.post(() -> {
                    if (fullText != null && !fullText.isEmpty()) {
                        callback.onSuccess(new AdminAiChatResponse(fullText, MODEL_NAME));
                    } else {
                        callback.onError("Empty response from AI");
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.e(TAG, "Gemini Chat failed: " + t.getMessage());
                mainHandler.post(() -> callback.onError(t.getMessage()));
            }
        }, executor);
    }

    private String buildPrompt(AdminAiChatRequest request) {
        Map<String, Object> stats = new HashMap<>();
        if (request.getStatsSnapshot() != null) {
            stats.put("totalAccounts", request.getStatsSnapshot().getTotalAccounts());
            stats.put("totalReviews", request.getStatsSnapshot().getTotalReviews());
            stats.put("averageRating", request.getStatsSnapshot().getAverageRating());
            stats.put("imageDownloads", request.getStatsSnapshot().getImageDownloads());
            stats.put("usersByMonth", request.getStatsSnapshot().getUsersByMonth());
            stats.put("imageDownloadsByMonth", request.getStatsSnapshot().getImageDownloadsByMonth());
            stats.put("reviewScoreByMonth", request.getStatsSnapshot().getReviewScoreByMonth());
        }

        return "You are Sparkle, a data analyst assistant for a photobooth app.\n" +
                "You are helping the admin understand the application statistics.\n" +
                "Language: " + request.getLanguageTag() + "\n" +
                "Context Data:\n" + gson.toJson(stats) + "\n\n" +
                "Admin's Question: \"" + request.getQuery() + "\"\n\n" +
                "Rules:\n" +
                "1) Answer concisely (under 3-4 sentences).\n" +
                "2) Use the provided data to support your answer if possible.\n" +
                "3) If you cannot answer, say so politely.\n" +
                "4) Answer in the same language as the question.";
    }
}
