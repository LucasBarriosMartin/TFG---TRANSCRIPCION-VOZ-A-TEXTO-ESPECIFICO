package com.example.myapplication

/**
 * Callbacks que recibe quien usa un RecognitionEngine.
 * Es el equivalente al RecognitionListener de Vosk, pero agnóstico al motor.
 *
 * Lo implementa MainActivity para recibir resultados de AMBOS motores
 * (Vosk y Whisper) con el mismo idioma, sin saber cuál está activo.
 */
interface EngineListener {

    /**
     * Resultado parcial mientras el usuario habla (texto provisional).
     * Vosk lo emite en tiempo real. Whisper puede emitirlo si el servidor lo soporta.
     */
    fun onResultadoParcial(texto: String)

    /**
     * Resultado final de una frase completa.
     * Vosk lo emite al detectar silencio. Whisper al recibir la respuesta del servidor.
     */
    fun onResultadoFinal(texto: String)

    /**
     * Error durante la transcripción.
     */
    fun onError(excepcion: Exception)
}