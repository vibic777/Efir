package ru.efir.core.pairing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Отрисовывает QR-код для строки [text] на Compose Canvas.
 *
 * Матрица вычисляется один раз на каждый [text] (кэшируется через [remember]).
 * Если кодирование невозможно — показывает текст-заглушку с [fallbackText].
 *
 * @param size сторона квадрата QR-кода.
 * @param paddingDp внутренний отступ (тихая зона) вокруг модулей.
 */
@Composable
fun QrCodeView(
    text: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 240.dp,
    paddingDp: androidx.compose.ui.unit.Dp = 8.dp,
    fallbackText: String = "Не удалось построить QR-код",
) {
    val matrix = remember(text) { generateQrMatrix(text) }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix == null) {
            Text(
                text = fallbackText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            return@Box
        }

        val dark = Color.Black
        val light = Color.White

        Canvas(modifier = Modifier.fillMaxWidth().size(size)) {
            val padding = paddingDp.toPx()
            val side = minOf(this.size.width, this.size.height)
            val cell = (side - padding * 2).coerceAtLeast(1f) / matrix.size

            // Фон (тихая зона + светлые модули).
            drawRect(
                color = light,
                topLeft = Offset.Zero,
                size = Size(this.size.width, this.size.height),
            )

            val originX = (this.size.width - cell * matrix.size) / 2f
            val originY = (this.size.height - cell * matrix.size) / 2f

            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (matrix[x, y]) {
                        drawRect(
                            color = dark,
                            topLeft = Offset(originX + x * cell, originY + y * cell),
                            size = Size(cell, cell),
                        )
                    }
                }
            }
        }
    }
}
