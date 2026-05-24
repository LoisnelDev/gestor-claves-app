import json
import os
import time
import base64
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

# Enlazar con las APIs de Java solo si estamos corriendo dentro de Android
if platform == 'android':
    from jnius import autoclass, cast
    PythonActivity = autoclass('org.kivy.android.PythonActivity')
    Intent = autoclass('android.content.Intent')
    Context = autoclass('android.content.Context')
    KeyguardManager = autoclass('android.app.KeyguardManager')

# --- CONFIGURACIÓN DE SEGURIDAD ---
_SISTEMA_SEG = {"p_m": "1975"}
CLAVE_AES = _SISTEMA_SEG["p_m"].zfill(16)[:16].encode('utf-8')

class EstiloInput(TextInput):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.background_normal = ''
        self.background_disabled_normal = ''
        self.background_color = (0.15, 0.15, 0.15, 1)
        self.foreground_color = (1, 1, 1, 1)
        self.disabled_foreground_color = (0.8, 0.8, 0.8, 1)
        self.font_size = '17sp'
        self.padding = [15, 15, 15, 15]
        self.size_hint_y = None
        self.height = '55dp'
        self.bind(text=self.reiniciar_tiempo_por_escritura)

    def reiniciar_tiempo_por_escritura(self, instance, value):
        app = App.get_running_app()
        if app:
            app.ultimo_toque = time.time()

