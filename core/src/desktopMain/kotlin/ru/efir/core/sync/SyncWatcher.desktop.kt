package ru.efir.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.efir.core.protocol.SyncRuleConfig
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes

/**
 * Desktop-наблюдатель изменений на базе `java.nio.file.WatchService`.
 *
 * Рекурсивно регистрирует все каталоги корня правила (включая появляющиеся) и
 * публикует относительные пути изменённых файлов.
 */
private class WatchServiceSyncWatcher : SyncWatcher {

    override fun changes(rule: SyncRuleConfig): Flow<String> = callbackFlow {
        val root = File(rule.localPath)
        if (!root.exists() || !root.isDirectory) {
            awaitClose { }
            return@callbackFlow
        }
        val rootPath = root.toPath()
        val service: WatchService = rootPath.fileSystem.newWatchService()
        val keys = mutableMapOf<WatchKey, Path>()
        val dirs = mutableSetOf<Path>()

        fun register(dir: Path) {
            if (!dirs.add(dir)) return
            runCatching {
                keys[dir.register(
                    service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE,
                )] = dir
            }
        }

        fun registerTree(start: Path) {
            runCatching {
                Files.walkFileTree(
                    start,
                    object : SimpleFileVisitor<Path>() {
                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            register(dir)
                            return FileVisitResult.CONTINUE
                        }
                    },
                )
            }
        }

        registerTree(rootPath)

        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch {
            while (isActive) {
                val key = runCatching { service.take() }.getOrNull() ?: break
                val dir = keys[key]
                for (event in key.pollEvents()) {
                    val kind = event.kind()
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue
                    val relative = event.context() as? Path ?: continue
                    val child = dir?.resolve(relative) ?: continue
                    if (Files.isDirectory(child)) {
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) registerTree(child)
                        continue
                    }
                    val rel = rootPath.relativize(child).toString().replace('\\', '/')
                    trySend(rel)
                }
                if (!key.reset()) {
                    keys.remove(key)
                    if (keys.isEmpty()) break
                }
            }
        }

        awaitClose {
            job.cancel()
            runCatching { service.close() }
        }
    }
}

actual fun createLocalSyncWatcher(): SyncWatcher = WatchServiceSyncWatcher()
