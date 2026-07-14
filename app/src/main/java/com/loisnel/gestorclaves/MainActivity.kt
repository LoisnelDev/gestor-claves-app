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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// ═══════════════════════════════════════════════════════════
// MODELO DE DATOS
// ═══════════════════════════════════════════════════════════
enum class Pantalla { LOGIN, CREAR_MASTER, INGRESAR_MASTER, MENU, EDITOR }

data class Clave(
    val sitio: String,
    val usuario: String,
    val password: String,
    val extras: String
)

// ═══════════════════════════════════════════════════════════
// CONSTANTES DE SEGURIDAD
// ═══════════════════════════════════════════════════════════
private const val PBKDF2_ITERATIONS = 200_000
private const val PBKDF2_KEY_LENGTH  = 256
private const val SALT_SIZE_BYTES    = 16
private const val IV_SIZE_BYTES      = 12
private const val GCM_TAG_LENGTH     = 128
private const val PBKDF2_ALGORITHM   = "PBKDF2WithHmacSHA256"
private const val AES_ALGORITHM      = "AES/GCM/NoPadding"
private const val MASTER_FLAG_KEY    = "master_configured"

// ═══════════════════════════════════════════════════════════
// ACTIVIDAD PRINCIPAL
// ═══════════════════════════════════════════════════════════
class MainActivity : FragmentActivity() {

    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val TIMEOUT_MS = 120_000L
    private var esperandoBiometria = false

    private val inactivityRunnable = Runnable {
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

    fun setEsperandoBiometria(valor: Boolean) {
        esperandoBiometria = valor
        if (valor) pausarTemporizador() else reiniciarTemporizador()
    }

    private fun mostrarNotificacionCierre() {
        val channelId = "gestor_seguridad"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = getString(R.string.notification_channel_desc) }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (!esperandoBiometria) reiniciarTemporizador()
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        if (!esperandoBiometria) reiniciarTemporizador()
    }

    override fun onPause() {
        super.onPause()
        if (!esperandoBiometria) pausarTemporizador()
    }

    override fun onDestroy() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        super.onDestroy()
    }
}

// ═══════════════════════════════════════════════════════════
// ALMACENAMIENTO ENCRIPTADO — Singleton thread-safe
// ═══════════════════════════════════════════════════════════
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

// ── Verificar si la contraseña maestra ya fue configurada ───
fun masterConfigurado(context: Context): Boolean {
    return try {
        getPrefs(context).getBoolean(MASTER_FLAG_KEY, false)
    } catch (e: Exception) { false }
}

// ═══════════════════════════════════════════════════════════
// CIFRADO DEL RESPALDO — PBKDF2 + AES-256-GCM
// ═══════════════════════════════════════════════════════════

// Derivar clave AES-256 desde contraseña maestra + salt
private fun derivarClave(password: String, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(
        password.toCharArray(),
        salt,
        PBKDF2_ITERATIONS,
        PBKDF2_KEY_LENGTH
    )
    val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
    val keyBytes = factory.generateSecret(spec).encoded
    spec.clearPassword() // Limpiar contraseña de memoria inmediatamente
    return SecretKeySpec(keyBytes, "AES")
}

