package com.example.expressionphotobooth;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminStickersActivity extends AppCompatActivity {

    private RecyclerView rvStickers;
    private StickerAdapter adapter;
    private List<StickerModel> stickerList = new ArrayList<>();
    
    private EditText etLabel;
    private MaterialButton btnUpload;
    private LinearProgressIndicator progress;
    private TextView tvEmpty;

    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_stickers);

        rvStickers = findViewById(R.id.rvAdminStickers);
        etLabel = findViewById(R.id.etStickerLabel);
        btnUpload = findViewById(R.id.btnUpload);
        progress = findViewById(R.id.progress);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new StickerAdapter(stickerList);
        rvStickers.setLayoutManager(new GridLayoutManager(this, 3));
        rvStickers.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        handleImageResult(imageUri);
                    }
                }
        );

        btnUpload.setOnClickListener(v -> {
            String label = etLabel.getText().toString().trim();
            if (label.isEmpty()) {
                Toast.makeText(this, R.string.admin_sticker_label_hint, Toast.LENGTH_SHORT).show();
                return;
            }
            openImagePicker();
        });

        loadStickers();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/png");
        pickImageLauncher.launch(intent);
    }

    private void handleImageResult(Uri uri) {
        String label = etLabel.getText().toString().trim();
        progress.setVisibility(View.VISIBLE);
        btnUpload.setEnabled(false);

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (bitmap == null) throw new Exception("Decoding failed");

                // Auto Transparency for any white background (threshold 245)
                Bitmap transparentBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                int w = transparentBitmap.getWidth();
                int h = transparentBitmap.getHeight();
                int[] pixels = new int[w * h];
                transparentBitmap.getPixels(pixels, 0, w, 0, 0, w, h);
                for (int i = 0; i < pixels.length; i++) {
                    int p = pixels[i];
                    int r = (p >> 16) & 0xff;
                    int g = (p >> 8) & 0xff;
                    int b = p & 0xff;
                    if (r > 245 && g > 245 && b > 245) pixels[i] = 0x00FFFFFF;
                }
                transparentBitmap.setPixels(pixels, 0, w, 0, 0, w, h);

                // Resize stickers to max 400px to keep firestore data small
                int max = 400;
                if (w > max || h > max) {
                    float ratio = (float) w / h;
                    int nw = ratio > 1 ? max : (int)(max * ratio);
                    int nh = ratio > 1 ? (int)(max / ratio) : max;
                    transparentBitmap = Bitmap.createScaledBitmap(transparentBitmap, nw, nh, true);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                transparentBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                String base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                Map<String, Object> data = new HashMap<>();
                data.put("label", label);
                data.put("base64", base64);
                data.put("timestamp", System.currentTimeMillis());

                FirebaseFirestore.getInstance().collection("custom_stickers")
                        .add(data)
                        .addOnSuccessListener(doc -> {
                            runOnUiThread(() -> {
                                progress.setVisibility(View.GONE);
                                btnUpload.setEnabled(true);
                                etLabel.setText("");
                                Toast.makeText(this, R.string.admin_sticker_success, Toast.LENGTH_SHORT).show();
                                loadStickers();
                            });
                        })
                        .addOnFailureListener(e -> {
                            runOnUiThread(() -> {
                                progress.setVisibility(View.GONE);
                                btnUpload.setEnabled(true);
                                Toast.makeText(this, R.string.admin_sticker_error, Toast.LENGTH_SHORT).show();
                            });
                        });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    btnUpload.setEnabled(true);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void loadStickers() {
        FirebaseFirestore.getInstance().collection("custom_stickers")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    stickerList.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        stickerList.add(new StickerModel(
                                doc.getString("label"),
                                doc.getString("base64")
                        ));
                    }
                    tvEmpty.setVisibility(stickerList.isEmpty() ? View.VISIBLE : View.GONE);
                    adapter.notifyDataSetChanged();
                });
    }

    private static class StickerModel {
        String label;
        String base64;
        StickerModel(String l, String b) { label = l; base64 = b; }
    }

    private static class StickerAdapter extends RecyclerView.Adapter<StickerAdapter.VH> {
        private final List<StickerModel> items;
        StickerAdapter(List<StickerModel> items) { this.items = items; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_admin_sticker, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int p) {
            StickerModel m = items.get(p);
            h.tv.setText(m.label);
            byte[] bytes = Base64.decode(m.base64, Base64.DEFAULT);
            h.iv.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            ImageView iv; TextView tv;
            VH(View v) { super(v); iv = v.findViewById(R.id.ivPreview); tv = v.findViewById(R.id.tvLabel); }
        }
    }
}
