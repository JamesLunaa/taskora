package com.example.taskora.ui.theme

import androidx.compose.ui.graphics.Color

// "Tiangge After Dark" palette (app-theme.md). Names follow the design tokens.

// Dark (default)
val NightGround = Color(0xFF0E0C13)
val UbeField = Color(0xFF2A1745)
val UbeLamp = Color(0xFFB794F6)
val OnLamp = Color(0xFF1A0F2B)
val LampContainer = Color(0xFF3D2466)
val OnLampContainer = Color(0xFFE9DDFF)
val OperatorPlum = Color(0xFF2F2640)
val OnOperator = Color(0xFFE6DCF7)
val PriceCardInk = Color(0xFFF2EDE4)
val DuskInk = Color(0xFFA59CB5)
val FieldLilac = Color(0xFFD9C8FF)
val SodiumAmber = Color(0xFFFFB84D)
val ExpenseCoral = Color(0xFFFF7A6B)
val StallShadow = Color(0xFF0A0910)
val StallLow = Color(0xFF13101A)
val Stall = Color(0xFF17131F)
val StallHigh = Color(0xFF1E1928)
val StallHighest = Color(0xFF262033)
val OutlineDusk = Color(0xFF6E6580)
val OutlineFaint = Color(0xFF2E2838)

// Light ("day")
val DayGround = Color(0xFFF7F4F0)
val DayField = Color(0xFF4A2A7A)
val DayOnField = Color(0xFFF7F2EA)
val DayFieldLilac = Color(0xFFDCCBFA)
val DayLamp = Color(0xFF6B3FB0)
val DayLampContainer = Color(0xFFE9DDFF)
val DayOperator = Color(0xFFE6DFF0)
val DayInk = Color(0xFF1B1622)
val DayMutedInk = Color(0xFF5B5268)
val DayAmber = Color(0xFF8A5100)
val DayExpense = Color(0xFFC2372B)
val DayStallLow = Color(0xFFF2EEEA)
val DayStallHigh = Color(0xFFE5DFDC)
val DayStallHighest = Color(0xFFDDD6D4)

/** Category palette as (dark, day) pairs. Lists store the dark ARGB value. */
val CategoryPalette: List<Pair<Color, Color>> = listOf(
    Color(0xFF5B8DEF) to Color(0xFF3563C9), // blue
    Color(0xFF4FB3F0) to Color(0xFF1C78B5), // sky
    Color(0xFF3FC1B0) to Color(0xFF11807A), // teal
    Color(0xFF4DB89A) to Color(0xFF1F8A73), // green
    Color(0xFFA6D25B) to Color(0xFF5A841D), // lime
    Color(0xFFE3B23C) to Color(0xFF9C730C), // mustard
    Color(0xFFF08A3E) to Color(0xFFC0601A), // orange
    Color(0xFFF4845F) to Color(0xFFB4532E), // coral
    Color(0xFFF2709A) to Color(0xFFBF3F69), // pink
    Color(0xFFE36FC7) to Color(0xFFAD3A8F), // orchid
    Color(0xFF9AB0C8) to Color(0xFF56708F), // steel
    Color(0xFFC9A27A) to Color(0xFF8A6843), // tan
)
