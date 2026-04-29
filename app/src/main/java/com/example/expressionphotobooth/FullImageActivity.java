package com.example.expressionphotobooth;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import com.example.expressionphotobooth.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.expressionphotobooth.utils.LocaleManager;

import java.io.File;
import java.io.OutputStream;

public class FullImageActivity extends AppCompatActivity {

    private String imagePath;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_image);

        imagePath = getIntent().getStringExtra("IMAGE_PATH");
        if (imagePath == null) {
            finish();
            return;
        }

        ImageView ivFull = findViewById(R.id.ivFullImage);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        File file = new File(imagePath);
        if (file.exists()) {
            Glide.with(this)
                    .load(file)
                    .into(ivFull);
        } else {
            finish();
            return;
        }

        findViewById(R.id.btnSave).setOnClickListener(v -> saveImageToSystemGallery());
        findViewById(R.id.btnShare).setOnClickListener(v -> shareImage());

        File videoFile = findLocalVideo();
        View btnSaveVideo = findViewById(R.id.btnSaveVideo);
        if (videoFile != null) {
            btnSaveVideo.setVisibility(View.VISIBLE);
            btnSaveVideo.setOnClickListener(v -> saveVideoToSystemGallery(videoFile.getAbsolutePath()));
        } else {
            btnSaveVideo.setVisibility(View.GONE);
        }
    }

    private File findLocalVideo() {
        if (imagePath == null) return null;
        File imgFile = new File(imagePath);
        String name = imgFile.getName();
        if (!name.startsWith("pose_")) return null;
        
        String[] parts = name.split("_");
        if (parts.length < 2) return null;
        String sessionId = parts[1]; // e.g. pose_sessionId_123.png
        
        File dir = imgFile.getParentFile();
        if (dir == null || !dir.exists()) return null;
        
        File[] files = dir.listFiles((d, n) -> n.startsWith("pose_" + sessionId) && n.endsWith(".mp4"));
        if (files != null && files.length > 0) {
            return files[0];
        }
        return null;
    }

    private void saveVideoToSystemGallery(String videoPath) {
        File file = new File(videoPath);
        if (!file.exists()) return;

        String name = "photobooth_timelapse_" + System.currentTimeMillis() + ".mp4";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Photobooth");

        Uri outputUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri != null) {
            try (java.io.InputStream in = new java.io.FileInputStream(file);
                 OutputStream out = getContentResolver().openOutputStream(outputUri)) {
                if (out != null) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    Toast.makeText(this, R.string.video_saved_success, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveImageToSystemGallery() {
        File file = new File(imagePath);
        if (!file.exists()) return;

        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) return;

        String name = "photobooth_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photobooth");

        Uri outputUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(outputUri)) {
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    Toast.makeText(this, R.string.saved_to_gallery_short, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void shareImage() {
        File file = new File(imagePath);
        if (!file.exists()) return;

        boolean isVideo = imagePath.endsWith(".mp4");
        String mimeType = isVideo ? "video/mp4" : "image/png";

        Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.setDataAndType(contentUri, mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);

        startActivity(Intent.createChooser(shareIntent, getString(R.string.result_share)));
    }
}

