package ru.efir.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import ru.efir.core.ui.i18n.RuStrings
import ru.efir.core.ui.i18n.Strings

/** Тексты интерфейса на выбранном языке. */
val LocalStrings = staticCompositionLocalOf<Strings> { RuStrings }

/** Раздел справки, к которому относится экран вокруг: туда ведёт нажатие на подсказку. */
val LocalHelpTopic = compositionLocalOf { HelpTopic.DEVICES }

/** Открывает справку на разделе. */
val LocalOpenHelp = staticCompositionLocalOf<(HelpTopic) -> Unit> { {} }

/** Место под заголовок окна: верхняя панель приложения сама служит заголовком (ПК, как в Notio). */
class TitleBarSpec(
    /** Перетаскивание окна за панель, двойной щелчок и системное меню. */
    val modifier: Modifier,
    /** Сколько оставить справа под системные кнопки «свернуть / развернуть / закрыть». */
    val endInset: Dp,
    /** Тема сменилась: системным кнопкам нужны светлые или тёмные значки. */
    val onTheme: (dark: Boolean) -> Unit = {},
)

/** Задан, когда окно рисует заголовок само; иначе панель — обычная строка под системным заголовком. */
val LocalTitleBar = staticCompositionLocalOf<TitleBarSpec?> { null }

/** Переключатель платформы для вкладки «Общие» настроек (трей на ПК, работа в фоне на телефоне). */
class ExtraSwitch(
    val title: (Strings) -> String,
    val hint: (Strings) -> String,
    val checked: Boolean,
    val onChange: (Boolean) -> Unit,
)

val LocalExtraSettings = compositionLocalOf<List<ExtraSwitch>> { emptyList() }

/** Вкладка экрана: значок, название и, если есть, число (сколько найдено, сколько не выдано). */
class TabItem(val icon: ImageVector, val title: String, val count: Int? = null)

/**
 * Вкладки внутри экрана: вместо длинной страницы из всех разделов сразу — по
 * одному разделу на вкладку. На узком экране полоса прокручивается вбок.
 */
@Composable
fun ScreenTabs(tabs: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val name = if (tab.count != null) "${tab.title} (${tab.count})" else tab.title
            HoverTip(name) {
                androidx.compose.material3.FilterChip(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    leadingIcon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(name, maxLines = 1) },
                    modifier = Modifier.semantics {
                        this.selected = index == selected
                        role = Role.Tab
                    },
                )
            }
        }
    }
}

/**
 * Кнопка-значок: [label] читает экранный диктор и показывает подсказка, так
 * что значок никого не оставляет гадать. Кнопки диалогов, редкие и опасные
 * действия и кнопки, показывающие значение, остаются словами.
 */
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    tonal: Boolean = false,
    topic: HelpTopic? = null,
) {
    HoverTip(label, topic = topic) {
        if (tonal) {
            FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
                Icon(icon, contentDescription = label)
            }
        } else {
            IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
                Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.38f))
            }
        }
    }
}

/** Один вариант из нескольких, показанный значком: выбранный залит основным цветом. */
@Composable
fun IconChoice(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    HoverTip(label) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.semantics {
                this.selected = selected
                role = Role.RadioButton
            },
            colors = if (selected) {
                IconButtonDefaults.filledIconButtonColors()
            } else {
                IconButtonDefaults.filledTonalIconButtonColors()
            },
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

/** Карточка раздела экрана: значок, заголовок, действия справа и содержимое. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** Раздел справки для всего, что в карточке, когда он не тот, что у экрана вокруг. */
    topic: HelpTopic? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) = HelpScope(topic ?: LocalHelpTopic.current) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                actions()
            }
            Column(
                Modifier.padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/** Пояснение мелким приглушённым текстом. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Сообщение об ошибке. */
@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(AppIcons.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Путь, идентификатор или иной «машинный» текст моноширинным шрифтом. */
@Composable
fun Mono(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Цветная точка состояния (подключено / нет). */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(color, CircleShape))
}

/** Пустое состояние экрана: значок, заголовок, пояснение и, если есть, действие. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action?.invoke()
    }
}

/** Строка настройки: название, пояснение и переключатель. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Hint(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        // Подсказка переключателя — его название: пояснение уже написано рядом.
        HoverTip(title) { Switch(checked = checked, onCheckedChange = onChange) }
    }
}

/** Строка списка: значок, две строки текста и действия справа. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = iconTint)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** Задаёт раздел справки для всего, что внутри. */
@Composable
fun HelpScope(topic: HelpTopic, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHelpTopic provides topic, content = content)
}
