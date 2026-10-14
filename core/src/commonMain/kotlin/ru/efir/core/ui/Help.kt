package ru.efir.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.efir.core.BuildInfo
import ru.efir.core.ui.i18n.Strings

/** Значок раздела справки и слова, которые он означает (те же, что в его подсказке). */
class HelpIcon(val icon: ImageVector, val label: (Strings) -> String)

/** Название и текст раздела справки на одном языке. */
class HelpText(val title: String, val body: String)

/** Вкладка справки: экран приложения, о котором её разделы. */
enum class HelpGroup(val icon: ImageVector, val title: (Strings) -> String) {
    START(AppIcons.Info, { it.helpGroupStart }),
    DEVICES(AppIcons.Smartphone, { it.navDevices }),
    SCREEN(AppIcons.ScreenRecord, { it.navScreen }),
    FILES(AppIcons.Folder, { it.navFiles }),
    SYNC(AppIcons.Sync, { it.navSync }),
    CLIPBOARD(AppIcons.ContentPaste, { it.navClipboard }),
    MEDIA(AppIcons.Videocam, { it.navMedia }),
    PHONE(AppIcons.Call, { it.navPhone }),
    SETTINGS(AppIcons.Tune, { it.settings }),
}

/**
 * Разделы справки, как в Notio: вкладка ([group]) и под ней разделы. Каждое место
 * приложения относится к своему разделу — нажатие на всплывшую подсказку открывает
 * справку именно на нём. Новая функция получает место здесь тем же шагом, что и код:
 * раздел, его текст на всех языках (`HelpTextsRu.kt`, `HelpTextsEn.kt`) и значки со
 * словами. Тест `HelpTest` проверяет, что у каждого раздела есть текст на каждом языке.
 */
enum class HelpTopic(val group: HelpGroup, val icons: List<HelpIcon> = emptyList()) {
    START(HelpGroup.START),
    HINTS(
        HelpGroup.START,
        listOf(
            HelpIcon(AppIcons.Help) { it.help },
            HelpIcon(AppIcons.Search) { it.helpSearch },
        ),
    ),
    BATTERY(HelpGroup.START),

    DEVICES(
        HelpGroup.DEVICES,
        listOf(
            HelpIcon(AppIcons.Key) { it.pairing },
            HelpIcon(AppIcons.ViewGrid) { it.showQr },
            HelpIcon(AppIcons.ContentCopy) { it.copyPairingData },
            HelpIcon(AppIcons.Link) { it.connect },
        ),
    ),
    NETWORK(
        HelpGroup.DEVICES,
        listOf(
            HelpIcon(AppIcons.Search) { it.findDevice },
            HelpIcon(AppIcons.Refresh) { it.recheckNetwork },
        ),
    ),
    TRUSTED(
        HelpGroup.DEVICES,
        listOf(
            HelpIcon(AppIcons.Lock) { it.tabTrusted },
            HelpIcon(AppIcons.Link) { it.connect },
            HelpIcon(AppIcons.Delete) { it.revoke },
        ),
    ),

    SCREEN(
        HelpGroup.SCREEN,
        listOf(
            HelpIcon(AppIcons.PlayArrow) { it.startScreen },
            HelpIcon(AppIcons.Stop) { it.stopScreen },
            HelpIcon(AppIcons.Fullscreen) { it.fullScreen },
            HelpIcon(AppIcons.RotateRight) { it.rotateScreen },
            HelpIcon(AppIcons.FullscreenExit) { it.exitFullScreen },
            HelpIcon(AppIcons.ArrowBack) { it.keyBack },
            HelpIcon(AppIcons.Home) { it.keyHome },
            HelpIcon(AppIcons.Apps) { it.keyRecents },
            HelpIcon(AppIcons.Send) { it.sendText },
        ),
    ),
    SCREEN_SHARE(
        HelpGroup.SCREEN,
        listOf(
            HelpIcon(AppIcons.ScreenRecord) { it.sharingNow },
            HelpIcon(AppIcons.Stop) { it.stopSharing },
        ),
    ),

