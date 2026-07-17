package com.loisnel.orion

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * OrionMainActivity — Entry point de ORION Platform
 *
 * applicationId: com.loisnel.orion (diferente de GDC: com.loisnel.gestorclaves)
 * Dos apps independientes en Play Store. Un :core compartido.
 *
 * Arquitectura Monorepo Multi-App:
 * - :core    → Fuente única de verdad criptográfica (compartida con GDC)
 * - :enterprise → UI industrial (exclusivo de ORION)
 * - :billing → Play Billing ORION (ProStatus independiente de GDC)
 * - :sync    → Store-and-Forward P2P (exclusivo de ORION)
 *
 * TAD-B v3.0 — ORION Platform — Fase de incubación
 */
class OrionMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE: evita capturas de pantalla con datos de misiones
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            OrionApp()
        }
    }
}

/**
 * OrionApp — Composable raíz de ORION
 *
 * TODO (Fase de construcción):
 * - Integrar SupervisorScreen desde :enterprise
 * - Integrar TechnicianScreen desde :enterprise
 * - Integrar DirectorScreen desde :enterprise
 * - Implementar NavController con roles RBAC
 * - Conectar BillingManager de :billing
 * - Inicializar SyncManager de :sync en startup
 */
@Composable
fun OrionApp() {
    Box(
        modifier          = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentAlignment  = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "⭐",
                fontSize   = 64.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text       = "ORION",
                color      = Color.White,
                fontSize   = 42.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text     = "Operational Reliability & Identity Operating Network",
                color    = Color(0xFF4A9EBF),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text     = "v1.0.0 — Grado Militar Industrial",
                color    = Color(0xFF555555),
                fontSize = 12.sp
            )
            Text(
                text     = "TAD-B v3.0 — 61 tests verificados",
                color    = Color(0xFF444444),
                fontSize = 11.sp
            )
        }
    }
}
