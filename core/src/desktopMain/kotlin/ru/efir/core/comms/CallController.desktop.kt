package ru.efir.core.comms

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.efir.core.protocol.CallActionType

// Desktop-заглушка: системных событий телефонии нет.

actual class CallController actual constructor() {
    actual fun start() = Unit

    actual fun stop() = Unit

    actual fun states(): Flow<ru.efir.core.protocol.CallStateChanged> = emptyFlow()

    actual fun performAction(action: CallActionType): String? =
        "Управление звонками доступно только на Android"

    actual fun initiateCall(number: String, subscriptionId: Int?): String? =
        "Исходящие звонки доступны только на Android"
}
