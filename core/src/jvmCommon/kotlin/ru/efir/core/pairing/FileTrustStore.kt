package ru.efir.core.pairing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import ru.efir.core.protocol.ProtocolCodec
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Персистентное хранилище доверенных устройств на JVM (Android и Desktop).
 *
 * Данные сохраняются в JSON-файл внутри каталога [directory]. Запись атомарная
 * (через временный файл и последующий `move`), чтобы исключить порчу данных при
 * аварийном завершении. Каталог создаётся автоматически при первой записи.
 *
 * Примечание по безопасности: содержимое (включая `sessionToken`) защищается
 * средствами файловой системы. Для Android каталог берётся из `context.filesDir`,
 * для Desktop — из домашнего каталога пользователя. Это совместимо с будущим
 * переходом на Android Keystore / Windows DPAPI.
 */
class FileTrustStore(
    private val directory: File,
    private val fileName: String = "trusted_devices.json",
) : TrustStore {

    private val mutex = Mutex()
    private val file: File get() = File(directory, fileName)
    private var cached: MutableMap<String, TrustedDevice>? = null

    override suspend fun all(): List<TrustedDevice> = mutex.withLock {
        load().values.toList()
    }

    override suspend fun find(deviceId: String): TrustedDevice? = mutex.withLock {
        load()[deviceId]
    }

    override suspend fun save(device: TrustedDevice) {
        mutex.withLock {
            val map = load().toMutableMap()
            map[device.deviceId] = device
            persist(map)
        }
    }

    override suspend fun remove(deviceId: String) {
        mutex.withLock {
            val map = load().toMutableMap()
            if (map.remove(deviceId) != null) persist(map)
        }
    }

    /** Ленивая загрузка из файла с кешированием. */
    private suspend fun load(): Map<String, TrustedDevice> {
        cached?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists()) return@runCatching emptyMap<String, TrustedDevice>()
                val text = file.readText(Charsets.UTF_8)
                if (text.isBlank()) return@runCatching emptyMap<String, TrustedDevice>()
                decodeFile(text)
            }.getOrElse { emptyMap() }
        }
        cached = loaded.toMutableMap()
        return loaded
    }

    private fun decodeFile(text: String): Map<String, TrustedDevice> {
        val list = ProtocolCodec.json.decodeFromString<List<TrustedDevice>>(text)
        return list.associateBy { it.deviceId }
    }

    private suspend fun persist(map: Map<String, TrustedDevice>) {
        cached = map.toMutableMap()
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val text = ProtocolCodec.json.encodeToString(
                ListSerializer(TrustedDevice.serializer()),
                map.values.toList(),
            )
            val tmp = File(directory, "$fileName.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            runCatching {
                Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                // ATOMIC_MOVE может быть не поддержан файловой системой — обычная замена.
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
