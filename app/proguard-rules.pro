# Keep Xposed entry point intact for LSPosed
-keep class com.notremock.app.xposed.NotreMockXposedEntry { *; }
-keep class com.notremock.app.xposed.XposedStatus { *; }

# Keep Shizuku & Parcelable classes
-keep class rikka.shizuku.** { *; }
-dontwarn de.robv.android.xposed.**
