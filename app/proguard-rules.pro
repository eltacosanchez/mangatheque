# Room / Kotlin : les entités sont lues par réflexion minimale, garder les noms de champs utiles.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class fr.mangatheque.app.data.** { *; }
# OkHttp
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
