package com.example.myapplication

/**
 * Interfaz que abstrae cualquier motor de reconocimiento de voz.
 * Tanto Vosk como Whisper deben implementarla.
 *
 * Es el "contrato de entrada": define lo que MainActivity puede pedirle
 * a cualquier motor, sin saber cuál es.
 */
interface RecognitionEngine {

    /**
     * Inicializa el motor (cargar modelo, conectar al servidor, etc.)
     * @param onReady Se llama cuando el motor está listo para escuchar.
     * @param onError Se llama si hay un error durante la inicialización.
     */
    fun inicializar(onReady: () -> Unit, onError: (Exception) -> Unit)

    /**
     * Empieza a escuchar y transcribir audio.
     * @param listener Recibe los resultados parciales y finales.
     */
    fun iniciarEscucha(listener: EngineListener)

    /**
     * Para la escucha activa.
     */
    fun pararEscucha()

    /**
     * Libera todos los recursos (modelo, conexión, hilos, etc.)
     * Llamar en onDestroy().
     */
    fun liberar()

    /**
     * @return true si el motor ya ha sido inicializado y está listo.
     */
    fun estaListo(): Boolean

    fun nombreMotorActivo(): String
}