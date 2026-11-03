package dev.umbra.qr.util;

import android.util.Log;
import androidx.annotation.Nullable;
import dev.umbra.qr.BuildConfig;

/**
 * Local-only logging. Output goes to logcat on this device and nowhere else.
 *
 * <p>Privacy rule: never pass scanned content, URLs, or image data to these methods; log only
 * what happened, never what was read.
 */
public final class AppLog {

    private static final String TAG = "Umbra";

    private AppLog() {}

    public static void d(String message) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message);
        }
    }

    public static void w(String message, @Nullable Throwable error) {
        Log.w(TAG, message, error);
    }

    public static void e(String message, Throwable error) {
        Log.e(TAG, message, error);
    }
}
