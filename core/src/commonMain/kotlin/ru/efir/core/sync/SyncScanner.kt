package ru.efir.core.sync

import ru.efir.core.files.PathSanitizer
import ru.efir.core.protocol.RemoteEntry
import ru.efir.core.protocol.SyncSide

/**
 * Функция листинга каталога (локально или удалённо) — возвращает содержимое
 * каталога [path] (по абсолютному пути стороны).
 */
fun interface SyncLister {
    suspend fun list(path: String): List<RemoteEntry>
}

/** Функция вычисления SHA-256 для стороны (или `null`, если недоступна). */
fun interface SyncHasher {
    suspend fun sha256(path: String): String?
}

/**
 * Рекурсивно обходит корневую папку правила и строит [SyncSnapshot] с
 * относительными путями. Применяет [PathFilter] (маски, размер, временные файлы).
 *
 * Права доступа к хэшам опциональны: если [hasher] не задан или вернул `null`,
 * узел остаётся без контрольной суммы — тогда differ сравнивает по размеру и mtime.
 *
 * @param side сторона, которой принадлежит сканируемое дерево.
 * @param lister листинг каталогов.
 * @param hasher вычисление хэша содержимого (может быть `null`).
 * @param maxDepth защита от слишком глубокого дерева.
 */
class SyncScanner(
    private val side: SyncSide,
    private val lister: SyncLister,
    private val hasher: SyncHasher? = null,
    private val maxDepth: Int = 64,
) {

    /** Сканирует [root] и возвращает снимок прошедших фильтр узлов. */
    suspend fun scan(root: String, filter: PathFilter = PathFilter.ACCEPT_ALL): SyncSnapshot {
        val normalizedRoot = PathSanitizer.normalize(root)
        val nodes = mutableListOf<SyncFileNode>()
        walk(normalizedRoot, normalizedRoot, filter, nodes, 0)
        return SyncSnapshot(side = side, nodes = nodes)
    }

    private suspend fun walk(
        root: String,
        dir: String,
        filter: PathFilter,
        out: MutableList<SyncFileNode>,
        depth: Int,
    ) {
        if (depth > maxDepth) return
        val entries = runCatching { lister.list(dir) }.getOrDefault(emptyList())
        for (entry in entries) {
            val relative = relativePath(root, entry.path)
            if (relative.isEmpty()) continue
            if (entry.directory) {
                if (filter.excludesDirectory(relative)) continue
                if (filter.accepts(relative, directory = true)) {
                    out.add(
                        SyncFileNode(
                            relativePath = relative,
                            directory = true,
                            size = 0,
                            modifiedAt = entry.modifiedAt,
                            sha256 = null,
                        ),
                    )
                }
                walk(root, entry.path, filter, out, depth + 1)
            } else {
                if (!filter.accepts(relative, directory = false, size = entry.size)) continue
                val sha = hasher?.let { runCatching { it.sha256(entry.path) }.getOrNull() }
                out.add(
                    SyncFileNode(
                        relativePath = relative,
                        directory = false,
                        size = entry.size,
                        modifiedAt = entry.modifiedAt,
                        sha256 = sha,
                    ),
                )
            }
        }
    }

    /** Относительный путь [path] внутри [root] (с каноническим `/`). */
    private fun relativePath(root: String, path: String): String {
        val normalized = PathSanitizer.normalize(path)
        val base = if (root.endsWith("/")) root else "$root/"
        return when {
            normalized == root -> ""
            normalized.startsWith(base) -> normalized.substring(base.length)
            else -> PathSanitizer.name(normalized)
        }
    }
}
