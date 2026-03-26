package com.example.expressionphotobooth;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public final class HelpDialogUtils {

    private HelpDialogUtils() {
    }

    public static void showPhotoboothHelp(Context context, String title, String subtitle, List<String> bullets) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_help_photobooth, null, false);

        TextView tvTitle = dialogView.findViewById(R.id.tvHelpTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvHelpSubtitle);
        TextView[] bulletTexts = new TextView[] {
                dialogView.findViewById(R.id.tvBullet1),
                dialogView.findViewById(R.id.tvBullet2),
                dialogView.findViewById(R.id.tvBullet3),
                dialogView.findViewById(R.id.tvBullet4)
        };
        View[] bulletRows = new View[] {
                dialogView.findViewById(R.id.rowBullet1),
                dialogView.findViewById(R.id.rowBullet2),
                dialogView.findViewById(R.id.rowBullet3),
                dialogView.findViewById(R.id.rowBullet4)
        };
        MaterialButton btnGotIt = dialogView.findViewById(R.id.btnGotIt);

        tvTitle.setText(title);
        if (TextUtils.isEmpty(subtitle)) {
            tvSubtitle.setVisibility(View.GONE);
        } else {
            tvSubtitle.setVisibility(View.VISIBLE);
            tvSubtitle.setText(subtitle);
        }

        int bulletCount = bullets == null ? 0 : Math.min(bullets.size(), bulletTexts.length);
        for (int i = 0; i < bulletRows.length; i++) {
            if (i < bulletCount) {
                bulletRows[i].setVisibility(View.VISIBLE);
                bulletTexts[i].setText(bullets.get(i));
            } else {
                bulletRows[i].setVisibility(View.GONE);
            }
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .create();

        btnGotIt.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
