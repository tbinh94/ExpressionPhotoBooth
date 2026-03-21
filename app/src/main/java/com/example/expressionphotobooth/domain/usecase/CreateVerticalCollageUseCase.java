package com.example.expressionphotobooth.domain.usecase;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CreateVerticalCollageUseCase {
    
    /**
     * Ghép các ảnh theo chiều dọc và đè Frame lên trên
     * @param context Context
     * @param imageUris Danh sách Uri ảnh đã chụp/chỉnh sửa
     * @param frameResId ID của Frame (resource drawable)
     * @return Bitmap kết quả đã ghép frame
     */
    public Bitmap execute(Context context, List<String> imageUris, int frameResId) {
        if (imageUris == null || imageUris.isEmpty()) return null;

        // 1. Load các bitmap ảnh đã chụp
        List<Bitmap> bitmaps = new ArrayList<>();
        for (String uriStr : imageUris) {
            try (InputStream is = context.getContentResolver().openInputStream(Uri.parse(uriStr))) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) {
                    bitmaps.add(bmp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (bitmaps.isEmpty()) return null;

        // 2. Load Frame để lấy kích thước làm chuẩn
        Bitmap frameBitmap = null;
        if (frameResId != 0) {
            frameBitmap = BitmapFactory.decodeResource(context.getResources(), frameResId);
        }

        int resultWidth;
        int resultHeight;

        if (frameBitmap != null) {
            // Nếu có frame, ta lấy kích thước frame làm chuẩn
            resultWidth = frameBitmap.getWidth();
            resultHeight = frameBitmap.getHeight();
        } else {
            // Nếu không có frame, tính toán dựa trên ảnh (stack dọc)
            resultWidth = bitmaps.get(0).getWidth();
            int totalH = 0;
            for (Bitmap b : bitmaps) totalH += b.getHeight();
            resultHeight = totalH;
        }

        // 3. Tạo Bitmap kết quả
        Bitmap result = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        // 4. Vẽ các ảnh vào Frame
        // Chia đều chiều cao frame cho số lượng ảnh (giả định frame có slots đều nhau)
        int slotHeight = resultHeight / bitmaps.size();
        
        for (int i = 0; i < bitmaps.size(); i++) {
            Bitmap bmp = bitmaps.get(i);
            
            // Tính toán vùng vẽ cho tấm ảnh này (để nó nằm gọn trong slot)
            Rect destRect = new Rect(0, i * slotHeight, resultWidth, (i + 1) * slotHeight);
            
            // Vẽ ảnh (tự động scale để fit vào destRect)
            canvas.drawBitmap(bmp, null, destRect, null);
        }

        // 5. Vẽ Frame đè lên trên cùng (Frame thường là PNG có vùng trong suốt ở giữa các slot)
        if (frameBitmap != null) {
            canvas.drawBitmap(frameBitmap, 0, 0, null);
            frameBitmap.recycle();
        }

        // Giải phóng bộ nhớ các bitmap tạm
        for (Bitmap b : bitmaps) {
            b.recycle();
        }

        return result;
    }

    // Giữ lại method cũ để không làm lỗi code hiện tại (nếu có chỗ khác gọi)
    public Bitmap execute(Context context, List<String> imageUris) {
        return execute(context, imageUris, 0);
    }
}
