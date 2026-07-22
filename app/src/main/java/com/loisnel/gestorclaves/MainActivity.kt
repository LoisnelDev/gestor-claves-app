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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.loisnel.gestorclaves.ClipboardUtils

// ═══════════════════════════════════════════════════════════
// MODELO DE DATOS
// ═══════════════════════════════════════════════════════════
// INGRESAR_MASTER: recuperación (backup existe, prefs vacías)
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
private const val PBKDF2_ITERATIONS  = 200_000
private const val PBKDF2_KEY_LENGTH  = 256
private const val SALT_SIZE_BYTES    = 16
private const val IV_SIZE_BYTES      = 12
private const val GCM_TAG_LENGTH     = 128
private const val PBKDF2_ALGORITHM   = "PBKDF2WithHmacSHA256"
private const val AES_ALGORITHM      = "AES/GCM/NoPadding"
// Flag guardado en EncryptedSharedPreferences del dispositivo actual.
// Solo indica si el master fue configurado en ESTE dispositivo.
// No viaja en el backup (EncryptedSharedPreferences está excluida del backup).
private const val MASTER_FLAG_KEY    = "master_configured"
// Clave donde se guarda la master password cifrada por el Android Keystore
// Vive en EncryptedSharedPreferences — protegida por hardware del dispositivo
// Solo se usa en el dispositivo actual — NO viaja en backups
private const val MASTER_PWD_KEY     = "master_password_enc"

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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.notification_channel_desc) })
        }
        val tienePermiso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        if (tienePermiso) {
            nm.notify(1001, NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true).build())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        reiniciarTemporizador()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GestorApp(activityScope = lifecycleScope, activity = this,
                    onSalir = { finishAffinity() })
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (!esperandoBiometria) reiniciarTemporizador()
        return super.dispatchTouchEvent(ev)
    }
    override fun onResume()  { super.onResume();  if (!esperandoBiometria) reiniciarTemporizador() }
    override fun onPause()   { super.onPause();   if (!esperandoBiometria) pausarTemporizador() }
    override fun onDestroy() { inactivityHandler.removeCallbacks(inactivityRunnable); super.onDestroy() }

    override fun onStop() {
        super.onStop()
        // Seguridad: borrar portapapeles inmediatamente al salir de la app
        ClipboardUtils.clearNow(this)
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
            context.applicationContext, "gestor_claves_prefs",
            MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { prefsInstance = it }
    }
}

// ═══════════════════════════════════════════════════════════
// DETECCIÓN DE ESTADO — lógica corregida
// ═══════════════════════════════════════════════════════════
//
// PROBLEMA ORIGINAL: el flag MASTER_FLAG_KEY vivía en EncryptedSharedPreferences,
// que está ligada al Android Keystore del hardware. Al desinstalar/reinstalar,
// el Keystore se destruye y el flag desaparece → la app siempre pedía master.
//
// SOLUCIÓN: Tres fuentes de verdad independientes:
// 1. EncryptedSharedPreferences.MASTER_FLAG_KEY → "master configurado en ESTE dispositivo"
// 2. backup_local_invisible.dat existe          → "hay datos de otro dispositivo/instalación"
// 3. EncryptedSharedPreferences.all sin FLAG    → "hay datos en esta sesión"
//
// FLUJO CORRECTO:
// ┌─ ¿Prefs tienen datos de usuario? (excluyendo FLAG)
// │   SÍ → ¿passwordMaster en memoria está vacío?
// │   │     NO → MENU (sesión activa)
// │   NO → ¿Existe backup.dat?
// │         SÍ → INGRESAR_MASTER (recuperación cross-device)
// │         NO → CREAR_MASTER (primer uso absoluto)

data class EstadoApp(
    val tieneDatosLocales: Boolean,
    val tieneBackupDat: Boolean,
    val masterConfiguradoEnDispositivo: Boolean
)

fun detectarEstado(context: Context): EstadoApp {
    return try {
        val prefs = getPrefs(context)
        val tieneDatos = prefs.all.any { it.key != MASTER_FLAG_KEY && it.key != MASTER_PWD_KEY }
        val masterFlag = prefs.getBoolean(MASTER_FLAG_KEY, false)
        val tieneBackup = java.io.File(context.filesDir, "backup_local_invisible.dat").exists()
        EstadoApp(tieneDatos, tieneBackup, masterFlag)
    } catch (e: Exception) {
        EstadoApp(false, false, false)
    }
}

