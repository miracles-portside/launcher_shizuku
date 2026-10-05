# Compose
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}
-dontwarn androidx.compose.**

# Keep data classes used via reflection / JSON
-keep class com.example.launcher.** { *; }

# Strip logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
