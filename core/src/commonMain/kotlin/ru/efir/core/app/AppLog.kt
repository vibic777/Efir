package ru.efir.core.app

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Куда журнал пишется на диск (реализация — файл в папке данных платформы). */
interface LogSink {
    /** Где лежит журнал — чтобы показать человеку. */
    val path: String?

    fun append(line: String)

    /** Весь журнал, старые строки первыми. */
    fun read(): String

    fun clear()
}

/**
 * Журнал приложения, как в Notio: что произошло и что пошло не так, по строке на
 * событие, в файле `efir.log` папки данных (предыдущий хранится как `efir.1.log`).
 * По нему ищут причину любой проблемы: запуск, сеть, поиск устройств, сопряжение,
 * соединение, разрешения, ошибки.
 *
 * В журнал никогда не попадают ключи доступа, токены, содержимое файлов, буфера
 * обмена, SMS и уведомлений. Сам он устройство не покидает: «Настройки → Журнал»
 * показывает его, копирует и отдаёт файлом.
 */
object AppLog {
    enum class Level { DEBUG, INFO, WARN, ERROR, FATAL }

    @Volatile
    private var sink: LogSink? = null

    /** Строки, записанные до подключения файла (самый ранний запуск). */
    private val early = ArrayDeque<String>()
    private val lock = Any()

    /** Подключает файл журнала; строки, накопленные до этого, дописываются в него. */
    fun install(target: LogSink) {
        val pending = synchronizedOnLock {
            sink = target
            early.toList().also { early.clear() }
        }
        pending.forEach { runCatching { target.append(it) } }
    }

    val path: String? get() = sink?.path

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String, error: Throwable? = null) = log(Level.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = log(Level.ERROR, tag, message, error)
    fun fatal(tag: String, message: String, error: Throwable? = null) = log(Level.FATAL, tag, message, error)

    fun log(level: Level, tag: String, message: String, error: Throwable? = null) {
        val line = format(level, tag, message, error)
        val target = sink
        if (target != null) {
            runCatching { target.append(line) }
        } else {
            synchronizedOnLock {
                if (early.size >= EARLY_LINES) early.removeFirst()
                early.addLast(line)
            }
        }
    }

    /** Весь журнал текстом; пусто, если его нет. */
    fun read(): String = runCatching { sink?.read() }.getOrNull().orEmpty()

    /** Последние [lines] строк — для показа на экране. */
    fun tail(lines: Int): String = read().trimEnd().lines().takeLast(lines).joinToString("\n")

    fun clear() {
        runCatching { sink?.clear() }
        i("app", "the journal was cleared")
    }

    private fun format(level: Level, tag: String, message: String, error: Throwable?): String {
        val t = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        fun two(n: Int) = n.toString().padStart(2, '0')
        val stamp = "${t.year}-${two(t.monthNumber)}-${two(t.dayOfMonth)} ${two(t.hour)}:${two(t.minute)}:${two(t.second)}." +
            (t.nanosecond / 1_000_000).toString().padStart(3, '0')
        val cause = error?.let { e ->
            // Название ошибки, её слова и первые строки стека: где случилось.
            "\n    " + e.stackTraceToString().lineSequence().take(STACK_LINES).joinToString("\n    ")
        }.orEmpty()
        return "$stamp ${level.name.padEnd(5)} $tag: $message$cause"
    }

    private fun <T> synchronizedOnLock(block: () -> T): T = locked(lock, block)

    private const val EARLY_LINES = 200
    private const val STACK_LINES = 12
}

/** Выполняет [block], пока никто другой не держит [lock]. */
internal expect fun <T> locked(lock: Any, block: () -> T): T
