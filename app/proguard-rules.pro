# SEHATI — aturan R8 untuk build staging/release

# SQLCipher (JNI): kelas dipanggil dari kode native
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# kotlinx.serialization (entitas @Serializable dikirim sebagai payload sinkronisasi)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class id.sehati.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class id.sehati.app.**$$serializer { *; }
-keepclassmembers class id.sehati.app.** { *** Companion; }

# Retrofit / OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Health Connect
-keep class androidx.health.connect.client.** { *; }

# Jangan menulis log yang memuat data sensitif pada rilis
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