// Recuperar master password guardada en EncryptedSharedPreferences
// Retorna null si no existe (primer uso o dispositivo nuevo)
fun recuperarMasterGuardada(context: Context): String? {
    return try {
        getPrefs(context).getString(MASTER_PWD_KEY, null)
    } catch (e: Exception) { null }
}

// Guardar master password en EncryptedSharedPreferences
// Protegida por Android Keystore — solo accesible en este dispositivo
fun guardarMasterEnPrefs(context: Context, passwordMaster: String) {
    try {
        getPrefs(context).edit().putString(MASTER_PWD_KEY, passwordMaster).commit()
    } catch (e: Exception) { }
}

// ═══════════════════════════════════════════════════════════
// CIFRADO PBKDF2 + AES-256-GCM
// ═══════════════════════════════════════════════════════════
private fun derivarClave(password: String, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
    val keyBytes = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
    spec.clearPassword()
    return SecretKeySpec(keyBytes, "AES")
}

private fun cifrarRespaldo(jsonPlano: String, password: String): ByteArray {
    val salt   = ByteArray(SALT_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
    val iv     = ByteArray(IV_SIZE_BYTES).also   { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance(AES_ALGORITHM)
    cipher.init(Cipher.ENCRYPT_MODE, derivarClave(password, salt), GCMParameterSpec(GCM_TAG_LENGTH, iv))
    return salt + iv + cipher.doFinal(jsonPlano.toByteArray(Charsets.UTF_8))
}

private fun descifrarRespaldo(datos: ByteArray, password: String): String? {
    return try {
        if (datos.size < SALT_SIZE_BYTES + IV_SIZE_BYTES) return null
        val salt    = datos.copyOfRange(0, SALT_SIZE_BYTES)
        val iv      = datos.copyOfRange(SALT_SIZE_BYTES, SALT_SIZE_BYTES + IV_SIZE_BYTES)
        val cifrado = datos.copyOfRange(SALT_SIZE_BYTES + IV_SIZE_BYTES, datos.size)
        val cipher  = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, derivarClave(password, salt), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        cipher.doFinal(cifrado).toString(Charsets.UTF_8)
    } catch (e: Exception) { null }
}

// ═══════════════════════════════════════════════════════════
// OPERACIONES DE DATOS
// ═══════════════════════════════════════════════════════════
private fun actualizarRespaldoLocal(context: Context,
    prefs: android.content.SharedPreferences, passwordMaster: String) {
    try {
        val json = JSONObject()
        prefs.all.forEach { (k, v) -> if (k != MASTER_FLAG_KEY && k != MASTER_PWD_KEY) json.put(k, v.toString()) }
        java.io.File(context.filesDir, "backup_local_invisible.dat")
            .writeBytes(cifrarRespaldo(json.toString(), passwordMaster))
    } catch (e: Exception) { }
}

fun guardarClaveAsync(scope: CoroutineScope, context: Context, clave: Clave,
    passwordMaster: String, onCompletado: () -> Unit) {
    scope.launch {
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("usuario", clave.usuario); put("password", clave.password)
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

suspend fun cargarClavesAsync(context: Context): List<Clave> =
    withContext(Dispatchers.IO) {
        try {
            getPrefs(context).all.filter { it.key != MASTER_FLAG_KEY && it.key != MASTER_PWD_KEY }
                .mapNotNull { (sitio, valor) ->
                    try { val j = JSONObject(valor.toString())
                        Clave(sitio, j.optString("usuario"), j.optString("password"), j.optString("extras"))
                    } catch (e: Exception) { null }
                }.sortedBy { it.sitio.lowercase() }
        } catch (e: Exception) { emptyList() }
    }

fun eliminarClaveAsync(scope: CoroutineScope, context: Context, sitio: String,
    passwordMaster: String, onCompletado: () -> Unit) {
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

fun configurarMasterAsync(scope: CoroutineScope, context: Context,
    passwordMaster: String, onCompletado: () -> Unit) {
    scope.launch {
        withContext(Dispatchers.IO) {
            try {
                val prefs = getPrefs(context)
                prefs.edit().putBoolean(MASTER_FLAG_KEY, true).commit()
                actualizarRespaldoLocal(context, prefs, passwordMaster)
            } catch (e: Exception) { }
        }
        withContext(Dispatchers.Main) { onCompletado() }
    }
}

// Restaurar desde backup cifrado — retorna true si éxito
suspend fun restaurarDesdeBackup(context: Context, passwordMaster: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val archivo = java.io.File(context.filesDir, "backup_local_invisible.dat")
            if (!archivo.exists()) return@withContext false
            val datos = archivo.readBytes()
            if (datos.isEmpty()) return@withContext false
            val jsonPlano = descifrarRespaldo(datos, passwordMaster) ?: return@withContext false
            val json = JSONObject(jsonPlano)
            val prefs = getPrefs(context)
            val editor = prefs.edit()
            json.keys().forEach { editor.putString(it, json.getString(it)) }
            editor.putBoolean(MASTER_FLAG_KEY, true)
            editor.commit()
            true
        } catch (e: Exception) { false }
    }

fun verificarPasswordMaster(context: Context, passwordMaster: String): Boolean {
    return try {
        val archivo = java.io.File(context.filesDir, "backup_local_invisible.dat")
        if (!archivo.exists()) return true // Sin backup, aceptar (caso edge)
        val datos = archivo.readBytes()
        descifrarRespaldo(datos, passwordMaster) != null
    } catch (e: Exception) { false }
}

// ═══════════════════════════════════════════════════════════
// APP PRINCIPAL
// ═══════════════════════════════════════════════════════════
@Composable
fun GestorApp(activityScope: CoroutineScope, activity: MainActivity, onSalir: () -> Unit) {
    val context = LocalContext.current
    var pantalla by remember { mutableStateOf(Pantalla.LOGIN) }
    var claveEditando by remember { mutableStateOf<Clave?>(null) }
    var listaClaves by remember { mutableStateOf(listOf<Clave>()) }
    var cargando by remember { mutableStateOf(false) }
    // Contraseña maestra en memoria — dura solo mientras la app está activa
    // Se limpia al cerrar la app (temporizador o usuario)
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
                // LÓGICA CORRECTA: master password transparente para el usuario
                val estado = detectarEstado(context)

                when {
                    // Caso 1: hay datos + master guardada en prefs → uso normal transparente
                    // La biometría ya autenticó — recuperamos master automáticamente
                    estado.tieneDatosLocales -> {
                        val masterGuardada = recuperarMasterGuardada(context)
                        if (masterGuardada != null) {
                            // Master encontrada — sesión transparente sin pedir nada
                            passwordMaster = masterGuardada
                            pantalla = Pantalla.MENU
                        } else {
                            // Edge case: hay datos pero no hay master guardada
                            // (migración desde versión anterior sin master)
                            // Tratar como primer uso para establecer master
                            pantalla = Pantalla.CREAR_MASTER
                        }
                    }
                    // Caso 2: no hay datos locales pero hay backup cifrado
                    // → recuperación en dispositivo nuevo o tras reinstalación
                    !estado.tieneDatosLocales && estado.tieneBackupDat ->
                        pantalla = Pantalla.INGRESAR_MASTER

                    // Caso 3: primer uso absoluto — sin datos ni backup
                    else -> pantalla = Pantalla.CREAR_MASTER
                }
            }
        )

        Pantalla.CREAR_MASTER -> PantallaCrearMaster(
            onConfirmado = { master ->
                passwordMaster = master
                // Guardar master en EncryptedSharedPreferences (protegida por Keystore)
                // para que usos futuros sean transparentes tras biometría
                guardarMasterEnPrefs(context, master)
                configurarMasterAsync(activityScope, context, master) {
                    pantalla = Pantalla.MENU
                }
            }
        )

        Pantalla.INGRESAR_MASTER -> PantallaIngresarMaster(
            activityScope = activityScope,
            esRecuperacion = true,
            onConfirmado = { master ->
                passwordMaster = master
                // Guardar master tras recuperación exitosa
                // para que futuros usos sean transparentes
                guardarMasterEnPrefs(context, master)
                pantalla = Pantalla.MENU
            }
        )


        Pantalla.MENU -> PantallaMenu(
            claves = listaClaves, cargando = cargando,
            onNuevo = { claveEditando = null; pantalla = Pantalla.EDITOR },
            onEditar = { clave -> claveEditando = clave; pantalla = Pantalla.EDITOR },
            onSalir = onSalir
        )

        Pantalla.EDITOR -> PantallaEditor(
            activityScope = activityScope, claveExistente = claveEditando,
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
fun PantallaLogin(activity: MainActivity, onAutenticado: () -> Unit) {
    val context = LocalContext.current
    var mensajeError by remember { mutableStateOf("") }
    var intentando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        activity.setEsperandoBiometria(true)
        lanzarBiometria(activity,
            onExito = { activity.setEsperandoBiometria(false); intentando = false; onAutenticado() },
            onError = { e -> activity.setEsperandoBiometria(false); intentando = false; mensajeError = e })
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).systemBarsPadding(),
        contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(32.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
            Column(modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.login_title), fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Text(stringResource(R.string.login_subtitle), fontSize = 13.sp,
                    color = Color.Gray, textAlign = TextAlign.Center)
                if (mensajeError.isNotEmpty())
                    Text(mensajeError, color = Color(0xFFFF6B6B), fontSize = 13.sp,
                        textAlign = TextAlign.Center)
                Button(onClick = {
                    if (intentando) return@Button
                    intentando = true; mensajeError = ""
                    activity.setEsperandoBiometria(true)
                    val fa = context as? FragmentActivity ?: run {
                        activity.setEsperandoBiometria(false); intentando = false; return@Button }
                    lanzarBiometria(fa,
                        onExito = { activity.setEsperandoBiometria(false); intentando = false; onAutenticado() },
                        onError = { e -> activity.setEsperandoBiometria(false); intentando = false; mensajeError = e })
                }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !intentando,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))) {
                    if (intentando) CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.login_btn), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// PANTALLA CREAR CONTRASEÑA MAESTRA (primer uso)
// ═══════════════════════════════════════════════════════════
@Composable
fun PantallaCrearMaster(onConfirmado: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var mostrarPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var procesando by remember { mutableStateOf(false) }
    var mostrarAdvertencia by remember { mutableStateOf(false) }
    var passwordPendiente by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).systemBarsPadding(),
        contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
            Column(modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.master_create_title), fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Text(stringResource(R.string.master_create_subtitle), fontSize = 13.sp,
                    color = Color.Gray, textAlign = TextAlign.Center)
                OutlinedTextField(value = password, onValueChange = { password = it; error = "" },
                    label = { Text(stringResource(R.string.master_field_password), color = Color.Gray) },
                    visualTransformation = if (mostrarPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), colors = campoColores(),
                    trailingIcon = {
                        TextButton(onClick = { mostrarPass = !mostrarPass }) {
                            Text(if (mostrarPass) stringResource(R.string.master_btn_hide)
                                 else stringResource(R.string.master_btn_show),
                                color = Color(0xFF0066CC), fontSize = 12.sp)
                        }
                    })
                OutlinedTextField(value = confirmar, onValueChange = { confirmar = it; error = "" },
                    label = { Text(stringResource(R.string.master_field_confirm), color = Color.Gray) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), colors = campoColores())
                // Indicador de fortaleza
                if (password.isNotEmpty()) {
                    val f = calcularFortaleza(
                        password,
                        stringResource(R.string.master_strength_weak),
                        stringResource(R.string.master_strength_fair),
                        stringResource(R.string.master_strength_good),
                        stringResource(R.string.master_strength_strong)
                    )
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) { i ->
                            Box(modifier = Modifier.weight(1f).height(5.dp).background(
                                if (i < f.nivel) f.color else Color(0xFF333333),
                                RoundedCornerShape(2.dp)))
                        }
                    }
                    Text(f.texto, color = f.color, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                }
                // CORRECCIÓN: stringResource() no puede llamarse dentro de lambdas onClick
                val errMinLength = stringResource(R.string.master_error_min_length)
                val errMismatch  = stringResource(R.string.master_error_mismatch)

                if (error.isNotEmpty())
                    Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center)
                Button(onClick = {
                    when {
                        password.length < 8 -> error = errMinLength
                        password != confirmar -> error = errMismatch
                        else -> { passwordPendiente = password; mostrarAdvertencia = true }
                    }
                }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !procesando && password.isNotEmpty() && confirmar.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))) {
                    if (procesando) CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.master_btn_create), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ── DIÁLOGO DE ADVERTENCIA CRÍTICA ───────────────────────
    // Multiidioma: usa stringResource → se muestra en el idioma del dispositivo
    // No se puede cerrar tocando fuera — el usuario DEBE leer y confirmar
    if (mostrarAdvertencia) {
        AlertDialog(
            onDismissRequest = { /* bloqueado intencionalmente */ },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(20.dp),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("⚠️", fontSize = 48.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.master_warning_title),
                        fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = Color(0xFFFF4444), textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Bloque rojo — mensaje principal
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0808)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.master_warning_body),
                            modifier = Modifier.padding(14.dp),
                            color = Color(0xFFFF6B6B), fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    // Lista de consecuencias
                    Text(stringResource(R.string.master_warning_if_forget),
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    listOf(
                        stringResource(R.string.master_warning_item1),
                        stringResource(R.string.master_warning_item2),
                        stringResource(R.string.master_warning_item3)
                    ).forEach { item ->
                        Text(item, color = Color(0xFFCCCCCC), fontSize = 13.sp,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                    // Bloque verde — recomendación
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF082A08)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.master_warning_recommendation_title),
                                color = Color(0xFF00CC66), fontSize = 13.sp,
                                fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.master_warning_recommendation_body),
                                color = Color(0xFF00CC66), fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { mostrarAdvertencia = false; procesando = true; onConfirmado(passwordPendiente) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.master_warning_confirm),
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { mostrarAdvertencia = false },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.master_warning_back),
                        color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════
