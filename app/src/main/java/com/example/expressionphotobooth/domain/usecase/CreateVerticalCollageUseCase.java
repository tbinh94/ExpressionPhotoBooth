package com.example.expressionphotobooth.domain.usecase;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CreateVerticalCollageUseCase {

    /**
     * Ghép các ảnh theo chiều dọc và đè Frame lên trên.
     * Tối ưu hóa việc cắt ảnh (Center Crop) để khớp hoàn hảo với các ô trong Frame.
     *
     * @param context Context
     * @param imageUris Danh sách Uri ảnh đã chụp/chỉnh sửa
     * @param frameResId ID của Frame (resource drawable)
     * @return Bitmap kết quả đã ghép frame
     */
    public Bitmap execute(Context context, List<String> imageUris, int frameResId) {
        if (imageUris == null || imageUris.isEmpty()) return null;

        // 1. Load Frame để lấy kích thước làm chuẩn
        Bitmap frameBitmap = null;
        if (frameResId != 0) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            frameBitmap = BitmapFactory.decodeResource(context.getResources(), frameResId, options);
        }

        if (frameBitmap == null) return null;

        int resultWidth = frameBitmap.getWidth();
        int resultHeight = frameBitmap.getHeight();

        // 2. Tạo Bitmap kết quả với kích thước của Frame
        Bitmap result = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

        // 3. Load và vẽ từng ảnh vào các ô (slots) của Frame
        int numPhotos = imageUris.size();
        int slotHeight = resultHeight / numPhotos;

        for (int i = 0; i < numPhotos; i++) {
            try (InputStream is = context.getContentResolver().openInputStream(Uri.parse(imageUris.get(i)))) {
                Bitmap originalBmp = BitmapFactory.decodeStream(is);
                if (originalBmp != null) {
                    // Xác định vùng đích (ô trong frame)
                    Rect destRect = new Rect(0, i * slotHeight, resultWidth, (i + 1) * slotHeight);

                    // Thực hiện Center Crop để ảnh không bị méo và lấp đầy ô
                    Rect srcRect = calculateCenterCropRect(originalBmp, destRect.width(), destRect.height());

                    canvas.drawBitmap(originalBmp, srcRect, destRect, paint);
                    originalBmp.recycle();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 4. Vẽ Frame đè lên trên cùng
        canvas.drawBitmap(frameBitmap, 0, 0, paint);
        frameBitmap.recycle();

        return result;
    }

    /**
     * Tính toán Rect để cắt ảnh theo kiểu Center Crop
     */
    private Rect calculateCenterCropRect(Bitmap bitmap, int targetWidth, int targetHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float targetAspect = (float) targetWidth / targetHeight;
        float actualAspect = (float) width / height;

        int srcWidth, srcHeight;
        int srcX, srcY;

        if (actualAspect > targetAspect) {
            // Ảnh quá rộng so với mục tiêu -> Cắt bớt 2 bên
            srcHeight = height;
            srcWidth = (int) (height * targetAspect);
            srcX = (width - srcWidth) / 2;
            srcY = 0;
        } else {
            // Ảnh quá cao so với mục tiêu -> Cắt bớt trên dưới
            srcWidth = width;
            srcHeight = (int) (width / targetAspect);
            srcX = 0;
            srcY = (height - srcHeight) / 2;
        }

        return new Rect(srcX, srcY, srcX + srcWidth, srcY + srcHeight);
    }

    public Bitmap execute(Context context, List<String> imageUris) {
        return execute(context, imageUris, 0);
    }
}