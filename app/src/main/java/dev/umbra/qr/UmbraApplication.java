package dev.umbra.qr;

import android.app.Application;
import android.os.StrictMode;

/** Application entry point. Enables strict, local-only diagnostics in debug builds. */
public final class UmbraApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                    new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
        }
    }
}
