# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.aura.ai.**$$serializer { *; }
-keepclassmembers class com.aura.ai.** { *** Companion; }
-keepclasseswithmembers class com.aura.ai.** { kotlinx.serialization.KSerializer serializer(...); }

# Tink (used by androidx.security) references annotations that are not shipped
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
