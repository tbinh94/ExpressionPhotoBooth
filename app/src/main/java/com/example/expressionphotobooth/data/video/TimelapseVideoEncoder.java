package com.example.expressionphotobooth.data.video;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.jcodec.api.android.AndroidSequenceEncoder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

// Encoder tao video timelapse tu danh sach anh chup.
public class TimelapseVideoEncoder {
    private static final int MAX_VIDEO_SIDE = 1280;

    public File encode(ContentResolver contentResolver, List<String> imageUris, File outputFile, int fps) throws IOException {
        if (imageUris == null || imageUris.isEmpty()) {
            throw new IOException("No images to encode");
        }

        Uri firstUri = Uri.parse(imageUris.get(0));
        BitmapFactory.Options firstBounds = readBounds(contentResolver, firstUri);
        if (firstBounds == null || firstBounds.outWidth <= 0 || firstBounds.outHeight <= 0) {
            throw new IOException("Cannot decode first image bounds");
        }

        int[] targetSize = resolveTargetSize(firstBounds.outWidth, firstBounds.outHeight);
        int targetWidth = toEven(targetSize[0]);
        int targetHeight = toEven(targetSize[1]);

        Bitmap first = decodeBitmap(contentResolver, firstUri, targetWidth, targetHeight);
        if (first == null) {
            throw new IOException("Cannot decode first image");
        }

        AndroidSequenceEncoder encoder = AndroidSequenceEncoder.createSequenceEncoder(outputFile, fps);

        try {
            encodeFrame(encoder, first, targetWidth, targetHeight);
            for (int i = 1; i < imageUris.size(); i++) {
                Bitmap bitmap = decodeBitmap(contentResolver, Uri.parse(imageUris.get(i)), targetWidth, targetHeight);
                if (bitmap != null) {
                    encodeFrame(encoder, bitmap, targetWidth, targetHeight);
                }
            }
            encoder.finish();
        } catch (Exception e) {
            throw new IOException("Failed to encode timelapse", e);
        }

        return outputFile;
    }

    private void encodeFrame(AndroidSequenceEncoder encoder, Bitmap source, int targetWidth, int targetHeight) throws IOException {
        Bitmap frame = source;
        if (source.getWidth() != targetWidth || source.getHeight() != targetHeight) {
            frame = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        }

        encoder.encodeImage(frame);

        if (frame != source && !frame.isRecycled()) {
            frame.recycle();
        }
        if (!source.isRecycled()) {
            source.recycle();
        }
    }

    private BitmapFactory.Options readBounds(ContentResolver contentResolver, Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream inputStream = contentResolver.openInputStream(uri)) {
            if (inputStream == null) {
                return null;
            }
            BitmapFactory.decodeStream(inputStream, null, options);
            return options;
        }
    }

    private Bitmap decodeBitmap(ContentResolver contentResolver, Uri uri, int reqWidth, int reqHeight) throws IOException {
        BitmapFactory.Options bounds = readBounds(contentResolver, uri);
        if (bounds == null) {
            return null;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight);

        try (InputStream inputStream = contentResolver.openInputStream(uri)) {
            if (inputStream == null) {
                return null;
            }
            return BitmapFactory.decodeStream(inputStream, null, decodeOptions);
        }
    }

    private int[] resolveTargetSize(int sourceWidth, int sourceHeight) {
        int longSide = Math.max(sourceWidth, sourceHeight);
        if (longSide <= MAX_VIDEO_SIDE) {
            return new int[]{sourceWidth, sourceHeight};
        }

        float scale = (float) MAX_VIDEO_SIDE / longSide;
        int width = Math.max(2, Math.round(sourceWidth * scale));
        int height = Math.max(2, Math.round(sourceHeight * scale));
        return new int[]{width, height};
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
        }

        return Math.max(inSampleSize, 1);
    }

    private int toEven(int value) {
        return value % 2 == 0 ? value : value - 1;
    }
}


