package com.example.expressionphotobooth;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    public interface OnPhotoSelectedListener {
        void onPhotoSelected(Uri uri, int position);
    }

    private final List<Uri> uris;
    private final OnPhotoSelectedListener onPhotoSelectedListener;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public PhotoAdapter(List<Uri> uris, OnPhotoSelectedListener onPhotoSelectedListener) {
        this.uris = uris;
        this.onPhotoSelectedListener = onPhotoSelectedListener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PhotoViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Uri uri = uris.get(position);
        holder.imageView.setImageURI(uri);
        holder.tvIndex.setText(String.valueOf(position + 1));

        boolean isSelected = position == selectedPosition;
        holder.selectionOverlay.setAlpha(isSelected ? 0.45f : 0f);
        holder.tvSelected.setAlpha(isSelected ? 1f : 0f);

        holder.itemView.setOnClickListener(v -> {
            int previous = selectedPosition;
            selectedPosition = holder.getBindingAdapterPosition();
            if (selectedPosition == RecyclerView.NO_POSITION) {
                return;
            }

            if (previous != RecyclerView.NO_POSITION) {
                notifyItemChanged(previous);
            }
            notifyItemChanged(selectedPosition);

            if (onPhotoSelectedListener != null) {
                onPhotoSelectedListener.onPhotoSelected(uris.get(selectedPosition), selectedPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return uris.size();
    }

    public Uri getSelectedUri() {
        if (selectedPosition >= 0 && selectedPosition < uris.size()) {
            return uris.get(selectedPosition);
        }
        return null;
    }

    public static final class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;
        final ViewGroup selectionOverlay;
        final TextView tvSelected;
        final TextView tvIndex;

        public PhotoViewHolder(android.view.View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.ivPhoto);
            selectionOverlay = itemView.findViewById(R.id.selectionOverlay);
            tvSelected = itemView.findViewById(R.id.tvSelected);
            tvIndex = itemView.findViewById(R.id.tvIndex);
        }
    }
}