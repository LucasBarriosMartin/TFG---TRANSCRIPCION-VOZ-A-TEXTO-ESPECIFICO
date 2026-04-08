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

    // 1. NUESTRA NUEVA CLASE DE VISTAS
    private lateinit var vistaApp: AppView

    // 2. Variables de estado y datos
    private val listaFrases = ArrayList<String>()
    private lateinit var adaptador: TranscriptionAdapter

    private lateinit var btnParar: Button
    private lateinit var btnLimpiar: Button
    private lateinit var btnGuardar: Button

    private lateinit var btnConfiguracion: ImageButton

    // 3. EL MOTOR INTELIGENTE — sustituye a las variables de Vosk que teníamos antes.
    //    SmartEngine decide internamente si usar Vosk o Whisper según la conexión.
    private lateinit var engine: RecognitionEngine

    private var isListening = true
    private var wasListening = false
    private var isPartialTyping = false

    private val createFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri -> guardarTexto(uri) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnParar = findViewById(R.id.btnPararReanudar)
        btnLimpiar = findViewById(R.id.btnLimpiar)
        btnGuardar = findViewById(R.id.btnGuardar)

        adaptador = TranscriptionAdapter(listaFrases)

        vistaApp = AppView(
            actividad = this,
            adaptador = adaptador,
            onPararReanudarClick = { toggleListening() },
            onLimpiarClick = { limpiarLista() },
            onGuardarClick = { abrirGuardadoDocumento() },
            onConfiguracionClick = { abrirConfiguracion() }
        )

        engine = SmartEngine(this, "http://100.125.52.21:8000") { nombre ->
            vistaApp.mostrarMotorActivo(nombre)
        }

        if (savedInstanceState != null) {
            savedInstanceState.getStringArrayList("LISTAS_GUARDADAS")?.let {
                listaFrases.addAll(it)
                adaptador.notifyDataSetChanged()
            }
            isListening = savedInstanceState.getBoolean("ESTA_ESCUCHANDO", isListening)
            wasListening = savedInstanceState.getBoolean("ESTABA_ESCUCHANDO", isListening)
        }

        vistaApp.estadoCargando()
        if (checkAudioPermission()) initEngine()

        // --- Aplicar accesibilidad ---
        val root = findViewById<android.view.View>(android.R.id.content)
        root.setBackgroundColor(
            AccessibilityManager.getBackgroundColor(this)
        )
        AccessibilityManager.applyAccessibility(this, root)

        // Aplicar a adaptador
        adaptador.setTamanoLetra(AccessibilityManager.getTextSizePx(this))
        adaptador.setColorTexto(AccessibilityManager.getTextColor(this))
        adaptador.setColorFondo(AccessibilityManager.getBackgroundColor(this))
        btnConfiguracion = findViewById(R.id.btnConfiguracion)
        aplicarEstiloImageButton(btnConfiguracion)
        aplicarColoresBotones()
    }

    // --- ACCIONES DE LOS BOTONES ---

    private fun toggleListening() {
        if (isListening)
            stopRecognition()
        else
            startRecognition()
    }

    private fun limpiarLista() {
        listaFrases.clear()
        adaptador.notifyDataSetChanged()
        isPartialTyping = false
    }

    private fun abrirGuardadoDocumento() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "transcripcion.txt")
        }
        createFileLauncher.launch(intent)
    }

    // --- LÓGICA DEL MOTOR ---

    private fun initEngine() {
        vistaApp.estadoCargando()

        // SmartEngine lanza Vosk y Whisper en paralelo internamente.
        // onReady() se llama en cuanto el primero esté listo.
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

    // --- ESCUCHAR Y PARAR ---

    private fun startRecognition() {
        if (!engine.estaListo()) return

        // "this" implementa EngineListener, así que recibe los callbacks de ambos motores
        engine.iniciarEscucha(this)
        isListening = true
        vistaApp.estadoListo(true) // Ponemos botón en PARAR
    }

    private fun stopRecognition() {
        engine.pararEscucha()
        isListening = false
        vistaApp.estadoListo(false) // Ponemos botón en REANUDAR
    }

    // --- EngineListener — igual para Vosk y Whisper ---

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

    // --- AUXILIARES, PERMISOS Y CICLO DE VIDA ---

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("LISTAS_GUARDADAS", listaFrases)
        outState.putBoolean("ESTA_ESCUCHANDO", isListening)
        outState.putBoolean("ESTABA_ESCUCHANDO", wasListening)
    }

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
        if (isListening) stopRecognition()
    }

    override fun onResume() {
        super.onResume()

        val root = findViewById<android.view.View>(android.R.id.content)
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

        if (engine.estaListo() && wasListening) startRecognition()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.liberar()
        vistaApp.liberarPantallaEncendida()
    }

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

    private fun abrirConfiguracion() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }


    private fun aplicarEstiloImageButton(boton: ImageButton) {
        val colorIcono = AccessibilityManager.getTextColor(this)
        boton.setColorFilter(colorIcono) // Aplica el color del icono según contraste

        // Ajusta tamaño del icono si quieres escalar
        val scale = when (AccessibilityManager.getTextSize(this)) {
            "normal" -> 1.0f
            "grande" -> 1.2f
            "extra" -> 1.4f
            else -> 1.0f
        }
        boton.scaleX = scale
        boton.scaleY = scale
    }

    private fun aplicarEstiloBoton(boton: Button) {

        val colorTexto = AccessibilityManager.getButtonTextColor(this)
        val colorFondo = AccessibilityManager.getButtonBackgroundColor(this)

        boton.setBackgroundColor(colorFondo)
        boton.setTextColor(colorTexto)
    }

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