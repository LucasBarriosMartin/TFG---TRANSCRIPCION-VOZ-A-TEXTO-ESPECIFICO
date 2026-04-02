package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/*
Clase que gestiona el texto en burbujas
 */
class TranscriptionAdapter(private val frases: List<String>) :
    RecyclerView.Adapter<TranscriptionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFrase: TextView = view.findViewById(R.id.tvFrase)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transcripcion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvFrase.text = frases[position]
        holder.tvFrase.contentDescription = frases[position]
    }

    override fun getItemCount() = frases.size
}