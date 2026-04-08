package com.example.expressionphotobooth.data.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expressionphotobooth.R;
import com.example.expressionphotobooth.domain.model.Frame;

import java.util.List;
import java.util.Map;

public class FrameAdapter extends RecyclerView.Adapter<FrameAdapter.FrameViewHolder> {

    public interface OnFrameSelectedListener {
        void onFrameSelected(Frame frame);
    }

    private final List<Frame> frameList;
    private final OnFrameSelectedListener onFrameSelectedListener;
    private int selectedFrameId;

    /** Optional: map of frameId -> rank (1-based). Only shown when not null. */
    private Map<Integer, Integer> rankMap;

    public FrameAdapter(List<Frame> frameList, int selectedFrameId, OnFrameSelectedListener onFrameSelectedListener) {
        this.frameList = frameList;
        this.selectedFrameId = selectedFrameId;
        this.onFrameSelectedListener = onFrameSelectedListener;
    }

    public void setRankMap(Map<Integer, Integer> rankMap) {
        this.rankMap = rankMap;
    }

    @NonNull
    @Override
    public FrameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_frame, parent, false);
        return new FrameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FrameViewHolder holder, int position) {
        Frame frame = frameList.get(position);

        // Load image
        if (frame.isRemote()) {
            try {
                byte[] decodedString = android.util.Base64.decode(frame.getRemoteBase64(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                holder.imgFrame.setImageBitmap(bitmap);
            } catch (Exception e) {
                holder.imgFrame.setImageResource(R.drawable.ic_image_error);
            }
        } else {
            holder.imgFrame.setImageResource(frame.getImageResId());
        }

        holder.tvFrameName.setText(frame.getLabel());

        // Selection state
        boolean isSelected = frame.getId() == selectedFrameId;
        holder.itemView.setSelected(isSelected);
        holder.selectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        // Rank badge — only show in Top 3 concept
        if (holder.tvRankBadge != null) {
            if (rankMap != null && rankMap.containsKey(frame.getId())) {
                int rank = rankMap.get(frame.getId());
                holder.tvRankBadge.setVisibility(View.VISIBLE);
                holder.tvRankBadge.setText("#" + rank);
                // Gold / Silver / Bronze tint
                int badgeColor;
                switch (rank) {
                    case 1:  badgeColor = 0xFFFFD700; break; // Gold
                    case 2:  badgeColor = 0xFFC0C0C0; break; // Silver
                    default: badgeColor = 0xFFCD7F32; break; // Bronze
                }
                holder.tvRankBadge.getBackground().setTint(badgeColor);
            } else {
                holder.tvRankBadge.setVisibility(View.GONE);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            selectedFrameId = frame.getId();
            notifyDataSetChanged();
            if (onFrameSelectedListener != null) {
                onFrameSelectedListener.onFrameSelected(frame);
            }
        });
    }

    @Override
    public int getItemCount() {
        return frameList != null ? frameList.size() : 0;
    }

    static class FrameViewHolder extends RecyclerView.ViewHolder {
        ImageView imgFrame;
        TextView tvFrameName;
        View selectionOverlay;
        TextView tvRankBadge;

        public FrameViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFrame = itemView.findViewById(R.id.imgFrame);
            tvFrameName = itemView.findViewById(R.id.tvFrameName);
            selectionOverlay = itemView.findViewById(R.id.selectionOverlay);
            tvRankBadge = itemView.findViewById(R.id.tvRankBadge);
        }
    }
}