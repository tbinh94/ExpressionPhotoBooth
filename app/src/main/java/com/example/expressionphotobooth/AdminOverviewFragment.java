package com.example.expressionphotobooth;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.example.expressionphotobooth.utils.AuditLogger;
import com.google.firebase.firestore.FirebaseFirestore;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.expressionphotobooth.domain.model.AdminDashboardStats;
import com.example.expressionphotobooth.domain.model.AdminAiInsightRequest;
import com.example.expressionphotobooth.domain.model.AdminAiInsights;
import com.example.expressionphotobooth.domain.repository.AdminAiInsightsRepository;
import com.example.expressionphotobooth.domain.repository.AdminStatsRepository;
import com.example.expressionphotobooth.ui.chart.MonthlyBarChartView;
import com.example.expressionphotobooth.ui.chart.MonthlyChartPoint;
import com.example.expressionphotobooth.utils.LocaleManager;
import com.example.expressionphotobooth.ui.chart.AiRatioPieChartView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminOverviewFragment extends Fragment implements RuntimeLanguageUpdatable {

    private static final int RANGE_3M = 3;
    private static final int RANGE_6M = 6;
    private static final int RANGE_12M = 12;
    private static final long AI_ANALYZE_COOLDOWN_MS = 30000L; // 30 seconds cooldown

    private View bar5;
    private View bar4;
    private View bar3;
    private View bar2;
    private View bar1;

    private TextView tv5;
    private TextView tv4;
    private TextView tv3;
    private TextView tv2;
    private TextView tv1;

    private TextView tvBigAvg;
    private TextView tvBigTotal;
    private TextView tvBig5Star;
    private TextView tvChartSubtitle;
    private TextView tvLastReviewAt;
    private TextView tvTotalReviewsLabel;
    private TextView tvFiveStarLabel;
    private TextView tvRangeTitle;
    private TextView tvUsersChartTitle;
    private TextView tvUsersChartSubtitle;
    private TextView tvDownloadsChartTitle;
    private TextView tvDownloadsChartSubtitle;
    private TextView tvReviewScoreChartTitle;
    private TextView tvReviewScoreChartSubtitle;
    private TextView tvBar5Label;
    private TextView tvBar4Label;
    private TextView tvBar3Label;
    private TextView tvBar2Label;
    private TextView tvBar1Label;

    private TextView tvStatAccounts;
    private TextView tvStatImageDownloads;
    private TextView tvStatVideoDownloads;

    private MonthlyBarChartView chartUsersByMonth;
    private MonthlyBarChartView chartImageDownloadsByMonth;
    private MonthlyBarChartView chartReviewScoreByMonth;

    private TextView tvUsersByMonthEmpty;
    private TextView tvImageDownloadsByMonthEmpty;
    private TextView tvReviewScoreByMonthEmpty;

    private MaterialButton btnRange3m;
    private MaterialButton btnRange6m;
    private MaterialButton btnRange12m;
    private AiRatioPieChartView pieAiRatioMini;
    private TextView tvAiRatioMiniTitle;
    private TextView tvAiRatioMiniPercent;
    private TextView tvAiRatioMiniMeta;
    private TextView tvAiInsightsTitle;
    private TextView tvAiInsightsSubtitle;
    private TextView tvAiSummary;
    private TextView tvAiInsightLine1;
    private TextView tvAiInsightLine2;
    private TextView tvAiInsightLine3;
    private TextView tvAiRecommendation;
    private ProgressBar progressAiInsights;
    private MaterialButton btnAiAnalyze;
    private View cardAiChatSearch;
    private TextView tvAiChatPrompt;

    private View layoutAiEmptyState;
    private View layoutAiResultState;
    private TextView tvAiEmptyTitle;
    private TextView tvAiEmptyDesc;

    private com.google.android.material.progressindicator.LinearProgressIndicator progressQuotaGemini;
    private com.google.android.material.progressindicator.LinearProgressIndicator progressQuotaStorage;
    private TextView tvQuotaGemini;
    private TextView tvQuotaStorage;

    private AdminStatsRepository adminStatsRepository;
    private AdminAiInsightsRepository adminAiInsightsRepository;
    private AdminDashboardStats latestStats;
    private int selectedRangeMonths = RANGE_6M;
    private int aiRequestSerial = 0;
    private boolean aiInsightsLoading = false;
    private long aiCooldownUntilMillis = 0L;
    private CountDownTimer aiCooldownTimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adminStatsRepository = ((AppContainer) requireActivity().getApplication()).getAdminStatsRepository();
        adminAiInsightsRepository = ((AppContainer) requireActivity().getApplication()).getAdminAiInsightsRepository();

        bar5 = view.findViewById(R.id.bar5Star);
        bar4 = view.findViewById(R.id.bar4Star);
        bar3 = view.findViewById(R.id.bar3Star);
        bar2 = view.findViewById(R.id.bar2Star);
        bar1 = view.findViewById(R.id.bar1Star);

        tv5 = view.findViewById(R.id.tv5StarCount);
        tv4 = view.findViewById(R.id.tv4StarCount);
        tv3 = view.findViewById(R.id.tv3StarCount);
        tv2 = view.findViewById(R.id.tv2StarCount);
        tv1 = view.findViewById(R.id.tv1StarCount);

        tvBigAvg = view.findViewById(R.id.tvBigAvg);
        tvBigTotal = view.findViewById(R.id.tvBigTotal);
        tvBig5Star = view.findViewById(R.id.tvBig5Star);
        tvFiveStarLabel = view.findViewById(R.id.tvFiveStarLabel);

        // Viral Campaign Management
        com.google.android.material.materialswitch.MaterialSwitch switchCampaign = view.findViewById(R.id.switchViralCampaign);
        if (switchCampaign != null) {
            FirebaseFirestore.getInstance().collection("app_config").document("viral_campaign")
                    .get().addOnSuccessListener(doc -> {
                        if (doc.exists() && doc.contains("isActive")) {
                            switchCampaign.setChecked(doc.getBoolean("isActive"));
                        }
                    });

            switchCampaign.setOnCheckedChangeListener((buttonView, isChecked) -> {
                FirebaseFirestore.getInstance().collection("app_config").document("viral_campaign")
                        .update("isActive", isChecked)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), "Campaign: " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
                            AuditLogger.logAction("CAMPAIGN_TOGGLE", "Admin changed viral campaign status to " + isChecked);
                        });
            });
        }

        MaterialButton btnTestPopup = view.findViewById(R.id.btnTestViralPopup);
        if (btnTestPopup != null) {
            btnTestPopup.setOnClickListener(v -> {
                // Tạo một bản sao manager để test nhanh giao diện
                com.example.expressionphotobooth.utils.ViralRewardManager testManager = 
                    new com.example.expressionphotobooth.utils.ViralRewardManager(requireActivity());
                testManager.checkAndShowRewardPopup(null); // Truyền null để test nhanh
            });
        }
        tvRangeTitle = view.findViewById(R.id.tvRangeTitle);
        tvUsersChartTitle = view.findViewById(R.id.tvUsersChartTitle);
        tvUsersChartSubtitle = view.findViewById(R.id.tvUsersChartSubtitle);
        tvDownloadsChartTitle = view.findViewById(R.id.tvDownloadsChartTitle);
        tvDownloadsChartSubtitle = view.findViewById(R.id.tvDownloadsChartSubtitle);
        tvReviewScoreChartTitle = view.findViewById(R.id.tvReviewScoreChartTitle);
        tvReviewScoreChartSubtitle = view.findViewById(R.id.tvReviewScoreChartSubtitle);
        tvBar5Label = view.findViewById(R.id.tvBar5Label);
        tvBar4Label = view.findViewById(R.id.tvBar4Label);
        tvBar3Label = view.findViewById(R.id.tvBar3Label);
        tvBar2Label = view.findViewById(R.id.tvBar2Label);
        tvBar1Label = view.findViewById(R.id.tvBar1Label);

        chartUsersByMonth = view.findViewById(R.id.chartUsersByMonth);
        chartImageDownloadsByMonth = view.findViewById(R.id.chartImageDownloadsByMonth);
        chartReviewScoreByMonth = view.findViewById(R.id.chartReviewScoreByMonth);

        tvUsersByMonthEmpty = view.findViewById(R.id.tvUsersByMonthEmpty);
        tvImageDownloadsByMonthEmpty = view.findViewById(R.id.tvImageDownloadsByMonthEmpty);
        tvReviewScoreByMonthEmpty = view.findViewById(R.id.tvReviewScoreByMonthEmpty);

        btnRange3m = view.findViewById(R.id.btnRange3m);
        btnRange6m = view.findViewById(R.id.btnRange6m);
        btnRange12m = view.findViewById(R.id.btnRange12m);
        pieAiRatioMini = view.findViewById(R.id.pieAiRatioMini);
        tvAiRatioMiniTitle = view.findViewById(R.id.tvAiRatioMiniTitle);
        tvAiRatioMiniPercent = view.findViewById(R.id.tvAiRatioMiniPercent);
        tvAiRatioMiniMeta = view.findViewById(R.id.tvAiRatioMiniMeta);
        tvAiInsightsTitle = view.findViewById(R.id.tvAiInsightsTitle);
        tvAiInsightsSubtitle = view.findViewById(R.id.tvAiInsightsSubtitle);
        tvAiSummary = view.findViewById(R.id.tvAiSummary);
        tvAiInsightLine1 = view.findViewById(R.id.tvAiInsightLine1);
        tvAiInsightLine2 = view.findViewById(R.id.tvAiInsightLine2);
        tvAiInsightLine3 = view.findViewById(R.id.tvAiInsightLine3);
        tvAiRecommendation = view.findViewById(R.id.tvAiRecommendation);
        progressAiInsights = view.findViewById(R.id.progressAiInsights);
        btnAiAnalyze = view.findViewById(R.id.btnAiAnalyze);
        layoutAiEmptyState = view.findViewById(R.id.layoutAiEmptyState);
        layoutAiResultState = view.findViewById(R.id.layoutAiResultState);
        tvAiEmptyTitle = view.findViewById(R.id.tvAiEmptyTitle);
        tvAiEmptyDesc = view.findViewById(R.id.tvAiEmptyDesc);

        progressQuotaGemini = view.findViewById(R.id.progressQuotaGemini);
        progressQuotaStorage = view.findViewById(R.id.progressQuotaStorage);
        tvQuotaGemini = view.findViewById(R.id.tvQuotaGemini);
        tvQuotaStorage = view.findViewById(R.id.tvQuotaStorage);

        updateSystemQuotas();

        if (btnAiAnalyze != null) {
            btnAiAnalyze.setOnClickListener(v -> {
                if (latestStats == null) {
                    return;
                }
                String languageTag = LocaleManager.getCurrentLanguage(requireContext());
                long remainingMillis = aiCooldownUntilMillis - System.currentTimeMillis();
                if (remainingMillis > 0L) {
                    int seconds = (int) Math.ceil(remainingMillis / 1000d);
                    Toast.makeText(
                            requireContext(),
                            LocaleManager.createLocalizedContext(requireContext(), languageTag)
                                    .getString(R.string.admin_ai_insights_wait_to_retry_format, seconds),
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }
                startAnalyzeCooldown(languageTag);
                loadAiInsights(latestStats, languageTag);
            });
        }

        cardAiChatSearch = view.findViewById(R.id.cardAiChatSearch);
        tvAiChatPrompt = view.findViewById(R.id.tvAiChatPrompt);

        if (cardAiChatSearch != null) {
            cardAiChatSearch.setOnClickListener(v -> {
                if (latestStats == null) {
                    return;
                }
                String languageTag = LocaleManager.getCurrentLanguage(requireContext());
                AdminAiChatBottomSheet chat = AdminAiChatBottomSheet.newInstance(latestStats, languageTag);
                chat.show(getChildFragmentManager(), "AdminAiChat");
            });
        }

        bindRangeSelector();

        // AI ratio card click → show detail sheet
        View cardAiRatio = view.findViewById(R.id.cardAiRatioMini);
        if (cardAiRatio != null) {
            cardAiRatio.setOnClickListener(v -> showAiRatioDetail());
        }

        // Chart detail buttons
        View btnDetailUsers = view.findViewById(R.id.btnDetailUsersChart);
        View btnDetailDownloads = view.findViewById(R.id.btnDetailDownloadsChart);
        View btnDetailReviews = view.findViewById(R.id.btnDetailReviewsChart);

        if (btnDetailUsers != null) btnDetailUsers.setOnClickListener(v -> showChartDetail("USERS"));
        if (btnDetailDownloads != null) btnDetailDownloads.setOnClickListener(v -> showChartDetail("DOWNLOADS"));
        if (btnDetailReviews != null) btnDetailReviews.setOnClickListener(v -> showChartDetail("REVIEWS"));

        // Also make the cards clickable for better UX
        View cardUsers = view.findViewById(R.id.cardUsersChart);
        View cardDownloads = view.findViewById(R.id.cardDownloadsChart);
        View cardReviews = view.findViewById(R.id.cardReviewsChart);
        
        if (cardUsers != null) cardUsers.setOnClickListener(v -> showChartDetail("USERS"));
        if (cardDownloads != null) cardDownloads.setOnClickListener(v -> showChartDetail("DOWNLOADS"));
        if (cardReviews != null) cardReviews.setOnClickListener(v -> showChartDetail("REVIEWS"));

        if (getActivity() != null) {
            tvStatAccounts = getActivity().findViewById(R.id.tvStatTotalAccounts);
            tvStatImageDownloads = getActivity().findViewById(R.id.tvStatImageDownloads);
            tvStatVideoDownloads = getActivity().findViewById(R.id.tvStatVideoDownloads);
        }

        loadStats();
    }

    private void bindRangeSelector() {
        if (btnRange3m != null) {
            btnRange3m.setOnClickListener(v -> applyRange(RANGE_3M));
        }
        if (btnRange6m != null) {
            btnRange6m.setOnClickListener(v -> applyRange(RANGE_6M));
        }
        if (btnRange12m != null) {
            btnRange12m.setOnClickListener(v -> applyRange(RANGE_12M));
        }
    }

    private void applyRange(int months) {
        if (selectedRangeMonths == months) {
            return;
        }
        selectedRangeMonths = months;
        String languageTag = LocaleManager.getCurrentLanguage(requireContext());
        if (latestStats != null) {
            renderStats(latestStats, true, languageTag);
            resetAiInsightsState(languageTag);
        }
    }

    private void loadStats() {
        adminStatsRepository.fetchDashboardStats(new AdminStatsRepository.StatsCallback() {
            @Override
            public void onSuccess(AdminDashboardStats stats) {
                if (!isAdded()) {
                    return;
                }
                String languageTag = LocaleManager.getCurrentLanguage(requireContext());
                renderStats(stats, true, languageTag);
                resetAiInsightsState(languageTag);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                if (tvChartSubtitle != null) {
                    tvChartSubtitle.setText(R.string.admin_overview_chart_subtitle_error);
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetAiInsightsState(String languageTag) {
        Context localized = LocaleManager.createLocalizedContext(requireContext(), languageTag);
        setAiInsightsLoading(false, languageTag);
        
        // Show Welcome (Empty) state, Hide Results
        if (layoutAiEmptyState != null) layoutAiEmptyState.setVisibility(View.VISIBLE);
        if (layoutAiResultState != null) layoutAiResultState.setVisibility(View.GONE);
        
        if (tvAiInsightsSubtitle != null) {
            tvAiInsightsSubtitle.setText(localized.getString(R.string.admin_ai_insights_tap_to_analyze));
        }
        if (tvAiSummary != null) tvAiSummary.setText("");
        if (tvAiInsightLine1 != null) tvAiInsightLine1.setText("");
        if (tvAiInsightLine2 != null) tvAiInsightLine2.setText("");
        if (tvAiInsightLine3 != null) tvAiInsightLine3.setText("");
        if (tvAiRecommendation != null) tvAiRecommendation.setText("");
        
        refreshAnalyzeButtonState(languageTag);
    }

    private void startAnalyzeCooldown(String languageTag) {
        aiCooldownUntilMillis = System.currentTimeMillis() + AI_ANALYZE_COOLDOWN_MS;
        if (aiCooldownTimer != null) {
            aiCooldownTimer.cancel();
        }
        aiCooldownTimer = new CountDownTimer(AI_ANALYZE_COOLDOWN_MS, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!isAdded()) {
                    return;
                }
                refreshAnalyzeButtonState(languageTag);
            }

            @Override
            public void onFinish() {
                aiCooldownUntilMillis = 0L;
                if (!isAdded()) {
                    return;
                }
                refreshAnalyzeButtonState(languageTag);
            }
        };
        aiCooldownTimer.start();
        refreshAnalyzeButtonState(languageTag);
    }

    private void refreshAnalyzeButtonState(String languageTag) {
        if (btnAiAnalyze == null || !isAdded()) {
            return;
        }
        Context localized = LocaleManager.createLocalizedContext(requireContext(), languageTag);
        long remainingMillis = aiCooldownUntilMillis - System.currentTimeMillis();
        int remainingSeconds = (int) Math.ceil(Math.max(0L, remainingMillis) / 1000d);
        boolean inCooldown = remainingSeconds > 0;
        boolean enabled = !aiInsightsLoading && !inCooldown;
        btnAiAnalyze.setEnabled(enabled);
        if (inCooldown) {
            btnAiAnalyze.setText(localized.getString(R.string.admin_ai_insights_analyze_cooldown_format, remainingSeconds));
        } else {
            btnAiAnalyze.setText(localized.getString(R.string.admin_ai_insights_analyze_cta));
        }
    }

    private void renderStats(AdminDashboardStats stats, boolean animateVisuals, String languageTag) {
        latestStats = stats;
        // Notify Activity so stat card clicks have fresh data
        if (getActivity() instanceof AdminDashboardActivity) {
            ((AdminDashboardActivity) getActivity()).onStatsLoaded(stats);
        }
        Context localized = LocaleManager.createLocalizedContext(requireContext(), languageTag);

        if (tvStatAccounts != null) tvStatAccounts.setText(String.valueOf(stats.getTotalAccounts()));
        if (tvStatImageDownloads != null) tvStatImageDownloads.setText(String.valueOf(stats.getImageDownloads()));
        if (tvStatVideoDownloads != null) tvStatVideoDownloads.setText(String.valueOf(stats.getVideoDownloads()));

        if (tvBigAvg != null) tvBigAvg.setText(String.format(Locale.getDefault(), "%.1f", stats.getAverageRating()));
        if (tvBigTotal != null) tvBigTotal.setText(String.valueOf(stats.getTotalReviews()));
        if (tvBig5Star != null) tvBig5Star.setText(String.valueOf(stats.getFiveStarCount()));
        if (pieAiRatioMini != null) {
            pieAiRatioMini.setData(stats.getAiRegisteredUsers(), stats.getTotalAccounts());
        }
        if (tvAiRatioMiniPercent != null) {
            tvAiRatioMiniPercent.setText(localized.getString(
                    R.string.admin_ai_ratio_percent_format,
                    stats.getAiRegisteredPercent()));
        }
        if (tvAiRatioMiniMeta != null) {
            tvAiRatioMiniMeta.setText(localized.getString(
                    R.string.admin_ai_overview_meta_compact,
                    stats.getAiRegisteredUsers(),
                    stats.getAiNotRegisteredUsers()));
        }
        if (tvChartSubtitle != null) {
            tvChartSubtitle.setText(localized.getString(R.string.admin_overview_chart_subtitle_format, stats.getTotalReviews()));
        }

        if (tvLastReviewAt != null) {
            long lastReviewAt = stats.getLastReviewAtMillis();
            if (lastReviewAt > 0L) {
                String formatted = DateFormat.format("dd/MM/yyyy - HH:mm", new Date(lastReviewAt)).toString();
                tvLastReviewAt.setText(localized.getString(R.string.admin_overview_last_review_format, formatted));
            } else {
                tvLastReviewAt.setText(localized.getString(R.string.admin_overview_last_review_fallback));
            }
        }

        int[] counts = stats.getRatingCounts();
        if (tv5 != null) tv5.setText(String.valueOf(counts[5]));
        if (tv4 != null) tv4.setText(String.valueOf(counts[4]));
        if (tv3 != null) tv3.setText(String.valueOf(counts[3]));
        if (tv2 != null) tv2.setText(String.valueOf(counts[2]));
        if (tv1 != null) tv1.setText(String.valueOf(counts[1]));

        if (animateVisuals) {
            animateBar(bar5, counts[5], stats.getTotalReviews());
            animateBar(bar4, counts[4], stats.getTotalReviews());
            animateBar(bar3, counts[3], stats.getTotalReviews());
            animateBar(bar2, counts[2], stats.getTotalReviews());
            animateBar(bar1, counts[1], stats.getTotalReviews());
        } else {
            setBarWidthInstant(bar5, counts[5], stats.getTotalReviews());
            setBarWidthInstant(bar4, counts[4], stats.getTotalReviews());
            setBarWidthInstant(bar3, counts[3], stats.getTotalReviews());
            setBarWidthInstant(bar2, counts[2], stats.getTotalReviews());
            setBarWidthInstant(bar1, counts[1], stats.getTotalReviews());
        }

        LinkedHashMap<String, Integer> usersSlice = keepLatest(stats.getUsersByMonth(), selectedRangeMonths);
        LinkedHashMap<String, Integer> imageSlice = keepLatest(stats.getImageDownloadsByMonth(), selectedRangeMonths);
        LinkedHashMap<String, Double> reviewSlice = keepLatest(stats.getReviewScoreByMonth(), selectedRangeMonths);

        bindChart(
                chartUsersByMonth,
                tvUsersByMonthEmpty,
                toIntChartPoints(usersSlice),
                Color.parseColor("#5A8AEE"), // Muted Blue Start
                Color.parseColor("#4266B8"), // Muted Blue End
                localized.getString(R.string.admin_overview_legend_users),
                animateVisuals
        );

        bindChart(
                chartImageDownloadsByMonth,
                tvImageDownloadsByMonthEmpty,
                toIntChartPoints(imageSlice),
                Color.parseColor("#EDAA66"), // Earthy Gold Start
                Color.parseColor("#C7843C"), // Earthy Gold End
                localized.getString(R.string.admin_overview_legend_downloads),
                animateVisuals
        );

        bindChart(
                chartReviewScoreByMonth,
                tvReviewScoreByMonthEmpty,
                toDoubleChartPoints(reviewSlice),
                Color.parseColor("#E86D6D"), // Muted Coral Start
                Color.parseColor("#B84949"), // Muted Coral End
                localized.getString(R.string.admin_overview_legend_review_score),
                animateVisuals
        );
    }

    private void showAiRatioDetail() {
        if (latestStats == null || getContext() == null) return;

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_stat_detail, null);
        sheet.setContentView(view);

        // Đảm bảo background trong suốt để hiện góc bo tròn của layout custom
        sheet.setOnShowListener(dialog -> {
            com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;
            View bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        com.google.android.material.card.MaterialCardView iconCard = view.findViewById(R.id.bsStatIconCard);
        android.widget.ImageView icon = view.findViewById(R.id.bsStatIcon);
        TextView title       = view.findViewById(R.id.bsStatTitle);
        TextView subtitle    = view.findViewById(R.id.bsStatSubtitle);
        TextView bigNumber   = view.findViewById(R.id.bsStatBigNumber);
        android.widget.LinearLayout detailContainer = view.findViewById(R.id.bsStatDetailContainer);

        int aiColor = android.graphics.Color.parseColor("#8B5CF6");
        iconCard.setCardBackgroundColor(aiColor);
        icon.setImageResource(R.drawable.ic_sparkle);
        title.setText("Tỷ lệ đăng ký AI");
        title.setTextColor(aiColor);
        bigNumber.setTextColor(aiColor);
        bigNumber.setText(String.format(java.util.Locale.getDefault(),
                "%.0f%%", latestStats.getAiRegisteredPercent()));
        subtitle.setText("Người dùng đã bật tính năng AI");

        addFragmentDetailRow(detailContainer, view,
                "🤖 Đã đăng ký AI",
                String.valueOf(latestStats.getAiRegisteredUsers()),
                "#8B5CF6");
        addFragmentDetailRow(detailContainer, view,
                "👤 Chưa đăng ký",
                String.valueOf(latestStats.getAiNotRegisteredUsers()),
                "#6B7280");
        addFragmentDetailRow(detailContainer, view,
                "📊 Tỷ lệ phần trăm",
                String.format(java.util.Locale.getDefault(), "%.1f%%", latestStats.getAiRegisteredPercent()),
                "#06B6D4");
        addFragmentDetailRow(detailContainer, view,
                "👥 Tổng người dùng",
                String.valueOf(latestStats.getTotalAccounts()),
                "#4A6CF7");

        view.findViewById(R.id.bsStatClose).setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    private void addFragmentDetailRow(android.widget.LinearLayout container, View sheetRoot,
                                       String label, String value, String colorHex) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_stat_detail_row, null);
        TextView tvLabel = row.findViewById(R.id.tvDetailRowLabel);
        TextView tvValue = row.findViewById(R.id.tvDetailRowValue);
        View indicator   = row.findViewById(R.id.viewDetailRowIndicator);
        tvLabel.setText(label);
        tvValue.setText(value);
        indicator.setBackgroundColor(android.graphics.Color.parseColor(colorHex));

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int)(8 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(lp);
        container.addView(row);
    }

    private void showChartDetail(String type) {
        if (latestStats == null || getContext() == null) return;

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_stat_detail, null);
        sheet.setContentView(view);

        sheet.setOnShowListener(dialog -> {
            com.google.android.material.bottomsheet.BottomSheetDialog d = (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;
            View bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        com.google.android.material.card.MaterialCardView iconCard = view.findViewById(R.id.bsStatIconCard);
        android.widget.ImageView icon = view.findViewById(R.id.bsStatIcon);
        TextView title       = view.findViewById(R.id.bsStatTitle);
        TextView subtitle    = view.findViewById(R.id.bsStatSubtitle);
        TextView bigNumber   = view.findViewById(R.id.bsStatBigNumber);
        android.widget.LinearLayout detailContainer = view.findViewById(R.id.bsStatDetailContainer);

        java.util.List<Integer> monthlyData;
        String colorHex;
        int iconRes;
        String titleStr;
        String unit;

        switch (type) {
            case "USERS":
                monthlyData = latestStats.getMonthlyUsers();
                colorHex = "#4A6CF7";
                iconRes = R.drawable.ic_person_24;
                titleStr = "Phân tích Tài khoản";
                unit = "người";
                bigNumber.setText(String.valueOf(latestStats.getTotalAccounts()));
                subtitle.setText("Tổng số người dùng hệ thống");
                break;
            case "DOWNLOADS":
                monthlyData = latestStats.getMonthlyImageDownloads();
                colorHex = "#EDAA66";
                iconRes = R.drawable.ic_download_24;
                titleStr = "Lượt tải ảnh & Video";
                unit = "lượt";
                bigNumber.setText(String.valueOf(latestStats.getTotalImageDownloads()));
                subtitle.setText("Tổng lượt tải về máy");
                break;
            case "REVIEWS":
                monthlyData = latestStats.getMonthlyReviewScores();
                colorHex = "#E86D6D";
                iconRes = R.drawable.ic_star_24;
                titleStr = "Đánh giá dịch vụ";
                unit = "điểm";
                bigNumber.setText(String.format(java.util.Locale.getDefault(), "%.1f", 
                    latestStats.getTotalAccounts() > 0 ? (latestStats.getTotalImageDownloads() / 100.0) : 4.8)); // Mock logic
                subtitle.setText("Điểm hài lòng trung bình");
                break;
            default: return;
        }

        iconCard.setCardBackgroundColor(android.graphics.Color.parseColor(colorHex));
        icon.setImageResource(iconRes);
        title.setText(titleStr);
        title.setTextColor(android.graphics.Color.parseColor(colorHex));
        bigNumber.setTextColor(android.graphics.Color.parseColor(colorHex));

        // Analysis
        int max = 0;
        int sum = 0;
        for (int v : monthlyData) {
            if (v > max) max = v;
            sum += v;
        }
        double avg = monthlyData.isEmpty() ? 0 : (double)sum / monthlyData.size();

        addFragmentDetailRow(detailContainer, view, "📈 Cao nhất", String.valueOf(max) + " " + unit, colorHex);
        addFragmentDetailRow(detailContainer, view, "📊 Trung bình tháng", String.format("%.1f ", avg) + unit, "#6B7280");
        addFragmentDetailRow(detailContainer, view, "📅 Chu kỳ phân tích", monthlyData.size() + " tháng", "#06B6D4");

        // Monthly list
        TextView tvTrend = new TextView(requireContext());
        tvTrend.setText("XU HƯỚNG THEO THÁNG");
        tvTrend.setTextSize(12f);
        tvTrend.setPadding(0, 24, 0, 16);
        tvTrend.setTextColor(android.graphics.Color.parseColor("#9CA3AF"));
        tvTrend.setLetterSpacing(0.1f);
        detailContainer.addView(tvTrend);

        String[] months = {"T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8", "T9", "T10", "T11", "T12"};
        for (int i = 0; i < monthlyData.size(); i++) {
            String mName = months[i % 12];
            addFragmentDetailRow(detailContainer, view, "Tháng " + mName, 
                String.valueOf(monthlyData.get(i)) + " " + unit, 
                i == monthlyData.size() - 1 ? colorHex : "#E5E7EB");
        }

        view.findViewById(R.id.bsStatClose).setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    private void loadAiInsights(AdminDashboardStats stats, String languageTag) {
        if (adminAiInsightsRepository == null || stats == null) {
            return;
        }

        int requestId = ++aiRequestSerial;
        setAiInsightsLoading(true, languageTag);

        AdminAiInsightRequest request = new AdminAiInsightRequest(
                selectedRangeMonths,
                languageTag,
                keepLatest(stats.getUsersByMonth(), selectedRangeMonths),
                keepLatest(stats.getImageDownloadsByMonth(), selectedRangeMonths),
                keepLatest(stats.getReviewScoreByMonth(), selectedRangeMonths),
                stats.getAiRegisteredUsers(),
                stats.getAiNotRegisteredUsers()
        );

        adminAiInsightsRepository.fetchInsights(request, new AdminAiInsightsRepository.Callback() {
            @Override
            public void onSuccess(AdminAiInsights insights) {
                if (!isAdded() || requestId != aiRequestSerial) {
                    return;
                }
                renderAiInsights(insights, languageTag);
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || requestId != aiRequestSerial) {
                    return;
                }
                setAiInsightsLoading(false, languageTag);
                if (tvAiInsightsSubtitle != null) {
                    tvAiInsightsSubtitle.setText(getString(R.string.admin_ai_insights_error));
                }
                if (tvAiSummary != null) {
                    tvAiSummary.setText(message == null || message.trim().isEmpty()
                            ? getString(R.string.admin_ai_insights_error)
                            : message.trim());
                }
            }
        });
    }

    private void setAiInsightsLoading(boolean loading, String languageTag) {
        aiInsightsLoading = loading;
        Context localized = LocaleManager.createLocalizedContext(requireContext(), languageTag);
        if (progressAiInsights != null) {
            progressAiInsights.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        refreshAnalyzeButtonState(languageTag);
        
        if (loading) {
            if (tvAiInsightsSubtitle != null) {
                tvAiInsightsSubtitle.setText(localized.getString(R.string.admin_ai_insights_loading));
            }
            // Dim the card slightly while loading
            if (layoutAiEmptyState != null) layoutAiEmptyState.setAlpha(0.5f);
            if (layoutAiResultState != null) layoutAiResultState.setAlpha(0.5f);
        } else {
            if (layoutAiEmptyState != null) layoutAiEmptyState.setAlpha(1.0f);
            if (layoutAiResultState != null) layoutAiResultState.setAlpha(1.0f);
        }
    }

    private void renderAiInsights(AdminAiInsights insights, String languageTag) {
        Context localized = LocaleManager.createLocalizedContext(requireContext(), languageTag);
        setAiInsightsLoading(false, languageTag);

        if (insights == null) {
            if (tvAiInsightsSubtitle != null) {
                tvAiInsightsSubtitle.setText(localized.getString(R.string.admin_ai_insights_error));
            }
            return;
        }

        // Show Result state, Hide Empty state
        if (layoutAiEmptyState != null) layoutAiEmptyState.setVisibility(View.GONE);
        if (layoutAiResultState != null) layoutAiResultState.setVisibility(View.VISIBLE);

        String source = insights.getSourceLabel();
        String confidence = localized.getString(R.string.admin_ai_insights_confidence, insights.getConfidence() * 100d);
        if (tvAiInsightsSubtitle != null) {
            tvAiInsightsSubtitle.setText(source.isEmpty() ? confidence : source + " | " + confidence);
        }

        if (tvAiSummary != null) {
            tvAiSummary.setText(insights.getSummary());
        }

        List<String> lines = insights.getInsights();
        if (tvAiInsightLine1 != null) {
            tvAiInsightLine1.setText(lines.size() > 0 ? "- " + lines.get(0) : "");
        }
        if (tvAiInsightLine2 != null) {
            tvAiInsightLine2.setText(lines.size() > 1 ? "- " + lines.get(1) : "");
        }
        if (tvAiInsightLine3 != null) {
            tvAiInsightLine3.setText(lines.size() > 2 ? "- " + lines.get(2) : "");
        }

        List<String> actions = insights.getRecommendations();
        if (tvAiRecommendation != null) {
            if (!actions.isEmpty()) {
                tvAiRecommendation.setText(localized.getString(
                        R.string.admin_ai_insights_recommendation_format,
                        actions.get(0)));
            } else {
                tvAiRecommendation.setText("");
            }
        }
    }

    private void bindChart(
            MonthlyBarChartView chartView,
            TextView emptyView,
            List<MonthlyChartPoint> points,
            int startColor,
            int endColor,
            String legend,
            boolean animate
    ) {
        if (chartView == null || emptyView == null) {
            return;
        }
        if (points == null || points.isEmpty()) {
            chartView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);
        chartView.setVisibility(View.VISIBLE);
        chartView.setBarColors(startColor, endColor);
        chartView.setShowYAxis(true);
        chartView.setLegendText(legend);
        chartView.setChartData(points, animate);
    }

    private List<MonthlyChartPoint> toIntChartPoints(LinkedHashMap<String, Integer> data) {
        List<MonthlyChartPoint> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            int value = entry.getValue() == null ? 0 : entry.getValue();
            list.add(new MonthlyChartPoint(formatMonthKey(entry.getKey()), value, String.valueOf(value)));
        }
        return list;
    }

    private List<MonthlyChartPoint> toDoubleChartPoints(LinkedHashMap<String, Double> data) {
        List<MonthlyChartPoint> list = new ArrayList<>();
        for (Map.Entry<String, Double> entry : data.entrySet()) {
            double value = entry.getValue() == null ? 0d : entry.getValue();
            list.add(new MonthlyChartPoint(
                    formatMonthKey(entry.getKey()),
                    (float) value,
                    String.format(Locale.getDefault(), "%.1f", value)
            ));
        }
        return list;
    }

    private <T> LinkedHashMap<String, T> keepLatest(LinkedHashMap<String, T> source, int limit) {
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        List<Map.Entry<String, T>> entries = new ArrayList<>(source.entrySet());
        int start = Math.max(0, entries.size() - limit);
        for (int i = start; i < entries.size(); i++) {
            Map.Entry<String, T> entry = entries.get(i);
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String formatMonthKey(String key) {
        if (key == null || key.length() != 7 || key.charAt(4) != '-') {
            return key == null ? "--/--" : key;
        }
        return key.substring(5, 7) + "/" + key.substring(2, 4);
    }

    private void animateBar(View bar, int count, int total) {
        if (bar == null || bar.getParent() == null) {
            return;
        }
        bar.post(() -> {
            int parentWidth = ((View) bar.getParent()).getWidth();
            int targetWidth = total > 0 ? (int) ((count / (float) total) * parentWidth) : 0;

            ValueAnimator anim = ValueAnimator.ofInt(0, targetWidth);
            anim.setDuration(700);
            anim.setStartDelay(120);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(a -> {
                ViewGroup.LayoutParams lp = bar.getLayoutParams();
                lp.width = (int) a.getAnimatedValue();
                bar.setLayoutParams(lp);
            });
            anim.start();
        });
    }

    private void setBarWidthInstant(View bar, int count, int total) {
        if (bar == null || bar.getParent() == null) {
            return;
        }
        bar.post(() -> {
            int parentWidth = ((View) bar.getParent()).getWidth();
            int targetWidth = total > 0 ? (int) ((count / (float) total) * parentWidth) : 0;
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.width = targetWidth;
            bar.setLayoutParams(lp);
        });
    }

    @Override
    public void onRuntimeLanguageChanged(@NonNull String languageTag) {
        if (!isAdded()) {
            return;
        }

        Context localized = LocaleManager.createLocalizedContext(requireContext(), languageTag);

        if (tvTotalReviewsLabel != null) tvTotalReviewsLabel.setText(localized.getString(R.string.admin_overview_total_reviews));
        if (tvFiveStarLabel != null) tvFiveStarLabel.setText(localized.getString(R.string.admin_overview_five_star_label));
        if (tvRangeTitle != null) tvRangeTitle.setText(localized.getString(R.string.admin_overview_range_title));
        if (tvAiRatioMiniTitle != null) tvAiRatioMiniTitle.setText(localized.getString(R.string.admin_ai_pie_title));
        if (tvAiInsightsTitle != null) tvAiInsightsTitle.setText(localized.getString(R.string.admin_ai_insights_title));
        refreshAnalyzeButtonState(languageTag);
        if (tvUsersChartTitle != null) tvUsersChartTitle.setText(localized.getString(R.string.admin_overview_users_chart_title));
        if (tvUsersChartSubtitle != null) tvUsersChartSubtitle.setText(localized.getString(R.string.admin_overview_users_chart_subtitle));
        if (tvDownloadsChartTitle != null) tvDownloadsChartTitle.setText(localized.getString(R.string.admin_overview_download_chart_title));
        if (tvDownloadsChartSubtitle != null) tvDownloadsChartSubtitle.setText(localized.getString(R.string.admin_overview_download_chart_subtitle));
        if (tvReviewScoreChartTitle != null) tvReviewScoreChartTitle.setText(localized.getString(R.string.admin_overview_review_score_chart_title));
        if (tvReviewScoreChartSubtitle != null) tvReviewScoreChartSubtitle.setText(localized.getString(R.string.admin_overview_review_score_chart_subtitle));
        if (tvBar5Label != null) tvBar5Label.setText(localized.getString(R.string.admin_overview_bar_five));
        if (tvBar4Label != null) tvBar4Label.setText(localized.getString(R.string.admin_overview_bar_four));
        if (tvBar3Label != null) tvBar3Label.setText(localized.getString(R.string.admin_overview_bar_three));
        if (tvBar2Label != null) tvBar2Label.setText(localized.getString(R.string.admin_overview_bar_two));
        if (tvBar1Label != null) tvBar1Label.setText(localized.getString(R.string.admin_overview_bar_one));
        if (btnRange3m != null) btnRange3m.setText(localized.getString(R.string.admin_overview_range_3m));
        if (btnRange6m != null) btnRange6m.setText(localized.getString(R.string.admin_overview_range_6m));
        if (btnRange12m != null) btnRange12m.setText(localized.getString(R.string.admin_overview_range_12m));
        if (tvUsersByMonthEmpty != null) tvUsersByMonthEmpty.setText(localized.getString(R.string.admin_overview_trend_empty));
        if (tvImageDownloadsByMonthEmpty != null) tvImageDownloadsByMonthEmpty.setText(localized.getString(R.string.admin_overview_trend_empty));
        if (tvReviewScoreByMonthEmpty != null) tvReviewScoreByMonthEmpty.setText(localized.getString(R.string.admin_overview_trend_empty));
        if (tvAiChatPrompt != null) tvAiChatPrompt.setText(localized.getString(R.string.admin_ai_chat_search_hint));
        if (tvAiEmptyTitle != null) tvAiEmptyTitle.setText(localized.getString(R.string.admin_ai_insights_empty_title));
        if (tvAiEmptyDesc != null) tvAiEmptyDesc.setText(localized.getString(R.string.admin_ai_insights_empty_desc));

        if (latestStats != null) {
            renderStats(latestStats, false, languageTag);
            resetAiInsightsState(languageTag);
        }
    }

    private void updateSystemQuotas() {
        if (progressQuotaGemini != null) {
            progressQuotaGemini.setProgress(28);
        }
        if (tvQuotaGemini != null) {
            tvQuotaGemini.setText("Healthy (28%)");
            tvQuotaGemini.setTextColor(Color.parseColor("#27A869"));
        }

        if (progressQuotaStorage != null) {
            progressQuotaStorage.setProgress(42);
        }
        if (tvQuotaStorage != null) {
            tvQuotaStorage.setText("42.5 GB / 100 GB");
            tvQuotaStorage.setTextColor(Color.GRAY);
        }
    }
}
