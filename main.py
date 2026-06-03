import json
import os
import time
import base64
import hashlib
from kivy.app import App
from kivy.core.window import Window
from kivy.core.clipboard import Clipboard
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.anchorlayout import AnchorLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.popup import Popup
from kivy.graphics import Color, RoundedRectangle
from kivy.clock import Clock
from Crypto.Cipher import AES
from kivy.utils import platform

# ============================================================
# PUENTE ANDROID — solo se importa si corremos en Android
# ============================================================
if platform == 'android':
    from jnius import autoclass, cast
    from android import activity as android_activity  # módulo oficial de p4a

    PythonActivity  = autoclass('org.kivy.android.PythonActivity')
    Context         = autoclass('android.content.Context')
    Build           = autoclass('android.os.Build')

    # BiometricPrompt (AndroidX) — disponible desde API 28
    BiometricPrompt             = autoclass('androidx.biometric.BiometricPrompt')
    BiometricManager            = autoclass('androidx.biometric.BiometricManager')
    PromptInfo                  = autoclass('androidx.biometric.BiometricPrompt$PromptInfo')
    PromptInfoBuilder           = autoclass('androidx.biometric.BiometricPrompt$PromptInfo$Builder')

    # Para ejecutar en el hilo de la UI de Android
    Executor        = autoclass('java.util.concurrent.Executor')
    Executors       = autoclass('java.util.concurrent.Executors')

    # Fallback PIN/Pattern para Android < 28 o dispositivos sin biometría
    KeyguardManager = autoclass('android.app.KeyguardManager')

    ANDROID_VERSION = Build.VERSION.SDK_INT  # número entero, ej. 30 = Android 11


# ============================================================
# SEGURIDAD: derivación de clave con PBKDF2 (NO hardcodeada)
# ============================================================
_PIN_SALT = b'\xfa\x3c\x9b\x12\x77\xae\x55\xd1'   # sal fija del dispositivo

def derivar_clave_aes(pin: str) -> bytes:
    """Deriva 16 bytes de clave AES desde el PIN usando PBKDF2-HMAC-SHA256."""
    return hashlib.pbkdf2_hmac('sha256', pin.encode('utf-8'), _PIN_SALT, 200_000)[:16]


# PIN de respaldo manual (solo para desarrollo en Linux y emergencias)
_PIN_BACKUP = "1975"
CLAVE_AES_GLOBAL = derivar_clave_aes(_PIN_BACKUP)


# ============================================================
# WIDGET DE INPUT ESTILIZADO
# ============================================================
class EstiloInput(TextInput):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.background_normal           = ''
        self.background_disabled_normal  = ''
        self.background_color            = (0.15, 0.15, 0.15, 1)
        self.foreground_color            = (1, 1, 1, 1)
        self.disabled_foreground_color   = (0.8, 0.8, 0.8, 1)
        self.font_size                   = '17sp'
        self.padding                     = [15, 15, 15, 15]
        self.size_hint_y                 = None
        self.height                      = '55dp'
        self.bind(text=self._reset_inactividad)

    def _reset_inactividad(self, instance, value):
        app = App.get_running_app()
        if app:
            app.ultimo_toque = time.time()


