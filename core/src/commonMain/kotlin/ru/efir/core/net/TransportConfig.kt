package ru.efir.core.net

/** Сетевые константы протокола Efir. */
object EfirNetwork {
    /** Порт, на котором Android-приложение слушает control-подключения. */
    const val DEFAULT_PORT: Int = 47812

    /** Путь WebSocket-endpoint. */
    const val DEFAULT_PATH: String = "/efir"

    /** Имя mDNS-сервиса. */
    const val SERVICE_TYPE: String = "_efir._tcp"

    /** Порт для UDP-бродкаста обнаружения. */
    const val DISCOVERY_PORT: Int = 47813
}

/**
 * Настройки TLS. Если [enabled] = false, соединение устанавливается без шифрования
 * (используется только для отладки в доверенной сети).
 */
data class TlsConfig(
    val enabled: Boolean = true,
    /** Отпечаток сертификата удалённой стороны (SHA-256, hex) для проверки доверия. */
    val expectedFingerprint: String? = null,
    /** Разрешать самоподписанные сертификаты при первом подключении (TOFU). */
    val allowSelfSigned: Boolean = true,
)

/** Конфигурация клиента. */
data class ClientConfig(
    val host: String,
    val port: Int = EfirNetwork.DEFAULT_PORT,
    val path: String = EfirNetwork.DEFAULT_PATH,
    val tls: TlsConfig = TlsConfig(),
)

/** Конфигурация сервера. */
data class ServerConfig(
    val bindAddress: String = "0.0.0.0",
    val port: Int = EfirNetwork.DEFAULT_PORT,
    val path: String = EfirNetwork.DEFAULT_PATH,
    val tls: TlsConfig = TlsConfig(),
)
