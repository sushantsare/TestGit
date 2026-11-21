# SQLCipher uses JNI callbacks into these classes
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
# kotlinx.serialization (backup format)
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.sushant.bahikhata.** { *** Companion; }
-keepclasseswithmembers class com.sushant.bahikhata.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.sushant.bahikhata.**$$serializer { *; }
-dontwarn com.google.errorprone.annotations.**

# Release builds carry no debug/info logging (routes, SMS stats). Warnings and errors stay.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
