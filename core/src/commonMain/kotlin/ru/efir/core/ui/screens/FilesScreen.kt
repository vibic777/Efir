package ru.efir.core.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import ru.efir.core.ui.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import ru.efir.core.ui.OutlinedButton
import ru.efir.core.ui.Text
import ru.efir.core.ui.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.efir.core.app.ActiveConnection
import ru.efir.core.app.EfirController
import ru.efir.core.app.localFilePickerSupported
import ru.efir.core.app.openLocalFolder
import ru.efir.core.app.pickLocalFiles
import ru.efir.core.files.FileKind
import ru.efir.core.files.classify
import ru.efir.core.media.decodeImage
import ru.efir.core.protocol.RemoteEntry
import ru.efir.core.transfer.QueuedTransfer
import ru.efir.core.transfer.TransferDirection
import ru.efir.core.transfer.TransferGroup
import ru.efir.core.transfer.TransferJob
import ru.efir.core.transfer.TransferStatus
import ru.efir.core.transfer.aggregateGroups
import ru.efir.core.ui.AppIcons
import ru.efir.core.ui.ErrorText
import ru.efir.core.ui.HelpTopic
import ru.efir.core.ui.Hint
import ru.efir.core.ui.IconAction
import ru.efir.core.ui.ListRow
import ru.efir.core.ui.LocalStrings
import ru.efir.core.ui.Mono
import ru.efir.core.ui.ScreenTabs
import ru.efir.core.ui.SectionCard
import ru.efir.core.ui.TabItem
import ru.efir.core.ui.i18n.Strings

/** Максимум символов текста в предпросмотре. */
private const val MAX_PREVIEW_CHARS = 8000

/** Что сейчас спрашивает диалог файлового менеджера. */
private sealed interface FileDialog {
    data object NewFolder : FileDialog
    data class Rename(val entry: RemoteEntry) : FileDialog
    data class Delete(val entry: RemoteEntry) : FileDialog
    data class Preview(val entry: RemoteEntry, val bytes: ByteArray?, val failed: Boolean = false) : FileDialog
}

