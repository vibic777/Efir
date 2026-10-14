package ru.efir.core.media

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Декодирует сжатое изображение (JPEG/PNG) в [ImageBitmap] для отображения.
 *
 * Реализация платформенная:
 *  - Desktop — `javax.imageio` + Skia-конвертация;
 *  - Android — `BitmapFactory` + Compose-конвертация.
 *
 * Возвращает `null`, если данные не удалось декодировать.
 */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?
