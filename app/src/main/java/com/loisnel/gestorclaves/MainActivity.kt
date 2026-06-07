package com.loisnel.gestorclaves

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ═══════════════════════════════════════════════════════════
// MODELO DE DATOS
// ═══════════════════════════════════════════════════════════
enum class Pantalla { LOGIN, MENU, EDITOR }

data class Clave(
    val sitio: String,
    val usuario: String,
    val password: String,
    val extras: String
)

// ═══════════════════════════════════════════════════════════
// ACTIVIDAD PRINCIPAL
// ═══════════════════════════════════════════════════════════
class MainActivity : FragmentActivity() {

    // ── Temporizador de inactividad (120 segundos) ──────────
    // Requerimiento: cierre automático si no hay actividad
    // - Se reinicia con cada toque (dispatchTouchEvent)
    // - Se pausa cuando la app va a segundo plano (onPause)
    // - Se reinicia cuando el usuario regresa (onResume)
    // - Se pausa durante el diálogo biométrico (esperandoBiometria)
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val TIMEOUT_MS = 120_000L

    // Bandera: pausa el temporizador mientras el diálogo biométrico está activo
    // Evita que el tiempo del diálogo cuente como inactividad
    private var esperandoBiometria = false

    private val inactivityRunnable = Runnable {
        // Verificar que la Activity siga activa antes de cerrar
        if (!isFinishing && !isDestroyed) {
            mostrarNotificacionCierre()
            finishAffinity()
        }
    }

    fun reiniciarTemporizador() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, TIMEOUT_MS)
    }

    private fun pausarTemporizador() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }

    // Llamado desde PantallaLogin para pausar/reanudar durante biometría
    fun setEsperandoBiometria(valor: Boolean) {
        esperandoBiometria = valor
        if (valor) pausarTemporizador() else reiniciarTemporizador()
    }

    // ── Notificación nativa al cerrar por inactividad ───────
    private fun mostrarNotificacionCierre() {
        val channelId = "gestor_seguridad"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val tienePermiso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (tienePermiso) {
            val notificacion = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(1001, notificacion)
        }
    }

    // ── Ciclo de vida ────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicitar permiso de notificaciones en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }

        // Pantalla encendida mientras el usuario esté en la app
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Respetar barras del sistema (status bar + nav bar)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Iniciar temporizador al arrancar
        reiniciarTemporizador()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GestorApp(
                    activityScope = lifecycleScope,
                    activity = this,
                    onSalir = { finishAffinity() }
                )
            }
        }
    }

    // Cada toque reinicia el contador — solo si no hay biometría activa
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (!esperandoBiometria) reiniciarTemporizador()
        return super.dispatchTouchEvent(ev)
    }

    // Requerimiento: al regresar a la app el contador se reinicia desde 0
    override fun onResume() {
        super.onResume()
        if (!esperandoBiometria) reiniciarTemporizador()
    }

    // Requerimiento: al salir de la app el contador se pausa
    override fun onPause() {
        super.onPause()
        if (!esperandoBiometria) pausarTemporizador()
    }

    override fun onDestroy() {
        // Siempre limpiar antes de super.onDestroy()
        inactivityHandler.removeCallbacks(inactivityRunnable)
        super.onDestroy()
    }
}

// ═══════════════════════════════════════════════════════════
// ALMACENAMIENTO ENCRIPTADO — Singleton thread-safe
// ═══════════════════════════════════════════════════════════

// @Volatile garantiza visibilidad entre hilos (double-checked locking)
@Volatile
private var prefsInstance: android.content.SharedPreferences? = null

private fun getPrefs(context: Context): android.content.SharedPreferences {
    return prefsInstance ?: synchronized(context.applicationContext) {
        prefsInstance ?: EncryptedSharedPreferences.create(
            context.applicationContext,
            "gestor_claves_prefs",
            MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { prefsInstance = it }
    }
}

// ── Respaldo local centralizado ──────────────────────────────
// Se actualiza tras cada operación de escritura exitosa
// El archivo se incluye en Google Backup para recuperación
// en caso de desinstalación o cambio de dispositivo
private fun actualizarRespaldoLocal(
    context: Context,
    prefs: android.content.SharedPreferences
) {
    try {
        val jsonBackup = JSONObject()
        prefs.all.forEach { (sitio, valor) ->
            jsonBackup.put(sitio, valor.toString())
        }
        java.io.File(context.filesDir, "backup_local_invisible.dat")
            .writeText(jsonBackup.toString(), Charsets.UTF_8)
    } catch (e: Exception) {
        // Error silencioso — el respaldo es secundario al guardado principal
    }
}

// ── Guardar clave (hilo IO) ──────────────────────────────────
fun guardarClaveAsync(
    scope: CoroutineScope,
    context: Context,
    clave: Clave,
    onCompletado: () -> Unit
) {
    scope.launch {
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("usuario", clave.usuario)
                    put("password", clave.password)
                    put("extras", clave.extras)
                }.toString()

                val prefs = getPrefs(context)
                // FIX: .commit() en lugar de .apply() — garantiza escritura
                // completada antes de actualizar el respaldo local
                prefs.edit().putString(clave.sitio, json).commit()

                // Respaldo solo después de confirmar escritura exitosa
                actualizarRespaldoLocal(context, prefs)

            } catch (e: Exception) {
                // Error controlado — no crashear
            }
        }
        // FIX: withContext(Main) garantiza que onCompletado() corre en hilo UI
        // independientemente del scope que se pase como parámetro
        withContext(Dispatchers.Main) {
            onCompletado()
        }
    }
}

