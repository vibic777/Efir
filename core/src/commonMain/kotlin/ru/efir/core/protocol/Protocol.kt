package ru.efir.core.protocol

import kotlinx.serialization.Serializable

/**
 * Версия протокола Efir.
 *
 * Увеличивается при несовместимых изменениях формата сообщений.
 * Стороны обмениваются версией при handshake и отказываются работать
 * при несовпадении мажорной версии.
 */
const val PROTOCOL_VERSION: Int = 1

/**
 * Логические каналы мультиплексирования поверх одного соединения.
 *
 * CONTROL — WebSocket RPC (запросы/ответы/события).
 * SCREEN  — бинарный поток кадров экрана.
 * CAMERA  — бинарный поток камеры.
 * AUDIO   — бинарный поток звука/микрофона.
 * FILES   — чанкованные передачи файлов.
 */
@Serializable
enum class ProtocolChannel {
    CONTROL,
    SCREEN,
    CAMERA,
    AUDIO,
    FILES,
}

/** Тип устройства-участника. */
@Serializable
enum class DeviceType {
    ANDROID,
    WINDOWS,
    UNKNOWN,
}

/**
 * Возможности (capabilities), которыми обмениваются устройства при handshake.
 * Определяют, какие функции доступны в данной паре.
 */
@Serializable
enum class Capability {
    SCREEN_CAPTURE,
    INPUT_INJECTION,
    FILE_MANAGER,
    FILE_SYNC,
    CLIPBOARD,
    NOTIFICATIONS,
    CAMERA,
    AUDIO,
    SMS,
    CALLS,
}

/**
 * Информация об устройстве, передаваемая при handshake и обнаружении.
 *
 * @param deviceId стабильный идентификатор устройства (генерируется при первом запуске).
 * @param name отображаемое имя (например, «Pixel 7»).
 * @param type тип платформы.
 * @param protocolVersion поддерживаемая версия протокола.
 * @param capabilities набор поддерживаемых возможностей.
 */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val type: DeviceType,
    val protocolVersion: Int = PROTOCOL_VERSION,
    val capabilities: Set<Capability> = emptySet(),
)

// ---------------------------------------------------------------------------
// Базовые маркеры сообщений.
//
// Все сообщения — элементы sealed-иерархии [Message], что позволяет
// kotlinx.serialization выполнять полиморфную (де)сериализацию по дискриминатору.
// ---------------------------------------------------------------------------

/** Базовый тип любого сообщения, передаваемого в [Envelope]. */
@Serializable
sealed interface Message

/** Запрос, на который ожидается ответ (RPC). */
@Serializable
sealed interface RpcRequest : Message

/** Ответ на ранее отправленный [RpcRequest] (связывается через Envelope.replyTo). */
@Serializable
sealed interface RpcResponse : Message

/** Событие «выстрелил и забыл», ответ не требуется. */
@Serializable
sealed interface Event : Message
