package com.example.taskora.ui.lists

import androidx.compose.ui.graphics.toArgb
import com.example.taskora.ui.theme.CategoryPalette

/** ARGB colors offered for lists (the dark variants; see `listColor` for display). */
val ListColors: List<Int> = CategoryPalette.map { it.first.toArgb() }