// Cifrar JSON con AES-256-GCM
// Formato del archivo: [16 bytes salt][12 bytes IV][N bytes cifrado]
private fun cifrarRespaldo(jsonPlano: String, password: String): ByteArray {
    val salt = ByteArray(SALT_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
    val iv   = ByteArray(IV_SIZE_BYTES).also  { SecureRandom().nextBytes(it) }
    val clave = derivarClave(password, salt)

    val cipher = Cipher.getInstance(AES_ALGORITHM)
    cipher.init(Cipher.ENCRYPT_MODE, clave, GCMParameterSpec(GCM_TAG_LENGTH, iv))
    val cifrado = cipher.doFinal(jsonPlano.toByteArray(Charsets.UTF_8))

    // Empaquetamos: salt + IV + datos cifrados
    return salt + iv + cifrado
}

// Descifrar respaldo con contraseña maestra
// Retorna null si la contraseña es incorrecta
private fun descifrarRespaldo(datos: ByteArray, password: String): String? {
    return try {
        if (datos.size < SALT_SIZE_BYTES + IV_SIZE_BYTES) return null

        val salt    = datos.copyOfRange(0, SALT_SIZE_BYTES)
        val iv      = datos.copyOfRange(SALT_SIZE_BYTES, SALT_SIZE_BYTES + IV_SIZE_BYTES)
        val cifrado = datos.copyOfRange(SALT_SIZE_BYTES + IV_SIZE_BYTES, datos.size)

        val clave = derivarClave(password, salt)
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, clave, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        cipher.doFinal(cifrado).toString(Charsets.UTF_8)
    } catch (e: Exception) {
        null // Contraseña incorrecta o datos corruptos
    }
}

// ═══════════════════════════════════════════════════════════
// OPERACIONES DE DATOS
// ═══════════════════════════════════════════════════════════

// Actualizar respaldo cifrado tras cada operación
private fun actualizarRespaldoLocal(
    context: Context,
    prefs: android.content.SharedPreferences,
    passwordMaster: String
) {
    try {
        val jsonBackup = JSONObject()
        prefs.all.forEach { (sitio, valor) ->
            if (sitio != MASTER_FLAG_KEY) // No incluir el flag interno
                jsonBackup.put(sitio, valor.toString())
        }
        val jsonPlano   = jsonBackup.toString()
        val datosCifrados = cifrarRespaldo(jsonPlano, passwordMaster)
        java.io.File(context.filesDir, "backup_local_invisible.dat")
            .writeBytes(datosCifrados)
    } catch (e: Exception) {
        // Error silencioso — respaldo es secundario al guardado principal
    }
}

// Guardar clave
fun guardarClaveAsync(
    scope: CoroutineScope,
    context: Context,
    clave: Clave,
    passwordMaster: String,
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
                prefs.edit().putString(clave.sitio, json).commit()
                actualizarRespaldoLocal(context, prefs, passwordMaster)
            } catch (e: Exception) { }
        }
        withContext(Dispatchers.Main) { onCompletado() }
    }
}

// Cargar claves
suspend fun cargarClavesAsync(context: Context): List<Clave> =
    withContext(Dispatchers.IO) {
        return@withContext try {
            getPrefs(context).all
                .filter { it.key != MASTER_FLAG_KEY }
                .mapNotNull { (sitio, valor) ->
                    try {
                        val json = JSONObject(valor.toString())
                        Clave(
                            sitio    = sitio,
                            usuario  = json.optString("usuario"),
                            password = json.optString("password"),
                            extras   = json.optString("extras")
                        )
                    } catch (e: Exception) { null }
                }.sortedBy { it.sitio.lowercase() }
        } catch (e: Exception) { emptyList() }
    }

// Eliminar clave
fun eliminarClaveAsync(
    scope: CoroutineScope,
    context: Context,
    sitio: String,
    passwordMaster: String,
    onCompletado: () -> Unit
) {
    scope.launch {
        withContext(Dispatchers.IO) {
            try {
                val prefs = getPrefs(context)
                prefs.edit().remove(sitio).commit()
                actualizarRespaldoLocal(context, prefs, passwordMaster)
            } catch (e: Exception) { }
        }
        withContext(Dispatchers.Main) { onCompletado() }
    }
}

// Configurar contraseña maestra por primera vez
fun configurarMasterAsync(
    scope: CoroutineScope,
    context: Context,
    passwordMaster: String,
    onCompletado: () -> Unit
) {
    scope.launch {
        withContext(Dispatchers.IO) {
            try {
                val prefs = getPrefs(context)
                prefs.edit().putBoolean(MASTER_FLAG_KEY, true).commit()
                // Crear respaldo inicial cifrado (vacío)
                actualizarRespaldoLocal(context, prefs, passwordMaster)
            } catch (e: Exception) { }
        }
        withContext(Dispatchers.Main) { onCompletado() }
    }
}

