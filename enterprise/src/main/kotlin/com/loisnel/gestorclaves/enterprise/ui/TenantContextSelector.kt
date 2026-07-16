package com.loisnel.gestorclaves.enterprise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loisnel.gestorclaves.core.data.entities.Tenant

/**
 * TenantContextSelector — Selector de organización activa
 *
 * Permite al técnico cambiar entre organizaciones/contratistas.
 * El cambio de contexto activa el zeroing de la TenantKey anterior
 * en memoria (Arrays.fill) antes de derivar la nueva.
 *
 * TAD-B v2.1.1 — Sección 5
 */
@Composable
fun TenantContextSelector(
    tenants: List<Tenant>,
    activeTenantId: String?,
    onTenantSelected: (Tenant) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text     = "Organización activa",
            style    = MaterialTheme.typography.labelMedium,
            color    = Color(0xFF888888),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        tenants.forEach { tenant ->
            val isActive = tenant.id == activeTenantId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { onTenantSelected(tenant) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive)
                        Color(0xFF1E3A5F)
                    else
                        Color(0xFF2A2A2A)
                )
            ) {
                Row(
                    modifier          = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = if (isActive) "●" else "○",
                        color      = if (isActive) Color(0xFF4CAF50) else Color(0xFF888888),
                        fontSize   = 16.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text       = tenant.name,
                            color      = Color.White,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontSize   = 16.sp
                        )
                        if (isActive) {
                            Text(
                                text     = "Contexto activo",
                                color    = Color(0xFF4CAF50),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