    FILES(
        HelpGroup.FILES,
        listOf(
            HelpIcon(AppIcons.ArrowUpward) { it.goUp },
            HelpIcon(AppIcons.NewFolder) { it.newFolder },
            HelpIcon(AppIcons.FileUpload) { it.upload },
            HelpIcon(AppIcons.FileDownload) { it.download },
            HelpIcon(AppIcons.Edit) { it.rename },
            HelpIcon(AppIcons.Delete) { it.delete },
            HelpIcon(AppIcons.Refresh) { it.refresh },
        ),
    ),
    TRANSFERS(
        HelpGroup.FILES,
        listOf(
            HelpIcon(AppIcons.Close) { it.cancelTransfer },
            HelpIcon(AppIcons.History) { it.resume },
        ),
    ),
    DOWNLOADS(
        HelpGroup.FILES,
        listOf(
            HelpIcon(AppIcons.Folder) { it.openDownloads },
            HelpIcon(AppIcons.Edit) { it.changeFolder },
        ),
    ),

    SYNC(
        HelpGroup.SYNC,
        listOf(
            HelpIcon(AppIcons.Add) { it.newRule },
            HelpIcon(AppIcons.Folder) { it.chooseFolder },
            HelpIcon(AppIcons.Sync) { it.syncNow },
            HelpIcon(AppIcons.Visibility) { it.previewPlan },
            HelpIcon(AppIcons.Edit) { it.edit },
            HelpIcon(AppIcons.Delete) { it.delete },
        ),
    ),
    SYNC_CONFLICTS(HelpGroup.SYNC, listOf(HelpIcon(AppIcons.Warning) { it.conflictsTitle(0).substringBefore(" (") })),
    SYNC_HISTORY(
        HelpGroup.SYNC,
        listOf(
            HelpIcon(AppIcons.Description) { it.journal },
            HelpIcon(AppIcons.History) { it.versionHistory },
            HelpIcon(AppIcons.Refresh) { it.restoreVersion },
        ),
    ),

    CLIPBOARD(
        HelpGroup.CLIPBOARD,
        listOf(
            HelpIcon(AppIcons.FileDownload) { it.pullClipboardShort },
            HelpIcon(AppIcons.ContentCopy) { it.copyAgain },
            HelpIcon(AppIcons.Star) { it.pin },
        ),
    ),
    NOTIFICATIONS(
        HelpGroup.CLIPBOARD,
        listOf(
            HelpIcon(AppIcons.Send) { it.reply },
            HelpIcon(AppIcons.Close) { it.dismissNotification },
        ),
    ),

    MEDIA(
        HelpGroup.MEDIA,
        listOf(
            HelpIcon(AppIcons.PhotoCamera) { it.cameraBack },
            HelpIcon(AppIcons.Smartphone) { it.cameraFront },
            HelpIcon(AppIcons.Mic) { it.audioMic },
            HelpIcon(AppIcons.VolumeUp) { it.audioSystem },
        ),
    ),

    PHONE(
        HelpGroup.PHONE,
        listOf(
            HelpIcon(AppIcons.Sms) { it.writeSms },
            HelpIcon(AppIcons.Send) { it.sendSms },
        ),
    ),
    CALLS(
        HelpGroup.PHONE,
        listOf(
            HelpIcon(AppIcons.Call) { it.callNumber },
            HelpIcon(AppIcons.CallReceived) { it.callIncoming },
            HelpIcon(AppIcons.CallMade) { it.callOutgoing },
            HelpIcon(AppIcons.CallEnd) { it.callMissed },
        ),
    ),

    SETTINGS(
        HelpGroup.SETTINGS,
        listOf(
            HelpIcon(AppIcons.Tune) { it.settings },
            HelpIcon(AppIcons.Search) { it.settingsSearch },
            HelpIcon(AppIcons.ArrowBack) { it.back },
        ),
    ),
    FOLDERS(
        HelpGroup.SETTINGS,
        listOf(
            HelpIcon(AppIcons.Folder) { it.chooseFolder },
            HelpIcon(AppIcons.Add) { it.addSharedFolder },
            HelpIcon(AppIcons.Refresh) { it.resetFolder },
        ),
    ),
    PERMISSIONS(
        HelpGroup.SETTINGS,
        listOf(
            HelpIcon(AppIcons.Check) { it.grant },
            HelpIcon(AppIcons.Warning) { it.revokePermission },
        ),
    ),
    LOG(
        HelpGroup.SETTINGS,
        listOf(
            HelpIcon(AppIcons.Refresh) { it.logRefresh },
            HelpIcon(AppIcons.ContentCopy) { it.logCopy },
            HelpIcon(AppIcons.FileUpload) { it.logExport },
            HelpIcon(AppIcons.Delete) { it.logClear },
        ),
    ),
    ABOUT(HelpGroup.SETTINGS, listOf(HelpIcon(AppIcons.Info) { it.about })),
    ;

