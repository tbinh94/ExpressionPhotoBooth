package com.example.expressionphotobooth.domain.model;

/**
 * Represents a document in the `top_frames` Firestore collection.
 * Schema:
 * {
 *   frame_id: "1",         // ID of the frame (matches Frame.id)
 *   frame_label: "Cushin", // Human-readable name
 *   count: 42,             // Total pick count
 *   rank: 1,               // Calculated rank (1 = most popular)
 *   score: 0.92,           // Normalized popularity score (0–1)
 *   updated_at: Timestamp  // Last time this was updated
 * }
 */
public class TopFrame {
    private String frameId;
    private String frameLabel;
    private long count;
    private int rank;
    private double score;

    public TopFrame() {}

    public TopFrame(String frameId, String frameLabel, long count, int rank, double score) {
        this.frameId = frameId;
        this.frameLabel = frameLabel;
        this.count = count;
        this.rank = rank;
        this.score = score;
    }

    public String getFrameId() { return frameId; }
    public void setFrameId(String frameId) { this.frameId = frameId; }

    public String getFrameLabel() { return frameLabel; }
    public void setFrameLabel(String frameLabel) { this.frameLabel = frameLabel; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
