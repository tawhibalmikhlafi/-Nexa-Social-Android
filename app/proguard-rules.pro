# Keep the model classes that Gson serialises to and from the on-disk JSON stores. Field names
# must stay intact because the persisted files and the Supabase payloads use the same names.
-keep class com.nexa.social.domain.model.** { *; }
-keep class com.nexa.social.data.local.** { *; }

# Gson uses reflection and generic signatures to build collection types.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# OkHttp/Okio ship their own consumer rules, but keep the warnings quiet for optional platforms.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep line numbers so release crash reports remain actionable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
