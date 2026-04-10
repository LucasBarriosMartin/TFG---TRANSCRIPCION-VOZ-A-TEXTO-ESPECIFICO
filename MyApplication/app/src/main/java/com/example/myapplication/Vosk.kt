package com.example.myapplication

import android.content.Context
import android.content.res.AssetManager
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class Vosk(private val context: Context) : RecognitionEngine, RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var listener: EngineListener? = null
    private var listo = false

    override fun inicializar(onReady: () -> Unit, onError: (Exception) -> Unit) {
        Thread {
            try {
                // Solo cargamos el modelo de texto (Súper rápido y ligero)
                val modelPath = copyModelFromAssets("vosk-model-small-es-0.42")
                val modeloTexto = Model(modelPath)

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    model = modeloTexto
                    listo = true
                    onReady()
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError(Exception("Error cargando Vosk: ${e.message}"))
                }
            }
        }.start()
    }

    override fun iniciarEscucha(listener: EngineListener) {
        if (!listo || model == null || speechService != null) return
        this.listener = listener

        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
        } catch (e: Exception) {
            listener.onError(e)
        }
    }

    override fun pararEscucha() {
        speechService?.stop()
        speechService = null
    }

    override fun liberar() {
        try {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            model?.close()
            model = null
            listo = false
        } catch (_: Exception) {}
    }

    override fun estaListo() = listo

    // --- PROCESAMIENTO DE RESULTADOS (Solo texto) ---

    override fun onPartialResult(hypothesis: String?) {
        hypothesis ?: return
        val texto = JSONObject(hypothesis).optString("partial")
        if (texto.isNotEmpty()) listener?.onResultadoParcial(texto)
    }

    override fun onResult(hypothesis: String?) {
        hypothesis ?: return
        val texto = JSONObject(hypothesis).optString("text")
        if (texto.isNotEmpty()) listener?.onResultadoFinal(texto)
    }

    override fun onFinalResult(hypothesis: String?) = onResult(hypothesis)
    override fun onError(e: java.lang.Exception?) { e?.let { listener?.onError(it) } }
    override fun onTimeout() {}
    override fun nombreMotorActivo(): String = "Vosk (Offline)"

    // --- COPIADO DE ARCHIVOS ---
    private fun copyModelFromAssets(modelName: String): String {
        val outDir = File(context.filesDir, modelName)
        if (outDir.exists()) return outDir.absolutePath
        outDir.mkdirs()
        copyAssetFolder(context.assets, modelName, outDir.absolutePath)
        return outDir.absolutePath
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toPath: String) {
        val files = assetManager.list(fromAssetPath) ?: return
        File(toPath).mkdirs()
        for (file in files) {
            val fullPath = "$fromAssetPath/$file"
            if (assetManager.list(fullPath).isNullOrEmpty()) {
                copyAsset(assetManager, fullPath, "$toPath/$file")
            } else {
                copyAssetFolder(assetManager, fullPath, "$toPath/$file")
            }
        }
    }

    private fun copyAsset(assetManager: AssetManager, fromAssetPath: String, toPath: String) {
        assetManager.open(fromAssetPath).use { input ->
            FileOutputStream(toPath).use { output -> input.copyTo(output) }
        }
    }
}