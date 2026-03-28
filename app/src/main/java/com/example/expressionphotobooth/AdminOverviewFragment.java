package com.example.expressionphotobooth;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Locale;

public class AdminOverviewFragment extends Fragment {

    private View bar5, bar4, bar3, bar2, bar1;
    private TextView tv5, tv4, tv3, tv2, tv1;
    private TextView tvBigAvg, tvBigTotal, tvBig5Star, tvChartSubtitle;
    private TextView tvStatTotal, tvStatAvg, tvStatLow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Chart bars
        bar5 = view.findViewById(R.id.bar5Star);
        bar4 = view.findViewById(R.id.bar4Star);
        bar3 = view.findViewById(R.id.bar3Star);
        bar2 = view.findViewById(R.id.bar2Star);
        bar1 = view.findViewById(R.id.bar1Star);

        // Chart counts
        tv5 = view.findViewById(R.id.tv5StarCount);
        tv4 = view.findViewById(R.id.tv4StarCount);
        tv3 = view.findViewById(R.id.tv3StarCount);
        tv2 = view.findViewById(R.id.tv2StarCount);
        tv1 = view.findViewById(R.id.tv1StarCount);

        // Big numbers
        tvBigAvg   = view.findViewById(R.id.tvBigAvg);
        tvBigTotal = view.findViewById(R.id.tvBigTotal);
        tvBig5Star = view.findViewById(R.id.tvBig5Star);
        tvChartSubtitle = view.findViewById(R.id.tvChartSubtitle);

        // Header stat cards (in the Activity's layout)
        if (getActivity() != null) {
            tvStatTotal = getActivity().findViewById(R.id.tvStatTotalReviews);
            tvStatAvg   = getActivity().findViewById(R.id.tvStatAvgScore);
            tvStatLow   = getActivity().findViewById(R.id.tvStatLow);
        }

        // Action cards
        view.findViewById(R.id.cardActionUserFlow).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), HomeActivity.class));
            requireActivity().finish();
        });

        view.findViewById(R.id.cardActionReviews).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), AdminReviewsActivity.class));
        });

        view.findViewById(R.id.cardActionSettings).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Cài đặt hệ thống", Toast.LENGTH_SHORT).show()
        );

        view.findViewById(R.id.cardActionSignOut).setOnClickListener(v -> {
            AuthRepository authRepository = ((AppContainer) requireActivity().getApplication()).getAuthRepository();
            authRepository.signOut();
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finish();
        });

        loadStats();
    }

    private void loadStats() {
        FirebaseFirestore.getInstance().collection("reviews")
                .get()
                .addOnSuccessListener(snap -> {
                    int total = snap.size();
                    int[] counts = new int[6]; // index 1–5
                    double scoreSum = 0;

                    for (QueryDocumentSnapshot doc : snap) {
                        Double r = doc.getDouble("rating");
                        if (r != null) {
                            int s = (int) Math.round(r);
                            if (s >= 1 && s <= 5) counts[s]++;
                            scoreSum += r;
                        }
                    }

                    double avg = total > 0 ? scoreSum / total : 0;
                    int lowCount = counts[1] + counts[2];

                    // Update header stat cards
                    if (tvStatTotal != null) tvStatTotal.setText(String.valueOf(total));
                    if (tvStatAvg != null)
                        tvStatAvg.setText(String.format(Locale.getDefault(), "%.1f", avg));
                    if (tvStatLow != null) tvStatLow.setText(String.valueOf(lowCount));

                    // Big numbers in chart card
                    if (tvBigAvg != null) tvBigAvg.setText(String.format(Locale.getDefault(), "%.1f", avg));
                    if (tvBigTotal != null) tvBigTotal.setText(String.valueOf(total));
                    if (tvBig5Star != null) tvBig5Star.setText(String.valueOf(counts[5]));
                    if (tvChartSubtitle != null) tvChartSubtitle.setText(total + " đánh giá");

                    // Individual counts
                    if (tv5 != null) tv5.setText(String.valueOf(counts[5]));
                    if (tv4 != null) tv4.setText(String.valueOf(counts[4]));
                    if (tv3 != null) tv3.setText(String.valueOf(counts[3]));
                    if (tv2 != null) tv2.setText(String.valueOf(counts[2]));
                    if (tv1 != null) tv1.setText(String.valueOf(counts[1]));

                    // Animate bars
                    if (total > 0) {
                        animateBar(bar5, counts[5], total);
                        animateBar(bar4, counts[4], total);
                        animateBar(bar3, counts[3], total);
                        animateBar(bar2, counts[2], total);
                        animateBar(bar1, counts[1], total);
                    }
                })
                .addOnFailureListener(e -> {
                    if (tvChartSubtitle != null)
                        tvChartSubtitle.setText("Không tải được dữ liệu");
                });
    }

    private void animateBar(View bar, int count, int total) {
        if (bar == null || bar.getParent() == null) return;
        bar.post(() -> {
            int parentWidth = ((View) bar.getParent()).getWidth();
            int targetWidth  = (int) ((count / (float) total) * parentWidth);

            ValueAnimator anim = ValueAnimator.ofInt(0, targetWidth);
            anim.setDuration(700);
            anim.setStartDelay(200);
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
