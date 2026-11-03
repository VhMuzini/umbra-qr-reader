package dev.umbra.qr.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QrDecoderTest {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private final QrDecoder decoder = new QrDecoder();

    @Test
    void decodesPlainQrCode() throws WriterException {
        Image image = render("https://example.org/umbra");

        assertEquals(Optional.of("https://example.org/umbra"), decode(image));
    }

    @Test
    void decodesUnicodePayload() throws WriterException {
        Image image = render("Olá, sombra — ação");

        assertEquals(Optional.of("Olá, sombra — ação"), decode(image));
    }

    @Test
    void decodesInvertedQrCode() throws WriterException {
        Image image = render("inverted").inverted();

        assertEquals(Optional.of("inverted"), decode(image));
    }

    @Test
    void decodesRotatedQrCode() throws WriterException {
        Image image = render("rotated").rotated90();

        assertEquals(Optional.of("rotated"), decode(image));
    }

    @Test
    void returnsEmptyWhenNoCodePresent() {
        int[] pixels = new int[200 * 200];
        java.util.Arrays.fill(pixels, WHITE);

        assertTrue(decoder.decodeArgb(pixels, 200, 200).isEmpty());
    }

    @Test
    void isReusableAcrossCalls() throws WriterException {
        assertEquals(Optional.of("first"), decode(render("first")));
        assertTrue(decoder.decodeArgb(new int[100 * 100], 100, 100).isEmpty());
        assertEquals(Optional.of("second"), decode(render("second")));
    }

    private Optional<String> decode(Image image) {
        return decoder.decodeArgb(image.pixels(), image.width(), image.height());
    }

    private static Image render(String text) throws WriterException {
        BitMatrix matrix =
                new QRCodeWriter()
                        .encode(
                                text,
                                BarcodeFormat.QR_CODE,
                                300,
                                300,
                                Map.of(EncodeHintType.CHARACTER_SET, "UTF-8"));
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = matrix.get(x, y) ? BLACK : WHITE;
            }
        }
        return new Image(pixels, width, height);
    }

    private record Image(int[] pixels, int width, int height) {

        Image inverted() {
            int[] out = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                out[i] = pixels[i] == BLACK ? WHITE : BLACK;
            }
            return new Image(out, width, height);
        }

        Image rotated90() {
            int[] out = new int[pixels.length];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    out[x * height + (height - 1 - y)] = pixels[y * width + x];
                }
            }
            return new Image(out, height, width);
        }
    }
}
