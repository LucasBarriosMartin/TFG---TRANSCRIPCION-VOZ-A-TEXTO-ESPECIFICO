package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Button
import android.widget.ImageButton
import android.view.View

class MainActivity : AppCompatActivity(), EngineListener {

    // Vista que encapsula la UI principal de la actividad
    private lateinit var vistaApp: AppView

    // Lista donde se van acumulando las frases transcritas
    private val listaFrases = ArrayList<String>()

    // Adaptador del RecyclerView que muestra la transcripción
    private lateinit var adaptador: TranscriptionAdapter

    // Botones principales de la interfaz
    private lateinit var btnParar: Button
    private lateinit var btnLimpiar: Button
    private lateinit var btnGuardar: Button
    private lateinit var btnConfiguracion: ImageButton

    // Motor de reconocimiento (puede usar Vosk o Whisper internamente)
    private lateinit var engine: RecognitionEngine

    // Estados del reconocimiento
    private var isListening = true
    private var wasListening = false
    private var isPartialTyping = false

    // Lanzador para crear un documento y guardar la transcripción
    private val createFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri -> guardarTexto(uri) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Referencias a botones
        btnParar = findViewById(R.id.btnPararReanudar)
        btnLimpiar = findViewById(R.id.btnLimpiar)
        btnGuardar = findViewById(R.id.btnGuardar)

        adaptador = TranscriptionAdapter(listaFrases)

        // Inicialización de la vista que gestiona la UI
        vistaApp = AppView(
            actividad = this,
            adaptador = adaptador,
            onPararReanudarClick = { toggleListening() },
            onLimpiarClick = { limpiarLista() },
            onGuardarClick = { abrirGuardadoDocumento() },
            onConfiguracionClick = { abrirConfiguracion() }
        )

        // Inicialización del motor de reconocimiento
        engine = SmartEngine(this, "https://cacharrin-1.taile1b3dc.ts.net") { nombre ->
            vistaApp.mostrarMotorActivo(nombre)
        }

        // Restaurar estado si la actividad se recrea
        if (savedInstanceState != null) {
            savedInstanceState.getStringArrayList("LISTAS_GUARDADAS")?.let {
                listaFrases.addAll(it)
                adaptador.notifyDataSetChanged()
            }
            isListening = savedInstanceState.getBoolean("ESTA_ESCUCHANDO", isListening)
            wasListening = savedInstanceState.getBoolean("ESTABA_ESCUCHANDO", isListening)
        }

        vistaApp.estadoCargando()

        if (checkAudioPermission()) {
            initEngine()
        }

        // Aplicar configuración de accesibilidad
        val root = findViewById<View>(android.R.id.content)
        root.setBackgroundColor(
            AccessibilityManager.getBackgroundColor(this)
        )
        AccessibilityManager.applyAccessibility(this, root)

        // Aplicar configuración visual al adaptador
        adaptador.setTamanoLetra(AccessibilityManager.getTextSizePx(this))
        adaptador.setColorTexto(AccessibilityManager.getTextColor(this))
        adaptador.setColorFondo(AccessibilityManager.getBackgroundColor(this))

        btnConfiguracion = findViewById(R.id.btnConfiguracion)