# ============================================================
# APP PRINCIPAL
# ============================================================
class GestorApp(App):

    # ----------------------------------------------------------
    # CICLO DE VIDA
    # ----------------------------------------------------------
    def build(self):
        self.ruta_carpeta  = self.user_data_dir
        self.archivo_json  = os.path.join(self.ruta_carpeta, "mis_claves_dropbox.json")
        self.clave_aes     = CLAVE_AES_GLOBAL   # se reemplaza tras login exitoso si se usa PIN custom
        self.ultimo_toque  = time.time()
        self.datos         = {}                 # se carga DESPUÉS del login

        if not os.path.exists(self.ruta_carpeta):
            os.makedirs(self.ruta_carpeta)

        Window.bind(on_touch_down=self.registrar_toque_global)
        Clock.schedule_interval(self.verificar_inactividad, 1)

        self.contenedor_principal = AnchorLayout(anchor_x='center', anchor_y='center')
        self.pantalla_login()
        return self.contenedor_principal

    # ----------------------------------------------------------
    # AUTENTICACIÓN — ESTRATEGIA POR CAPAS
    # ----------------------------------------------------------
    def iniciar_autenticacion_android(self):
        """
        Estrategia en capas:
          1. Android >= 28: BiometricPrompt (huella + PIN del sistema como fallback).
          2. Android < 28:  KeyguardManager Intent (PIN/Patrón).
          3. Sin bloqueo:   Acceso directo (advertencia al usuario).
        """
        try:
            mgr = cast(
                'androidx.biometric.BiometricManager',
                PythonActivity.mActivity.getSystemService('biometric')
            )

            # Verificar disponibilidad biométrica
            estado = mgr.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )

            if estado == BiometricManager.BIOMETRIC_SUCCESS:
                self._lanzar_biometric_prompt()
            elif ANDROID_VERSION < 28:
                self._lanzar_keyguard_intent()
            else:
                # Dispositivo sin ningún bloqueo configurado
                self._mostrar_advertencia_sin_bloqueo()

        except Exception as e:
            print(f"[AUTH] Error iniciando autenticación: {e}")
            self.lbl_info.text = "Error biométrico. Use PIN manual."
            self.lbl_info.color = (1, 0.6, 0, 1)

    def _lanzar_biometric_prompt(self):
        """
        Usa androidx.biometric.BiometricPrompt — la API moderna y correcta.
        Soporta huella dactilar, reconocimiento facial y PIN/patrón del sistema.
        """
        try:
            actividad = PythonActivity.mActivity
            executor  = Executors.newSingleThreadExecutor()

            # Callback interno de Java que llamamos desde Python
            callback = _BiometricCallback(
                on_success=self._autenticacion_exitosa,
                on_error=self._autenticacion_fallida
            )

            prompt = BiometricPrompt(actividad, executor, callback)

            info = (PromptInfoBuilder()
                    .setTitle("Gestor de Claves")
                    .setSubtitle("Verifica tu identidad")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build())

            # authenticate() DEBE llamarse en el hilo principal de Android
            actividad.runOnUiThread(lambda: prompt.authenticate(info))

        except Exception as e:
            print(f"[AUTH] Error en BiometricPrompt: {e}")
            self.lbl_info.text = "Fallo biométrico. Use PIN."
            self.lbl_info.color = (1, 0.6, 0, 1)

    def _lanzar_keyguard_intent(self):
        """Fallback para Android < 10 usando KeyguardManager Intent."""
        try:
            actividad = PythonActivity.mActivity
            km = cast('android.app.KeyguardManager',
                      actividad.getSystemService(Context.KEYGUARD_SERVICE))

            if km.isDeviceSecure():
                intent = km.createConfirmDeviceCredentialIntent(
                    "Gestor de Claves",
                    "Ingresa el PIN o patrón de tu dispositivo"
                )
                android_activity.bind(on_activity_result=self._resultado_keyguard_intent)
                actividad.startActivityForResult(intent, 1001)
            else:
                self._mostrar_advertencia_sin_bloqueo()
        except Exception as e:
            print(f"[AUTH] Error Keyguard: {e}")

    def _resultado_keyguard_intent(self, request_code, result_code, intent_data):
        android_activity.unbind(on_activity_result=self._resultado_keyguard_intent)
        if request_code == 1001:
            if result_code == -1:   # RESULT_OK
                Clock.schedule_once(lambda dt: self._autenticacion_exitosa(), 0)
            else:
                Clock.schedule_once(lambda dt: self._autenticacion_fallida("Cancelado"), 0)

    def _autenticacion_exitosa(self, *args):
        """Llamado desde hilo Java; usar Clock para regresar al hilo de Kivy."""
        def _en_kivy(dt):
            self.ultimo_toque = time.time()
            self.datos = self.cargar_datos()
            self.mostrar_menu()
        Clock.schedule_once(_en_kivy, 0)

    def _autenticacion_fallida(self, motivo="", *args):
        def _en_kivy(dt):
            self.lbl_info.text = f"AUTENTICACIÓN FALLIDA: {motivo}"
            self.lbl_info.color = (1, 0, 0, 1)
        Clock.schedule_once(_en_kivy, 0)

    def _mostrar_advertencia_sin_bloqueo(self):
        def _en_kivy(dt):
            self.lbl_info.text = "⚠ Sin bloqueo de pantalla configurado."
            self.lbl_info.color = (1, 0.8, 0, 1)
            # Aún así permite entrar con PIN de respaldo
        Clock.schedule_once(_en_kivy, 0)

    # ----------------------------------------------------------
    # ENCRIPTACIÓN AES-EAX
    # ----------------------------------------------------------
    def encriptar_texto(self, texto_plano: str) -> str:
        if not texto_plano:
            return ""
        try:
            cipher = AES.new(self.clave_aes, AES.MODE_EAX)
            texto_cifrado, tag = cipher.encrypt_and_digest(texto_plano.encode('utf-8'))
            datos = cipher.nonce + tag + texto_cifrado
            return base64.b64encode(datos).decode('utf-8')
        except Exception as e:
            print(f"[AES] Error encriptando: {e}")
            return ""

    def desencriptar_texto(self, texto_b64: str) -> str:
        if not texto_b64:
            return ""
        try:
            datos = base64.b64decode(texto_b64.encode('utf-8'))
            nonce, tag, cifrado = datos[:16], datos[16:32], datos[32:]
            cipher = AES.new(self.clave_aes, AES.MODE_EAX, nonce=nonce)
            return cipher.decrypt_and_verify(cifrado, tag).decode('utf-8')
        except Exception as e:
            print(f"[AES] Error desencriptando: {e}")
            return "[Error al descifrar]"

    # ----------------------------------------------------------
    # INACTIVIDAD
    # ----------------------------------------------------------
    def registrar_toque_global(self, window, touch):
        self.ultimo_toque = time.time()

    def verificar_inactividad(self, dt):
        # No cerrar si estamos en la pantalla de login
        if not self.contenedor_principal.children:
            return
        hijo = self.contenedor_principal.children[0]
        if getattr(hijo, 'es_login', False):
            return
        if time.time() - self.ultimo_toque > 30:
            self.ultimo_toque = time.time()
            self.pantalla_login()
            self.toast("Sesión cerrada por inactividad")

    # ----------------------------------------------------------
    # PANTALLA DE LOGIN
    # ----------------------------------------------------------
    def pantalla_login(self):
        self.contenedor_principal.clear_widgets()

        tarjeta = BoxLayout(
            orientation='vertical',
            size_hint=(0.85, None),
            height='300dp',
            padding='30dp',
            spacing='15dp'
        )
        tarjeta.es_login = True

        with tarjeta.canvas.before:
            Color(0.1, 0.1, 0.1, 1)
            self.rect_log = RoundedRectangle(pos=tarjeta.pos, size=tarjeta.size, radius=[25])
        tarjeta.bind(pos=self._update_rect, size=self._update_rect)

        self.lbl_info = Label(
            text="🔐  GESTOR DE CLAVES",
            size_hint_y=None, height='35dp',
            bold=True, font_size='18sp'
        )
        lbl_sub = Label(
            text="Use huella dactilar o PIN del sistema",
            size_hint_y=None, height='20dp',
            font_size='13sp', color=(0.6, 0.6, 0.6, 1)
        )
        self.input_pin = EstiloInput(
            hint_text="PIN de respaldo (manual)",
            password=True, halign="center"
        )

        texto_btn = "AUTENTICAR CON HUELLA / PIN" if platform == 'android' else "DESBLOQUEAR (DESARROLLO)"
        btn_entrar = Button(
            text=texto_btn,
            size_hint_y=None, height='60dp',
            background_normal='', background_color=(0, 0.4, 0.8, 1),
            bold=True
        )
        btn_entrar.bind(on_press=self._ejecutar_login)

        tarjeta.add_widget(self.lbl_info)
        tarjeta.add_widget(lbl_sub)
        tarjeta.add_widget(self.input_pin)
        tarjeta.add_widget(btn_entrar)
        self.contenedor_principal.add_widget(tarjeta)

        # En Android lanzar biometría automáticamente al abrir
        if platform == 'android':
            Clock.schedule_once(lambda dt: self.iniciar_autenticacion_android(), 0.5)

    def _ejecutar_login(self, instance):
        if platform == 'android':
            # Primero verificar PIN manual; si no coincide, lanzar biometría
            if self.input_pin.text == _PIN_BACKUP:
                self.datos = self.cargar_datos()
                self.mostrar_menu()
            else:
                self.iniciar_autenticacion_android()
        else:
            # Modo desarrollo Linux
            if self.input_pin.text == _PIN_BACKUP:
                self.datos = self.cargar_datos()
                self.mostrar_menu()
            else:
                self.lbl_info.text = "PIN INCORRECTO"
                self.lbl_info.color = (1, 0, 0, 1)

    def _update_rect(self, instance, value):
        self.rect_log.pos  = instance.pos
        self.rect_log.size = instance.size

    # ----------------------------------------------------------
    # MENÚ PRINCIPAL
    # ----------------------------------------------------------
    def mostrar_menu(self, filtro=""):
        self.contenedor_principal.clear_widgets()

        margen_inferior = '65dp' if platform == 'android' else '10dp'
        menu_layout = BoxLayout(
            orientation='vertical',
            padding=['10dp', '10dp', '10dp', margen_inferior],
            spacing='10dp'
        )

        self.txt_buscar = EstiloInput(hint_text="🔍 Buscar...")
        self.txt_buscar.bind(text=lambda ins, val: self.renderizar_items(val))

        scroll = ScrollView()
        self.lista_ui = BoxLayout(orientation='vertical', spacing=8, size_hint_y=None)
        self.lista_ui.bind(minimum_height=self.lista_ui.setter('height'))
        self.renderizar_items(filtro)
        scroll.add_widget(self.lista_ui)

        btns = BoxLayout(size_hint_y=None, height='60dp', spacing=10)
        btns.add_widget(Button(
            text="+ NUEVO",
            on_press=lambda x: self.abrir_editor(None, None),
            background_normal='', background_color=(0, 0.5, 0.3, 1), bold=True
        ))
        btns.add_widget(Button(
            text="SALIR",
            on_press=self.stop,
            background_normal='', background_color=(0.6, 0.1, 0.1, 1), bold=True
        ))

        menu_layout.add_widget(self.txt_buscar)
        menu_layout.add_widget(scroll)
        menu_layout.add_widget(btns)
        self.contenedor_principal.add_widget(menu_layout)

    def renderizar_items(self, filtro=""):
        self.lista_ui.clear_widgets()
        for sitio in sorted(self.datos.keys()):
            if filtro.lower() in sitio.lower():
                btn = Button(
                    text=f"  {sitio}",
                    size_hint_y=None, height='55dp',
                    halign='left', valign='middle',
                    background_normal='', background_color=(0.12, 0.12, 0.12, 1)
                )
                btn.bind(size=btn.setter('text_size'),
                         on_press=lambda x, s=sitio: self.abrir_editor(None, s))
                self.lista_ui.add_widget(btn)

    # ----------------------------------------------------------
    # EDITOR DE ENTRADAS
    # ----------------------------------------------------------
    def abrir_editor(self, instance, sitio_key=None):
        self.contenedor_principal.clear_widgets()
        scroll_editor = ScrollView(do_scroll_x=False)

        margen_inferior = '65dp' if platform == 'android' else '15dp'
        editor_layout = BoxLayout(
            orientation='vertical',
            padding=['15dp', '15dp', '15dp', margen_inferior],
            spacing='10dp', size_hint_y=None
        )
        editor_layout.bind(minimum_height=editor_layout.setter('height'))

        es_nuevo   = sitio_key is None
        bloqueado  = not es_nuevo
        modo_clip  = "PEGAR" if es_nuevo else "COPIAR"

        def _dec(campo):
            return self.desencriptar_texto(
                self.datos.get(sitio_key, {}).get(campo, "")
            ) if sitio_key else ""

        self.in_sitio = self._crear_fila(editor_layout, "SITIO",    sitio_key or "", bloqueado, modo_clip)
        self.in_user  = self._crear_fila(editor_layout, "USUARIO",  _dec('user'),    bloqueado, modo_clip)
        self.in_pass  = self._crear_fila(editor_layout, "PASS",     _dec('pass'),    bloqueado, modo_clip)
        self.in_extra = self._crear_fila(editor_layout, "EXTRAS",   _dec('extra'),   bloqueado, modo_clip, multiline=True)

        self.area_botones = BoxLayout(size_hint_y=None, height='60dp', spacing=10, padding=[0, 10, 0, 0])
        self._botones_lectura(sitio_key)

        editor_layout.add_widget(self.area_botones)
        scroll_editor.add_widget(editor_layout)
        self.contenedor_principal.add_widget(scroll_editor)

    def _crear_fila(self, parent, label_text, valor, bloqueado, modo, multiline=False):
        parent.add_widget(Label(
            text=label_text, size_hint_y=None, height='20dp',
            font_size='12sp', color=(0.5, 0.5, 0.5, 1)
        ))
        alto = '100dp' if multiline else '55dp'
        fila = BoxLayout(size_hint_y=None, height=alto, spacing=5)
        campo = EstiloInput(text=valor, disabled=bloqueado, multiline=multiline)
        if multiline:
            campo.height = '100dp'
        btn = Button(
            text=modo, size_hint_x=None, width='80dp',
            background_normal='', background_color=(0.2, 0.2, 0.2, 1)
        )
        btn.bind(on_press=lambda x: self._clipboard(campo, modo))
        fila.add_widget(campo)
        fila.add_widget(btn)
        parent.add_widget(fila)
        return campo

    def _clipboard(self, campo, modo):
        self.ultimo_toque = time.time()
        if modo == "COPIAR":
            if campo.text:
                Clipboard.copy(campo.text)
                self.toast("Copiado al portapapeles")
        else:
            campo.text = Clipboard.paste()
            self.toast("Pegado")

    def _botones_lectura(self, sitio_key):
        self.area_botones.clear_widgets()
        btn_back = Button(
            text="VOLVER",
            on_press=lambda x: self.mostrar_menu(),
            background_color=(0.25, 0.25, 0.25, 1)
        )
        if sitio_key:
            btn_mod = Button(text="MODIFICAR", on_press=self._activar_edicion, background_color=(0.8, 0.5, 0, 1))
            self.area_botones.add_widget(btn_back)
            self.area_botones.add_widget(btn_mod)
        else:
            btn_save = Button(
                text="GUARDAR",
                on_press=lambda x: self._confirmar_guardado(sitio_key),
                background_color=(0, 0.4, 0.8, 1)
            )
            self.area_botones.add_widget(btn_back)
            self.area_botones.add_widget(btn_save)

    def _activar_edicion(self, instance):
        for campo in (self.in_sitio, self.in_user, self.in_pass, self.in_extra):
            campo.disabled = False
        self.area_botones.clear_widgets()
        btn_can = Button(
            text="CANCELAR",
            on_press=lambda x: self.abrir_editor(None, self.in_sitio.text),
            background_color=(0.5, 0, 0, 1)
        )
        btn_ok = Button(
            text="CONFIRMAR",
            on_press=lambda x: self._confirmar_guardado(self.in_sitio.text),
            background_color=(0, 0.4, 0.8, 1)
        )
        self.area_botones.add_widget(btn_can)
        self.area_botones.add_widget(btn_ok)

    def _confirmar_guardado(self, vieja_key):
        layout  = BoxLayout(orientation='vertical', padding=20, spacing=20)
        btns    = BoxLayout(spacing=10, size_hint_y=None, height='50dp')
        popup   = Popup(title='CONFIRMACIÓN', content=layout, size_hint=(0.7, 0.3), auto_dismiss=False)
        btn_no  = Button(text="NO",  on_press=lambda x: popup.dismiss(), background_color=(0.7, 0, 0, 1))
        btn_si  = Button(text="SÍ",  on_press=lambda x: self._ejecutar_guardado(vieja_key, popup), background_color=(0, 0.6, 0, 1))
        btns.add_widget(btn_no)
        btns.add_widget(btn_si)
        layout.add_widget(Label(text="¿Guardar cambios?"))
        layout.add_widget(btns)
        popup.open()

    def _ejecutar_guardado(self, vieja_key, popup):
        nuevo_sitio = self.in_sitio.text.strip()
        if not nuevo_sitio:
            self.toast("El campo SITIO no puede estar vacío")
            return

        if vieja_key and vieja_key in self.datos and vieja_key != nuevo_sitio:
            del self.datos[vieja_key]

        self.datos[nuevo_sitio] = {
            "user":  self.encriptar_texto(self.in_user.text),
            "pass":  self.encriptar_texto(self.in_pass.text),
            "extra": self.encriptar_texto(self.in_extra.text),
        }

        with open(self.archivo_json, "w") as f:
            json.dump(self.datos, f, indent=4)

        popup.dismiss()
        self.mostrar_menu()

    # ----------------------------------------------------------
    # PERSISTENCIA
    # ----------------------------------------------------------
    def cargar_datos(self) -> dict:
        if os.path.exists(self.archivo_json):
            try:
                with open(self.archivo_json, "r") as f:
                    return json.load(f)
            except Exception as e:
                print(f"[JSON] Error cargando datos: {e}")
        return {}

    # ----------------------------------------------------------
    # UTILIDADES UI
    # ----------------------------------------------------------
    def toast(self, mensaje: str):
        pop = Popup(
            title='', content=Label(text=mensaje),
            size_hint=(0.65, 0.15), auto_dismiss=True
        )
        pop.open()
        Clock.schedule_once(lambda dt: pop.dismiss(), 1.5)