// PANTALLA INGRESAR MASTER — recuperación Y sesión
// ═══════════════════════════════════════════════════════════
// esRecuperacion=true  → restaurar datos desde backup (dispositivo nuevo/reinstalación)
// esRecuperacion=false → solo verificar y cargar en memoria (reinicio de sesión)
@Composable
fun PantallaIngresarMaster(activityScope: CoroutineScope,
    esRecuperacion: Boolean, onConfirmado: (String) -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var mostrarPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var procesando by remember { mutableStateOf(false) }

    val titulo   = if (esRecuperacion) stringResource(R.string.master_recovery_title)
                   else stringResource(R.string.master_session_title)
    val subtitulo = if (esRecuperacion) stringResource(R.string.master_recovery_subtitle)
                    else stringResource(R.string.master_session_subtitle)
    val btnTexto  = if (esRecuperacion) stringResource(R.string.master_recovery_btn)
                    else stringResource(R.string.master_session_btn)
    val errorMsg  = stringResource(R.string.master_recovery_error)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).systemBarsPadding(),
        contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
            Column(modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(subtitulo, fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
                OutlinedTextField(value = password, onValueChange = { password = it; error = "" },
                    label = { Text(stringResource(R.string.master_field_password), color = Color.Gray) },
                    visualTransformation = if (mostrarPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), colors = campoColores(),
                    trailingIcon = {
                        TextButton(onClick = { mostrarPass = !mostrarPass }) {
                            Text(if (mostrarPass) stringResource(R.string.master_btn_hide)
                                 else stringResource(R.string.master_btn_show),
                                color = Color(0xFF0066CC), fontSize = 12.sp)
                        }
                    })
                if (error.isNotEmpty())
                    Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center)
                Button(onClick = {
                    if (password.isBlank()) return@Button
                    procesando = true
                    activityScope.launch {
                        val exito = if (esRecuperacion) {
                            restaurarDesdeBackup(context, password)
                        } else {
                            // Solo verificar contraseña, no restaurar
                            withContext(Dispatchers.IO) { verificarPasswordMaster(context, password) }
                        }
                        withContext(Dispatchers.Main) {
                            procesando = false
                            if (exito) onConfirmado(password)
                            else error = errorMsg
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !procesando && password.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))) {
                    if (procesando) CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(btnTexto, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// INDICADOR DE FORTALEZA
// ═══════════════════════════════════════════════════════════
data class Fortaleza(val nivel: Int, val color: Color, val texto: String)

// CORRECCIÓN: función normal (no @Composable) — recibe strings como parámetros
// Los strings se resuelven en el contexto @Composable del llamador (PantallaCrearMaster)
fun calcularFortaleza(
    password: String,
    strWeak: String, strFair: String, strGood: String, strStrong: String
): Fortaleza {
    var p = 0
    if (password.length >= 8) p++
    if (password.length >= 12) p++
    if (password.any { it.isDigit() } && password.any { it.isLetter() }) p++
    if (password.any { !it.isLetterOrDigit() }) p++
    return when (p) {
        0, 1 -> Fortaleza(1, Color(0xFFFF4444), strWeak)
        2    -> Fortaleza(2, Color(0xFFCC7700), strFair)
        3    -> Fortaleza(3, Color(0xFF4DA6FF), strGood)
        else -> Fortaleza(4, Color(0xFF00AA66), strStrong)
    }
}

// ═══════════════════════════════════════════════════════════
// BIOMETRÍA NATIVA
// ═══════════════════════════════════════════════════════════
fun lanzarBiometria(activity: FragmentActivity, onExito: () -> Unit, onError: (String) -> Unit) {
    val mgr = BiometricManager.from(activity)
    val auth = BiometricManager.Authenticators.BIOMETRIC_STRONG or
               BiometricManager.Authenticators.DEVICE_CREDENTIAL
    when (mgr.canAuthenticate(auth)) {
        BiometricManager.BIOMETRIC_SUCCESS -> { }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            return onError(activity.getString(R.string.biometric_no_hardware))
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            return onError(activity.getString(R.string.biometric_none_enrolled))
        else -> return onError(activity.getString(R.string.biometric_unavailable))
    }
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(r); onExito() }
            override fun onAuthenticationError(code: Int, str: CharSequence) {
                super.onAuthenticationError(code, str)
                if (code == BiometricPrompt.ERROR_USER_CANCELED ||
                    code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    code == BiometricPrompt.ERROR_CANCELED) onError("")
                else onError(activity.getString(R.string.biometric_error_prefix, str))
            }
            override fun onAuthenticationFailed() { super.onAuthenticationFailed() }
        }).authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.dialog_biometric_title))
            .setSubtitle(activity.getString(R.string.dialog_biometric_subtitle))
            .setAllowedAuthenticators(auth).build())
}

