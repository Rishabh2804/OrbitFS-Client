-keep class org.orbitfs.** { *; }
-dontwarn org.orbitfs.**

# Jackson record serialization
-keepattributes *Annotation*
-keep,allowobfuscation,allowoptimization,allowshrinking class com.fasterxml.jackson.**
-dontwarn com.fasterxml.jackson.databind.**
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,InnerClasses

