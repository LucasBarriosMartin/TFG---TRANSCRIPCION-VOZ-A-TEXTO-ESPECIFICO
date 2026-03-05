package com.example.myapplication

import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * VISTA DE LA APP
 */
class Vistapp (
    private val actividad: AppCompatActivity,
    private val adaptador: TranscripcionAdapter,
    // Estas son las "ordenes" que la vista recibirá desde el MainActivity (Lambdas)
    private val onPararReanudarClick: () -> Unit,
    private val onLimpiarClick: () -> Unit,
    private val onGuardarClick: () -> Unit
) {
    // Enganchamos las vistas (findViewById)
    private val recyclerView: RecyclerView = actividad.findViewById(R.id.recyclerViewTranscripcion)
    private val btnPararReanudar: Button = actividad.findViewById(R.id.btnPararReanudar)
    private val btnLimpiar: Button = actividad.findViewById(R.id.btnLimpiar)
    private val btnGuardar: Button = actividad.findViewById(R.id.btnGuardar)

    init {
        configurarRecyclerView()
        configurarBotones()
    }

    private fun configurarRecyclerView() {
        // Le decimos a la lista que se muestre de arriba a abajo
        recyclerView.layoutManager = LinearLayoutManager(actividad)
        recyclerView.adapter = adaptador
    }

    private fun configurarBotones() {
        // Cuando el usuario pulse mis botones, ejecuto las funciones que me pasó el MainActivity
        btnPararReanudar.setOnClickListener { onPararReanudarClick() }
        btnLimpiar.setOnClickListener { onLimpiarClick() }
        btnGuardar.setOnClickListener { onGuardarClick() }
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
        val usuarioAbajo = !recyclerView.canScrollVertically(1)
        if (usuarioAbajo && tamLista > 0) {
            recyclerView.smoothScrollToPosition(tamLista - 1)
        }
    }
}