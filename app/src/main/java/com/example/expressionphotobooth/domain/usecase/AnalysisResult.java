package com.example.expressionphotobooth.domain.usecase;

import android.graphics.Rect;

public class AnalysisResult {
    private final boolean matched;
    private final Rect boundingBox;
    private final String label;

    public AnalysisResult(boolean matched, Rect boundingBox, String label) {
        this.matched = matched;
        this.boundingBox = boundingBox;
        this.label = label;
    }

    public boolean isMatched() {
        return matched;
    }

    public Rect getBoundingBox() {
        return boundingBox;
    }

    public String getLabel() {
        return label;
    }
}
