package ru.efir.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

actual fun appFontFamily(): FontFamily = FontFamily(
    Font("font/roboto_regular.ttf", FontWeight.Normal),
    Font("font/roboto_medium.ttf", FontWeight.Medium),
    Font("font/roboto_bold.ttf", FontWeight.Bold),
)
