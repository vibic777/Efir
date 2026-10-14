package ru.efir.core.sync

import ru.efir.core.protocol.SyncConflictStrategy
import ru.efir.core.protocol.SyncDirection
import ru.efir.core.protocol.SyncRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Проверяет пресеты стратегий и подсказки направления (без платформенных зависимостей). */
class SyncPresetsTest {

    private fun rule() = SyncRuleConfig(
        id = "r1",
        name = "Мои документы",
        localPath = "C:/Docs",
        remotePath = "/sdcard/Docs",
        direction = SyncDirection.BIDIRECTIONAL,
        trigger = ru.efir.core.protocol.SyncTrigger.MANUAL,
        conflictStrategy = SyncConflictStrategy.ASK,
        deletePropagation = true,
        backupBeforeOverwrite = false,
        includePatterns = listOf("*.txt"),
    )

    @Test
    fun mirrorToRemoteSetsOneWayAndOverwrite() {
        val updated = SyncPreset.MIRROR_TO_REMOTE.applyTo(rule())
        assertEquals(SyncDirection.LOCAL_TO_REMOTE, updated.direction)
        assertEquals(SyncConflictStrategy.OVERWRITE, updated.conflictStrategy)
        assertTrue(updated.deletePropagation)
    }

    @Test
    fun safeMergeKeepsBothSidesAndNewest() {
        val updated = SyncPreset.SAFE_MERGE.applyTo(rule())
        assertEquals(SyncDirection.BIDIRECTIONAL, updated.direction)
        assertEquals(SyncConflictStrategy.PREFER_NEWEST, updated.conflictStrategy)
        assertFalse(updated.deletePropagation)
        assertTrue(updated.backupBeforeOverwrite)
    }

    @Test
    fun addOnlyNeverOverwrites() {
        val updated = SyncPreset.ADD_ONLY.applyTo(rule())
        assertEquals(SyncConflictStrategy.APPEND, updated.conflictStrategy)
        assertFalse(updated.deletePropagation)
    }

    @Test
    fun presetPreservesIdentityAndPaths() {
        val original = rule()
        val updated = SyncPreset.MIRROR_TO_LOCAL.applyTo(original)
        assertEquals(original.id, updated.id)
        assertEquals(original.name, updated.name)
        assertEquals(original.localPath, updated.localPath)
        assertEquals(original.remotePath, updated.remotePath)
        assertEquals(original.includePatterns, updated.includePatterns)
        assertEquals(SyncDirection.REMOTE_TO_LOCAL, updated.direction)
    }

    @Test
    fun askPresetRequestsManualResolution() {
        val updated = SyncPreset.ASK_ON_CONFLICT.applyTo(rule())
        assertEquals(SyncConflictStrategy.ASK, updated.conflictStrategy)
    }

    @Test
    fun directionHintMentionsSides() {
        assertTrue(directionHint(SyncDirection.BIDIRECTIONAL).contains("↔"))
        assertTrue(directionHint(SyncDirection.LOCAL_TO_REMOTE).contains("ПК"))
        assertTrue(directionHint(SyncDirection.REMOTE_TO_LOCAL).contains("телефон"))
    }
}
