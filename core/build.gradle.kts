plugins {
    // No incluir version en submodulos — AGP ya está en el classpath raíz
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace  = "com.loisnel.gestorclaves.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        // SIN applicationId — es una library
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Habilitar BuildConfig para debug flags si se necesita
    buildFeatures {
        buildConfig = false
    }

    // Configuración de tests locales (JUnit4 puro — sin emulador)
    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // ── Room — estructura relacional ──────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Coroutines — IO no bloqueante ─────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Tests unitarios — JUnit4 puro, sin emulador ───────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // javax.crypto, java.security, Android Keystore:
    // incluidos en el Android SDK — sin dependencia adicional
}

// Room schema export — requerido cuando exportSchema = true
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
