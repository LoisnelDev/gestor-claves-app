pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GestorClaves"

// ── GestorClaves (B2C — com.loisnel.gestorclaves) ─────────────────
include(":app")          // GDC entry point — se renombrará a :app-gdc en Fase 4

// ── ORION (B2B — com.loisnel.orion) ───────────────────────────────
include(":app-orion")    // ORION entry point — nuevo

// ── Módulos compartidos (:core = fuente única de verdad) ──────────
include(":core")         // Criptografía pura: Ed25519, HKDF, AES-GCM, Room ORION
include(":enterprise")   // UI industrial ORION: QR/NFC, Screens, FDE
include(":billing")      // Play Billing: ProStatus independiente por app
include(":sync")         // Store-and-Forward P2P: exclusivo de ORION
