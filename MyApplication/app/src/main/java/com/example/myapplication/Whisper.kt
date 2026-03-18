package com.example.myapplication

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class Whisper(
    private val context: Context,
    private val serverUrl: String
) : RecognitionEngine {

    @Volatile private var listo = false
    @Volatile private var escuchando = false
    private var listener: EngineListener? = null
    private var audioRecord: AudioRecord? = null
    private var grabacionThread: Thread? = null
    private var envioThread: Thread? = null
    private val colaChunks = ArrayBlockingQueue<ByteArray>(10)

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val SAMPLE_RATE    = 16000
        private const val CHUNK_SEGUNDOS = 6
        private const val CHUNK_SAMPLES  = SAMPLE_RATE * CHUNK_SEGUNDOS
        private const val CHUNK_BYTES    = CHUNK_SAMPLES * 2
        private const val UMBRAL_SILENCIO = 500.0
    }

    // ------------------------------------------------------------------ //
    //  RecognitionEngine
    // ------------------------------------------------------------------ //

    override fun inicializar(onReady: () -> Unit, onError: (Exception) -> Unit) {
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
                runOnMain { onError(Exception("Sin conexión al servidor: ${e.message}")) }
            }
        })
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun iniciarEscucha(listener: EngineListener) {
        if (escuchando) return
        this.listener = listener
        escuchando = true
        colaChunks.clear()
        iniciarGrabacionPorChunks()
    }

    override fun pararEscucha() {
        escuchando = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        grabacionThread?.interrupt()
        grabacionThread = null
        envioThread?.interrupt()
        envioThread = null
        colaChunks.clear()
    }

    override fun liberar() {
        pararEscucha()
        listo = false
    }

    override fun estaListo() = listo
    override fun nombreMotorActivo(): String = "Whisper"

    // ------------------------------------------------------------------ //
    //  Grabación continua + envío en paralelo
    // ------------------------------------------------------------------ //

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
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

        // Hilo 1: graba sin parar y mete chunks en la cola
        grabacionThread = Thread {
            val pcmBuffer = ByteArray(CHUNK_BYTES)

            while (escuchando && !Thread.currentThread().isInterrupted) {
                var totalLeido = 0
                while (totalLeido < CHUNK_BYTES && escuchando) {
                    val leido = audioRecord?.read(
                        pcmBuffer, totalLeido, CHUNK_BYTES - totalLeido
                    ) ?: break
                    if (leido > 0) totalLeido += leido
                }

                if (totalLeido > 0 && escuchando) {
                    val pcmCopia = pcmBuffer.copyOf(totalLeido)
                    if (!esSilencio(pcmCopia)) {
                        // offer() descarta el chunk si la cola está llena (evita acumulación)
                        colaChunks.offer(pcmCopia)
                    }
                }
            }
        }.also { it.start() }

        // Hilo 2: consume la cola y envía al servidor
        envioThread = Thread {
            var chunkIndex = 0

            while (escuchando && !Thread.currentThread().isInterrupted) {
                // Espera hasta 1 segundo a que haya un chunk disponible
                val pcm = colaChunks.poll(1, TimeUnit.SECONDS) ?: continue
                val wavBytes = pcmAWav(pcm)
                enviarChunk(wavBytes, "chunk_${chunkIndex++}.wav")
            }
        }.also { it.start() }
    }

    // ------------------------------------------------------------------ //
    //  Detección de silencio por RMS
    // ------------------------------------------------------------------ //

    private fun esSilencio(pcm: ByteArray): Boolean {
        var suma = 0.0
        for (i in pcm.indices step 2) {
            val muestra = ((pcm[i].toInt() and 0xFF) or ((pcm[i + 1].toInt() and 0xFF) shl 8)).toShort()
            suma += muestra * muestra
        }
        val rms = Math.sqrt(suma / (pcm.size / 2))
        android.util.Log.d("Whisper_RMS", "RMS = $rms")
        return rms < UMBRAL_SILENCIO
    }

    // ------------------------------------------------------------------ //
    //  Envío del chunk al servidor
    // ------------------------------------------------------------------ //

    private fun enviarChunk(wavBytes: ByteArray, nombreFichero: String) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name     = "archivo",
                filename = nombreFichero,
                body     = wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("$serverUrl/api/transcribir")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()

            if (response.isSuccessful && bodyString != null) {
                val json    = JSONObject(bodyString)
                val speaker = json.optString("speaker")
                val texto   = json.optString("text")

                if (texto.isNotEmpty()) {
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
    // ------------------------------------------------------------------ //

    private fun pcmAWav(pcm: ByteArray): ByteArray {
        val totalDataLen = pcm.size + 36
        val byteRate     = SAMPLE_RATE * 2

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
        writeInt32LE(16)
        writeInt16LE(1)           // PCM
        writeInt16LE(1)           // Mono
        writeInt32LE(SAMPLE_RATE)
        writeInt32LE(byteRate)
        writeInt16LE(2)           // Block align
        writeInt16LE(16)          // Bits por muestra
        out.write("data".toByteArray())
        writeInt32LE(pcm.size)
        out.write(pcm)

        return out.toByteArray()
    }

    private fun runOnMain(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }
}