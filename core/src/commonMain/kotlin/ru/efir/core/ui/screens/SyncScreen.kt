package ru.efir.core.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import ru.efir.core.ui.Button
import ru.efir.core.ui.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import ru.efir.core.ui.OutlinedButton
import ru.efir.core.ui.OutlinedTextField
import ru.efir.core.ui.Switch
import ru.efir.core.ui.Text
import ru.efir.core.ui.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.efir.core.app.ActiveConnection
import ru.efir.core.app.EfirController
import ru.efir.core.app.AppFolders
import ru.efir.core.protocol.DeviceType
import ru.efir.core.protocol.SyncActionType
import ru.efir.core.protocol.SyncConflict
import ru.efir.core.protocol.SyncConflictStrategy
import ru.efir.core.protocol.SyncDirection
import ru.efir.core.protocol.SyncPlanResult
import ru.efir.core.protocol.SyncResolution
import ru.efir.core.protocol.SyncResolutionChoice
import ru.efir.core.protocol.SyncRuleConfig
import ru.efir.core.protocol.SyncSide
import ru.efir.core.protocol.SyncTrigger
import ru.efir.core.sync.SyncPreset
import ru.efir.core.sync.SyncVersion
import ru.efir.core.sync.backupBeforeOverwrite
import ru.efir.core.sync.conflictStrategy
import ru.efir.core.sync.deletePropagation
import ru.efir.core.sync.direction
import ru.efir.core.ui.AppIcons
import ru.efir.core.ui.ErrorText
import ru.efir.core.ui.HelpTopic
import ru.efir.core.ui.Hint
import ru.efir.core.ui.IconAction
import ru.efir.core.ui.ListRow
import ru.efir.core.ui.LocalStrings
import ru.efir.core.ui.Mono
import ru.efir.core.ui.SectionCard
import ru.efir.core.ui.SwitchRow
import ru.efir.core.ui.i18n.Strings

/** Названия сторон правила: «здесь» и «там» зависят от того, что за устройства соединены. */
private class Sides(val local: String, val remote: String)

