package com.example.expressionphotobooth.domain.usecase;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CreateVerticalCollageUseCase {
    public Bitmap execute(Context context, List<String> imageUris) {
        if (imageUris == null || imageUris.isEmpty()) return null;

        List<Bitmap> bitmaps = new ArrayList<>();
        int totalHeight = 0;
        int maxWidth = 0;

        for (String uriStr : imageUris) {
            try (InputStream is = context.getContentResolver().openInputStream(Uri.parse(uriStr))) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) {
                    bitmaps.add(bmp);
                    totalHeight += bmp.getHeight();
                    maxWidth = Math.max(maxWidth, bmp.getWidth());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (bitmaps.isEmpty()) return null;

        Bitmap collage = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(collage);
        int currentY = 0;

        for (Bitmap bmp : bitmaps) {
            // Vẽ ảnh vào giữa theo chiều ngang nếu độ rộng khác nhau
            int left = (maxWidth - bmp.getWidth()) / 2;
            canvas.drawBitmap(bmp, left, currentY, null);
            currentY += bmp.getHeight();
            // Không recycle ở đây vì có thể là ảnh gốc đang dùng ở chỗ khác,
            // nhưng trong case này thường là các bitmap temp được decode ra.
        }

        return collage;
    }
}