// ═══════════════════════════════════════════════════════════
// PANTALLA MENÚ
// ═══════════════════════════════════════════════════════════
@Composable
fun PantallaMenu(claves: List<Clave>, cargando: Boolean,
    onNuevo: () -> Unit, onEditar: (Clave) -> Unit, onSalir: () -> Unit) {
    var busqueda by remember { mutableStateOf("") }
    val filtradas by remember(claves, busqueda) {
        derivedStateOf {
            if (busqueda.isEmpty()) claves
            else claves.filter { it.sitio.contains(busqueda, ignoreCase = true) }
        }
    }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))
        .statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), fontSize = 20.sp,
                fontWeight = FontWeight.Bold, color = Color.White)
            Button(onClick = onSalir,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB2222)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)) {
                Text(stringResource(R.string.menu_btn_salir), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        OutlinedTextField(value = busqueda, onValueChange = { busqueda = it },
            label = { Text(stringResource(R.string.menu_search_hint)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, colors = campoColores())
        when {
            cargando -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF0066CC)) }
            claves.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text(stringResource(R.string.menu_empty_list), color = Color.Gray,
                    textAlign = TextAlign.Center, fontSize = 14.sp) }
            filtradas.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text(stringResource(R.string.menu_no_results, busqueda), color = Color.Gray,
                    textAlign = TextAlign.Center, fontSize = 14.sp) }
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtradas, key = { it.sitio }) { clave ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onEditar(clave) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(2.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp),
                            Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(clave.sitio, color = Color.White, fontSize = 16.sp,
                                fontWeight = FontWeight.Medium)
                            Text("›", color = Color.Gray, fontSize = 20.sp)
                        }
                    }
                }
            }
        }
        Button(onClick = onNuevo,
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006633)),
            shape = RoundedCornerShape(12.dp)) {
            Text(stringResource(R.string.menu_btn_nueva), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// PANTALLA EDITOR
// ═══════════════════════════════════════════════════════════
@Composable
fun PantallaEditor(activityScope: CoroutineScope, claveExistente: Clave?,
    passwordMaster: String, onGuardar: () -> Unit, onVolver: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val esNuevo = claveExistente == null
    var sitio    by remember { mutableStateOf(claveExistente?.sitio    ?: "") }
    var usuario  by remember { mutableStateOf(claveExistente?.usuario  ?: "") }
    var password by remember { mutableStateOf(claveExistente?.password ?: "") }
    var extras   by remember { mutableStateOf(claveExistente?.extras   ?: "") }
    var modoEdicion         by remember { mutableStateOf(esNuevo) }
    var mostrarPassword     by remember { mutableStateOf(false) }
    var guardando           by remember { mutableStateOf(false) }
    var mostrarConfGuardar  by remember { mutableStateOf(false) }
    var mostrarConfEliminar by remember { mutableStateOf(false) }
    val lineasExtras by remember(extras) {
        derivedStateOf { extras.split("\n").filter { it.isNotBlank() } }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))
        .statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {

        // ── ZONA SCROLLABLE ──────────────────────────────────
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (esNuevo) stringResource(R.string.editor_title_nuevo) else "🔑 ${claveExistente?.sitio}",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                textAlign = TextAlign.Center)
            CampoEditor(stringResource(R.string.editor_label_sitio), sitio, modoEdicion,
                { sitio = it }, { clipboard.setText(AnnotatedString(sitio)); ClipboardUtils.scheduleClear(context) },
                { clipboard.getText()?.text?.let { sitio = it } })
            CampoEditor(stringResource(R.string.editor_label_usuario), usuario, modoEdicion,
                { usuario = it }, { clipboard.setText(AnnotatedString(usuario)); ClipboardUtils.scheduleClear(context) },
                { clipboard.getText()?.text?.let { usuario = it } })
            OutlinedTextField(value = password, onValueChange = { if (modoEdicion) password = it },
                label = { Text(stringResource(R.string.editor_label_pass), color = Color.Gray) },
                enabled = modoEdicion,
                visualTransformation = if (mostrarPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), colors = campoColores(),
                trailingIcon = {
                    Row(Modifier.padding(end = 4.dp), Arrangement.spacedBy(4.dp), Alignment.CenterVertically) {
                        TextButton(onClick = { mostrarPassword = !mostrarPassword }) {
                            Text(if (mostrarPassword) stringResource(R.string.editor_btn_ocultar)
                                 else stringResource(R.string.editor_btn_ver),
                                color = Color(0xFF0066CC), fontSize = 12.sp) }
                        if (!modoEdicion) TextButton(onClick = { clipboard.setText(AnnotatedString(password)); ClipboardUtils.scheduleClear(context) }) {
                            Text(stringResource(R.string.editor_btn_copiar),
                                color = Color(0xFFCC7700), fontSize = 12.sp) }
                    }
                })
            if (modoEdicion) {
                OutlinedTextField(value = extras, onValueChange = { extras = it },
                    label = { Text(stringResource(R.string.editor_label_extras_edit), color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(), maxLines = 4, colors = campoColores(),
                    trailingIcon = {
                        TextButton(onClick = { clipboard.getText()?.text?.let { extras = it } }) {
                            Text(stringResource(R.string.editor_btn_pegar),
                                color = Color(0xFF00AA66), fontSize = 12.sp) }
                    })
            } else {
                if (lineasExtras.isEmpty()) {
                    OutlinedTextField(value = "", onValueChange = {},
                        label = { Text(stringResource(R.string.editor_label_extras_view), color = Color.Gray) },
                        enabled = false, modifier = Modifier.fillMaxWidth(), colors = campoColores())
                } else {
                    lineasExtras.forEachIndexed { i, linea ->
                        OutlinedTextField(value = linea, onValueChange = {},
                            label = { Text("${stringResource(R.string.editor_label_extras_view)} (${i + 1})", color = Color.Gray) },
                            enabled = false, modifier = Modifier.fillMaxWidth(), colors = campoColores(),
                            trailingIcon = {
                                TextButton(onClick = { clipboard.setText(AnnotatedString(linea)); ClipboardUtils.scheduleClear(context) }) {
                                    Text(stringResource(R.string.editor_btn_copiar),
                                        color = Color(0xFFCC7700), fontSize = 12.sp) }
                            })
                    }
                }
            }
        }

        // ── ZONA FIJA — botones siempre visibles ─────────────
        HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 1.dp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onVolver, Modifier.weight(1f).height(50.dp)) {
                Text(stringResource(R.string.editor_btn_volver), color = Color.White) }
            if (!esNuevo && !modoEdicion)
                Button(onClick = { modoEdicion = true }, Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC7700))) {
                    Text(stringResource(R.string.editor_btn_modificar), fontWeight = FontWeight.Bold) }
            if (modoEdicion)
                Button(onClick = { if (sitio.isNotBlank() && !guardando) mostrarConfGuardar = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                    enabled = !guardando && sitio.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066CC))) {
                    if (guardando) CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.editor_btn_guardar), fontWeight = FontWeight.Bold) }
        }
        if (!esNuevo && !modoEdicion)
            TextButton(onClick = { mostrarConfEliminar = true },
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(stringResource(R.string.editor_link_eliminar),
                    color = Color(0xFFFF4444), fontSize = 14.sp) }
    }

    if (mostrarConfGuardar) {
        AlertDialog(onDismissRequest = { if (!guardando) mostrarConfGuardar = false },
            title = { Text(stringResource(R.string.dialog_confirm_title), fontWeight = FontWeight.Bold) },
            text  = { Text(stringResource(R.string.dialog_confirm_save_text, sitio)) },
            confirmButton = {
                TextButton(onClick = {
                    if (!guardando) { guardando = true
                        guardarClaveAsync(activityScope, context,
                            Clave(sitio, usuario, password, extras), passwordMaster) {
                            guardando = false; mostrarConfGuardar = false; onGuardar() } }
                }) { Text(stringResource(R.string.dialog_btn_si), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { if (!guardando) mostrarConfGuardar = false }) {
                    Text(stringResource(R.string.dialog_btn_no), color = Color.Gray) }
            })
    }

    if (mostrarConfEliminar) {
        AlertDialog(onDismissRequest = { mostrarConfEliminar = false },
            title = { Text(stringResource(R.string.dialog_delete_title),
                fontWeight = FontWeight.Bold, color = Color(0xFFFF4444)) },
            text = { Text(stringResource(R.string.dialog_delete_text, claveExistente?.sitio ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    claveExistente?.sitio?.let { s ->
                        eliminarClaveAsync(activityScope, context, s, passwordMaster) {
                            mostrarConfEliminar = false; onVolver() } }
                }) { Text(stringResource(R.string.dialog_btn_eliminar),
                    color = Color(0xFFFF4444), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfEliminar = false }) {
                    Text(stringResource(R.string.dialog_btn_cancelar), color = Color.Gray) }
            })
    }
}

