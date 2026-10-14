# Протокол Эфир

Документ описывает транспорт, каналы и формат сообщений между Windows-клиентом и
Android-хостом.

## Транспорт

- **Discovery:** UDP-бродкаст + mDNS-сервис `_efir._tcp` в локальной сети.
- **Control-канал:** WebSocket (RPC) поверх TLS.
- **Бинарные каналы:** SCREEN, CAMERA, AUDIO, FILES — поверх того же соединения.
- **Шифрование:** TLS с самоподписанными сертификатами; доверие — TOFU + пиннинг
  отпечатка SHA-256. На время отладки TLS может быть отключён (`TlsConfig.enabled = false`).

## Конверт и RPC

Каждое сообщение оборачивается в `Envelope`:

- `id` — уникальный идентификатор;
- `replyTo` — id запроса для ответа;
- `channel` — логический канал (`CONTROL`/`SCREEN`/`CAMERA`/`AUDIO`/`FILES`);
- `timestamp`;
- `body` — одно из сообщений (полиморфная сериализация по дискриминатору `type`).

Типы сообщений: `RpcRequest` (ожидает ответ), `RpcResponse`, `Event` (fire-and-forget).

Кодек — JSON (`ProtocolCodec`), версия протокола `PROTOCOL_VERSION`.

## Группы сообщений

| Группа | Файл | Примеры |
| --- | --- | --- |
| Handshake | `HandshakeMessages.kt` | `Hello`, `HelloAck`, capabilities |
| Файлы | `FileMessages.kt` | `fsList`, `fsStat`, `fsDelete`, `fsRename`, `fsMkdir`, `fsReadBlock` |
| Передача | `TransferMessages.kt` | `transferInit/Accepted/Chunk/Complete/Verified` |
| Синхронизация | `SyncMessages.kt` | `syncPlanRequest`, `syncPlanResult`, `syncApplyRequest`, `syncStatus` |
| Медиа | `MediaMessages.kt` | `screenStart/Stop`, `screenEnded` (событие), `cameraStart/Stop`, `audioStart/Stop` |
| Ввод | `InputMessages.kt` | `inputTap`, `inputSwipe`, `inputKey`, `inputText`, `inputMultiTouch` |
| Коммуникации | `CommsMessages.kt` | `clipboardSet/Get/Changed`, `notificationPosted/Removed/Dismiss/Reply`, `smsList/Send/Threads/Subscriptions`, `smsDeliveryStatus`, `callLogList`, `callAction`, `callInitiate` |

## SMS и звонки

- **Чтение**: `smsList` (тред/все), `smsThreads` (диалоги). Провайдер `Telephony.Sms`.
- **Отправка**: `smsSend` с полями `subscriptionId` (выбор SIM при двойной SIM) и
  `deliveryReport` (запрос статуса). Хост отвечает `smsListResult` либо `ErrorMessage`
  (`sms_send_failed`).
- **Реал-тайм приём**: broadcast `SMS_RECEIVED`/`SMS_DELIVER` → событие `smsReceived`.
- **Статусы доставки**: `SmsManager` sent/delivery `PendingIntent` →
  [`EfirSmsStatusReceiver`](../core/src/androidMain/kotlin/ru/efir/core/comms/EfirSmsStatusReceiver.kt)
  → событие `smsDeliveryStatus` (`SENT`/`DELIVERED`/`FAILED`).
- **SIM-подписки**: `smsSubscriptions` → `smsSubscriptionsResult`.
- **Звонки**: `PhoneStateListener` → `callStateChanged` (`IDLE`/`RINGING`/`OFFHOOK`,
  флаг `incoming`); действия с ПК — `callAction` (`ANSWER`/`REJECT`/`END`), ответ —
  `callActionResult`. На Android исполняется через `TelecomManager`.
- **Исходящие звонки**: `callInitiate` с полями `number` и `subscriptionId` (выбор
  SIM при двойной SIM); хост набирает номер через `TelecomManager.placeCall`
  (требует `CALL_PHONE`), ответ — `callActionResult`.
- **Реал-тайм треды SMS**: входящее SMS обновляет сводку диалога на ПК (сниппет,
  счётчик, время) без полной перезагрузки (`groupSmsThreads`/`mergeSmsThread`).
- **Роль default SMS app**: для записи в «Отправленные» и отчётов о доставке
  требуется роль приложения SMS по умолчанию (запрос через
  `Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT`).

