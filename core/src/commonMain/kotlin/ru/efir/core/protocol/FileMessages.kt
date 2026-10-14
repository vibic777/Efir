package ru.efir.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Запись файловой системы (файл или каталог). */
@Serializable
data class RemoteEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val readable: Boolean = true,
    val writable: Boolean = false,
)

/** Список содержимого каталога. `path = null` — запрос доступных корней. */
@Serializable
@SerialName("fsList")
data class FsList(
    val path: String? = null,
    val offset: Int = 0,
    val limit: Int = 500,
) : RpcRequest

/** Результат [FsList]. */
@Serializable
@SerialName("fsListResult")
data class FsListResult(
    val path: String,
    val entries: List<RemoteEntry>,
    val hasMore: Boolean = false,
) : RpcResponse

/** Метаданные одного пути. */
@Serializable
@SerialName("fsStat")
data class FsStat(
    val path: String,
) : RpcRequest

/** Результат [FsStat]. */
@Serializable
@SerialName("fsStatResult")
data class FsStatResult(
    val entry: RemoteEntry,
) : RpcResponse

/** Удаление путей. */
@Serializable
@SerialName("fsDelete")
data class FsDelete(
    val paths: List<String>,
    val recursive: Boolean = false,
) : RpcRequest

/** Результат [FsDelete]. */
@Serializable
@SerialName("fsDeleteResult")
data class FsDeleteResult(
    val deleted: Int,
) : RpcResponse

/** Переименование/перемещение пути. */
@Serializable
@SerialName("fsRename")
data class FsRename(
    val path: String,
    val newName: String,
) : RpcRequest

/** Результат [FsRename]. */
@Serializable
@SerialName("fsRenameResult")
data class FsRenameResult(
    val entry: RemoteEntry,
) : RpcResponse

/** Создание каталога. */
@Serializable
@SerialName("fsMkdir")
data class FsMkdir(
    val path: String,
) : RpcRequest

/** Результат [FsMkdir]. */
@Serializable
@SerialName("fsMkdirResult")
data class FsMkdirResult(
    val entry: RemoteEntry,
) : RpcResponse

/** Запрос блока данных файла для предпросмотра (первые [maxBytes] с [offset]). */
@Serializable
@SerialName("fsReadBlock")
data class FsReadBlock(
    val path: String,
    val offset: Long = 0,
    val maxBytes: Int = 256 * 1024,
) : RpcRequest

/**
 * Результат [FsReadBlock]: блок данных (base64) с метаданными.
 *
 * @param entry метаданные файла.
 * @param offset фактическое смещение блока.
 * @param eof достигнут ли конец файла.
 * @param dataBase64 содержимое блока в base64.
 */
@Serializable
@SerialName("fsReadBlockResult")
data class FsReadBlockResult(
    val entry: RemoteEntry,
    val offset: Long,
    val eof: Boolean,
    val dataBase64: String,
) : RpcResponse