/** Экран «Синхронизация»: правила, запуск, предпросмотр, конфликты, журнал и история версий. */
@Composable
fun SyncScreen(controller: EfirController, connection: ActiveConnection) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val rules by controller.syncRules.collectAsState()
    val status by controller.syncStatus.collectAsState()
    val journal by controller.syncJournal.collectAsState()
    val conflicts by controller.syncConflicts.collectAsState()
    val conflictRuleId by controller.conflictRuleId.collectAsState()

    val sides = remember(connection, s) {
        val local = s.sideName(controller.selfType)
        val remote = s.sideName(connection.remote.type)
        // Два одинаковых устройства: «ПК» и «ПК» ничего не говорят.
        if (local == remote) Sides(s.sideThis, s.sideOther) else Sides(local, remote)
    }

    var editing by remember { mutableStateOf<SyncRuleConfig?>(null) }
    var removing by remember { mutableStateOf<SyncRuleConfig?>(null) }
    var plan by remember { mutableStateOf<Pair<String, SyncPlanResult>?>(null) }
    var planError by remember { mutableStateOf(false) }
    var versionsRule by remember { mutableStateOf<String?>(null) }
    var versions by remember { mutableStateOf<List<SyncVersion>>(emptyList()) }
    var versionsBusy by remember { mutableStateOf(false) }
    var versionsFile by remember { mutableStateOf<String?>(null) }

    fun loadVersions(ruleId: String, file: String?) {
        versionsRule = ruleId
        versionsFile = file
        versionsBusy = true
        scope.launch {
            versions = runCatching {
                if (file == null) controller.syncVersions(ruleId) else controller.fileVersions(ruleId, file)
            }.getOrDefault(emptyList())
            versionsBusy = false
        }
    }

    ScreenColumn(HelpTopic.SYNC) {
        SectionCard(
            title = s.syncRules,
            icon = AppIcons.Sync,
            actions = {
                IconAction(AppIcons.Add, s.newRule, {
                    editing = SyncRuleConfig(
                        id = "rule_${controller.newId()}",
                        name = s.newRuleName,
                        localPath = AppFolders.syncDir,
                        remotePath = if (connection.remote.type == DeviceType.ANDROID) "/storage/emulated/0/Efir" else "",
                    )
                })
            },
        ) {
            if (rules.isEmpty()) {
                Hint(s.noRules)
                Button(tip = s.tipNewRule, onClick = {
                    editing = SyncRuleConfig(
                        id = "rule_${controller.newId()}",
                        name = s.newRuleName,
                        localPath = AppFolders.syncDir,
                        remotePath = if (connection.remote.type == DeviceType.ANDROID) "/storage/emulated/0/Efir" else "",
                    )
                }) { Text(s.newRule) }
            }
            rules.forEach { rule ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.name, style = MaterialTheme.typography.titleSmall)
                        Mono("${rule.localPath}  ${directionArrow(rule.direction)}  ${rule.remotePath}", maxLines = 2)
                        Hint(s.triggerSummary(rule) + " · " + s.conflictsLabel + ": " + s.strategyName(rule.conflictStrategy, sides.local, sides.remote))
                    }
                    Switch(checked = rule.enabled, onCheckedChange = { controller.setSyncRuleEnabled(rule.id, it) }, tip = s.tipRuleEnabled)
                }
                Row {
                    IconAction(AppIcons.Sync, s.syncNow, { controller.runSyncRule(rule.id) }, enabled = !status.running)
                    IconAction(AppIcons.Visibility, s.previewPlan, {
                        plan = null
                        planError = false
                        scope.launch {
                            val result = runCatching { controller.planSyncRule(rule.id) }.getOrNull()
                            if (result == null) planError = true else plan = rule.id to result
                        }
                    })
                    IconAction(AppIcons.Description, s.journal, { controller.loadSyncJournal(rule.id) })
                    IconAction(AppIcons.History, s.versionHistory, { loadVersions(rule.id, null) })
                    IconAction(AppIcons.Edit, s.edit, { editing = rule })
                    IconAction(AppIcons.Delete, s.delete, { removing = rule })
                }
            }

            if (status.running || status.total > 0) {
                Hint(
                    s.syncProgress(status.processed, status.total) +
                        (status.currentPath?.let { " · $it" } ?: "") +
                        (status.message?.let { " · $it" } ?: ""),
                )
                if (status.total > 0) {
                    LinearProgressIndicator(
                        progress = { status.processed.toFloat() / status.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (planError) ErrorText(s.planFailed)
        }

        if (conflicts.isNotEmpty()) {
            ConflictsCard(
                conflicts = conflicts,
                sides = sides,
                ruleName = rules.firstOrNull { it.id == conflictRuleId }?.name,
                onResolve = { list, choice ->
                    conflictRuleId?.let { ruleId ->
                        controller.resolveSyncConflicts(ruleId, list.map { SyncResolution(it.relativePath, choice) })
                    }
                },
            )
        }

        plan?.let { (ruleId, result) ->
            SectionCard(
                title = s.planTitle(rules.firstOrNull { it.id == ruleId }?.name ?: ruleId),
                icon = AppIcons.Visibility,
                actions = { IconAction(AppIcons.Close, s.close, { plan = null }) },
            ) {
                Hint(s.planSummary(result.actions.size, result.conflicts.size))
                result.actions.take(30).forEach { action ->
                    Mono(
                        s.actionName(action.type) + "  " +
                            (if (action.side == SyncSide.LOCAL) sides.local else sides.remote) + "  " +
                            action.relativePath,
                        maxLines = 1,
                    )
                }
                if (result.actions.size > 30) Hint(s.andMore(result.actions.size - 30))
            }
        }

        journal?.let { j ->
            SectionCard(
                title = s.journalTitle(rules.firstOrNull { it.id == j.ruleId }?.name ?: j.ruleId),
                icon = AppIcons.Description,
                topic = HelpTopic.SYNC_HISTORY,
            ) {
                if (j.entries.isEmpty()) Hint(s.journalEmpty)
                j.entries.takeLast(30).reversed().forEach { entry ->
                    ListRow(
                        title = s.actionName(entry.action) + " · " + entry.relativePath,
                        icon = if (entry.success) AppIcons.Check else AppIcons.Warning,
                        iconTint = if (entry.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        subtitle = formatDateTime(entry.timestamp) + (entry.detail?.let { " · $it" } ?: ""),
                    )
                }
            }
        }

        versionsRule?.let { ruleId ->
            val rule = rules.firstOrNull { it.id == ruleId }
            SectionCard(
                title = s.versionsTitle(rule?.name ?: ruleId),
                icon = AppIcons.History,
                topic = HelpTopic.SYNC_HISTORY,
                actions = {
                    IconAction(AppIcons.Refresh, s.refresh, { loadVersions(ruleId, versionsFile) })
                    IconAction(AppIcons.Delete, s.clearVersions, {
                        controller.clearSyncVersions(ruleId)
                        versions = emptyList()
                    })
                    IconAction(AppIcons.Close, s.close, { versionsRule = null })
                },
            ) {
                versionsFile?.let { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Hint(s.versionsOnlyFile(file), Modifier.weight(1f))
                        TextButton(onClick = { loadVersions(ruleId, null) }, tip = s.tipShowAllFiles) { Text(s.showAllFiles) }
                    }
                }
                when {
                    versionsBusy -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    versions.isEmpty() -> Hint(s.versionsEmpty)
                }
                versions.take(30).forEach { version ->
                    ListRow(
                        title = version.relativePath,
                        icon = AppIcons.File,
                        subtitle = formatDateTime(version.timestamp) + " · " + s.bytes(version.size) + " · " +
                            (if (version.side == SyncSide.LOCAL) sides.local else sides.remote),
                    ) {
                        if (versionsFile != version.relativePath) {
                            IconAction(AppIcons.Search, s.onlyThisFile, { loadVersions(ruleId, version.relativePath) })
                        }
                        IconAction(AppIcons.Refresh, s.restoreVersion, { controller.restoreSyncVersion(ruleId, version) })
                    }
                }
                if (versions.size > 30) Hint(s.andMore(versions.size - 30))
            }
        }
    }

    editing?.let { rule ->
        SyncRuleEditor(
            controller = controller,
            initial = rule,
            sides = sides,
            onSave = {
                controller.saveSyncRule(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    removing?.let { rule ->
        ConfirmDialog(
            title = s.deleteRuleTitle,
            text = s.deleteRuleText(rule.name),
            confirm = s.delete,
            onConfirm = {
                controller.removeSyncRule(rule.id)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

/** Конфликты стратегии «Спрашивать»: решение по каждому файлу или одно на все. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConflictsCard(
    conflicts: List<SyncConflict>,
    sides: Sides,
    ruleName: String?,
    onResolve: (List<SyncConflict>, SyncResolutionChoice) -> Unit,
) {
    val s = LocalStrings.current
    SectionCard(s.conflictsTitle(conflicts.size) + (ruleName?.let { " · $it" } ?: ""), icon = AppIcons.Warning, topic = HelpTopic.SYNC_CONFLICTS) {
        Hint(s.conflictsHint)
        conflicts.forEach { conflict ->
            Mono(conflict.relativePath, maxLines = 2)
            Hint(
                "${sides.local}: ${s.bytes(conflict.localSize)}, ${formatDateTime(conflict.localModifiedAt)}   ·   " +
                    "${sides.remote}: ${s.bytes(conflict.remoteSize)}, ${formatDateTime(conflict.remoteModifiedAt)}",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onResolve(listOf(conflict), SyncResolutionChoice.KEEP_LOCAL) }, tip = s.tipKeepSide(sides.local)) { Text(s.keep(sides.local)) }
                OutlinedButton(onClick = { onResolve(listOf(conflict), SyncResolutionChoice.KEEP_REMOTE) }, tip = s.tipKeepSide(sides.remote)) { Text(s.keep(sides.remote)) }
                OutlinedButton(onClick = { onResolve(listOf(conflict), SyncResolutionChoice.KEEP_BOTH) }, tip = s.tipKeepBoth) { Text(s.keepBoth) }
                TextButton(onClick = { onResolve(listOf(conflict), SyncResolutionChoice.SKIP) }, tip = s.tipSkipConflict) { Text(s.skip) }
            }
        }
        if (conflicts.size > 1) {
            Hint(s.forAllConflicts)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onResolve(conflicts, SyncResolutionChoice.KEEP_LOCAL) }, tip = s.tipKeepSide(sides.local)) { Text(s.keep(sides.local)) }
                OutlinedButton(onClick = { onResolve(conflicts, SyncResolutionChoice.KEEP_REMOTE) }, tip = s.tipKeepSide(sides.remote)) { Text(s.keep(sides.remote)) }
                OutlinedButton(onClick = { onResolve(conflicts, SyncResolutionChoice.KEEP_NEWEST) }, tip = s.tipKeepNewest) { Text(s.keepNewest) }
                OutlinedButton(onClick = { onResolve(conflicts, SyncResolutionChoice.KEEP_BOTH) }, tip = s.tipKeepBoth) { Text(s.keepBoth) }
                TextButton(onClick = { onResolve(conflicts, SyncResolutionChoice.SKIP) }, tip = s.tipSkipConflict) { Text(s.skip) }
            }
        }
    }
}

/** Диалог создания и изменения правила синхронизации. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncRuleEditor(
    controller: EfirController,
    initial: SyncRuleConfig,
    sides: Sides,
    onSave: (SyncRuleConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var name by remember { mutableStateOf(initial.name) }
    var localPath by remember { mutableStateOf(initial.localPath) }
    var remotePath by remember { mutableStateOf(initial.remotePath) }
    var direction by remember { mutableStateOf(initial.direction) }
    var trigger by remember { mutableStateOf(initial.trigger) }
    var strategy by remember { mutableStateOf(initial.conflictStrategy) }
    var interval by remember { mutableStateOf(initial.scheduleIntervalMinutes.takeIf { it > 0 }?.toString() ?: "30") }
    var include by remember { mutableStateOf(initial.includePatterns.joinToString(", ")) }
    var exclude by remember { mutableStateOf(initial.excludePatterns.joinToString(", ")) }
    var deletePropagation by remember { mutableStateOf(initial.deletePropagation) }
    var backup by remember { mutableStateOf(initial.backupBeforeOverwrite) }

    // Какую из двух папок сейчас выбирают в окне: на этом устройстве (true) или на другом (false).
    var picking by remember { mutableStateOf<Boolean?>(null) }

    val intervalMinutes = interval.toIntOrNull() ?: 0
    val valid = name.isNotBlank() && localPath.isNotBlank() && remotePath.isNotBlank() &&
        (trigger != SyncTrigger.SCHEDULED || intervalMinutes > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.ruleTitle) },
        confirmButton = {
            TextButton(enabled = valid, tip = s.tipSaveRule, onClick = {
                onSave(
                    initial.copy(
                        name = name.trim(),
                        localPath = localPath.trim(),
                        remotePath = remotePath.trim(),
                        direction = direction,
                        trigger = trigger,
                        conflictStrategy = strategy,
                        scheduleIntervalMinutes = if (trigger == SyncTrigger.SCHEDULED) intervalMinutes else 0,
                        includePatterns = parseList(include),
                        excludePatterns = parseList(exclude),
                        deletePropagation = deletePropagation,
                        backupBeforeOverwrite = backup,
                    ),
                )
            }) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text(s.ruleName) }, tip = s.tipRuleName, singleLine = true, modifier = Modifier.fillMaxWidth())
                // Путь можно вписать, а можно выбрать папку в окне — кнопкой справа.
                OutlinedTextField(
                    localPath, { localPath = it },
                    label = { Text(s.folderOn(sides.local)) },
                    tip = s.tipFolderPath,
                    trailingIcon = { IconAction(AppIcons.Folder, s.chooseFolder, { picking = true }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    remotePath, { remotePath = it },
                    label = { Text(s.folderOn(sides.remote)) },
                    tip = s.tipFolderPath,
                    trailingIcon = { IconAction(AppIcons.Folder, s.chooseFolder, { picking = false }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Hint(s.presets)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncPreset.entries.forEach { preset ->
                        val chosen = direction == preset.direction && strategy == preset.conflictStrategy &&
                            deletePropagation == preset.deletePropagation && backup == preset.backupBeforeOverwrite
                        FilterChip(chosen, {
                            direction = preset.direction
                            strategy = preset.conflictStrategy
                            deletePropagation = preset.deletePropagation
                            backup = preset.backupBeforeOverwrite
                        }, { Text(s.presetName(preset, sides.local, sides.remote)) })
                    }
                }

                Hint(s.direction)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncDirection.entries.forEach { d ->
                        FilterChip(direction == d, { direction = d }, {
                            Text("${sides.local} ${directionArrow(d)} ${sides.remote}")
                        })
                    }
                }

                Hint(s.trigger)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncTrigger.entries.forEach { t ->
                        FilterChip(trigger == t, { trigger = t }, { Text(s.triggerName(t)) })
                    }
                }
                if (trigger == SyncTrigger.SCHEDULED) {
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { interval = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(s.intervalMinutes) },
                        tip = s.tipInterval,
                        singleLine = true,
                        isError = intervalMinutes <= 0,
                    )
                }

                Hint(s.onConflict)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncConflictStrategy.entries.forEach { st ->
                        FilterChip(strategy == st, { strategy = st }, { Text(s.strategyName(st, sides.local, sides.remote)) })
                    }
                }

                SwitchRow(s.propagateDeletes, deletePropagation, { deletePropagation = it }, subtitle = s.propagateDeletesHint)
                SwitchRow(s.backupVersions, backup, { backup = it }, subtitle = s.backupVersionsHint)

                OutlinedTextField(include, { include = it }, label = { Text(s.includeMasks) }, tip = s.tipMasks, placeholder = { Text("*.jpg, *.png") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(exclude, { exclude = it }, label = { Text(s.excludeMasks) }, tip = s.tipMasks, placeholder = { Text("*.tmp, .git/*") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
    )

    picking?.let { local ->
        FolderPickerDialog(
            title = s.folderOnDevice(if (local) sides.local else sides.remote),
            start = (if (local) localPath else remotePath).takeIf { it.isNotBlank() },
            source = remember(local) { if (local) localFolderSource() else remoteFolderSource(controller) },
            onPick = { path ->
                if (local) localPath = path else remotePath = path
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

private fun parseList(text: String): List<String> =
    text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun directionArrow(direction: SyncDirection): String = when (direction) {
    SyncDirection.BIDIRECTIONAL -> "↔"
    SyncDirection.LOCAL_TO_REMOTE -> "→"
    SyncDirection.REMOTE_TO_LOCAL -> "←"
}

private fun Strings.triggerSummary(rule: SyncRuleConfig): String = when (rule.trigger) {
    SyncTrigger.SCHEDULED -> everyMinutes(rule.scheduleIntervalMinutes)
    else -> triggerName(rule.trigger)
}

private fun Strings.triggerName(trigger: SyncTrigger): String = when (trigger) {
    SyncTrigger.MANUAL -> triggerManual
    SyncTrigger.SCHEDULED -> triggerScheduled
    SyncTrigger.ON_CONNECT -> triggerOnConnect
    SyncTrigger.ON_CHANGE -> triggerOnChange
}

private fun Strings.strategyName(strategy: SyncConflictStrategy, local: String, remote: String): String = when (strategy) {
    SyncConflictStrategy.MERGE -> strategyMerge
    SyncConflictStrategy.OVERWRITE -> strategyOverwrite
    SyncConflictStrategy.APPEND -> strategyAppend
    SyncConflictStrategy.PREFER_LOCAL -> wins(local)
    SyncConflictStrategy.PREFER_REMOTE -> wins(remote)
    SyncConflictStrategy.PREFER_NEWEST -> strategyNewest
    SyncConflictStrategy.RENAME -> strategyRename
    SyncConflictStrategy.ASK -> strategyAsk
}

private fun Strings.presetName(preset: SyncPreset, local: String, remote: String): String = when (preset) {
    SyncPreset.SAFE_MERGE -> presetSafeMerge
    SyncPreset.MIRROR_TO_REMOTE -> mirror(local, remote)
    SyncPreset.MIRROR_TO_LOCAL -> mirror(remote, local)
    SyncPreset.ADD_ONLY -> presetAddOnly
    SyncPreset.ASK_ON_CONFLICT -> presetAsk
}

private fun Strings.actionName(type: SyncActionType): String = when (type) {
    SyncActionType.ADD -> actionAdd
    SyncActionType.UPDATE -> actionUpdate
    SyncActionType.DELETE -> actionDelete
    SyncActionType.RENAME -> actionRename
    SyncActionType.SKIP -> actionSkip
}
