package com.example.expressionphotobooth.data.repository;

import androidx.annotation.NonNull;

import com.example.expressionphotobooth.domain.model.AdminDashboardStats;
import com.example.expressionphotobooth.domain.model.DownloadType;
import com.example.expressionphotobooth.domain.repository.AdminStatsRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

public class FirebaseAdminStatsRepository implements AdminStatsRepository {
    private static final String USERS_COLLECTION = "users";
    private static final String REVIEWS_COLLECTION = "reviews";
    private static final String DOWNLOAD_EVENTS_COLLECTION = "download_events";
    private static final String ADMIN_METRICS_COLLECTION = "admin_metrics";
    private static final String ADMIN_GLOBAL_DOC = "global";

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    public FirebaseAdminStatsRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    @Override
    public void fetchDashboardStats(StatsCallback callback) {
        Task<QuerySnapshot> usersTask = firestore.collection(USERS_COLLECTION).get();
        Task<QuerySnapshot> reviewsTask = firestore.collection(REVIEWS_COLLECTION).get();
        Task<QuerySnapshot> downloadsTask = firestore.collection(DOWNLOAD_EVENTS_COLLECTION).get();

        Tasks.whenAllSuccess(usersTask, reviewsTask, downloadsTask)
                .addOnSuccessListener(results -> {
                    QuerySnapshot usersSnapshot = (QuerySnapshot) results.get(0);
                    QuerySnapshot reviewsSnapshot = (QuerySnapshot) results.get(1);
                    QuerySnapshot downloadsSnapshot = (QuerySnapshot) results.get(2);

                    int totalAccounts = usersSnapshot.size();
                    int totalReviews = reviewsSnapshot.size();

                    int[] ratingCounts = new int[6];
                    double scoreSum = 0d;
                    long lastReviewAt = 0L;

                    TreeMap<String, Integer> usersByMonth = new TreeMap<>();
                    TreeMap<String, Integer> imageDownloadsByMonth = new TreeMap<>();
                    TreeMap<String, Double> reviewScoreByMonth = new TreeMap<>();

                    for (QueryDocumentSnapshot userDoc : usersSnapshot) {
                        long userCreatedAt = resolveMillis(userDoc, "createdAt", "timestamp");
                        if (userCreatedAt > 0) {
                            String month = toMonthKey(userCreatedAt);
                            usersByMonth.put(month, usersByMonth.getOrDefault(month, 0) + 1);
                        }
                    }

                    for (QueryDocumentSnapshot reviewDoc : reviewsSnapshot) {
                        Double ratingValue = reviewDoc.getDouble("rating");
                        if (ratingValue != null) {
                            scoreSum += ratingValue;
                            int rounded = (int) Math.round(ratingValue);
                            if (rounded >= 1 && rounded <= 5) {
                                ratingCounts[rounded]++;
                            }
                        }

                        long reviewMillis = resolveMillis(reviewDoc, "createdAt", "timestamp");
                        if (reviewMillis > lastReviewAt) {
                            lastReviewAt = reviewMillis;
                        }
                        if (reviewMillis > 0 && ratingValue != null) {
                            String month = toMonthKey(reviewMillis);
                            reviewScoreByMonth.put(month, reviewScoreByMonth.getOrDefault(month, 0d) + ratingValue);
                        }
                    }

                    int imageDownloads = 0;
                    int videoDownloads = 0;
                    for (QueryDocumentSnapshot downloadDoc : downloadsSnapshot) {
                        String type = downloadDoc.getString("type");
                        if (DownloadType.IMAGE.toFirestoreValue().equals(type)) {
                            imageDownloads++;
                            long downloadMillis = resolveMillis(downloadDoc, "createdAt", "timestamp");
                            if (downloadMillis > 0) {
                                String month = toMonthKey(downloadMillis);
                                imageDownloadsByMonth.put(month, imageDownloadsByMonth.getOrDefault(month, 0) + 1);
                            }
                        } else if (DownloadType.VIDEO.toFirestoreValue().equals(type)) {
                            videoDownloads++;
                        }
                    }

                    double averageRating = totalReviews > 0 ? scoreSum / totalReviews : 0d;
                    int lowRatingCount = ratingCounts[1] + ratingCounts[2];

                    AdminDashboardStats stats = new AdminDashboardStats(
                            totalAccounts,
                            totalReviews,
                            averageRating,
                            lowRatingCount,
                            ratingCounts[5],
                            imageDownloads,
                            videoDownloads,
                            lastReviewAt,
                            ratingCounts,
                            new LinkedHashMap<>(usersByMonth),
                            new LinkedHashMap<>(imageDownloadsByMonth),
                            new LinkedHashMap<>(reviewScoreByMonth)
                    );
                    callback.onSuccess(stats);
                })
                .addOnFailureListener(e -> callback.onError(safeMessage(e, "Failed to load dashboard metrics.")));
    }

    @Override
    public void recordDownload(DownloadType downloadType) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", downloadType.toFirestoreValue());
        event.put("createdAt", FieldValue.serverTimestamp());
        event.put("timestamp", System.currentTimeMillis());

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            event.put("userId", user.getUid());
            event.put("userEmail", user.getEmail());
        }

        firestore.collection(DOWNLOAD_EVENTS_COLLECTION).add(event);

        Map<String, Object> aggregatePatch = new HashMap<>();
        String fieldName = downloadType == DownloadType.IMAGE ? "imageDownloads" : "videoDownloads";
        aggregatePatch.put(fieldName, FieldValue.increment(1));
        aggregatePatch.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection(ADMIN_METRICS_COLLECTION)
                .document(ADMIN_GLOBAL_DOC)
                .set(aggregatePatch, SetOptions.merge());
    }

    @Override
    public void recordReviewSubmitted() {
        Map<String, Object> patch = new HashMap<>();
        patch.put("lastReviewAt", FieldValue.serverTimestamp());
        patch.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection(ADMIN_METRICS_COLLECTION)
                .document(ADMIN_GLOBAL_DOC)
                .set(patch, SetOptions.merge());
    }

    private long resolveMillis(QueryDocumentSnapshot doc, String serverField, String fallbackField) {
        long serverMillis = coerceToMillis(doc.get(serverField));
        if (serverMillis > 0L) {
            return serverMillis;
        }

        long fallbackMillis = coerceToMillis(doc.get(fallbackField));
        if (fallbackMillis > 0L) {
            return fallbackMillis;
        }

        return 0L;
    }

    private long coerceToMillis(Object rawValue) {
        if (rawValue == null) {
            return 0L;
        }
        if (rawValue instanceof Timestamp) {
            return ((Timestamp) rawValue).toDate().getTime();
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        if (rawValue instanceof Date) {
            return ((Date) rawValue).getTime();
        }
        if (rawValue instanceof String) {
            try {
                return Long.parseLong(((String) rawValue).trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private String toMonthKey(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(millis));
    }

    @NonNull
    private String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message;
    }
}



