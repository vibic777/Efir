package ru.efir.core.files

/**
 * Корневые каталоги файловой системы по умолчанию для текущей платформы.
 *
 * Android: внешнее хранилище (`/storage/emulated/0`), при отсутствии — `filesDir`.
 * Desktop: домашний каталог пользователя.
 *
 * Значения используются для построения [FileSystem.roots] и ограничения доступа.
 */
expect fun defaultFileRoots(): List<String>

/**
 * Папки, которые другому устройству не отдаются никогда: данные самого приложения
 * (токены доверия, журнал, настройки). На ПК они лежат внутри домашней папки,
 * которая открыта целиком.
 */
expect fun protectedFilePaths(): List<String>

/**
 * С чего начинается выбор папки на этом устройстве: диски на ПК, общая память и
 * карта памяти на телефоне. Это только то, что видит сам человек в своём приложении;
 * другому устройству открыты лишь [allowedFileRoots].
 */
expect fun localBrowseRoots(): List<String>

/** Подпапки [path] на этом устройстве (полные пути через `/`), по алфавиту; пусто, если прочитать нельзя. */
expect fun listLocalFolders(path: String): List<String>

/** Создаёт папку на этом устройстве; `true`, если она есть после вызова. */
expect fun makeLocalFolder(path: String): Boolean

private class RootsCache(val version: Int, val at: Long, val roots: List<String>)

@Volatile
private var rootsCache: RootsCache? = null

/** Стандартные корни перечитываются не чаще: на телефоне за ними стоят системные вызовы. */
private const val ROOTS_TTL_MS = 30_000L

/**
 * Все папки этого устройства, открытые другому: стандартные корни платформы, папки,
 * добавленные в «Настройках», папка загрузок и папки правил синхронизации.
 * За их пределы файловые операции другой стороны не выходят.
 */
fun allowedFileRoots(): List<String> {
    val folders = ru.efir.core.app.AppFolders
    // Сначала чтение папок: первое обращение поднимает их из настроек и меняет версию.
    val extra = folders.sharedFolders + folders.downloadDir + folders.ruleFolders
    val now = ru.efir.core.protocol.nowMillis()
    rootsCache?.let { if (it.version == folders.version && now - it.at < ROOTS_TTL_MS) return it.roots }
    val all = (defaultFileRoots() + extra).filter { it.isNotBlank() }
        .distinctBy { PathSanitizer.normalize(it).trimEnd('/') }
    // Папка внутри уже открытой папки отдельным разделом не показывается.
    val roots = all.filter { folder -> all.none { other -> other !== folder && PathSanitizer.isWithin(other, folder) } }
    rootsCache = RootsCache(folders.version, now, roots)
    return roots
}
