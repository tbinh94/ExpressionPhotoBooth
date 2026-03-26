package com.example.expressionphotobooth;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    public interface OnPhotoSelectedListener {
        void onSelectionChanged(List<Uri> selectedUris, int selectedCount);
    }

    private final List<Uri> uris;
    private final OnPhotoSelectedListener onPhotoSelectedListener;
    private final int maxSelection;
    private final Set<Integer> selectedPositions = new LinkedHashSet<>();

    public PhotoAdapter(List<Uri> uris, OnPhotoSelectedListener onPhotoSelectedListener) {
        this(uris, Integer.MAX_VALUE, onPhotoSelectedListener);
    }

    public PhotoAdapter(List<Uri> uris, int maxSelection, OnPhotoSelectedListener onPhotoSelectedListener) {
        this.uris = new ArrayList<>(uris);
        this.maxSelection = Math.max(1, maxSelection);
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
        // Glide handles file/content URI decoding better than setImageURI in RecyclerView.
        Glide.with(holder.imageView)
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(android.R.color.darker_gray)
                .error(android.R.drawable.ic_menu_report_image)
                .into(holder.imageView);
        holder.tvIndex.setText(String.valueOf(position + 1));

        boolean isSelected = selectedPositions.contains(position);
        holder.selectionOverlay.setAlpha(isSelected ? 1f : 0f);
        if (holder.cvSelectedBadge != null) {
            holder.cvSelectedBadge.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current == RecyclerView.NO_POSITION) {
                return;
            }

            if (selectedPositions.contains(current)) {
                selectedPositions.remove(current);
            } else {
                if (selectedPositions.size() >= maxSelection) {
                    Toast.makeText(v.getContext(), v.getContext().getString(R.string.selection_limit_reached, maxSelection), Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedPositions.add(current);
            }
            notifyItemChanged(current);

            if (onPhotoSelectedListener != null) {
                List<Uri> selectedUris = getSelectedUris();
                onPhotoSelectedListener.onSelectionChanged(selectedUris, selectedUris.size());
            }
        });
    }

    @Override
    public int getItemCount() {
        return uris.size();
    }

    public Uri getSelectedUri() {
        List<Uri> selectedUris = getSelectedUris();
        return selectedUris.isEmpty() ? null : selectedUris.get(0);
    }

    public List<Uri> getSelectedUris() {
        List<Uri> result = new ArrayList<>();
        for (Integer selectedPosition : selectedPositions) {
            if (selectedPosition != null && selectedPosition >= 0 && selectedPosition < uris.size()) {
                result.add(uris.get(selectedPosition));
            }
        }
        return result;
    }

    public void setUris(List<Uri> newUris) {
        Set<String> keepSelected = new HashSet<>();
        for (Uri selected : getSelectedUris()) {
            keepSelected.add(selected.toString());
        }

        uris.clear();
        uris.addAll(newUris);
        selectedPositions.clear();

        for (int i = 0; i < uris.size(); i++) {
            if (keepSelected.contains(uris.get(i).toString())) {
                selectedPositions.add(i);
            }
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        if (selectedPositions.isEmpty()) {
            return;
        }
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public static final class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;
        final View selectionOverlay;
        final View cvSelectedBadge;
        final TextView tvIndex;

        public PhotoViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.ivPhoto);
            selectionOverlay = itemView.findViewById(R.id.selectionOverlay);
            cvSelectedBadge = itemView.findViewById(R.id.cvSelectedBadge);
            tvIndex = itemView.findViewById(R.id.tvIndex);
        }
    }
}
