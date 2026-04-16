# AIDL
-keep class com.example.ota.aidl.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.ota.model.** { *; }
-keep class com.example.ota.aidl.UpdateInfo { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Parcelable CREATOR
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
