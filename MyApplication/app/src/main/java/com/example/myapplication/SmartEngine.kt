package com.example.myapplication

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Motor inteligente: inicializa Vosk y Whisper en PARALELO al arrancar la app.
 * Decide cuál usar según el estado de la conexión a internet.
 *
 * Reglas:
 *   - Hay internet + Whisper listo  → usa Whisper (más preciso)
 *   - Sin internet O servidor caído → usa Vosk (local, sin conexión)
 *   - La red cambia en tiempo real  → cambia de motor automáticamente
 *
 * MainActivity solo habla con SmartEngine, nunca directamente con Vosk o Whisper.
 */
class SmartEngine(
    private val context: Context,
    whisperUrl: String,
    private val onMotorCambiado: (String) -> Unit = {}
) : RecognitionEngine {

    // 1. NUESTROS DOS MOTORES
    private val vosk    = Vosk(context)
    private val whisper = Whisper(context, whisperUrl)

    private var voskListo    = false
    private var whisperListo = false
    private var escuchando   = false
    private var listener: EngineListener? = null

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // ------------------------------------------------------------------ //
    //  Selección del motor activo según conectividad
    // ------------------------------------------------------------------ //

    private val motorActivo: RecognitionEngine
        get() = if (hayConexion() && whisperListo) whisper else vosk

    private fun hayConexion(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps    = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // ------------------------------------------------------------------ //
    //  Escuchar cambios de red en tiempo real
    //  Si la red cambia mientras el usuario habla, cambiamos de motor
    //  sin que tenga que hacer nada.
    // ------------------------------------------------------------------ //

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Recuperó conexión → si estaba escuchando con Vosk, cambiar a Whisper
            if (escuchando && whisperListo) cambiarMotor()
        }
        override fun onLost(network: Network) {
            // Perdió conexión → si estaba escuchando con Whisper, cambiar a Vosk
            if (escuchando && voskListo) cambiarMotor()
        }
    }

    private fun cambiarMotor() {
        // Para el motor que NO queremos y arranca el que SÍ queremos
        val motorAnterior = if (hayConexion()) vosk else whisper
        motorAnterior.pararEscucha()
        motorActivo.iniciarEscucha(listener ?: return)

        val nombre = if (hayConexion() && whisperListo) "Whisper" else "Vosk"
        onMotorCambiado(nombre)
    }

    // ------------------------------------------------------------------ //
    //  RecognitionEngine
    // ------------------------------------------------------------------ //

    override fun inicializar(onReady: () -> Unit, onError: (Exception) -> Unit) {
        // Registrar escucha de cambios de red
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Lanzar AMBOS motores en paralelo para no perder tiempo
        vosk.inicializar(
            onReady = {
                voskListo = true
                comprobarSiListos(onReady)
            },
            onError = { e ->
                // Vosk falló — avisamos pero seguimos (quizás Whisper funciona)
                onError(Exception("Vosk: ${e.message}"))
            }
        )

        whisper.inicializar(
            onReady = {
                whisperListo = true
                comprobarSiListos(onReady)
            },
            onError = {
                // Servidor no disponible — no es error fatal, usaremos Vosk
                whisperListo = false
                comprobarSiListos(onReady)
            }
        )
    }

    /**
     * Llamamos onReady() en cuanto AL MENOS UNO de los motores esté listo.
     * Así el usuario no espera a que carguen los dos.
     * El segundo motor que termine simplemente se queda listo en segundo plano.
     */
    private var yaAvisado = false
    private fun comprobarSiListos(onReady: () -> Unit) {
        if (!yaAvisado && (voskListo || whisperListo)) {
            yaAvisado = true
            onReady()
        }
    }

    override fun iniciarEscucha(listener: EngineListener) {
        this.listener = listener
        escuchando = true
        motorActivo.iniciarEscucha(listener)
    }

    override fun pararEscucha() {
        escuchando = false
        // Paramos los dos por si acaso (el que no esté activo lo ignora)
        vosk.pararEscucha()
        whisper.pararEscucha()
    }

    override fun liberar() {
        escuchando = false
        connectivityManager.unregisterNetworkCallback(networkCallback)
        vosk.liberar()
        whisper.liberar()
    }

    override fun estaListo() = voskListo || whisperListo

    override fun nombreMotorActivo(): String = motorActivo.nombreMotorActivo()


}