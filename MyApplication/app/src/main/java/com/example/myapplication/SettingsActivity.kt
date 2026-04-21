package com.example.myapplication

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        btnBack = findViewById(R.id.btnBack)

        // Aplicar estilo accesible al botón de volver
        aplicarEstiloImageButton(btnBack)

        btnBack.setOnClickListener {
            finish()
        }

        // Referencias a las tarjetas de opciones
        val cardLetraNormal = findViewById<MaterialCardView>(R.id.cardLetraNormal)
        val cardLetraGrande = findViewById<MaterialCardView>(R.id.cardLetraGrande)
        val cardLetraGigante = findViewById<MaterialCardView>(R.id.cardLetraGigante)

        val cardNegroBlanco = findViewById<MaterialCardView>(R.id.cardNegroBlanco)
        val cardBlancoNegro = findViewById<MaterialCardView>(R.id.cardBlancoNegro)
        val cardNegroAmarillo = findViewById<MaterialCardView>(R.id.cardNegroAmarillo)

        val opcionesLetra = listOf(cardLetraNormal, cardLetraGrande, cardLetraGigante)
        val opcionesContraste = listOf(cardNegroBlanco, cardBlancoNegro, cardNegroAmarillo)

        // --- TAMAÑO DE LETRA ---

        cardLetraNormal.setOnClickListener {

            seleccionarCard(opcionesLetra, cardLetraNormal)

            AccessibilityManager.saveTextSize(this, "normal")
            aplicarAccesibilidadUI()
        }

        cardLetraGrande.setOnClickListener {

            seleccionarCard(opcionesLetra, cardLetraGrande)

            AccessibilityManager.saveTextSize(this, "grande")
            aplicarAccesibilidadUI()
        }

        cardLetraGigante.setOnClickListener {

            seleccionarCard(opcionesLetra, cardLetraGigante)

            AccessibilityManager.saveTextSize(this, "extra")
            aplicarAccesibilidadUI()
        }

        // --- CONTRASTE ---

        cardNegroBlanco.setOnClickListener {

            seleccionarCard(opcionesContraste, cardNegroBlanco)

            AccessibilityManager.saveContrast(this, "dark")
            aplicarAccesibilidadUI()
        }

        cardBlancoNegro.setOnClickListener {

            seleccionarCard(opcionesContraste, cardBlancoNegro)

            AccessibilityManager.saveContrast(this, "light")
            aplicarAccesibilidadUI()
        }

        cardNegroAmarillo.setOnClickListener {

            seleccionarCard(opcionesContraste, cardNegroAmarillo)

            AccessibilityManager.saveContrast(this, "yellow")
            aplicarAccesibilidadUI()
        }

        // Restaurar la configuración guardada previamente

        when (AccessibilityManager.getTextSize(this)) {
            "normal" -> seleccionarCard(opcionesLetra, cardLetraNormal)
            "grande" -> seleccionarCard(opcionesLetra, cardLetraGrande)
            "extra" -> seleccionarCard(opcionesLetra, cardLetraGigante)
        }

        when (AccessibilityManager.getContrast(this)) {
            "dark" -> seleccionarCard(opcionesContraste, cardNegroBlanco)
            "light" -> seleccionarCard(opcionesContraste, cardBlancoNegro)
            "yellow" -> seleccionarCard(opcionesContraste, cardNegroAmarillo)
        }

        aplicarAccesibilidadUI()
    }

    // Ajusta color y escala del icono según la configuración de accesibilidad
    private fun aplicarEstiloImageButton(boton: ImageButton) {

        val colorIcono = AccessibilityManager.getTextColor(this)

        boton.setColorFilter(colorIcono)

        val escala = when (AccessibilityManager.getTextSize(this)) {
            "normal" -> 1.0f
            "grande" -> 1.2f
            "extra" -> 1.4f
            else -> 1.0f
        }

        boton.scaleX = escala
        boton.scaleY = escala
    }

    // Marca visualmente qué tarjeta está seleccionada
    private fun seleccionarCard(lista: List<MaterialCardView>, seleccionada: MaterialCardView) {

        lista.forEach {
            it.strokeWidth = 0
        }

        seleccionada.strokeWidth = 6
    }

    // Aplica colores y tamaño de texto según la configuración de accesibilidad
    private fun aplicarAccesibilidadUI() {

        val root = findViewById<android.view.View>(android.R.id.content)

        val tvTituloLetra = findViewById<android.widget.TextView>(R.id.tvTituloLetra)
        val tvTituloContraste = findViewById<android.widget.TextView>(R.id.tvTituloContraste)

        val colorTexto = AccessibilityManager.getTextColor(this)
        val colorFondo = AccessibilityManager.getBackgroundColor(this)
        val tamanoTexto = AccessibilityManager.getTextSizePx(this) * 0.8f

        root.setBackgroundColor(colorFondo)

        tvTituloLetra.setTextColor(colorTexto)
        tvTituloContraste.setTextColor(colorTexto)

        tvTituloLetra.textSize = tamanoTexto
        tvTituloContraste.textSize = tamanoTexto

        aplicarEstiloImageButton(btnBack)
    }
}