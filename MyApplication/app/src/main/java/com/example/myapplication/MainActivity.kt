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

/* TODO:
    - Pasar de texto a braille (comprobar)
    - Hacer la configuración

    Dudas:
    - El talk-back lo lee en voz alta y se vuelve a transcribir (problema)
    - ¿Poner en la pantalla los simbolos braille? (para la presentación)
    - Cuando ya no cabe el texto en la pantalla, ¿tiene que scrolear la pantalla a medida que se escribe el texto o no?
    - ¿Lo que se esté visualizando en la linea braille tiene que coincidir con la de la aplicacion?
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
    private var textoAcumulado = "" // Guarda lo que se está escuchando

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

        // Vemos si venimos de un giro de pantalla
        if (savedInstanceState != null) {
            textoAcumulado = savedInstanceState.getString("TEXTO_GUARDADO", textoAcumulado)
            isListening = savedInstanceState.getBoolean("ESTABA_ESCUCHANDO", isListening)

            // Ponemos el texto antiguo
            textViewTranscript.text = textoAcumulado
        }

        // Boton de PARAR/REANUDAR
        btnPararReanudar.text = if (isListening) "PARAR" else "REANUDAR"
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

        // Si tenemos permiso, arrancamos si estamos en escucha
        // TODO: si no tiene permisos, ¿Los volvemos a pedir?
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (isListening) {
                startRecognition()
            } else {
                btnPararReanudar.text = "REANUDAR"
            }
        }
    }

    // Funcion que se activa cuando se piden los permisos de escucha (para que empiece a escuchar directamente)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) { // Peticion de audio
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { // si nos dan los permisos
                startRecognition()
            }
            else { // si no nos dan los permisos, mostramos el mensaje de error correspondiente
                Toast.makeText(
                    this,
                    "Se necesita permiso de micro para funcionar",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Si se van a borrar los datos al girar la pantalla nos guardamos lo actual
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("TEXTO_GUARDADO", textoAcumulado) // Guardamos el texto
        outState.putBoolean("ESTABA_ESCUCHANDO", isListening) // Guardamos si estamos escuchando o no
    }


    override fun onDestroy() {
        super.onDestroy()
        // Liberar el micrófono para que se pueda volver a usar.
        speechRecognizer?.destroy()
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
                // Mostrar el texto definitivo
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val texto = matches[0].replaceFirstChar { it.uppercase() } // Para que la primera letra se ponga mayuscula
                    val textoFinal = texto + ". ";

                    textoAcumulado += textoFinal;

                    textViewTranscript.text = textoAcumulado // Machacamos texto

                    val view = scrollView.getChildAt(0)
                    val diff = (view.bottom - (scrollView.height + scrollView.scrollY))
                    if (diff <= 50) { // Si estamos abajo hacemos scroll
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }

                    // Envía el texto a la linea braille
                    // TODO: comprobar funcionamiento de esta linea
                    textViewTranscript.announceForAccessibility(textoFinal)
                }
                // Si seguimos en modo escucha, reiniciamos el ciclo
                if (isListening) startRecognition()
            }

            override fun onPartialResults(partialResults: Bundle?) { // va mostrando la frase no definitiva
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {

                    // Mostramos lo guardado + lo nuevo
                    val textoNuevo = matches[0].replaceFirstChar { it.uppercase() }
                    textViewTranscript.text = textoAcumulado + textoNuevo

                    val view = scrollView.getChildAt(0)
                    val diff = (view.bottom - (scrollView.height + scrollView.scrollY))
                    if (diff <= 50) { // Si estamos abajo hacemos scroll
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
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
