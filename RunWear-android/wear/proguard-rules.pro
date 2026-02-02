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

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Keep Retrofit API interfaces
-keep interface com.runwear.shared.data.api.** { *; }
-keep class com.runwear.shared.data.api.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Dagger/Hilt - Keep custom @Qualifier annotations
-keep @interface com.runwear.shared.di.WeatherRetrofit
-keep @interface com.runwear.shared.di.GeocodingRetrofit
-keep @interface com.runwear.shared.di.NominatimRetrofit

# Keep entire DI module unobfuscated
-keep class com.runwear.shared.di.** { *; }

# Keep Dagger-generated component implementations
-keep class **_Factory { *; }
-keep class **_HiltModules* { *; }
-keep class **_ComponentTreeDeps { *; }
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }
-keep class dagger.hilt.** { *; }
-keep class dagger.internal.** { *; }
-keep class hilt_aggregated_deps.** { *; }

# Keep all Dagger generated code
-keep class * extends dagger.internal.Factory { *; }
-keep class * implements dagger.MembersInjector { *; }
