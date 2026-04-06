package com.example.expressionphotobooth.domain.usecase;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult;

import java.nio.FloatBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI Portrait Mode — sử dụng ML Kit Subject Segmentation.
 *
 * Flow:
 *   Camera Frame → Person Segmentation → Binary Mask
 *   → Gaussian Blur Background → Composite → Portrait Output
 *
 * Cách dùng:
 *   PortraitProcessor pp = new PortraitProcessor();
 *   pp.process(bitmap, result -> {
 *       // result là Bitmap portrait đã xử lý
 *   }, error -> {
 *       // xử lý lỗi
 *   });
 */
public class PortraitProcessor {

    private static final String TAG = "PortraitProcessor";

    /** Bán kính blur — càng lớn càng mờ hậu cảnh. */
    private static final int BLUR_RADIUS = 25;

    /** Ngưỡng confidence để tách chủ thể (0.0–1.0) */
    private static final float MASK_THRESHOLD = 0.5f;

    private final SubjectSegmenter segmenter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface OnPortraitReady {
        void onResult(@NonNull Bitmap portraitBitmap);
    }

    public interface OnPortraitError {
        void onError(@NonNull String message);
    }

    public PortraitProcessor() {
        SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .build();
        segmenter = SubjectSegmentation.getClient(options);
    }

    /**
     * Xử lý ảnh thành portrait mode (background blur).
     * Callback luôn được gọi trên background thread — caller cần tự chuyển về UI thread.
     */
    public void process(@NonNull Bitmap source,
                        @NonNull OnPortraitReady onReady,
                        @NonNull OnPortraitError onError) {

        executor.execute(() -> {
            try {
                InputImage inputImage = InputImage.fromBitmap(source, 0);
                Task<SubjectSegmentationResult> task = segmenter.process(inputImage);
                SubjectSegmentationResult result = Tasks.await(task);

                FloatBuffer confidenceMask = result.getForegroundConfidenceMask();
                if (confidenceMask == null) {
                    onError.onError("Không phát hiện chủ thể trong ảnh");
                    return;
                }

                int w = source.getWidth();
                int h = source.getHeight();

                // ── Bước 1: Tạo binary mask từ confidence map ──
                Bitmap maskBitmap = createMask(confidenceMask, w, h);

                // ── Bước 2: Blur toàn bộ ảnh gốc (Gaussian-like blur) ──
                Bitmap blurredBg = stackBlur(source, BLUR_RADIUS);

                // ── Bước 3: Composite (người sharp + nền blur) ──
                Bitmap portrait = composite(source, blurredBg, maskBitmap);

                maskBitmap.recycle();
                blurredBg.recycle();

                Log.d(TAG, "✅ Portrait processing complete: " + w + "x" + h);
                onReady.onResult(portrait);

            } catch (Exception e) {
                Log.e(TAG, "Portrait processing failed", e);
                onError.onError(e.getMessage());
            }
        });
    }

    /**
     * Tạo bitmap mask trắng/đen từ FloatBuffer confidence.
     */
    private Bitmap createMask(FloatBuffer confidenceBuffer, int width, int height) {
        confidenceBuffer.rewind();
        int[] pixels = new int[width * height];

        for (int i = 0; i < width * height; i++) {
            float confidence = confidenceBuffer.get();
            // Smooth edge: áp dụng alpha dựa trên confidence thay vì hard threshold
            int alpha;
            if (confidence >= MASK_THRESHOLD + 0.15f) {
                alpha = 255;
            } else if (confidence <= MASK_THRESHOLD - 0.15f) {
                alpha = 0;
            } else {
                // Feather edge: chuyển tiếp mượt giữa 0 và 255
                float t = (confidence - (MASK_THRESHOLD - 0.15f)) / 0.30f;
                alpha = (int) (t * 255);
            }
            pixels[i] = (alpha << 24) | 0x00FFFFFF; // White pixel with variable alpha
        }

        Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mask.setPixels(pixels, 0, width, 0, 0, width, height);
        return mask;
    }

    /**
     * Ghép ảnh gốc (foreground) lên nền blur, sử dụng mask làm alpha.
     */
    private Bitmap composite(Bitmap foreground, Bitmap blurredBg, Bitmap mask) {
        int w = foreground.getWidth();
        int h = foreground.getHeight();

        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Vẽ nền blur
        canvas.drawBitmap(blurredBg, 0, 0, paint);

        // Tạo foreground được mask
        Bitmap maskedForeground = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas fgCanvas = new Canvas(maskedForeground);
        fgCanvas.drawBitmap(mask, 0, 0, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        fgCanvas.drawBitmap(foreground, 0, 0, paint);
        paint.setXfermode(null);

        // Vẽ foreground lên output
        canvas.drawBitmap(maskedForeground, 0, 0, new Paint(Paint.ANTI_ALIAS_FLAG));
        maskedForeground.recycle();

        return output;
    }

    /**
     * Stack blur nhanh — biến thể tối ưu của Box Blur nhiều pass.
     * Không cần RenderScript hay thư viện bên ngoài.
     */
    private Bitmap stackBlur(Bitmap source, int radius) {
        if (radius < 1) return source.copy(source.getConfig(), true);

        Bitmap bitmap = source.copy(Bitmap.Config.ARGB_8888, true);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1, hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int[] r = new int[wh], g = new int[wh], b = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;

        int[] vmin = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer, stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < w; x++) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum];

                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                }

                if (i < hm) { yp += w; }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum; gsum -= goutsum; bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p];

                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2];
                rsum += rinsum; gsum += ginsum; bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2];
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return bitmap;
    }
}
