package com.example.expressionphotobooth.domain.usecase;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import com.example.expressionphotobooth.data.video.TimelapseVideoEncoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

// UseCase tao va luu video timelapse vao Gallery.
public class CreateTimelapseVideoUseCase {
    private final TimelapseVideoEncoder timelapseVideoEncoder;

    public CreateTimelapseVideoUseCase(TimelapseVideoEncoder timelapseVideoEncoder) {
        this.timelapseVideoEncoder = timelapseVideoEncoder;
    }

    public Uri execute(Context context, List<String> capturedUris, int fps) throws IOException {
        File tempVideo = new File(context.getCacheDir(), "timelapse_" + System.currentTimeMillis() + ".mp4");
        timelapseVideoEncoder.encode(context.getContentResolver(), capturedUris, tempVideo, fps);

        String displayName = "photobooth_timelapse_" + System.currentTimeMillis() + ".mp4";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Photobooth");

        Uri outputUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri == null) {
            throw new IOException("Cannot create destination video uri");
        }

        // Copy file tam vao MediaStore de video xuat hien trong Gallery.
        try (FileInputStream inputStream = new FileInputStream(tempVideo);
             OutputStream outputStream = context.getContentResolver().openOutputStream(outputUri)) {
            if (outputStream == null) {
                throw new IOException("Cannot open output stream");
            }

            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }

        return outputUri;
    }
}

