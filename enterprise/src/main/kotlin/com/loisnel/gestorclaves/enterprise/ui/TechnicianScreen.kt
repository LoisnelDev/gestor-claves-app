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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TechnicianScreen — Pantalla del técnico de campo
 *
 * UI optimizada para uso en campo sin conexión:
 * - Botones grandes para uso con guantes
 * - Alto contraste para luz solar directa
 * - Textos grandes para lectura rápida
 *
 * RBAC: solo visible cuando userRole == TECHNICIAN
 * TAD-B v2.1.1 — Sección 4
 */
@Composable
fun TechnicianScreen(
    activeMissionId: String?,
    missionStatus: String?,
    decryptedAssets: List<Pair<String, String>>, // (label, plaintext)
    showFDEWarning: Boolean,
    onScanMission: () -> Unit,
    onGenerateCloseQR: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        if (showFDEWarning) FDEWarningBanner()

        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E6B6B))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text       = "FCP — Técnico de Campo",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 22.sp
                )
                Text(
                    text  = if (activeMissionId != null)
                        "Misión activa: ${activeMissionId.take(8)}..."
                    else
                        "Sin misión activa",
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botones de acción principales
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick  = onScanMission,
                modifier = Modifier.weight(1f).height(72.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E5F9C)
                )
            ) {
                Text(
                    text       = "ESCANEAR\nMISIÓN QR",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp
                )
            }

            Button(
                onClick  = onGenerateCloseQR,
                enabled  = activeMissionId != null && missionStatus == "ACTIVE",
                modifier = Modifier.weight(1f).height(72.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = Color(0xFF1A5C2A),
                    disabledContainerColor = Color(0xFF333333)
                )
            ) {
                Text(
                    text       = "GENERAR\nQR CIERRE",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Lista de credenciales descifradas
        if (decryptedAssets.isNotEmpty()) {
            Text(
                text     = "Credenciales de campo (${decryptedAssets.size})",
                color    = Color(0xFF888888),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier              = Modifier.fillMaxSize(),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                items(decryptedAssets) { (label, credential) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1A1A)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text       = label,
                                color      = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                fontSize   = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text     = credential,
                                color    = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier          = Modifier.fillMaxSize(),
                contentAlignment  = Alignment.Center
            ) {
                Text(
                    text     = "Escanea un QR de misión\npara cargar credenciales",
                    color    = Color(0xFF444444),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
