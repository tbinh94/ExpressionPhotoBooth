package com.example.expressionphotobooth;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Locale;

public class AdminReviewsActivity extends AppCompatActivity {

    private LinearProgressIndicator progressReviews;
    private LinearLayout layoutEmptyReviews;
    private View scrollReviews;
    private LinearLayout containerReviews;

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

        loadReviews();
    }

    private void loadReviews() {
        progressReviews.setVisibility(View.VISIBLE);
        layoutEmptyReviews.setVisibility(View.GONE);
        scrollReviews.setVisibility(View.GONE);

        FirebaseFirestore.getInstance().collection("reviews")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                progressReviews.setVisibility(View.GONE);

                if (queryDocumentSnapshots.isEmpty()) {
                    layoutEmptyReviews.setVisibility(View.VISIBLE);
                    return;
                }

                scrollReviews.setVisibility(View.VISIBLE);
                containerReviews.removeAllViews();
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    addReviewItem(doc);
                }
            })
            .addOnFailureListener(e -> {
                progressReviews.setVisibility(View.GONE);
                Toast.makeText(this, "Không thể tải reviews: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    private void addReviewItem(QueryDocumentSnapshot doc) {
        View itemView = getLayoutInflater().inflate(R.layout.item_admin_review, containerReviews, false);

        TextView tvEmail = itemView.findViewById(R.id.tvReviewEmail);
        TextView tvRating = itemView.findViewById(R.id.tvReviewRating);
        TextView tvFeedback = itemView.findViewById(R.id.tvReviewFeedback);

        String email = doc.getString("userEmail");
        Double rating = doc.getDouble("rating");
        String feedback = doc.getString("feedback");

        tvEmail.setText(email != null ? email : "N/A");
        tvRating.setText(String.format(Locale.getDefault(), "%.1f ★", rating != null ? rating : 0.0));
        tvFeedback.setText(feedback != null && !feedback.isEmpty() ? feedback : "(Không có nội dung)");

        containerReviews.addView(itemView);
    }
}
