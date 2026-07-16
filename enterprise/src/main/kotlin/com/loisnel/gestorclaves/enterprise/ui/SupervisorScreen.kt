package com.loisnel.gestorclaves.enterprise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.loisnel.gestorclaves.core.data.entities.MissionLog
import com.loisnel.gestorclaves.core.data.entities.Tenant
import com.loisnel.gestorclaves.core.domain.FCPConstants

/**
 * SupervisorScreen — Pantalla principal del supervisor de cuadrilla
 *
 * Diseño de alto contraste para uso industrial bajo luz solar directa.
 * Botones grandes para uso con guantes industriales.
 *
 * RBAC: solo visible cuando userRole == SUPERVISOR
 * FDE Warning: visible si el dispositivo no tiene FDE activo
 *
 * TAD-B v2.1.1 — Sección 4
 */
@Composable
fun SupervisorScreen(
    tenants: List<Tenant>,
    activeTenantId: String?,
    missions: List<MissionLog>,
    showFDEWarning: Boolean,
    isProActive: Boolean,
    onTenantSelected: (Tenant) -> Unit,
    onEmitMission: () -> Unit,
    onScanClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)) // Fondo negro absoluto — máximo contraste
    ) {
        // ── FDE Warning (si aplica) ───────────────────────────────
        if (showFDEWarning) {
            FDEWarningBanner()
        }

        // ── Header ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D2B4E))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text       = "FCP — Supervisor",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 22.sp
                )
                Text(
                    text     = if (isProActive) "● Licencia Pro activa" else "○ Modo básico",
                    color    = if (isProActive) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    fontSize = 13.sp
                )
            }
        }

        // ── Selector de tenant ────────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))
        TenantContextSelector(
            tenants          = tenants,
            activeTenantId   = activeTenantId,
            onTenantSelected = onTenantSelected
        )

        // ── Botones principales ───────────────────────────────────
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Emitir misión — solo si es Pro y hay tenant activo
            Button(
                onClick  = onEmitMission,
                enabled  = isProActive && activeTenantId != null,
                modifier = Modifier.weight(1f).height(64.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Color(0xFF1E5F9C),
                    disabledContainerColor = Color(0xFF333333)
                )
            ) {
                Text(
                    text       = "EMITIR\nMISIÓN",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp
                )
            }

            // Escanear QR de cierre
            Button(
                onClick  = onScanClose,
                modifier = Modifier.weight(1f).height(64.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A5C2A)
                )
            ) {
                Text(
                    text       = "ESCANEAR\nCIERRE",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp
                )
            }
        }

        // ── Lista de misiones ─────────────────────────────────────
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text     = "Misiones activas",
            color    = Color(0xFF888888),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(missions) { mission ->
                MissionCard(mission = mission)
            }
        }
    }
}

@Composable
private fun MissionCard(mission: MissionLog, modifier: Modifier = Modifier) {
    val statusColor = when (mission.status) {
        FCPConstants.STATUS_ISSUED      -> Color(0xFF1E5F9C)
        FCPConstants.STATUS_ACTIVE      -> Color(0xFFCC8800)
        FCPConstants.STATUS_COMPLETED   -> Color(0xFF1A5C2A)
        FCPConstants.STATUS_FAILED      -> Color(0xFF8B0000)
        FCPConstants.STATUS_EXPIRED     -> Color(0xFF444444)
        FCPConstants.STATUS_INTERRUPTED -> Color(0xFF7A4A00)
        else                            -> Color(0xFF333333)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(statusColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text      = mission.status,
                    color     = Color.White,
                    fontSize  = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text      = mission.id.take(8) + "...",
                    color     = Color(0xFF888888),
                    fontSize  = 11.sp
                )
                mission.device_model?.let {
                    Text(text = it, color = Color(0xFF666666), fontSize = 10.sp)
                }
            }
        }
    }
}
