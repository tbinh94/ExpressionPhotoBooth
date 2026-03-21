package com.example.expressionphotobooth.data.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expressionphotobooth.R;
import com.example.expressionphotobooth.domain.model.Frame;
import java.util.List;

public class FrameAdapter extends RecyclerView.Adapter<FrameAdapter.FrameViewHolder> {

    private List<Frame> frameList;
    private int selectedPosition = -1;
    private OnFrameClickListener listener;

    public interface OnFrameClickListener {
        void onFrameClick(Frame frame);
    }

    public FrameAdapter(List<Frame> frameList, OnFrameClickListener listener) {
        this.frameList = frameList;
        this.listener = listener;
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

        // Hiển thị trạng thái được chọn
        if (selectedPosition == position) {
            holder.itemView.setBackgroundResource(R.drawable.bg_frame_selected);
        } else {
            holder.itemView.setBackgroundResource(0);
        }

        holder.itemView.setOnClickListener(v -> {
            int previousPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);

            if (listener != null) {
                listener.onFrameClick(frame);
            }
        });
    }

    @Override
    public int getItemCount() {
        return frameList != null ? frameList.size() : 0;
    }

    static class FrameViewHolder extends RecyclerView.ViewHolder {
        ImageView imgFrame;
        public FrameViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFrame = itemView.findViewById(R.id.imgFrame);
        }
    }
}
