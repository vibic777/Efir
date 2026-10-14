package ru.efir.core.app

import androidx.compose.runtime.mutableStateOf

/**
 * Папки этого устройства, которые выбирает человек («Настройки» → «Папки»): куда
 * складывать скачанное, какую папку предлагать новому правилу синхронизации и
 * какие папки, кроме стандартных, открыты другому устройству.
 *
 * Значения читаются из настроек при первом обращении и запоминаются между
 * запусками. Пустой выбор означает папку по умолчанию. Папка загрузок и папки
 * правил синхронизации открыты другому устройству сами: без этого в них нельзя
 * было бы ни писать, ни синхронизировать (см. `allowedFileRoots`).
 */
object AppFolders {
    const val PREF_DOWNLOADS = "downloadDir"
    const val PREF_SYNC = "syncLocalDir"
    const val PREF_SHARED = "sharedFolders"

    private val downloads = mutableStateOf<String?>(null)
    private val sync = mutableStateOf<String?>(null)
    private val shared = mutableStateOf<List<String>>(emptyList())

    @Volatile
    private var rules: List<String> = emptyList()

    @Volatile
    private var loaded = false

    /** Растёт с каждым изменением: по нему сбрасывается кэш разрешённых корней. */
    @Volatile
    var version: Int = 0
        private set

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        downloads.value = loadPreference(PREF_DOWNLOADS)?.let(::clean)
        sync.value = loadPreference(PREF_SYNC)?.let(::clean)
        shared.value = loadPreference(PREF_SHARED).orEmpty().split('\n').mapNotNull(::clean).distinct()
        version++
    }

    /** Единый вид пути: прямые слэши, без пробелов по краям и слэша в конце (кроме корня диска). */
    fun clean(path: String): String? {
        val unified = path.trim().replace('\\', '/')
        if (unified.isEmpty()) return null
        val trimmed = unified.trimEnd('/')
        return when {
            trimmed.isEmpty() -> "/"
            trimmed.length == 2 && trimmed[1] == ':' -> "$trimmed/"
            else -> trimmed
        }
    }

    /** Куда складывается скачанное. */
    val downloadDir: String
        get() {
            ensureLoaded()
            return downloads.value ?: defaultDownloadDir().replace('\\', '/')
        }

    /** Выбрана ли папка загрузок человеком (иначе — стандартная). */
    val downloadDirChosen: Boolean
        get() {
            ensureLoaded()
            return downloads.value != null
        }

    /** Задаёт папку загрузок; `null` возвращает стандартную. */
    fun chooseDownloadDir(path: String?) {
        ensureLoaded()
        downloads.value = path?.let(::clean)
        savePreference(PREF_DOWNLOADS, downloads.value.orEmpty())
        version++
    }

    /** Папка этого устройства, которую получает новое правило синхронизации. */
    val syncDir: String
        get() {
            ensureLoaded()
            return sync.value ?: defaultSyncLocalDir().replace('\\', '/')
        }

    val syncDirChosen: Boolean
        get() {
            ensureLoaded()
            return sync.value != null
        }

    fun chooseSyncDir(path: String?) {
        ensureLoaded()
        sync.value = path?.let(::clean)
        savePreference(PREF_SYNC, sync.value.orEmpty())
        version++
    }

    /** Папки, которые человек открыл другому устройству сверх стандартных. */
    val sharedFolders: List<String>
        get() {
            ensureLoaded()
            return shared.value
        }

    fun addSharedFolder(path: String) {
        ensureLoaded()
        val folder = clean(path) ?: return
        if (folder in shared.value) return
        saveShared(shared.value + folder)
    }

    fun removeSharedFolder(path: String) {
        ensureLoaded()
        saveShared(shared.value - path)
    }

    private fun saveShared(folders: List<String>) {
        shared.value = folders
        savePreference(PREF_SHARED, folders.joinToString("\n"))
        version++
    }

    /** Папки этого устройства из правил синхронизации: их сообщает контроллер. */
    var ruleFolders: List<String>
        get() = rules
        set(value) {
            val folders = value.mapNotNull(::clean).distinct()
            if (folders == rules) return
            rules = folders
            version++
        }
}
