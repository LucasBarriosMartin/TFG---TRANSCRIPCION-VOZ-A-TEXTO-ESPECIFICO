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
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), RecognitionListener {

    // 1. NUESTRA NUEVA CLASE DE VISTAS
    private lateinit var vistaApp: Vistapp

    // 2. Variables de estado y datos
    private val listaFrases = ArrayList<String>()
    private lateinit var adaptador: TranscripcionAdapter

    private var model: Model? = null
    private var speechService: SpeechService? = null

    private var isListening = true
    private var wasListening = false
    private var isModelLoaded = false
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

        // Inicializamos el adaptador
        adaptador = TranscripcionAdapter(listaFrases)

        // ¡MAGIA! Conectamos la Vista pasándole las funciones de lo que tiene que hacer
        vistaApp = Vistapp(
            actividad = this,
            adaptador = adaptador,
            onPararReanudarClick = { toggleListening() },
            onLimpiarClick = { limpiarLista() },
            onGuardarClick = { abrirGuardadoDocumento() }
        )

        // Restaurar estado si se giró la pantalla
        if (savedInstanceState != null) {
            val frasesGuardadas = savedInstanceState.getStringArrayList("LISTAS_GUARDADAS")
            if (frasesGuardadas != null) {
                listaFrases.addAll(frasesGuardadas)
                adaptador.notifyDataSetChanged()
            }
            isListening = savedInstanceState.getBoolean("ESTA_ESCUCHANDO", isListening)
            wasListening = savedInstanceState.getBoolean("ESTABA_ESCUCHANDO", isListening)
        }

        vistaApp.estadoCargando()

        if (checkAudioPermission()) {
            initVosk()
        }
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

    // --- LOGICA VOSK ---

    private fun initVosk() {
        vistaApp.estadoCargando()

        Thread {
            try {
                val modelPath = copyModelFromAssets("vosk-model-es")
                val modeloCargado = Model(modelPath)

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    model = modeloCargado
                    isModelLoaded = true

                    if (isListening || wasListening) {
                        startRecognition()
                        Toast.makeText(this, "Vosk listo y escuchando", Toast.LENGTH_SHORT).show()
                    } else {
                        vistaApp.estadoListo(false) // Ponemos botón en REANUDAR
                        Toast.makeText(this, "Vosk listo", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(
                            this,
                            "Error cargando modelo: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }.start()
    }

    // --- ESCUCHAR Y TRANSCRIBIR ---

    private fun startRecognition() {
        if (!isModelLoaded || model == null) return
        if (speechService != null) return

        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)

            isListening = true
            vistaApp.estadoListo(true) // Ponemos botón en PARAR
        } catch (e: Exception) {
            Toast.makeText(this, "Error al iniciar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecognition() {
        speechService?.stop()
        speechService = null
        isListening = false
        vistaApp.estadoListo(false) // Ponemos botón en REANUDAR
    }

    override fun onResult(hypothesis: String?) {
        if (hypothesis != null) {
            val json = JSONObject(hypothesis)
            val texto = json.optString("text")

            if (texto.isNotEmpty()) {
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
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        if (hypothesis != null) {
            val json = JSONObject(hypothesis)
            val textoParcial = json.optString("partial")

            if (textoParcial.isNotEmpty()) {
                val textoBonito = textoParcial.replaceFirstChar { it.uppercase() }

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
        }
    }

    // --- AUXILIARES, PERMISOS Y CICLO DE VIDA ---

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("LISTAS_GUARDADAS", listaFrases)
        outState.putBoolean("ESTA_ESCUCHANDO", isListening)
        outState.putBoolean("ESTABA_ESCUCHANDO", wasListening)
    }

    override fun onFinalResult(hypothesis: String?) {
        onResult(hypothesis)
    }

    override fun onError(exception: java.lang.Exception?) {
        Toast.makeText(this, "Error vosk: ${exception?.message}", Toast.LENGTH_SHORT).show()
    }

    override fun onTimeout() {}

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
                initVosk()
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            model?.close()
            model = null
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        if (isModelLoaded && wasListening) startRecognition()
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

    // Funciones de copiado de Assets omitidas por brevedad (se quedan igual que las tenías al final del archivo)
    private fun copyModelFromAssets(modelName: String): String {
        val assetManager = assets
        val outDir = File(filesDir, modelName)

        if (outDir.exists()) {
            return outDir.absolutePath
        }

        outDir.mkdirs()
        copyAssetFolder(assetManager, modelName, outDir.absolutePath)

        return outDir.absolutePath
    }

    private fun copyAssetFolder(
        assetManager: android.content.res.AssetManager,
        fromAssetPath: String,
        toPath: String
    ) {
        val files = assetManager.list(fromAssetPath) ?: return

        File(toPath).mkdirs()

        for (file in files) {
            val fullPath = "$fromAssetPath/$file"
            val subFiles = assetManager.list(fullPath)

            if (subFiles.isNullOrEmpty()) {
                copyAsset(assetManager, fullPath, "$toPath/$file")
            } else {
                copyAssetFolder(assetManager, fullPath, "$toPath/$file")
            }
        }
    }

    private fun copyAsset(
        assetManager: android.content.res.AssetManager,
        fromAssetPath: String,
        toPath: String
    ) {
        assetManager.open(fromAssetPath).use { input ->
            FileOutputStream(toPath).use { output ->
                input.copyTo(output)
            }
        }
    }
}