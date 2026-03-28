package com.example.expressionphotobooth;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

public class AdminRedirectFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_redirect, container, false);

        MaterialButton btnGoToReviews = view.findViewById(R.id.btnGoToReviews);
        btnGoToReviews.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), AdminReviewsActivity.class));
        });

        return view;
    }
}