// ═══════════════════════════════════════════════════════════
// COMPONENTES REUTILIZABLES
// ═══════════════════════════════════════════════════════════
@Composable
fun CampoEditor(label: String, valor: String, habilitado: Boolean,
    onCambio: (String) -> Unit, onCopiar: () -> Unit, onPegar: () -> Unit) {
    OutlinedTextField(value = valor, onValueChange = { if (habilitado) onCambio(it) },
        label = { Text(label, color = Color.Gray) }, enabled = habilitado,
        singleLine = true, modifier = Modifier.fillMaxWidth(), colors = campoColores(),
        trailingIcon = {
            if (habilitado) TextButton(onClick = onPegar) {
                Text(stringResource(R.string.editor_btn_pegar), color = Color(0xFF00AA66), fontSize = 12.sp)
            } else TextButton(onClick = onCopiar) {
                Text(stringResource(R.string.editor_btn_copy_short), color = Color(0xFFCC7700), fontSize = 12.sp)
            }
        })
}

@Composable
fun campoColores() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    disabledTextColor = Color(0xFFCCCCCC), focusedLabelColor = Color(0xFF0066CC),
    unfocusedLabelColor = Color.Gray, disabledLabelColor = Color.Gray,
    focusedBorderColor = Color(0xFF0066CC), unfocusedBorderColor = Color(0xFF444444),
    disabledBorderColor = Color(0xFF333333))
