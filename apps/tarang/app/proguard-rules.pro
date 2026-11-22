# jaudiotagger: desktop-only classes it references but never uses on Android
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.beans.**
-dontwarn javax.sound.**
-keep class org.jaudiotagger.** { *; }
# kotlinx-serialization
-keepclassmembers @kotlinx.serialization.Serializable class com.sushant.tarang.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.sushant.tarang.**$$serializer { *; }
# Material colour utilities (used by reflection-free code, kept small by R8 anyway)
-dontwarn com.google.errorprone.annotations.**
