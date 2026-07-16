// build.gradle.kts — Proyecto raíz
// Declara plugins disponibles para todos los submodulos
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library)     apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.ksp)                 apply false
}
