package com.example.expressionphotobooth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminReviewsActivity extends AppCompatActivity {

    private LinearProgressIndicator progressReviews;
    private LinearLayout layoutEmptyReviews;
    private View scrollReviews;
    private LinearLayout containerReviews;
    
    private TextView tvAvgScore, tvTotalCount, tvFiveStarCount, tvLowStarCount;
    private EditText etSearch;
    private ChipGroup chipGroupFilters;

    private List<ReviewData> allReviews = new ArrayList<>();
    private String currentSearch = "";
    private int currentFilterId = R.id.chipAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        
        etSearch = findViewById(R.id.etSearchReview);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);

        setupListeners();
        loadReviews();
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
                applyFilters();
            }
        });
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
                        doc.getString("feedback")
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
                Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    private void updateSummary(int total, double sum, int count5, int countLow) {
        tvTotalCount.setText(String.valueOf(total));
        tvFiveStarCount.setText(String.valueOf(count5));
        tvLowStarCount.setText(String.valueOf(countLow));
        double avg = total > 0 ? sum / total : 0;
        tvAvgScore.setText(String.format(Locale.getDefault(), "%.1f", avg));
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

        tvEmail.setText(review.email);
        tvRating.setText(String.format(Locale.getDefault(), "%.1f ★", review.rating));
        tvFeedback.setText(review.feedback.isEmpty() ? "(Không có nội dung)" : review.feedback);

        containerReviews.addView(itemView);
    }

    private static class ReviewData {
        String email;
        double rating;
        String feedback;

        ReviewData(String email, Double rating, String feedback) {
            this.email = email != null ? email : "N/A";
            this.rating = rating != null ? rating : 0.0;
            this.feedback = feedback != null ? feedback : "";
        }
    }
}