## Бинарные кадры

Медиа-кадры кодируются `MediaFrameCodec` (magic `MF`): тип (SCREEN/CAMERA/AUDIO),
sessionId, sequence, timestamp, размеры/частоту, MIME/кодек и полезную нагрузку.
Байт `flags` кодирует `bit0` — ключевой кадр и `bit1` — кадр конфигурации кодека
(CSD: SPS/PPS для `video/avc`), который получатель накапливает, но не отображает.

### Видео-кодек

Кадры изображений несут `mimeType`:

- `image/jpeg` — совместимый программный поток (по умолчанию);
- `video/avc` — аппаратно закодированный H.264 (Annex-B NAL-единицы).

Хост кодирует H.264 через `MediaCodec` при `codec = "h264"` в
`screenStart`/`cameraStart` (opt-in). Клиент декодирует поток: на ПК — чистый
JVM-декодер (`VideoFrameDecoder`, JCodec + Skia), на Android-хосте не требуется.
Если хост/устройство не поддерживает Surface-кодирование, автоматически
используется JPEG-путь.

Файловые чанки кодируются `TransferFrameCodec` (magic `TF`): DATA/ACK, transferId,
seq, offset, payload. Мультиплексирование по префиксу: `TF` → файловый канал,
иначе → медиа-канал.

## Адаптивное качество потока

`screenStart`/`cameraStart` содержат поля качества: `bitrate`, `fps`, `jpegQuality`
и флаг `adaptive` (по умолчанию `true`). При включённом `adaptive` хост оценивает
фактическую скорость отдачи по скользящему окну (`AdaptiveBitrateController`) и
корректирует качество JPEG/FPS прямо в горячем пути:

- перегрузка → шаг вниз (сначала масштаб, затем JPEG, затем FPS);
- недогруз → шаг вверх (сначала FPS, затем JPEG, затем масштаб);
- гистерезис `[0.6; 1.15]` от целевого битрейта исключает «дребезг».

Изменения применяются через `ScreenCaptureSource.setEncoderQuality` /
`CameraCaptureSource.setEncoderQuality` (на Android — без пересоздания сессии).

## Сопряжение

Сопряжение **симметрично**: любое устройство (Android или Windows) может как ждать
подключения, так и инициировать его. Роль определяется направлением соединения, а не
платформой.

1. Ждущая сторона поднимает сервер, генерирует одноразовый `pairingToken` и
   **ключ доступа** из 12 символов `0-9A-Z` (отображается группами по 4 через дефис:
   `A1B2-C3D4-E5F6`) и показывает QR (`PairingPayload`) либо JSON-текст.
2. Подключающаяся сторона считывает QR/JSON и отправляет `pairingToken` + ключ доступа
   (`pairRequest`). Ключ сравнивается регистронезависимо и игнорируя дефисы/пробелы.
3. Ждущая сторона проверяет токен и ключ, сохраняет доверенное устройство и выдаёт
   `sessionToken`.
4. Повторные подключения — по `sessionToken` в `hello` без QR и ключа.

Ручной вариант: подключающаяся сторона указывает `IP:порт` и (при первичном сопряжении)
вставляет JSON данных сопряжения и ключ доступа.

## Показ экрана: отказ и окончание

Экран показывает любая из сторон: телефон (MediaProjection) и компьютер (снимки основного монитора, JPEG).

- На `screenStart` показывающая сторона отвечает `screenStarted` либо `error` с кодом причины в `code`:
  `screen_permission_needed`, `screen_refused`, `screen_disabled`, `screen_unavailable`, `screen_failed`
  (`StreamErrors`). Код, а не фраза: каждая сторона показывает причину на своём языке.
- Ответа ждут до 60 секунд: телефону нужно дать человеку ответить на вопрос Android (сам телефон ждёт 45 секунд).
- Событие `screenEnded { sessionId, reason }` шлёт показывающая сторона, когда показ закончила она сама: его остановили
  на устройстве (`screen_stopped`), выключили в настройках (`screen_disabled`) или захват сорвался (`screen_failed`).
  Смотрящая сторона убирает картинку. Кадры, пришедшие после события, отбрасываются.
- Событие добавлено в 0.4.0 без смены `PROTOCOL_VERSION`: обе стороны обновляются вместе.

## Версионирование

При handshake стороны обмениваются версией протокола и набором возможностей
(`Capability`). При несовпадении мажорной версии соединение отклоняется.
