# Keep TDLib Java bindings (accessed via JNI reflection)
-keep class org.drinkless.tdlib.** { *; }
-dontwarn org.drinkless.tdlib.**

# Keep BuildConfig fields consumed by reflection-free code paths
-keep class com.supergram.app.BuildConfig { *; }
