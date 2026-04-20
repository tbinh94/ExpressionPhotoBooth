package com.example.expressionphotobooth.domain.usecase;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.expressionphotobooth.data.graphics.BitmapEditRenderer;
import com.example.expressionphotobooth.domain.model.EditState;

import java.io.IOException;
import java.io.InputStream;

import android.graphics.BitmapFactory;

// UseCase gom nghiep vu render anh edited de UI goi 1 diem duy nhat.
public class RenderEditedBitmapUseCase {
    private final BitmapEditRenderer renderer;
    private PortraitProcessor portraitProcessor;

    public RenderEditedBitmapUseCase(BitmapEditRenderer renderer) {
        this.renderer = renderer;
    }

    public void setPortraitProcessor(PortraitProcessor portraitProcessor) {
        this.portraitProcessor = portraitProcessor;
    }

    public Bitmap execute(Context context, Bitmap source, EditState state, boolean skipBackground) {
        Bitmap processedSource = source;
        Bitmap portraitResult = null;

        if (!skipBackground && portraitProcessor != null && state.getBackgroundStyle() != EditState.BackgroundStyle.NONE) {
            try {
                Bitmap customBg = null;
                EditState.BackgroundStyle style = state.getBackgroundStyle();
                
                if (style == EditState.BackgroundStyle.CUSTOM && state.getCustomBackgroundBase64() != null) {
                    byte[] bytes = android.util.Base64.decode(state.getCustomBackgroundBase64(), android.util.Base64.DEFAULT);
                    customBg = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                } else if (style != EditState.BackgroundStyle.BLUR && style != EditState.BackgroundStyle.NONE) {
                    customBg = loadAssetBg(context, style);
                }

                portraitResult = portraitProcessor.processSync(source, customBg);
                processedSource = portraitResult;

                if (customBg != null) customBg.recycle();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Bitmap finalBitmap = renderer.render(context, processedSource, state);
        
        // Clean up temporary portrait bitmap if created
        if (portraitResult != null && portraitResult != source) {
            // We can't recycle here because renderer.render might have used it as the result?
            // Actually BitmapEditRenderer.render creates a NEW bitmap (target).
            // So it's safe to recycle processedSource IF it was created by PortraitProcessor.
            portraitResult.recycle();
        }

        return finalBitmap;
    }

    private Bitmap loadAssetBg(Context context, EditState.BackgroundStyle style) {
        String name = "";
        switch (style) {
            case STUDIO: name = "bg_studio.png"; break;
            case BEACH:  name = "bg_beach.png"; break;
            case SPACE:  name = "bg_space.png"; break;
            case VINTAGE: name = "bg_vintage.png"; break;
            default: return null;
        }
        try (InputStream is = context.getAssets().open("backgrounds/" + name)) {
            return BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            return null;
        }
    }
}