// Verificar y restaurar desde respaldo cifrado
suspend fun verificarYRestaurarRespaldo(
    context: Context,
    passwordMaster: String
): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        val prefs = getPrefs(context)

        // Solo restaurar si no hay datos de usuario
        val tieneDatos = prefs.all.any { it.key != MASTER_FLAG_KEY }
        if (tieneDatos) return@withContext true

        val archivo = java.io.File(context.filesDir, "backup_local_invisible.dat")
        if (!archivo.exists()) return@withContext false

        val datosCifrados = archivo.readBytes()
        if (datosCifrados.isEmpty()) return@withContext false

        // Intentar descifrar — si falla, contraseña incorrecta
        val jsonPlano = descifrarRespaldo(datosCifrados, passwordMaster)
            ?: return@withContext false // Contraseña incorrecta

        val jsonBackup = JSONObject(jsonPlano)
        val editor = prefs.edit()
        jsonBackup.keys().forEach { sitio ->
            editor.putString(sitio, jsonBackup.getString(sitio))
        }
        editor.putBoolean(MASTER_FLAG_KEY, true)
        editor.commit()
        true
    } catch (e: Exception) { false }
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

    // Contraseña maestra en memoria — nunca persiste en variables globales
    var passwordMaster by remember { mutableStateOf("") }

    LaunchedEffect(pantalla) {
        if (pantalla == Pantalla.MENU) {
            cargando = true
            listaClaves = cargarClavesAsync(context)
            cargando = false
        }
    }

    when (pantalla) {
        Pantalla.LOGIN -> PantallaLogin(
            activity = activity,
            onAutenticado = {
                // Después de biometría: verificar si es primer uso o recuperación
                val esPrimerUso = !masterConfigurado(context)
                val tieneRespaldo = java.io.File(
                    context.filesDir, "backup_local_invisible.dat"
                ).exists()

                pantalla = when {
                    esPrimerUso && !tieneRespaldo -> Pantalla.CREAR_MASTER
                    esPrimerUso && tieneRespaldo  -> Pantalla.INGRESAR_MASTER
                    else                           -> Pantalla.MENU
                }
            }
        )

        Pantalla.CREAR_MASTER -> PantallaCrearMaster(
            activityScope = activityScope,
            onConfirmado = { master ->
                passwordMaster = master
                configurarMasterAsync(activityScope, context, master) {
                    pantalla = Pantalla.MENU
                }
            }
        )

        Pantalla.INGRESAR_MASTER -> PantallaIngresarMaster(
            activityScope = activityScope,
            onConfirmado = { master ->
                passwordMaster = master
                pantalla = Pantalla.MENU
            },
            onRecuperacionFallida = {
                // Contraseña incorrecta — volver a intentar
            }
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
            passwordMaster = passwordMaster,
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

    LaunchedEffect(Unit) {
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
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth().height(52.dp),
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
// PANTALLA CREAR CONTRASEÑA MAESTRA (primer uso)
// ═══════════════════════════════════════════════════════════
@Composable
fun PantallaCrearMaster(
    activityScope: CoroutineScope,
    onConfirmado: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var mostrarPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var procesando by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🔑 Master Password",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Create a master password to encrypt your backup. You will need it to recover your data on a new device.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                // Campo contraseña
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = "" },
                    label = { Text("Master password", color = Color.Gray) },
                    visualTransformation = if (mostrarPass) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    colors = campoColores(),
                    trailingIcon = {
                        TextButton(onClick = { mostrarPass = !mostrarPass }) {
                            Text(
                                text = if (mostrarPass) "Hide" else "Show",
                                color = Color(0xFF0066CC),
                                fontSize = 12.sp
                            )
                        }
                    }
                )

                // Confirmar contraseña
                OutlinedTextField(
                    value = confirmar,
                    onValueChange = { confirmar = it; error = "" },
                    label = { Text("Confirm password", color = Color.Gray) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    colors = campoColores()
                )

                // Indicador de fortaleza
                if (password.isNotEmpty()) {
                    val fortaleza = calcularFortaleza(password)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(4) { i ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .background(
                                        color = if (i < fortaleza.nivel)
                                            fortaleza.color
                                        else Color(0xFF333333),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                    Text(
                        text = fortaleza.texto,
                        color = fortaleza.color,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (error.isNotEmpty()) {
                    Text(
                        text = error,
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        when {
                            password.length < 8 ->
                                error = "Password must be at least 8 characters"
                            password != confirmar ->
                                error = "Passwords do not match"
                            else -> {
                                procesando = true
                                onConfirmado(password)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !procesando && password.isNotEmpty() && confirmar.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))
                ) {
                    if (procesando) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("CREATE AND CONTINUE", fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "⚠️ If you forget this password, your backup cannot be recovered.",
                    fontSize = 11.sp,
                    color = Color(0xFFCC7700),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// PANTALLA INGRESAR CONTRASEÑA MAESTRA (recuperación)
// ═══════════════════════════════════════════════════════════
@Composable
fun PantallaIngresarMaster(
    activityScope: CoroutineScope,
    onConfirmado: (String) -> Unit,
    onRecuperacionFallida: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var mostrarPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var procesando by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🔑 Recovery",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "A backup was found. Enter your master password to restore your data.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = "" },
                    label = { Text("Master password", color = Color.Gray) },
                    visualTransformation = if (mostrarPass) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    colors = campoColores(),
                    trailingIcon = {
                        TextButton(onClick = { mostrarPass = !mostrarPass }) {
                            Text(
                                text = if (mostrarPass) "Hide" else "Show",
                                color = Color(0xFF0066CC),
                                fontSize = 12.sp
                            )
                        }
                    }
                )

                if (error.isNotEmpty()) {
                    Text(
                        text = error,
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        if (password.isBlank()) return@Button
                        procesando = true
                        activityScope.launch {
                            val exito = verificarYRestaurarRespaldo(context, password)
                            withContext(Dispatchers.Main) {
                                procesando = false
                                if (exito) {
                                    onConfirmado(password)
                                } else {
                                    error = "Incorrect password. Please try again."
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !procesando && password.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))
                ) {
                    if (procesando) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("RESTORE DATA", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// INDICADOR DE FORTALEZA DE CONTRASEÑA
// ═══════════════════════════════════════════════════════════
data class Fortaleza(val nivel: Int, val color: Color, val texto: String)

fun calcularFortaleza(password: String): Fortaleza {
    var puntos = 0
    if (password.length >= 8)  puntos++
    if (password.length >= 12) puntos++
    if (password.any { it.isDigit() } && password.any { it.isLetter() }) puntos++
    if (password.any { !it.isLetterOrDigit() }) puntos++

    return when (puntos) {
        0, 1 -> Fortaleza(1, Color(0xFFFF4444), "Weak")
        2    -> Fortaleza(2, Color(0xFFCC7700), "Fair")
        3    -> Fortaleza(3, Color(0xFF4DA6FF), "Good")
        else -> Fortaleza(4, Color(0xFF00AA66), "Strong")
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
        BiometricManager.BIOMETRIC_SUCCESS -> { }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            return onError(activity.getString(R.string.biometric_no_hardware))
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            return onError(activity.getString(R.string.biometric_none_enrolled))
        else ->
            return onError(activity.getString(R.string.biometric_unavailable))
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                super.onAuthenticationSucceeded(result)
                onExito()
            }
            override fun onAuthenticationError(
                errorCode: Int, errString: CharSequence
            ) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    onError("")
                } else {
                    onError(activity.getString(R.string.biometric_error_prefix, errString))
                }
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        OutlinedTextField(
            value = busqueda,
            onValueChange = { busqueda = it },
            label = { Text(text = stringResource(R.string.menu_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = campoColores()
        )

        when {
            cargando -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Color(0xFF0066CC)) }
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
                    items(items = clavesFiltradas, key = { it.sitio }) { clave ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onEditar(clave) },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = clave.sitio,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(text = "›", color = Color.Gray, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onNuevo,
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 8.dp),
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
    passwordMaster: String,
    onGuardar: () -> Unit,
    onVolver: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val esNuevo = claveExistente == null

    var sitio    by remember { mutableStateOf(claveExistente?.sitio    ?: "") }
    var usuario  by remember { mutableStateOf(claveExistente?.usuario  ?: "") }
    var password by remember { mutableStateOf(claveExistente?.password ?: "") }
    var extras   by remember { mutableStateOf(claveExistente?.extras   ?: "") }
    var modoEdicion       by remember { mutableStateOf(esNuevo) }
    var mostrarPassword   by remember { mutableStateOf(false) }
    var guardando         by remember { mutableStateOf(false) }
    var mostrarConfGuardar  by remember { mutableStateOf(false) }
    var mostrarConfEliminar by remember { mutableStateOf(false) }

    val lineasExtras by remember(extras) {
        derivedStateOf { extras.split("\n").filter { it.isNotBlank() } }
    }

    // ── Arquitectura de dos capas ────────────────────────────
    // Capa 1 (weight=1f): contenido scrollable — título y campos
    // Capa 2 (fija):      botones siempre visibles en la parte inferior
    // Esto garantiza acceso a los botones sin importar cuántos
    // campos EXTRAS tenga la entrada.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // ── ZONA SCROLLABLE — título y todos los campos ──────
        androidx.compose.foundation.rememberScrollState().let { scrollState ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (esNuevo) stringResource(R.string.editor_title_nuevo)
                           else "🔑 ${claveExistente?.sitio}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                    textAlign = TextAlign.Center
                )

                CampoEditor(
                    label = stringResource(R.string.editor_label_sitio),
                    valor = sitio, habilitado = modoEdicion,
                    onCambio = { sitio = it },
                    onCopiar = { clipboard.setText(AnnotatedString(sitio)) },
                    onPegar  = { clipboard.getText()?.text?.let { sitio = it } }
                )
                CampoEditor(
                    label = stringResource(R.string.editor_label_usuario),
                    valor = usuario, habilitado = modoEdicion,
                    onCambio = { usuario = it },
                    onCopiar = { clipboard.setText(AnnotatedString(usuario)) },
                    onPegar  = { clipboard.getText()?.text?.let { usuario = it } }
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { if (modoEdicion) password = it },
                    label = { Text(stringResource(R.string.editor_label_pass), color = Color.Gray) },
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
                            TextButton(onClick = { mostrarPassword = !mostrarPassword }) {
                                Text(
                                    text = if (mostrarPassword) stringResource(R.string.editor_btn_ocultar)
                                           else stringResource(R.string.editor_btn_ver),
                                    color = Color(0xFF0066CC), fontSize = 12.sp
                                )
                            }
                            if (!modoEdicion) {
                                TextButton(onClick = { clipboard.setText(AnnotatedString(password)) }) {
                                    Text(stringResource(R.string.editor_btn_copiar),
                                        color = Color(0xFFCC7700), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                )

                if (modoEdicion) {
                    OutlinedTextField(
                        value = extras, onValueChange = { extras = it },
                        label = { Text(stringResource(R.string.editor_label_extras_edit), color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(), maxLines = 4, colors = campoColores(),
                        trailingIcon = {
                            TextButton(onClick = { clipboard.getText()?.text?.let { extras = it } }) {
                                Text(stringResource(R.string.editor_btn_pegar),
                                    color = Color(0xFF00AA66), fontSize = 12.sp)
                            }
                        }
                    )
                } else {
                    if (lineasExtras.isEmpty()) {
                        OutlinedTextField(
                            value = "", onValueChange = {},
                            label = { Text(stringResource(R.string.editor_label_extras_view), color = Color.Gray) },
                            enabled = false, modifier = Modifier.fillMaxWidth(), colors = campoColores()
                        )
                    } else {
                        lineasExtras.forEachIndexed { i, linea ->
                            OutlinedTextField(
                                value = linea, onValueChange = {},
                                label = { Text("${stringResource(R.string.editor_label_extras_view)} (${i + 1})", color = Color.Gray) },
                                enabled = false, modifier = Modifier.fillMaxWidth(), colors = campoColores(),
                                trailingIcon = {
                                    TextButton(onClick = { clipboard.setText(AnnotatedString(linea)) }) {
                                        Text(stringResource(R.string.editor_btn_copiar),
                                            color = Color(0xFFCC7700), fontSize = 12.sp)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── ZONA FIJA — botones siempre visibles ─────────────
        HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onVolver, modifier = Modifier.weight(1f).height(50.dp)) {
                Text(stringResource(R.string.editor_btn_volver), color = Color.White)
            }
            if (!esNuevo && !modoEdicion) {
                Button(
                    onClick = { modoEdicion = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC7700))
                ) { Text(stringResource(R.string.editor_btn_modificar), fontWeight = FontWeight.Bold) }
            }
            if (modoEdicion) {
                Button(
                    onClick = { if (sitio.isNotBlank() && !guardando) mostrarConfGuardar = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                    enabled = !guardando && sitio.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))
                ) {
                    if (guardando) {
                        CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.editor_btn_guardar), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (!esNuevo && !modoEdicion) {
            TextButton(
                onClick = { mostrarConfEliminar = true },
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                Text(stringResource(R.string.editor_link_eliminar),
                    color = Color(0xFFFF4444), fontSize = 14.sp)
            }
        }
    }

    if (mostrarConfGuardar) {
        AlertDialog(
            onDismissRequest = { if (!guardando) mostrarConfGuardar = false },
            title = { Text(stringResource(R.string.dialog_confirm_title), fontWeight = FontWeight.Bold) },
            text  = { Text(stringResource(R.string.dialog_confirm_save_text, sitio)) },
            confirmButton = {
                TextButton(onClick = {
                    if (!guardando) {
                        guardando = true
                        guardarClaveAsync(
                            scope = activityScope, context = context,
                            clave = Clave(sitio, usuario, password, extras),
                            passwordMaster = passwordMaster
                        ) {
                            guardando = false
                            mostrarConfGuardar = false
                            onGuardar()
                        }
                    }
                }) { Text(stringResource(R.string.dialog_btn_si), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { if (!guardando) mostrarConfGuardar = false }) {
                    Text(stringResource(R.string.dialog_btn_no), color = Color.Gray)
                }
            }
        )
    }

    if (mostrarConfEliminar) {
        AlertDialog(
            onDismissRequest = { mostrarConfEliminar = false },
            title = {
                Text(stringResource(R.string.dialog_delete_title),
                    fontWeight = FontWeight.Bold, color = Color(0xFFFF4444))
            },
            text = {
                Text(stringResource(R.string.dialog_delete_text, claveExistente?.sitio ?: ""))
            },
            confirmButton = {
                TextButton(onClick = {
                    val sitioAEliminar = claveExistente?.sitio
                    if (sitioAEliminar != null) {
                        eliminarClaveAsync(
                            scope = activityScope, context = context,
                            sitio = sitioAEliminar,
                            passwordMaster = passwordMaster
                        ) {
                            mostrarConfEliminar = false
                            onVolver()
                        }
                    }
                }) {
                    Text(stringResource(R.string.dialog_btn_eliminar),
                        color = Color(0xFFFF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfEliminar = false }) {
                    Text(stringResource(R.string.dialog_btn_cancelar), color = Color.Gray)
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
    label: String, valor: String, habilitado: Boolean,
    onCambio: (String) -> Unit, onCopiar: () -> Unit, onPegar: () -> Unit
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { if (habilitado) onCambio(it) },
        label = { Text(label, color = Color.Gray) },
        enabled = habilitado, singleLine = true,
        modifier = Modifier.fillMaxWidth(), colors = campoColores(),
        trailingIcon = {
            if (habilitado) {
                TextButton(onClick = onPegar) {
                    Text(stringResource(R.string.editor_btn_pegar),
                        color = Color(0xFF00AA66), fontSize = 12.sp)
                }
            } else {
                TextButton(onClick = onCopiar) {
                    Text(stringResource(R.string.editor_btn_copy_short),
                        color = Color(0xFFCC7700), fontSize = 12.sp)
                }
            }
        }
    )
}

@Composable
fun campoColores() = OutlinedTextFieldDefaults.colors(
    focusedTextColor    = Color.White,
    unfocusedTextColor  = Color.White,
    disabledTextColor   = Color(0xFFCCCCCC),
    focusedLabelColor   = Color(0xFF0066CC),
    unfocusedLabelColor = Color.Gray,
    disabledLabelColor  = Color.Gray,
    focusedBorderColor  = Color(0xFF0066CC),
    unfocusedBorderColor  = Color(0xFF444444),
    disabledBorderColor   = Color(0xFF333333)
)
