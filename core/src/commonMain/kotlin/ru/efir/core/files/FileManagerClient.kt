package ru.efir.core.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
import ru.efir.core.protocol.RemoteEntry

/**
 * Состояние файлового менеджера на стороне инициатора (ПК).
 *
 * @param currentPath текущий открытый каталог (`null` — ещё не выбран).
 * @param entries содержимое текущего каталога.
 * @param roots доступные корневые каталоги удалённой стороны.
 * @param hasMore есть ли ещё элементы (пагинация).
 * @param loading идёт ли операция.
 * @param error последняя ошибка.
 */
data class FileBrowserState(
    val currentPath: String? = null,
    val entries: List<RemoteEntry> = emptyList(),
    val roots: List<RemoteEntry> = emptyList(),
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Клиент файлового менеджера: навигация по удалённой ФС и базовые операции
 * (`list`/`stat`/`delete`/`rename`/`mkdir`), публикующие состояние для UI.
 *
 * Все запросы выполняются через RPC контрольного канала; ответы-ошибки
 * ([ErrorMessage]) превращаются в [FileBrowserState.error].
 */
class FileManagerClient(
    private val session: EfirSession,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    /** Загружает список корней удалённой стороны. */
    fun loadRoots() {
        scope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { session.rpc.call(FsList(path = null)) }
                .onSuccess { response -> applyRoots(response) }
                .onFailure { error -> fail(error) }
        }
    }

    /** Открывает каталог [path] (переход в него). */
    fun open(path: String) {
        scope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { session.rpc.call(FsList(path = path)) }
                .onSuccess { response -> applyListing(response) }
                .onFailure { error -> fail(error) }
        }
    }

    /** Обновляет текущий каталог. */
    fun refresh() {
        _state.value.currentPath?.let { open(it) } ?: loadRoots()
    }

    /** Переходит к родительскому каталогу (если возможно). */
    fun goUp() {
        val current = _state.value.currentPath ?: return
        val parent = PathSanitizer.parent(current)
        if (parent.isBlank() || parent == current) {
            loadRoots()
        } else {
            open(parent)
        }
    }

    /** Удаляет пути (рекурсивно при [recursive]). */
    fun delete(paths: List<String>, recursive: Boolean = false) {
        if (paths.isEmpty()) return
        scope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { session.rpc.call(FsDelete(paths = paths, recursive = recursive)) }
                .onSuccess { response ->
                    when (response) {
                        is FsDeleteResult -> refresh()
                        is ErrorMessage -> _state.value = _state.value.copy(loading = false, error = response.message)
                        else -> _state.value = _state.value.copy(loading = false)
                    }
                }
                .onFailure { error -> fail(error) }
        }
    }

    /** Переименовывает элемент [path] в [newName]. */
    fun rename(path: String, newName: String) {
        scope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { session.rpc.call(FsRename(path = path, newName = newName)) }
                .onSuccess { response ->
                    when (response) {
                        is FsRenameResult -> refresh()
                        is ErrorMessage -> _state.value = _state.value.copy(loading = false, error = response.message)
                        else -> _state.value = _state.value.copy(loading = false)
                    }
                }
                .onFailure { error -> fail(error) }
        }
    }

    /** Создаёт каталог [path]. */
    fun mkdir(path: String) {
        scope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { session.rpc.call(FsMkdir(path = path)) }
                .onSuccess { response ->
                    when (response) {
                        is FsMkdirResult -> refresh()
                        is ErrorMessage -> _state.value = _state.value.copy(loading = false, error = response.message)
                        else -> _state.value = _state.value.copy(loading = false)
                    }
                }
                .onFailure { error -> fail(error) }
        }
    }

    /** Метаданные пути. */
    suspend fun stat(path: String): RemoteEntry? = runCatching {
        (session.rpc.call(FsStat(path = path)) as? FsStatResult)?.entry
    }.getOrNull()

    /** Листинг удалённого каталога (для рекурсивной передачи папок); `null` — разделы устройства. */
    suspend fun listRemote(path: String?): List<RemoteEntry> =
        runCatching { session.rpc.call(FsList(path = path)) }
            .getOrNull()
            .let { (it as? FsListResult)?.entries }
            .orEmpty()

    /** Создаёт удалённый каталог; `true` при успехе. */
    suspend fun mkdirRemote(path: String): Boolean = runCatching {
        when (session.rpc.call(FsMkdir(path = path))) {
            is FsMkdirResult -> true
            else -> false
        }
    }.getOrDefault(false)

    /** Удаляет удалённые пути (рекурсивно при [recursive]); `true` при успехе. */
    suspend fun deleteRemote(paths: List<String>, recursive: Boolean = false): Boolean = runCatching {
        when (session.rpc.call(FsDelete(paths = paths, recursive = recursive))) {
            is FsDeleteResult -> true
            else -> false
        }
    }.getOrDefault(false)

    /**
     * Загружает первые [maxBytes] файла для предпросмотра.
     * Возвращает пару (метаданные, декодированные байты) или `null` при ошибке.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun readPreview(path: String, maxBytes: Int = 256 * 1024): Pair<RemoteEntry, ByteArray>? =
        runCatching {
            val response = session.rpc.call(FsReadBlock(path = path, offset = 0, maxBytes = maxBytes))
            val result = response as? FsReadBlockResult ?: return@runCatching null
            result.entry to Base64.Default.decode(result.dataBase64)
        }.getOrNull()

    private fun applyRoots(response: ru.efir.core.protocol.Message) {
        when (response) {
            is FsListResult -> _state.value = FileBrowserState(roots = response.entries, loading = false)
            is ErrorMessage -> _state.value = _state.value.copy(loading = false, error = response.message)
            else -> _state.value = _state.value.copy(loading = false)
        }
    }

    private fun applyListing(response: ru.efir.core.protocol.Message) {
        when (response) {
            is FsListResult -> _state.value = _state.value.copy(
                currentPath = response.path,
                entries = response.entries,
                hasMore = response.hasMore,
                loading = false,
                error = null,
            )

            is ErrorMessage -> _state.value = _state.value.copy(loading = false, error = response.message)
            else -> _state.value = _state.value.copy(loading = false)
        }
    }

    private fun fail(error: Throwable) {
        _state.value = _state.value.copy(loading = false, error = error.message ?: "Ошибка файловой операции")
    }
}
