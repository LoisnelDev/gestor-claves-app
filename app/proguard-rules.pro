# ═══════════════════════════════════════════════════════════════════
# FCP — ProGuard Rules
# TAD-B v2.1.1 — Fase Delta: Hardening final
# ═══════════════════════════════════════════════════════════════════

# ── Reglas generales de Kotlin ─────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Room — mantener entidades y DAOs ──────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * implements androidx.room.RoomDatabase { *; }
-keepclassmembers @androidx.room.Entity class * { *; }

# ── FCP — mantener todas las clases del proyecto ──────────────────
-keep class com.loisnel.gestorclaves.** { *; }
-keepclassmembers class com.loisnel.gestorclaves.** { *; }

# ── Seguridad — clases de criptografía ────────────────────────────
-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class android.security.keystore.** { *; }
-dontwarn java.security.**
-dontwarn javax.crypto.**

# ── Google Play Billing ───────────────────────────────────────────
-keep class com.android.billingclient.** { *; }
-keepclassmembers class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# ── ZXing — QR code library ───────────────────────────────────────
-keep class com.google.zxing.** { *; }
-keepclassmembers class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ── Coroutines ────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Compose ───────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── AndroidX general ──────────────────────────────────────────────
-keep class androidx.** { *; }
-dontwarn androidx.**

# ── Mantener nombres para stack traces en producción ──────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Eliminar logs de debug en release ─────────────────────────────
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
