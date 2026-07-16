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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DirectorScreen — Panel de KPIs ejecutivos para el Director
 *
 * El Director tiene SOLO visibilidad de datos agregados.
 * NO tiene acceso a credenciales individuales ni puede emitir misiones.
 * Esto es el RBAC en la capa de UI (complementa el enforcement en :core).
 *
 * TAD-B v2.1.1 — Sección 4
 */
@Composable
fun DirectorScreen(
    totalMissions: Int,
    completedMissions: Int,
    failedMissions: Int,
    activeTechnicians: Int,
    complianceRate: Float, // 0.0 - 1.0
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF4A1A7A))
                .padding(16.dp)
        ) {
            Text(
                text       = "FCP — Panel Ejecutivo",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // KPI Cards
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KPICard(
                modifier = Modifier.weight(1f),
                label    = "MISIONES\nTOTALES",
                value    = totalMissions.toString(),
                color    = Color(0xFF1E5F9C)
            )
            KPICard(
                modifier = Modifier.weight(1f),
                label    = "TÉCNICOS\nACTIVOS",
                value    = activeTechnicians.toString(),
                color    = Color(0xFF0E6B6B)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KPICard(
                modifier = Modifier.weight(1f),
                label    = "COMPLETADAS",
                value    = completedMissions.toString(),
                color    = Color(0xFF1A5C2A)
            )
            KPICard(
                modifier = Modifier.weight(1f),
                label    = "FALLIDAS",
                value    = failedMissions.toString(),
                color    = Color(0xFF8B0000)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Compliance rate
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Column(
                modifier          = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text     = "TASA DE CUMPLIMIENTO",
                    color    = Color(0xFF888888),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text       = "${(complianceRate * 100).toInt()}%",
                    color      = if (complianceRate >= 0.9f) Color(0xFF4CAF50)
                                 else if (complianceRate >= 0.7f) Color(0xFFFF9800)
                                 else Color(0xFFCC2222),
                    fontSize   = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun KPICard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, color = Color(0xFFCCCCCC), fontSize = 11.sp)
        }
    }
}
