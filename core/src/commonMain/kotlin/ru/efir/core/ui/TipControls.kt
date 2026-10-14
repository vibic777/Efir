package ru.efir.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import ru.efir.core.pairing.SecureTokens
import ru.efir.core.protocol.nowMillis

/*
 * Подсказка есть у каждого элемента, которым управляют: у кнопки, поля ввода,
 * переключателя, варианта выбора. Button, OutlinedButton, TextButton,
 * OutlinedTextField, FilterChip, Switch и Text этого файла стоят в экранах на
 * месте одноимённых из Material 3: это они же внутри подсказки. Текст подсказки —
 * параметр `tip`; где его нет, подсказкой служит надпись самого элемента (её
 * сообщает изнутри Text этого файла), так что без подсказки не остаётся ничего.
 */

/** Сколько секунд подсказка остаётся на экране; меняется в «Настройках». Ноль — подсказки выключены. */
object TipSettings {
    const val PREF = "tipSeconds"
    const val DEFAULT_SECONDS = 5

    /** Варианты для «Настроек». */
    val CHOICES = listOf(0, 3, 5, 10, 20)

    var seconds by mutableIntStateOf(DEFAULT_SECONDS)
}

/** Куда надпись внутри элемента сообщает свой текст — для его подсказки. */
private val LocalHint = compositionLocalOf<MutableState<String?>?> { null }

/** Глубина вложенности подсказок: у поля ввода есть своя, у значка внутри него — своя. */
private val LocalTipDepth = compositionLocalOf { 0 }

private class ShownTip(val owner: Any, val depth: Int, val at: Long)

/** Чья подсказка сейчас показана: одна на всё окно, самая новая. */
private val shownTip = mutableStateOf<ShownTip?>(null)

private const val HOVER_MILLIS = 500L

/** Вложенный элемент показал подсказку только что: внешний свою поверх не ставит. */
private const val NESTED_MILLIS = 300L

@Composable
private fun tell(text: String?) {
    val hint = LocalHint.current ?: return
    if (!text.isNullOrBlank()) SideEffect { if (hint.value == null) hint.value = text }
}

/** Подсказка с готовым текстом — см. [HoverTip] ниже. */
@Composable
fun HoverTip(
    text: String?,
    modifier: Modifier = Modifier,
    topic: HelpTopic? = null,
    content: @Composable () -> Unit,
) = HoverTip({ text }, modifier, topic, true, content)

/**
 * Подсказка к элементу, как в Notio: всплывает, когда курсор задержался на нём
 * или палец удерживает его, остаётся на [TipSettings.seconds] секунд — и когда
 * курсор уже ушёл, чтобы до неё можно было дотянуться, — а нажатие на саму
 * подсказку открывает справку на разделе [topic] (по умолчанию — раздел экрана).
 * Удержание, вызвавшее подсказку, элемент не нажимает.
 *
 * Текст спрашивается только под курсором или пальцем: пока к элементу не
 * подошли, здесь ничего не работает. Размер, который дали этой обёртке,
 * она передаёт содержимому, поэтому элемент внутри заполняет, взвешивается и
 * выравнивается так же, как без неё.
 *
 * @param touchHold показывать ли по удержанию пальца (в поле ввода удержание — это выделение текста).
 */
@Composable
fun HoverTip(
    name: () -> String?,
    modifier: Modifier = Modifier,
    topic: HelpTopic? = null,
    touchHold: Boolean = true,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    val hint by rememberUpdatedState(name)
    val me = remember { Any() }
    val depth = LocalTipDepth.current
    val openHelp = LocalOpenHelp.current
    val target = topic ?: LocalHelpTopic.current

    fun show() {
        if (TipSettings.seconds <= 0 || hint().isNullOrBlank()) return
        val now = nowMillis()
        val current = shownTip.value
        if (current != null && current.owner !== me && current.depth > depth && now - current.at < NESTED_MILLIS) return
        shownTip.value = ShownTip(me, depth, now)
        visible = true
    }

    if (hovered) {
        LaunchedEffect(Unit) {
            delay(HOVER_MILLIS)
            show()
        }
    }

    Box(
        modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val mouse = event.changes.none { it.type == PointerType.Touch }
                        when (event.type) {
                            PointerEventType.Enter -> if (mouse) hovered = true
                            PointerEventType.Exit -> hovered = false
                            PointerEventType.Press -> if (mouse) {
                                hovered = false
                                visible = false
                            }
                        }
                    }
                }
            }
            .pointerInput(touchHold) {
                if (!touchHold) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.type != PointerType.Touch || hint().isNullOrBlank()) return@awaitEachGesture
                    val held = try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation(PointerEventPass.Initial)
                        }
                        false
                    } catch (_: PointerEventTimeoutCancellationException) {
                        true
                    }
                    if (held) {
                        show()
                        // Остаток касания принадлежит подсказке: отпускание не нажимает элемент.
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            },
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalTipDepth provides depth + 1, content = content)
        val shown = if (visible && shownTip.value?.owner === me) hint() else null
        if (!shown.isNullOrBlank()) {
            val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap) { AboveAnchor(gap) },
                onDismissRequest = { visible = false },
            ) {
                Surface(
                    onClick = {
                        visible = false
                        openHelp(target)
                    },
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.material3.Text(
                            shown,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        // Знак «об этом есть ещё»: подсказка ведёт в справку.
                        Icon(
                            AppIcons.Help,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            LaunchedEffect(Unit) {
                delay(TipSettings.seconds.coerceAtLeast(1) * 1_000L)
                visible = false
            }
        }
    }
}

/** Над элементом по центру; под ним, если сверху нет места; никогда не за краем окна. */
private class AboveAnchor(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - gap - popupContentSize.height
        val y = if (above >= 0) {
            above
        } else {
            (anchorBounds.bottom + gap).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        }
        return IntOffset(x, y)
    }
}

