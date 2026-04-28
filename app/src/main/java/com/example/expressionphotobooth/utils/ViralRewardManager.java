package com.example.expressionphotobooth.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.expressionphotobooth.R;
import com.example.expressionphotobooth.AppContainer;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.expressionphotobooth.utils.AuditLogger;

import java.io.File;

public class ViralRewardManager {

    private final Activity activity;
    private final FirebaseFirestore db;

    public ViralRewardManager(Activity activity) {
        this.activity = activity;
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Kiểm tra Firestore xem chiến dịch có đang hoạt động hay không
     */
    public void checkAndShowRewardPopup(Uri photoUri) {
        db.collection("app_config").document("viral_campaign")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    boolean isActive = false;
                    if (documentSnapshot.exists()) {
                        Boolean active = documentSnapshot.getBoolean("isActive");
                        isActive = (active != null && active);
                    } else {
                        // Nếu chưa có config thì mặc định cho phép để tính năng hoạt động
                        isActive = true; 
                    }

                    if (isActive) {
                        showPopup(photoUri);
                    } else {
                        // Fallback về share mặc định nếu chiến dịch bị tắt chủ động
                        startDefaultShare(photoUri);
                    }
                })
                .addOnFailureListener(e -> {
                    // Fallback nếu lỗi network - cho hiện popup luôn để giữ trải nghiệm người dùng
                    showPopup(photoUri);
                });
    }

    private void startDefaultShare(Uri photoUri) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.setDataAndType(photoUri, activity.getContentResolver().getType(photoUri));
        shareIntent.putExtra(Intent.EXTRA_STREAM, photoUri);
        shareIntent.setType("image/png");
        activity.startActivity(Intent.createChooser(shareIntent, "Share Photo"));
    }

    private void showPopup(Uri photoUri) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_viral_reward, null);
        dialog.setContentView(view);

        // Thiết kế Window trong suốt để thấy hiệu ứng Glassmorphism
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        view.findViewById(R.id.btnShareClaim).setOnClickListener(v -> {
            shareToSocial(photoUri);
            grantReward();
            dialog.dismiss();
        });

        view.findViewById(R.id.btnMaybeLater).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void shareToSocial(Uri photoUri) {
        if (photoUri == null) {
            Toast.makeText(activity, "[Chế độ Test] Đã kích hoạt chia sẻ giả lập", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, photoUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        activity.startActivity(Intent.createChooser(shareIntent, "Share your memory and get rewards!"));
    }

    private void grantReward() {
        // Logic trao thưởng thực tế (mở khóa VIP trong Firestore)
        AuthRepository authRepository = ((AppContainer) activity.getApplication()).getAuthRepository();
        String uid = authRepository.getCurrentUid();
        
        if (uid != null) {
            java.util.Map<String, Object> rewardData = new java.util.HashMap<>();
            rewardData.put("viralRewardUnlocked", true);
            rewardData.put("rewardClaimedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

            db.collection("users").document(uid)
                    .set(rewardData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        AuditLogger.logAction("USER_REWARD_CLAIMED", "User " + uid + " unlocked viral stickers");
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("ViralRewardManager", "Failed to grant reward: " + e.getMessage());
                    });
        }

        // Hiển thị Popup thành công thiết kế đẹp
        Dialog successDialog = new Dialog(activity);
        successDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_reward_success, null);
        successDialog.setContentView(view);

        if (successDialog.getWindow() != null) {
            successDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            successDialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        view.findViewById(R.id.btnAwesome).setOnClickListener(v -> successDialog.dismiss());
        successDialog.show();
    }
}
