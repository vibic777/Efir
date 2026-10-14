package ru.efir.core.net

import kotlinx.coroutines.CoroutineScope

/**
 * JVM-реализация [EfirHost]: оборачивает [EfirServer] и прячет детали Ktor.
 */
private class JvmEfirHost(
    private val config: ServerConfig,
    private val onConnection: suspend (EfirConnection) -> Unit,
) : EfirHost {

    private var server: EfirServer? = null

    override fun start() {
        server = EfirServer(config = config, onConnection = onConnection).also { it.start() }
    }

    override fun stop() {
        server?.stop()
        server = null
    }
}

actual fun createEfirHost(
    config: ServerConfig,
    onConnection: suspend (EfirConnection) -> Unit,
): EfirHost = JvmEfirHost(config, onConnection)

actual suspend fun openEfirConnection(
    config: ClientConfig,
    parent: CoroutineScope,
): EfirConnection = ClientConnection.connect(config = config, scope = parent)
