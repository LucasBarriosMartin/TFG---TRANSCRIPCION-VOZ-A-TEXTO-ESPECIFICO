package com.example.myapplication

interface InterfazModelo {
    fun iniciarEscucha()
    fun detenerEscucha()
    fun destruir()

    interface Listener {
        fun onListo()
        fun onTextoFinal(texto: String)
        fun onTextoParcial(texto: String)
        fun onError(mensaje: String)
    }

}