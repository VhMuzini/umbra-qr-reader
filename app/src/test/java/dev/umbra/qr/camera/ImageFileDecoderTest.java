package dev.umbra.qr.camera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ImageFileDecoderTest {

    @Test
    public void smallImagesAreNotDownsampled() {
        assertEquals(1, ImageFileDecoder.sampleSizeFor(800, 600, 2048));
        assertEquals(1, ImageFileDecoder.sampleSizeFor(2048, 2048, 2048));
    }

    @Test
    public void largeImagesUseSmallestSufficientPowerOfTwo() {
        assertEquals(2, ImageFileDecoder.sampleSizeFor(4000, 3000, 2048));
        assertEquals(4, ImageFileDecoder.sampleSizeFor(3000, 8192, 2048));
        assertEquals(8, ImageFileDecoder.sampleSizeFor(12000, 9000, 2048));
    }
}
