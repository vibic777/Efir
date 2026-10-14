package ru.efir.core.comms

import kotlinx.coroutines.flow.Flow
import ru.efir.core.protocol.CallActionType
import ru.efir.core.protocol.CallStateChanged

/**
 * Платформенный источник состояния звонка (индикация входящих/активных вызовов)
 * и исполнитель действий над вызовом.
 *
 * На Android подписывается на системные события телефонии (PhoneStateListener)
 * и управляет вызовом через `TelecomManager`; на Desktop — пустой поток и
 * неподдерживаемые действия.
 */
expect class CallController() {
    /** Начинает прослушивание состояния звонка. */
    fun start()

    /** Прекращает прослушивание. */
    fun stop()

    /** Поток изменений состояния звонка. */
    fun states(): Flow<CallStateChanged>

    /**
     * Выполняет действие над текущим звонком.
     * @return `null` при успехе, иначе текст ошибки для удалённой стороны.
     */
    fun performAction(action: CallActionType): String?

    /**
     * Инициирует исходящий звонок на номер (требует `CALL_PHONE`).
     *
     * @param subscriptionId выбранная SIM-подписка (двойная SIM); `null` — по умолчанию.
     * @return `null` при успехе, иначе текст ошибки для удалённой стороны.
     */
    fun initiateCall(number: String, subscriptionId: Int? = null): String?
}
