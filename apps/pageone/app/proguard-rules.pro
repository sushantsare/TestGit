# PDFBox-Android: resources (glyph lists, fonts) are loaded by name; optional JPEG2000 decoder isn't bundled
-keep class com.tom_roush.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.**
# kotlinx-serialization
-keepclassmembers @kotlinx.serialization.Serializable class com.sushant.pageone.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.sushant.pageone.**$$serializer { *; }
