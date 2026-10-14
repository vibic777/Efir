package ru.efir.core.sync

import ru.efir.core.protocol.SyncActionType
import ru.efir.core.protocol.SyncConflictStrategy
import ru.efir.core.protocol.SyncDirection
import ru.efir.core.protocol.SyncResolution
import ru.efir.core.protocol.SyncResolutionChoice
import ru.efir.core.protocol.SyncRuleConfig
import ru.efir.core.protocol.SyncSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Проверяет differ + planner: добавление/удаление/конфликты и стратегии. */
class SyncPlannerTest {

    private val differ = SyncDiffer()
    private val planner = SyncPlanner()

    private fun node(path: String, size: Long = 10, mtime: Long = 100, hash: String? = null) =
        SyncFileNode(relativePath = path, directory = false, size = size, modifiedAt = mtime, sha256 = hash)

    private fun snapshot(side: SyncSide, vararg nodes: SyncFileNode) = SyncSnapshot(side, nodes.toList())

    private fun rule(
        direction: SyncDirection = SyncDirection.BIDIRECTIONAL,
        strategy: SyncConflictStrategy = SyncConflictStrategy.PREFER_NEWEST,
        deletePropagation: Boolean = false,
    ) = SyncRuleConfig(
        id = "r",
        name = "r",
        localPath = "/local",
        remotePath = "/remote",
        direction = direction,
        conflictStrategy = strategy,
        deletePropagation = deletePropagation,
    )

    @Test
    fun newLocalFileIsUploaded() {
        val local = snapshot(SyncSide.LOCAL, node("a.txt"))
        val remote = snapshot(SyncSide.REMOTE)
        val plan = planner.plan(rule(), local, remote, differ.diff(local, remote, null))
        assertEquals(1, plan.actions.size)
        assertEquals(SyncActionType.ADD, plan.actions[0].type)
        assertEquals(SyncSide.LOCAL, plan.actions[0].side)
    }

    @Test
    fun newRemoteFileIsDownloaded() {
        val local = snapshot(SyncSide.LOCAL)
        val remote = snapshot(SyncSide.REMOTE, node("b.txt"))
        val plan = planner.plan(rule(), local, remote, differ.diff(local, remote, null))
        assertEquals(1, plan.actions.size)
        assertEquals(SyncSide.REMOTE, plan.actions[0].side)
    }

