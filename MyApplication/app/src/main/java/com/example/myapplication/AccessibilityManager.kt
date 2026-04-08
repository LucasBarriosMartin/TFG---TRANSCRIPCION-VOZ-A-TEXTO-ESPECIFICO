package com.example.myapplication

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.content.SharedPreferences

object AccessibilityManager {

    private const val PREFS_NAME = "accessibility_prefs"

    private const val KEY_TEXT_SIZE = "text_size"
    private const val KEY_CONTRAST = "contrast"

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

    fun applyAccessibility(context: Context, root: android.view.View) {

        val textSize = getTextSize(context)
        val contrast = getContrast(context)

        applyTextSize(root, textSize)
        applyContrast(root, contrast)
    }

    private fun applyTextSize(view: android.view.View, size: String) {

        if (view is android.widget.TextView) {

            when (size) {
                "normal" -> view.textSize = 16f
                "grande" -> view.textSize = 20f
                "extra" -> view.textSize = 24f
            }
        }

        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTextSize(view.getChildAt(i), size)
            }
        }
    }

    private fun applyContrast(view: View, contrast: String) {
        if (view is TextView) {
            when (contrast) {
                "light" -> {
                    view.setTextColor(Color.BLACK)
                    view.setBackgroundColor(Color.WHITE)
                }
                "dark" -> {
                    view.setTextColor(Color.WHITE)
                    view.setBackgroundColor(Color.BLACK)
                }
                "yellow" -> {
                    view.setTextColor(Color.YELLOW)
                    view.setBackgroundColor(Color.BLACK)
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyContrast(view.getChildAt(i), contrast)
            }
        }
    }

    fun getTextColor(context: Context): Int {
        return when(getContrast(context)) {
            "dark" -> Color.WHITE
            "light" -> Color.BLACK
            "yellow" -> Color.YELLOW
            else -> Color.WHITE
        }
    }

    fun getBackgroundColor(context: Context): Int {
        return when(getContrast(context)) {
            "dark" -> Color.BLACK
            "light" -> Color.WHITE
            "yellow" -> Color.BLACK
            else -> Color.BLACK
        }
    }

    fun getTextSizePx(context: Context): Float {
        return when(getTextSize(context)) {
            "normal" -> 20f
            "grande" -> 30f
            "extra" -> 40f
            else -> 20f
        }
    }

    fun getButtonBackgroundColor(context: Context): Int {
        return when(getContrast(context)) {
            "dark" -> Color.parseColor("#4CAF50") // verde sobre fondo negro
            "light" -> Color.BLACK                // negro sobre fondo blanco
            "yellow" -> Color.parseColor("#333333") // gris oscuro sobre fondo negro
            else -> Color.BLACK
        }
    }

    fun getButtonTextColor(context: Context): Int {
        return when(getContrast(context)) {
            "dark", "light", "yellow" -> Color.WHITE
            else -> Color.WHITE
        }
    }
}