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
import com.google.ai.client.generativeai.type.RequestOptions;
import com.google.ai.client.generativeai.type.SafetySetting;
import com.example.expressionphotobooth.domain.model.AdminDashboardStats;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirebaseAdminAiChatRepository implements AdminAiChatRepository {
    private static final String TAG = "AiChatRepo";
    private static final String MODEL_NAME = "gemini-flash-latest"; // Use latest flash stable

    private final GenerativeModelFutures model;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FirebaseAdminAiChatRepository() {
        // Lấy API Key từ BuildConfig (đã được cấu hình đọc từ local.properties)
        String apiKey = BuildConfig.GEMINI_API_KEY;

        GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
        configBuilder.temperature = 0.5f;
        configBuilder.maxOutputTokens = 3000; // Extended capacity for long sessions
        GenerationConfig config = configBuilder.build();

        List<SafetySetting> safetySettings = new ArrayList<>();
        safetySettings.add(new SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH));
        safetySettings.add(new SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH));

        Content systemInstruction = new Content.Builder()
                .addText("You are Sparkle, a concise photobooth analytics bot. Use provided stats. Reply in 2-3 sentences. Same language as user.")
                .build();

        GenerativeModel gm = new GenerativeModel(
                MODEL_NAME,
                apiKey,
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
        StringBuilder stats = new StringBuilder();
        if (request.getStatsSnapshot() != null) {
            AdminDashboardStats s = request.getStatsSnapshot();
            stats.append("Acc=").append(s.getTotalAccounts());
            stats.append(",Rev=").append(s.getTotalReviews());
            stats.append(",Rat=").append(String.format(java.util.Locale.US, "%.1f", s.getAverageRating()));
            stats.append(",DL=").append(s.getImageDownloads());
            if (s.getUsersByMonth() != null && !s.getUsersByMonth().isEmpty()) stats.append(",Usr/Mo=").append(s.getUsersByMonth());
            if (s.getImageDownloadsByMonth() != null && !s.getImageDownloadsByMonth().isEmpty()) stats.append(",DL/Mo=").append(s.getImageDownloadsByMonth());
            if (s.getReviewScoreByMonth() != null && !s.getReviewScoreByMonth().isEmpty()) stats.append(",Rt/Mo=").append(s.getReviewScoreByMonth());
        }

        return "Stats: " + stats + "\nUser: " + request.getQuery();
    }
}
