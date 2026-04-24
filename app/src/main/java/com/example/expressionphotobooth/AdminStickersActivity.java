package com.example.expressionphotobooth;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.bumptech.glide.Glide;
import com.example.expressionphotobooth.utils.LocaleManager;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

public class AdminStickersActivity extends AppCompatActivity {

    private RecyclerView rvStickers;
    private StickerAdapter adapter;
    private List<StickerModel> stickerList = new ArrayList<>();
    
    private EditText etLabel;
    private MaterialButton btnUpload;
    private LinearProgressIndicator progress;
    private TextView tvEmpty;

    private SubjectSegmenter segmenter;

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

        segmenter = SubjectSegmentation.getClient(
                new SubjectSegmenterOptions.Builder()
                        .enableForegroundBitmap()
                        .build()
        );

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
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pickImageLauncher.launch(Intent.createChooser(intent, "Select Sticker Image"));
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

                // Use ML Kit Subject Segmentation to extract the subject (Messenger style)
                InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
                
                Bitmap extractedSubject = null;
                try {
                    // Synchronously wait for the segmentation to complete on this background thread
                    Task<Bitmap> task = segmenter.process(inputImage)
                            .continueWith(t -> {
                                if (!t.isSuccessful()) throw t.getException();
                                return t.getResult().getForegroundBitmap();
                            });

                    extractedSubject = Tasks.await(task, 10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception mlError) {
                    Log.e("AdminStickers", "ML Kit segmentation failed: " + mlError.getMessage());
                    // This is where the SecurityException would be caught
                }

                if (extractedSubject == null) {
                    // Fallback to basic white removal if segmentation fails
                    extractedSubject = applyBasicWhiteRemoval(bitmap);
                }

                int w = extractedSubject.getWidth();
                int h = extractedSubject.getHeight();

                // Resize stickers to max 400px to keep firestore data small
                int max = 400;
                Bitmap finalSticker = extractedSubject;
                if (w > max || h > max) {
                    float ratio = (float) w / h;
                    int nw = ratio > 1 ? max : (int)(max * ratio);
                    int nh = ratio > 1 ? (int)(max / ratio) : max;
                    finalSticker = Bitmap.createScaledBitmap(extractedSubject, nw, nh, true);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                finalSticker.compress(Bitmap.CompressFormat.PNG, 95, baos);
                String base64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);

                // Save metadata and base64 directly to Firestore
                Map<String, Object> data = new HashMap<>();
                data.put("label", label);
                data.put("base64", base64);
                data.put("type", "admin");
                data.put("isGlobal", true);
                data.put("timestamp", System.currentTimeMillis());

                FirebaseFirestore.getInstance().collection("stickers")
                        .add(data)
                        .addOnSuccessListener(docRef -> {
                            runOnUiThread(() -> {
                                progress.setVisibility(View.GONE);
                                btnUpload.setEnabled(true);
                                etLabel.setText("");
                                String languageTag = LocaleManager.getCurrentLanguage(this);
                                HelpDialogUtils.showHistoryStyledNotice(
                                        this,
                                        R.drawable.ic_success_circle,
                                        LocaleManager.getString(this, R.string.admin_sticker_success, languageTag),
                                        "",
                                        LocaleManager.getString(this, R.string.common_ok, languageTag),
                                        null
                                );
                                loadStickers();
                            });
                        })
                        .addOnFailureListener(e -> {
                            runOnUiThread(() -> {
                                progress.setVisibility(View.GONE);
                                btnUpload.setEnabled(true);
                                String languageTag = LocaleManager.getCurrentLanguage(this);
                                HelpDialogUtils.showHistoryStyledNotice(
                                        this,
                                        R.drawable.ic_error_circle,
                                        "Firestore Error",
                                        e.getMessage(),
                                        LocaleManager.getString(this, R.string.common_ok, languageTag),
                                        null
                                );
                            });
                        });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    btnUpload.setEnabled(true);
                    String languageTag = LocaleManager.getCurrentLanguage(this);
                    HelpDialogUtils.showHistoryStyledNotice(
                            this,
                            R.drawable.ic_error_circle,
                            "Processing Error",
                            e.getMessage(),
                            LocaleManager.getString(this, R.string.common_ok, languageTag),
                            null
                    );
                });
            }
        }).start();
    }

    private Bitmap applyBasicWhiteRemoval(Bitmap bitmap) {
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
        return transparentBitmap;
    }

    private void deleteSticker(String id) {
        if (id == null) return;
        String languageTag = LocaleManager.getCurrentLanguage(this);
        HelpDialogUtils.showHistoryStyledConfirm(
                this,
                R.drawable.ic_delete_24,
                LocaleManager.getString(this, R.string.admin_sticker_delete_confirm, languageTag),
                "",
                LocaleManager.getString(this, R.string.history_popup_ok, languageTag),
                LocaleManager.getString(this, R.string.history_popup_cancel, languageTag),
                () -> {
                    FirebaseFirestore.getInstance().collection("stickers")
                            .document(id)
                            .delete()
                            .addOnSuccessListener(v -> {
                                HelpDialogUtils.showHistoryStyledNotice(
                                        this,
                                        R.drawable.ic_success_circle,
                                        LocaleManager.getString(this, R.string.admin_sticker_deleted, languageTag),
                                        "",
                                        LocaleManager.getString(this, R.string.common_ok, languageTag),
                                        null
                                );
                                loadStickers();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                },
                null
        );
    }

    private void loadStickers() {
        FirebaseFirestore.getInstance().collection("stickers")
                .whereEqualTo("type", "admin")
                .get()
                .addOnSuccessListener(snap -> {
                    stickerList.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        stickerList.add(new StickerModel(
                                doc.getId(),
                                doc.getString("label"),
                                doc.getString("base64")
                        ));
                    }
                    runOnUiThread(() -> {
                        tvEmpty.setVisibility(stickerList.isEmpty() ? View.VISIBLE : View.GONE);
                        adapter.notifyDataSetChanged();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminStickers", "Error loading: " + e.getMessage());
                    Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private static class StickerModel {
        String id;
        String label;
        String base64;
        StickerModel(String id, String l, String b) { 
            this.id = id;
            this.label = l; 
            this.base64 = b; 
        }
    }

    private class StickerAdapter extends RecyclerView.Adapter<StickerAdapter.VH> {
        private final List<StickerModel> items;
        StickerAdapter(List<StickerModel> items) { this.items = items; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_admin_sticker, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            StickerModel m = items.get(position);
            h.tv.setText(m.label);
            if (m.base64 != null) {
                byte[] bytes = android.util.Base64.decode(m.base64, android.util.Base64.DEFAULT);
                h.iv.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            }
            h.btnDelete.setOnClickListener(v -> deleteSticker(m.id));
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            ImageView iv; TextView tv; View btnDelete;
            VH(View v) { 
                super(v); 
                iv = v.findViewById(R.id.ivPreview); 
                tv = v.findViewById(R.id.tvLabel); 
                btnDelete = v.findViewById(R.id.btnDeleteSticker);
            }
        }
    }
}
