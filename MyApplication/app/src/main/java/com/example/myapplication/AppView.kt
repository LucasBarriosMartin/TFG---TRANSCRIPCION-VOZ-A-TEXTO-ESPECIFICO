package com.example.myapplication

import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * VISTA DE LA APP
 */
class AppView (
    private val actividad: AppCompatActivity,
    private val adaptador: TranscriptionAdapter,
    // Estas son las "ordenes" que la vista recibirá desde el MainActivity (Lambdas)
    private val onPararReanudarClick: () -> Unit,
    private val onLimpiarClick: () -> Unit,
    private val onGuardarClick: () -> Unit,
    private val onConfiguracionClick: () -> Unit
) {
    // Enganchamos las vistas (findViewById)
    private val recyclerView: RecyclerView = actividad.findViewById(R.id.recyclerViewTranscripcion)
    private val btnPararReanudar: Button = actividad.findViewById(R.id.btnPararReanudar)
    private val btnLimpiar: Button = actividad.findViewById(R.id.btnLimpiar)
    private val btnGuardar: Button = actividad.findViewById(R.id.btnGuardar)
    private val btnConfiguracion: Button = actividad.findViewById(R.id.btnConfiguracion)

    private val tvMotorActivo: TextView = actividad.findViewById(R.id.tvMotorActivo)

    // Variable que trackea continuamente si el usuario está al final
    private var usuarioAlFinal = true

    init {
        configurarRecyclerView()
        configurarBotones()
        actividad.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun mostrarMotorActivo(motor: String) {
        tvMotorActivo.text = "🎙 $motor"
    }

    private fun configurarRecyclerView() {
        // Le decimos a la lista que se muestre de arriba a abajo
        recyclerView.layoutManager = LinearLayoutManager(actividad)
        recyclerView.adapter = adaptador

        // OnScrollListener actualiza esa variable en tiempo real
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                usuarioAlFinal = !recyclerView.canScrollVertically(1)
            }
        })

    }

    private fun configurarBotones() {
        // Cuando el usuario pulse mis botones, ejecuto las funciones que me pasó el MainActivity
        btnPararReanudar.setOnClickListener { onPararReanudarClick() }
        btnLimpiar.setOnClickListener { onLimpiarClick() }
        btnGuardar.setOnClickListener { onGuardarClick() }
        btnConfiguracion.setOnClickListener { onConfiguracionClick() }
    }

    fun liberarPantallaEncendida() {
        actividad.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // --- FUNCIONES PARA CAMBIAR LA UI DESDE EL MAIN ACTIVITY ---

    // Cambia el botón de grabar cuando Vosk está leyendo el modelo
    fun estadoCargando() {
        btnPararReanudar.text = "CARGANDO..."
        btnPararReanudar.isEnabled = false
    }

    // Cambia el botón entre PARAR y REANUDAR
    fun estadoListo(escuchando: Boolean) {
        btnPararReanudar.isEnabled = true
        btnPararReanudar.text = if (escuchando) "PARAR" else "REANUDAR"
    }

    // Baja la pantalla automáticamente para que el usuario vea la última frase
    fun hacerScrollAbajo(tamLista: Int) {
        if (usuarioAlFinal && tamLista > 0) {
            recyclerView.post {   // ← espera a que el item esté dibujado
                recyclerView.scrollToPosition(tamLista - 1)
            }
        }
    }
}