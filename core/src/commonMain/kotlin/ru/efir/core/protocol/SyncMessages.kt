package ru.efir.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Направление синхронизации правила. */
@Serializable
enum class SyncDirection {
    BIDIRECTIONAL,
    LOCAL_TO_REMOTE,
    REMOTE_TO_LOCAL,
}

/** Стратегия разрешения конфликтов. */
@Serializable
enum class SyncConflictStrategy {
    /** Объединять содержимое обеих сторон. */
    MERGE,
    /** Перезаписывать выбранной стороной/более новым. */
    OVERWRITE,
    /** Только добавлять новое, не удаляя. */
    APPEND,
    PREFER_LOCAL,
    PREFER_REMOTE,
    PREFER_NEWEST,
    /** Автопереименование при коллизиях: name (1), name (2), … */
    RENAME,
    /** Спрашивать пользователя. */
    ASK,
}

/** Триггер запуска синхронизации. */
@Serializable
enum class SyncTrigger {
    MANUAL,
    SCHEDULED,
    ON_CONNECT,
    ON_CHANGE,
}

/** Сторона синхронизации. */
@Serializable
enum class SyncSide {
    LOCAL,
    REMOTE,
}

/** Тип действия в плане синхронизации. */
@Serializable
enum class SyncActionType {
    ADD,
    UPDATE,
    DELETE,
    RENAME,
    SKIP,
}

/**
 * Конфигурация правила синхронизации.
 *
 * @param scheduleIntervalMinutes интервал для триггера [SyncTrigger.SCHEDULED]
 *   (минуты; `0` — только ручной запуск/не задан).
 * @param minFileSize нижняя граница размера файла для включения (`null` — без ограничения).
 * @param maxFileSize верхняя граница размера файла (`null` — без ограничения).
 */
@Serializable
data class SyncRuleConfig(
    val id: String,
    val name: String,
    val localPath: String,
    val remotePath: String,
    val direction: SyncDirection = SyncDirection.BIDIRECTIONAL,
    val trigger: SyncTrigger = SyncTrigger.MANUAL,
    val scheduleIntervalMinutes: Int = 0,
    val conflictStrategy: SyncConflictStrategy = SyncConflictStrategy.PREFER_NEWEST,
    /** Применять ли удаления, сделанные на другой стороне. */
    val deletePropagation: Boolean = false,
    /** Делать резервную копию перед перезаписью. */
    val backupBeforeOverwrite: Boolean = true,
    val includePatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList(),
    val minFileSize: Long? = null,
    val maxFileSize: Long? = null,
    val enabled: Boolean = true,
)

/** Одно действие плана синхронизации. */
@Serializable
data class SyncAction(
    val type: SyncActionType,
    /** Сторона-источник действия. */
    val side: SyncSide,
    val relativePath: String,
    /** Признак каталога (для создания/удаления пустых деректорий). */
    val directory: Boolean = false,
    val newName: String? = null,
    val size: Long = 0,
    val sha256: String? = null,
)

/** Описание конфликта версий файла. */
@Serializable
data class SyncConflict(
    val relativePath: String,
    val localSize: Long,
    val remoteSize: Long,
    val localModifiedAt: Long,
    val remoteModifiedAt: Long,
)

/** Ручной выбор разрешения конфликта пользователем (стратегия `ASK`). */
@Serializable
enum class SyncResolutionChoice {
    /** Оставить версию компьютера (локальную). */
    KEEP_LOCAL,
    /** Оставить версию телефона (удалённую). */
    KEEP_REMOTE,
    /** Оставить более новую версию (по времени изменения). */
    KEEP_NEWEST,
    /** Сохранить оба файла, переименовав входящий. */
    KEEP_BOTH,
    /** Пропустить файл (ничего не делать). */
    SKIP,
}

/** Один выбор пользователя по конкретному конфликту. */
@Serializable
data class SyncResolution(
    val relativePath: String,
    val choice: SyncResolutionChoice,
)

/** Запрос построения плана синхронизации (dry-run). */
@Serializable
@SerialName("syncPlanRequest")
data class SyncPlanRequest(
    val rule: SyncRuleConfig,
) : RpcRequest

/** Результат планирования: действия и конфликты. */
@Serializable
@SerialName("syncPlanResult")
data class SyncPlanResult(
    val ruleId: String,
    val actions: List<SyncAction>,
    val conflicts: List<SyncConflict> = emptyList(),
) : RpcResponse

/** Применение плана синхронизации. */
@Serializable
@SerialName("syncApplyRequest")
data class SyncApplyRequest(
    val ruleId: String,
    val actions: List<SyncAction>,
) : RpcRequest

/** Текущий статус синхронизации. */
@Serializable
@SerialName("syncStatus")
data class SyncStatus(
    val ruleId: String,
    val running: Boolean,
    val currentPath: String? = null,
    val processed: Int = 0,
    val total: Int = 0,
    val message: String? = null,
) : Event

/** Обнаружены конфликты, требующие решения. */
@Serializable
@SerialName("syncConflictEvent")
data class SyncConflictEvent(
    val ruleId: String,
    val conflicts: List<SyncConflict>,
) : Event
