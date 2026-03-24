package com.example.expressionphotobooth.utils;

import android.graphics.Rect;

import com.example.expressionphotobooth.R;

import java.util.ArrayList;
import java.util.List;

public class FrameConfig {

    // Hàm này trả về danh sách các hình chữ nhật (vị trí các lỗ hổng) dựa vào ID của Frame
    public static List<Rect> getHolesForFrame(int frameResId) {
        List<Rect> holes = new ArrayList<>();

        // Ví dụ: Đo tọa độ cho Frame số 1 (Princess 4 ảnh)
        if (frameResId == R.drawable.frm_basic_white) {
            // Rect(trái, trên, phải, dưới) - Đo bằng Pixel tương ứng với kích thước gốc của file Frame.png
            holes.add(new Rect(10, 15, 170, 125)); // Lỗ 1
            holes.add(new Rect(10, 130, 170, 240)); // Lỗ 2
            holes.add(new Rect(10, 245, 170, 355)); // Lỗ 3
            holes.add(new Rect(10, 365, 170, 475)); // Lỗ 4
        }

        if (frameResId == R.drawable.frm_brown_caro) {
            // Rect(trái, trên, phải, dưới) - Đo bằng Pixel tương ứng với kích thước gốc của file Frame.png
            holes.add(new Rect(8, 12, 220, 165)); // Lỗ 1
            holes.add(new Rect(8, 178, 220, 331)); // Lỗ 2
            holes.add(new Rect(8, 348, 220, 500)); // Lỗ 3
//            holes.add(new Rect(8, 365, 170, 475)); // Lỗ 4
        }

        return holes;
    }
}