    @Test
    fun oneWayDirectionSkipsReverse() {
        val local = snapshot(SyncSide.LOCAL)
        val remote = snapshot(SyncSide.REMOTE, node("b.txt"))
        val plan = planner.plan(
            rule(direction = SyncDirection.LOCAL_TO_REMOTE),
            local,
            remote,
            differ.diff(local, remote, null),
        )
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun removedLocalFileDeletesRemoteWhenPropagating() {
        // База: файл был; локально удалён, удалённо ещё есть.
        val baseline = SyncBaseline(
            "r",
            listOf(SyncBaselineEntry("c.txt", local = node("c.txt"), remote = node("c.txt"))),
        )
        val local = snapshot(SyncSide.LOCAL)
        val remote = snapshot(SyncSide.REMOTE, node("c.txt"))
        val plan = planner.plan(
            rule(deletePropagation = true),
            local,
            remote,
            differ.diff(local, remote, baseline),
        )
        assertEquals(1, plan.actions.size)
        assertEquals(SyncActionType.DELETE, plan.actions[0].type)
        assertEquals(SyncSide.REMOTE, plan.actions[0].side)
    }

    @Test
    fun noDeleteActionWhenPropagationDisabled() {
        val baseline = SyncBaseline(
            "r",
            listOf(SyncBaselineEntry("c.txt", local = node("c.txt"), remote = node("c.txt"))),
        )
        val local = snapshot(SyncSide.LOCAL)
        val remote = snapshot(SyncSide.REMOTE, node("c.txt"))
        val plan = planner.plan(rule(), local, remote, differ.diff(local, remote, baseline))
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun preferNewestPicksNewerSide() {
        val baseline = SyncBaseline(
            "r",
            listOf(
                SyncBaselineEntry(
                    "d.txt",
                    local = node("d.txt", size = 10, mtime = 100),
                    remote = node("d.txt", size = 10, mtime = 100),
                ),
            ),
        )
        val local = snapshot(SyncSide.LOCAL, node("d.txt", size = 20, mtime = 200))
        val remote = snapshot(SyncSide.REMOTE, node("d.txt", size = 30, mtime = 150))
        val plan = planner.plan(
            rule(strategy = SyncConflictStrategy.PREFER_NEWEST),
            local,
            remote,
            differ.diff(local, remote, baseline),
        )
        assertEquals(1, plan.actions.size)
        assertEquals(SyncSide.LOCAL, plan.actions[0].side)
    }

    @Test
    fun askProducesConflict() {
        val baseline = SyncBaseline(
            "r",
            listOf(
                SyncBaselineEntry(
                    "e.txt",
                    local = node("e.txt", mtime = 100),
                    remote = node("e.txt", mtime = 100),
                ),
            ),
        )
        val local = snapshot(SyncSide.LOCAL, node("e.txt", size = 20, mtime = 200))
        val remote = snapshot(SyncSide.REMOTE, node("e.txt", size = 30, mtime = 150))
        val plan = planner.plan(
            rule(strategy = SyncConflictStrategy.ASK),
            local,
            remote,
            differ.diff(local, remote, baseline),
        )
        assertEquals(1, plan.conflicts.size)
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun manualResolutionKeepLocalWins() {
        val baseline = SyncBaseline(
            "r",
            listOf(
                SyncBaselineEntry(
                    "g.txt",
                    local = node("g.txt", mtime = 100),
                    remote = node("g.txt", mtime = 100),
                ),
            ),
        )
        val local = snapshot(SyncSide.LOCAL, node("g.txt", size = 20, mtime = 200))
        val remote = snapshot(SyncSide.REMOTE, node("g.txt", size = 30, mtime = 150))
        val diffs = differ.diff(local, remote, baseline)
        val plan = planner.resolve(
            rule(strategy = SyncConflictStrategy.ASK),
            local,
            remote,
            diffs,
            listOf(SyncResolution("g.txt", SyncResolutionChoice.KEEP_LOCAL)),
        )
        assertEquals(1, plan.actions.size)
        assertEquals(SyncSide.LOCAL, plan.actions[0].side)
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun manualResolutionSkipLeavesNothing() {
        val baseline = SyncBaseline(
            "r",
            listOf(
                SyncBaselineEntry(
                    "h.txt",
                    local = node("h.txt", mtime = 100),
                    remote = node("h.txt", mtime = 100),
                ),
            ),
        )
        val local = snapshot(SyncSide.LOCAL, node("h.txt", size = 20, mtime = 200))
        val remote = snapshot(SyncSide.REMOTE, node("h.txt", size = 30, mtime = 150))
        val diffs = differ.diff(local, remote, baseline)
        val plan = planner.resolve(
            rule(strategy = SyncConflictStrategy.ASK),
            local,
            remote,
            diffs,
            listOf(SyncResolution("h.txt", SyncResolutionChoice.SKIP)),
        )
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun renameStrategyGeneratesNewName() {
        val baseline = SyncBaseline(
            "r",
            listOf(
                SyncBaselineEntry(
                    "f.txt",
                    local = node("f.txt", mtime = 100),
                    remote = node("f.txt", mtime = 100),
                ),
            ),
        )
        val local = snapshot(SyncSide.LOCAL, node("f.txt", size = 20, mtime = 300))
        val remote = snapshot(SyncSide.REMOTE, node("f.txt", size = 30, mtime = 150))
        val plan = planner.plan(
            rule(strategy = SyncConflictStrategy.RENAME),
            local,
            remote,
            differ.diff(local, remote, baseline),
        )
        assertEquals(1, plan.actions.size)
        val action = plan.actions[0]
        assertEquals(SyncSide.LOCAL, action.side)
        // Правило именования: «аааа» → «аааа-1» (дефис, без пробелов и скобок).
        assertEquals("f-1.txt", action.newName)
    }

    @Test
    fun renameTakesFirstFreeSuffix() {
        // Если в цели есть f.txt и f-2.txt, входящий f.txt должен стать f-1.txt,
        // а не f-3.txt (берётся первый незанятый суффикс).
        val baseline = SyncBaseline(
            "r",
            listOf(
                SyncBaselineEntry(
                    "f.txt",
                    local = node("f.txt", mtime = 100),
                    remote = node("f.txt", mtime = 100),
                ),
            ),
        )
        val local = snapshot(SyncSide.LOCAL, node("f.txt", size = 20, mtime = 300))
        val remote = snapshot(
            SyncSide.REMOTE,
            node("f.txt", size = 30, mtime = 150),
            node("f-2.txt", size = 5, mtime = 10),
        )
        val plan = planner.plan(
            rule(strategy = SyncConflictStrategy.RENAME),
            local,
            remote,
            differ.diff(local, remote, baseline),
        )
        val action = plan.actions.single { it.newName != null }
        assertEquals("f-1.txt", action.newName)
    }

    @Test
    fun renameKeepsExtensionIntact() {
        // Файл без расширения получает суффикс целиком, архив с точкой в имени —
        // расширение из последней точки.
        val baseline = SyncBaseline(
            "r",
            listOf(
                SyncBaselineEntry(
                    "README",
                    local = node("README", mtime = 100),
                    remote = node("README", mtime = 100),
                ),
                SyncBaselineEntry(
                    "archive.tar.gz",
                    local = node("archive.tar.gz", mtime = 100),
                    remote = node("archive.tar.gz", mtime = 100),
                ),
            ),
        )
        val local = snapshot(
            SyncSide.LOCAL,
            node("README", size = 20, mtime = 300),
            node("archive.tar.gz", size = 20, mtime = 300),
        )
        val remote = snapshot(
            SyncSide.REMOTE,
            node("README", size = 30, mtime = 150),
            node("archive.tar.gz", size = 30, mtime = 150),
        )
        val plan = planner.plan(
            rule(strategy = SyncConflictStrategy.RENAME),
            local,
            remote,
            differ.diff(local, remote, baseline),
        )
        val names = plan.actions.mapNotNull { it.newName }.toSet()
        assertTrue("README-1" in names, "ожидали README-1, получили $names")
        assertTrue("archive.tar-1.gz" in names, "ожидали archive.tar-1.gz, получили $names")
    }
}
