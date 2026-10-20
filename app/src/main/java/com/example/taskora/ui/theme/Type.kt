package com.example.taskora.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.taskora.R

// Variable-font settings are still experimental in Compose.
@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int) = Font(
    R.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Archivo (variable: wght 100-900, wdth 62-125), bundled in res/font. */
val Archivo = FontFamily(archivo(400), archivo(500), archivo(600), archivo(700), archivo(800))

private fun TextStyle.archivo() = copy(fontFamily = Archivo)

val Typography = Typography().run {
    copy(
        displayLarge = displayLarge.archivo(),
        displayMedium = displayMedium.archivo(),
        displaySmall = displaySmall.archivo(),
        headlineLarge = headlineLarge.archivo(),
        headlineMedium = headlineMedium.archivo(),
        headlineSmall = headlineSmall.archivo(),
        titleLarge = titleLarge.archivo().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.archivo(),
        titleSmall = titleSmall.archivo(),
        // Body text is 17sp in the design.
        bodyLarge = bodyLarge.archivo().copy(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.archivo(),
        bodySmall = bodySmall.archivo(),
        labelLarge = labelLarge.archivo(),
        labelMedium = labelMedium.archivo(),
        labelSmall = labelSmall.archivo(),
    )
}
