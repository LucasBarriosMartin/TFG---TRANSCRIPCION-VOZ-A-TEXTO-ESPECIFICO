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

/*
 * Implementación de RecognitionEngine basada en Whisper.
 *
 * Funciona enviando audio al servidor en tiempo real mediante chunks PCM.
 * El flujo es el siguiente:
 * 1. Se graba audio continuamente con AudioRecord
 * 2. Se divide en bloques (chunks)
 * 3. Se filtran silencios
 * 4. Se envían al servidor en paralelo
 * 5. Se procesan las respuestas de transcripción
 */
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

    // Cliente HTTP con timeouts ajustados para streaming de audio
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SEGUNDOS = 6
        private const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_SEGUNDOS
        private const val CHUNK_BYTES = CHUNK_SAMPLES * 2
        private const val UMBRAL_SILENCIO = 500.0
    }

    // ---------------- RecognitionEngine ---------------- //

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
                    runOnMain {
                        onError(Exception("Servidor respondió ${response.code}"))
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnMain {
                    onError(Exception("Sin conexión al servidor: ${e.message}"))
                }
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

        // Detiene grabación y libera recursos de audio
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        grabacionThread = null
    }

    override fun liberar() {

        pararEscucha()
        listo = false
    }

    override fun estaListo() = listo
    override fun nombreMotorActivo(): String = "Whisper"

    // ---------------- Grabación + envío en paralelo ---------------- //

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

        // Hilo de grabación: genera chunks de audio
        grabacionThread = Thread {

            val pcmBuffer = ByteArray(CHUNK_BYTES)

            while (escuchando && !Thread.currentThread().isInterrupted) {

                var totalLeido = 0

                while (totalLeido < CHUNK_BYTES && escuchando) {

                    val leido = audioRecord?.read(
                        pcmBuffer,
                        totalLeido,
                        CHUNK_BYTES - totalLeido
                    ) ?: break

                    if (leido > 0) totalLeido += leido
                }

                if (totalLeido > 0 && escuchando) {

                    val pcmCopia = pcmBuffer.copyOf(totalLeido)

                    if (!esSilencio(pcmCopia)) {
                        colaChunks.offer(pcmCopia)
                    }
                }
            }
        }.also { it.start() }

        // Hilo de envío: consume la cola y manda al servidor
        envioThread = Thread {

            var chunkIndex = 0

            while ((escuchando || colaChunks.isNotEmpty())
                && !Thread.currentThread().isInterrupted) {

                try {

                    val pcm = colaChunks.poll(1, TimeUnit.SECONDS) ?: continue

                    val wavBytes = pcmAWav(pcm)

                    enviarChunk(wavBytes, "chunk_${chunkIndex++}.wav")

                } catch (e: InterruptedException) {

                    android.util.Log.d(
                        "Whisper",
                        "Hilo de envío interrumpido"
                    )
                    break

                } catch (e: Exception) {

                    android.util.Log.e(
                        "Whisper",
                        "Error en envío: ${e.message}"
                    )
                }
            }
        }.also { it.start() }
    }

    // ---------------- Detección de silencio ---------------- //

    private fun esSilencio(pcm: ByteArray): Boolean {

        var suma = 0.0

        for (i in pcm.indices step 2) {

            val muestra = (
                    (pcm[i].toInt() and 0xFF) or
                            ((pcm[i + 1].toInt() and 0xFF) shl 8)
                    ).toShort()

            suma += muestra * muestra
        }

        val rms = Math.sqrt(suma / (pcm.size / 2))

        android.util.Log.d("Whisper_RMS", "RMS = $rms")

        return rms < UMBRAL_SILENCIO
    }

    // ---------------- Envío al servidor ---------------- //

    private fun enviarChunk(wavBytes: ByteArray, nombreFichero: String) {

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "archivo",
                filename = nombreFichero,
                body = wavBytes.toRequestBody("audio/wav".toMediaType())
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

                val json = JSONObject(bodyString)
                val speaker = json.optString("speaker")
                val texto = json.optString("text")

                if (texto.isNotEmpty()) {

                    val resultado =
                        if (speaker.isNotEmpty())
                            "[$speaker]: $texto"
                        else
                            texto

                    runOnMain {
                        listener?.onResultadoFinal(resultado)
                    }
                }

            } else {

                runOnMain {
                    listener?.onError(
                        Exception("Error servidor: ${response.code}")
                    )
                }
            }

        } catch (e: Exception) {

            runOnMain {
                listener?.onError(
                    Exception("Error de red con Whisper: ${e.message}")
                )
            }
        }
    }

    // ---------------- PCM → WAV ---------------- //

    private fun pcmAWav(pcm: ByteArray): ByteArray {

        val totalDataLen = pcm.size + 36
        val byteRate = SAMPLE_RATE * 2

        val out = ByteArrayOutputStream()

        fun writeInt32LE(v: Int) {
            out.write(
                ByteBuffer.allocate(4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(v)
                    .array()
            )
        }

        fun writeInt16LE(v: Int) {
            out.write(
                ByteBuffer.allocate(2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(v.toShort())
                    .array()
            )
        }

        out.write("RIFF".toByteArray())
        writeInt32LE(totalDataLen)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        writeInt32LE(16)
        writeInt16LE(1)
        writeInt16LE(1)
        writeInt32LE(SAMPLE_RATE)
        writeInt32LE(byteRate)
        writeInt16LE(2)
        writeInt16LE(16)
        out.write("data".toByteArray())
        writeInt32LE(pcm.size)
        out.write(pcm)

        return out.toByteArray()
    }

    private fun runOnMain(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }
}