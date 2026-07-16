package com.loisnel.gestorclaves.core.domain

/**
 * UnauthorizedRoleException — Enforcement de RBAC en capa :core
 *
 * Lanzada por los Repositories cuando un rol intenta ejecutar
 * una acción para la que no tiene permiso.
 *
 * Esta excepción es el mecanismo que garantiza que el RBAC NO es
 * solo "seguridad por UI" (ocultar botones). El contrato de datos
 * lo enforcea independientemente de lo que muestre la capa UI.
 *
 * TAD-B v2.1.1 — Sección 14
 */
class UnauthorizedRoleException(
    val required: UserRole,
    val actual: UserRole,
    val action: String
) : SecurityException(
    "RBAC: Role '$actual' cannot perform '$action'. Required: '$required'"
)
