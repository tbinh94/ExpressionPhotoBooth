package com.example.expressionphotobooth.domain.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdminAiInsights implements Serializable {
    private final String summary;
    private final List<String> insights;
    private final List<String> recommendations;
    private final boolean fromAi;
    private final String sourceLabel;
    private final double confidence;

    public AdminAiInsights(
            String summary,
            List<String> insights,
            List<String> recommendations,
            boolean fromAi,
            String sourceLabel,
            double confidence
    ) {
        this.summary = summary == null ? "" : summary;
        this.insights = insights == null ? new ArrayList<>() : new ArrayList<>(insights);
        this.recommendations = recommendations == null ? new ArrayList<>() : new ArrayList<>(recommendations);
        this.fromAi = fromAi;
        this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
        this.confidence = confidence;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getInsights() {
        return Collections.unmodifiableList(insights);
    }

    public List<String> getRecommendations() {
        return Collections.unmodifiableList(recommendations);
    }

    public boolean isFromAi() {
        return fromAi;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public double getConfidence() {
        return confidence;
    }
}
