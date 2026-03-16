package com.example.expressionphotobooth.domain.usecase;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.expressionphotobooth.data.graphics.BitmapEditRenderer;
import com.example.expressionphotobooth.domain.model.EditState;

// UseCase gom nghiep vu render anh edited de UI goi 1 diem duy nhat.
public class RenderEditedBitmapUseCase {
    private final BitmapEditRenderer renderer;

    public RenderEditedBitmapUseCase(BitmapEditRenderer renderer) {
        this.renderer = renderer;
    }

    public Bitmap execute(Context context, Bitmap source, EditState state) {
        return renderer.render(context, source, state);
    }
}

