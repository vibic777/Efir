package ru.efir.core.sync

import ru.efir.core.files.FileManagerClient
import ru.efir.core.protocol.RemoteEntry
import ru.efir.core.transfer.TransferEngine

/** [SyncRemoteFs] поверх клиента файлового менеджера (RPC к Android). */
class FileManagerRemoteFs(
    private val client: FileManagerClient,
) : SyncRemoteFs {

    override suspend fun list(path: String): List<RemoteEntry> = client.listRemote(path)

    override suspend fun mkdir(path: String): Boolean = client.mkdirRemote(path)

    override suspend fun delete(paths: List<String>, recursive: Boolean): Boolean =
        client.deleteRemote(paths, recursive)
}

/**
 * [SyncTransfer] поверх движка передачи файлов.
 *
 * Направление относительно ПК-клиента: [upload] отправляет локальный файл на
 * удалённую сторону, [download] — наоборот.
 */
class EngineSyncTransfer(
    private val engine: TransferEngine,
    private val priority: ru.efir.core.transfer.TransferPriority = ru.efir.core.transfer.TransferPriority.NORMAL,
    private val group: String? = null,
    private val groupLabel: String? = null,
) : SyncTransfer {

    override suspend fun upload(localPath: String, remotePath: String, overwrite: Boolean) {
        engine.uploadFile(localPath, remotePath, overwrite, priority = priority, group = group, groupLabel = groupLabel)
    }

    override suspend fun download(remotePath: String, localPath: String, overwrite: Boolean) {
        engine.downloadFile(remotePath, localPath, overwrite, priority = priority, group = group, groupLabel = groupLabel)
    }
}
