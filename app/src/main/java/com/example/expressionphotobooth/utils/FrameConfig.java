package com.example.expressionphotobooth.utils;

import android.graphics.Rect;

import com.example.expressionphotobooth.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FrameConfig {

    // Returns number of photo slots required by a frame.
    public static int getSlotCountForFrame(int frameResId) {
        return getHolesForFrame(frameResId).size();
    }

    // Hàm này trả về danh sách các hình chữ nhật (vị trí các lỗ hổng) dựa vào ID của Frame
    public static List<Rect> getHolesForFrame(int frameResId) {
        List<Rect> holes = new ArrayList<>();


        // Danh sách các frame có cùng cấu trúc 3x4
        List<Integer> Frame_3x4 = Arrays.asList(
                R.drawable.frm_3x4_cushin,
                R.drawable.frm_3x4_movie,
                R.drawable.frm_3x4_pig_hero // Thêm các ảnh khác vào đây
        );


        if (Frame_3x4.contains(frameResId)) {
            holes.add(new Rect(21, 19, 164, 205));
            holes.add(new Rect(186, 19, 329, 205));
            holes.add(new Rect(21, 226, 164, 412));
            holes.add(new Rect(186, 226, 329, 412));
        }


        // Danh sách các frame có cùng cấu trúc 16x9 (3 ảnh)
        List<Integer> Frame3_16x9 = Arrays.asList(
                R.drawable.frm3_16x9_blue_canvas,
                R.drawable.frm3_16x9_green_doodle,
                R.drawable.frm3_16x9_red_star // Thêm các ảnh khác vào đây
        );


        if (Frame3_16x9.contains(frameResId)) {
            holes.add(new Rect(22, 30, 260, 162));
            holes.add(new Rect(22, 187, 260, 322));
            holes.add(new Rect(22, 343, 260, 477));
        }


        // Danh sách các frame có cùng cấu trúc 16x9 (4 ảnh)
        List<Integer> Frame4_16x9 = Arrays.asList(
                R.drawable.frm4_16x9_bow,
                R.drawable.frm4_16x9_food,
                R.drawable.frm4_16x9_heart // Thêm các ảnh khác vào đây
        );


        if (Frame4_16x9.contains(frameResId)) {
            holes.add(new Rect(17, 18, 255, 152));
            holes.add(new Rect(17, 168, 255, 302));
            holes.add(new Rect(17, 317, 255, 451));
            holes.add(new Rect(17, 467, 255, 601));
        }

        if (holes.isEmpty()) {
            return getDefaultHoles();
        }
        
        return holes;
    }

    public static List<Rect> getDefaultHoles() {
        List<Rect> holes = new ArrayList<>();
        holes.add(new Rect(17, 18, 255, 152));
        holes.add(new Rect(17, 168, 255, 302));
        holes.add(new Rect(17, 317, 255, 451));
        holes.add(new Rect(17, 467, 255, 601));
        return holes;
    }


}
