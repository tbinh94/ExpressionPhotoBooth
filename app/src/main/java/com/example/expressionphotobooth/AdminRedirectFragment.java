package com.example.expressionphotobooth;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.example.expressionphotobooth.utils.LocaleManager;

public class AdminRedirectFragment extends Fragment implements RuntimeLanguageUpdatable {

    private TextView tvRedirectIcon;
    private TextView tvRedirectTitle;
    private TextView tvRedirectSubtitle;
    private MaterialButton btnGoToReviews;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_redirect, container, false);

        tvRedirectIcon = view.findViewById(R.id.tvRedirectIcon);
        tvRedirectTitle = view.findViewById(R.id.tvRedirectTitle);
        tvRedirectSubtitle = view.findViewById(R.id.tvRedirectSubtitle);
        btnGoToReviews = view.findViewById(R.id.btnGoToReviews);
        btnGoToReviews.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), AdminReviewsActivity.class));
        });

        return view;
    }

    @Override
    public void onRuntimeLanguageChanged(@NonNull String languageTag) {
        if (!isAdded()) {
            return;
        }
        Context localized = LocaleManager.createLocalizedContext(requireContext(), languageTag);
        if (tvRedirectIcon != null) {
            tvRedirectIcon.setText(localized.getString(R.string.admin_redirect_icon));
        }
        if (tvRedirectTitle != null) {
            tvRedirectTitle.setText(localized.getString(R.string.admin_redirect_title));
        }
        if (tvRedirectSubtitle != null) {
            tvRedirectSubtitle.setText(localized.getString(R.string.admin_redirect_subtitle));
        }
        if (btnGoToReviews != null) {
            btnGoToReviews.setText(localized.getString(R.string.admin_redirect_open_reviews));
        }
    }
}
