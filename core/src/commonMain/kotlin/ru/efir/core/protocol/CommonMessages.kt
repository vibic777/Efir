package ru.efir.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Успешное подтверждение запроса (ответ без данных). */
@Serializable
@SerialName("ack")
data class Ack(
    val ok: Boolean = true,
    val detail: String? = null,
) : RpcResponse

/** Ошибка обработки запроса. */
@Serializable
@SerialName("error")
data class ErrorMessage(
    val code: String,
    val message: String,
) : RpcResponse

/** Проверка доступности соединения (heartbeat). */
@Serializable
@SerialName("ping")
data class Ping(val nonce: Long) : Event

/** Ответ на [Ping]. */
@Serializable
@SerialName("pong")
data class Pong(val nonce: Long) : Event

/** Уведомление об изменении информации об устройстве. */
@Serializable
@SerialName("deviceInfoChanged")
data class DeviceInfoChanged(val device: DeviceInfo) : Event

/** Запрос списка поддерживаемых возможностей. */
@Serializable
@SerialName("requestCapabilities")
object RequestCapabilities : RpcRequest

/** Ответ со списком возможностей. */
@Serializable
@SerialName("capabilitiesResult")
data class CapabilitiesResult(val capabilities: Set<Capability>) : RpcResponse
