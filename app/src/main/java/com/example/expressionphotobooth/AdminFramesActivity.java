package com.example.expressionphotobooth;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdminFramesActivity extends AppCompatActivity {

    private RecyclerView rvAdminFrames;
    private AdminFrameAdapter adapter;
    private List<FrameModel> frameList = new ArrayList<>();
    
    private EditText etFrameLabel;
    private AutoCompleteTextView actvLayoutType;
    private MaterialButton btnUpload;
    private LinearProgressIndicator progress;
    private TextView tvEmpty;

    private ActivityResultLauncher<Intent> pickImageLauncher;
    
    private final String[] LAYOUT_OPTIONS = new String[]{
        "3x4 (4 ảnh)",
        "16x9 (3 ảnh)",
        "16x9 (4 ảnh)"
    };

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(com.example.expressionphotobooth.utils.LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_frames);

        rvAdminFrames = findViewById(R.id.rvAdminFrames);
        etFrameLabel = findViewById(R.id.etFrameLabel);
        actvLayoutType = findViewById(R.id.actvLayoutType);
        btnUpload = findViewById(R.id.btnUpload);
        progress = findViewById(R.id.progress);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new AdminFrameAdapter(frameList, frame -> showDeleteConfirmation(frame));
        rvAdminFrames.setLayoutManager(new GridLayoutManager(this, 2));
        rvAdminFrames.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        ArrayAdapter<String> layoutAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, LAYOUT_OPTIONS);
        actvLayoutType.setAdapter(layoutAdapter);

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        handleUpload(imageUri);
                    }
                }
        );

        btnUpload.setOnClickListener(v -> {
            String label = etFrameLabel.getText().toString().trim();
            String layout = actvLayoutType.getText().toString().trim();
            
            if (TextUtils.isEmpty(label)) {
                showNotificationPopup("Thiếu thông tin", "Vui lòng nhập tên khung ảnh trước khi tải lên.", false);
                return;
            }
            if (TextUtils.isEmpty(layout)) {
                showNotificationPopup("Thiếu thông tin", "Vui lòng chọn kiểu layout cho khung ảnh.", false);
                return;
            }

            // Kiểm tra trùng tên
            for (FrameModel frame : frameList) {
                if (frame.getLabel().equalsIgnoreCase(label)) {
                    showNotificationPopup("Trùng Tên", "Tên khung ảnh này đã tồn tại. Vui lòng đặt một tên khác để dễ dàng quản lý.", false);
                    return;
                }
            }

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        loadFrames();
    }

    private void handleUpload(Uri imageUri) {
        if (imageUri == null) return;
        
        String label = etFrameLabel.getText().toString().trim();
        String layoutName = actvLayoutType.getText().toString().trim();
        String layoutCode = mapLayoutNameToCode(layoutName);

        progress.setVisibility(View.VISIBLE);
        btnUpload.setEnabled(false);

        new Thread(() -> {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(imageUri);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                if (bitmap == null) {
                    throw new Exception("Không thể đọc ảnh");
                }
                
                // Giới hạn kích thước ảnh xuống 800px để tránh base64 quá lớn
                int max = 800;
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                if (w > max || h > max) {
                    float ratio = (float) w / h;
                    int nw = ratio > 1 ? max : (int)(max * ratio);
                    int nh = ratio > 1 ? (int)(max / ratio) : max;
                    bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, nw, nh, true);
                }

                // Lưu frame gốc (không xóa nền ở đây).
                // Việc xóa nền trắng sẽ được thực hiện khi render tại ResultActivity,
                // giúp admin không cần phải upload lại frame mỗi khi thay đổi thuật toán.

                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, baos);
                byte[] data = baos.toByteArray();
                String base64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);

                runOnUiThread(() -> saveFrameToFirestore(label, layoutCode, base64));

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    btnUpload.setEnabled(true);
                    showNotificationPopup("Lỗi đọc ảnh", "Không thể xử lý ảnh bạn chọn: " + e.getMessage(), false);
                });
            }
        }).start();
    }

    private android.graphics.Bitmap applyWhiteRemovalInHoles(android.graphics.Bitmap bitmap, String layoutCode) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        
        java.util.List<android.graphics.Rect> holes = com.example.expressionphotobooth.utils.FrameConfig.getHolesForLayoutScaled(layoutCode, w, h);
        
        android.graphics.Bitmap transparentBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
        int[] pixels = new int[w * h];
        transparentBitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        
        boolean[] visited = new boolean[w * h];
        
        for (android.graphics.Rect rect : holes) {
            // Chỉ bắt đầu tìm kiếm màu trắng từ vùng trung tâm của lỗ trống (tránh viền)
            int cx = rect.centerX();
            int cy = rect.centerY();
            int startX = Math.max(0, cx - rect.width() / 4);
            int endX = Math.min(w, cx + rect.width() / 4);
            int startY = Math.max(0, cy - rect.height() / 4);
            int endY = Math.min(h, cy + rect.height() / 4);

            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {
                    int idx = y * w + x;
                    if (visited[idx]) continue;

                    int p = pixels[idx];
                    int r = (p >> 16) & 0xff;
                    int g = (p >> 8) & 0xff;
                    int b = p & 0xff;

                    // Nếu gặp pixel trắng, bắt đầu Flood Fill (loang màu) để xóa toàn bộ vùng trắng liền kề
                    if (r > 240 && g > 240 && b > 240) {
                        java.util.Queue<Integer> q = new java.util.LinkedList<>();
                        q.add(idx);
                        visited[idx] = true;

                        while (!q.isEmpty()) {
                            int curr = q.poll();
                            pixels[curr] = 0x00FFFFFF; // Đổi thành trong suốt

                            int currX = curr % w;
                            int currY = curr / w;

                            // Kiểm tra 4 hướng xung quanh
                            int[] neighbors = {curr - 1, curr + 1, curr - w, curr + w};
                            for (int n : neighbors) {
                                if (n >= 0 && n < w * h && !visited[n]) {
                                    int nx = n % w;
                                    // Chặn trường hợp bị tràn lề màn hình
                                    if (Math.abs(nx - currX) > 1) continue;

                                    int np = pixels[n];
                                    int nr = (np >> 16) & 0xff;
                                    int ng = (np >> 8) & 0xff;
                                    int nb = np & 0xff;

                                    if (nr > 240 && ng > 240 && nb > 240) {
                                        visited[n] = true;
                                        q.add(n);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        transparentBitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return transparentBitmap;
    }



    private void saveFrameToFirestore(String label, String layoutCode, String base64) {
        Map<String, Object> data = new HashMap<>();
        data.put("label", label);
        data.put("layoutType", layoutCode);
        data.put("slotCount", com.example.expressionphotobooth.utils.FrameConfig.getSlotCountForLayout(layoutCode));
        data.put("base64", base64);
        data.put("createdAt", System.currentTimeMillis());

        FirebaseFirestore.getInstance().collection("frames")
                .add(data)
                .addOnSuccessListener(docRef -> {
                    progress.setVisibility(View.GONE);
                    btnUpload.setEnabled(true);
                    etFrameLabel.setText("");
                    actvLayoutType.setText("");
                    showNotificationPopup("Thành Công", "Khung ảnh đã được tải lên máy chủ thành công!", true);
                    loadFrames();
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    btnUpload.setEnabled(true);
                    showNotificationPopup("Lỗi Lưu Dữ Liệu", "Không thể lưu vào cơ sở dữ liệu: " + e.getMessage(), false);
                });
    }

    private void loadFrames() {
        FirebaseFirestore.getInstance().collection("frames")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    frameList.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        frameList.add(new FrameModel(
                                doc.getId(),
                                doc.getString("base64"),
                                doc.getString("label"),
                                doc.getString("layoutType"),
                                doc.getLong("createdAt") != null ? doc.getLong("createdAt") : 0L
                        ));
                    }
                    tvEmpty.setVisibility(frameList.isEmpty() ? View.VISIBLE : View.GONE);
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminFramesActivity", "Lỗi tải khung ảnh: " + e.getMessage());
                    showNotificationPopup("Lỗi Đồng Bộ", "Không thể tải danh sách khung ảnh.", false);
                });
    }

    private void showDeleteConfirmation(FrameModel frame) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Xóa khung ảnh")
                .setMessage("Bạn có chắc chắn muốn xóa khung ảnh này?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteFrame(frame))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteFrame(FrameModel frame) {
        progress.setVisibility(View.VISIBLE);
        deleteFirestoreDocument(frame.getId());
    }
    
    private void deleteFirestoreDocument(String id) {
        FirebaseFirestore.getInstance().collection("frames").document(id)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    progress.setVisibility(View.GONE);
                    showNotificationPopup("Đã Xóa", "Khung ảnh đã được xóa khỏi hệ thống.", true);
                    loadFrames();
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    showNotificationPopup("Lỗi Xóa", "Không thể xóa dữ liệu, vui lòng thử lại.", false);
                });
    }

    private String mapLayoutNameToCode(String name) {
        if ("3x4 (4 ảnh)".equals(name)) return "3x4_4";
        if ("16x9 (3 ảnh)".equals(name)) return "16x9_3";
        if ("16x9 (4 ảnh)".equals(name)) return "16x9_4";
        return "unknown";
    }

    private void showNotificationPopup(String title, String message, boolean isSuccess) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_beautiful_popup);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        android.widget.ImageView ivIcon = dialog.findViewById(R.id.ivPopupIcon);
        android.widget.TextView tvTitle = dialog.findViewById(R.id.tvPopupTitle);
        android.widget.TextView tvMessage = dialog.findViewById(R.id.tvPopupMessage);
        com.google.android.material.button.MaterialButton btnAction = dialog.findViewById(R.id.btnPopupAction);

        tvTitle.setText(title);
        tvMessage.setText(message);

        if (isSuccess) {
            ivIcon.setImageResource(R.drawable.ic_success_circle);
            btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1CD1A1")));
            btnAction.setText("Tuyệt Vời");
        } else {
            ivIcon.setImageResource(R.drawable.ic_error_circle);
            btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF4B4B")));
            btnAction.setText("Thử Lại");
            tvTitle.setTextColor(android.graphics.Color.parseColor("#FF4B4B"));
        }

        btnAction.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