    fun title(s: Strings): String = s.helpTexts[name]?.title ?: name

    fun body(s: Strings): String = s.helpTexts[name]?.body.orEmpty()
}

/**
 * Место справки, где поиск нашёл слова: раздел и кусок его текста вокруг первого
 * совпадения; [from]..[to] — найденное в [snippet] (`-1`, если совпал только заголовок).
 */
class HelpHit(val topic: HelpTopic, val snippet: String, val from: Int, val to: Int)

private const val SNIPPET_BEFORE = 50
private const val SNIPPET_AFTER = 110

/**
 * Контекстный поиск по справке: разделы, в названии, тексте или словах значков
 * которых есть [query] (без учёта регистра). Сначала ищется фраза целиком; если её
 * нет — разделы, где есть все её слова. Найденные по названию идут первыми.
 * Меньше двух букв не ищется.
 */
fun searchHelp(query: String, s: Strings): List<HelpHit> {
    val phrase = query.trim()
    if (phrase.length < 2) return emptyList()
    val words = phrase.split(' ', ',', '.').filter { it.length >= 2 }

    val byTitle = ArrayList<HelpHit>()
    val byText = ArrayList<HelpHit>()
    for (topic in HelpTopic.entries) {
        val title = topic.title(s)
        val bodies = listOf(topic.body(s)) + topic.icons.map { it.label(s) }
        val whole = title + "\n" + bodies.joinToString("\n")
        // Что подсвечивать: всю фразу, а если её нет, но есть все слова — первое из них.
        val needle = when {
            whole.contains(phrase, ignoreCase = true) -> phrase
            words.size > 1 && words.all { whole.contains(it, ignoreCase = true) } -> words.first()
            else -> continue
        }
        val inTitle = title.contains(needle, ignoreCase = true)
        val body = bodies.firstOrNull { it.contains(needle, ignoreCase = true) }
        val hit = if (body != null) {
            val at = body.indexOf(needle, ignoreCase = true)
            // Кусок начинается с целого слова.
            val start = (at - SNIPPET_BEFORE).coerceAtLeast(0).let { raw ->
                if (raw == 0) 0 else body.indexOf(' ', raw).let { space -> if (space in 0 until at) space + 1 else raw }
            }
            val end = (at + needle.length + SNIPPET_AFTER).coerceAtMost(body.length)
            val piece = (if (start > 0) "…" else "") + body.substring(start, end).replace('\n', ' ') + (if (end < body.length) "…" else "")
            val from = at - start + (if (start > 0) 1 else 0)
            HelpHit(topic, piece, from, from + needle.length)
        } else {
            HelpHit(topic, bodies.first().take(SNIPPET_BEFORE + SNIPPET_AFTER).replace('\n', ' '), -1, -1)
        }
        if (inTitle) byTitle += hit else byText += hit
    }
    return byTitle + byText
}

/**
 * Куда ведёт нажатая подсказка и кнопка «Справка». Справка показывается поверх окна
 * и поверх открытого диалога ([HelpOverlay]): её закрытие оставляет всё как было —
 * экран, вкладку, набранный в диалоге текст.
 */
object HelpLink {
    var topic: HelpTopic? by mutableStateOf(null)
        private set

    var open: Boolean by mutableStateOf(false)
        private set

    fun open(topic: HelpTopic?) {
        this.topic = topic
        open = true
    }

    fun close() {
        open = false
    }
}

/**
 * Справка: вкладка на каждый экран приложения, под ней — разделы; раздел — это его
 * текст и значки, которые там встречаются. Сверху — поиск по всей справке: найденное
 * показано в окружении, нажатие открывает раздел с подсветкой искомого. [topic] —
 * раздел, на котором справка открывается.
 */
