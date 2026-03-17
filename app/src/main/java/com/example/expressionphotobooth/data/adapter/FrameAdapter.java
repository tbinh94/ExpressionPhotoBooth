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

    public FrameAdapter(List<Frame> frameList) {
        this.frameList = frameList;
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