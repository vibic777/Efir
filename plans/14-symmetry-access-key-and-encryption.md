# План 14 — Симметричное сопряжение, ключ доступа и шифрование

## Цель

Привести блоки сопряжения, показа экрана, файлового менеджера и синхронизации
в соответствие с формулировками ТЗ: **обе версии равноправны** (любое устройство
может ждать подключение или инициировать его), вместо 6-значного PIN используется
**ключ доступа из 12 символов `0-9A-Z`**, трафик **реально шифруется**.

## Текущее состояние (аудит)

| Требование ТЗ | Что есть сейчас | Где |
| --- | --- | --- |
| Обе стороны могут ждать сопряжение / подключаться | Роль зашита в枚举: `ANDROID_HOST` / `WINDOWS_CLIENT`; QR показывает только Android | [`EfirRole`](core/src/commonMain/kotlin/ru/efir/core/app/EfirController.kt), [`HostPairingSection` / `ClientConnectSection`](core/src/commonMain/kotlin/ru/efir/core/app/EfirAppScreen.kt) |
| Ключ доступа 12 символов `0-9A-Z`, по 4 через дефис | 6 цифровых PIN (`SecureTokens.pin()`) | [`SecureRandom.kt`](core/src/commonMain/kotlin/ru/efir/core/pairing/SecureRandom.kt), [`PairingService`](core/src/commonMain/kotlin/ru/efir/core/pairing/PairingService.kt) |
| Мобильная в ожидании: JSON на экране + кнопка «показать QR» + «копировать в буфер» | Только QR + JSON-текст, без кнопок | `HostPairingSection` |
| Ручная настройка IP/порт + поиск | Есть ручной IP на клиенте, поиск (UDP) есть; у десктопа нет режима ожидания | `ClientConnectSection`, [`DiscoveryManager`](core/src/commonMain/kotlin/ru/efir/core/discovery/DiscoveryManager.kt) |
| Шифрование данных после рукопожатия | `TlsConfig(enabled = false)` на обеих сторонах, отпечаток не обменивается | [`EfirController.startHost`](core/src/commonMain/kotlin/ru/efir/core/app/EfirController.kt), [`JvmTls`](core/src/jvmCommon/kotlin/ru/efir/core/net/JvmTls.kt) |
| Сессию запоминать как доверенную, лёгкое переподключение | Реализовано (`TrustStore` + `Hello.sessionToken`) | [`SessionHandshake`](core/src/commonMain/kotlin/ru/efir/core/app/SessionHandshake.kt) |
| «Показать экран» работает с обеих сторон | Только Android → ПК | [`ScreenStreamHost`](core/src/commonMain/kotlin/ru/efir/core/media/ScreenStreamHost.kt) / [`MediaStreamClient`](core/src/commonMain/kotlin/ru/efir/core/media/MediaStreamClient.kt) |
| Захват мыши/ввода, для Android тоже | Ввод с ПК на Android есть; с Android на ПК — нет | [`InputInjectionHost`](core/src/commonMain/kotlin/ru/efir/core/input/InputInjectionHost.kt) |
| Файловый менеджер а-ля Total Commander (туда-сюда) | Одна панель: ПК浏览ит Android | [`FileManagerClient`](core/src/commonMain/kotlin/ru/efir/core/files/FileManagerClient.kt) |
| Синхронизация помеченных папок, автосинхронизация | Реализовано (движок, пресеты, планировщик, watcher) | [`SyncEngine`](core/src/commonMain/kotlin/ru/efir/core/sync/SyncEngine.kt) |
| Стратегии: замещение / слияние / добавление | Есть `OVERWRITE` / `MERGE` / `APPEND`, но не названы по-русски и не сгруппированы в явные 3 режима | [`SyncPresets`](core/src/commonMain/kotlin/ru/efir/core/sync/SyncPresets.kt) |
| Правило имён: `аааа` → `аааа-1`, затем `аааа-2`, свободное | Сейчас `аааа (1).ext` (пробел + скобки) | [`SyncPlanner.uniqueName`](core/src/commonMain/kotlin/ru/efir/core/sync/SyncPlanner.kt) |

## Итерации

### Итерация 1 — Ключ доступа вместо PIN ✅

- `SecureTokens.accessKey(12)` — 12 символов из алфавита `0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ`
  (36 символов ≈ 62 бита энтропии), всегда верхний регистр.
- `SecureTokens.formatAccessKey()` —/groups по 4 через дефис: `A1B2-C3D4-E5F6`.
- `SecureTokens.normalizeAccessKey()` — регистронезависимость и терпимость к дефисам/пробелам
  при ручном вводе.
