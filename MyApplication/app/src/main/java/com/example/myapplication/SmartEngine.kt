package com.example.myapplication

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/*
 * Motor de reconocimiento que gestiona automáticamente dos motores:
 * - Vosk (local/offline)
 * - Whisper (servidor remoto)
 *
 * Ambos se inicializan en paralelo. Si hay conexión y el servidor responde,
 * se usa Whisper. Si falla la conexión o el servidor deja de responder,
 * el sistema cambia automáticamente a Vosk para continuar funcionando offline.
 *
 * También incluye un mecanismo que intenta recuperar Whisper periódicamente
 * cuando la app está funcionando con Vosk.
 */
class SmartEngine(
    private val context: Context,
    private val whisperUrl: String,
    private val onMotorCambiado: (String) -> Unit = {}
) : RecognitionEngine {

    // Motores disponibles
    private val vosk = Vosk(context)
    private val whisper = Whisper(context, whisperUrl)

    // Estados internos
    @Volatile private var voskListo = false
    @Volatile private var whisperListo = false
    @Volatile private var escuchando = false

    private var listenerPrincipal: EngineListener? = null
    @Volatile private var motorActual: RecognitionEngine? = null

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Cliente HTTP ligero usado únicamente para comprobar si el servidor responde
    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private var recuperadorRunnable: Runnable? = null

    // Comprueba si el dispositivo tiene conexión a internet
    private fun hayConexion(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /*
     * Listener intermedio que intercepta eventos de los motores.
     * Permite reaccionar a errores de Whisper cambiando automáticamente
     * a Vosk sin que la actividad tenga que gestionarlo.
     */
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
                    println("SmartEngine: Whisper falló. Cambiando a Vosk.")
                    forzarCambioAVosk()
                }

            } else {
                listenerPrincipal?.onError(excepcion)
            }
        }
    }

    // Detecta cambios de conectividad del sistema
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {

            // Espera unos segundos antes de intentar recuperar Whisper
            if (escuchando) runOnMain {
                mainHandler.postDelayed({ intentarRecuperarWhisper() }, 3000)
            }
        }

        override fun onLost(network: Network) {

            if (escuchando && motorActual == whisper) {
                runOnMain { forzarCambioAVosk() }
            }
        }
    }

    // Cambia el motor activo a Vosk
    private fun forzarCambioAVosk() {

        if (motorActual == vosk) return

        motorActual?.pararEscucha()

        motorActual = vosk

        listenerPrincipal?.let {
            motorActual?.iniciarEscucha(escudoListener)
        }

        onMotorCambiado("Vosk (Offline)")

        iniciarRadarDeRecuperacion()
    }

    /*
     * Intenta volver a Whisper si hay conexión y el servidor responde
     * correctamente al endpoint de ping.
     */
    private fun intentarRecuperarWhisper() {

        if (!hayConexion() || motorActual == whisper) return

        Thread {

            try {

                val request = Request.Builder()
                    .url("$whisperUrl/api/ping")
                    .get()
                    .build()

                val response = pingClient.newCall(request).execute()

                if (response.isSuccessful) {

                    runOnMain {

                        if (motorActual == vosk && escuchando) {

                            println("SmartEngine: conexión recuperada. Volviendo a Whisper.")

                            motorActual?.pararEscucha()

                            motorActual = whisper

                            listenerPrincipal?.let {
                                motorActual?.iniciarEscucha(escudoListener)
                            }

                            onMotorCambiado("Whisper")

                            pararRadarDeRecuperacion()
                        }
                    }
                }

            } catch (_: Exception) {
                // Si falla el ping simplemente seguimos usando Vosk
            }

        }.start()
    }

    // Inicia un proceso periódico que intenta recuperar Whisper
    private fun iniciarRadarDeRecuperacion() {

        pararRadarDeRecuperacion()

        recuperadorRunnable = object : Runnable {

            override fun run() {

                if (escuchando && motorActual == vosk) {

                    intentarRecuperarWhisper()

                    mainHandler.postDelayed(this, 5000)
                }
            }
        }

        mainHandler.postDelayed(recuperadorRunnable!!, 5000)
    }

    private fun pararRadarDeRecuperacion() {

        recuperadorRunnable?.let {
            mainHandler.removeCallbacks(it)
        }

        recuperadorRunnable = null
    }

    // ---------------- Implementación de RecognitionEngine ---------------- //

    override fun inicializar(onReady: () -> Unit, onError: (Exception) -> Unit) {

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        vosk.inicializar(
            onReady = {
                voskListo = true
                comprobarSiListos(onReady)
            },
            onError = { e ->
                onError(Exception("Vosk: ${e.message}"))
            }
        )

        whisper.inicializar(
            onReady = {
                whisperListo = true
                comprobarSiListos(onReady)
            },
            onError = {
                whisperListo = false
                comprobarSiListos(onReady)
            }
        )
    }

    private var yaAvisado = false

    // Notifica que el sistema está listo cuando al menos un motor lo está
    private fun comprobarSiListos(onReady: () -> Unit) {

        if (!yaAvisado && (voskListo || whisperListo)) {

            yaAvisado = true

            onReady()
        }
    }

    override fun iniciarEscucha(listener: EngineListener) {

        this.listenerPrincipal = listener

        escuchando = true

        // Si hay red y Whisper está listo lo usamos; si no, Vosk
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

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}

        vosk.liberar()
        whisper.liberar()
    }

    private fun runOnMain(block: () -> Unit) {

        mainHandler.post(block)
    }

    override fun estaListo() = voskListo || whisperListo

    override fun nombreMotorActivo(): String =
        if (motorActual == whisper) "Whisper" else "Vosk"
}