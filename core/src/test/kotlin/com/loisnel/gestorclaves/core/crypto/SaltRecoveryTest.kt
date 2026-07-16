package com.loisnel.gestorclaves.core.crypto

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException
import java.security.SecureRandom

/**
 * Suite de tests para SaltRecovery — UT-009 a UT-012
 *
 * Verifica el mecanismo de exportación/importación del UserSalt
 * (key-wrapping con PBKDF2 + AES-GCM).
 */
class SaltRecoveryTest {

    private val testPassword  = "correct-master-password-2026"
    private val testUserSalt  = ByteArray(32).also { SecureRandom().nextBytes(it) }

    // ═══════════════════════════════════════════════════════════
    // UT-009: SaltRecovery — Round-trip exitoso
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut009_salt_backup_export_import_roundtrip() {
        val backup   = SaltRecoveryManager.exportSaltBackup(testUserSalt, testPassword)
        val restored = SaltRecoveryManager.importSaltBackup(backup, testPassword)
        assertArrayEquals(
            "UT-009 FAILED: UserSalt restaurado debe ser idéntico al original",
            testUserSalt, restored
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-010: SaltRecovery — Password incorrecto lanza AEADBadTagException
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut010_salt_backup_wrong_password_throws_aead_exception() {
        val backup = SaltRecoveryManager.exportSaltBackup(testUserSalt, testPassword)
        try {
            SaltRecoveryManager.importSaltBackup(backup, "wrong-password-!!!")
            fail("UT-010 FAILED: Password incorrecto debe lanzar AEADBadTagException")
        } catch (e: AEADBadTagException) {
            // ✓ Comportamiento correcto
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UT-011: SaltRecovery — Backups diferentes por SecureRandom
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut011_salt_backup_two_exports_are_different() {
        val backup1 = SaltRecoveryManager.exportSaltBackup(testUserSalt, testPassword)
        val backup2 = SaltRecoveryManager.exportSaltBackup(testUserSalt, testPassword)
        assertFalse(
            "UT-011 FAILED: Dos backups del mismo salt deben ser diferentes (wrapSalt aleatorio)",
            backup1.contentEquals(backup2)
        )
    }

    // ═══════════════════════════════════════════════════════════
    // UT-012: SaltRecovery — Backup corrupto lanza excepción
    // ═══════════════════════════════════════════════════════════
    @Test
    fun ut012_salt_backup_corrupted_throws_exception() {
        val backup  = SaltRecoveryManager.exportSaltBackup(testUserSalt, testPassword)
        val corrupt = backup.copyOf()
        corrupt[corrupt.size - 1] = (corrupt[corrupt.size - 1].toInt() xor 0xFF).toByte()
        try {
            SaltRecoveryManager.importSaltBackup(corrupt, testPassword)
            fail("UT-012 FAILED: Backup corrupto debe lanzar excepción")
        } catch (e: Exception) {
            // ✓ AEADBadTagException o similar
        }
    }
}
