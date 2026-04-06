package com.example.myapplication

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Motor inteligente: inicializa Vosk y Whisper en paralelo.
 * Cambia automáticamente entre motores según si tenemos conexión al servidor (enviando un ping).
 */

class SmartEngine(
    private val context: Context,
    private val whisperUrl: String, // <-- AÑADIDO 'private val' para usarlo en el radar
    private val onMotorCambiado: (String) -> Unit = {}
) : RecognitionEngine {

    private val vosk = Vosk(context)
    private val whisper = Whisper(context, whisperUrl)

    @Volatile private var voskListo = false
    @Volatile private var whisperListo = false
    @Volatile private var escuchando = false
    private var listenerPrincipal: EngineListener? = null

    @Volatile private var motorActual: RecognitionEngine? = null

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // 📡 EL RADAR: Un cliente ultraligero solo para hacer "pings"
    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private var recuperadorRunnable: Runnable? = null

    private fun hayConexion(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // --- 🛡️ EL ESCUDO INTERCEPTOR 🛡️ ---
    private val escudoListener = object : EngineListener {
        override fun onResultadoParcial(texto: String) {
            listenerPrincipal?.onResultadoParcial(texto)
        }

        override fun onResultadoFinal(texto: String) {
            listenerPrincipal?.onResultadoFinal(texto)
        }

        override fun onError(excepcion: Exception) {
            if (motorActual == whisper) {
                runOnMain {
                    println("SmartEngine: Whisper falló. Pasando a Vosk y activando Radar.")
                    forzarCambioAVosk()
                }
            } else {
                listenerPrincipal?.onError(excepcion)
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Le damos 3 segundos de margen a Tailscale para que abra el túnel
            if (escuchando) runOnMain {
                mainHandler.postDelayed({ intentarRecuperarWhisper() }, 3000)
            }
        }
        override fun onLost(network: Network) {
            if (escuchando && motorActual == whisper) runOnMain { forzarCambioAVosk() }
        }
    }

    private fun forzarCambioAVosk() {
        if (motorActual == vosk) return

        motorActual?.pararEscucha()
        motorActual = vosk

        listenerPrincipal?.let { motorActual?.iniciarEscucha(escudoListener) }
        onMotorCambiado("Vosk (Offline)")

        iniciarRadarDeRecuperacion() // Encendemos el radar
    }

    private fun intentarRecuperarWhisper() {
        if (!hayConexion() || motorActual == whisper) return

        Thread {
            try {
                // Hacemos la prueba de fuego: ¿Cacharrín está realmente ahí?
                val request = Request.Builder().url("$whisperUrl/api/ping").get().build()
                val response = pingClient.newCall(request).execute()

                if (response.isSuccessful) {
                    runOnMain {
                        // Si estamos en Vosk y Cacharrín responde, ¡volvemos a Whisper!
                        if (motorActual == vosk && escuchando) {
                            println("SmartEngine: ¡Conexión recuperada! Volviendo a Whisper.")
                            motorActual?.pararEscucha()
                            motorActual = whisper
                            listenerPrincipal?.let { motorActual?.iniciarEscucha(escudoListener) }
                            onMotorCambiado("Whisper")
                            pararRadarDeRecuperacion() // Apagamos el radar
                        }
                    }
                }
            } catch (e: Exception) {
                // Silencio. El servidor aún no está listo. Seguimos en Vosk.
            }
        }.start()
    }

    private fun iniciarRadarDeRecuperacion() {
        pararRadarDeRecuperacion()
        recuperadorRunnable = object : Runnable {
            override fun run() {
                if (escuchando && motorActual == vosk) {
                    intentarRecuperarWhisper()
                    mainHandler.postDelayed(this, 5000) // Volver a lanzar el radar en 5 seg
                }
            }
        }
        mainHandler.postDelayed(recuperadorRunnable!!, 5000)
    }

    private fun pararRadarDeRecuperacion() {
        recuperadorRunnable?.let { mainHandler.removeCallbacks(it) }
        recuperadorRunnable = null
    }

    // ---------------- RecognitionEngine ---------------- //

    override fun inicializar(onReady: () -> Unit, onError: (Exception) -> Unit) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        vosk.inicializar(
            onReady = { voskListo = true; comprobarSiListos(onReady) },
            onError = { e -> onError(Exception("Vosk: ${e.message}")) }
        )

        whisper.inicializar(
            onReady = {
                whisperListo = true
                comprobarSiListos(onReady)
            },
            onError = { whisperListo = false; comprobarSiListos(onReady) }
        )
    }

    private var yaAvisado = false
    private fun comprobarSiListos(onReady: () -> Unit) {
        if (!yaAvisado && (voskListo || whisperListo)) {
            yaAvisado = true
            onReady()
        }
    }

    override fun iniciarEscucha(listener: EngineListener) {
        this.listenerPrincipal = listener
        escuchando = true

        // Intentamos empezar con Whisper si hay red, si no, Vosk y activamos radar
        if (hayConexion() && whisperListo) {
            motorActual = whisper
        } else {
            motorActual = vosk
            iniciarRadarDeRecuperacion()
        }

        motorActual?.iniciarEscucha(escudoListener)
        onMotorCambiado(if (motorActual == whisper) "Whisper" else "Vosk")
    }

    override fun pararEscucha() {
        escuchando = false
        pararRadarDeRecuperacion()
        motorActual?.pararEscucha()
    }

    override fun liberar() {
        escuchando = false
        pararRadarDeRecuperacion()
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (_: Exception) {}
        vosk.liberar()
        whisper.liberar()
    }

    private fun runOnMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    override fun estaListo() = voskListo || whisperListo
    override fun nombreMotorActivo(): String = if (motorActual == whisper) "Whisper" else "Vosk"
}