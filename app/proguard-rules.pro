# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ---- AU Notes R8 & ProGuard Optimization Rules ----

# Keep readable names so in-app crash reports stay understandable
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,JavascriptInterface

# OkHttp & TLS optional providers
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**

# Room entities, DAOs, Database and models
-keep class com.example.data.model.** { *; }
-keep class com.example.data.db.** { *; }
-keep class com.example.data.repository.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# Alarm & Reminder BroadcastReceiver (instantiated dynamically by the OS)
-keep class com.example.ui.util.AlarmReceiver { *; }
-keep class com.example.ui.util.AlarmScheduler { *; }

# WorkManager background workers
-keep class com.example.ui.screens.TempMailNotificationWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# In-App Crash Reporter & Audio System
-keep class com.example.ui.util.CrashReporter { *; }
-keep class com.example.ui.util.SirenAudioPlayer { *; }

# Biometric Authentication
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# Name Generator & Webview JavaScript Bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.example.ui.screens.AuWebBridge { *; }
-keepclassmembers class com.example.ui.screens.AuWebBridge {
    public *;
}

# Image loading (Coil) & Compose
-dontwarn coil.**
-dontwarn androidx.compose.**