- `PairingService.PendingPairing.pin` → `accessKey`; `verify(..., accessKey, ...)`.
- Протокол: `PairRequest.pin` → `PairRequest.accessKey`.
- UI: ждущая сторона показывает ключ крупно моноширинно; подключающаяся вводит
  ключ (поле автоматически форматирует группы по 4).

### Итерация 2 — Симметричные роли

- `EfirRole` → `enum { HOST, CLIENT }` (роль — это состояние сессии, а не тип платформы);
  `DeviceType` уже различает ANDROID/WINDOWS.
- Каждая сторона умеет и `beginPairing()` (ждёт), и `connectTo(...)` (подключается).
- Ждущая сторона: QR + JSON + кнопки «Показать QR» / «Копировать JSON в буфер».
- Подключающаяся сторона: «Навести камеру» (скан QR), «Вставить JSON», «Вручную IP:порт»,
  плюс список найденных устройств.
- Ручные IP/порт — на обеих сторонах.

### Итерация 3 — Режим «сервис-переключатель»

- Переключатель «Постоянно ждать подключения» на обеих сторонах.
- Android: foreground-сервис (уже есть [`ScreenCaptureService`](androidApp/src/main/kotlin/ru/efir/android/ScreenCaptureService.kt))
  держит `EfirHost` поднятым; Desktop: трей-режим / фоновое окно.
- При включённом режиме хост стартует в `start()`, а не по вызову `beginPairing()`.

### Итерация 4 — Шифрование (TLS)

- Самоподписанный сертификат генерируется один раз и хранится в `dataDir`
  (`~/.efir/` на ПК, `filesDir` на Android); отпечаток SHA-256 кладётся в `PairingPayload`.
- Сервер: Ktor CIO + TLS-контекст с этим сертификатом; клиент: pinning-`TrustManager`
  (уже готово в `JvmTls.pinningManager`).
- `TlsConfig(enabled = true)` по умолчанию; при сопряжении отпечаток проверяется
  автоматически из QR/JSON.
- После успешного сопряжения `sessionToken` — общий секрет, дальше доверие по пиннингу.

### Итерация 5 — «Показать экран» в обе стороны

- Desktop-захват уже есть ([`ScreenCapture.desktop.kt`](core/src/desktopMain/kotlin/ru/efir/core/media/ScreenCapture.desktop.kt));
  нужно поднять `ScreenStreamHost` на десктопе и `MediaStreamClient` на Android.
- Ввод: `InputInjectionHost` на десктопе (мышь/клавиатура, уже есть
  [`InputInjector.desktop.kt`](core/src/desktopMain/kotlin/ru/efir/core/input/InputInjector.desktop.kt)),
  на Android — `EfirAccessibilityService` (есть).
- UI: секция «Управление экраном» симметрична — кнопка «Показать экран <удалённого>».

### Итерация 6 — Двусторонний файловый менеджер

- Двухпанельный режим: локальная панель (`LocalFileSystem`, есть) + удалённая
  (`FileManagerClient`, есть) — перенос файлов кнопками «→»/«←» и drag-n-drop.
- `FileManagerHost` поднимается на обеих сторонах (сейчас только на Android-хосте).

### Итерация 7 — Синхронизация: русские названия и правило имён

- Явные три режима из ТЗ: **Замещение** (`OVERWRITE`, односторонняя),
  **Слияние** (`MERGE`/`PREFER_NEWEST`, двусторонняя, одинаковое не трогает),
  **Добавление** (`APPEND` → переименование входящего).
- `uniqueName`: `аааа.ext` → `аааа-1.ext`; заняты `аааа` и `аааа-2` → берётся свободное `аааа-1`.

## Критерии готовности

- [x] Ключ доступа 12 символов `0-9A-Z`, отображается как `XXXX-XXXX-XXXX`, принимается в любом регистре и с дефисами.
- [ ] Любое устройство может быть и ждущим, и подключающимся без пересборки.
- [ ] Android в ожидании: JSON на экране + «Показать QR» + «Копировать в буфер».
- [ ] Desktop в ожидании: QR на экране + IP/порт в подписи.
- [ ] Трафик между устройствами шифруется (TLS), отпечаток проверяется при сопряжении.
- [ ] Режим постоянного ожидания работает на обеих платформах.
- [ ] Экран и ввод работают в обе стороны.
- [ ] Файловый менеджер переносит файлы в обе стороны из одного окна.
- [ ] Правило переименования конфликтов — `имя-1.ext`.
