package com.example.expressionphotobooth;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.expressionphotobooth.ui.chart.AiRatioPieChartView;
import com.example.expressionphotobooth.utils.LocaleManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.text.format.DateUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminReviewsActivity extends AppCompatActivity {

    private LinearProgressIndicator progressReviews;
    private LinearLayout layoutEmptyReviews;
    private View scrollReviews;
    private LinearLayout containerReviews;
    
    private TextView tvAvgScore, tvTotalCount, tvFiveStarCount, tvLowStarCount;
    private TextView tvAiRatioTitle, tvAiRatioPercent, tvAiRegisteredLegend, tvAiNotRegisteredLegend;
    private AiRatioPieChartView pieAiRatio;
    private EditText etSearch;
    private ChipGroup chipGroupFilters;

    private List<ReviewData> allReviews = new ArrayList<>();
    private String currentSearch = "";
    private int currentFilterId = R.id.chipAll;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_reviews);

        MaterialToolbar toolbar = findViewById(R.id.toolbarReviews);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        progressReviews = findViewById(R.id.progressReviews);
        layoutEmptyReviews = findViewById(R.id.layoutEmptyReviews);
        scrollReviews = findViewById(R.id.scrollReviews);
        containerReviews = findViewById(R.id.containerReviews);

        tvAvgScore = findViewById(R.id.tvAvgScore);
        tvTotalCount = findViewById(R.id.tvTotalCount);
        tvFiveStarCount = findViewById(R.id.tvFiveStarCount);
        tvLowStarCount = findViewById(R.id.tvLowStarCount);
        tvAiRatioTitle = findViewById(R.id.tvAiRatioTitle);
        tvAiRatioPercent = findViewById(R.id.tvAiRatioPercent);
        tvAiRegisteredLegend = findViewById(R.id.tvAiRegisteredLegend);
        tvAiNotRegisteredLegend = findViewById(R.id.tvAiNotRegisteredLegend);
        pieAiRatio = findViewById(R.id.pieAiRatio);
        
        etSearch = findViewById(R.id.etSearchReview);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);

        setupListeners();
        loadReviews();
        loadAiRegistrationStats();
    }

    private void setupListeners() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                currentSearch = s.toString().toLowerCase().trim();
                applyFilters();
            }
        });

        chipGroupFilters.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId != View.NO_ID) {
                currentFilterId = checkedId;
                updateChipAppearance();
                applyFilters();
            }
        });
    }

    private void updateChipAppearance() {
        int activeColor = ContextCompat.getColor(this, R.color.app_blue);
        int inactiveColor = Color.parseColor("#FFFFFF");
        int activeTextColor = Color.WHITE;
        int inactiveTextColor = Color.parseColor("#475569");

        for (int i = 0; i < chipGroupFilters.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupFilters.getChildAt(i);
            boolean isSelected = chip.getId() == currentFilterId;
            
            chip.setChipBackgroundColor(ColorStateList.valueOf(isSelected ? activeColor : inactiveColor));
            chip.setTextColor(isSelected ? activeTextColor : inactiveTextColor);
            chip.setChipStrokeWidth(isSelected ? 0 : dpToPx(1));
        }
    }

    private float dpToPx(int dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void loadReviews() {
        progressReviews.setVisibility(View.VISIBLE);
        layoutEmptyReviews.setVisibility(View.GONE);

        FirebaseFirestore.getInstance().collection("reviews")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                progressReviews.setVisibility(View.GONE);
                allReviews.clear();

                double sum = 0;
                int count5 = 0;
                int countLow = 0;

                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    ReviewData data = new ReviewData(
                        doc.getString("userEmail"),
                        doc.getDouble("rating"),
                        doc.getString("feedback"),
                        doc.getLong("timestamp"),
                        doc.getString("date")
                    );
                    allReviews.add(data);

                    sum += data.rating;
                    if (data.rating >= 4.5) count5++;
                    if (data.rating < 3) countLow++;
                }

                updateSummary(allReviews.size(), sum, count5, countLow);
                applyFilters();
            })
            .addOnFailureListener(e -> {
                progressReviews.setVisibility(View.GONE);
                Toast.makeText(this, getString(R.string.admin_reviews_error_load_format, e.getMessage()), Toast.LENGTH_LONG).show();
            });
    }

    private void updateSummary(int total, double sum, int count5, int countLow) {
        tvTotalCount.setText(String.valueOf(total));
        tvFiveStarCount.setText(String.valueOf(count5));
        tvLowStarCount.setText(String.valueOf(countLow));
        double avg = total > 0 ? sum / total : 0;
        tvAvgScore.setText(String.format(Locale.getDefault(), "%.1f", avg));
    }

    private void loadAiRegistrationStats() {
        FirebaseFirestore.getInstance().collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int totalUsers = queryDocumentSnapshots.size();
                    int aiRegisteredUsers = 0;

                    long now = System.currentTimeMillis();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String role = doc.getString("role");
                        Long premiumUntil = doc.getLong("premiumUntil");
                        boolean isPremiumRole = "premium".equalsIgnoreCase(role);
                        boolean hasValidPremiumTime = premiumUntil != null && premiumUntil > now;
                        if (isPremiumRole || hasValidPremiumTime) {
                            aiRegisteredUsers++;
                        }
                    }

                    int notRegistered = Math.max(0, totalUsers - aiRegisteredUsers);
                    double percent = totalUsers > 0 ? (aiRegisteredUsers * 100d) / totalUsers : 0d;

                    if (pieAiRatio != null) {
                        pieAiRatio.setData(aiRegisteredUsers, totalUsers);
                    }
                    if (tvAiRatioPercent != null) {
                        tvAiRatioPercent.setText(getString(R.string.admin_ai_ratio_percent_format, percent));
                    }
                    if (tvAiRegisteredLegend != null) {
                        tvAiRegisteredLegend.setText(getString(R.string.admin_ai_registered_with_count, aiRegisteredUsers));
                    }
                    if (tvAiNotRegisteredLegend != null) {
                        tvAiNotRegisteredLegend.setText(getString(R.string.admin_ai_not_registered_with_count, notRegistered));
                    }
                    if (tvAiRatioTitle != null) {
                        tvAiRatioTitle.setText(getString(R.string.admin_ai_pie_title));
                    }
                })
                .addOnFailureListener(e -> {
                    if (pieAiRatio != null) {
                        pieAiRatio.setData(0, 0);
                    }
                    if (tvAiRatioPercent != null) {
                        tvAiRatioPercent.setText(getString(R.string.admin_ai_ratio_percent_format, 0d));
                    }
                });
    }

    private void applyFilters() {
        containerReviews.removeAllViews();
        List<ReviewData> filteredList = new ArrayList<>();

        for (ReviewData review : allReviews) {
            boolean matchesSearch = review.email.toLowerCase().contains(currentSearch) 
                || review.feedback.toLowerCase().contains(currentSearch);
            
            boolean matchesChip = false;
            if (currentFilterId == R.id.chipAll) matchesChip = true;
            else if (currentFilterId == R.id.chip5Star) matchesChip = (review.rating >= 4.5);
            else if (currentFilterId == R.id.chip4Star) matchesChip = (review.rating >= 3.5 && review.rating < 4.5);
            else if (currentFilterId == R.id.chip3Star) matchesChip = (review.rating >= 2.5 && review.rating < 3.5);
            else if (currentFilterId == R.id.chipNegative) matchesChip = (review.rating < 3);

            if (matchesSearch && matchesChip) {
                filteredList.add(review);
                addReviewToContainer(review);
            }
        }

        layoutEmptyReviews.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
        scrollReviews.setVisibility(filteredList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void addReviewToContainer(ReviewData review) {
        View itemView = getLayoutInflater().inflate(R.layout.item_admin_review, containerReviews, false);

        TextView tvEmail = itemView.findViewById(R.id.tvReviewEmail);
        TextView tvRating = itemView.findViewById(R.id.tvReviewRating);
        TextView tvFeedback = itemView.findViewById(R.id.tvReviewFeedback);
        TextView tvTime = itemView.findViewById(R.id.tvReviewTime);

        tvEmail.setText(TextUtils.isEmpty(review.email) ? getString(R.string.admin_review_email_na) : review.email);
        tvRating.setText(String.format(Locale.getDefault(), "%.1f", review.rating));
        tvFeedback.setText(review.feedback.isEmpty() ? getString(R.string.admin_review_no_content) : review.feedback);

        if (review.timestamp != null && review.timestamp > 0) {
            tvTime.setText(DateUtils.getRelativeTimeSpanString(review.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        } else if (!TextUtils.isEmpty(review.date)) {
            tvTime.setText(review.date);
        } else {
            tvTime.setText("");
        }

        // Set avatar letter
        TextView tvAvatar = itemView.findViewById(R.id.tvAvatar);
        if (tvAvatar != null) {
            String initial = "A";
            if (!TextUtils.isEmpty(review.email)) {
                initial = review.email.substring(0, 1).toUpperCase();
            }
            tvAvatar.setText(initial);
        }

        containerReviews.addView(itemView);
    }

    private static class ReviewData {
        String email;
        double rating;
        String feedback;
        Long timestamp;
        String date;

        ReviewData(String email, Double rating, String feedback, Long timestamp, String date) {
            this.email = email != null ? email : "";
            this.rating = rating != null ? rating : 0.0;
            this.feedback = feedback != null ? feedback : "";
            this.timestamp = timestamp;
            this.date = date;
        }
    }
}
