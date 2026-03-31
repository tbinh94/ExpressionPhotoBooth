package com.example.expressionphotobooth.ui.chart;

public class MonthlyChartPoint {
    private final String label;
    private final float value;
    private final String valueText;

    public MonthlyChartPoint(String label, float value, String valueText) {
        this.label = label;
        this.value = value;
        this.valueText = valueText;
    }

    public String getLabel() {
        return label;
    }

    public float getValue() {
        return value;
    }

    public String getValueText() {
        return valueText;
    }
}

