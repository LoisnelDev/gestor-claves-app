# Changelog — Gestor de Claves

## [v0.2.0-beta] — 2026-06-03
### Cambios críticos
- Reemplazado `KeyguardManager.createConfirmDeviceCredentialIntent()` (deprecado Android 10+)
  por `androidx.biometric.BiometricPrompt` — API oficial vigente para Android 9 a 15+
- Implementado `_BiometricCallback` vía `PythonJavaClass` (jnius) — thread-safe
- Fallback automático a `KeyguardManager` para Android < 28

### Seguridad
- Clave AES dejó de ser hardcodeada
- Derivación de clave con PBKDF2-HMAC-SHA256 (200k iteraciones)

### Correcciones
- Toast con auto-dismiss (antes permanecía en pantalla indefinidamente)
- Validación de campo SITIO vacío al guardar
- Desvinculación correcta del listener `on_activity_result`

### Build
- `targetSdk` actualizado a 34 (requerido por Google Play desde agosto 2024)
- Dependencia `androidx.biometric:biometric:1.1.0` agregada al spec
- `android.enable_androidx = True` habilitado

---

## [v0.1.3-beta] — anterior
- Fix: Migración a KeyguardManager para evitar crash (ClassNotFoundException) en Android 14+

## [v0.1.0-alpha] — inicial
- Código base con biometría Android
