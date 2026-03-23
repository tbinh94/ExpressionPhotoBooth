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

public class FrameAdapter extends RecyclerView.Adapter<FrameAdapter.FrameViewHolder> {

    public interface OnFrameSelectedListener {
        void onFrameSelected(Frame frame);
    }

    private final List<Frame> frameList;
    private final OnFrameSelectedListener onFrameSelectedListener;
    private int selectedFrameId;

    public FrameAdapter(List<Frame> frameList, int selectedFrameId, OnFrameSelectedListener onFrameSelectedListener) {
        this.frameList = frameList;
        this.selectedFrameId = selectedFrameId;
        this.onFrameSelectedListener = onFrameSelectedListener;
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
        holder.imgFrame.setImageResource(frame.getImageResId());
        holder.tvFrameName.setText(frame.getLabel());

        boolean isSelected = frame.getId() == selectedFrameId;
        holder.itemView.setSelected(isSelected);
        holder.selectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);

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

        public FrameViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFrame = itemView.findViewById(R.id.imgFrame);
            tvFrameName = itemView.findViewById(R.id.tvFrameName);
            selectionOverlay = itemView.findViewById(R.id.selectionOverlay);
        }
    }
}