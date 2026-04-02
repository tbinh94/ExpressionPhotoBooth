package com.example.expressionphotobooth;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.Glide;
import com.example.expressionphotobooth.domain.repository.AuthRepository;
import com.example.expressionphotobooth.utils.LocaleManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GalleryActivity extends AppCompatActivity {

    private RecyclerView rvGallery;
    private View emptyState;
    private GalleryAdapter adapter;
    private List<File> galleryFiles = new ArrayList<>();
    private AuthRepository authRepository;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        authRepository = ((AppContainer) getApplication()).getAuthRepository();
        
        rvGallery = findViewById(R.id.rvGallery);
        emptyState = findViewById(R.id.emptyState);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadGallery();
    }

    private void loadGallery() {
        String uid = authRepository.getCurrentUid();
        if (uid == null) {
            finish();
            return;
        }

        File galleryDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "user_gallery/" + uid);
        galleryFiles.clear();

        if (galleryDir.exists() && galleryDir.isDirectory()) {
            File[] files = galleryDir.listFiles((dir, name) -> name.endsWith(".png"));
            if (files != null) {
                Collections.addAll(galleryFiles, files);
                // Sort by last modified (newest first)
                Collections.sort(galleryFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            }
        }

        if (galleryFiles.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvGallery.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvGallery.setVisibility(View.VISIBLE);
            setupRecyclerView();
        }
    }

    private void setupRecyclerView() {
        if (adapter == null) {
            adapter = new GalleryAdapter(galleryFiles);
            // Staggered look for better premium feel
            rvGallery.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
            rvGallery.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }

    private class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {
        private final List<File> files;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        GalleryAdapter(List<File> files) {
            this.files = files;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gallery, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = files.get(position);

            Glide.with(holder.ivPhoto)
                    .load(file)
                    .centerCrop()
                    .into(holder.ivPhoto);

            holder.tvDate.setText(dateFormat.format(new Date(file.lastModified())));

            holder.btnDelete.setOnClickListener(v -> showDeleteConfirm(file, holder.getBindingAdapterPosition()));

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(GalleryActivity.this, FullImageActivity.class);
                intent.putExtra("IMAGE_PATH", file.getAbsolutePath());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPhoto;
            TextView tvDate;
            View btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                ivPhoto = itemView.findViewById(R.id.ivGalleryPhoto);
                tvDate = itemView.findViewById(R.id.tvDate);
                btnDelete = itemView.findViewById(R.id.btnDeleteItem);
            }
        }
    }

    private void showDeleteConfirm(File file, int position) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.gallery_delete_confirm)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    if (file.delete()) {
                        galleryFiles.remove(position);
                        adapter.notifyItemRemoved(position);
                        if (galleryFiles.isEmpty()) {
                            emptyState.setVisibility(View.VISIBLE);
                            rvGallery.setVisibility(View.GONE);
                        }
                        Toast.makeText(this, R.string.gallery_delete_success, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }
}
