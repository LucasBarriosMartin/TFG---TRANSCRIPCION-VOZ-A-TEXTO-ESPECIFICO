package com.example.myapplication

/*
 * Interfaz común para cualquier motor de reconocimiento de voz.
 *
 * La idea es que la aplicación no dependa de una implementación concreta.
 * Motores como Vosk o Whisper deben implementar esta interfaz para que
 * MainActivity pueda usarlos sin saber cuál está funcionando realmente.
 */
interface RecognitionEngine {

    // Inicializa el motor (cargar modelo local, abrir conexión, etc.)
    // onReady se ejecuta cuando el motor está listo para empezar a escuchar
    // onError se ejecuta si ocurre algún problema durante la inicialización
    fun inicializar(onReady: () -> Unit, onError: (Exception) -> Unit)

    // Empieza a escuchar audio del micrófono y enviar resultados al listener
    fun iniciarEscucha(listener: EngineListener)

    // Detiene la escucha activa
    fun pararEscucha()

    // Libera recursos usados por el motor (modelo, red, hilos...)
    // Se suele llamar desde onDestroy() de la actividad
    fun liberar()

    // Indica si el motor ya está preparado para usarse
    fun estaListo(): Boolean

    // Devuelve el nombre del motor que está funcionando
    // (útil para mostrarlo en la interfaz)
    fun nombreMotorActivo(): String
}