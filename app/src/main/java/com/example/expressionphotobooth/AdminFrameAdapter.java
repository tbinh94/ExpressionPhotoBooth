package com.example.expressionphotobooth;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminFrameAdapter extends RecyclerView.Adapter<AdminFrameAdapter.FrameViewHolder> {

    private final List<FrameModel> frameList;
    private final OnFrameDeleteListener deleteListener;

    public interface OnFrameDeleteListener {
        void onDelete(FrameModel frame);
    }

    public AdminFrameAdapter(List<FrameModel> frameList, OnFrameDeleteListener deleteListener) {
        this.frameList = frameList;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public FrameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_frame, parent, false);
        return new FrameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FrameViewHolder holder, int position) {
        FrameModel frame = frameList.get(position);
        holder.tvLabel.setText(frame.getLabel());
        holder.tvLayout.setText(frame.getLayoutType());

        Bitmap frameBitmap = null;
        if (frame.getBase64() != null && !frame.getBase64().isEmpty()) {
            try {
                byte[] decoded = android.util.Base64.decode(frame.getBase64(), android.util.Base64.DEFAULT);
                frameBitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                holder.ivFrame.setImageBitmap(frameBitmap);
            } catch (Exception e) {
                e.printStackTrace();
                holder.ivFrame.setImageDrawable(null);
            }
        } else {
            holder.ivFrame.setImageDrawable(null);
        }

        final Bitmap finalBitmap = frameBitmap;

        // Click item → show detail bottom sheet
        holder.itemView.setOnClickListener(v -> showDetailSheet(v, frame, finalBitmap));

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(frame);
            }
        });
    }

    private void showDetailSheet(View anchorView, FrameModel frame, Bitmap bitmap) {
        BottomSheetDialog sheet = new BottomSheetDialog(anchorView.getContext(), R.style.BottomSheetDialogTheme);
        View view = LayoutInflater.from(anchorView.getContext())
                .inflate(R.layout.bottom_sheet_frame_detail, null);
        sheet.setContentView(view);

        // Frame preview
        ImageView ivPreview = view.findViewById(R.id.bsIvFrame);
        if (bitmap != null) {
            ivPreview.setImageBitmap(bitmap);
        }

        // Label
        TextView tvLabel = view.findViewById(R.id.bsTvLabel);
        tvLabel.setText(frame.getLabel());

        // Layout type (human-readable)
        TextView tvLayout = view.findViewById(R.id.bsTvLayout);
        tvLayout.setText(readableLayout(frame.getLayoutType()));

        // Slot count
        TextView tvSlot = view.findViewById(R.id.bsTvSlotCount);
        tvSlot.setText(slotCountForLayout(frame.getLayoutType()) + " ảnh");

        // Upload date
        TextView tvDate = view.findViewById(R.id.bsTvDate);
        if (frame.getCreatedAt() > 0) {
            String dateStr = new SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault())
                    .format(new Date(frame.getCreatedAt()));
            tvDate.setText(dateStr);
        } else {
            tvDate.setText("Không rõ");
        }

        // Frame ID (short)
        TextView tvId = view.findViewById(R.id.bsTvId);
        String id = frame.getId() != null ? frame.getId() : "---";
        tvId.setText(id.length() > 16 ? id.substring(0, 16) + "…" : id);

        // Delete button
        MaterialButton btnDelete = view.findViewById(R.id.bsBtnDelete);
        btnDelete.setOnClickListener(v -> {
            sheet.dismiss();
            if (deleteListener != null) {
                deleteListener.onDelete(frame);
            }
        });

        // Close button
        MaterialButton btnClose = view.findViewById(R.id.bsBtnClose);
        btnClose.setOnClickListener(v -> sheet.dismiss());

        sheet.show();
    }

    private String readableLayout(String layoutCode) {
        if (layoutCode == null) return "Không rõ";
        switch (layoutCode) {
            case "3x4_4":   return "3×4 – 4 ảnh";
            case "16x9_3":  return "16×9 – 3 ảnh";
            case "16x9_4":  return "16×9 – 4 ảnh";
            default:        return layoutCode;
        }
    }

    private int slotCountForLayout(String layoutCode) {
        if (layoutCode == null) return 0;
        switch (layoutCode) {
            case "3x4_4":  return 4;
            case "16x9_3": return 3;
            case "16x9_4": return 4;
            default:       return 0;
        }
    }

    @Override
    public int getItemCount() {
        return frameList.size();
    }

    static class FrameViewHolder extends RecyclerView.ViewHolder {
        ImageView ivFrame;
        TextView tvLabel;
        TextView tvLayout;
        ImageButton btnDelete;

        public FrameViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFrame    = itemView.findViewById(R.id.ivFrame);
            tvLabel    = itemView.findViewById(R.id.tvFrameLabel);
            tvLayout   = itemView.findViewById(R.id.tvFrameLayout);
            btnDelete  = itemView.findViewById(R.id.btnDelete);
        }
    }
}
