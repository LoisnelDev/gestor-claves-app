plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace  = "com.loisnel.gestorclaves.billing"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions { jvmTarget = "11" }

    buildFeatures { buildConfig = false }

    testOptions {
        unitTests { isReturnDefaultValues = true }
    }
}

dependencies {
    // Google Play Billing — SOLO en este módulo
    implementation(libs.billing.ktx)

    // AndroidX mínimo para Context
    implementation(libs.androidx.core.ktx)

    // Coroutines para StateFlow
    implementation(libs.kotlinx.coroutines.android)

    // Tests JVM puro — SIN Room, SIN DAOs
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // NOTA: NO hay dependencia de :core aquí
    // BillingManager NO accede a ningún DAO — Never-block policy (TAD-B v2.1.1 Mejora 2)
}
