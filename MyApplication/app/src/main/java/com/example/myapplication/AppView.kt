package com.example.myapplication

import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton

// Clase encargada de gestionar los elementos visuales de la pantalla principal
class AppView(
    private val actividad: AppCompatActivity,
    private val adaptador: TranscriptionAdapter,

    // Acciones que ejecutará la vista cuando el usuario pulse los botones
    private val onPararReanudarClick: () -> Unit,
    private val onLimpiarClick: () -> Unit,
    private val onGuardarClick: () -> Unit,
    private val onConfiguracionClick: () -> Unit
) {

    // Referencias a las vistas del layout
    private val recyclerView: RecyclerView = actividad.findViewById(R.id.recyclerViewTranscripcion)
    private val btnPararReanudar: Button = actividad.findViewById(R.id.btnPararReanudar)
    private val btnLimpiar: Button = actividad.findViewById(R.id.btnLimpiar)
    private val btnGuardar: Button = actividad.findViewById(R.id.btnGuardar)
    private val btnConfiguracion: ImageButton = actividad.findViewById(R.id.btnConfiguracion)
    private val tvMotorActivo: TextView = actividad.findViewById(R.id.tvMotorActivo)

    // Indica si el usuario está viendo el final de la lista
    private var usuarioAlFinal = true

    init {
        configurarRecyclerView()
        configurarBotones()

        // Mantener la pantalla encendida mientras la app está en uso
        actividad.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Muestra en pantalla qué motor de reconocimiento está activo
    fun mostrarMotorActivo(motor: String) {
        tvMotorActivo.text = "🎙 $motor"
    }

    // Configura el RecyclerView que muestra la transcripción
    private fun configurarRecyclerView() {

        recyclerView.layoutManager = LinearLayoutManager(actividad)
        recyclerView.adapter = adaptador

        // Detecta si el usuario está al final de la lista
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                usuarioAlFinal = !recyclerView.canScrollVertically(1)
            }
        })
    }

    // Asocia cada botón con la acción que le corresponde
    private fun configurarBotones() {
        btnPararReanudar.setOnClickListener { onPararReanudarClick() }
        btnLimpiar.setOnClickListener { onLimpiarClick() }
        btnGuardar.setOnClickListener { onGuardarClick() }
        btnConfiguracion.setOnClickListener { onConfiguracionClick() }
    }

    // Permite que la pantalla vuelva a apagarse cuando la actividad se destruye
    fun liberarPantallaEncendida() {
        actividad.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // --- Cambios de estado de la interfaz ---

    // Estado mostrado mientras el motor de reconocimiento se está inicializando
    fun estadoCargando() {
        btnPararReanudar.text = "CARGANDO..."
        btnPararReanudar.isEnabled = false
    }

    // Cambia el texto del botón según si el motor está escuchando o no
    fun estadoListo(escuchando: Boolean) {
        btnPararReanudar.isEnabled = true
        btnPararReanudar.text = if (escuchando) "PARAR" else "REANUDAR"
    }

    // Hace scroll automático al último elemento si el usuario ya estaba al final
    fun hacerScrollAbajo(tamLista: Int) {
        if (usuarioAlFinal && tamLista > 0) {
            recyclerView.post {
                recyclerView.scrollToPosition(tamLista - 1)
            }
        }
    }
}