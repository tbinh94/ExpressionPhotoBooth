package com.example.expressionphotobooth;

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
    private OnFrameClickListener listener;
    private int selectedPosition = -1; // Biến lưu vị trí frame đang được chọn

    // 1. Tạo "bộ đàm" giao tiếp
    public interface OnFrameClickListener {
        void onFrameClick(Frame frame);
    }

    // 2. Cập nhật Constructor để nhận bộ đàm
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

        // 3. Hiệu ứng thị giác: Nếu đang được chọn thì làm mờ ảnh một chút để báo hiệu
        if (selectedPosition == holder.getAdapterPosition()) {
            holder.itemView.setAlpha(0.5f); // Làm mờ đi 50%
        } else {
            holder.itemView.setAlpha(1.0f); // Rõ nét 100%
        }

        // 4. Bắt sự kiện Click
        holder.itemView.setOnClickListener(v -> {
            selectedPosition = holder.getAdapterPosition();
            notifyDataSetChanged(); // Yêu cầu vẽ lại danh sách để cập nhật hiệu ứng mờ
            if (listener != null) {
                listener.onFrameClick(frame); // Truyền dữ liệu frame bị click qua bộ đàm
            }
        });
    }

    @Override
    public int getItemCount() { return frameList != null ? frameList.size() : 0; }

    static class FrameViewHolder extends RecyclerView.ViewHolder {
        ImageView imgFrame;
        public FrameViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFrame = itemView.findViewById(R.id.imgFrame);
        }
    }
}