package ru.efir.core.net

import ru.efir.core.app.loadPreference
import ru.efir.core.app.savePreference

/**
 * Порт, на котором это устройство ждёт подключения («Настройки» → «Сеть»).
 *
 * По умолчанию порт выбирается сам: стандартный, а если он занят другой программой —
 * ближайший свободный за ним. Вручную задаётся любой порт; занят ли он, видно тут же.
 * Порт читается при запуске: изменение действует со следующего запуска приложения.
 * Другому устройству менять у себя ничего не нужно — порт оно узнаёт из поиска,
 * из данных сопряжения или из адреса вида `192.168.1.5:порт`.
 */
object NetworkSettings {
    const val PREF_PORT = "listenPort"

    /** Порты ниже 1024 системные, приложению без прав их не дают. */
    val RANGE = 1024..65535

    /** Сколько портов после стандартного пробует автоматический выбор. */
    private const val AUTO_TRIES = 20

    fun valid(port: Int?): Boolean = port != null && port in RANGE && port != EfirNetwork.DISCOVERY_PORT

    /** Порт, заданный вручную, или `null` — выбирать автоматически. */
    fun manualPort(): Int? = loadPreference(PREF_PORT)?.toIntOrNull()?.takeIf { valid(it) }

    /** Задаёт порт вручную; `null` возвращает автоматический выбор. */
    fun saveManualPort(port: Int?) = savePreference(PREF_PORT, port?.toString().orEmpty())

    /** Порт автоматического выбора на сейчас: стандартный или первый свободный за ним. */
    fun autoPort(free: (Int) -> Boolean = ::isPortFree): Int =
        (EfirNetwork.DEFAULT_PORT..EfirNetwork.DEFAULT_PORT + AUTO_TRIES)
            .firstOrNull { valid(it) && free(it) } ?: EfirNetwork.DEFAULT_PORT

    /** На каком порту ждать подключения в этом запуске. */
    fun listenPort(): Int = manualPort() ?: autoPort()
}