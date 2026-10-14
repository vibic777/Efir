package ru.efir.core.sync

/**
 * Сравнивает снимки локальной и удалённой стороны с базовой линией последней
 * успешной синхронизации и выдаёт список различий [SyncFileDiff].
 *
 * Базовая линия хранит наблюдённое состояние *каждой* стороны, поэтому
 * «изменение» определяется относительно собственной стороны:
 *  - узел появился там, где базы не было — [SyncSideChange.CREATED];
 *  - узел исчез, хотя был в базе — [SyncSideChange.REMOVED];
 *  - содержимое/размер/mtime отличаются от базы — [SyncSideChange.MODIFIED].
 *
 * Это устойчиво к копированию: после синхронизации базовые состояния сторон
 * совпадают с фактическими, даже если mtime источника и копии различаются.
 *
 * Конфликт фиксируется, когда обе стороны изменились несовместимо.
 */
class SyncDiffer {

    /** Строит различия по объединению путей обеих сторон и базовой линии. */
    fun diff(
        local: SyncSnapshot,
        remote: SyncSnapshot,
        baseline: SyncBaseline?,
    ): List<SyncFileDiff> {
        val base = baseline?.byPath() ?: emptyMap()
        val paths = (local.byPath.keys + remote.byPath.keys + base.keys).sorted()

        return paths.map { path ->
            val localNode = local[path]
            val remoteNode = remote[path]
            val baseEntry = base[path]

            val localChange = classify(localNode, baseEntry?.local)
            val remoteChange = classify(remoteNode, baseEntry?.remote)
            val conflict = isConflict(localNode, remoteNode, localChange, remoteChange)

            SyncFileDiff(
                relativePath = path,
                local = localNode,
                remote = remoteNode,
                localChange = localChange,
                remoteChange = remoteChange,
                conflict = conflict,
            )
        }
    }

    /** Классифицирует изменение стороны относительно её базового состояния. */
    private fun classify(node: SyncFileNode?, base: SyncFileNode?): SyncSideChange = when {
        node == null && base == null -> SyncSideChange.NONE
        node == null -> SyncSideChange.REMOVED
        base == null -> SyncSideChange.CREATED
        contentEquals(node, base) -> SyncSideChange.NONE
        else -> SyncSideChange.MODIFIED
    }

    /**
     * Конфликт: обе стороны менялись, но пришли к разному состоянию.
     * Если обе стороны изменились, но содержимое совпало — конфликта нет
     * (стороны конвергировали независимо).
     */
    private fun isConflict(
        local: SyncFileNode?,
        remote: SyncFileNode?,
        localChange: SyncSideChange,
        remoteChange: SyncSideChange,
    ): Boolean {
        val localTouched = localChange != SyncSideChange.NONE
        val remoteTouched = remoteChange != SyncSideChange.NONE
        if (!localTouched || !remoteTouched) return false
        // Обе стороны присутствуют и совпали — конфликта нет.
        if (local != null && remote != null && nodesEqual(local, remote)) return false
        return true
    }

    private fun contentEquals(node: SyncFileNode, base: SyncFileNode): Boolean {
        if (node.directory != base.directory) return false
        if (node.directory) return true
        return if (node.sha256 != null && base.sha256 != null) {
            node.sha256 == base.sha256
        } else {
            node.size == base.size && node.modifiedAt == base.modifiedAt
        }
    }

    private fun nodesEqual(a: SyncFileNode, b: SyncFileNode): Boolean {
        if (a.directory != b.directory) return false
        if (a.directory) return true
        return if (a.sha256 != null && b.sha256 != null) {
            a.sha256 == b.sha256
        } else {
            a.size == b.size
        }
    }
}
