# Add project specific ProGuard rules here.

# SLF4J
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.runwear.**$$serializer { *; }
-keepclassmembers class com.runwear.** {
    *** Companion;
}

# Keep data classes
-keep class com.runwear.shared.data.model.** { *; }
-keep class com.runwear.shared.domain.model.** { *; }
