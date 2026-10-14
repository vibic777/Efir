package ru.efir.core.pairing

/** Криптостойкая случайность, реализуемая платформенно. */
expect object SecureRandomProvider {
    /** Возвращает [size] случайных байтов. */
    fun bytes(size: Int): ByteArray

    /** Возвращает случайное неотрицательное число из [0, bound). */
    fun nextInt(bound: Int): Int
}

/** Утилиты генерации токенов и ключей доступа на основе платформенной случайности. */
object SecureTokens {

    /**
     * Алфавит ключа доступа: цифры и заглавные латинские буквы (36 символов, ~5.17 бит
     * на символ). Исключены неоднозначные при чтении глазом символы не убираем —
     * ключ всё равно набирается вручную с экрана, а регистронезависимость ввода
     * снимает путаницу `O`/`0`, `I`/`1`.
     */
    const val ACCESS_KEY_ALPHABET: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /** Длина ключа доступа в символах (12 символов ≈ 62 бита энтропии). */
    const val ACCESS_KEY_LENGTH: Int = 12

    /** Сколько символов в одной группе при отображении ключа. */
    const val ACCESS_KEY_GROUP_SIZE: Int = 4

    /** Токен заданной длины в hex-представлении. */
    fun token(byteLength: Int = 16): String =
        SecureRandomProvider.bytes(byteLength).joinToString("") { byte ->
            val v = byte.toInt() and 0xFF
            val hex = v.toString(16)
            if (hex.length == 1) "0$hex" else hex
        }

    /**
     * Ключ доступа длиной [length] символов из [ACCESS_KEY_ALPHABET] (верхний регистр).
     * Используется для подтверждения сопряжения вместо числового PIN.
     */
    fun accessKey(length: Int = ACCESS_KEY_LENGTH): String = buildString(length) {
        repeat(length) { append(ACCESS_KEY_ALPHABET[SecureRandomProvider.nextInt(ACCESS_KEY_ALPHABET.length)]) }
    }

    /** Разбивает ключ на группы по (4) символа через дефис: `A1B2-C3D4-E5F6`. */
    fun formatAccessKey(key: String): String {
        val raw = normalizeAccessKey(key)
        if (raw.isEmpty()) return ""
        return raw.chunked(ACCESS_KEY_GROUP_SIZE).joinToString("-")
    }

    /**
     * Приводит введённый пользователем ключ к каноническому виду: верхний регистр,
     * только символы алфавита, без дефисов и пробелов. Неверные символы отбрасываются,
     * поэтому `a1b2 c3d4-e5f6!` и `A1B2C3D4E5F6` дают один и тот же результат.
     */
    fun normalizeAccessKey(key: String): String = buildString(key.length) {
        for (ch in key) {
            val upper = ch.uppercaseChar()
            if (upper in ACCESS_KEY_ALPHABET) append(upper)
        }
    }

    /** Корректен ли ключ: ровно [ACCESS_KEY_LENGTH] символов алфавита. */
    fun isAccessKeyValid(key: String): Boolean = normalizeAccessKey(key).length == ACCESS_KEY_LENGTH
}
