package ru.efir.desktop

import ru.efir.core.app.AppLog
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * «Запускать вместе с системой». Windows: значение в `HKCU\...\Run` пользователя —
 * права администратора не нужны. Linux: файл в `~/.config/autostart`, который
 * запускает любой рабочий стол, соблюдающий правило XDG. Само значение (или файл)
 * и есть настройка: в папке данных ничего не хранится.
 */
object AutoStart {
    private const val RUN = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "Efir"

    /** С этим аргументом Эфир запускает система: окно тогда не показывается, Эфир сидит в трее. */
    const val ARG = "--autostart"

    @Volatile
    var startedBySystem: Boolean = false

    private val onWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /** Что запускать: Efir.exe упакованного приложения. `null` при запуске из Gradle и в тестах. */
    private val exe: File?
        get() = System.getProperty("jpackage.app-path")?.let { File(it).absoluteFile }?.takeIf { it.isFile }

    /** Можно ли включить автозапуск (приложение установлено или распаковано, а не запущено из сборки). */
    val available: Boolean get() = exe != null

    private fun linuxEntry(): File {
        val config = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), ".config")
        return File(File(config, "autostart"), "efir.desktop")
    }

    private fun run(vararg command: String): Boolean = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
    }.getOrDefault(false)

    fun isOn(): Boolean {
        if (!onWindows) return linuxEntry().isFile
        return run("reg", "query", RUN, "/v", NAME)
    }

    /** Включает или выключает; `false`, если система отказала (переключатель тогда покажет то, что есть на деле). */
    fun set(on: Boolean): Boolean {
        val exe = exe ?: return false
        if (!onWindows) {
            return runCatching {
                val entry = linuxEntry()
                if (on) {
                    entry.parentFile.mkdirs()
                    entry.writeText(
                        "[Desktop Entry]\nType=Application\nName=Efir\nName[ru]=Эфир\n" +
                            "Exec=\"${exe.path}\" $ARG\nTerminal=false\nX-GNOME-Autostart-enabled=true\n",
                    )
                } else if (entry.isFile) {
                    check(entry.delete())
                }
            }.isSuccess
        }
        return if (on) {
            run("reg", "add", RUN, "/v", NAME, "/t", "REG_SZ", "/d", "\"${exe.path}\" $ARG", "/f")
        } else {
            !isOn() || run("reg", "delete", RUN, "/v", NAME, "/f")
        }
    }
}

/**
 * Один Эфир на пользователя. Первый держит замок на `instance.lock` в папке данных
 * и слушает локальный порт, записанный в `instance.port`; второй запуск просит
 * первый показать окно и завершается. Иначе второй экземпляр не смог бы занять
 * порты ожидания и обнаружения и молча работал бы наполовину.
 *
 * Любой сбой самой проверки даёт приложению запуститься: замок — удобство, а не
 * причина не открыться.
 */
object SingleInstance {
    private const val SHOW = "show"

    private var lock: FileLock? = null
    private var lockFile: RandomAccessFile? = null
    private var server: ServerSocket? = null

    /** Что делает работающий Эфир, когда его просят показаться (задаёт окно). */
    @Volatile
    var onShow: () -> Unit = {}

    /** `true`: этот Эфир единственный. `false`: другой уже работает и его попросили показать окно. */
    fun claim(root: File): Boolean {
        root.mkdirs()
        val file = runCatching { RandomAccessFile(File(root, "instance.lock"), "rw") }.getOrNull() ?: return true
        val got = try {
            file.channel.tryLock()
        } catch (_: java.nio.channels.OverlappingFileLockException) {
            null
        } catch (_: Exception) {
            runCatching { file.close() }
            return true
        }
        if (got == null) {
            runCatching { file.close() }
            // Работающий не ответил (завис или завершается) — запускаемся сами, а не исчезаем без слова.
            val answered = askTheOtherToShow(root)
            AppLog.i("app", if (answered) "a second start: the running Efir was asked to show its window" else "a second start: the running Efir did not answer, starting anyway")
            runCatching { AppLog.read() }
            return !answered
        }
        lockFile = file
        lock = got
        listen(root)
        return true
    }

    private fun askTheOtherToShow(root: File): Boolean {
        val lines = runCatching { File(root, "instance.port").readLines() }.getOrNull() ?: return false
        val port = lines.firstOrNull()?.trim()?.toIntOrNull() ?: return false
        val token = lines.getOrNull(1)?.trim().orEmpty()
        return runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000)
                s.getOutputStream().write("$SHOW\n$token\n".toByteArray())
            }
        }.isSuccess
    }

    private fun listen(root: File) {
        val socket = runCatching { ServerSocket(0, 4, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        server = socket
        // Порт и секрет лежат в папке данных пользователя: просьба без секрета не выполняется.
        val token = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        runCatching { File(root, "instance.port").writeText("${socket.localPort}\n$token\n") }
        Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching {
                    client.use { c ->
                        c.soTimeout = 2_000
                        val reader = c.getInputStream().bufferedReader()
                        val line = reader.readLine()
                        val secret = reader.readLine().orEmpty()
                        if (!MessageDigest.isEqual(secret.toByteArray(), token.toByteArray())) return@use
                        if (line == SHOW) javax.swing.SwingUtilities.invokeLater { onShow() }
                    }
                }
            }
        }, "efir-single-instance").apply { isDaemon = true }.start()
    }
}

/** Необработанная ошибка любого потока записывается в `crash.log` папки данных: по нему ищут причину. */
fun installCrashLog(dataDir: File) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        runCatching {
            dataDir.mkdirs()
            val log = File(dataDir, "crash.log")
            // Журнал не растёт без конца: старый начинается заново.
            if (log.length() > 512 * 1024) log.delete()
            log.appendText("${java.time.LocalDateTime.now()} [${thread.name}]\n${error.stackTraceToString()}\n")
        }
        // То же — в общий журнал приложения (его и присылают).
        AppLog.fatal("app", "uncaught error in thread ${thread.name}", error)
        runCatching { AppLog.read() }
        previous?.uncaughtException(thread, error)
    }
}
