package ru.efir.core.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import ru.efir.core.protocol.SyncRuleConfig

/**
 * Хранилище состояния синхронизации: правила, базовые линии (последнее
 * синхронизированное состояние) и журналы операций.
 *
 * Реализации: [InMemorySyncStateStore] (для тестов) и платформенная
 * file-backed (см. `FileSyncStateStore` на JVM).
 */
interface SyncStateStore {

    // --- Правила ---
    suspend fun rules(): List<SyncRuleConfig>
    suspend fun rule(id: String): SyncRuleConfig?
    suspend fun saveRule(rule: SyncRuleConfig)
    suspend fun removeRule(id: String)

    // --- Базовые линии ---
    suspend fun baseline(ruleId: String): SyncBaseline?
    suspend fun saveBaseline(baseline: SyncBaseline)

    // --- Журнал ---
    suspend fun journal(ruleId: String): SyncJournal?
    suspend fun appendJournal(entry: SyncJournalEntry)
    suspend fun clearJournal(ruleId: String)
}

/** Простейшее хранилище в памяти (состояние живёт только в рамках процесса). */
class InMemorySyncStateStore : SyncStateStore {

    private val mutex = Mutex()
    private val rules = mutableMapOf<String, SyncRuleConfig>()
    private val baselines = mutableMapOf<String, SyncBaseline>()
    private val journals = mutableMapOf<String, MutableList<SyncJournalEntry>>()

    override suspend fun rules(): List<SyncRuleConfig> = mutex.withLock {
        rules.values.sortedBy { it.name.lowercase() }
    }

    override suspend fun rule(id: String): SyncRuleConfig? = mutex.withLock { rules[id] }

    override suspend fun saveRule(rule: SyncRuleConfig) {
        mutex.withLock { rules[rule.id] = rule }
    }

    override suspend fun removeRule(id: String) {
        mutex.withLock {
            rules.remove(id)
            baselines.remove(id)
            journals.remove(id)
        }
    }

    override suspend fun baseline(ruleId: String): SyncBaseline? = mutex.withLock { baselines[ruleId] }

    override suspend fun saveBaseline(baseline: SyncBaseline) {
        mutex.withLock { baselines[baseline.ruleId] = baseline }
    }

    override suspend fun journal(ruleId: String): SyncJournal? = mutex.withLock {
        journals[ruleId]?.let { SyncJournal(ruleId, it.toList()) }
    }

    override suspend fun appendJournal(entry: SyncJournalEntry) {
        mutex.withLock {
            val list = journals.getOrPut(entry.ruleId) { mutableListOf() }
            list.add(entry)
            // Ограничиваем журнал, чтобы не расти безгранично.
            if (list.size > MAX_JOURNAL_ENTRIES) {
                val excess = list.size - MAX_JOURNAL_ENTRIES
                repeat(excess) { list.removeAt(0) }
            }
        }
    }

    override suspend fun clearJournal(ruleId: String) {
        mutex.withLock { journals.remove(ruleId) }
    }

    private companion object {
        const val MAX_JOURNAL_ENTRIES = 500
    }
}

/** Текущее время в epoch millis. */
internal fun syncNow(): Long = Clock.System.now().toEpochMilliseconds()

/** Ограничение числа записей журнала (общее для реализаций). */
internal const val MAX_SYNC_JOURNAL_ENTRIES: Int = 500
