package ru.efir.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolCodecTest {

    private fun roundTrip(body: Message): Message {
        val envelope = Envelope(
            id = MessageIds.new(),
            timestamp = 123456789L,
            body = body,
        )
        val json = ProtocolCodec.encode(envelope)
        return ProtocolCodec.decode(json).body
    }

    @Test
    fun roundTripAllMessages() {
        val samples: List<Message> = listOf(
            // Общие
            Ack(),
            ErrorMessage(code = "e", message = "err"),
            Ping(nonce = 42L),
            Pong(nonce = 42L),
            DeviceInfoChanged(
                DeviceInfo(
                    deviceId = "d1",
                    name = "Pixel",
                    type = DeviceType.ANDROID,
                    capabilities = setOf(Capability.SCREEN_CAPTURE, Capability.CLIPBOARD),
                ),
            ),
            RequestCapabilities,
            CapabilitiesResult(setOf(Capability.FILE_SYNC)),
            // Handshake
            Hello(
                device = DeviceInfo("w1", "PC", DeviceType.WINDOWS),
                sessionToken = "s",
                pairToken = "p",
            ),
            HelloAck(
                device = DeviceInfo("d1", "Pixel", DeviceType.ANDROID),
                accepted = true,
                sessionToken = "s",
            ),
            PairRequest(pairingToken = "t", accessKey = "A1B2-C3D4-E5F6", device = DeviceInfo("w1", "PC", DeviceType.WINDOWS)),
            PairResponse(accepted = true, sessionToken = "s"),
            // Ввод
            InputTap(x = 0.5f, y = 0.25f, durationMs = 60),
            InputSwipe(fromX = 0.1f, fromY = 0.1f, toX = 0.9f, toY = 0.9f, durationMs = 300),
            InputMultiTouch(strokes = listOf(TouchStroke(listOf(TouchPoint(0.1f, 0.2f)), 0, 100))),
            InputText(text = "привет"),
            InputKey(action = KeyAction.BACK),
            // Файлы
            FsList(path = "/sdcard", offset = 0, limit = 100),
            FsListResult(path = "/sdcard", entries = listOf(RemoteEntry("a.txt", "/sdcard/a.txt", false, 10, 1000))),
            FsStat(path = "/x"),
            FsStatResult(RemoteEntry("x", "/x", true, 0, 0)),
            FsDelete(paths = listOf("/a"), recursive = true),
            FsRename(path = "/a", newName = "b"),
            FsMkdir(path = "/new"),
            // Передачи
            TransferInit(transferId = "t1", path = "/a", size = 100, kind = TransferKind.UPLOAD),
            TransferAccepted(transferId = "t1", resumeFrom = 0),
            TransferComplete(transferId = "t1"),
            TransferVerified(transferId = "t1", ok = true),
            TransferProgress(transferId = "t1", bytes = 50, size = 100),
            TransferAbort(transferId = "t1"),
            // Синхронизация
            SyncPlanRequest(SyncRuleConfig("r1", "rule", "/local", "/remote")),
            SyncPlanResult(ruleId = "r1", actions = listOf(SyncAction(SyncActionType.ADD, SyncSide.LOCAL, "f"))),
            SyncApplyRequest(ruleId = "r1", actions = emptyList()),
            SyncStatus(ruleId = "r1", running = true),
            SyncConflictEvent(ruleId = "r1", conflicts = emptyList()),
            // Медиа
            ScreenStart(),
            ScreenStarted(sessionId = "s1", width = 1280, height = 720, codec = "h264"),
            ScreenStop,
            ScreenStopped(sessionId = "s1"),
            CameraStart(),
            CameraStarted(sessionId = "c1", width = 1920, height = 1080, codec = "h264"),
            CameraStop,
            CameraStopped(sessionId = "c1"),
            AudioStart(),
            AudioStarted(sessionId = "a1", sampleRate = 48000, channels = 1, codec = "aac"),
            AudioStop,
            AudioStopped(sessionId = "a1"),
            // Коммуникации
            ClipboardSet(text = "hi"),
            ClipboardGet,
            ClipboardData(text = "data"),
            ClipboardChanged(text = "data"),
            NotificationPosted(id = "n1", packageName = "pkg", postedAt = 1L),
            NotificationRemoved(id = "n1"),
            NotificationDismiss(id = "n1"),
            NotificationReply(id = "n1", text = "ok"),
            NotificationActionResult(id = "n1", success = true),
            SmsList(threadId = 1L),
            SmsListResult(messages = listOf(SmsMessage(1, 1, "+7900", "text", 1L, SmsType.INBOX))),
            SmsSend(address = "+7900", body = "hi", subscriptionId = 2),
            SmsReceived(SmsMessage(1, 1, "+7900", "text", 1L, SmsType.INBOX)),
            SmsSubscriptions,
            SmsSubscriptionsResult(listOf(SmsSubscription(subscriptionId = 1, isDefault = true))),
            SmsThreads(limit = 10),
            SmsThreadsResult(listOf(SmsThread(threadId = 1, address = "+7900", snippet = "hi"))),
            SmsDeliveryStatusChanged("+7900", SmsDeliveryStatus.DELIVERED),
            CallLogList(limit = 10),
            CallLogListResult(listOf(CallLogEntry(id = 1, number = "+7900", date = 1L, type = "INCOMING"))),
            CallStateChanged(state = CallState.RINGING, number = "+7900"),
            CallAction(action = CallActionType.ANSWER),
            CallActionResult(ok = true),
            CallInitiate(number = "+7900", subscriptionId = 1),
        )

        samples.forEach { sample ->
            val decoded = roundTrip(sample)
            assertEquals(sample, decoded, "Несовпадение при round-trip для ${sample::class.simpleName}")
        }
    }

    @Test
    fun discriminatorIsType() {
        val envelope = Envelope(id = "x", timestamp = 1L, body = Ping(nonce = 1L))
        val json = ProtocolCodec.encode(envelope)
        assertTrue(json.contains("\"type\":\"ping\""), "Ожидался дискриминатор type=ping, получено: $json")
    }

    @Test
    fun messageIdsAreUniqueAndSized() {
        val ids = List(1000) { MessageIds.new() }
        assertEquals(1000, ids.toSet().size)
        assertTrue(ids.all { it.length == 24 })
    }
}
