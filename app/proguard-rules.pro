# Keep libxposed API 102 entry point (declared in META-INF/xposed/java_init.list)
# and the entire hook package intact for cross-process injection and reflection.
-keep class com.wild0408.headsetpopup.hook.** { *; }
-dontwarn io.github.libxposed.api.**

# Keep Android manifest components accessed by system / com.xiaomi.bluetooth.
-keep class com.wild0408.headsetpopup.MainActivity { *; }
-keep class com.wild0408.headsetpopup.config.PopupConfigProvider { *; }

# Keep Compose / Kotlin runtime metadata required for stability.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
