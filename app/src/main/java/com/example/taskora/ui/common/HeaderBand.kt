package com.example.taskora.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.example.taskora.ui.theme.TaskoraTheme

/** Top app bars sit on the header band (ube-field), with lilac icons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun headerBandTopAppBarColors(): TopAppBarColors {
    val colors = TaskoraTheme.colors
    return TopAppBarDefaults.topAppBarColors(
        containerColor = colors.headerBand,
        scrolledContainerColor = colors.headerBand,
        titleContentColor = colors.onHeaderBand,
        navigationIconContentColor = colors.headerBandAccent,
        actionIconContentColor = colors.headerBandAccent,
    )
}
