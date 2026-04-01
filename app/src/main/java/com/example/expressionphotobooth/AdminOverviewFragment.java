package com.example.expressionphotobooth;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.expressionphotobooth.domain.model.AdminDashboardStats;
import com.example.expressionphotobooth.domain.repository.AdminStatsRepository;
import com.example.expressionphotobooth.ui.chart.MonthlyBarChartView;
import com.example.expressionphotobooth.ui.chart.MonthlyChartPoint;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminOverviewFragment extends Fragment {

    private static final int RANGE_3M = 3;
    private static final int RANGE_6M = 6;
    private static final int RANGE_12M = 12;

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

    private TextView tvStatAccounts;
    private TextView tvStatImageDownloads;
    private TextView tvStatVideoDownloads;

    private MonthlyBarChartView chartUsersByMonth;
    private MonthlyBarChartView chartImageDownloadsByMonth;
    private MonthlyBarChartView chartReviewScoreByMonth;

    private TextView tvUsersByMonthEmpty;
    private TextView tvImageDownloadsByMonthEmpty;
    private TextView tvReviewScoreByMonthEmpty;

    private AdminStatsRepository adminStatsRepository;
    private AdminDashboardStats latestStats;
    private int selectedRangeMonths = RANGE_6M;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adminStatsRepository = ((AppContainer) requireActivity().getApplication()).getAdminStatsRepository();

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
        tvChartSubtitle = view.findViewById(R.id.tvChartSubtitle);
        tvLastReviewAt = view.findViewById(R.id.tvLastReviewAt);

        chartUsersByMonth = view.findViewById(R.id.chartUsersByMonth);
        chartImageDownloadsByMonth = view.findViewById(R.id.chartImageDownloadsByMonth);
        chartReviewScoreByMonth = view.findViewById(R.id.chartReviewScoreByMonth);

        tvUsersByMonthEmpty = view.findViewById(R.id.tvUsersByMonthEmpty);
        tvImageDownloadsByMonthEmpty = view.findViewById(R.id.tvImageDownloadsByMonthEmpty);
        tvReviewScoreByMonthEmpty = view.findViewById(R.id.tvReviewScoreByMonthEmpty);

        bindRangeSelector(view);

        if (getActivity() != null) {
            tvStatAccounts = getActivity().findViewById(R.id.tvStatTotalAccounts);
            tvStatImageDownloads = getActivity().findViewById(R.id.tvStatImageDownloads);
            tvStatVideoDownloads = getActivity().findViewById(R.id.tvStatVideoDownloads);
        }

        loadStats();
    }

    private void bindRangeSelector(View root) {
        View btn3m = root.findViewById(R.id.btnRange3m);
        View btn6m = root.findViewById(R.id.btnRange6m);
        View btn12m = root.findViewById(R.id.btnRange12m);

        if (btn3m != null) {
            btn3m.setOnClickListener(v -> applyRange(RANGE_3M));
        }
        if (btn6m != null) {
            btn6m.setOnClickListener(v -> applyRange(RANGE_6M));
        }
        if (btn12m != null) {
            btn12m.setOnClickListener(v -> applyRange(RANGE_12M));
        }
    }

    private void applyRange(int months) {
        if (selectedRangeMonths == months) {
            return;
        }
        selectedRangeMonths = months;
        if (latestStats != null) {
            renderStats(latestStats);
        }
    }

    private void loadStats() {
        adminStatsRepository.fetchDashboardStats(new AdminStatsRepository.StatsCallback() {
            @Override
            public void onSuccess(AdminDashboardStats stats) {
                if (!isAdded()) {
                    return;
                }
                renderStats(stats);
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

    private void renderStats(AdminDashboardStats stats) {
        latestStats = stats;

        if (tvStatAccounts != null) tvStatAccounts.setText(String.valueOf(stats.getTotalAccounts()));
        if (tvStatImageDownloads != null) tvStatImageDownloads.setText(String.valueOf(stats.getImageDownloads()));
        if (tvStatVideoDownloads != null) tvStatVideoDownloads.setText(String.valueOf(stats.getVideoDownloads()));

        if (tvBigAvg != null) tvBigAvg.setText(String.format(Locale.getDefault(), "%.1f", stats.getAverageRating()));
        if (tvBigTotal != null) tvBigTotal.setText(String.valueOf(stats.getTotalReviews()));
        if (tvBig5Star != null) tvBig5Star.setText(String.valueOf(stats.getFiveStarCount()));
        if (tvChartSubtitle != null) {
            tvChartSubtitle.setText(getString(R.string.admin_overview_chart_subtitle_format, stats.getTotalReviews()));
        }

        if (tvLastReviewAt != null) {
            long lastReviewAt = stats.getLastReviewAtMillis();
            if (lastReviewAt > 0L) {
                String formatted = DateFormat.format("dd/MM/yyyy - HH:mm", new Date(lastReviewAt)).toString();
                tvLastReviewAt.setText(getString(R.string.admin_overview_last_review_format, formatted));
            } else {
                tvLastReviewAt.setText(R.string.admin_overview_last_review_fallback);
            }
        }

        int[] counts = stats.getRatingCounts();
        if (tv5 != null) tv5.setText(String.valueOf(counts[5]));
        if (tv4 != null) tv4.setText(String.valueOf(counts[4]));
        if (tv3 != null) tv3.setText(String.valueOf(counts[3]));
        if (tv2 != null) tv2.setText(String.valueOf(counts[2]));
        if (tv1 != null) tv1.setText(String.valueOf(counts[1]));

        animateBar(bar5, counts[5], stats.getTotalReviews());
        animateBar(bar4, counts[4], stats.getTotalReviews());
        animateBar(bar3, counts[3], stats.getTotalReviews());
        animateBar(bar2, counts[2], stats.getTotalReviews());
        animateBar(bar1, counts[1], stats.getTotalReviews());

        LinkedHashMap<String, Integer> usersSlice = keepLatest(stats.getUsersByMonth(), selectedRangeMonths);
        LinkedHashMap<String, Integer> imageSlice = keepLatest(stats.getImageDownloadsByMonth(), selectedRangeMonths);
        LinkedHashMap<String, Double> reviewSlice = keepLatest(stats.getReviewScoreByMonth(), selectedRangeMonths);

        bindChart(
                chartUsersByMonth,
                tvUsersByMonthEmpty,
                toIntChartPoints(usersSlice),
                ContextCompat.getColor(requireContext(), R.color.app_blue),
                getString(R.string.admin_overview_legend_users)
        );

        bindChart(
                chartImageDownloadsByMonth,
                tvImageDownloadsByMonthEmpty,
                toIntChartPoints(imageSlice),
                ContextCompat.getColor(requireContext(), R.color.app_pink),
                getString(R.string.admin_overview_legend_downloads)
        );

        bindChart(
                chartReviewScoreByMonth,
                tvReviewScoreByMonthEmpty,
                toDoubleChartPoints(reviewSlice),
                Color.parseColor("#F5A623"),
                getString(R.string.admin_overview_legend_review_score)
        );
    }

    private void bindChart(
            MonthlyBarChartView chartView,
            TextView emptyView,
            List<MonthlyChartPoint> points,
            int color,
            String legend
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
        chartView.setBarColor(color);
        chartView.setShowYAxis(true);
        chartView.setLegendText(legend);
        chartView.setChartData(points, true);
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
}
