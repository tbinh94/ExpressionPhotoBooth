package com.example.expressionphotobooth.data.repository;

import com.example.expressionphotobooth.domain.model.Frame;
import com.example.expressionphotobooth.domain.model.TopFrame;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all Firestore operations related to frame popularity statistics.
 *
 * Firestore collection: `top_frames`
 * Document ID: frame_id (e.g. "1", "2", "3")
 * Schema:
 * {
 *   frame_id: "1",
 *   frame_label: "Cushin",
 *   count: 42,
 *   rank: 1,
 *   score: 0.92,
 *   updated_at: Timestamp
 * }
 */
public class FrameStatsRepository {

    private static final String COLLECTION = "top_frames";
    private static final int TOP_LIMIT = 20;

    private final FirebaseFirestore db;

    public FrameStatsRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Records a frame selection. Increments the count atomically.
     * If document doesn't exist yet, creates it with count = 1.
     *
     * @param frame The frame the user confirmed (pressed NEXT).
     */
    public void recordFrameSelection(android.content.Context context, Frame frame) {
        String docId = String.valueOf(frame.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("frame_id", docId);
        data.put("frame_label", frame.getLabel());
        data.put("count", FieldValue.increment(1));
        data.put("updated_at", FieldValue.serverTimestamp());

        db.collection(COLLECTION)
                .document(docId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("FrameStats", "Write successful for: " + docId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("FrameStats", "Write FAILED for: " + docId, e);
                });
    }

    /**
     * Fetches top 3 most-selected frames from `top_frames`, ordered by count desc.
     * Also updates the rank and score fields after fetching.
     *
     * @return Task that resolves with a QuerySnapshot of <= 3 documents.
     */
    public Task<QuerySnapshot> fetchTop3() {
        return db.collection(COLLECTION)
                .orderBy("count", Query.Direction.DESCENDING)
                .limit(TOP_LIMIT)
                .get()
                .addOnSuccessListener(snapshots -> {
                    // Recalculate rank & score after a successful fetch,
                    // then persist back — fire-and-forget, no UI dependency.
                    if (snapshots == null || snapshots.isEmpty()) return;

                    long maxCount = 0;
                    for (DocumentSnapshot doc : snapshots) {
                        Long cnt = doc.getLong("count");
                        if (cnt != null && cnt > maxCount) maxCount = cnt;
                    }
                    final long finalMaxCount = maxCount;

                    int rank = 1;
                    for (DocumentSnapshot doc : snapshots) {
                        Long cnt = doc.getLong("count");
                        double score = (finalMaxCount > 0 && cnt != null)
                                ? (double) cnt / finalMaxCount
                                : 0.0;

                        Map<String, Object> update = new HashMap<>();
                        update.put("rank", rank);
                        update.put("score", score);
                        db.collection(COLLECTION)
                                .document(doc.getId())
                                .update(update);

                        rank++;
                    }
                });
    }

    /**
     * Parses QuerySnapshot results into TopFrame model list.
     */
    public List<TopFrame> parseTopFrames(QuerySnapshot snapshot) {
        List<TopFrame> result = new ArrayList<>();
        if (snapshot == null) return result;

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            TopFrame tf = new TopFrame();
            tf.setFrameId(doc.getId());
            tf.setFrameLabel(doc.getString("frame_label"));
            Long cnt = doc.getLong("count");
            tf.setCount(cnt != null ? cnt : 0);
            Long rank = doc.getLong("rank");
            tf.setRank(rank != null ? rank.intValue() : 0);
            Double score = doc.getDouble("score");
            tf.setScore(score != null ? score : 0.0);
            result.add(tf);
        }
        return result;
    }
}
