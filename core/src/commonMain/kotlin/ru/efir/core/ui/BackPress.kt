package ru.efir.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Как платформа перехватывает системную кнопку «Назад». Задаёт приложение телефона
 * (`BackHandler` из androidx.activity); на компьютере такой кнопки нет.
 */
val LocalBackPress = staticCompositionLocalOf<@Composable (enabled: Boolean, onBack: () -> Unit) -> Unit> { { _, _ -> } }

/**
 * Системная кнопка «Назад» телефона: пока [enabled], она вызывает [onBack] вместо
 * выхода из приложения (со страницы настроек — в меню, из раздела — к устройствам).
 */
@Composable
fun BackPress(enabled: Boolean = true, onBack: () -> Unit) = LocalBackPress.current(enabled, onBack)