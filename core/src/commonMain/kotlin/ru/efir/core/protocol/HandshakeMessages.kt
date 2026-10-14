package ru.efir.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Первое сообщение после установки TLS-соединения.
 * Инициатор (обычно Windows-клиент) сообщает о себе и способе доступа.
 *
 * @param device информация об инициаторе.
 * @param sessionToken токен активной сессии, если клиент уже сопряжён и переподключается.
 * @param pairToken одноразовый токен сопряжения, если клиент подключается впервые.
 */
@Serializable
@SerialName("hello")
data class Hello(
    val device: DeviceInfo,
    val sessionToken: String? = null,
    val pairToken: String? = null,
    /** Подтверждение знания токена доверия (`AuthProof`); сам токен по сети не передаётся. */
    val tokenProof: String? = null,
) : RpcRequest

/**
 * Ответ на [Hello].
 *
 * @param accepted принято ли соединение.
 * @param sessionToken выданный токен сессии для последующих переподключений.
 */
@Serializable
@SerialName("helloAck")
data class HelloAck(
    val device: DeviceInfo,
    val accepted: Boolean,
    val sessionToken: String? = null,
    val reason: String? = null,
    /** Встречное подтверждение: принимающая сторона тоже знает токен доверия. */
    val hostProof: String? = null,
) : RpcResponse

/**
 * Запрос сопряжения по одноразовому токену (из QR) с подтверждением ключом доступа.
 *
 * @param pairingToken токен, закодированный в QR-коде.
 * @param accessKey ключ доступа из 12 символов `0-9A-Z`, отображаемый на принимающей
 *   стороне группами по 4 через дефис. Принимается в любом регистре и с любыми
 *   разделителями (сравнение нормализуется).
 */
@Serializable
@SerialName("pairRequest")
data class PairRequest(
    val pairingToken: String,
    val accessKey: String,
    val device: DeviceInfo,
    /** Подтверждение знания ключа доступа (`AuthProof`); сам ключ по сети не передаётся, [accessKey] пуст. */
    val keyProof: String? = null,
) : RpcRequest

/** Ответ на [PairRequest]. */
@Serializable
@SerialName("pairResponse")
data class PairResponse(
    val accepted: Boolean,
    val sessionToken: String? = null,
    val reason: String? = null,
    /** Встречное подтверждение: принимающая сторона действительно знает ключ доступа. */
    val hostProof: String? = null,
) : RpcResponse
