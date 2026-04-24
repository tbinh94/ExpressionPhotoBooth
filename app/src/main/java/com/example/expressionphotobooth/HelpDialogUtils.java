package com.example.expressionphotobooth;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.expressionphotobooth.utils.LocaleManager;

import java.util.List;
import java.util.Locale;

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
                dialogView.findViewById(R.id.tvBullet4),
                dialogView.findViewById(R.id.tvBullet5),
                dialogView.findViewById(R.id.tvBullet6),
                dialogView.findViewById(R.id.tvBullet7),
                dialogView.findViewById(R.id.tvBullet8)
        };
        View[] bulletRows = new View[] {
                dialogView.findViewById(R.id.rowBullet1),
                dialogView.findViewById(R.id.rowBullet2),
                dialogView.findViewById(R.id.rowBullet3),
                dialogView.findViewById(R.id.rowBullet4),
                dialogView.findViewById(R.id.rowBullet5),
                dialogView.findViewById(R.id.rowBullet6),
                dialogView.findViewById(R.id.rowBullet7),
                dialogView.findViewById(R.id.rowBullet8)
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

    public static void showUsageGuideBranded(
            Context context,
            String title,
            String subtitle,
            List<String> steps,
            int[] stepIcons,
            String ctaText,
            Runnable onCta
    ) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_usage_guide_branded, null, false);

        TextView tvTitle = dialogView.findViewById(R.id.tvGuideTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvGuideSubtitle);
        TextView[] stepNumbers = new TextView[] {
                dialogView.findViewById(R.id.tvStepNumber1),
                dialogView.findViewById(R.id.tvStepNumber2),
                dialogView.findViewById(R.id.tvStepNumber3),
                dialogView.findViewById(R.id.tvStepNumber4),
                dialogView.findViewById(R.id.tvStepNumber5),
                dialogView.findViewById(R.id.tvStepNumber6),
                dialogView.findViewById(R.id.tvStepNumber7),
                dialogView.findViewById(R.id.tvStepNumber8)
        };
        ImageView[] stepIconViews = new ImageView[] {
                dialogView.findViewById(R.id.ivStepIcon1),
                dialogView.findViewById(R.id.ivStepIcon2),
                dialogView.findViewById(R.id.ivStepIcon3),
                dialogView.findViewById(R.id.ivStepIcon4),
                dialogView.findViewById(R.id.ivStepIcon5),
                dialogView.findViewById(R.id.ivStepIcon6),
                dialogView.findViewById(R.id.ivStepIcon7),
                dialogView.findViewById(R.id.ivStepIcon8)
        };
        TextView[] stepTexts = new TextView[] {
                dialogView.findViewById(R.id.tvStepText1),
                dialogView.findViewById(R.id.tvStepText2),
                dialogView.findViewById(R.id.tvStepText3),
                dialogView.findViewById(R.id.tvStepText4),
                dialogView.findViewById(R.id.tvStepText5),
                dialogView.findViewById(R.id.tvStepText6),
                dialogView.findViewById(R.id.tvStepText7),
                dialogView.findViewById(R.id.tvStepText8)
        };
        View[] stepRows = new View[] {
                dialogView.findViewById(R.id.rowStep1),
                dialogView.findViewById(R.id.rowStep2),
                dialogView.findViewById(R.id.rowStep3),
                dialogView.findViewById(R.id.rowStep4),
                dialogView.findViewById(R.id.rowStep5),
                dialogView.findViewById(R.id.rowStep6),
                dialogView.findViewById(R.id.rowStep7),
                dialogView.findViewById(R.id.rowStep8)
        };
        MaterialButton btnCta = dialogView.findViewById(R.id.btnGuideCta);

        tvTitle.setText(title);
        if (TextUtils.isEmpty(subtitle)) {
            tvSubtitle.setVisibility(View.GONE);
        } else {
            tvSubtitle.setVisibility(View.VISIBLE);
            tvSubtitle.setText(subtitle);
        }

        int stepCount = steps == null ? 0 : Math.min(steps.size(), stepRows.length);
        for (int i = 0; i < stepRows.length; i++) {
            if (i < stepCount) {
                stepRows[i].setVisibility(View.VISIBLE);
                stepNumbers[i].setText(String.format(Locale.getDefault(), "%d", i + 1));
                stepTexts[i].setText(steps.get(i));
                int iconRes = (stepIcons != null && i < stepIcons.length && stepIcons[i] != 0)
                        ? stepIcons[i]
                        : R.drawable.ic_help_24;
                stepIconViews[i].setImageResource(iconRes);
            } else {
                stepRows[i].setVisibility(View.GONE);
            }
        }

        if (!TextUtils.isEmpty(ctaText)) {
            btnCta.setText(ctaText);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .create();

        btnCta.setOnClickListener(v -> animateCtaThen(v, () -> {
            dialog.dismiss();
            if (onCta != null) {
                onCta.run();
            }
        }));

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        View card = dialogView.findViewById(R.id.cardGuideContainer);
        animateGuideDialogEntrance(card != null ? card : dialogView);
        animateStepRowsCascade(stepRows, stepCount);
    }

    private static void animateGuideDialogEntrance(View target) {
        if (target == null) {
            return;
        }
        float startOffsetPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                14f,
                target.getResources().getDisplayMetrics()
        );
        target.setAlpha(0f);
        target.setScaleX(0.96f);
        target.setScaleY(0.96f);
        target.setTranslationY(startOffsetPx);
        target.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    private static void animateCtaThen(View button, Runnable endAction) {
        if (button == null) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }
        button.animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(80L)
                .withEndAction(() -> button.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120L)
                        .setInterpolator(new DecelerateInterpolator(1.4f))
                        .withEndAction(() -> {
                            if (endAction != null) {
                                endAction.run();
                            }
                        })
                        .start())
                .start();
    }

    private static void animateStepRowsCascade(View[] rows, int visibleCount) {
        if (rows == null || visibleCount <= 0) {
            return;
        }
        float startOffsetPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                rows[0].getResources().getDisplayMetrics()
        );
        final long perRowDelayMs = 24L;

        for (int i = 0; i < visibleCount && i < rows.length; i++) {
            View row = rows[i];
            if (row == null || row.getVisibility() != View.VISIBLE) {
                continue;
            }
            row.setAlpha(0f);
            row.setTranslationY(startOffsetPx);
            long startDelay = 90L + (i * perRowDelayMs);
            row.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(startDelay)
                    .setDuration(180L)
                    .setInterpolator(new DecelerateInterpolator(1.4f))
                    .start();
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

        String languageTag = LocaleManager.getCurrentLanguage(context);
        ivIcon.setImageResource(R.drawable.ic_person_add_24);
        ivIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(context, R.color.app_blue));
        tvTitle.setText(title);
        tvMessage.setText(message);
        btnSecondary.setText(LocaleManager.getString(context, R.string.history_popup_cancel, languageTag));
        btnPrimary.setText(LocaleManager.getString(context, R.string.home_guest_register_now, languageTag));
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

    public static void showHistoryStyledNotice(
            Context context,
            int iconRes,
            String title,
            String message,
            String primaryText,
            Runnable onPrimary
    ) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_history_notice, null, false);

        ImageView ivIcon = dialogView.findViewById(R.id.ivDialogIcon);
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvDialogMessage);
        MaterialButton btnSecondary = dialogView.findViewById(R.id.btnDialogSecondary);
        MaterialButton btnPrimary = dialogView.findViewById(R.id.btnDialogPrimary);

        ivIcon.setImageResource(iconRes);
        ivIcon.setColorFilter(Color.parseColor("#2E58AE"));
        tvTitle.setText(title);
        tvMessage.setText(message);
        btnSecondary.setVisibility(View.GONE);
        btnPrimary.setText(primaryText);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPrimary != null) {
                onPrimary.run();
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    public static void showHistoryStyledConfirm(
            Context context,
            int iconRes,
            String title,
            String message,
            String primaryText,
            String secondaryText,
            Runnable onPrimary,
            Runnable onSecondary
    ) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_history_notice, null, false);

        ImageView ivIcon = dialogView.findViewById(R.id.ivDialogIcon);
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvDialogMessage);
        MaterialButton btnSecondary = dialogView.findViewById(R.id.btnDialogSecondary);
        MaterialButton btnPrimary = dialogView.findViewById(R.id.btnDialogPrimary);

        ivIcon.setImageResource(iconRes);
        ivIcon.setColorFilter(Color.parseColor("#2E58AE"));
        tvTitle.setText(title);
        tvMessage.setText(message);
        btnSecondary.setVisibility(View.VISIBLE);
        btnSecondary.setText(secondaryText);
        btnPrimary.setText(primaryText);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnSecondary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onSecondary != null) {
                onSecondary.run();
            }
        });
        btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPrimary != null) {
                onPrimary.run();
            }
        });

        dialog.setOnCancelListener(d -> {
            if (onSecondary != null) {
                onSecondary.run();
            }
        });

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

    public static void showHelpDialog(Context context) {
        String title = context.getString(R.string.help_title);
        String subtitle = context.getString(R.string.help_subtitle);
        List<String> bullets = java.util.Arrays.asList(
                context.getString(R.string.help_tip_1),
                context.getString(R.string.help_tip_2),
                context.getString(R.string.help_tip_3),
                context.getString(R.string.help_tip_4)
        );
        showPhotoboothHelp(context, title, subtitle, bullets);
    }
}
