plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace  = "com.loisnel.orion"
    compileSdk = 36

    defaultConfig {
        applicationId  = "com.loisnel.orion"  // applicationId DIFERENTE de GDC
        minSdk         = 28
        targetSdk      = 36
        versionCode    = 1
        versionName    = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled     = false
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }
}

dependencies {
    // ── Módulos ORION ─────────────────────────────────────────────
    implementation(project(":core"))       // Núcleo criptográfico compartido
    implementation(project(":enterprise")) // UI industrial ORION
    implementation(project(":billing"))    // Play Billing ORION
    implementation(project(":sync"))       // Store-and-Forward P2P

    // ── Compose ────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // ── AndroidX ───────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.biometric)

    // ── Tests ──────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
