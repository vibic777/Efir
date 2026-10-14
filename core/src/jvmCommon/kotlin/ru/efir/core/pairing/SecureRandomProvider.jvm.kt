package ru.efir.core.pairing

import java.security.SecureRandom

actual object SecureRandomProvider {
    private val random = SecureRandom()

    actual fun bytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    actual fun nextInt(bound: Int): Int = random.nextInt(bound)
}