        aplicarEstiloImageButton(btnConfiguracion)
        aplicarColoresBotones()
    }

    // Alterna entre escuchar o detener la escucha
    private fun toggleListening() {
        if (isListening)
            stopRecognition()
        else
            startRecognition()
    }

    // Limpia la lista de frases transcritas
    private fun limpiarLista() {
        listaFrases.clear()
        adaptador.notifyDataSetChanged()
        isPartialTyping = false
    }

    // Abre el selector para guardar la transcripción como archivo
    private fun abrirGuardadoDocumento() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "transcripcion.txt")
        }
        createFileLauncher.launch(intent)
    }

    // Inicializa el motor de reconocimiento
    private fun initEngine() {
        vistaApp.estadoCargando()

        engine.inicializar(
            onReady = {
                if (isFinishing || isDestroyed) return@inicializar

                vistaApp.mostrarMotorActivo(engine.nombreMotorActivo())

                if (isListening || wasListening) {
                    startRecognition()
                    Toast.makeText(this, "Motor listo y escuchando", Toast.LENGTH_SHORT).show()
                } else {
                    vistaApp.estadoListo(false)
                    Toast.makeText(this, "Motor listo", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { e ->
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Error cargando motor: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // Comienza la escucha del micrófono
    private fun startRecognition() {
        if (!engine.estaListo()) return

        engine.iniciarEscucha(this)
        isListening = true
        vistaApp.estadoListo(true)
    }

    // Detiene la escucha del micrófono
    private fun stopRecognition() {
        engine.pararEscucha()
        isListening = false
        vistaApp.estadoListo(false)
    }

    // Resultado parcial del motor de reconocimiento
    override fun onResultadoParcial(texto: String) {
        val textoBonito = texto.replaceFirstChar { it.uppercase() }

        if (!isPartialTyping) {
            listaFrases.add(textoBonito)
            isPartialTyping = true
            adaptador.notifyItemInserted(listaFrases.size - 1)
        } else {
            listaFrases[listaFrases.size - 1] = textoBonito
            adaptador.notifyItemChanged(listaFrases.size - 1)
        }

        vistaApp.hacerScrollAbajo(listaFrases.size)
    }

    // Resultado final confirmado por el motor
    override fun onResultadoFinal(texto: String) {
        val textoFinal = texto.replaceFirstChar { it.uppercase() } + ". "

        if (isPartialTyping) {
            listaFrases[listaFrases.size - 1] = textoFinal
            adaptador.notifyItemChanged(listaFrases.size - 1)
            isPartialTyping = false
        } else {
            listaFrases.add(textoFinal)
            adaptador.notifyItemInserted(listaFrases.size - 1)
        }

        vistaApp.hacerScrollAbajo(listaFrases.size)
    }

    override fun onError(excepcion: Exception) {
        Toast.makeText(this, "Error: ${excepcion.message}", Toast.LENGTH_SHORT).show()
    }

    // Guarda el estado de la actividad
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("LISTAS_GUARDADAS", listaFrases)
        outState.putBoolean("ESTA_ESCUCHANDO", isListening)
        outState.putBoolean("ESTABA_ESCUCHANDO", wasListening)
    }

    // Comprueba si el permiso de micrófono está concedido
    private fun checkAudioPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return false
        }
        return true
    }

    // Resultado de la petición de permisos
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                isListening = true
                initEngine()
            } else {
                Toast.makeText(this, "Necesito el micrófono para transcribir", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    override fun onPause() {
        super.onPause()

        wasListening = isListening

        if (isListening) {
            stopRecognition()
        }
    }

    override fun onResume() {
        super.onResume()

        val root = findViewById<View>(android.R.id.content)

        root.setBackgroundColor(
            AccessibilityManager.getBackgroundColor(this)
        )

        AccessibilityManager.applyAccessibility(this, root)

        adaptador.setTamanoLetra(AccessibilityManager.getTextSizePx(this))
        adaptador.setColorTexto(AccessibilityManager.getTextColor(this))
        adaptador.setColorFondo(AccessibilityManager.getBackgroundColor(this))

        aplicarColoresBotones()
        aplicarEstiloImageButton(btnConfiguracion)

        adaptador.notifyDataSetChanged()

        if (engine.estaListo() && wasListening) {
            startRecognition()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        engine.liberar()
        vistaApp.liberarPantallaEncendida()
    }

    // Guarda todas las frases en el archivo seleccionado
    private fun guardarTexto(uri: android.net.Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val textoAGuardar = listaFrases.joinToString(separator = "\n")
                outputStream.write(textoAGuardar.toByteArray())
            }

            Toast.makeText(this, "Guardado correctamente", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Abre la pantalla de configuración
    private fun abrirConfiguracion() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    // Ajusta color y escala del icono según la configuración de accesibilidad
    private fun aplicarEstiloImageButton(boton: ImageButton) {

        val colorIcono = AccessibilityManager.getTextColor(this)
        boton.setColorFilter(colorIcono)

        val scale = when (AccessibilityManager.getTextSize(this)) {
            "normal" -> 1.0f
            "grande" -> 1.2f
            "extra" -> 1.4f
            else -> 1.0f
        }

        boton.scaleX = scale
        boton.scaleY = scale
    }

    // Aplica colores accesibles a un botón estándar
    private fun aplicarEstiloBoton(boton: Button) {

        val colorTexto = AccessibilityManager.getButtonTextColor(this)
        val colorFondo = AccessibilityManager.getButtonBackgroundColor(this)

        boton.setBackgroundColor(colorFondo)
        boton.setTextColor(colorTexto)
    }

    // Aplica los colores de accesibilidad a todos los botones
    private fun aplicarColoresBotones() {

        val root = findViewById<View>(android.R.id.content)

        val colorTexto = AccessibilityManager.getTextColor(this)
        val colorFondo = AccessibilityManager.getBackgroundColor(this)

        root.setBackgroundColor(colorFondo)

        btnParar.setTextColor(colorTexto)
        btnLimpiar.setTextColor(colorTexto)
        btnGuardar.setTextColor(colorTexto)

        aplicarEstiloBoton(btnParar)
        aplicarEstiloBoton(btnLimpiar)
        aplicarEstiloBoton(btnGuardar)

        aplicarEstiloImageButton(btnConfiguracion)
    }
}