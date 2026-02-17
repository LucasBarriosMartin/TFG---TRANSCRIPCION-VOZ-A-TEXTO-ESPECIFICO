package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
// IA VOSK
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.io.FileOutputStream


/* TODO:
    - Que al girar la pantalla no se pete
    - Pasar de texto a braille (comprobar)
    - Hacer la configuración

    Dudas:
    - El talk-back lo lee en voz alta y se vuelve a transcribir (problema?) hola
    - ¿Lo que se esté visualizando en la linea braille tiene que coincidir con la de la aplicacion?
 */

class MainActivity : AppCompatActivity(), RecognitionListener {

    // Variables de vista
    private lateinit var scrollView: ScrollView
    private lateinit var textViewTranscript: TextView
    private lateinit var btnPararReanudar: Button
    private lateinit var btnLimpiar: Button
    private lateinit var btnGuardar: Button

    // Reconocimiento de voz
    // private var speechRecognizer: SpeechRecognizer? = null
    // private var speechIntent: Intent? = null
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var textoAcumulado = "" // Guarda lo que se está escuchando

    // Flag de escuhca
    private var isListening = false
    private var wasListening = false
    private var isModelLoaded = false

    // Variable de guardado: gestiona la respuesta de donde quiere guardar
    // Este objeto gestiona la respuesta cuando el usuario elige dónde guardar el archivo
    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) { // Si el usuario elige una opcion valida guardamos el texto donde selecciono
            result.data?.data?.let { uri -> guardarTexto(uri) }
        }
    }

    // Inicio de la app
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar las vistas
        scrollView = findViewById(R.id.scrollView)
        textViewTranscript = findViewById(R.id.textViewTranscript)
        btnPararReanudar = findViewById(R.id.btnPararReanudar)
        btnLimpiar = findViewById(R.id.btnLimpiar)
        btnGuardar = findViewById(R.id.btnGuardar)

        // Vemos si venimos de un giro de pantalla
        if (savedInstanceState != null) {
            textoAcumulado = savedInstanceState.getString("TEXTO_GUARDADO", textoAcumulado)
            isListening = savedInstanceState.getBoolean("ESTA_ESCUCHANDO", isListening)
            wasListening = savedInstanceState.getBoolean("ESTABA_ESCUCHANDO", isListening)

            // Ponemos el texto antiguo
            textViewTranscript.text = textoAcumulado
        }

        // Boton de PARAR/REANUDAR
        btnPararReanudar.text = "CARGANDO..."
        btnPararReanudar.isEnabled = false
        btnPararReanudar.setOnClickListener { toggleListening() }

        // Boton de LIMPIAR
        btnLimpiar.setOnClickListener {
            textViewTranscript.text = ""
            textoAcumulado = ""
        }

        // Boton de GUARDAR
        btnGuardar.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain" // tipo .txt
                putExtra(Intent.EXTRA_TITLE, "transcripcion.txt") // Nombre sugerido
            }
            createFileLauncher.launch(intent)
        }

        // Permisos y Arranque Automático
        checkAudioPermission()

        // Cargamos VOSK
        initVosk();
    }

    // Funcion que carga la IA vosk
    private fun initVosk() {
        btnPararReanudar.isEnabled = false
        btnPararReanudar.text = "CARGANDO..."

        Thread {
            try {
                val modelPath = copyModelFromAssets("vosk-model-es")
                model = Model(modelPath)

                runOnUiThread {
                    isModelLoaded = true
                    btnPararReanudar.isEnabled = true
                    btnPararReanudar.text = "REANUDAR"
                    Toast.makeText(this, "Vosk listo", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error cargando modelo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }


    // -- CONTROL DEL MICROFONO --

    // Funcion para escuchar o dejar de escuchar
    private fun toggleListening() {
        if (isListening) {
            // Si estábamos escuchando, le damos a PARAR
            stopRecognition()
        } else {
            // Si estábamos parados, le damos a REANUDAR
            startRecognition()
        }
    }

    // Funcion que gestiona la escucha
    private fun startRecognition() {
        if (!isModelLoaded || model == null) return // Si no existe, nos salimos
        if (speechService != null) return // Si ya existe, no creamos otro

        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this) // abre el microfono

            isListening = true
            btnPararReanudar.text = "PARAR"
        } catch (e: Exception) {
            Toast.makeText(this, "Error al iniciar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Funcion para parar de escuchar
    private fun stopRecognition() {
        speechService?.stop()
        speechService = null
        isListening = false
        btnPararReanudar.text = "REANUDAR"
    }

    // -- ESCUCHAR Y TRANSCRIBIR --

    // Funcion que muestra el texto definitivo
    override fun onResult(hypothesis: String?) {
        if (hypothesis != null) {
            val json = JSONObject(hypothesis)
            val texto = json.optString("text")

            if (texto.isNotEmpty()) {
                val textoFinal = texto.replaceFirstChar { it.uppercase() } + ". "

                val usuarioAbajo = isBottom() // Comprobamos si estamos abajo del todo

                textoAcumulado += textoFinal
                textViewTranscript.text = textoAcumulado // Machacamos texto

                if (usuarioAbajo) { // Si estamos abajo, hacemos sroll, sino, no
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }

                // Envía el texto a la linea braille
                // TODO: comprobar funcionamiento de esta linea que esta obsoleta
                textViewTranscript.announceForAccessibility(textoFinal)
            }
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        if (hypothesis != null) {
            val json = JSONObject(hypothesis)
            val textoParcial = json.optString("partial")

            if (textoParcial.isNotEmpty()) {
                val textoBonito = textoParcial.replaceFirstChar { it.uppercase() }
                val usuarioAbajo = isBottom() // Comprobamos si estamos abajo del todo

                textViewTranscript.text = textoAcumulado + textoBonito

                if (usuarioAbajo) { // Si estamos abajo del todo hacemos sroll, sino, no
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
        }
    }

    private fun isBottom(): Boolean {
        if (scrollView.childCount == 0) return true
        val view = scrollView.getChildAt(0)
        val diff = (view.bottom - (scrollView.height + scrollView.scrollY))
        return diff <= 50 // TODO: poner 100?
    }

    // -- GUARDADO DE ESTADO
    // Si se van a borrar los datos al girar la pantalla nos guardamos lo actual
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("TEXTO_GUARDADO", textoAcumulado) // Guardamos el texto
        outState.putBoolean("ESTA_ESCUCHANDO", isListening) // Guardamos si estamos escuchando o no
    }

    // -- AUXILIARES --

    // Funciones del RecognitionListener
    override fun onFinalResult(hypothesis: String?) {
        onResult(hypothesis)
    }

    override fun onError(exception: java.lang.Exception?) {
        Toast.makeText(this, "Error vosk: ${exception?.message}", Toast.LENGTH_SHORT).show()
    }

    override fun onTimeout() {}

    // Funcion para pedir los permisos de voz
    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    // Funcion que se activa cuando se piden los permisos de escucha (para que empiece a escuchar directamente)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) { // Peticion de audio
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { // si nos dan los permisos
                isListening = true
                if (isModelLoaded) startRecognition() // Si el modelo está activo
            }
            else { // Si no nos dan los permisos, avisamos
                Toast.makeText(this, "Necesito el micrófono para transcribir", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Funcion para cuando se minimice o bloquee el movil
    override fun onPause() {
        super.onPause()
        wasListening = isListening

        if (isListening) {
            stopRecognition()
        }
    }

    // Funcion para cuando se cierra completamente la app
    override fun onDestroy() {
        super.onDestroy()

        try {
            // Liberar el micrófono para que se pueda volver a usar.
            if (speechService != null) {
                speechService?.stop()
                speechService?.shutdown()
                speechService = null
            }
            if (model != null) {
                model?.close()
                model = null
            }
        }catch (e: Exception){ }
    }

    // Funcion para cuando se vuelva a abrir estando minimizada
    override fun onResume() {
        super.onResume()

        if (isModelLoaded && wasListening) {
            startRecognition()
        }
    }

    // Funcion para guardar el texto transcrito
    private fun guardarTexto(uri: android.net.Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val textoAGuardar = textViewTranscript.text.toString()
                outputStream.write(textoAGuardar.toByteArray())
            }
            Toast.makeText(this, "Guardado correctamente", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

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

    private fun copyAssetFolder(assetManager: android.content.res.AssetManager, fromAssetPath: String, toPath: String) {
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

    private fun copyAsset(assetManager: android.content.res.AssetManager, fromAssetPath: String, toPath: String) {
        assetManager.open(fromAssetPath).use { input ->
            FileOutputStream(toPath).use { output ->
                input.copyTo(output)
            }
        }
    }



}
