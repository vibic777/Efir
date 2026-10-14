package ru.efir.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import ru.efir.core.protocol.ProtocolCodec
import ru.efir.core.protocol.SyncRuleConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Персистентное хранилище состояния синхронизации на JVM (Android и Desktop).
 *
 * Правила, базовые линии и журналы сохраняются в JSON-файлы каталога
 * [directory]. Запись атомарная (временный файл + `move`), кеш в памяти
 * заполняется лениво при первом обращении.
 */
class FileSyncStateStore(
    private val directory: File,
    private val rulesFile: String = "sync_rules.json",
    private val baselinesFile: String = "sync_baselines.json",
    private val journalsFile: String = "sync_journals.json",
) : SyncStateStore {

    private val mutex = Mutex()

    private var rulesCache: MutableMap<String, SyncRuleConfig>? = null
    private var baselinesCache: MutableMap<String, SyncBaseline>? = null
    private var journalsCache: MutableMap<String, MutableList<SyncJournalEntry>>? = null

    // --- Правила ---

    override suspend fun rules(): List<SyncRuleConfig> = mutex.withLock {
        loadRules().values.sortedBy { it.name.lowercase() }
    }

    override suspend fun rule(id: String): SyncRuleConfig? = mutex.withLock { loadRules()[id] }

    override suspend fun saveRule(rule: SyncRuleConfig) {
        mutex.withLock {
            val map = loadRules().toMutableMap()
            map[rule.id] = rule
            persistRules(map)
        }
    }

    override suspend fun removeRule(id: String) {
        mutex.withLock {
            val map = loadRules().toMutableMap()
            if (map.remove(id) != null) persistRules(map)
            val baselines = loadBaselines().toMutableMap()
            val journals = loadJournals().toMutableMap()
            val removedBaseline = baselines.remove(id) != null
            val removedJournal = journals.remove(id) != null
            if (removedBaseline) persistBaselines(baselines)
            if (removedJournal) persistJournals(journals)
        }
    }

    // --- Базовые линии ---

    override suspend fun baseline(ruleId: String): SyncBaseline? = mutex.withLock { loadBaselines()[ruleId] }

    override suspend fun saveBaseline(baseline: SyncBaseline) {
        mutex.withLock {
            val map = loadBaselines().toMutableMap()
            map[baseline.ruleId] = baseline
            persistBaselines(map)
        }
    }

    // --- Журнал ---

    override suspend fun journal(ruleId: String): SyncJournal? = mutex.withLock {
        loadJournals()[ruleId]?.let { SyncJournal(ruleId, it.toList()) }
    }

    override suspend fun appendJournal(entry: SyncJournalEntry) {
        mutex.withLock {
            val map = loadJournals().toMutableMap()
            val list = map.getOrPut(entry.ruleId) { mutableListOf() }
            list.add(entry)
            if (list.size > MAX_SYNC_JOURNAL_ENTRIES) {
                val excess = list.size - MAX_SYNC_JOURNAL_ENTRIES
                repeat(excess) { list.removeAt(0) }
            }
            persistJournals(map)
        }
    }

    override suspend fun clearJournal(ruleId: String) {
        mutex.withLock {
            val map = loadJournals().toMutableMap()
            if (map.remove(ruleId) != null) persistJournals(map)
        }
    }

    // --- Загрузка/сохранение ----------------------------------------------------

    private suspend fun loadRules(): Map<String, SyncRuleConfig> {
        rulesCache?.let { return it }
        val loaded = read(File(directory, rulesFile)) { text ->
            ProtocolCodec.json.decodeFromString<List<SyncRuleConfig>>(text).associateBy { it.id }
        }
        rulesCache = loaded.toMutableMap()
        return loaded
    }

    private suspend fun loadBaselines(): Map<String, SyncBaseline> {
        baselinesCache?.let { return it }
        val loaded = read(File(directory, baselinesFile)) { text ->
            ProtocolCodec.json.decodeFromString<List<SyncBaseline>>(text).associateBy { it.ruleId }
        }
        baselinesCache = loaded.toMutableMap()
        return loaded
    }

    private suspend fun loadJournals(): Map<String, MutableList<SyncJournalEntry>> {
        journalsCache?.let { return it }
        val loaded = read(File(directory, journalsFile)) { text ->
            ProtocolCodec.json.decodeFromString<List<SyncJournal>>(text)
                .associate { it.ruleId to it.entries.toMutableList() }
        }
        journalsCache = loaded.toMutableMap()
        return loaded
    }

    private suspend fun persistRules(map: Map<String, SyncRuleConfig>) {
        rulesCache = map.toMutableMap()
        write(File(directory, rulesFile)) {
            ProtocolCodec.json.encodeToString(ListSerializer(SyncRuleConfig.serializer()), map.values.toList())
        }
    }

    private suspend fun persistBaselines(map: Map<String, SyncBaseline>) {
        baselinesCache = map.toMutableMap()
        write(File(directory, baselinesFile)) {
            ProtocolCodec.json.encodeToString(ListSerializer(SyncBaseline.serializer()), map.values.toList())
        }
    }

    private suspend fun persistJournals(map: Map<String, MutableList<SyncJournalEntry>>) {
        journalsCache = map.toMutableMap()
        write(File(directory, journalsFile)) {
            val journals = map.map { (ruleId, entries) -> SyncJournal(ruleId, entries.toList()) }
            ProtocolCodec.json.encodeToString(ListSerializer(SyncJournal.serializer()), journals)
        }
    }

    private suspend fun <T> read(file: File, decode: (String) -> Map<String, T>): Map<String, T> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists()) return@runCatching emptyMap()
                val text = file.readText(Charsets.UTF_8)
                if (text.isBlank()) return@runCatching emptyMap()
                decode(text)
            }.getOrElse { emptyMap() }
        }

    private suspend fun write(file: File, encode: () -> String) {
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val text = encode()
            val tmp = File(directory, "${file.name}.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            runCatching {
                Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
