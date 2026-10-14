package ru.efir.core.pairing

/**
 * Матрица QR-кода: [size] × [size] булевых модулей (true — тёмный модуль).
 *
 * Простой платформо-независимый формат, пригодный для отрисовки на Compose Canvas.
 */
data class QrMatrix(
    val size: Int,
    private val modules: BooleanArray,
) {
    /** Возвращает значение модуля по координатам (x — столбец, y — строка). */
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    companion object {
        /** Создаёт матрицу из низкоуровневого массива модулей (row-major). */
        fun of(size: Int, modules: BooleanArray): QrMatrix {
            require(size > 0) { "Размер матрицы должен быть положительным" }
            require(modules.size == size * size) {
                "Ожидалось ${size * size} модулей, получено ${modules.size}"
            }
            return QrMatrix(size, modules)
        }
    }
}

/**
 * Генерирует [QrMatrix] для произвольной строки [text].
 *
 * Реализуется платформенно (ZXing на JVM). Возвращает `null`, если строку
 * невозможно закодировать (слишком длинная, неподдерживаемые символы и т.п.).
 *
 * @param errorCorrection уровень коррекции ошибок (по умолчанию средний).
 */
expect fun generateQrMatrix(
    text: String,
    errorCorrection: QrErrorCorrection = QrErrorCorrection.MEDIUM,
): QrMatrix?

/** Уровни коррекции ошибок QR (совместимы с ZXing). */
enum class QrErrorCorrection { LOW, MEDIUM, QUARTILE, HIGH }
