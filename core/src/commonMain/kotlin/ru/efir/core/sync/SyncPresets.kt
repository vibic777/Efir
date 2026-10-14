package ru.efir.core.sync

import ru.efir.core.protocol.SyncConflictStrategy
import ru.efir.core.protocol.SyncDirection
import ru.efir.core.protocol.SyncRuleConfig

/**
 * Готовые пресеты стратегий синхронизации: подставляют направление, стратегию
 * конфликтов, распространение удалений и бэкап одним действием. Пресет меняет
 * только поведенческие поля правила — имя, идентификатор и пути сохраняются.
 */
enum class SyncPreset(val label: String, val description: String) {

    /** Двусторонняя синхронизация с приоритетом новейшей версии, без удалений. */
    SAFE_MERGE(
        label = "Безопасное объединение",
        description = "Двусторонняя, побеждает новейшая версия, удаления не распространяются",
    ),

    /** Одностороннее зеркало ПК → телефон: телефон приводится к состоянию ПК. */
    MIRROR_TO_REMOTE(
        label = "Зеркало ПК → телефон",
        description = "Односторонняя, телефон повторяет ПК (с удалениями)",
    ),

    /** Одностороннее зеркало телефон → ПК: ПК приводится к состоянию телефона. */
    MIRROR_TO_LOCAL(
        label = "Зеркало телефон → ПК",
        description = "Односторонняя, ПК повторяет телефон (с удалениями)",
    ),

    /** Только добавление новых файлов, существующие не перезаписываются. */
    ADD_ONLY(
        label = "Только добавление",
        description = "Двусторонняя, существующие файлы не перезаписываются",
    ),

    /** Двусторонняя, но конфликты разрешаются вручную. */
    ASK_ON_CONFLICT(
        label = "Спрашивать о конфликтах",
        description = "Двусторонняя, конфликты разрешаются вручную",
    ),
}

/** Применяет пресет к правилу [config], сохраняя id/имя/пути/триггер. */
fun SyncPreset.applyTo(config: SyncRuleConfig): SyncRuleConfig = config.copy(
    direction = direction,
    conflictStrategy = conflictStrategy,
    deletePropagation = deletePropagation,
    backupBeforeOverwrite = backupBeforeOverwrite,
)

/** Направление синхронизации, задаваемое пресетом. */
val SyncPreset.direction: SyncDirection
    get() = when (this) {
        SyncPreset.SAFE_MERGE,
        SyncPreset.ADD_ONLY,
        SyncPreset.ASK_ON_CONFLICT -> SyncDirection.BIDIRECTIONAL
        SyncPreset.MIRROR_TO_REMOTE -> SyncDirection.LOCAL_TO_REMOTE
        SyncPreset.MIRROR_TO_LOCAL -> SyncDirection.REMOTE_TO_LOCAL
    }

/** Стратегия конфликтов, задаваемая пресетом. */
val SyncPreset.conflictStrategy: SyncConflictStrategy
    get() = when (this) {
        SyncPreset.SAFE_MERGE -> SyncConflictStrategy.PREFER_NEWEST
        SyncPreset.MIRROR_TO_REMOTE,
        SyncPreset.MIRROR_TO_LOCAL -> SyncConflictStrategy.OVERWRITE
        SyncPreset.ADD_ONLY -> SyncConflictStrategy.APPEND
        SyncPreset.ASK_ON_CONFLICT -> SyncConflictStrategy.ASK
    }

/** Применяет ли пресет удаления, сделанные на другой стороне. */
val SyncPreset.deletePropagation: Boolean
    get() = this == SyncPreset.MIRROR_TO_REMOTE || this == SyncPreset.MIRROR_TO_LOCAL

/** Делает ли пресет резервную копию перед перезаписью. */
val SyncPreset.backupBeforeOverwrite: Boolean
    get() = this != SyncPreset.MIRROR_TO_REMOTE

/**
 * Понятная расшифровка направления с учётом сторон (по умолчанию «ПК» и
 * «телефон»). Используется как подсказка рядом со стрелкой направления.
 */
fun directionHint(
    direction: SyncDirection,
    localLabel: String = "ПК",
    remoteLabel: String = "телефон",
): String = when (direction) {
    SyncDirection.BIDIRECTIONAL -> "Изменения синхронизируются в обе стороны ($localLabel ↔ $remoteLabel)"
    SyncDirection.LOCAL_TO_REMOTE -> "Изменения переносятся только с $localLabel на $remoteLabel"
    SyncDirection.REMOTE_TO_LOCAL -> "Изменения переносятся только с $remoteLabel на $localLabel"
}
