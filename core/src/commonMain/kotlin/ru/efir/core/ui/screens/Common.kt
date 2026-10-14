package ru.efir.core.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import ru.efir.core.ui.OutlinedTextField
import ru.efir.core.ui.Text
import ru.efir.core.ui.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.efir.core.protocol.DeviceType
import ru.efir.core.ui.AppIcons
import ru.efir.core.ui.HelpScope
import ru.efir.core.ui.HelpTopic
import ru.efir.core.ui.LocalStrings
import ru.efir.core.ui.i18n.Strings

/** Каркас экрана: прокручиваемая колонка карточек ограниченной ширины и раздел справки для подсказок. */
@Composable
fun ScreenColumn(topic: HelpTopic, content: @Composable ColumnScope.() -> Unit) = HelpScope(topic) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Значок устройства по его типу. */
fun deviceIcon(type: DeviceType): ImageVector = when (type) {
    DeviceType.ANDROID -> AppIcons.Smartphone
    else -> AppIcons.Computer
}

/** Короткое название стороны по типу устройства («ПК» / «Телефон»). */
fun Strings.sideName(type: DeviceType): String = when (type) {
    DeviceType.ANDROID -> sidePhone
    DeviceType.WINDOWS -> sidePc
    DeviceType.UNKNOWN -> sideDevice
}

/** Название платформы. */
fun Strings.platformName(type: DeviceType): String = when (type) {
    DeviceType.ANDROID -> "Android"
    DeviceType.WINDOWS -> "Windows"
    DeviceType.UNKNOWN -> unknown
}

/** Размер в Б/КБ/МБ/ГБ/ТБ на языке интерфейса. */
fun Strings.bytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes ${byteUnits[0]}"
    var value = bytes.toDouble() / 1024
    var index = 1
    while (value >= 1024 && index < byteUnits.lastIndex) {
        value /= 1024
        index++
    }
    val rounded = (value * 10).toLong() / 10.0
    return "$rounded ${byteUnits[index]}"
}

private fun two(value: Int): String = if (value < 10) "0$value" else value.toString()

/** Дата и время по часам этого устройства: `2026-10-11 14:05`. */
fun formatDateTime(millis: Long): String {
    if (millis <= 0) return "—"
    val t = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.year}-${two(t.monthNumber)}-${two(t.dayOfMonth)} ${two(t.hour)}:${two(t.minute)}"
}

/** Диалог подтверждения опасного действия. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

/** Диалог с одним полем ввода (имя папки, новое имя файла). */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var value by remember { mutableStateOf(initial) }
    val valid = value.isNotBlank() && '/' !in value && '\\' !in value
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                isError = !valid,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = valid) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
