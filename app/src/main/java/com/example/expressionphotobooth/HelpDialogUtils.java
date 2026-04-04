package com.example.expressionphotobooth;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.expressionphotobooth.utils.LocaleManager;

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

    public static void showCenteredNotice(Context context, String title, String message, boolean isPositive) {
        showCenteredNotice(context, title, message, isPositive, null);
    }

    public static void showCenteredNotice(Context context, String title, String message, boolean isPositive, Runnable onDismiss) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_center_notice, null, false);

        ImageView ivIcon = dialogView.findViewById(R.id.ivNoticeIcon);
        TextView tvTitle = dialogView.findViewById(R.id.tvNoticeTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvNoticeMessage);
        MaterialButton btnOk = dialogView.findViewById(R.id.btnNoticeOk);

        ivIcon.setImageResource(isPositive ? R.drawable.ic_check_24 : R.drawable.ic_help_24);
        ivIcon.setColorFilter(Color.parseColor(isPositive ? "#1AA36D" : "#2A5298"));
        tvTitle.setText(title);
        tvMessage.setText(message);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            if (onDismiss != null) {
                onDismiss.run();
            }
        });

        dialog.setOnCancelListener(d -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    public static void showHistoryGuestRegisterCta(Context context, String title, String message, Runnable onRegisterNow) {
        showHistoryGuestRegisterCta(context, title, message, onRegisterNow, null);
    }

    public static void showHistoryGuestRegisterCta(Context context, String title, String message, Runnable onRegisterNow, Runnable onCancel) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_history_notice, null, false);

        ImageView ivIcon = dialogView.findViewById(R.id.ivDialogIcon);
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvDialogMessage);
        MaterialButton btnSecondary = dialogView.findViewById(R.id.btnDialogSecondary);
        MaterialButton btnPrimary = dialogView.findViewById(R.id.btnDialogPrimary);

        ivIcon.setImageResource(R.drawable.ic_person_add_24);
        ivIcon.setColorFilter(Color.parseColor("#2E58AE"));
        tvTitle.setText(title);
        tvMessage.setText(message);
        btnSecondary.setText(R.string.history_popup_cancel);
        btnPrimary.setText(LocaleManager.getString(
                context,
                R.string.home_guest_register_now,
                LocaleManager.getCurrentLanguage(context)
        ));
        btnPrimary.setBackgroundResource(R.drawable.bg_history_pill_cta);
        btnPrimary.setTextColor(Color.WHITE);
        btnPrimary.setIconResource(R.drawable.ic_person_add_24);
        btnPrimary.setIconTint(ColorStateList.valueOf(Color.WHITE));
        btnPrimary.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btnPrimary.setIconSize(context.getResources().getDimensionPixelSize(R.dimen.home_guest_cta_icon_size));
        btnPrimary.setIconPadding(context.getResources().getDimensionPixelSize(R.dimen.home_guest_cta_icon_padding));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnSecondary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onCancel != null) {
                onCancel.run();
            }
        });
        btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onRegisterNow != null) {
                onRegisterNow.run();
            }
        });

        dialog.setOnCancelListener(d -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    public static void showSelectionRequiredDialog(Context context, String title, String message) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_history_notice, null, false);

        ImageView ivIcon = dialogView.findViewById(R.id.ivDialogIcon);
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvDialogMessage);
        MaterialButton btnSecondary = dialogView.findViewById(R.id.btnDialogSecondary);
        MaterialButton btnPrimary = dialogView.findViewById(R.id.btnDialogPrimary);

        ivIcon.setImageResource(R.drawable.ic_help_24);
        ivIcon.setColorFilter(Color.parseColor("#2E58AE"));
        tvTitle.setText(title);
        tvMessage.setText(message);

        btnSecondary.setVisibility(View.GONE);
        btnPrimary.setText(R.string.selection_required_continue);
        btnPrimary.setBackgroundResource(R.drawable.bg_history_pill_blue);
        btnPrimary.setTextColor(Color.WHITE);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnPrimary.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    public static void showSubscriptionQR(Context context, String qrUrl) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_subscription_qr, null, false);
        ImageView ivQr = dialogView.findViewById(R.id.ivQrCode);
        MaterialButton btnClose = dialogView.findViewById(R.id.btnSubClose);

        Glide.with(context)
                .load(qrUrl)
                .placeholder(R.drawable.ic_image_error)
                .into(ivQr);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
