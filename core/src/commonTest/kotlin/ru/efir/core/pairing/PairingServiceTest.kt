package ru.efir.core.pairing

import kotlinx.coroutines.test.runTest
import ru.efir.core.protocol.DeviceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingServiceTest {

    private fun service(store: TrustStore = InMemoryTrustStore()) = PairingService(
        trustStore = store,
        fingerprint = "abcd",
        host = "192.168.0.10",
        port = 47812,
    )

    @Test
    fun successfulPairingCreatesTrust() = runTest {
        val store = InMemoryTrustStore()
        val pairing = service(store)
        val session = pairing.begin("android-1", "Pixel", DeviceType.ANDROID)

        val verdict = pairing.verify(
            pairingToken = session.payload.pairingToken,
            accessKey = session.accessKey,
            clientDeviceId = "win-1",
            clientName = "PC",
            clientType = DeviceType.WINDOWS,
        )

        val accepted = assertIs<PairingVerdict.Accepted>(verdict)
        assertEquals("win-1", accepted.trusted.deviceId)
        assertTrue(store.find("win-1") != null)
        assertTrue(pairing.isTrusted("win-1", accepted.trusted.sessionToken))
    }

    @Test
    fun wrongAccessKeyIsRejected() = runTest {
        val pairing = service()
        val session = pairing.begin("android-1", "Pixel", DeviceType.ANDROID)

        val verdict = pairing.verify(
            pairingToken = session.payload.pairingToken,
            accessKey = "000000000000",
            clientDeviceId = "win-1",
            clientName = "PC",
            clientType = DeviceType.WINDOWS,
        )

        assertIs<PairingVerdict.Rejected>(verdict)
    }

    @Test
    fun accessKeyIsCaseAndSeparatorInsensitive() = runTest {
        val pairing = service()
        val session = pairing.begin("android-1", "Pixel", DeviceType.ANDROID)

        // Тот же ключ, но в нижнем регистре и с дефисами/пробелами — должен быть принят.
        val typed = session.accessKey.lowercase().chunked(4).joinToString("- ")
        val verdict = pairing.verify(
            pairingToken = session.payload.pairingToken,
            accessKey = typed,
            clientDeviceId = "win-1",
            clientName = "PC",
            clientType = DeviceType.WINDOWS,
        )

        assertIs<PairingVerdict.Accepted>(verdict)
    }

    @Test
    fun wrongTokenIsRejected() = runTest {
        val pairing = service()
        val session = pairing.begin("android-1", "Pixel", DeviceType.ANDROID)

        val verdict = pairing.verify(
            pairingToken = "deadbeef",
            accessKey = session.accessKey,
            clientDeviceId = "win-1",
            clientName = "PC",
            clientType = DeviceType.WINDOWS,
        )

        assertIs<PairingVerdict.Rejected>(verdict)
    }

    @Test
    fun sessionIsSingleUse() = runTest {
        val pairing = service()
        val session = pairing.begin("android-1", "Pixel", DeviceType.ANDROID)

        pairing.verify(session.payload.pairingToken, session.accessKey, "win-1", "PC", DeviceType.WINDOWS)
        // Повторная попытка с тем же токеном должна быть отклонена (сессия израсходована).
        val second = pairing.verify(session.payload.pairingToken, session.accessKey, "win-2", "PC2", DeviceType.WINDOWS)
        assertIs<PairingVerdict.Rejected>(second)
    }

    @Test
    fun unknownSessionIsRejected() = runTest {
        val pairing = service()
        val verdict = pairing.verify("x", "1", "win-1", "PC", DeviceType.WINDOWS)
        assertIs<PairingVerdict.Rejected>(verdict)
    }

    @Test
    fun tokensAndAccessKeysHaveExpectedShape() {
        assertTrue(SecureTokens.token(16).length == 32)
        assertFalse(SecureTokens.token() == SecureTokens.token())

        val key = SecureTokens.accessKey()
        assertEquals(SecureTokens.ACCESS_KEY_LENGTH, key.length)
        assertTrue(key.all { it in SecureTokens.ACCESS_KEY_ALPHABET }, "ключ: $key")
        assertEquals(key, key.uppercase())
        assertTrue(SecureTokens.isAccessKeyValid(key))
        assertFalse(SecureTokens.isAccessKeyValid(key.drop(1)))
    }

    @Test
    fun accessKeyFormatsInGroupsOfFour() {
        assertEquals("A1B2-C3D4-E5F6", SecureTokens.formatAccessKey("a1b2c3d4e5f6"))
        assertEquals("A1B2-C3D4-E5F6", SecureTokens.formatAccessKey("A1B2-C3D4-E5F6"))
        assertEquals("", SecureTokens.formatAccessKey(""))
    }
}
