package com.example.myapplication

import android.content.Context
import android.graphics.Color

object AccessibilityManager {

    // Nombre del archivo de preferencias donde guardamos la configuración
    private const val PREFS_NAME = "accessibility_prefs"

    // Claves usadas para guardar tamaño de texto y contraste
    private const val KEY_TEXT_SIZE = "text_size"
    private const val KEY_CONTRAST = "contrast"


    // Guardado y lectura de preferencias

    fun saveTextSize(context: Context, size: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TEXT_SIZE, size).apply()
    }

    fun getTextSize(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TEXT_SIZE, "normal") ?: "normal"
    }

    fun saveContrast(context: Context, contrast: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONTRAST, contrast).apply()
    }

    fun getContrast(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CONTRAST, "light") ?: "light"
    }

    // Métodos auxiliares para obtener colores

    fun getTextColor(context: Context): Int {
        return when (getContrast(context)) {
            "dark" -> Color.WHITE
            "light" -> Color.BLACK
            "yellow" -> Color.YELLOW
            else -> Color.WHITE
        }
    }

    fun getBackgroundColor(context: Context): Int {
        return when (getContrast(context)) {
            "dark" -> Color.BLACK
            "light" -> Color.WHITE
            "yellow" -> Color.BLACK
            else -> Color.BLACK
        }
    }

    fun getTextSizePx(context: Context): Float {
        return when (getTextSize(context)) {
            "normal" -> 20f
            "grande" -> 30f
            "extra" -> 40f
            else -> 20f
        }
    }

    fun getButtonBackgroundColor(context: Context): Int {
        return when (getContrast(context)) {
            "dark" -> Color.parseColor("#4CAF50")
            "light" -> Color.BLACK
            "yellow" -> Color.parseColor("#333333")
            else -> Color.BLACK
        }
    }

    fun getButtonTextColor(context: Context): Int {
        return when (getContrast(context)) {
            "dark", "light", "yellow" -> Color.WHITE
            else -> Color.WHITE
        }
    }
}
