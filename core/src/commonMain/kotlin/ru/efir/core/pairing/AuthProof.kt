package ru.efir.core.pairing

/** HMAC-SHA256 средствами платформы. */
expect fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

/**
 * Подтверждение «я знаю секрет» без передачи самого секрета: подпись (HMAC-SHA256)
 * отпечатка канала и роли, сделанная секретом как ключом. Секрет — ключ доступа
 * при сопряжении и токен доверия при следующих подключениях.
 *
 * Перехвативший подтверждение не узнаёт секрет и не может им воспользоваться в
 * другом соединении: отпечаток канала там другой. Подтверждают обе стороны —
 * телефон убеждается в компьютере, а компьютер в телефоне.
 */
object AuthProof {
    /** Роль подтверждающего: открывший соединение или принявший его. */
    const val CLIENT = "client"
    const val HOST = "host"

    fun of(secret: String, channelBinding: ByteArray?, role: String): String {
        val data = (channelBinding ?: ByteArray(0)) + role.encodeToByteArray()
        return hmacSha256(secret.encodeToByteArray(), data).joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    /** Сравнение за одинаковое время: по скорости ответа подпись не подобрать. */
    fun matches(proof: String?, secret: String, channelBinding: ByteArray?, role: String): Boolean {
        if (proof == null) return false
        val expected = of(secret, channelBinding, role)
        if (proof.length != expected.length) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (proof[i].code xor expected[i].code)
        return diff == 0
    }
}
