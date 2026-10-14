package ru.efir.core.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.efir.core.app.AppFolders
import ru.efir.core.app.EfirController
import ru.efir.core.files.listLocalFolders
import ru.efir.core.files.localBrowseRoots
import ru.efir.core.files.makeLocalFolder
import ru.efir.core.ui.AppIcons
import ru.efir.core.ui.ErrorText
import ru.efir.core.ui.Hint
import ru.efir.core.ui.IconAction
import ru.efir.core.ui.LocalStrings
import ru.efir.core.ui.OutlinedTextField
import ru.efir.core.ui.Text
import ru.efir.core.ui.TextButton

/** Папка в окне выбора: имя для показа и полный путь через `/`. */
class FolderItem(val name: String, val path: String)

/**
 * Где окно выбора берёт папки: на этом устройстве ([localFolderSource]) или на
 * другом ([remoteFolderSource]). Окно одно на оба случая.
 */
class FolderSource(
    /** С чего начинается выбор: диски, память, открытые другим устройством папки. */
    val roots: suspend () -> List<FolderItem>,
    /** Подпапки папки. */
    val list: suspend (String) -> List<FolderItem>,
    /** Создаёт папку; `true`, если она есть после вызова. */
    val make: suspend (String) -> Boolean,
    /** Папки другого устройства: выбирать можно только из того, что оно открыло. */
    val remote: Boolean,
)

private fun unix(path: String): String = path.replace('\\', '/')

private fun nameOf(path: String): String = unix(path).trimEnd('/').substringAfterLast('/').ifEmpty { unix(path) }

/** Папки этого устройства: любой диск и любая папка, которую видит сам человек. */
fun localFolderSource(): FolderSource = FolderSource(
    roots = { withContext(Dispatchers.Default) { localBrowseRoots().map { FolderItem(unix(it), unix(it)) } } },
    list = { path -> withContext(Dispatchers.Default) { listLocalFolders(path).map { FolderItem(nameOf(it), unix(it)) } } },
    make = { path -> withContext(Dispatchers.Default) { makeLocalFolder(path) } },
    remote = false,
)

/** Папки другого устройства — те, что оно открыло («Настройки» → «Папки» на нём). */
fun remoteFolderSource(controller: EfirController): FolderSource = FolderSource(
    roots = { controller.listRemoteFolders(null).map { FolderItem(unix(it.path), unix(it.path)) } },
    list = { path -> controller.listRemoteFolders(path).sortedBy { it.name.lowercase() }.map { FolderItem(it.name, unix(it.path)) } },
    make = { path -> controller.makeRemoteFolder(path) },
    remote = true,
)

/** Папка уровнем выше; `null` — выше только список дисков и разделов. */
private fun parentOf(path: String, roots: List<FolderItem>): String? {
    val current = path.trimEnd('/')
    if (roots.any { it.path.trimEnd('/') == current }) return null
    val at = current.lastIndexOf('/')
    return when {
        at < 0 -> null
        at == 0 -> if (current.length > 1) "/" else null
        else -> current.substring(0, at).let { if (it.length == 2 && it[1] == ':') "$it/" else it }
    }
}

/**
 * Окно выбора папки: сверху путь (его можно вписать и перейти), ниже — диски и
 * разделы либо подпапки открытой папки. «Новая папка» создаёт её тут же,
 * «Выбрать эту папку» отдаёт открытую папку в [onPick].
 */
@Composable
fun FolderPickerDialog(
    title: String,
    start: String?,
    source: FolderSource,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var roots by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var current by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }

    fun open(path: String?) {
        loading = true
        failed = false
        scope.launch {
            val target = path?.let { AppFolders.clean(it) }
            current = target
            typed = target.orEmpty()
            items = runCatching { if (target == null) roots else source.list(target) }.getOrDefault(emptyList())
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        roots = runCatching { source.roots() }.getOrDefault(emptyList())
        open(start?.takeIf { it.isNotBlank() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(s.pickerPath) },
                    tip = s.tipPickerPath,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { open(typed) }),
                    trailingIcon = {
                        IconAction(AppIcons.Send, s.pickerGo, { open(typed) }, enabled = typed.isNotBlank())
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val folder = current
                    IconAction(AppIcons.ArrowUpward, s.goUp, { open(folder?.let { parentOf(it, roots) }) }, enabled = folder != null)
                    IconAction(AppIcons.NewFolder, s.newFolder, { naming = true }, enabled = folder != null)
                    IconAction(AppIcons.Refresh, s.refresh, { open(folder) })
                    Hint(if (folder == null) s.pickerRoots else nameOf(folder), Modifier.weight(1f).padding(start = 8.dp))
                }
                if (source.remote) Hint(s.pickerRemoteHint)
                HorizontalDivider()
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) ErrorText(s.pickerFailed)
                Column(Modifier.height(280.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (!loading && items.isEmpty()) Hint(s.pickerEmpty, Modifier.padding(vertical = 8.dp))
                    items.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { open(item.path) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AppIcons.Folder, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            androidx.compose.material3.Text(
                                item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Выбирается открытая папка; путь, вписанный в поле, сначала открывают стрелкой.
            TextButton(
                enabled = !loading && (current != null || typed.isNotBlank()),
                onClick = {
                    val chosen = AppFolders.clean(typed.ifBlank { current.orEmpty() }) ?: return@TextButton
                    scope.launch {
                        // Папка создаётся, если её ещё нет: выбрать можно и новую.
                        if (source.make(chosen)) onPick(chosen) else failed = true
                    }
                },
            ) { Text(s.pickerChoose) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )

    if (naming) {
        TextInputDialog(
            title = s.newFolder,
            label = s.folderName,
            initial = s.newFolderDefault,
            confirm = s.create,
            onConfirm = { name ->
                naming = false
                val parent = current ?: return@TextInputDialog
                val path = parent.trimEnd('/') + "/" + name
                scope.launch { if (source.make(path)) open(path) else failed = true }
            },
            onDismiss = { naming = false },
        )
    }
}
