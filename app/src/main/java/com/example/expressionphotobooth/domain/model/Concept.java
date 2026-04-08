package com.example.expressionphotobooth.domain.model;

import java.util.List;

public class Concept {
    private String conceptName;
    private List<Frame> frames;
    private boolean trending;

    public Concept(String conceptName, List<Frame> frames) {
        this.conceptName = conceptName;
        this.frames = frames;
        this.trending = false;
    }

    public Concept(String conceptName, List<Frame> frames, boolean trending) {
        this.conceptName = conceptName;
        this.frames = frames;
        this.trending = trending;
    }

    public String getConceptName() { return conceptName; }
    public List<Frame> getFrames() { return frames; }
    public boolean isTrending() { return trending; }
}