/** Экран «Файлы»: файлы удалённого устройства, операции над ними и передачи. */
@Composable
fun FilesScreen(controller: EfirController, connection: ActiveConnection) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val state by controller.fileBrowser.collectAsState()
    val transfers by controller.transfers.collectAsState()
    val pending by controller.pendingTransfers.collectAsState()
    var dialog by remember { mutableStateOf<FileDialog?>(null) }
    val path = state.currentPath

    var tab by remember { mutableStateOf(0) }

    ScreenColumn(HelpTopic.FILES) {
        ScreenTabs(
            tabs = listOf(
                TabItem(AppIcons.Folder, s.navFiles),
                TabItem(AppIcons.Sync, s.tabTransfers, (pending.size + transfers.size).takeIf { it > 0 }),
            ),
            selected = tab,
            onSelect = { tab = it },
        )

        if (tab == 0) SectionCard(
            title = s.filesOf(connection.remote.name),
            icon = AppIcons.Folder,
            actions = {
                if (path != null) {
                    IconAction(AppIcons.ArrowUpward, s.goUp, {
                        // Из корня раздела «вверх» возвращает к списку разделов.
                        if (state.roots.any { it.path == path } || '/' !in path.trimEnd('/')) controller.loadFileRoots()
                        else controller.fileGoUp()
                    })
                    IconAction(AppIcons.NewFolder, s.newFolder, { dialog = FileDialog.NewFolder })
                    if (localFilePickerSupported) {
                        IconAction(AppIcons.FileUpload, s.upload, {
                            scope.launch {
                                val files = pickLocalFiles()
                                if (files.isNotEmpty()) controller.uploadTo(path, files)
                            }
                        })
                    }
                }
                IconAction(AppIcons.Refresh, s.refresh, {
                    if (path == null) controller.loadFileRoots() else controller.refreshFiles()
                })
            },
        ) {
            Mono(path ?: s.fileRoots, maxLines = 2)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorText(it) }

            val entries = if (path == null) state.roots else state.entries
            if (entries.isEmpty() && !state.loading) Hint(if (path == null) s.noRoots else s.emptyFolder)

            Column {
                entries.sortedWith(compareByDescending<RemoteEntry> { it.directory }.thenBy { it.name.lowercase() })
                    .forEach { entry ->
                        FileRow(
                            entry = entry,
                            root = path == null,
                            onOpen = { controller.openFolder(entry.path) },
                            onDownload = { controller.downloadToDefault(entry) },
                            onPreview = {
                                dialog = FileDialog.Preview(entry, null)
                                scope.launch {
                                    val bytes = controller.readFilePreview(entry.path)?.second
                                    // Диалог могли закрыть или открыть другой файл, пока шла загрузка.
                                    val shown = dialog
                                    if (shown is FileDialog.Preview && shown.entry.path == entry.path) {
                                        dialog = FileDialog.Preview(entry, bytes, failed = bytes == null)
                                    }
                                }
                            },
                            onRename = { dialog = FileDialog.Rename(entry) },
                            onDelete = { dialog = FileDialog.Delete(entry) },
                        )
                    }
            }
            if (state.hasMore) Hint(s.moreItems)

            HorizontalDivider()
            // Куда сохраняется скачанное; карандаш ведёт в «Настройки» → «Папки», где папку меняют.
            val openSettings = LocalOpenSettings.current
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Hint(s.downloadsTo(controller.downloadDir), Modifier.weight(1f))
                if (localFilePickerSupported) {
                    IconAction(AppIcons.Folder, s.openDownloads, { openLocalFolder(controller.downloadDir) }, topic = HelpTopic.DOWNLOADS)
                }
                IconAction(AppIcons.Edit, s.changeFolder, { openSettings(SettingsSection.FOLDERS) }, topic = HelpTopic.DOWNLOADS)
            }
        }

        if (tab == 1) {
            if (pending.isEmpty() && transfers.isEmpty()) Hint(s.transfersEmpty)
            if (pending.isNotEmpty()) PendingTransfersCard(controller, pending)
            if (transfers.isNotEmpty()) TransfersCard(controller, transfers)
        }
    }

    when (val d = dialog) {
        null -> Unit

        FileDialog.NewFolder -> TextInputDialog(
            title = s.newFolder,
            label = s.folderName,
            initial = s.newFolderDefault,
            confirm = s.create,
            onConfirm = { name ->
                path?.let { controller.makeFolder(it.trimEnd('/') + "/" + name) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is FileDialog.Rename -> TextInputDialog(
            title = s.rename,
            label = s.newName,
            initial = d.entry.name,
            confirm = s.rename,
            onConfirm = { name ->
                if (name != d.entry.name) controller.renameFile(d.entry.path, name)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is FileDialog.Delete -> ConfirmDialog(
            title = s.deleteTitle,
            text = if (d.entry.directory) s.deleteFolderText(d.entry.name) else s.deleteFileText(d.entry.name),
            confirm = s.delete,
            onConfirm = {
                controller.deleteFiles(listOf(d.entry.path), recursive = d.entry.directory)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        is FileDialog.Preview -> PreviewDialog(d, onDownload = {
            controller.downloadToDefault(d.entry)
            dialog = null
        }, onDismiss = { dialog = null })
    }
}

@Composable
private fun FileRow(
    entry: RemoteEntry,
    root: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onPreview: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    val kind = classify(entry)
    ListRow(
        title = entry.name.ifBlank { entry.path },
        icon = when {
            entry.directory -> AppIcons.Folder
            kind == FileKind.IMAGE -> AppIcons.Image
            kind == FileKind.TEXT -> AppIcons.Description
            else -> AppIcons.File
        },
        iconTint = if (entry.directory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        subtitle = when {
            root -> entry.path
            entry.directory -> formatDateTime(entry.modifiedAt)
            else -> s.bytes(entry.size) + " · " + formatDateTime(entry.modifiedAt)
        },
        modifier = Modifier.clickable(onClick = if (entry.directory) onOpen else onPreview),
    ) {
        if (!root) {
            IconAction(AppIcons.FileDownload, if (entry.directory) s.downloadFolder else s.download, onDownload)
            IconAction(AppIcons.Edit, s.rename, onRename)
            IconAction(AppIcons.Delete, s.delete, onDelete)
        }
    }
}

/**
 * Предпросмотр файла по его типу: изображение рисуется, текст показывается
 * фрагментом, для остального — пояснение и кнопка «Скачать».
 */
@Composable
private fun PreviewDialog(preview: FileDialog.Preview, onDownload: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val entry = preview.entry
    val bytes = preview.bytes
    val kind = classify(entry)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Hint(s.bytes(entry.size) + " · " + s.kindName(kind) + " · " + formatDateTime(entry.modifiedAt))
                when {
                    preview.failed -> ErrorText(s.previewFailed)
                    bytes == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    kind == FileKind.IMAGE -> {
                        val bitmap = remember(entry.path) { decodeImage(bytes) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = entry.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                            )
                        } else {
                            Hint(s.previewImageFailed)
                        }
                    }

                    kind == FileKind.TEXT -> {
                        Mono(remember(entry.path) { bytes.decodeToString().take(MAX_PREVIEW_CHARS) })
                        if (entry.size > bytes.size) Hint(s.previewTruncated(s.bytes(bytes.size.toLong())))
                    }

                    else -> Hint(s.previewUnavailable)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDownload, tip = s.tipDownload) { Text(s.download) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.close) } },
    )
}

/** Название категории файла. */
private fun Strings.kindName(kind: FileKind): String = when (kind) {
    FileKind.IMAGE -> kindImage
    FileKind.VIDEO -> kindVideo
    FileKind.AUDIO -> kindAudio
    FileKind.TEXT -> kindText
    FileKind.DIRECTORY -> kindFolder
    FileKind.OTHER -> kindFile
}

/** Незавершённые передачи прошлых запусков: продолжить (по одной или все) либо убрать. */
@Composable
private fun PendingTransfersCard(controller: EfirController, pending: List<QueuedTransfer>) {
    val s = LocalStrings.current
    SectionCard(s.pendingTransfers(pending.size), icon = AppIcons.History, topic = HelpTopic.TRANSFERS) {
        Hint(s.pendingHint)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { controller.resumePendingTransfers() }, tip = s.tipResumeAll) { Text(s.resumeAll) }
            OutlinedButton(onClick = { controller.discardAllPendingTransfers() }, tip = s.tipDiscardAll) { Text(s.discardAll) }
        }
        pending.forEach { job ->
            ListRow(
                title = job.remotePath.substringAfterLast('/'),
                icon = if (job.direction == TransferDirection.UPLOAD) AppIcons.FileUpload else AppIcons.FileDownload,
                subtitle = s.bytes(job.transferredBytes) + " / " +
                    (if (job.totalBytes > 0) s.bytes(job.totalBytes) else "?") +
                    (job.groupLabel?.let { " · " + s.inFolder(it) } ?: ""),
            ) {
                IconAction(AppIcons.PlayArrow, s.resume, { controller.resumePendingTransfer(job.id) })
                IconAction(AppIcons.Close, s.discard, { controller.discardPendingTransfer(job.id) })
            }
            if (job.totalBytes > 0) LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Идущие и завершённые передачи: общий прогресс папок и отдельные файлы. */
@Composable
private fun TransfersCard(controller: EfirController, transfers: List<TransferJob>) {
    val s = LocalStrings.current
    val groups = remember(transfers) { aggregateGroups(transfers) }
    val singles = transfers.filter { it.group == null }
    SectionCard(s.transfersTitle(transfers.size), icon = AppIcons.Sync, topic = HelpTopic.TRANSFERS) {
        groups.forEach { group -> GroupRow(group) }
        singles.forEach { job ->
            ListRow(
                title = job.remotePath.substringAfterLast('/'),
                icon = if (job.direction == TransferDirection.UPLOAD) AppIcons.FileUpload else AppIcons.FileDownload,
                iconTint = if (job.status == TransferStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                subtitle = buildString {
                    append(s.bytes(job.transferredBytes)).append(" / ").append(s.bytes(job.totalBytes))
                    append(" · ").append(s.transferStatus(job.status, job.error))
                    val speed = job.speedBytesPerSecond
                    if (job.status == TransferStatus.RUNNING && speed > 0) {
                        append(" · ").append(s.bytes(speed)).append(s.perSecond)
                        job.etaSeconds?.let { append(" · ").append(s.eta(it)) }
                    }
                },
            ) {
                if (!job.isFinished) IconAction(AppIcons.Close, s.cancelTransfer, { controller.cancelTransfer(job.id) })
            }
            if (!job.isFinished) LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GroupRow(group: TransferGroup) {
    val s = LocalStrings.current
    ListRow(
        title = group.label,
        icon = AppIcons.Folder,
        subtitle = s.groupProgress(group.finishedCount, group.jobCount) + " · " +
            s.bytes(group.transferredBytes) + " / " + s.bytes(group.totalBytes) + " · " +
            s.transferStatus(group.status, null),
    )
    LinearProgressIndicator(progress = { group.progress }, modifier = Modifier.fillMaxWidth())
}

/** Подпись состояния передачи. */
fun Strings.transferStatus(status: TransferStatus, error: String?): String = when (status) {
    TransferStatus.QUEUED -> statusQueued
    TransferStatus.RUNNING -> statusRunning
    TransferStatus.COMPLETED -> statusDone
    TransferStatus.FAILED -> statusFailed(error ?: "?")
    TransferStatus.CANCELLED -> statusCancelled
}
