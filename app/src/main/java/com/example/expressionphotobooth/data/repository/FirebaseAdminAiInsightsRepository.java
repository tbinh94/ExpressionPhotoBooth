package com.example.expressionphotobooth.data.repository;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;

import com.example.expressionphotobooth.R;
import com.example.expressionphotobooth.domain.model.AdminAiInsightRequest;
import com.example.expressionphotobooth.domain.model.AdminAiInsights;
import com.example.expressionphotobooth.domain.repository.AdminAiInsightsRepository;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FirebaseAdminAiInsightsRepository implements AdminAiInsightsRepository {
    private static final String FUNCTION_NAME = "adminAiInsights";
    private static final String FUNCTIONS_REGION = "asia-southeast1";
    // Android emulator maps host machine localhost to 10.0.2.2.
    private static final String DEBUG_EMULATOR_HOST = "10.0.2.2";
    private static final int DEBUG_EMULATOR_PORT = 5001;

    private final Context appContext;
    private final FirebaseFunctions functions;

    public FirebaseAdminAiInsightsRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION);
        boolean isDebuggable = (this.appContext.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (isDebuggable) {
            this.functions.useEmulator(DEBUG_EMULATOR_HOST, DEBUG_EMULATOR_PORT);
        }
    }

    @Override
    public void fetchInsights(AdminAiInsightRequest request, Callback callback) {
        if (request == null) {
            callback.onError("Missing insight request payload.");
            return;
        }

        Map<String, Object> payload = buildPayload(request);
        functions
                .getHttpsCallable(FUNCTION_NAME)
                .call(payload)
                .addOnSuccessListener(result -> {
                    AdminAiInsights parsed = parseResponse(result.getData(), request);
                    callback.onSuccess(parsed);
                })
                .addOnFailureListener(error -> callback.onSuccess(buildFallbackInsights(request)));
    }

    private Map<String, Object> buildPayload(AdminAiInsightRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("rangeMonths", request.getRangeMonths());
        payload.put("languageTag", request.getLanguageTag());

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("usersByMonth", request.getUsersByMonth());
        snapshot.put("imageDownloadsByMonth", request.getImageDownloadsByMonth());
        snapshot.put("reviewScoreByMonth", request.getReviewScoreByMonth());

        Map<String, Object> aiRatio = new HashMap<>();
        aiRatio.put("withAI", request.getAiRegisteredUsers());
        aiRatio.put("withoutAI", request.getAiNotRegisteredUsers());
        snapshot.put("aiRatio", aiRatio);

        payload.put("statsSnapshot", snapshot);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private AdminAiInsights parseResponse(Object raw, AdminAiInsightRequest request) {
        String languageTag = request.getLanguageTag();
        if (!(raw instanceof Map)) {
            return buildFallbackInsights(request);
        }

        Map<String, Object> data = (Map<String, Object>) raw;
        String summary = stringOrDefault(data.get("summary"), localized(languageTag,
                R.string.admin_ai_insights_summary_fallback_vi,
                R.string.admin_ai_insights_summary_fallback_en));

        List<String> insights = parseStringOrObjectList(data.get("insights"));
        if (insights.isEmpty()) {
            insights.add(localized(languageTag,
                    R.string.admin_ai_insights_line_no_data_vi,
                    R.string.admin_ai_insights_line_no_data_en));
        }

        List<String> recommendations = parseRecommendationList(data.get("recommendations"));
        if (recommendations.isEmpty()) {
            recommendations.add(localized(languageTag,
                    R.string.admin_ai_insights_action_fallback_vi,
                    R.string.admin_ai_insights_action_fallback_en));
        }

        double confidence = parseDouble(data.get("confidence"));
        String source = localized(languageTag,
                R.string.admin_ai_insights_source_ai_vi,
                R.string.admin_ai_insights_source_ai_en);

        return new AdminAiInsights(summary, clampList(insights, 3), clampList(recommendations, 2), true, source, confidence);
    }

    private List<String> parseStringOrObjectList(Object rawList) {
        List<String> out = new ArrayList<>();
        if (!(rawList instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) rawList) {
            if (item instanceof String) {
                String text = ((String) item).trim();
                if (!text.isEmpty()) {
                    out.add(text);
                }
            } else if (item instanceof Map) {
                Object detail = ((Map<?, ?>) item).get("detail");
                Object title = ((Map<?, ?>) item).get("title");
                String merged = stringOrDefault(title, "");
                String detailText = stringOrDefault(detail, "");
                if (!detailText.isEmpty()) {
                    merged = merged.isEmpty() ? detailText : merged + ": " + detailText;
                }
                if (!merged.trim().isEmpty()) {
                    out.add(merged.trim());
                }
            }
        }
        return out;
    }

    private List<String> parseRecommendationList(Object rawList) {
        List<String> out = new ArrayList<>();
        if (!(rawList instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) rawList) {
            if (item instanceof String) {
                String text = ((String) item).trim();
                if (!text.isEmpty()) {
                    out.add(text);
                }
            } else if (item instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) item;
                String title = stringOrDefault(map.get("title"), "");
                String action = stringOrDefault(map.get("action"), "");
                String merged = title;
                if (!action.isEmpty()) {
                    merged = merged.isEmpty() ? action : merged + " - " + action;
                }
                if (!merged.trim().isEmpty()) {
                    out.add(merged.trim());
                }
            }
        }
        return out;
    }

    private AdminAiInsights buildFallbackInsights(AdminAiInsightRequest request) {
        boolean vi = isVietnamese(request.getLanguageTag());
        LinkedHashMap<String, Integer> users = request.getUsersByMonth();
        LinkedHashMap<String, Integer> downloads = request.getImageDownloadsByMonth();
        LinkedHashMap<String, Double> reviews = request.getReviewScoreByMonth();

        List<String> insights = new ArrayList<>();
        insights.add(buildTrendLine(users, vi,
                "Tài khoản mới", "New accounts"));
        insights.add(buildTrendLine(downloads, vi,
                "Lượt tải ảnh", "Image downloads"));
        insights.add(buildTrendLineDouble(reviews, vi,
                "Điểm đánh giá", "Review score"));

        int totalAi = request.getAiRegisteredUsers() + request.getAiNotRegisteredUsers();
        double ratio = totalAi > 0 ? (request.getAiRegisteredUsers() * 100d) / totalAi : 0d;
        String aiRatioLine = vi
                ? String.format(Locale.getDefault(), "Tỷ lệ user đăng ký AI hiện tại là %.1f%%.", ratio)
                : String.format(Locale.getDefault(), "Current AI-registered user ratio is %.1f%%.", ratio);

        if (insights.size() >= 3) {
            insights.set(2, insights.get(2) + " " + aiRatioLine);
        } else {
            insights.add(aiRatioLine);
        }

        List<String> actions = new ArrayList<>();
        actions.add(vi
                ? "Ưu tiên đẩy frame/chủ đề đang có hiệu suất tải cao lên vị trí đầu trang Home trong 7 ngày."
                : "Prioritize high-performing frames/themes on Home for the next 7 days.");
        actions.add(vi
                ? "Theo dõi biến động review theo tháng và phản hồi nhóm đánh giá thấp trong vòng 24 giờ."
                : "Track monthly review shifts and respond to low-score feedback within 24 hours.");

        String summary = vi
                ? "Báo cáo tóm tắt được tạo theo quy tắc nội bộ do AI API chưa phản hồi."
                : "Summary generated by internal rules because AI API is currently unavailable.";
        String source = vi
                ? appContext.getString(R.string.admin_ai_insights_source_rule_vi)
                : appContext.getString(R.string.admin_ai_insights_source_rule_en);

        return new AdminAiInsights(summary, clampList(insights, 3), clampList(actions, 2), false, source, 0.55d);
    }

    private String buildTrendLine(Map<String, Integer> map, boolean vi, String labelVi, String labelEn) {
        if (map == null || map.size() < 2) {
            return vi
                    ? labelVi + " chưa đủ dữ liệu để kết luận xu hướng."
                    : labelEn + " does not have enough data for trend detection.";
        }
        List<Integer> values = new ArrayList<>(map.values());
        int current = values.get(values.size() - 1);
        int previous = values.get(values.size() - 2);
        double pct = previous == 0 ? 0d : ((current - previous) * 100d / previous);
        if (vi) {
            return String.format(Locale.getDefault(), "%s tháng gần nhất %s %.1f%% (%d -> %d).",
                    labelVi, pct >= 0 ? "tăng" : "giảm", Math.abs(pct), previous, current);
        }
        return String.format(Locale.getDefault(), "%s in the latest month %s %.1f%% (%d -> %d).",
                labelEn, pct >= 0 ? "increased" : "decreased", Math.abs(pct), previous, current);
    }

    private String buildTrendLineDouble(Map<String, Double> map, boolean vi, String labelVi, String labelEn) {
        if (map == null || map.size() < 2) {
            return vi
                    ? labelVi + " chưa đủ dữ liệu để kết luận xu hướng."
                    : labelEn + " does not have enough data for trend detection.";
        }
        List<Double> values = new ArrayList<>(map.values());
        double current = values.get(values.size() - 1);
        double previous = values.get(values.size() - 2);
        double diff = current - previous;
        if (vi) {
            return String.format(Locale.getDefault(), "%s thay đổi %.2f điểm (%s -> %s).",
                    labelVi,
                    diff,
                    trimDouble(previous),
                    trimDouble(current));
        }
        return String.format(Locale.getDefault(), "%s changed by %.2f points (%s -> %s).",
                labelEn,
                diff,
                trimDouble(previous),
                trimDouble(current));
    }

    private String trimDouble(double value) {
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private List<String> clampList(List<String> source, int maxItems) {
        List<String> out = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return out;
        }
        for (String item : source) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
            if (out.size() >= maxItems) {
                break;
            }
        }
        return out;
    }

    private boolean isVietnamese(String languageTag) {
        return languageTag != null && languageTag.toLowerCase(Locale.US).startsWith("vi");
    }

    private String stringOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private double parseDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return 0d;
            }
        }
        return 0d;
    }

    private String localized(String languageTag, int viRes, int enRes) {
        return appContext.getString(isVietnamese(languageTag) ? viRes : enRes);
    }
}



