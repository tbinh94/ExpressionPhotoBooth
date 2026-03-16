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

    public File encode(ContentResolver contentResolver, List<String> imageUris, File outputFile, int fps) throws IOException {
        if (imageUris == null || imageUris.isEmpty()) {
            throw new IOException("No images to encode");
        }

        Bitmap first = decodeBitmap(contentResolver, Uri.parse(imageUris.get(0)));
        if (first == null) {
            throw new IOException("Cannot decode first image");
        }

        int targetWidth = toEven(first.getWidth());
        int targetHeight = toEven(first.getHeight());

        AndroidSequenceEncoder encoder = AndroidSequenceEncoder.createSequenceEncoder(outputFile, fps);

        try {
            encodeFrame(encoder, first, targetWidth, targetHeight);
            for (int i = 1; i < imageUris.size(); i++) {
                Bitmap bitmap = decodeBitmap(contentResolver, Uri.parse(imageUris.get(i)));
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

    private Bitmap decodeBitmap(ContentResolver contentResolver, Uri uri) throws IOException {
        try (InputStream inputStream = contentResolver.openInputStream(uri)) {
            if (inputStream == null) {
                return null;
            }
            return BitmapFactory.decodeStream(inputStream);
        }
    }

    private int toEven(int value) {
        return value % 2 == 0 ? value : value - 1;
    }
}


