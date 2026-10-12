-keepattributes *Annotation*, InnerClasses, Signature

# kotlinx.serialization
-keepclassmembers class app.lumen.chat.data.api.** {
    *** Companion;
}
-keepclasseswithmembers class app.lumen.chat.data.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp optional platform classes
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# EncryptedSharedPreferences (Tink + protobuf lite)
-keep class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
