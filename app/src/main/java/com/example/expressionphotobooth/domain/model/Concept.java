package com.example.expressionphotobooth.domain.model;

import java.util.List;

public class Concept {
    private String conceptName;
    private List<Frame> frames;

    public Concept(String conceptName, List<Frame> frames) {
        this.conceptName = conceptName;
        this.frames = frames;
    }

    public String getConceptName() { return conceptName; }
    public List<Frame> getFrames() { return frames; }
}