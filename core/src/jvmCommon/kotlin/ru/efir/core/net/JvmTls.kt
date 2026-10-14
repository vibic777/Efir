package ru.efir.core.net

import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Утилиты TLS для JVM-платформ (Android и Desktop).
 *
 * Политика доверия Efir строится на «TOFU + пиннинг отпечатка»: при первом
 * подключении доверяем самоподписанному сертификату, запоминая его SHA-256,
 * при последующих — сравниваем отпечаток.
 */
object JvmTls {

    /** Доверяет любому сертификату (используется при первом сопряжении, TOFU). */
    fun trustAllManager(): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /**
     * Доверяет только сертификату с заданным отпечатком SHA-256 (hex, без разделителей).
     * Если сертификат отсутствует или отпечаток не совпадает — соединение отклоняется.
     */
    fun pinningManager(expectedFingerprint: String): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = verify(chain)
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = verify(chain)
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

        private fun verify(chain: Array<out X509Certificate>?) {
            val leaf = chain?.firstOrNull()
                ?: throw java.security.cert.CertificateException("Пустая цепочка сертификатов")
            val actual = fingerprintOf(leaf)
            if (!actual.equals(expectedFingerprint, ignoreCase = true)) {
                throw java.security.cert.CertificateException(
                    "Отпечаток сертификата не совпадает: ожидался $expectedFingerprint, получен $actual",
                )
            }
        }
    }

    /** Вычисляет отпечаток сертификата SHA-256 в hex (нижний регистр). */
    fun fingerprintOf(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString("") { byte ->
            val v = byte.toInt() and 0xFF
            val hex = v.toString(16)
            if (hex.length == 1) "0$hex" else hex
        }
    }
}
