package com.example.myapplication

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Abrimos la memoria del móvil
        val prefs = getSharedPreferences("ConfigAccesibilidad", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // --- LÓGICA DE TAMAÑO DE LETRA ---

        // Al pulsar la 'A' pequeña
        findViewById<TextView>(R.id.previewLetraNormal).setOnClickListener {
            editor.putFloat("tamano_letra", 20f).apply()
            Toast.makeText(this, "Tamaño Normal seleccionado", Toast.LENGTH_SHORT).show()
        }
        // Al pulsar la 'A' mediana
        findViewById<TextView>(R.id.previewLetraGrande).setOnClickListener {
            editor.putFloat("tamano_letra", 30f).apply()
            Toast.makeText(this, "Tamaño Grande seleccionado", Toast.LENGTH_SHORT).show()
        }
        // Al pulsar la 'A' gigante
        findViewById<TextView>(R.id.previewLetraGigante).setOnClickListener {
            editor.putFloat("tamano_letra", 40f).apply()
            Toast.makeText(this, "Tamaño Súper Grande seleccionado", Toast.LENGTH_SHORT).show()
        }

        // --- LÓGICA DE CONTRASTE Y FONDO ---
        // Ahora guardamos el color de fondo y el color de texto por separado

        // Opción 1: Fondo Negro, Letra Blanca
        findViewById<TextView>(R.id.previewFondoNegroLetraBlanca).setOnClickListener {
            editor.putInt("color_fondo", Color.BLACK).apply()
            editor.putInt("color_texto", Color.WHITE).apply()
            Toast.makeText(this, "Contraste Negro/Blanco seleccionado", Toast.LENGTH_SHORT).show()
        }

        // Opción 2: Fondo Blanco, Letra Negra
        findViewById<TextView>(R.id.previewFondoBlancoLetraNegra).setOnClickListener {
            editor.putInt("color_fondo", Color.WHITE).apply()
            editor.putInt("color_texto", Color.BLACK).apply()
            Toast.makeText(this, "Contraste Blanco/Negro seleccionado", Toast.LENGTH_SHORT).show()
        }

        // Opción 3: Fondo Negro, Letra Amarilla
        findViewById<TextView>(R.id.previewFondoNegroLetraAmarilla).setOnClickListener {
            editor.putInt("color_fondo", Color.BLACK).apply()
            editor.putInt("color_texto", Color.YELLOW).apply()
            Toast.makeText(this, "Contraste Negro/Amarillo seleccionado", Toast.LENGTH_SHORT).show()
        }

        // Botón Volver
        findViewById<Button>(R.id.btnVolver).setOnClickListener {
            finish()
        }
    }
}