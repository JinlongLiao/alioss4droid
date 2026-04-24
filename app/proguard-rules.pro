# Aliyun OSS SDK
-keep class com.alibaba.sdk.android.** { *; }
-keep class com.aliyun.** { *; }
-dontwarn com.alibaba.sdk.android.**
-dontwarn com.aliyun.**

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.ossbrowser.app.**$$serializer { *; }
-keepclassmembers class com.ossbrowser.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.ossbrowser.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