class GestorApp(App):
    def build(self):
        self.ruta_carpeta = self.user_data_dir
        self.archivo_json = os.path.join(self.ruta_carpeta, "mis_claves_dropbox.json")
        
        if not os.path.exists(self.ruta_carpeta):
            os.makedirs(self.ruta_carpeta)

        self.datos = self.cargar_datos()
        self.ultimo_toque = time.time()
        
        Window.bind(on_touch_down=self.registrar_toque_global)
        Clock.schedule_interval(self.verificar_inactividad, 1)
        
        self.contenedor_principal = AnchorLayout(anchor_x='center', anchor_y='center')
        return self.pantalla_login()

    # --- PUENTE NATIVO SEGURO Y COMPATIBLE ---
    def autenticar_con_android(self):
        """ Invoca el sistema de autenticación nativo usando la API base sin dependencias externas """
        try:
            actividad_actual = PythonActivity.mActivity
            servicio_seguridad = actividad_actual.getSystemService(Context.KEYGUARD_SERVICE)
            gestor_seguridad = cast('android.app.KeyguardManager', servicio_seguridad)
            
            if gestor_seguridad and gestor_seguridad.isDeviceSecure():
                intent_autenticacion = gestor_seguridad.createConfirmDeviceCredentialIntent(
                    "Gestor de Claves", 
                    "Verifica tu identidad usando la huella digital o el PIN de tu celular"
                )
                if intent_autenticacion:
                    PythonActivity.bind(on_activity_result=self.procesar_respuesta_biometrica)
                    actividad_actual.startActivityForResult(intent_autenticacion, 1001)
                    return
            
            # Si no tiene bloqueo configurado, entra al menú principal directo
            self.mostrar_menu()
        except Exception as e:
            print(f"Error al llamar la API nativa: {e}")
            self.lbl_info.text = "Sensor en espera. Use PIN manual."
            self.lbl_info.color = (1, 0.6, 0, 1)

    def procesar_respuesta_biometrica(self, request_code, result_code, intent_data):
        if request_code == 1001:
            if result_code == -1: # RESULT_OK nativo
                self.ultimo_toque = time.time()
                self.datos = self.cargar_datos()
                self.mostrar_menu()
            else:
                self.lbl_info.text = "AUTENTICACIÓN CANCELADA / FALLIDA"
                self.lbl_info.color = (1, 0, 0, 1)

    # --- ENCRIPTACIÓN AES ---
    def encriptar_texto(self, texto_plano):
        if not texto_plano: return ""
        try:
            cipher = AES.new(CLAVE_AES, AES.MODE_EAX)
            nonce = cipher.nonce
            texto_cifrado, tag = cipher.encrypt_and_digest(texto_plano.encode('utf-8'))
            datos_completos = nonce + tag + texto_cifrado
            return base64.b64encode(datos_completos).decode('utf-8')
        except Exception as e:
            return ""

    def desencriptar_texto(self, texto_encriptado_b64):
        if not texto_encriptado_b64: return ""
        try:
            datos_completos = base64.b64decode(texto_encriptado_b64.encode('utf-8'))
            nonce, tag, texto_cifrado = datos_completos[:16], datos_completos[16:32], datos_completos[32:]
            cipher = AES.new(CLAVE_AES, AES.MODE_EAX, nonce=nonce)
            return cipher.decrypt_and_verify(texto_cifrado, tag).decode('utf-8')
        except Exception as e:
            return "[Error al descifrar]"

    def registrar_toque_global(self, window, touch):
        self.ultimo_toque = time.time()

    def verificar_inactividad(self, dt):
        if self.contenedor_principal.children and hasattr(self.contenedor_principal.children[0], 'es_login'):
            return
        if time.time() - self.ultimo_toque > 20: 
            self.ultimo_toque = time.time() 
            self.pantalla_login()
            self.toast_comprobacion("Sesión cerrada por inactividad")

    def pantalla_login(self):
        self.contenedor_principal.clear_widgets()
        tarjeta = BoxLayout(orientation='vertical', size_hint=(0.85, None), height='280dp', padding='30dp', spacing='20dp')
        tarjeta.es_login = True 
        
        with tarjeta.canvas.before:
            Color(0.1, 0.1, 0.1, 1)
            self.rect_log = RoundedRectangle(pos=tarjeta.pos, size=tarjeta.size, radius=[25,])
        tarjeta.bind(pos=self._update_rect, size=self._update_rect)
        
        self.lbl_info = Label(text="PROTECCIÓN BIOMÉTRICA", size_hint_y=None, height='30dp', bold=True)
        self.input_pin = EstiloInput(hint_text="PIN alternativo manual", password=True, halign="center")
        
        texto_boton = "ESCANEAR HUELLA / PIN" if platform == 'android' else "DESBLOQUEAR (LINUX)"
        self.btn_entrar = Button(text=texto_boton, size_hint_y=None, height='60dp', background_normal='', background_color=(0, 0.4, 0.8, 1), bold=True)
        self.btn_entrar.bind(on_press=self.ejecutar_autenticacion_dinamica)
        
        tarjeta.add_widget(self.lbl_info)
        tarjeta.add_widget(self.input_pin)
        tarjeta.add_widget(self.btn_entrar)
        self.contenedor_principal.add_widget(tarjeta)
        
        if platform == 'android':
            Clock.schedule_once(lambda dt: self.autenticar_con_android(), 0.5)
            
        return self.contenedor_principal

    def ejecutar_autenticacion_dinamica(self, instance):
        if platform == 'android':
            if self.input_pin.text == _SISTEMA_SEG["p_m"]:
                self.mostrar_menu()
            else:
                self.autenticar_con_android()
        else:
            if self.input_pin.text == _SISTEMA_SEG["p_m"]:
                self.mostrar_menu()
            else:
                self.lbl_info.text = "PIN DESARROLLO INCORRECTO"
                self.lbl_info.color = (1, 0, 0, 1)

    def _update_rect(self, instance, value):
        self.rect_log.pos = instance.pos
        self.rect_log.size = instance.size

    # --- INTERFAZ DINÁMICA CON MARGEN DE SEGURIDAD (SAFE AREA) ---
    def mostrar_menu(self, filtro=""):
        self.contenedor_principal.clear_widgets()
        
        margen_inferior = '65dp' if platform == 'android' else '10dp'
        menu_layout = BoxLayout(orientation='vertical', padding=['10dp', '10dp', '10dp', margen_inferior], spacing='10dp')
        
        self.txt_buscar = EstiloInput(hint_text="🔍 Buscar...")
        self.txt_buscar.bind(text=lambda ins, val: self.renderizar_items(val))
        
        scroll = ScrollView()
        self.lista_ui = BoxLayout(orientation='vertical', spacing=8, size_hint_y=None)
        self.lista_ui.bind(minimum_height=self.lista_ui.setter('height'))
        self.renderizar_items(filtro)
        scroll.add_widget(self.lista_ui)
        
        btns = BoxLayout(size_hint_y=None, height='60dp', spacing=10)
        btns.add_widget(Button(text="+ NUEVO", on_press=lambda x: self.abrir_editor(None, None), background_normal='', background_color=(0, 0.5, 0.3, 1), bold=True))
        btns.add_widget(Button(text="SALIR", on_press=self.stop, background_normal='', background_color=(0.6, 0.1, 0.1, 1), bold=True))
        
        menu_layout.add_widget(self.txt_buscar)
        menu_layout.add_widget(scroll)
        menu_layout.add_widget(btns)
        self.contenedor_principal.add_widget(menu_layout)

    def renderizar_items(self, filtro):
        self.lista_ui.clear_widgets()
        for sitio in sorted(self.datos.keys()):
            if filtro.lower() in sitio.lower():
                btn = Button(text=f"  {sitio}", size_hint_y=None, height='55dp', halign='left', valign='middle', background_normal='', background_color=(0.12, 0.12, 0.12, 1))
                btn.bind(size=btn.setter('text_size'), on_press=lambda x, s=sitio: self.abrir_editor(None, s))
                self.lista_ui.add_widget(btn)

    def manejar_portapapeles(self, campo, modo):
        self.ultimo_toque = time.time() 
        if modo == "COPIAR":
            if campo.text:
                Clipboard.copy(campo.text)
                self.toast_comprobacion("Copiado")
        else:
            campo.text = Clipboard.paste()
            self.toast_comprobacion("Pegado")

    def toast_comprobacion(self, mensaje):
        pop = Popup(title='Info', content=Label(text=mensaje), size_hint=(0.6, 0.2))
        pop.open()

    def abrir_editor(self, instance, sitio_key=None):
        self.contenedor_principal.clear_widgets()
        scroll_editor = ScrollView(do_scroll_x=False)
        
        margen_inferior = '65dp' if platform == 'android' else '15dp'
        editor_layout = BoxLayout(orientation='vertical', padding=['15dp', '15dp', '15dp', margen_inferior], spacing='10dp', size_hint_y=None)
        editor_layout.bind(minimum_height=editor_layout.setter('height'))
        
        es_nuevo = sitio_key is None
        bloqueado = not es_nuevo
        modo_clip = "PEGAR" if es_nuevo else "COPIAR"
        
        user_val = self.desencriptar_texto(self.datos.get(sitio_key, {}).get('user', "")) if sitio_key else ""
        pass_val = self.desencriptar_texto(self.datos.get(sitio_key, {}).get('pass', "")) if sitio_key else ""
        extra_val = self.desencriptar_texto(self.datos.get(sitio_key, {}).get('extra', "")) if sitio_key else ""
        
        self.in_sitio = self.crear_fila_dato(editor_layout, "SITIO", sitio_key if sitio_key else "", bloqueado, modo_clip)
        self.in_user = self.crear_fila_dato(editor_layout, "USUARIO", user_val, bloqueado, modo_clip)
        self.in_pass = self.crear_fila_dato(editor_layout, "PASS", pass_val, bloqueado, modo_clip)
        self.in_extra = self.crear_fila_dato(editor_layout, "EXTRAS", extra_val, bloqueado, modo_clip, multiline=True)

        self.area_botones = BoxLayout(size_hint_y=None, height='60dp', spacing=10, padding=[0, 10, 0, 0])
        self.configurar_botones_lectura(sitio_key)
        editor_layout.add_widget(self.area_botones)
        scroll_editor.add_widget(editor_layout)
        self.contenedor_principal.add_widget(scroll_editor)

    def crear_fila_dato(self, parent, label_text, valor, bloqueado, modo, multiline=False):
        parent.add_widget(Label(text=label_text, size_hint_y=None, height='20dp', font_size='12sp', color=(0.5, 0.5, 0.5, 1)))
        fila = BoxLayout(size_hint_y=None, height='55dp' if not multiline else '100dp', spacing=5)
        input_campo = EstiloInput(text=valor, disabled=bloqueado, multiline=multiline)
        if multiline: input_campo.height = '100dp'
        btn_clip = Button(text=modo, size_hint_x=None, width='80dp', background_normal='', background_color=(0.2, 0.2, 0.2, 1))
        btn_clip.bind(on_press=lambda x: self.manejar_portapapeles(input_campo, modo))
        fila.add_widget(input_campo); fila.add_widget(btn_clip)
        parent.add_widget(fila)
        return input_campo

    def configurar_botones_lectura(self, sitio_key):
        self.area_botones.clear_widgets()
        btn_back = Button(text="VOLVER", on_press=lambda x: self.mostrar_menu(), background_color=(0.25, 0.25, 0.25, 1))
        if sitio_key:
            btn_mod = Button(text="MODIFICAR", on_press=self.activar_edicion, background_color=(0.8, 0.5, 0, 1))
            self.area_botones.add_widget(btn_back); self.area_botones.add_widget(btn_mod)
        else:
            btn_save = Button(text="GUARDAR", on_press=lambda x: self.confirmar_guardado(sitio_key), background_color=(0, 0.4, 0.8, 1))
            self.area_botones.add_widget(btn_back); self.area_botones.add_widget(btn_save)

    def activar_edicion(self, instance):
        self.in_sitio.disabled = self.in_user.disabled = self.in_pass.disabled = self.in_extra.disabled = False
        self.area_botones.clear_widgets()
        btn_can = Button(text="CANCELAR", on_press=lambda x: self.abrir_editor(None, self.in_sitio.text), background_color=(0.5, 0, 0, 1))
        btn_save = Button(text="CONFIRMAR", on_press=lambda x: self.confirmar_guardado(self.in_sitio.text), background_color=(0, 0.4, 0.8, 1))
        self.area_botones.add_widget(btn_can); self.area_botones.add_widget(btn_save)

    def confirmar_guardado(self, vieja_key):
        layout = BoxLayout(orientation='vertical', padding=20, spacing=20)
        btn_layout = BoxLayout(spacing=10, size_hint_y=None, height='50dp')
        btn_no = Button(text="NO", on_press=lambda x: popup.dismiss(), background_color=(0.7, 0, 0, 1))
        btn_si = Button(text="SÍ", on_press=lambda x: self.ejecutar_guardado(vieja_key, popup), background_color=(0, 0.6, 0, 1))
        btn_layout.add_widget(btn_no); btn_layout.add_widget(btn_si)
        layout.add_widget(Label(text="¿Guardar cambios?")); layout.add_widget(btn_layout)
        popup = Popup(title='CONFIRMACIÓN', content=layout, size_hint=(0.7, 0.3), auto_dismiss=False); popup.open()

    def ejecutar_guardado(self, vieja_key, popup):
        if not self.in_sitio.text: return
        if vieja_key in self.datos: del self.datos[vieja_key]
        
        self.datos[self.in_sitio.text] = {
            "user": self.encriptar_texto(self.in_user.text),
            "pass": self.encriptar_texto(self.in_pass.text),
            "extra": self.encriptar_texto(self.in_extra.text)
        }
        
        with open(self.archivo_json, "w") as f: json.dump(self.datos, f, indent=4)
        popup.dismiss(); self.mostrar_menu()

    def cargar_datos(self):
        if os.path.exists(self.archivo_json):
            try:
                with open(self.archivo_json, "r") as f: return json.load(f)
            except Exception as e:
                return {}
        return {}

if __name__ == '__main__':
    GestorApp().run()