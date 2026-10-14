package ru.efir.core.net

import kotlinx.coroutines.CoroutineScope

/**
 * Запущенный хост Efir: слушает входящие control-подключения.
 *
 * Реализация платформенная (см. [createEfirHost]); на Android это сервер,
 * к которому подключается Windows-клиент, в обратном сценарии — наоборот.
 */
interface EfirHost {

    /** Запускает приём соединений (не блокирует вызывающий поток). */
    fun start()

    /** Останавливает хост и освобождает сетевые ресурсы. */
    fun stop()
}

/**
 * Создаёт платформенный хост Efir, слушающий [config].
 *
 * Каждое принятое соединение передаётся в [onConnection]; функция имеет
 * suspend-тип, чтобы обработчик мог дождаться завершения сессии.
 */
expect fun createEfirHost(
    config: ServerConfig,
    onConnection: suspend (EfirConnection) -> Unit,
): EfirHost

/**
 * Устанавливает клиентское соединение Efir с удалённым хостом по [config].
 *
 * @param parent область корутин для фоновых задач транспорта (чтение кадров).
 */
expect suspend fun openEfirConnection(
    config: ClientConfig,
    parent: CoroutineScope,
): EfirConnection
