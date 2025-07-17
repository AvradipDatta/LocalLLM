package com.google.ai.edge.gallery.utils

import androidx.compose.ui.graphics.Color

object RandomColorPicker {
    private val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Magenta, Color.Cyan)

    fun getRandomColor(): Color {
        return colors.random()
    }
}