// ── Cargar claves (hilo IO) ──────────────────────────────────
suspend fun cargarClavesAsync(context: Context): List<Clave> =
    withContext(Dispatchers.IO) {
        return@withContext try {
            getPrefs(context).all.mapNotNull { (sitio, valor) ->
                try {
                    val json = JSONObject(valor.toString())
                    Clave(
                        sitio = sitio,
                        usuario = json.optString("usuario"),
                        password = json.optString("password"),
                        extras = json.optString("extras")
                    )
                } catch (e: Exception) {
                    null // Ignorar entradas corruptas individualmente
                }
            }.sortedBy { it.sitio.lowercase() } // lowercase para orden consistente
        } catch (e: Exception) {
            emptyList()
        }
    }

// ── Eliminar clave (hilo IO) ─────────────────────────────────
fun eliminarClaveAsync(
    scope: CoroutineScope,
    context: Context,
    sitio: String,
    onCompletado: () -> Unit
) {
    scope.launch {
        withContext(Dispatchers.IO) {
            try {
                val prefs = getPrefs(context)
                // FIX: .commit() garantiza eliminación antes de actualizar respaldo
                prefs.edit().remove(sitio).commit()
                actualizarRespaldoLocal(context, prefs)
            } catch (e: Exception) { }
        }
        // FIX: withContext(Main) garantiza hilo UI
        withContext(Dispatchers.Main) {
            onCompletado()
        }
    }
}

// ── Verificar y restaurar desde respaldo ─────────────────────
// Escenario 1: Desinstalación accidental (mismo dispositivo)
// Escenario 2: Migración a dispositivo nuevo via Google Backup
suspend fun verificarYRestaurarRespaldoInvisible(context: Context) =
    withContext(Dispatchers.IO) {
        try {
            val prefs = getPrefs(context)
            // Solo restaurar si no hay datos — no sobreescribir datos existentes
            if (prefs.all.isNotEmpty()) return@withContext

            val archivoRespaldo = java.io.File(context.filesDir, "backup_local_invisible.dat")
            if (!archivoRespaldo.exists()) return@withContext

            val contenido = archivoRespaldo.readText(Charsets.UTF_8)
            if (contenido.isBlank()) return@withContext

            val jsonBackup = JSONObject(contenido)
            val editor = prefs.edit()
            jsonBackup.keys().forEach { sitio ->
                editor.putString(sitio, jsonBackup.getString(sitio))
            }
            // FIX: .commit() garantiza restauración completa antes de cargar UI
            editor.commit()

        } catch (e: Exception) { }
    }

