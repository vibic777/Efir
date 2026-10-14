package ru.efir.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.efir.core.protocol.SyncRuleConfig
import ru.efir.core.protocol.SyncTrigger

/**
 * Наблюдатель изменений файловой системы для триггера [SyncTrigger.ON_CHANGE].
 * Реализации (платформенные) публикуют относительные пути изменённых файлов.
 */
interface SyncWatcher {
    /** Поток относительных путей, изменённых в корне правила. */
    fun changes(rule: SyncRuleConfig): Flow<String>
}

/**
 * Создаёт платформенный наблюдатель изменений файловой системы.
 * Desktop — `WatchService`, Android — `FileObserver`.
 */
expect fun createLocalSyncWatcher(): SyncWatcher

/** Планировщик запусков правил синхронизации по триггерам. */
class SyncScheduler(
    private val scope: CoroutineScope,
    /** Обработчик запуска синхронизации правила (обычно [SyncEngine.sync]). */
    private val onRun: suspend (SyncRuleConfig) -> Unit,
    private val watcher: SyncWatcher? = null,
) {

    private var scheduledJob: Job? = null
    private var watchJobs = mutableMapOf<String, Job>()

    private val _fired = MutableSharedFlow<String>(extraBufferCapacity = 32)

    /** События запусков (ruleId) — для UI/логов. */
    val fired: SharedFlow<String> = _fired.asSharedFlow()

    /** Обрабатывает подключение: включает триггеры [SyncTrigger.ON_CONNECT] и расписания. */
    fun onConnected(rules: List<SyncRuleConfig>) {
        start(rules)
        scope.launch {
            for (rule in rules) {
                if (rule.enabled && rule.trigger == SyncTrigger.ON_CONNECT) run(rule)
            }
        }
    }

    /** Запускает периодические и событийные триггеры для набора правил. */
    fun start(rules: List<SyncRuleConfig>) {
        stop()
        scheduledJob = scope.launch {
            while (isActive) {
                val scheduled = rules.filter {
                    it.enabled && it.trigger == SyncTrigger.SCHEDULED && it.scheduleIntervalMinutes > 0
                }
                val minInterval = scheduled.minOfOrNull { it.scheduleIntervalMinutes }
                if (minInterval == null) {
                    // Нет активных расписаний — ждём и перечитываем набор.
                    delay(IDLE_TICK_MS)
                    continue
                }
                delay(minInterval * 60_000L)
                // Прошёл минимальный интервал — запускаем все «созревшие» расписания.
                for (rule in scheduled) {
                    val periodMs = rule.scheduleIntervalMinutes * 60_000L
                    val lastAt = lastRunAt[rule.id]
                    if (lastAt == null || syncNow() - lastAt >= periodMs) run(rule)
                }
            }
        }
        // Подписка на изменения файлов (real-time watcher).
        val w = watcher ?: return
        for (rule in rules) {
            if (rule.enabled && rule.trigger == SyncTrigger.ON_CHANGE) {
                watchJobs[rule.id] = scope.launch {
                    w.changes(rule).collect { run(rule) }
                }
            }
        }
    }

    /** Останавливает все триггеры. */
    fun stop() {
        scheduledJob?.cancel()
        scheduledJob = null
        watchJobs.values.forEach { it.cancel() }
        watchJobs.clear()
    }

    private var lastRunAt = mutableMapOf<String, Long>()

    private suspend fun run(rule: SyncRuleConfig) {
        lastRunAt[rule.id] = syncNow()
        _fired.tryEmit(rule.id)
        runCatching { onRun(rule) }
    }

    private companion object {
        const val IDLE_TICK_MS = 30_000L
    }
}
