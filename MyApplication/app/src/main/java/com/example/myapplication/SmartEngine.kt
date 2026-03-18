package com.example.myapplication

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Motor inteligente: inicializa Vosk y Whisper en paralelo.
 * Cambia automáticamente entre motores según conexión a internet.
 */
class SmartEngine(
    private val context: Context,
    whisperUrl: String,
    private val onMotorCambiado: (String) -> Unit = {}
) : RecognitionEngine {

    private val vosk = Vosk(context)
    private val whisper = Whisper(context, whisperUrl)

    @Volatile  private var voskListo = false
    @Volatile  private var whisperListo = false
    @Volatile  private var escuchando = false
    private var listener: EngineListener? = null
    @Volatile  private var motorActual: RecognitionEngine? = null

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val motorActivo: RecognitionEngine
        get() = if (hayConexion() && whisperListo) whisper else vosk

    private fun hayConexion(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (escuchando && whisperListo) cambiarMotor()
        }

        override fun onLost(network: Network) {
            if (escuchando && voskListo) cambiarMotor()
        }
    }

    private fun cambiarMotor() {
        val nuevoMotor = motorActivo

        if (motorActual == nuevoMotor) return

        motorActual?.pararEscucha()
        motorActual = nuevoMotor

        listener?.let { motorActual?.iniciarEscucha(it) }

        val nombre = if (nuevoMotor == whisper) "Whisper" else "Vosk"
        onMotorCambiado(nombre)
    }

    // ---------------- RecognitionEngine ---------------- //

    override fun inicializar(onReady: () -> Unit, onError: (Exception) -> Unit) {
        // Registrar escucha de cambios de red
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        vosk.inicializar(
            onReady = {
                voskListo = true
                comprobarSiListos(onReady)
            },
            onError = { e -> onError(Exception("Vosk: ${e.message}")) }
        )

        whisper.inicializar(
            onReady = {
                whisperListo = true
                // Si estamos escuchando y hay internet, cambiar automáticamente a Whisper
                if (escuchando && hayConexion()) cambiarMotor()
                comprobarSiListos(onReady)
            },
            onError = {
                whisperListo = false
                comprobarSiListos(onReady)
            }
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
        this.listener = listener
        escuchando = true

        motorActual = motorActivo
        motorActual?.iniciarEscucha(listener)
    }

    override fun pararEscucha() {
        escuchando = false
        motorActual?.pararEscucha()
    }

    override fun liberar() {
        escuchando = false
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}

        vosk.pararEscucha()
        whisper.pararEscucha()
        vosk.liberar()
        whisper.liberar()

        motorActual = null
        listener = null
    }

    override fun estaListo() = voskListo || whisperListo
    override fun nombreMotorActivo(): String = motorActivo.nombreMotorActivo()
}