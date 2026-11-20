# kotlinx.serialization: keep generated serializers for the app and core models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.aliasadi.tamdid.** {
    *** Companion;
}
-keepclasseswithmembers class com.aliasadi.tamdid.** {
    kotlinx.serialization.KSerializer serializer(...);
}