// ═══════════════════════════════════════════════════════════
// APP PRINCIPAL
// ═══════════════════════════════════════════════════════════
@Composable
fun GestorApp(
    activityScope: CoroutineScope,
    activity: MainActivity,
    onSalir: () -> Unit
) {
    val context = LocalContext.current
    var pantalla by remember { mutableStateOf(Pantalla.LOGIN) }
    var claveEditando by remember { mutableStateOf<Clave?>(null) }
    var listaClaves by remember { mutableStateOf(listOf<Clave>()) }
    var cargando by remember { mutableStateOf(false) }

    // Recargar lista cada vez que se navega al MENU
    LaunchedEffect(pantalla) {
        if (pantalla == Pantalla.MENU) {
            cargando = true
            verificarYRestaurarRespaldoInvisible(context)
            listaClaves = cargarClavesAsync(context)
            cargando = false
        }
    }

    when (pantalla) {
        Pantalla.LOGIN -> PantallaLogin(
            activity = activity,
            onAutenticado = { pantalla = Pantalla.MENU }
        )
        Pantalla.MENU -> PantallaMenu(
            claves = listaClaves,
            cargando = cargando,
            onNuevo = { claveEditando = null; pantalla = Pantalla.EDITOR },
            onEditar = { clave -> claveEditando = clave; pantalla = Pantalla.EDITOR },
            onSalir = onSalir
        )
        Pantalla.EDITOR -> PantallaEditor(
            activityScope = activityScope,
            claveExistente = claveEditando,
            onGuardar = { pantalla = Pantalla.MENU },
            onVolver = { pantalla = Pantalla.MENU }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// PANTALLA LOGIN
// ═══════════════════════════════════════════════════════════
@Composable
fun PantallaLogin(
    activity: MainActivity,
    onAutenticado: () -> Unit
) {
    val context = LocalContext.current
    var mensajeError by remember { mutableStateOf("") }
    var intentando by remember { mutableStateOf(false) }

    // Lanzar biometría automáticamente al entrar
    LaunchedEffect(Unit) {
        // Pausar temporizador durante el diálogo biométrico
        activity.setEsperandoBiometria(true)
        lanzarBiometria(
            activity = activity,
            onExito = {
                activity.setEsperandoBiometria(false)
                intentando = false
                onAutenticado()
            },
            onError = { error ->
                activity.setEsperandoBiometria(false)
                intentando = false
                mensajeError = error
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.login_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.login_subtitle),
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                if (mensajeError.isNotEmpty()) {
                    Text(
                        text = mensajeError,
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        // FIX: guard clause — evita doble toque
                        if (intentando) return@Button
                        intentando = true
                        mensajeError = ""
                        activity.setEsperandoBiometria(true)
                        val fragActivity = context as? FragmentActivity ?: run {
                            activity.setEsperandoBiometria(false)
                            intentando = false
                            return@Button
                        }
                        lanzarBiometria(
                            activity = fragActivity,
                            onExito = {
                                activity.setEsperandoBiometria(false)
                                intentando = false
                                onAutenticado()
                            },
                            onError = { error ->
                                activity.setEsperandoBiometria(false)
                                intentando = false
                                mensajeError = error
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !intentando,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))
                ) {
                    if (intentando) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.login_btn),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// BIOMETRÍA NATIVA
// ═══════════════════════════════════════════════════════════
fun lanzarBiometria(
    activity: FragmentActivity,
    onExito: () -> Unit,
    onError: (String) -> Unit
) {
    val biometricManager = BiometricManager.from(activity)
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

    when (biometricManager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> { /* continuar */ }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            return onError(activity.getString(R.string.biometric_no_hardware))
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            return onError(activity.getString(R.string.biometric_none_enrolled))
        else ->
            return onError(activity.getString(R.string.biometric_unavailable))
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                super.onAuthenticationSucceeded(result)
                onExito()
            }

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence
            ) {
                super.onAuthenticationError(errorCode, errString)
                // FIX: incluir ERROR_CANCELED además de los dos originales
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    onError("") // Cancelación voluntaria — no mostrar error
                } else {
                    onError(activity.getString(R.string.biometric_error_prefix, errString))
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Intento fallido — el diálogo sigue abierto, no hacer nada
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.dialog_biometric_title))
        .setSubtitle(activity.getString(R.string.dialog_biometric_subtitle))
        .setAllowedAuthenticators(authenticators)
        .build()

    prompt.authenticate(promptInfo)
}

// ═══════════════════════════════════════════════════════════
// PANTALLA MENÚ
// ═══════════════════════════════════════════════════════════
@Composable
fun PantallaMenu(
    claves: List<Clave>,
    cargando: Boolean,
    onNuevo: () -> Unit,
    onEditar: (Clave) -> Unit,
    onSalir: () -> Unit
) {
    var busqueda by remember { mutableStateOf("") }

    // derivedStateOf evita recalcular en cada recomposición innecesaria
    val clavesFiltradas by remember(claves, busqueda) {
        derivedStateOf {
            if (busqueda.isEmpty()) claves
            else claves.filter { it.sitio.contains(busqueda, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Encabezado ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FIX: app_name en lugar de login_title para el título del menú
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Button(
                onClick = onSalir,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB2222)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = stringResource(R.string.menu_btn_salir),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Buscador ─────────────────────────────────────────
        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            label = { Text(text = stringResource(R.string.menu_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = campoColores()
        )

        // ── Contenido: usando when para cubrir todos los casos ─
        when {
            cargando -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF0066CC))
                }
            }
            claves.isEmpty() -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.menu_empty_list),
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
            clavesFiltradas.isEmpty() -> {
                // FIX: caso búsqueda sin resultados — antes caía en LazyColumn vacío
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.menu_no_results, busqueda),
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = clavesFiltradas,
                        key = { it.sitio } // Clave única para recomposición eficiente
                    ) { clave ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditar(clave) },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = clave.sitio,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "›",
                                    color = Color.Gray,
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Botón nueva clave ────────────────────────────────
        Button(
            onClick = onNuevo,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006633)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.menu_btn_nueva),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// PANTALLA EDITOR
// ═══════════════════════════════════════════════════════════
@Composable
fun PantallaEditor(
    activityScope: CoroutineScope,
    claveExistente: Clave?,
    onGuardar: () -> Unit,
    onVolver: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val esNuevo = claveExistente == null

    var sitio by remember { mutableStateOf(claveExistente?.sitio ?: "") }
    var usuario by remember { mutableStateOf(claveExistente?.usuario ?: "") }
    var password by remember { mutableStateOf(claveExistente?.password ?: "") }
    var extras by remember { mutableStateOf(claveExistente?.extras ?: "") }
    var modoEdicion by remember { mutableStateOf(esNuevo) }
    var mostrarPassword by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var mostrarConfGuardar by remember { mutableStateOf(false) }
    var mostrarConfEliminar by remember { mutableStateOf(false) }

    // derivedStateOf evita recalcular lineas en cada recomposición
    val lineasExtras by remember(extras) {
        derivedStateOf {
            extras.split("\n").filter { it.isNotBlank() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Título ───────────────────────────────────────────
        Text(
            text = if (esNuevo) stringResource(R.string.editor_title_nuevo)
            else "🔑 ${claveExistente?.sitio}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp),
            textAlign = TextAlign.Center
        )

        // ── Campo SITIO ──────────────────────────────────────
        CampoEditor(
            label = stringResource(R.string.editor_label_sitio),
            valor = sitio,
            habilitado = modoEdicion,
            onCambio = { sitio = it },
            onCopiar = { clipboard.setText(AnnotatedString(sitio)) },
            onPegar = { clipboard.getText()?.text?.let { sitio = it } }
        )

        // ── Campo USUARIO ────────────────────────────────────
        CampoEditor(
            label = stringResource(R.string.editor_label_usuario),
            valor = usuario,
            habilitado = modoEdicion,
            onCambio = { usuario = it },
            onCopiar = { clipboard.setText(AnnotatedString(usuario)) },
            onPegar = { clipboard.getText()?.text?.let { usuario = it } }
        )

        // ── Campo PASSWORD con toggle Ver/Ocultar ────────────
        OutlinedTextField(
            value = password,
            onValueChange = { if (modoEdicion) password = it },
            label = { Text(text = stringResource(R.string.editor_label_pass), color = Color.Gray) },
            enabled = modoEdicion,
            visualTransformation = if (mostrarPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = campoColores(),
            trailingIcon = {
                Row(
                    modifier = Modifier.padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Toggle Ver/Ocultar siempre visible
                    TextButton(onClick = { mostrarPassword = !mostrarPassword }) {
                        Text(
                            text = if (mostrarPassword) stringResource(R.string.editor_btn_ocultar)
                            else stringResource(R.string.editor_btn_ver),
                            color = Color(0xFF0066CC),
                            fontSize = 12.sp
                        )
                    }
                    // Copiar solo en modo lectura
                    if (!modoEdicion) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(password))
                        }) {
                            Text(
                                text = stringResource(R.string.editor_btn_copiar),
                                color = Color(0xFFCC7700),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        )

        // ── Campo EXTRAS ─────────────────────────────────────
        if (modoEdicion) {
            // Modo edición: campo multilinea libre con Pegar
            OutlinedTextField(
                value = extras,
                onValueChange = { extras = it },
                label = {
                    Text(
                        text = stringResource(R.string.editor_label_extras_edit),
                        color = Color.Gray
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                colors = campoColores(),
                trailingIcon = {
                    TextButton(onClick = {
                        clipboard.getText()?.text?.let { extras = it }
                    }) {
                        Text(
                            text = stringResource(R.string.editor_btn_pegar),
                            color = Color(0xFF00AA66),
                            fontSize = 12.sp
                        )
                    }
                }
            )
        } else {
            // Modo lectura: una fila por línea con Copiar individual
            if (lineasExtras.isEmpty()) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = {
                        Text(
                            text = stringResource(R.string.editor_label_extras_view),
                            color = Color.Gray
                        )
                    },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = campoColores()
                )
            } else {
                lineasExtras.forEachIndexed { i, linea ->
                    OutlinedTextField(
                        value = linea,
                        onValueChange = {},
                        label = {
                            Text(
                                text = "${stringResource(R.string.editor_label_extras_view)} (${i + 1})",
                                color = Color.Gray
                            )
                        },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = campoColores(),
                        trailingIcon = {
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(linea))
                            }) {
                                Text(
                                    text = stringResource(R.string.editor_btn_copiar),
                                    color = Color(0xFFCC7700),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Botones de acción ─────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onVolver,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text(text = stringResource(R.string.editor_btn_volver), color = Color.White)
            }

            if (!esNuevo && !modoEdicion) {
                Button(
                    onClick = { modoEdicion = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC7700))
                ) {
                    Text(
                        text = stringResource(R.string.editor_btn_modificar),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (modoEdicion) {
                Button(
                    onClick = {
                        // FIX: doble guard — evita doble toque y campo vacío
                        if (sitio.isNotBlank() && !guardando) mostrarConfGuardar = true
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    enabled = !guardando && sitio.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))
                ) {
                    if (guardando) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.editor_btn_guardar),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Eliminar solo en modo lectura de entrada existente
        if (!esNuevo && !modoEdicion) {
            TextButton(
                onClick = { mostrarConfEliminar = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.editor_link_eliminar),
                    color = Color(0xFFFF4444),
                    fontSize = 14.sp
                )
            }
        }
    }

    // ── Diálogo confirmar guardar ────────────────────────────
    if (mostrarConfGuardar) {
        AlertDialog(
            onDismissRequest = {
                // No permitir cerrar con toque fuera mientras guarda
                if (!guardando) mostrarConfGuardar = false
            },
            title = {
                Text(
                    text = stringResource(R.string.dialog_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(R.string.dialog_confirm_save_text, sitio))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // FIX: guard clause para evitar doble confirmación
                        if (!guardando) {
                            guardando = true
                            guardarClaveAsync(
                                scope = activityScope,
                                context = context,
                                clave = Clave(sitio, usuario, password, extras)
                            ) {
                                guardando = false
                                mostrarConfGuardar = false
                                onGuardar()
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.dialog_btn_si),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (!guardando) mostrarConfGuardar = false
                }) {
                    Text(text = stringResource(R.string.dialog_btn_no), color = Color.Gray)
                }
            }
        )
    }

    // ── Diálogo confirmar eliminar ───────────────────────────
    if (mostrarConfEliminar) {
        AlertDialog(
            onDismissRequest = { mostrarConfEliminar = false },
            title = {
                Text(
                    text = stringResource(R.string.dialog_delete_title),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF4444)
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.dialog_delete_text,
                        claveExistente?.sitio ?: ""
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // FIX: operador seguro en lugar de !! para evitar NullPointerException
                        val sitioAEliminar = claveExistente?.sitio
                        if (sitioAEliminar != null) {
                            eliminarClaveAsync(activityScope, context, sitioAEliminar) {
                                mostrarConfEliminar = false
                                onVolver()
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.dialog_btn_eliminar),
                        color = Color(0xFFFF4444),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfEliminar = false }) {
                    Text(text = stringResource(R.string.dialog_btn_cancelar), color = Color.Gray)
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// COMPONENTES REUTILIZABLES
// ═══════════════════════════════════════════════════════════
@Composable
fun CampoEditor(
    label: String,
    valor: String,
    habilitado: Boolean,
    onCambio: (String) -> Unit,
    onCopiar: () -> Unit,
    onPegar: () -> Unit
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { if (habilitado) onCambio(it) },
        label = { Text(label, color = Color.Gray) },
        enabled = habilitado,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = campoColores(),
        trailingIcon = {
            if (habilitado) {
                TextButton(onClick = onPegar) {
                    Text(
                        text = stringResource(R.string.editor_btn_pegar),
                        color = Color(0xFF00AA66),
                        fontSize = 12.sp
                    )
                }
            } else {
                TextButton(onClick = onCopiar) {
                    Text(
                        text = stringResource(R.string.editor_btn_copy_short),
                        color = Color(0xFFCC7700),
                        fontSize = 12.sp
                    )
                }
            }
        }
    )
}

@Composable
fun campoColores() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color(0xFFCCCCCC),
    focusedLabelColor = Color(0xFF0066CC),
    unfocusedLabelColor = Color.Gray,
    disabledLabelColor = Color.Gray,
    focusedBorderColor = Color(0xFF0066CC),
    unfocusedBorderColor = Color(0xFF444444),
    disabledBorderColor = Color(0xFF333333)
)