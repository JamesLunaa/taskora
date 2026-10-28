package io.github.jameslunaa.taskora.ui.lists

import androidx.compose.ui.graphics.toArgb
import io.github.jameslunaa.taskora.ui.theme.CategoryPalette

/** ARGB colors offered for lists (the dark variants; see `listColor` for display). */
val ListColors: List<Int> = CategoryPalette.map { it.first.toArgb() }
