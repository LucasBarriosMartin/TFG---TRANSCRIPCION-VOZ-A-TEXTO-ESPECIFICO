package com.example.myapplication

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

class MotorVosk(
    private val context: Context,
    private val listener: InterfazModelo.Listener // <-- MUCHO MÁS LIMPIO
) : InterfazModelo, RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isModelLoaded = false

    init {
        cargarModelo()
    }

    private fun cargarModelo() {
        Thread {
            try {
                val modelPath = File(context.filesDir, "vosk-model-es").absolutePath
                model = Model(modelPath)
                isModelLoaded = true
                listener.onListo() // Avisamos al listener
            } catch (e: Exception) {
                listener.onError("Error cargando modelo: ${e.message}")
            }
        }.start()
    }

    override fun iniciarEscucha() {
        if (!isModelLoaded || model == null || speechService != null) return
        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
        } catch (e: Exception) {
            listener.onError("Error al iniciar: ${e.message}")
        }
    }

    override fun detenerEscucha() {
        speechService?.stop()
        speechService = null
    }

    override fun destruir() {
        detenerEscucha()
        speechService?.shutdown()
        model?.close()
        model = null
    }

    // --- MÉTODOS DE VOSK ---
    override fun onResult(hypothesis: String?) {
        val texto = JSONObject(hypothesis ?: "{}").optString("text")
        if (texto.isNotEmpty()) listener.onTextoFinal(texto)
    }

    override fun onPartialResult(hypothesis: String?) {
        val textoParcial = JSONObject(hypothesis ?: "{}").optString("partial")
        if (textoParcial.isNotEmpty()) listener.onTextoParcial(textoParcial)
    }

    override fun onFinalResult(hypothesis: String?) { onResult(hypothesis) }
    override fun onError(exception: Exception?) { listener.onError(exception?.message ?: "Error") }
    override fun onTimeout() {}
}