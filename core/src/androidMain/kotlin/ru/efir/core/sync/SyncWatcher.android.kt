package ru.efir.core.sync

import android.os.FileObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ru.efir.core.protocol.SyncRuleConfig
import java.io.File

/**
 * Android-наблюдатель изменений на базе `FileObserver`.
 *
 * `FileObserver` на Android не рекурсивен, поэтому регистрируем обзервер на
 * корень и на каждый существующий подкаталог (в разумных пределах). Относительные
 * пути публикуются с прямым разделителем `/`.
 */
private class FileObserverSyncWatcher : SyncWatcher {

    override fun changes(rule: SyncRuleConfig): Flow<String> = callbackFlow {
        val root = File(rule.localPath)
        if (!root.exists() || !root.isDirectory) {
            awaitClose { }
            return@callbackFlow
        }
        val rootPath = root.absolutePath
        val observers = mutableListOf<FileObserver>()

        fun registerDir(dir: File) {
            if (observers.size >= MAX_OBSERVERS) return
            val base = dir.absolutePath
            val observer = object : FileObserver(base, ALL_EVENTS) {
                override fun onEvent(event: Int, path: String?) {
                    if (path.isNullOrBlank()) return
                    val full = File(base, path)
                    if (full.isDirectory) return
                    val rel = full.absolutePath
                        .removePrefix(rootPath)
                        .trimStart(File.separatorChar)
                        .replace('\\', '/')
                    if (rel.isNotEmpty()) trySend(rel)
                }
            }
            observer.startWatching()
            observers += observer
        }

        fun walk(dir: File, depth: Int) {
            if (depth > MAX_DEPTH) return
            registerDir(dir)
            dir.listFiles()?.filter { it.isDirectory }?.forEach { walk(it, depth + 1) }
        }

        walk(root, 0)

        awaitClose {
            observers.forEach { runCatching { it.stopWatching() } }
        }
    }

    private companion object {
        const val MAX_OBSERVERS = 256
        const val MAX_DEPTH = 8
        const val ALL_EVENTS = FileObserver.CREATE or FileObserver.MODIFY or
            FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM or
            FileObserver.CLOSE_WRITE
    }
}

actual fun createLocalSyncWatcher(): SyncWatcher = FileObserverSyncWatcher()
