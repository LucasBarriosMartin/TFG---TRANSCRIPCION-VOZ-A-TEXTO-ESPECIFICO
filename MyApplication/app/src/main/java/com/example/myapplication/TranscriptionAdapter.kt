package com.example.myapplication

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TranscriptionAdapter(private val frases: List<String>) :
    RecyclerView.Adapter<TranscriptionAdapter.ViewHolder>() {

    private var colorTextoActual: Int = Color.WHITE
    private var tamanoLetraActual: Float = 20f

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFrase: TextView = view.findViewById(R.id.tvFrase)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transcripcion, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val frase = frases[position]

        holder.tvFrase.text = frase
        holder.tvFrase.textSize = tamanoLetraActual
        holder.tvFrase.setTextColor(colorTextoActual)
    }

    override fun getItemCount() = frases.size

    fun setColorTexto(color: Int) {
        colorTextoActual = color
        notifyDataSetChanged()
    }

    fun setTamanoLetra(size: Float) {
        tamanoLetraActual = size
        notifyDataSetChanged()
    }
}