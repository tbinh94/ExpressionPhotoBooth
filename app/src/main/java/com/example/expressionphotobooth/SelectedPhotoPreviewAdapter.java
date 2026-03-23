package com.example.expressionphotobooth;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

// Horizontal preview list of currently selected photos.
public class SelectedPhotoPreviewAdapter extends RecyclerView.Adapter<SelectedPhotoPreviewAdapter.SelectedPhotoViewHolder> {

    private final List<Uri> selectedUris = new ArrayList<>();

    @NonNull
    @Override
    public SelectedPhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selected_photo, parent, false);
        return new SelectedPhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SelectedPhotoViewHolder holder, int position) {
        Uri uri = selectedUris.get(position);
        Glide.with(holder.ivSelectedPreview)
                .load(uri)
                .centerCrop()
                .into(holder.ivSelectedPreview);
        holder.tvSelectedOrder.setText(String.valueOf(position + 1));
    }

    @Override
    public int getItemCount() {
        return selectedUris.size();
    }

    public void setSelectedUris(List<Uri> uris) {
        selectedUris.clear();
        if (uris != null) {
            selectedUris.addAll(uris);
        }
        notifyDataSetChanged();
    }

    static final class SelectedPhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivSelectedPreview;
        final TextView tvSelectedOrder;

        SelectedPhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSelectedPreview = itemView.findViewById(R.id.ivSelectedPreview);
            tvSelectedOrder = itemView.findViewById(R.id.tvSelectedOrder);
        }
    }
}

