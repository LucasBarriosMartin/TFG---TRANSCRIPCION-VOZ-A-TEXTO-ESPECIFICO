package com.example.myapplication

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Implementación de RecognitionEngine que envía audio al servidor Whisper.
 *
 * Estrategia de chunks:
 *   1. Graba CHUNK_SEGUNDOS de audio PCM en un hilo secundario
 *   2. Lo convierte a WAV (añadiendo la cabecera estándar)
 *   3. Lo envía al servidor via POST multipart/form-data
 *   4. Recibe JSON con "speaker" y "text" y lo emite por EngineListener
 *   5. Repite mientras escuchando == true
 */
class Whisper(
    private val context: Context,
    private val serverUrl: String  // Ej: "http://192.168.1.100:8000"
) : RecognitionEngine {

    private var listo = false
    private var escuchando = false
    private var listener: EngineListener? = null
    private var audioRecord: AudioRecord? = null
    private var grabacionThread: Thread? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)   // Whisper puede tardar un poco
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val SAMPLE_RATE    = 16000
        private const val CHUNK_SEGUNDOS = 3          // Enviar al servidor cada 3 segundos
        private const val CHUNK_SAMPLES  = SAMPLE_RATE * CHUNK_SEGUNDOS
        private const val CHUNK_BYTES    = CHUNK_SAMPLES * 2  // 16bit = 2 bytes por muestra
    }

    // ------------------------------------------------------------------ //
    //  RecognitionEngine
    // ------------------------------------------------------------------ //

    override fun inicializar(onReady: () -> Unit, onError: (Exception) -> Unit) {
        // Ping al servidor para comprobar que está vivo antes de usarlo
        val request = Request.Builder()
            .url("$serverUrl/api/ping")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    listo = true
                    runOnMain { onReady() }
                } else {
                    runOnMain { onError(Exception("Servidor respondió ${response.code}")) }
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                // El servidor no está disponible — SmartEngine usará Vosk en su lugar
                runOnMain { onError(Exception("Sin conexión al servidor: ${e.message}")) }
            }
        })
    }

    override fun iniciarEscucha(listener: EngineListener) {
        if (escuchando) return
        this.listener = listener
        escuchando = true
        iniciarGrabacionPorChunks()
    }

    override fun pararEscucha() {
        escuchando = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        grabacionThread?.interrupt()
        grabacionThread = null
    }

    override fun liberar() {
        pararEscucha()
        listo = false
    }

    override fun estaListo() = listo

    // ------------------------------------------------------------------ //
    //  Grabación → chunk → WAV → servidor → JSON
    // ------------------------------------------------------------------ //

    override fun nombreMotorActivo(): String = "Whisper"

    private fun iniciarGrabacionPorChunks() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, CHUNK_BYTES)
        )
        audioRecord?.startRecording()

        grabacionThread = Thread {
            val pcmBuffer = ByteArray(CHUNK_BYTES)
            var chunkIndex = 0

            while (escuchando && !Thread.currentThread().isInterrupted) {
                // Leemos exactamente CHUNK_BYTES de audio PCM
                var totalLeido = 0
                while (totalLeido < CHUNK_BYTES && escuchando) {
                    val leido = audioRecord?.read(
                        pcmBuffer, totalLeido, CHUNK_BYTES - totalLeido
                    ) ?: break
                    if (leido > 0) totalLeido += leido
                }

                if (totalLeido > 0 && escuchando) {
                    // Convertimos PCM crudo a WAV con cabecera estándar
                    val wavBytes = pcmAWav(pcmBuffer.copyOf(totalLeido))
                    enviarChunk(wavBytes, "chunk_${chunkIndex++}.wav")
                }
            }
        }.also { it.start() }
    }

    private fun enviarChunk(wavBytes: ByteArray, nombreFichero: String) {
        // Mismo formato multipart que espera vuestro servidor FastAPI
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name      = "archivo",       // Mismo nombre que en el endpoint Python
                filename  = nombreFichero,
                body      = wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("$serverUrl/api/transcribir")
            .post(requestBody)
            .build()

        // Llamada síncrona — ya estamos en un hilo secundario (grabacionThread)
        try {
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()

            if (response.isSuccessful && bodyString != null) {
                val json    = JSONObject(bodyString)
                val speaker = json.optString("speaker")
                val texto   = json.optString("text")

                if (texto.isNotEmpty()) {
                    // Formato final: "[Nayra]: Texto transcrito"
                    val textoFormateado = if (speaker.isNotEmpty()) "[$speaker]: $texto" else texto
                    runOnMain { listener?.onResultadoFinal(textoFormateado) }
                }
            }
        } catch (e: IOException) {
            runOnMain { listener?.onError(Exception("Error enviando chunk: ${e.message}")) }
        }
    }

    // ------------------------------------------------------------------ //
    //  PCM → WAV
    //  Añade la cabecera WAV estándar para que Whisper entienda el audio
    // ------------------------------------------------------------------ //

    private fun pcmAWav(pcm: ByteArray): ByteArray {
        val totalDataLen  = pcm.size + 36
        val byteRate      = SAMPLE_RATE * 2  // Mono 16bit

        val out = ByteArrayOutputStream()

        fun writeInt32LE(v: Int) {
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        }
        fun writeInt16LE(v: Int) {
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())
        }

        out.write("RIFF".toByteArray())
        writeInt32LE(totalDataLen)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        writeInt32LE(16)            // Tamaño bloque fmt
        writeInt16LE(1)             // PCM sin compresión
        writeInt16LE(1)             // Mono
        writeInt32LE(SAMPLE_RATE)
        writeInt32LE(byteRate)
        writeInt16LE(2)             // Block align
        writeInt16LE(16)            // Bits por muestra
        out.write("data".toByteArray())
        writeInt32LE(pcm.size)
        out.write(pcm)

        return out.toByteArray()
    }

    private fun runOnMain(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }
}