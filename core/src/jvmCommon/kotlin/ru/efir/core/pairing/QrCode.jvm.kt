package ru.efir.core.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * JVM-реализация генерации QR-кода на базе ZXing.
 *
 * Кодирует [text] в QR-матрицу без масштабирования (1 модуль = 1 пиксель),
 * чтобы отрисовка могла сама выбрать размер ячейки.
 */
actual fun generateQrMatrix(
    text: String,
    errorCorrection: QrErrorCorrection,
): QrMatrix? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to errorCorrection.toZxing(),
        // Стандартная «тихая зона» QR — 4 модуля; меньшее значение ломает декодирование.
        EncodeHintType.MARGIN to 4,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val bitMatrix: BitMatrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        hints,
    )
    val size = bitMatrix.width
    val modules = BooleanArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            modules[y * size + x] = bitMatrix.get(x, y)
        }
    }
    QrMatrix.of(size, modules)
}.getOrNull()

private fun QrErrorCorrection.toZxing(): ErrorCorrectionLevel = when (this) {
    QrErrorCorrection.LOW -> ErrorCorrectionLevel.L
    QrErrorCorrection.MEDIUM -> ErrorCorrectionLevel.M
    QrErrorCorrection.QUARTILE -> ErrorCorrectionLevel.Q
    QrErrorCorrection.HIGH -> ErrorCorrectionLevel.H
}
