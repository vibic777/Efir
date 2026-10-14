package ru.efir.core.comms

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.efir.core.media.EfirAndroidEnvironment
import ru.efir.core.protocol.CallActionType
import ru.efir.core.protocol.CallState
import ru.efir.core.protocol.CallStateChanged

/**
 * Android-источник состояния звонка на базе `PhoneStateListener` и исполнитель
 * действий над вызовом через `TelecomManager`.
 *
 * Публикует события при смене состояния линии (IDLE/RINGING/OFFHOOK).
 * Принятие входящего (`ANSWER`) требует `ANSWER_PHONE_CALLS` (API 26+);
 * завершение/отклонение (`END`/`REJECT`) — `ANSWER_PHONE_CALLS` на API 28+.
 */
actual class CallController actual constructor() {

    private val states = MutableSharedFlow<CallStateChanged>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var listener: PhoneStateListener? = null
    private var started = false
    private var lastState: CallState = CallState.IDLE
    private var incoming = true

    actual fun start() {
        if (started) return
        val context = EfirAndroidEnvironment.appContext ?: return
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        val l = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handle(state, phoneNumber)
            }
        }
        runCatching {
            @Suppress("DEPRECATION")
            telephony.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            listener = l
            started = true
        }
    }

    actual fun stop() {
        val context = EfirAndroidEnvironment.appContext ?: return
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        listener?.let { l ->
            runCatching {
                @Suppress("DEPRECATION")
                telephony?.listen(l, PhoneStateListener.LISTEN_NONE)
            }
        }
        listener = null
        started = false
    }

    actual fun states(): Flow<CallStateChanged> = states.asSharedFlow()

    actual fun performAction(action: CallActionType): String? {
        val context = EfirAndroidEnvironment.appContext ?: return "Нет доступа к контексту Android"
        val telecom = context.getSystemService(TelecomManager::class.java)
            ?: return "Телефония недоступна"
        return runCatching {
            when (action) {
                CallActionType.ANSWER -> {
                    @Suppress("DEPRECATION")
                    telecom.acceptRingingCall()
                    null
                }

                CallActionType.REJECT, CallActionType.END -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        @Suppress("DEPRECATION")
                        telecom.endCall()
                        null
                    } else {
                        "Завершение звонка требует Android 9+ или системных привилегий"
                    }
                }
            }
        }.getOrElse { error ->
            error.message ?: "Не удалось выполнить действие над звонком"
        }
    }

    actual fun initiateCall(number: String, subscriptionId: Int?): String? {
        val context = EfirAndroidEnvironment.appContext ?: return "Нет доступа к контексту Android"
        if (number.isBlank()) return "Не указан номер для звонка"
        val telecom = context.getSystemService(TelecomManager::class.java)
            ?: return "Телефония недоступна"
        return runCatching {
            val extras = Bundle().apply {
                // `placeCall` доступен с API 23 (minSdk 26), SIM-слот опционален.
                subscriptionId?.let {
                    putInt(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
                }
            }
            telecom.placeCall(Uri.fromParts("tel", number, null), extras)
            null
        }.getOrElse { error ->
            error.message ?: "Не удалось инициировать звонок"
        }
    }

    private fun handle(systemState: Int, number: String?) {
        val state = when (systemState) {
            TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> CallState.OFFHOOK
            else -> CallState.IDLE
        }
        if (state == lastState) return
        if (state == CallState.RINGING) incoming = true
        else if (state == CallState.OFFHOOK && lastState == CallState.IDLE) incoming = false
        lastState = state
        states.tryEmit(
            CallStateChanged(
                state = state,
                number = number?.takeIf { it.isNotBlank() },
                incoming = incoming,
            ),
        )
    }
}
