package com.loisnel.gestorclaves.enterprise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * FDEWarningBanner — Advertencia de cifrado de disco no activo
 *
 * Se muestra en la parte superior de todas las pantallas Enterprise
 * si el dispositivo no tiene FDE (Full Disk Encryption) activo.
 *
 * En entornos industriales de gama baja es posible encontrar
 * dispositivos sin FDE. Los BLOBs de assets están cifrados
 * individualmente con AES-256-GCM, pero los metadatos del
 * Mission_Log podrían estar expuestos.
 *
 * TAD-B v2.1.1 — Sección 8
 */
@Composable
fun FDEWarningBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFCC2222))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text       = "⚠",
            color      = Color.White,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text       = "ADVERTENCIA: Este dispositivo no tiene cifrado de disco activo. " +
                         "Los datos de auditoría pueden estar expuestos.",
            color      = Color.White,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Medium,
            style      = MaterialTheme.typography.bodySmall
        )
    }
}
