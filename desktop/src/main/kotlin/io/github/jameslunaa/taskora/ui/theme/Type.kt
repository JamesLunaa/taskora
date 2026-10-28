package io.github.jameslunaa.taskora.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.archivo
import org.jetbrains.compose.resources.Font

/** Archivo (variable: wght 100-900, wdth 62-125), bundled in composeResources/font. */
@Composable
private fun archivoFamily(): FontFamily {
    @Composable
    fun archivo(weight: Int) = Font(
        Res.font.archivo,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )
    return FontFamily(archivo(400), archivo(500), archivo(600), archivo(700), archivo(800))
}

@Composable
fun taskoraTypography(): Typography {
    val family = archivoFamily()
    return remember(family) {
        fun TextStyle.archivo() = copy(fontFamily = family)
        Typography().run {
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
                // 17sp body text suits a phone; desktop is read closer, so it stays at 16sp.
                bodyLarge = bodyLarge.archivo().copy(fontSize = 16.sp, lineHeight = 22.sp),
                bodyMedium = bodyMedium.archivo(),
                bodySmall = bodySmall.archivo(),
                labelLarge = labelLarge.archivo(),
                labelMedium = labelMedium.archivo(),
                labelSmall = labelSmall.archivo(),
            )
        }
    }
}
