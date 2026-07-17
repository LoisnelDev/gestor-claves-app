# GestorClaves — Data Safety (Google Play Console)

## ¿Tu app recopila datos de los usuarios?
**NO.** GestorClaves no recopila, transmite ni comparte ningún dato del usuario.

## ¿Los datos se comparten con terceros?
**NO.** Ningún dato sale del dispositivo del usuario.

## ¿Qué datos almacena la app?
GestorClaves almacena exclusivamente datos en el dispositivo local:

| Tipo de dato | Dónde | Cifrado | Propósito |
|---|---|---|---|
| Credenciales (contraseñas) | EncryptedSharedPreferences | AES-256-GCM | Almacenamiento local seguro |
| Contraseña maestra (hash) | EncryptedSharedPreferences | PBKDF2WithHmacSHA256 | Verificación de identidad |
| Backup cifrado | Google Drive (Auto Backup) | AES-256-GCM | Recuperación entre dispositivos |

## ¿Los datos están cifrados en tránsito?
**N/A.** Los datos nunca salen del dispositivo en texto plano.

## ¿Los datos están cifrados en reposo?
**SÍ.** Todos los datos se cifran con AES-256-GCM usando Android Keystore
(TEE/StrongBox). La clave de cifrado nunca sale del hardware seguro del dispositivo.

## ¿El usuario puede solicitar eliminación de datos?
**SÍ.** El usuario puede eliminar todos los datos desinstalando la aplicación
o usando la opción "Eliminar todos los datos" en la configuración de la app.

## ¿Qué permisos usa la app?
| Permiso | Propósito |
|---|---|
| USE_BIOMETRIC | Autenticación biométrica (huella, cara) |
| USE_FINGERPRINT | Compatibilidad con API anteriores |

## Estándares de seguridad implementados
- OWASP Mobile Application Security (MSTG) 2023
- NIST SP 800-63B (Autenticación)
- RFC 5869 (HKDF para derivación de claves)
- AES-256-GCM (NIST SP 800-38D)
- PBKDF2WithHmacSHA256 — 200.000 iteraciones

## Contacto para preguntas de privacidad
Developer: LoisnelDev
GitHub: https://github.com/LoisnelDev/gestor-claves-app
