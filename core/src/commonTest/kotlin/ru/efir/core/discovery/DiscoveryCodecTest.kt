package ru.efir.core.discovery

import ru.efir.core.protocol.Capability
import ru.efir.core.protocol.DeviceType
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoveryCodecTest {

    @Test
    fun announceRoundTrip() {
        val announce = DiscoveryAnnounce(
            deviceId = "d1",
            name = "Pixel 7",
            type = DeviceType.ANDROID,
            host = "192.168.0.5",
            port = 47812,
            capabilities = setOf(Capability.SCREEN_CAPTURE, Capability.FILE_MANAGER),
            timestamp = 1700000000000L,
        )

        val decoded = DiscoveryCodec.decode(DiscoveryCodec.encode(announce))
        assertEquals(announce, decoded)
    }
}
