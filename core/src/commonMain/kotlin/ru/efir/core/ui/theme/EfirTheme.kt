package ru.efir.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** Как выбирается светлая или тёмная тема. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Шрифт приложения — Roboto (SIL OFL 1.1, `docs/licenses/Roboto-OFL.txt`) с
 * настоящими начертаниями regular / medium / bold: системный шрифт многих
 * телефонов не даёт приложениям жирного. Файлы лежат в ресурсах платформы.
 */
expect fun appFontFamily(): FontFamily

// Зелёная тема Notio: те же основной цвет и контейнеры, светлые и тёмные.
private val LightPrimary = Color(0xFF1B7F5C)
private val LightContainer = Color(0xFFB8F0D6)
private val LightOnContainer = Color(0xFF00201A)
private val DarkPrimary = Color(0xFF7FD8B5)
private val DarkContainer = Color(0xFF005139)
private val DarkOnPrimary = Color(0xFF101010)
private val DarkOnContainer = Color(0xFFF4F4F4)

/** Цвет «всё хорошо» (подключено, выдано) — читается на обеих темах. */
val ColorScheme.positive: Color get() = primary

fun efirColorScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = DarkPrimary, onPrimary = DarkOnPrimary,
        primaryContainer = DarkContainer, onPrimaryContainer = DarkOnContainer,
        secondary = DarkPrimary, onSecondary = DarkOnPrimary,
        secondaryContainer = DarkContainer, onSecondaryContainer = DarkOnContainer,
    )
} else {
    lightColorScheme(
        primary = LightPrimary, onPrimary = Color.White,
        primaryContainer = LightContainer, onPrimaryContainer = LightOnContainer,
        secondary = LightPrimary, onSecondary = Color.White,
        secondaryContainer = LightContainer, onSecondaryContainer = LightOnContainer,
    )
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private fun typography(family: FontFamily): Typography = Typography().let { t ->
    fun TextStyle.inAppFont() = copy(fontFamily = family)
    Typography(
        displayLarge = t.displayLarge.inAppFont(),
        displayMedium = t.displayMedium.inAppFont(),
        displaySmall = t.displaySmall.inAppFont(),
        headlineLarge = t.headlineLarge.inAppFont(),
        headlineMedium = t.headlineMedium.inAppFont(),
        headlineSmall = t.headlineSmall.inAppFont(),
        titleLarge = t.titleLarge.inAppFont(),
        titleMedium = t.titleMedium.inAppFont(),
        titleSmall = t.titleSmall.inAppFont(),
        bodyLarge = t.bodyLarge.inAppFont(),
        bodyMedium = t.bodyMedium.inAppFont(),
        bodySmall = t.bodySmall.inAppFont(),
        labelLarge = t.labelLarge.inAppFont(),
        labelMedium = t.labelMedium.inAppFont(),
        labelSmall = t.labelSmall.inAppFont(),
    )
}

private val AppTypography: Typography by lazy { typography(appFontFamily()) }

/** Тема приложения: цвета Notio, Roboto, светлая или тёмная по [mode]. */
@Composable
fun EfirTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = efirColorScheme(mode.isDark()),
        typography = AppTypography,
        content = content,
    )
}