/**
 * Элемент с подсказкой: [tip], а без него — надпись, которую элемент сообщит изнутри.
 * [modifier] получает обёртка, а не элемент: так он занимает в строке то же место.
 */
@Composable
fun Hinted(
    tip: String?,
    modifier: Modifier = Modifier,
    topic: HelpTopic? = null,
    touchHold: Boolean = true,
    content: @Composable () -> Unit,
) {
    // На другом языке надпись другая: сказанное раньше забывается.
    val told = remember(LocalStrings.current) { mutableStateOf<String?>(null) }
    HoverTip({ tip ?: told.value }, modifier, topic, touchHold) {
        CompositionLocalProvider(LocalHint provides told, content = content)
    }
}

/** Text из Material 3, который заодно сообщает свою надпись подсказке элемента вокруг. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) {
    tell(text)
    androidx.compose.material3.Text(
        text, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration,
        textAlign, lineHeight, overflow, softWrap, maxLines, minLines, style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    tell(text.text)
    androidx.compose.material3.Text(
        text, modifier, color, textAlign = textAlign, overflow = overflow, maxLines = maxLines, style = style,
    )
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tip: String? = null,
    topic: HelpTopic? = null,
    content: @Composable RowScope.() -> Unit,
) = Hinted(tip, modifier, topic) {
    androidx.compose.material3.Button(onClick = onClick, enabled = enabled, content = content)
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tip: String? = null,
    topic: HelpTopic? = null,
    content: @Composable RowScope.() -> Unit,
) = Hinted(tip, modifier, topic) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, enabled = enabled, content = content)
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tip: String? = null,
    topic: HelpTopic? = null,
    content: @Composable RowScope.() -> Unit,
) = Hinted(tip, modifier, topic) {
    androidx.compose.material3.TextButton(onClick = onClick, enabled = enabled, content = content)
}

/** Поле ввода; подсказка — [tip] или подпись поля. Удержание пальца в поле остаётся выделением текста. */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    tip: String? = null,
    topic: HelpTopic? = null,
) = Hinted(tip, modifier, topic, touchHold = false) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tip: String? = null,
    topic: HelpTopic? = null,
) = Hinted(tip, modifier, topic) {
    androidx.compose.material3.FilterChip(selected = selected, onClick = onClick, label = label, enabled = enabled)
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tip: String? = null,
    topic: HelpTopic? = null,
) = Hinted(tip, modifier, topic) {
    androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
}

/** Enter (и Enter цифрового блока) в поле выполняет [action] — то же, что главная кнопка окна. */
fun Modifier.onEnter(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && event.type == KeyEventType.KeyDown) {
        action()
        true
    } else {
        false
    }
}

/**
 * Где встать курсору в ключе, записанном группами (`A1B2-C3D4-E5F6`), если перед
 * курсором [keyChars] символов самого ключа: сразу после последнего из них.
 */
fun accessKeyCursor(formatted: String, keyChars: Int): Int {
    if (keyChars <= 0) return 0
    var seen = 0
    formatted.forEachIndexed { index, ch ->
        if (ch != '-') {
            seen++
            if (seen == keyChars) return index + 1
        }
    }
    return formatted.length
}

/**
 * Поле ключа доступа. Ключ на лету приводится к виду `XXXX-XXXX-XXXX`, и курсор
 * при этом ставится явно — после того символа, за которым он стоял: текст,
 * изменённый под рукой у обычного поля, оставлял курсор перед введённым символом.
 * Подсказки и автозамена клавиатуры выключены: ключ — не слово.
 */
@Composable
fun AccessKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    tip: String? = null,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Ключ поменяли снаружи (очистили): поле следует за ним, курсор — в конец.
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))

    Hinted(tip, modifier, touchHold = false) {
        androidx.compose.material3.OutlinedTextField(
            value = field,
            onValueChange = { input ->
                val limit = SecureTokens.ACCESS_KEY_LENGTH
                val before = SecureTokens.normalizeAccessKey(input.text.take(input.selection.end)).length.coerceAtMost(limit)
                val formatted = SecureTokens.formatAccessKey(SecureTokens.normalizeAccessKey(input.text).take(limit))
                field = TextFieldValue(formatted, TextRange(accessKeyCursor(formatted, before)))
                onValueChange(formatted)
            },
            label = { androidx.compose.material3.Text(label) },
            placeholder = { androidx.compose.material3.Text("XXXX-XXXX-XXXX") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        )
    }
}
