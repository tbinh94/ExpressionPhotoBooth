package com.example.expressionphotobooth.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeometryMathTest {

    @Test
    public void centerCrop_forHole3x4_isStable() {
        GeometryMath.CropRect crop = GeometryMath.calculateCenterCrop(1200f, 900f, 3f, 4f);

        assertEquals(675f, crop.width(), 0.01f);
        assertEquals(900f, crop.height(), 0.01f);
        assertEquals(262.5f, crop.left, 0.01f);
        assertEquals(0f, crop.top, 0.01f);
    }

    @Test
    public void centerCrop_forHole16x9_isStable() {
        GeometryMath.CropRect crop = GeometryMath.calculateCenterCrop(900f, 1200f, 16f, 9f);

        assertEquals(900f, crop.width(), 0.01f);
        assertEquals(506.25f, crop.height(), 0.01f);
        assertEquals(0f, crop.left, 0.01f);
        assertEquals(346.875f, crop.top, 0.01f);
    }

    @Test
    public void normalized_roundTrip_keepsPoint() {
        float left = 100f;
        float width = 300f;
        float absolute = 280f;

        float normalized = GeometryMath.absoluteToNormalized(left, width, absolute);
        float mapped = GeometryMath.normalizedToAbsolute(left, width, normalized);

        assertEquals(absolute, mapped, 0.001f);
    }

    @Test
    public void exifRotation_simulatedBySwappingDimensions_stillValid() {
        GeometryMath.CropRect portrait = GeometryMath.calculateCenterCrop(1080f, 1920f, 16f, 9f);
        GeometryMath.CropRect landscape = GeometryMath.calculateCenterCrop(1920f, 1080f, 16f, 9f);

        assertTrue(portrait.width() > 0f && portrait.height() > 0f);
        assertTrue(landscape.width() > 0f && landscape.height() > 0f);
        assertEquals(16f / 9f, portrait.width() / portrait.height(), 0.001f);
        assertEquals(16f / 9f, landscape.width() / landscape.height(), 0.001f);
    }

    @Test
    public void placementError_isWithinTwoPercentOfHoleSize() {
        GeometryMath.CropRect srcCrop = GeometryMath.calculateCenterCrop(1200f, 900f, 238f, 134f);
        float normalizedX = 0.84f;
        float normalizedY = 0.18f;

        float sourceX = GeometryMath.normalizedToAbsolute(srcCrop.left, srcCrop.width(), normalizedX);
        float sourceY = GeometryMath.normalizedToAbsolute(srcCrop.top, srcCrop.height(), normalizedY);

        float mappedNormalizedX = GeometryMath.absoluteToNormalized(srcCrop.left, srcCrop.width(), sourceX);
        float mappedNormalizedY = GeometryMath.absoluteToNormalized(srcCrop.top, srcCrop.height(), sourceY);

        float holeWidth = 238f;
        float holeHeight = 134f;
        float errorX = Math.abs(mappedNormalizedX - normalizedX) * holeWidth;
        float errorY = Math.abs(mappedNormalizedY - normalizedY) * holeHeight;

        assertTrue(errorX <= holeWidth * 0.02f);
        assertTrue(errorY <= holeHeight * 0.02f);
    }
}


