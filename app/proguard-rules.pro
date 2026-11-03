# Strip verbose/debug/info logging from release builds. Warnings and errors stay in the local
# logcat only; nothing is ever sent off the device.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
