package dev.umbra.qr.camera;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.annotation.WorkerThread;
import dev.umbra.qr.core.QrDecoder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads a QR code from an image picked by the user. Blocking: call from a single background
 * thread.
 */
public final class ImageFileDecoder {

    /** Large photos are downsampled to this size, which keeps QR modules readable. */
    static final int MAX_DIMENSION = 2048;

    /** What happened when reading the picked image. */
    public sealed interface Outcome {
        record Found(String text) implements Outcome {}

        record NotFound() implements Outcome {}

        record Failed(Exception error) implements Outcome {}
    }

    private final ContentResolver resolver;
    private final QrDecoder decoder = new QrDecoder();

    public ImageFileDecoder(ContentResolver resolver) {
        this.resolver = resolver;
    }

    @WorkerThread
    public Outcome decode(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = open(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return new Outcome.Failed(new IOException("Unsupported image format"));
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_DIMENSION);
            Bitmap bitmap;
            try (InputStream in = open(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) {
                return new Outcome.Failed(new IOException("Image could not be decoded"));
            }

            try {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                return decoder.decodeArgb(pixels, width, height)
                        .<Outcome>map(Outcome.Found::new)
                        .orElseGet(Outcome.NotFound::new);
            } finally {
                bitmap.recycle();
            }
        } catch (IOException | SecurityException e) {
            return new Outcome.Failed(e);
        }
    }

    private InputStream open(Uri uri) throws FileNotFoundException {
        InputStream in = resolver.openInputStream(uri);
        if (in == null) {
            throw new FileNotFoundException("Content provider returned no stream");
        }
        return in;
    }

    /** Smallest power-of-two divisor that brings the longest side within {@code maxDimension}. */
    static int sampleSizeFor(int width, int height, int maxDimension) {
        int sampleSize = 1;
        while (Math.max(width, height) / sampleSize > maxDimension) {
            sampleSize *= 2;
        }
        return sampleSize;
    }
}
