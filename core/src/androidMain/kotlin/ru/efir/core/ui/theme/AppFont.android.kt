package ru.efir.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ru.efir.core.R

actual fun appFontFamily(): FontFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold),
)
