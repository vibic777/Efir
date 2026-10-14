package ru.efir.core.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.RGBLuminanceSource
import ru.efir.core.protocol.DeviceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QrCodeTest {

    @Test
    fun generatesSquareMatrixWithinSizeLimits() {
        val matrix = generateQrMatrix("hello-efir")
        assertNotNull(matrix, "QR-матрица должна быть построена")
        // 21 модуль (версия 1) + тихая зона по 4 модуля с каждой стороны.
        assertTrue(matrix.size >= 29, "Минимальный размер с quiet zone — 29, получен ${matrix.size}")
        assertEquals(1, matrix.size % 2, "Размер матрицы должен быть нечётным: ${matrix.size}")
    }

    @Test
    fun payloadRoundTripsThroughQr() {
        val payload = PairingPayload(
            deviceId = "android-1",
            deviceName = "Pixel",
            type = DeviceType.ANDROID,
            host = "192.168.0.10",
            port = 47812,
            fingerprint = "abcdef0123456789",
            pairingToken = "0123456789abcdef0123456789abcdef",
            expiresAt = 1_700_000_000_000L,
        )
        val encoded = PairingCodec.encode(payload)

        val matrix = generateQrMatrix(encoded)
        assertNotNull(matrix)

        val decoded = decode(matrix)
        assertEquals(encoded, decoded, "Данные сопряжения должны корректно декодироваться из QR")
    }

    @Test
    fun tooLongTextReturnsNull() {
        val huge = "x".repeat(10_000)
        assertTrue(generateQrMatrix(huge) == null, "Слишком длинная строка не должна кодироваться")
    }

    /** Декодирует нашу матрицу обратно в строку через ZXing (с масштабированием модулей). */
    private fun decode(matrix: QrMatrix, scale: Int = 4): String {
        val side = matrix.size * scale
        val pixels = IntArray(side * side) { i ->
            val x = (i % side) / scale
            val y = (i / side) / scale
            if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val source = RGBLuminanceSource(side, side, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        return MultiFormatReader().decode(bitmap, hints).text
    }
}
