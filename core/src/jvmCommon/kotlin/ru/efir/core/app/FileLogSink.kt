package ru.efir.core.app

import java.io.File
import java.util.concurrent.Executors

internal actual fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock, block)

/**
 * Журнал в файле `efir.log` папки [dir]; дойдя до [MAX_BYTES], он становится
 * `efir.1.log` (прежний такой удаляется), и запись начинается заново — журнал
 * не растёт без конца. Пишет отдельный поток: тот, кто записал строку, диска не ждёт.
 */
class FileLogSink(private val dir: File) : LogSink {
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "efir-log").apply { isDaemon = true } }
    private val lock = Any()

    private val file get() = File(dir, FILE)
    private val old get() = File(dir, OLD)

    override val path: String get() = file.absolutePath

    override fun append(line: String) {
        runCatching { writer.execute { write(line) } }
    }

    /** Строка, после которой приложение завершится: пишется сразу, на вызвавшем потоке. */
    fun appendNow(line: String) = write(line)

    private fun write(line: String) = synchronized(lock) {
        runCatching {
            dir.mkdirs()
            if (file.length() > MAX_BYTES) {
                old.delete()
                file.renameTo(old)
            }
            file.appendText(line + System.lineSeparator())
        }
    }

    override fun read(): String {
        // Сначала дописывается то, что ещё в очереди (замок при этом свободен: он нужен пишущему потоку).
        runCatching { writer.submit {}.get() }
        return synchronized(lock) { readFiles() }
    }

    private fun readFiles(): String {
        return listOf(old, file).filter { it.isFile }.joinToString("") { runCatching { it.readText() }.getOrDefault("") }
    }

    override fun clear() {
        synchronized(lock) {
            old.delete()
            file.delete()
        }
    }

    private companion object {
        const val FILE = "efir.log"
        const val OLD = "efir.1.log"
        const val MAX_BYTES = 512 * 1024L
    }
}