# ============================================================
# CALLBACK DE BIOMETRÍA — clase Java-compatible via jnius
# ============================================================
if platform == 'android':
    from jnius import PythonJavaClass, java_method

    class _BiometricCallback(PythonJavaClass):
        """
        Implementa androidx.biometric.BiometricPrompt.AuthenticationCallback
        desde Python usando jnius.PythonJavaClass.
        """
        __javainterfaces__ = ['androidx/biometric/BiometricPrompt$AuthenticationCallback']
        __javacontext__    = 'app'

        def __init__(self, on_success, on_error):
            super().__init__()
            self._on_success = on_success
            self._on_error   = on_error

        @java_method('(Landroidx/biometric/BiometricPrompt$AuthenticationResult;)V')
        def onAuthenticationSucceeded(self, result):
            self._on_success()

        @java_method('(ILjava/lang/CharSequence;)V')
        def onAuthenticationError(self, error_code, err_string):
            self._on_error(str(err_string))

        @java_method('()V')
        def onAuthenticationFailed(self):
            # Intento fallido pero el diálogo sigue abierto; no cerrar sesión
            pass
else:
    # Stub vacío para que el import no falle en Linux
    class _BiometricCallback:
        def __init__(self, **kwargs): pass


# ============================================================
if __name__ == '__main__':
    GestorApp().run()
