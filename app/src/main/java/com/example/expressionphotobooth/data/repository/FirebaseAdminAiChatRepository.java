package com.example.expressionphotobooth.data.repository;

import com.example.expressionphotobooth.domain.model.AdminAiChatRequest;
import com.example.expressionphotobooth.domain.model.AdminAiChatResponse;
import com.example.expressionphotobooth.domain.repository.AdminAiChatRepository;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class FirebaseAdminAiChatRepository implements AdminAiChatRepository {
    private final FirebaseFunctions functions;
    private final Gson gson = new Gson();

    public FirebaseAdminAiChatRepository() {
        // region: asia-southeast1 matches functions/index.js
        this.functions = FirebaseFunctions.getInstance("asia-southeast1");
    }

    @Override
    public void sendQuery(AdminAiChatRequest request, Callback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("query", request.getQuery());
        data.put("languageTag", request.getLanguageTag());
        
        // Simplify stats snapshot for network payload
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
        data.put("statsSnapshot", stats);

        functions.getHttpsCallable("adminAiChat")
                .call(data)
                .addOnSuccessListener(result -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resData = (Map<String, Object>) result.getData();
                    if (resData != null) {
                        String answer = (String) resData.get("answer");
                        String model = (String) resData.get("model");
                        callback.onSuccess(new AdminAiChatResponse(answer, model));
                    } else {
                        callback.onError("Empty response from AI");
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onError(e.getMessage());
                });
    }
}
