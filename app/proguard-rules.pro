# Aliyun OSS SDK
-keep class com.alibaba.sdk.android.** { *; }
-keep class com.aliyun.** { *; }
-dontwarn com.alibaba.sdk.android.**
-dontwarn com.aliyun.**

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class io.github.jinlongliao.ossbrowser.**$$serializer { *; }
-keepclassmembers class io.github.jinlongliao.ossbrowser.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.jinlongliao.ossbrowser.** {
    kotlinx.serialization.KSerializer serializer(...);
}
