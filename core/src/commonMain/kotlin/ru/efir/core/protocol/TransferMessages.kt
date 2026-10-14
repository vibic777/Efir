package ru.efir.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Направление передачи относительно инициатора. */
@Serializable
enum class TransferKind {
    /** Инициатор отправляет данные (например, ПК → телефон). */
    UPLOAD,
    /** Инициатор запрашивает данные (например, телефон → ПК). */
    DOWNLOAD,
}

/**
 * Начало передачи файла.
 *
 * @param transferId идентификатор передачи.
 * @param path целевой/исходный путь на удалённой стороне.
 * @param size полный размер файла в байтах.
 * @param sha256 ожидаемая контрольная сумма (если известна).
 * @param overwrite разрешено ли перезаписывать существующий файл.
 */
@Serializable
@SerialName("transferInit")
data class TransferInit(
    val transferId: String,
    val path: String,
    val size: Long,
    val sha256: String? = null,
    val kind: TransferKind,
    val overwrite: Boolean = false,
    /** Предлагаемое инициатором смещение докачки (для DOWNLOAD). */
    val resumeFrom: Long = 0,
    /** Время изменения исходного файла инициатора (epoch millis) для проверки изменений. */
    val sourceModifiedAt: Long = 0,
) : RpcRequest

/**
 * Приём передачи.
 *
 * @param resumeFrom смещение, с которого следует продолжить передачу (докачка).
 * @param chunkSize рекомендуемый размер чанка для бинарного канала.
 * @param totalSize полный размер источника (заполняется для [TransferKind.DOWNLOAD]).
 */
@Serializable
@SerialName("transferAccepted")
data class TransferAccepted(
    val transferId: String,
    val resumeFrom: Long = 0,
    val chunkSize: Int = 256 * 1024,
    val totalSize: Long = 0,
) : RpcResponse

/**
 * Отказ в приёме передачи (нет места, запрещён путь, конфликт без overwrite).
 *
 * @param reason человекочитаемая причина.
 */
@Serializable
@SerialName("transferRejected")
data class TransferRejected(
    val transferId: String,
    val reason: String,
) : RpcResponse

/**
 * Подтверждение приёма чанка (для управления потоком).
 *
 * @param seq порядковый номер чанка.
 * @param received суммарно принято байт.
 */
@Serializable
@SerialName("transferChunkAck")
data class TransferChunkAck(
    val transferId: String,
    val seq: Int,
    val received: Long,
) : Event

/** Завершение передачи данных. */
@Serializable
@SerialName("transferComplete")
data class TransferComplete(
    val transferId: String,
    val sha256: String? = null,
) : RpcRequest

/** Результат проверки целостности. */
@Serializable
@SerialName("transferVerified")
data class TransferVerified(
    val transferId: String,
    val ok: Boolean,
    val sha256: String? = null,
    val detail: String? = null,
) : RpcResponse

/** Прогресс передачи. */
@Serializable
@SerialName("transferProgress")
data class TransferProgress(
    val transferId: String,
    val bytes: Long,
    val size: Long,
) : Event

/** Передача данных завершена отправителем (для DOWNLOAD). */
@Serializable
@SerialName("transferFinished")
data class TransferFinished(
    val transferId: String,
    val sha256: String? = null,
) : Event

/** Прерывание передачи. */
@Serializable
@SerialName("transferAbort")
data class TransferAbort(
    val transferId: String,
    val reason: String? = null,
) : Event
