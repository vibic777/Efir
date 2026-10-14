package ru.efir.core.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Кодек протокола: преобразование [Envelope] ↔ JSON-строка.
 *
 * Использует полиморфную сериализацию sealed-иерархии [Message]
 * с дискриминатором `type`.
 */
object ProtocolCodec {

    /** Настроенный экземпляр Json. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        classDiscriminator = "type"
    }

    /** Сериализует конверт в строку. */
    fun encode(envelope: Envelope): String = json.encodeToString(envelope)

    /** Десериализует строку в конверт. */
    fun decode(text: String): Envelope = json.decodeFromString(text)
}

/** Ошибка несовместимости версий протокола. */
class ProtocolVersionMismatchException(
    val remoteVersion: Int,
    val localVersion: Int = PROTOCOL_VERSION,
) : Exception("Несовместимая версия протокола: удалённая=$remoteVersion, локальная=$localVersion")
