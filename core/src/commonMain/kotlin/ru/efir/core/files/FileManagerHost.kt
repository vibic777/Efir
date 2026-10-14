package ru.efir.core.files

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import ru.efir.core.net.EfirSession
import ru.efir.core.protocol.ErrorMessage
import ru.efir.core.protocol.FsDelete
import ru.efir.core.protocol.FsDeleteResult
import ru.efir.core.protocol.FsList
import ru.efir.core.protocol.FsListResult
import ru.efir.core.protocol.FsMkdir
import ru.efir.core.protocol.FsMkdirResult
import ru.efir.core.protocol.FsReadBlock
import ru.efir.core.protocol.FsReadBlockResult
import ru.efir.core.protocol.FsRename
import ru.efir.core.protocol.FsRenameResult
import ru.efir.core.protocol.FsStat
import ru.efir.core.protocol.FsStatResult
import ru.efir.core.protocol.Message

/**
 * Принимающая сторона файлового менеджера.
 *
 * Регистрирует RPC-обработчики `fsList`/`fsStat`/`fsDelete`/`fsRename`/`fsMkdir`
 * и `fsReadBlock`, делегируя операции backend'у [FileSystem] (JVM/SAF), и
 * преобразует ошибки доступа в понятные [ErrorMessage].
 */
class FileManagerHost(
    private val session: EfirSession,
    private val fileSystem: FileSystem = LocalFileSystemFactory.create(),
) {

    /** Регистрирует обработчики на сессии. Вызывается после установки соединения. */
    fun attach() {
        session.rpc.onRequest<FsList> { request -> handleList(request) }
        session.rpc.onRequest<FsStat> { request -> handleStat(request) }
        session.rpc.onRequest<FsDelete> { request -> handleDelete(request) }
        session.rpc.onRequest<FsRename> { request -> handleRename(request) }
        session.rpc.onRequest<FsMkdir> { request -> handleMkdir(request) }
        session.rpc.onRequest<FsReadBlock> { request -> handleReadBlock(request) }
    }

    private suspend fun handleList(request: FsList): Message = guard {
        val listing = fileSystem.list(request.path, request.offset, request.limit)
        FsListResult(
            path = listing.path,
            entries = listing.entries,
            hasMore = listing.hasMore,
        )
    }

    private suspend fun handleStat(request: FsStat): Message = guard {
        FsStatResult(entry = fileSystem.stat(request.path))
    }

    private suspend fun handleDelete(request: FsDelete): Message = guard {
        fileSystem.delete(request.paths, request.recursive)
        FsDeleteResult(deleted = request.paths.size)
    }

    private suspend fun handleRename(request: FsRename): Message = guard {
        FsRenameResult(entry = fileSystem.rename(request.path, request.newName))
    }

    private suspend fun handleMkdir(request: FsMkdir): Message = guard {
        FsMkdirResult(entry = fileSystem.mkdir(request.path))
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun handleReadBlock(request: FsReadBlock): Message = guard {
        val handle = fileSystem.open(request.path)
        val data = fileSystem.read(request.path, request.offset, request.maxBytes)
        FsReadBlockResult(
            entry = ru.efir.core.protocol.RemoteEntry(
                name = handle.name,
                path = handle.path,
                directory = false,
                size = handle.size,
                modifiedAt = handle.modifiedAt,
                readable = true,
                writable = false,
            ),
            offset = request.offset,
            eof = request.offset + data.size >= handle.size,
            dataBase64 = Base64.Default.encode(data),
        )
    }

    /** Оборачивает операцию: ошибки доступа/аргументов → [ErrorMessage]. */
    private suspend fun guard(block: suspend () -> Message): Message = try {
        block()
    } catch (error: IllegalArgumentException) {
        ErrorMessage(code = "fs_invalid_path", message = error.message ?: "Некорректный путь")
    } catch (error: SecurityException) {
        ErrorMessage(code = "fs_permission_denied", message = error.message ?: "Нет доступа")
    } catch (error: Exception) {
        ErrorMessage(code = "fs_error", message = error.message ?: "Ошибка файловой системы")
    }
}

/**
 * Фабрика backend'а файловой системы. На JVM (Android и Desktop) — [LocalFileSystem].
 * Позже может быть заменена на SAF-реализацию для Android.
 */
expect object LocalFileSystemFactory {
    fun create(): FileSystem
}
