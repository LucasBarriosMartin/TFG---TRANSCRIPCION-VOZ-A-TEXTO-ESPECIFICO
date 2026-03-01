package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream


/* TODO:
    - Pasar de texto a braille
        - Cambiar el TextView por un RecyclerView (lista de todas las frases hasta el momento)
    - Hacer la configuración

    Dudas:
    - El talk-back lo lee en voz alta y se vuelve a transcribir (problema?) hola
    - ¿Lo que se esté visualizando en la linea braille tiene que coincidir con la de la aplicacion?
    - ¿Ponemos los botones arriba del todo para que los sordociegos los vean nada más abrir la app?
 */

class MainActivity : AppCompatActivity(), RecognitionListener {

    // Variables de vista
    // - SrollView
    // private lateinit var scrollView: ScrollView
    // private lateinit var textViewTranscript: TextView
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private val listaFrases = ArrayList<String>()
    private lateinit var adaptador: TranscripcionAdapter
    // - Botones
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
    private var isListening = true
    private var wasListening = false
    private var isModelLoaded = false
    private var isPartialTyping = false // Para que no se creen multiples burbujas

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
        recyclerView = findViewById(R.id.recyclerViewTranscripcion)
        btnPararReanudar = findViewById(R.id.btnPararReanudar)
        btnLimpiar = findViewById(R.id.btnLimpiar)
        btnGuardar = findViewById(R.id.btnGuardar)

        // Configurar el RecyclerView
        adaptador = TranscripcionAdapter(listaFrases)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = adaptador


        if (savedInstanceState != null) { // Vemos si venimos de un giro de pantalla o de minimizar la app
            val frasesGuardadas = savedInstanceState.getStringArrayList("LISTAS_GUARDADAS")
            if (frasesGuardadas != null) {
                listaFrases.addAll(frasesGuardadas)
                adaptador.notifyDataSetChanged() // Refrescar lista
            }

            isListening = savedInstanceState.getBoolean("ESTA_ESCUCHANDO", isListening)
            wasListening = savedInstanceState.getBoolean("ESTABA_ESCUCHANDO", isListening)
        }

        // Boton de PARAR/REANUDAR
        btnPararReanudar.text = "CARGANDO..."
        btnPararReanudar.isEnabled = false
        btnPararReanudar.setOnClickListener { toggleListening() }

        // Boton de LIMPIAR
        btnLimpiar.setOnClickListener {
            listaFrases.clear()
            adaptador.notifyDataSetChanged()
            isPartialTyping = false
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
        // y cargamos VOSK si nos dan los permisos
        checkAudioPermission()

    }

    // Funcion que carga la IA vosk
    private fun initVosk() {
        btnPararReanudar.isEnabled = false
        btnPararReanudar.text = "CARGANDO..."

        Thread {
            try {
                val modelPath = copyModelFromAssets("vosk-model-es")
                val modeloCargado = Model(modelPath)

                runOnUiThread {
                    // Si el usuario cerró la app mientras se copiaban los 50MB, abortamos para que no explote.
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    model = modeloCargado
                    isModelLoaded = true
                    btnPararReanudar.isEnabled = true

                    // Si la app se acaba de instalar y nos dio permiso o si la minimizó y volvió, arrancamos solos.
                    if (isListening || wasListening) {
                        startRecognition()
                        Toast.makeText(this, "Vosk listo y escuchando", Toast.LENGTH_SHORT).show()
                    } else {
                        // Si no quería escuchar, lo dejamos pausado
                        btnPararReanudar.text = "REANUDAR"
                        Toast.makeText(this, "Vosk listo", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Error cargando modelo: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }


    // -- CONTROL DEL MICROFONO --

    // Funcion para escuchar o dejar de escuchar
    private fun toggleListening() {
        if (isListening) { // Si estábamos escuchando, le damos a PARAR
            stopRecognition()
        } else { // Si estábamos parados, le damos a REANUDAR
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

                val usuarioAbajo = !recyclerView.canScrollVertically(1)

                if (isPartialTyping) { // Si estábamos escribiendo parcialmente, machacamos la última burbuja con el texto final
                    listaFrases[listaFrases.size - 1] = textoFinal
                    adaptador.notifyItemChanged(listaFrases.size - 1)
                    isPartialTyping = false
                }
                else { // Si llega de golpe el texto transcrito, creamos burbuja nueva
                    listaFrases.add(textoFinal)
                    adaptador.notifyItemInserted(listaFrases.size - 1)
                }

                // Hacemos scroll si estamos abajo del todo
                if (usuarioAbajo) {
                    recyclerView.smoothScrollToPosition(listaFrases.size - 1)
                }
            }
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        if (hypothesis != null) {
            val json = JSONObject(hypothesis)
            val textoParcial = json.optString("partial")

            if (textoParcial.isNotEmpty()) {
                val textoBonito = textoParcial.replaceFirstChar { it.uppercase() }

                val usuarioAbajo = !recyclerView.canScrollVertically(1)

                if (!isPartialTyping) { // Es la primera palabra de la frase, creamos una burbuja nueva
                    listaFrases.add(textoBonito)
                    isPartialTyping = true
                    adaptador.notifyItemInserted(listaFrases.size - 1)
                }
                else { // Ya existe la burbuja parcial, la actualizamoss
                    listaFrases[listaFrases.size - 1] = textoBonito
                    adaptador.notifyItemChanged(listaFrases.size - 1)
                }

                // Hacemos scroll si estamos abajo del todo
                if (usuarioAbajo) {
                    recyclerView.smoothScrollToPosition(listaFrases.size - 1)
                }
            }
        }
    }

    // -- GUARDADO DE ESTADO
    // Si se van a borrar los datos al girar la pantalla nos guardamos lo actual
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("LISTAS_GUARDADAS", listaFrases)
        outState.putBoolean("ESTA_ESCUCHANDO", isListening)
        outState.putBoolean("ESTABA_ESCUCHANDO", wasListening)
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
        else { // cargamos vosk si tenemos los permisos de microfono
            initVosk()
        }
    }

    // Funcion que se activa cuando se piden los permisos de escucha (para que empiece a escuchar directamente)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) { // Peticion de audio
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { // si nos dan los permisos
                isListening = true
                initVosk()
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
                val textoAGuardar = listaFrases.joinToString(separator = "\n")
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
