package dev.umbra.qr.core;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.Map;
import java.util.Optional;

/**
 * Decodes QR codes from luminance data, fully offline.
 *
 * <p>Not thread-safe: the underlying ZXing reader keeps state, so use one instance per thread.
 */
public final class QrDecoder {

    private static final Map<DecodeHintType, Object> HINTS =
            Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE);

    private final QRCodeReader reader = new QRCodeReader();

    /**
     * Looks for a QR code in the image, also trying the colour-inverted image (light modules on
     * a dark background).
     *
     * @return the decoded text, or empty when no readable QR code was found
     */
    public Optional<String> decode(LuminanceSource source) {
        return tryDecode(source).or(() -> tryDecode(source.invert()));
    }

    /** Convenience overload for ARGB pixel buffers, such as Android bitmaps. */
    public Optional<String> decodeArgb(int[] pixels, int width, int height) {
        return decode(new RGBLuminanceSource(width, height, pixels));
    }

    private Optional<String> tryDecode(LuminanceSource source) {
        try {
            Result result = reader.decode(new BinaryBitmap(new HybridBinarizer(source)), HINTS);
            return Optional.ofNullable(result.getText());
        } catch (NotFoundException | ChecksumException | FormatException e) {
            // Expected for most camera frames: no (readable) code in view.
            return Optional.empty();
        } finally {
            reader.reset();
        }
    }
}
