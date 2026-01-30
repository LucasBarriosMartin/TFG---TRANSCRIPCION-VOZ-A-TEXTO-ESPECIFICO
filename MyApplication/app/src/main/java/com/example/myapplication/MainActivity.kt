package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import java.io.OutputStream

/* TODO:
    - Pasar de texto a braille
    - Hacer la configuración
 */

class MainActivity : AppCompatActivity() {

    // Variables de vista
    private lateinit var scrollView: ScrollView
    private lateinit var textViewTranscript: TextView
    private lateinit var btnPararReanudar: Button
    private lateinit var btnLimpiar: Button
    private lateinit var btnGuardar: Button

    // Reconocimiento de voz
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null

    // Flag de escuhca
    private var isListening = true

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

        // Boton de PARAR/REANUDAR
        btnPararReanudar.text = "PARAR" // Como arranca escuchando, el botón debe permitir PARAR.
        btnPararReanudar.setOnClickListener { toggleListening() }

        // Boton de LIMPIAR
        btnLimpiar.setOnClickListener { textViewTranscript.text = "" }

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

        // Si ya tenemos permiso, arrancamos inmediatamente
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecognition()
        }
    }

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
        isListening = true // Semáforo en VERDE
        btnPararReanudar.text = "PARAR" // Visualmente indicamos que la próxima acción es parar

        // Si no existe, lo creamos
        if (speechRecognizer == null) {
            initSpeechRecognizer()
        }

        // Intentamos escuchar
        try {
            speechRecognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Funcion para parar de escuchar
    private fun stopRecognition() {
        isListening = false
        btnPararReanudar.text = "REANUDAR"
        speechRecognizer?.stopListening() // cancel() si queremos que pare lo que está procesando
    }

    // Funcion que configura el reconocedor de voz
    private fun initSpeechRecognizer() {

        // Si existía uno, destruirlo y crear uno nuevo
        if (speechRecognizer != null) speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        // Comprobamos si podemos reconocer por voz
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Tu móvil no soporta reconocimiento de voz", Toast.LENGTH_LONG).show()
            return
        }

        // Configuración de escucha
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) // conversación casual
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()) // que use el idioma del movil
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // no espera a que haya un silencio para enviar los resultados
        }

        // Reaccion a los eventos
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {} // preparado para escuchar
            override fun onBeginningOfSpeech() {} // empezó a hablar
            override fun onRmsChanged(rmsdB: Float) {} // cambio de volumen
            override fun onBufferReceived(buffer: ByteArray?) {} // datos recibidos
            override fun onEndOfSpeech() {} // silencio detectado

            override fun onError(error: Int) {
                // Si el error es "permisos" o "cliente", cancelamos para no bloquear
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || error == SpeechRecognizer.ERROR_CLIENT) {
                    isListening = false
                    btnPararReanudar.text = "REANUDAR"
                    return
                }

                // Si debemos seguir escuchando, reintentamos despues de medio segundo
                if (isListening) {
                    textViewTranscript.postDelayed({ startRecognition() }, 500)
                }
            }

            override fun onResults(results: Bundle?) {
                // Extraer el texto final
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val texto = matches[0].replaceFirstChar { it.uppercase() } // Para que la primera letra se ponga mayuscula
                    addTextToScreen(texto + ". ") // Añadir texto en la pantalla
                }
                // Si seguimos en modo escucha, reiniciamos el ciclo
                if (isListening) startRecognition()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // TODO: Poner el texto provicional en un tono más claro que no es el definitivo
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    // Funcion que escribe sin mover la pantalla del usuario
    private fun addTextToScreen(newText: String) {
        val scrollY = scrollView.scrollY
        textViewTranscript.append(newText) // Escribimos
        scrollView.post { scrollView.scrollTo(0, scrollY) } // Movemos a la posicion anterior
    }

    // Funcion para pedir los permisos de voz
    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
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

}