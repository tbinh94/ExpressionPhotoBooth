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

    public static int getSlotCountForLayout(String layout) {
        return getHolesForLayout(layout).size();
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

    public static List<Rect> getHolesForLayout(String layout) {
        List<Rect> holes = new ArrayList<>();
        if ("3x4_4".equals(layout)) {
            holes.add(new Rect(21, 19, 164, 205));
            holes.add(new Rect(186, 19, 329, 205));
            holes.add(new Rect(21, 226, 164, 412));
            holes.add(new Rect(186, 226, 329, 412));
        } else if ("16x9_3".equals(layout)) {
            holes.add(new Rect(22, 30, 260, 162));
            holes.add(new Rect(22, 187, 260, 322));
            holes.add(new Rect(22, 343, 260, 477));
        } else if ("16x9_4".equals(layout)) {
            holes.add(new Rect(17, 18, 255, 152));
            holes.add(new Rect(17, 168, 255, 302));
            holes.add(new Rect(17, 317, 255, 451));
            holes.add(new Rect(17, 467, 255, 601));
        } else {
            return getDefaultHoles();
        }
        return holes;
    }
    /**
     * Returns holes scaled to the ACTUAL pixel dimensions of a remote/uploaded frame.
     * Holes are stored as normalized fractions (0-1) derived from the reference
     * local drawable's native pixel size, then multiplied by actual frame W/H.
     *
     * Reference dimensions measured from local drawables:
     *   3x4_4  : refW=350, refH=432
     *   16x9_3 : refW=282, refH=500
     *   16x9_4 : refW=272, refH=622
     */
    public static List<Rect> getHolesForLayoutScaled(String layout, int frameW, int frameH) {
        List<Rect> holes = new ArrayList<>();

        if ("3x4_4".equals(layout)) {
            // Reference frame: 350 x 432 px
            // Normalized from: (21,19,164,205),(186,19,329,205),(21,226,164,412),(186,226,329,412)
            float rW = 350f, rH = 432f;
            holes.add(normRect( 21f,  19f, 164f, 205f, rW, rH, frameW, frameH));
            holes.add(normRect(186f,  19f, 329f, 205f, rW, rH, frameW, frameH));
            holes.add(normRect( 21f, 226f, 164f, 412f, rW, rH, frameW, frameH));
            holes.add(normRect(186f, 226f, 329f, 412f, rW, rH, frameW, frameH));
        } else if ("16x9_3".equals(layout)) {
            // Reference frame: 282 x 500 px
            float rW = 282f, rH = 500f;
            holes.add(normRect( 22f,  30f, 260f, 162f, rW, rH, frameW, frameH));
            holes.add(normRect( 22f, 187f, 260f, 322f, rW, rH, frameW, frameH));
            holes.add(normRect( 22f, 343f, 260f, 477f, rW, rH, frameW, frameH));
        } else if ("16x9_4".equals(layout)) {
            // Reference frame: 272 x 622 px
            float rW = 272f, rH = 622f;
            holes.add(normRect( 17f,  18f, 255f, 152f, rW, rH, frameW, frameH));
            holes.add(normRect( 17f, 168f, 255f, 302f, rW, rH, frameW, frameH));
            holes.add(normRect( 17f, 317f, 255f, 451f, rW, rH, frameW, frameH));
            holes.add(normRect( 17f, 467f, 255f, 601f, rW, rH, frameW, frameH));
        } else {
            // Fallback: divide frame into equal vertical strips
            return getDefaultHolesScaled(frameW, frameH);
        }
        return holes;
    }

    private static Rect normRect(float l, float t, float r, float b,
                                  float refW, float refH,
                                  int frameW, int frameH) {
        return new Rect(
                Math.round((l / refW) * frameW),
                Math.round((t / refH) * frameH),
                Math.round((r / refW) * frameW),
                Math.round((b / refH) * frameH)
        );
    }

    private static List<Rect> getDefaultHolesScaled(int frameW, int frameH) {
        List<Rect> holes = new ArrayList<>();
        float rW = 272f, rH = 622f;
        holes.add(normRect(17f,  18f, 255f, 152f, rW, rH, frameW, frameH));
        holes.add(normRect(17f, 168f, 255f, 302f, rW, rH, frameW, frameH));
        holes.add(normRect(17f, 317f, 255f, 451f, rW, rH, frameW, frameH));
        holes.add(normRect(17f, 467f, 255f, 601f, rW, rH, frameW, frameH));
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

    public static Rect getPrimaryHoleForFrame(int frameResId) {
        List<Rect> holes = getHolesForFrame(frameResId);
        if (holes.isEmpty()) {
            return null;
        }
        Rect hole = holes.get(0);
        return new Rect(hole.left, hole.top, hole.right, hole.bottom);
    }


}
