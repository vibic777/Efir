package ru.efir.core.pairing

import kotlinx.coroutines.runBlocking
import ru.efir.core.protocol.DeviceType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileTrustStoreTest {

    private fun tempDir(): File =
        File.createTempFile("efir-trust", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }

    private fun device(id: String, token: String = "tok-$id") = TrustedDevice(
        deviceId = id,
        name = "Устройство $id",
        type = DeviceType.WINDOWS,
        fingerprint = "fp-$id",
        sessionToken = token,
        pairedAt = 1_000L,
        lastSeenAt = 2_000L,
    )

    @Test
    fun savedDeviceIsPersistedAndReloaded() = runBlocking {
        val dir = tempDir()
        val store = FileTrustStore(dir)
        store.save(device("win-1"))

        // Новый экземпляр читает данные из файла (проверяем реальную персистентность).
        val reloaded = FileTrustStore(dir)
        val found = reloaded.find("win-1")
        assertNotNull(found, "Устройство должно сохраниться на диск")
        assertEquals("Устройство win-1", found.name)
        assertEquals(DeviceType.WINDOWS, found.type)
        assertEquals(1, reloaded.all().size)
    }

    @Test
    fun removeDeletesFromDisk() = runBlocking {
        val dir = tempDir()
        val store = FileTrustStore(dir)
        store.save(device("win-1"))
        store.save(device("win-2"))
        assertEquals(2, store.all().size)

        store.remove("win-1")
        assertNull(store.find("win-1"))
        assertEquals(1, FileTrustStore(dir).all().size)
    }

    @Test
    fun updateExistingDeviceOverwrites() = runBlocking {
        val dir = tempDir()
        val store = FileTrustStore(dir)
        store.save(device("win-1", token = "old"))
        store.save(device("win-1", token = "new"))

        val reloaded = FileTrustStore(dir)
        assertEquals("new", reloaded.find("win-1")?.sessionToken)
        assertEquals(1, reloaded.all().size, "Повторная запись не должна дублировать устройство")
    }

    @Test
    fun emptyStoreReturnsEmptyList() = runBlocking {
        val store = FileTrustStore(tempDir())
        assertTrue(store.all().isEmpty())
        assertNull(store.find("missing"))
    }
}
