package ru.efir.core.net

import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Порт соединения: автоматический выбор обходит занятые порты, занятость порта видна. */
class NetworkSettingsTest {

    @Test
    fun autoTakesTheStandardPortOrTheNextFreeOne() {
        assertEquals(EfirNetwork.DEFAULT_PORT, NetworkSettings.autoPort { true })
        assertEquals(EfirNetwork.DEFAULT_PORT + 2, NetworkSettings.autoPort { it >= EfirNetwork.DEFAULT_PORT + 2 })
        // Порт поиска устройств автоматический выбор не берёт.
        assertEquals(EfirNetwork.DISCOVERY_PORT + 1, NetworkSettings.autoPort { it > EfirNetwork.DEFAULT_PORT })
        // Всё занято — остаётся стандартный: ошибка будет видна в журнале.
        assertEquals(EfirNetwork.DEFAULT_PORT, NetworkSettings.autoPort { false })
    }

    @Test
    fun onlySensiblePortsAreAccepted() {
        assertTrue(NetworkSettings.valid(50000))
        assertFalse(NetworkSettings.valid(80))
        assertFalse(NetworkSettings.valid(70000))
        assertFalse(NetworkSettings.valid(EfirNetwork.DISCOVERY_PORT))
        assertFalse(NetworkSettings.valid(null))
    }

    @Test
    fun busyPortIsSeen() {
        ServerSocket(0).use { taken ->
            assertFalse(isPortFree(taken.localPort))
        }
        val port = ServerSocket(0).use { it.localPort }
        assertTrue(isPortFree(port))
    }
}