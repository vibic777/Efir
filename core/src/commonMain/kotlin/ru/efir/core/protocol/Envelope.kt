package ru.efir.core.protocol

import kotlinx.serialization.Serializable

/**
 * Универсальная обёртка сообщения, передаваемого по control-каналу.
 *
 * @param protocolVersion версия протокола на момент отправки.
 * @param id уникальный идентификатор сообщения.
 * @param replyTo для ответов — id исходного запроса; иначе null.
 * @param channel логический канал (обычно CONTROL).
 * @param timestamp время отправки (epoch millis).
 * @param body полезная нагрузка — конкретное сообщение из иерархии [Message].
 */
@Serializable
data class Envelope(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val id: String,
    val replyTo: String? = null,
    val channel: ProtocolChannel = ProtocolChannel.CONTROL,
    val timestamp: Long,
    val body: Message,
)
