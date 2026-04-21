package com.example.myapplication

// Interfaz que define los callbacks del motor de reconocimiento.
// La implementa la actividad que quiere recibir resultados del motor.
interface EngineListener {

    // Texto parcial mientras el usuario sigue hablando
    fun onResultadoParcial(texto: String)

    // Resultado final cuando se completa una frase
    fun onResultadoFinal(texto: String)

    // Notifica cualquier error ocurrido durante el reconocimiento
    fun onError(excepcion: Exception)
}