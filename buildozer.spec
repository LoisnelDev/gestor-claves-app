[app]
title           = Gestor de Claves
package.name    = gestorclaves
package.domain  = org.loisnel

source.dir      = .
source.include_exts = py,png,jpg,kv,atlas

version         = 1.0
requirements    = python3,kivy,pycryptodome,android

# OrientaciÃ³n fija vertical
orientation     = portrait

# Pantalla completa
fullscreen       = 0

# -------------------------------------------------------
# PERMISOS â€” biometrÃ­a no requiere permiso especial en
# Android, pero USE_BIOMETRIC sÃ­ es necesario en algunos
# fabricantes (Samsung, Xiaomi).
# -------------------------------------------------------
android.permissions = USE_BIOMETRIC,USE_FINGERPRINT

# -------------------------------------------------------
# API LEVELS
# minApi  28 = Android 9.0 â€” mÃ­nimo para BiometricPrompt
# target  34 = Android 14  â€” requerido por Google Play 2024
# -------------------------------------------------------
android.minapi  = 28
android.api     = 34
android.ndk     = 25b

# -------------------------------------------------------
# DEPENDENCIAS GRADLE â€” AndroidX Biometric es OBLIGATORIA
# -------------------------------------------------------
android.gradle_dependencies = androidx.biometric:biometric:1.1.0

# Habilitar AndroidX (obligatorio con biometric:1.1.0)
android.enable_androidx = True

# -------------------------------------------------------
# ARQUITECTURAS â€” cubre el 99% de dispositivos modernos
# -------------------------------------------------------
android.archs = arm64-v8a, armeabi-v7a

# -------------------------------------------------------
# BUILDOZER / P4A
# -------------------------------------------------------
#p4a.branch = develop
p4a.fork = kivy
p4a.branch = master
[buildozer]
log_level = 2
warn_on_root = 1 