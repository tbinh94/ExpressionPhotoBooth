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
import com.example.expressionphotobooth.utils.AuditLogger;
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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
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
                        doc.getId(),
                        doc.getString("userEmail"),
                        doc.getDouble("rating"),
                        doc.getString("feedback"),
                        doc.getLong("timestamp"),
                        doc.getString("date"),
                        doc.getString("status"),
                        doc.getString("adminReply")
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
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    private void updateSummary(int total, double sum, int count5, int countLow) {
        tvTotalCount.setText(String.valueOf(total));
        tvFiveStarCount.setText(String.valueOf(count5));
        tvLowStarCount.setText(String.valueOf(countLow));
        double avg = total > 0 ? sum / total : 0;
        tvAvgScore.setText(String.format(Locale.US, "%.1f", avg));
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
        TextView tvStatus = itemView.findViewById(R.id.tvStatus);
        View cardStatus = itemView.findViewById(R.id.cardStatus);
        View btnChangeStatus = itemView.findViewById(R.id.btnChangeStatus);

        tvEmail.setText(TextUtils.isEmpty(review.email) ? "N/A" : review.email);
        tvRating.setText(String.format(Locale.getDefault(), "%d sao", (int) review.rating));
        tvFeedback.setText(review.feedback.isEmpty() ? "(No content)" : review.feedback);

        if (review.timestamp != null && review.timestamp > 0) {
            tvTime.setText(DateUtils.getRelativeTimeSpanString(review.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        } else if (!TextUtils.isEmpty(review.date)) {
            tvTime.setText(review.date);
        } else {
            tvTime.setText("");
        }

        updateStatusUi(tvStatus, cardStatus, review.status);

        View layoutAdminReply = itemView.findViewById(R.id.layoutAdminReply);
        TextView tvAdminReply = itemView.findViewById(R.id.tvAdminReply);

        if (!TextUtils.isEmpty(review.adminReply)) {
            layoutAdminReply.setVisibility(View.VISIBLE);
            tvAdminReply.setText(review.adminReply);
        } else {
            layoutAdminReply.setVisibility(View.GONE);
        }

        btnChangeStatus.setOnClickListener(v -> {
            showReviewActionSheet(review, tvStatus, cardStatus, layoutAdminReply, tvAdminReply);
        });

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

    private void updateStatusUi(TextView tvStatus, View cardStatus, String status) {
        if ("completed".equalsIgnoreCase(status)) {
            tvStatus.setText("Completed");
            tvStatus.setTextColor(Color.parseColor("#10B981"));
            cardStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#ECFDF5")));
        } else if ("processing".equalsIgnoreCase(status)) {
            tvStatus.setText("Processing");
            tvStatus.setTextColor(Color.parseColor("#3B82F6"));
            cardStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EFF6FF")));
        } else {
            tvStatus.setText("Pending");
            tvStatus.setTextColor(Color.parseColor("#F59E0B"));
            cardStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFFBEB")));
        }
    }

    private String getNextStatus(String current) {
        if ("completed".equalsIgnoreCase(current)) return "pending";
        if ("processing".equalsIgnoreCase(current)) return "completed";
        return "processing";
    }

    private void showReviewActionSheet(ReviewData review, TextView tvStatus, View cardStatus, View layoutAdminReply, TextView tvAdminReplyText) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.TransparentBottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.layout_admin_review_action_sheet, null);

        TextView tvSheetUserEmail = view.findViewById(R.id.tvSheetUserEmail);
        TextView tvSheetFeedback = view.findViewById(R.id.tvSheetFeedback);
        
        com.google.android.material.button.MaterialButton btnStatusPending = view.findViewById(R.id.btnStatusPending);
        com.google.android.material.button.MaterialButton btnStatusProcessing = view.findViewById(R.id.btnStatusProcessing);
        com.google.android.material.button.MaterialButton btnStatusCompleted = view.findViewById(R.id.btnStatusCompleted);
        
        EditText etAdminReply = view.findViewById(R.id.etAdminReply);
        View btnReplyViaEmail = view.findViewById(R.id.btnReplyViaEmail);
        View btnCancel = view.findViewById(R.id.btnCancel);
        View btnSave = view.findViewById(R.id.btnSave);

        tvSheetUserEmail.setText(review.email);
        tvSheetFeedback.setText(TextUtils.isEmpty(review.feedback) ? "(No content)" : review.feedback);
        if (!TextUtils.isEmpty(review.adminReply)) {
            etAdminReply.setText(review.adminReply);
        }

        final String[] selectedStatus = { review.status != null ? review.status : "pending" };

        Runnable updateStatusButtonsUi = () -> {
            if ("completed".equalsIgnoreCase(selectedStatus[0])) {
                btnStatusCompleted.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981")));
                btnStatusCompleted.setTextColor(Color.WHITE);
                
                btnStatusProcessing.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00000000")));
                btnStatusProcessing.setTextColor(Color.parseColor("#3B82F6"));
                btnStatusProcessing.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#3B82F6")));
                
                btnStatusPending.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00000000")));
                btnStatusPending.setTextColor(Color.parseColor("#F59E0B"));
                btnStatusPending.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#F59E0B")));
            } else if ("processing".equalsIgnoreCase(selectedStatus[0])) {
                btnStatusCompleted.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00000000")));
                btnStatusCompleted.setTextColor(Color.parseColor("#10B981"));
                btnStatusCompleted.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981")));
                
                btnStatusProcessing.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#3B82F6")));
                btnStatusProcessing.setTextColor(Color.WHITE);
                
                btnStatusPending.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00000000")));
                btnStatusPending.setTextColor(Color.parseColor("#F59E0B"));
                btnStatusPending.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#F59E0B")));
            } else {
                btnStatusCompleted.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00000000")));
                btnStatusCompleted.setTextColor(Color.parseColor("#10B981"));
                btnStatusCompleted.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981")));
                
                btnStatusProcessing.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00000000")));
                btnStatusProcessing.setTextColor(Color.parseColor("#3B82F6"));
                btnStatusProcessing.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#3B82F6")));
                
                btnStatusPending.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F59E0B")));
                btnStatusPending.setTextColor(Color.WHITE);
            }
        };

        updateStatusButtonsUi.run();

        btnStatusPending.setOnClickListener(v -> {
            selectedStatus[0] = "pending";
            updateStatusButtonsUi.run();
        });
        btnStatusProcessing.setOnClickListener(v -> {
            selectedStatus[0] = "processing";
            updateStatusButtonsUi.run();
        });
        btnStatusCompleted.setOnClickListener(v -> {
            selectedStatus[0] = "completed";
            updateStatusButtonsUi.run();
        });

        btnReplyViaEmail.setOnClickListener(v -> {
            String emailText;
            if (review.rating >= 4.0) {
                emailText = "Chào bạn,\n\n" +
                        "Cảm ơn bạn đã sử dụng dịch vụ của Our Memories Photobooth và dành thời gian đánh giá " + ((int) review.rating) + " sao cho chúng tôi!\n\n" +
                        "Chúng tôi rất vui khi biết bạn hài lòng với trải nghiệm này. Đội ngũ của chúng tôi luôn nỗ lực mang lại những bức hình đẹp và kỷ niệm đáng nhớ nhất cho khách hàng.\n\n" +
                        "Nếu bạn có thêm bất kỳ đóng góp nào để giúp dịch vụ ngày một tốt hơn, đừng ngần ngại phản hồi nhé!\n\n" +
                        "Chúc bạn một ngày tuyệt vời!\n" +
                        "Trân trọng,\n" +
                        "Đội ngũ Our Memories Photobooth";
            } else {
                emailText = "Chào bạn,\n\n" +
                        "Lời đầu tiên, Our Memories Photobooth xin chân thành gửi lời xin lỗi vì đã chưa mang lại trải nghiệm tốt nhất cho bạn trong lần sử dụng dịch vụ vừa qua.\n\n" +
                        "Chúng tôi đã ghi nhận đánh giá " + ((int) review.rating) + " sao cùng phản hồi của bạn" + 
                        (TextUtils.isEmpty(review.feedback) ? "." : ": \"" + review.feedback + "\".") + "\n\n" +
                        "Đội ngũ quản trị đang rà soát lại quy trình để cải thiện chất lượng dịch vụ ngay lập tức. Rất hy vọng sẽ có cơ hội được đón tiếp và phục vụ bạn tốt hơn trong những lần tiếp theo.\n\n" +
                        "Nếu bạn cần hỗ trợ thêm, vui lòng liên hệ lại với chúng tôi qua email này.\n\n" +
                        "Trân trọng,\n" +
                        "Đội ngũ Our Memories Photobooth";
            }

            android.content.Intent emailIntent = new android.content.Intent(android.content.Intent.ACTION_SENDTO);
            String uriString = "mailto:" + android.net.Uri.encode(review.email) +
                    "?subject=" + android.net.Uri.encode("Phản hồi đánh giá - Our Memories Photobooth") +
                    "&body=" + android.net.Uri.encode(emailText);
            emailIntent.setData(android.net.Uri.parse(uriString));
            
            emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Phản hồi đánh giá - Our Memories Photobooth");
            emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, emailText);

            try {
                startActivity(emailIntent);
            } catch (android.content.ActivityNotFoundException e) {
                Toast.makeText(this, "Không tìm thấy ứng dụng Email", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newStatus = selectedStatus[0];
            String replyText = etAdminReply.getText().toString().trim();
            updateReviewStatusAndReply(review, newStatus, replyText, tvStatus, cardStatus, layoutAdminReply, tvAdminReplyText, dialog);
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void updateReviewStatusAndReply(ReviewData review, String newStatus, String replyText, TextView tvStatus, View cardStatus, View layoutAdminReply, TextView tvAdminReplyText, com.google.android.material.bottomsheet.BottomSheetDialog dialog) {
        if (review.id == null) return;
        
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("status", newStatus);
        updates.put("adminReply", replyText);
        
        FirebaseFirestore.getInstance().collection("reviews").document(review.id)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    AuditLogger.logAction("UPDATE_REVIEW_STATUS", review.id, "New status: " + newStatus + ", Reply: " + replyText);
                    review.status = newStatus;
                    review.adminReply = replyText;
                    
                    updateStatusUi(tvStatus, cardStatus, newStatus);
                    if (!TextUtils.isEmpty(replyText)) {
                        layoutAdminReply.setVisibility(View.VISIBLE);
                        tvAdminReplyText.setText(replyText);
                    } else {
                        layoutAdminReply.setVisibility(View.GONE);
                    }
                    
                    Toast.makeText(this, "Đã cập nhật đánh giá", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private static class ReviewData {
        String id;
        String email;
        double rating;
        String feedback;
        Long timestamp;
        String date;
        String status;
        String adminReply;

        ReviewData(String id, String email, Double rating, String feedback, Long timestamp, String date, String status, String adminReply) {
            this.id = id;
            this.email = email != null ? email : "";
            this.rating = rating != null ? rating : 0.0;
            this.feedback = feedback != null ? feedback : "";
            this.timestamp = timestamp;
            this.date = date;
            this.status = status != null ? status : "pending";
            this.adminReply = adminReply != null ? adminReply : "";
        }
    }
}
