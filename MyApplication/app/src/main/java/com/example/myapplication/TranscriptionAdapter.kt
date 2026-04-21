package com.example.myapplication

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Adaptador del RecyclerView que muestra las frases transcritas
class TranscriptionAdapter(private val frases: List<String>) :
    RecyclerView.Adapter<TranscriptionAdapter.ViewHolder>() {

    // Configuración visual actual (se actualiza desde AccessibilityManager)
    private var colorTextoActual: Int = Color.WHITE
    private var tamanoLetraActual: Float = 20f
    private var colorFondoActual: Int = Color.BLACK

    // ViewHolder que contiene la vista de cada frase
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFrase: TextView = view.findViewById(R.id.tvFrase)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transcripcion, parent, false)
    )

    // Vincula cada frase con su vista correspondiente
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        holder.tvFrase.text = frases[position]

        // Aplicar configuración visual actual
        holder.tvFrase.textSize = tamanoLetraActual
        holder.tvFrase.setTextColor(colorTextoActual)

        holder.itemView.setBackgroundColor(colorFondoActual)
    }

    override fun getItemCount() = frases.size

    // --- Configuración visual dinámica ---

    fun setColorTexto(color: Int) {
        colorTextoActual = color
        notifyDataSetChanged()
    }

    fun setTamanoLetra(size: Float) {
        tamanoLetraActual = size
        notifyDataSetChanged()
    }

    fun setColorFondo(color: Int) {
        colorFondoActual = color
        notifyDataSetChanged()
    }
}