@Composable
fun HelpScreen(topic: HelpTopic? = null) {
    val s = LocalStrings.current
    var current by remember(topic) { mutableStateOf(topic ?: HelpTopic.START) }
    var query by remember(topic) { mutableStateOf("") }
    // Что подсветить в разделе, открытом из поиска.
    var marked by remember(topic) { mutableStateOf("") }
    val hits = remember(query, s) { searchHelp(query, s) }
    val searching = query.trim().length >= 2

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                label = { Text(s.helpSearch) },
                tip = s.tipHelpSearch,
                topic = HelpTopic.HINTS,
                leadingIcon = { Icon(AppIcons.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconAction(AppIcons.Close, s.clearSearch, { query = "" }, topic = HelpTopic.HINTS)
                },
                singleLine = true,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            )
        }

        if (searching) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).widthIn(max = 960.dp),
            ) {
                Hint(if (hits.isEmpty()) s.helpNothingFound else s.helpFound(hits.size), Modifier.padding(vertical = 8.dp))
                hits.forEach { hit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                marked = if (hit.from >= 0) hit.snippet.substring(hit.from, hit.to) else ""
                                current = hit.topic
                                query = ""
                            }
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(hit.topic.group.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            androidx.compose.material3.Text(
                                hit.topic.group.title(s) + " › " + hit.topic.title(s),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        androidx.compose.material3.Text(
                            highlighted(hit.snippet, hit.from, hit.to),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
            return@Column
        }

        val groups = HelpGroup.entries
        ScrollableTabRow(selectedTabIndex = groups.indexOf(current.group), edgePadding = 8.dp) {
            groups.forEach { group ->
                Tab(
                    selected = group == current.group,
                    onClick = {
                        marked = ""
                        current = HelpTopic.entries.first { it.group == group }
                    },
                    text = { androidx.compose.material3.Text(group.title(s), maxLines = 1) },
                    icon = { Icon(group.icon, null, Modifier.size(20.dp)) },
                )
            }
        }
        val sections = HelpTopic.entries.filter { it.group == current.group }
        if (sections.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = sections.indexOf(current),
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                sections.forEach { section ->
                    Tab(
                        selected = section == current,
                        onClick = {
                            marked = ""
                            current = section
                        },
                        text = { androidx.compose.material3.Text(section.title(s), style = MaterialTheme.typography.labelLarge, maxLines = 1) },
                    )
                }
            }
        }

        // Каждый раздел начинается со своего начала.
        key(current) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopStart) {
                SelectionContainer {
                    Column(
                        Modifier.padding(16.dp).widthIn(max = 900.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        androidx.compose.material3.Text(
                            current.title(s),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        androidx.compose.material3.Text(marking(current.body(s), marked), style = MaterialTheme.typography.bodyLarge)
                        if (current.icons.isNotEmpty()) {
                            androidx.compose.material3.Text(
                                s.helpIconsTitle,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                            )
                            current.icons.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(item.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                    androidx.compose.material3.Text(marking(item.label(s), marked), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        Hint(s.helpFooter)
                    }
                }
            }
        }
    }
}

/** Кусок текста, в котором найденное выделено жирным. */
private fun highlighted(text: String, from: Int, to: Int): AnnotatedString = buildAnnotatedString {
    append(text)
    if (from >= 0 && to <= text.length && from < to) addStyle(SpanStyle(fontWeight = FontWeight.Bold), from, to)
}

/** Текст раздела, в котором выделено каждое вхождение [needle]. */
@Composable
private fun marking(text: String, needle: String): AnnotatedString {
    val accent = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    return buildAnnotatedString {
        var from = 0
        while (needle.isNotEmpty()) {
            val at = text.indexOf(needle, from, ignoreCase = true)
            if (at < 0) break
            append(text.substring(from, at))
            withStyle(accent) { append(text.substring(at, at + needle.length)) }
            from = at + needle.length
        }
        append(text.substring(from))
    }
}

/**
 * Справка поверх всего, что есть в окне (как в Notio): нажатая подсказка открывает её
 * на своём разделе. Она лежит над страницей и над открытым диалогом, поэтому её
 * закрытие ничего не теряет. В заголовке — версия приложения.
 */
@Composable
fun HelpOverlay() {
    if (!HelpLink.open) return
    val s = LocalStrings.current
    val titleBar = LocalTitleBar.current
    Dialog(onDismissRequest = HelpLink::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).then(titleBar?.modifier ?: Modifier).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAction(AppIcons.ArrowBack, s.back, HelpLink::close, topic = HelpTopic.HINTS)
                    Spacer(Modifier.width(4.dp))
                    androidx.compose.material3.Text(s.help, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Text(
                        s.versionLine(BuildInfo.VERSION),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width((titleBar?.endInset ?: 0.dp) + 12.dp))
                }
                HorizontalDivider()
                HelpScope(HelpTopic.HINTS) { HelpScreen(HelpLink.topic) }
            }
        }
    }
}
