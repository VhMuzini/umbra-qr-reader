package dev.umbra.qr.camera;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.zxing.PlanarYUVLuminanceSource;
import dev.umbra.qr.core.QrDecoder;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feeds camera frames to {@link QrDecoder}. Reports at most one result, then stays paused until
 * {@link #resume()} is called, so the same code is not reported over and over.
 *
 * <p>Must run on a single analysis thread; the decoder and frame buffer are not thread-safe.
 */
public final class QrAnalyzer implements ImageAnalysis.Analyzer {

    /** Receives decoded text on the analysis thread. */
    public interface Listener {
        void onDecoded(String text);
    }

    private final QrDecoder decoder = new QrDecoder();
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final Listener listener;
    private byte[] luminance = new byte[0];

    public QrAnalyzer(Listener listener) {
        this.listener = listener;
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (paused.get()) {
                return;
            }
            // Plane 0 of YUV_420_888 is the luminance (Y) channel: all a QR decoder needs.
            ImageProxy.PlaneProxy plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int rowStride = plane.getRowStride();
            int width = image.getWidth();
            int height = image.getHeight();

            int required = rowStride * height;
            if (luminance.length < required) {
                luminance = new byte[required];
            }
            buffer.rewind();
            // The last row may be shorter than rowStride; the rest of the array is never read.
            buffer.get(luminance, 0, Math.min(buffer.remaining(), required));

            PlanarYUVLuminanceSource source =
                    new PlanarYUVLuminanceSource(
                            luminance, rowStride, height, 0, 0, width, height, false);
            decoder.decode(source)
                    .ifPresent(
                            text -> {
                                if (paused.compareAndSet(false, true)) {
                                    listener.onDecoded(text);
                                }
                            });
        } finally {
            image.close();
        }
    }
}
