# План 13 — Производительность, стабильность и упаковка

## Цель

Оптимизировать систему, обеспечить стабильность и подготовить дистрибутивы Android (APK) и Windows (инсталлятор), а также документацию.

## Зависимости

Все предыдущие планы.

## Производительность

- Аппаратное кодирование/декодирование видео (MediaCodec/MediaFoundation).
- Адаптивный битрейт по качеству сети.
- Батчинг ввода, минимальные аллокации в горячих путях.
- Эффективные буферы (пулы), нулевое копирование где возможно.
- Профилирование: задержка ввода, FPS потока, пропускная способность файлов.
- Управление энергопотреблением Android (Doze, wake locks точечно, foreground services).

## Стабильность

- Восстановление соединения и передач после обрывов.
- Корректная работа в фоне (foreground services, автозапуск на ПК).
- Логирование и диагностика (уровни, экспорт логов).
- Обработка «краевых» версий Android 8–15 и Windows 10/11.

## Тестирование

- Юнит-тесты `core` (кодек, differ, transfer).
- Интеграционные тесты соединения (эмулятор/реальное устройство).
- Ручные сценарии: управление, файлы, синхронизация, буфер, уведомления, звонки.
- Тесты на разных разрешениях экрана и ориентациях.

## Упаковка

- **Android:** подписанный APK/AAB, версии, обфускация (R8), релизные ключи.
- **Windows:** `jpackage` (MSI/EXE), иконка, автозапуск (опционально), подпись кода (опционально).
- **Установка и онбординг:** инструкция по выдаче разрешений (Accessibility, MediaProjection, SAF, SMS).

## Документация

- README: запуск сборки, требования, скриншоты.
- Руководство пользователя: сопряжение, управление, синхронизация.
- Описание протокола и архитектуры.

## Шаги реализации

1. Профилировать и оптимизировать горячие пути (видео, файлы, ввод).
2. Довести обработку ошибок и восстановление.
3. Написать тесты (юнит/интеграционные) ключевых модулей.
4. Настроить релизные сборки и упаковку.
5. Написать README и руководство пользователя.
6. Провести финальное тестирование на реальных устройствах.

## Критерии готовности

- [x] Приемлемая задержка управления и плавный поток (JPEG по умолчанию + opt-in H.264;
      аппаратное декодирование на ПК — далее).
- [x] Адаптивный битрейт по качеству сети (`AdaptiveBitrateController`).
- [x] Аппаратное кодирование видео (`MediaCodec`) + профилирование (`StreamMetrics`).
- [x] Управление энергопотреблением (wake lock, исключение из Doze).
- [x] Устойчивость к обрывам и перезапускам (докачка передач, persist очереди передач; переподключение — базовое).
- [x] Готовые конфигурации дистрибутивов: MSI/EXE (`packageMsi`/`packageExe`), Android release.
- [x] Подпись Windows-инсталляторов и Android-релиза.
- [x] Документация и онбординг по разрешениям.

## Что реализовано (итерация 4 — производительность и поставка)

- **Аппаратное кодирование видео (opt-in):** Android-хост кодирует H.264 через
  `MediaCodec` (Surface-кодирование) —
  [`AndroidH264Encoder`](core/src/androidMain/kotlin/ru/efir/core/media/H264Encoder.android.kt);
  задействовано для экрана (`VirtualDisplay`) и камеры (Camera2 → Surface).
  ПК декодирует поток чистым JVM-декодером `VideoFrameDecoder`
  ([`VideoDecoding`](core/src/commonMain/kotlin/ru/efir/core/media/VideoDecoding.kt),
  desktop — JCodec + Skia). При отсутствии поддержки — автоматический откат на JPEG.
  Кадры кодека несут флаг `config` (CSD/SPS-PPS), см.
  [`MediaFrameCodec`](core/src/commonMain/kotlin/ru/efir/core/media/MediaFrameCodec.kt).
- **Профилирование:**
  [`StreamMetrics`](core/src/commonMain/kotlin/ru/efir/core/media/StreamMetrics.kt) —
  фактический FPS, битрейт, средний размер кадра и оценка задержки «захват → обработка»
  по скользящему окну; выведено в состояния хостов/клиентов (`actualFps`, `latencyMs`,
  `bitrateBps`). Тесты: `StreamMetricsTest`.
- **Энергопотребление (Android):** foreground-сервис удерживает частичный `WakeLock`;
  приложение запрашивает исключение из оптимизации батареи (Doze) через
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`;
  [`ScreenCaptureService`](androidApp/src/main/kotlin/ru/efir/android/ScreenCaptureService.kt),
  [`MainActivity`](androidApp/src/main/kotlin/ru/efir/android/MainActivity.kt).
- **Подпись Windows-инсталляторов:**
  [`scripts/sign-windows.ps1`](scripts/sign-windows.ps1) — самоподписанный
  code-signing сертификат (или PFX) + `signtool`; MSI и EXE успешно подписаны.
- **Подпись Android-релиза реальным ключом:** сгенерирован ключ
  `androidApp/efir-release.jks`, создан `keystore.properties`, `assembleRelease`
  подписывает APK (`validateSigningRelease`). Неподписанный релиз не устанавливается
  на устройство — теперь установка работает.
- **Скрипт релиза:** [`build-release.cmd`](build-release.cmd) дополнен шагом подписи.

## Что реализовано (итерация 3 — релиз)

- **Собран релиз Windows**: [`Efir-0.1.0.msi`](desktopApp/build/compose/binaries/main/msi) и
  `Efir-0.1.0.exe` (`packageMsi`/`packageExe`, с включённой JRE).
- **Исправлена упаковка под WiX 3.11**: метаданные установщика приведены к ASCII
  (WiX 3.11 отклонял non-ASCII в `description`/`copyright` — `light.exe` exit 311).
- **Собран Android release**: `assembleRelease` (`R8` + `shrinkResources`).
- **Конфигурация подписи Android**: чтение `androidApp/keystore.properties` в
  [`androidApp/build.gradle.kts`](androidApp/build.gradle.kts) (без ключа — неподписанный APK),
  шаблон [`keystore.properties.example`](androidApp/keystore.properties.example).
- **Скрипт релиза** [`build-release.cmd`](build-release.cmd): тесты `core` → MSI/EXE → Android.
- **`.gitignore`**: игнорирование `keystore.properties` и ключей.

## Что реализовано (итерация 2)

- **Адаптивный битрейт**: [`AdaptiveBitrateController`](core/src/commonMain/kotlin/ru/efir/core/media/AdaptiveBitrateController.kt)
  — оценка фактической скорости по скользящему окну, гистерезис `[0.6; 1.15]`
  от целевого битрейта, ступенчатая регулировка JPEG/FPS/масштаба.
- Поля `jpegQuality` и `adaptive` добавлены в `screenStart`/`cameraStart`
  ([`MediaMessages`](core/src/commonMain/kotlin/ru/efir/core/protocol/MediaMessages.kt)),
  проброс в [`ScreenQuality`](core/src/commonMain/kotlin/ru/efir/core/media/ScreenCapture.kt)/
  [`CameraQuality`](core/src/commonMain/kotlin/ru/efir/core/media/CameraCapture.kt).
- Хосты [`ScreenStreamHost`](core/src/commonMain/kotlin/ru/efir/core/media/ScreenStreamHost.kt)/
  [`CameraStreamHost`](core/src/commonMain/kotlin/ru/efir/core/media/CameraAudioHost.kt):
  троттлинг FPS и применение регулировок через `setEncoderQuality`; состояние
  (`fps`/`jpegQuality`/`measuredBps`) выведено в стримы.
- Android-источники применяют качество на лету (JPEG без пересоздания сессии,
  Camera2 `JPEG_QUALITY`).
- Тесты: [`AdaptiveBitrateControllerTest`](core/src/commonTest/kotlin/ru/efir/core/media/AdaptiveBitrateControllerTest.kt).

## Что реализовано (итерация 1)

- **Windows-упаковка** [`desktopApp/build.gradle.kts`](desktopApp/build.gradle.kts):
  MSI/EXE, включённая JRE (`includeAllModules`), ярлыки, `upgradeUuid`, per-user install.
- **Android-упаковка** [`androidApp/build.gradle.kts`](androidApp/build.gradle.kts):
  R8/обфускация + `shrinkResources`, правила
  [`proguard-rules.pro`](androidApp/proguard-rules.pro) (сохранение сериализаторов,
  Ktor, служб/приёмников).
- **Документация**: корневой [`README.md`](README.md) (сборка/запуск/упаковка),
  [`docs/permissions.md`](docs/permissions.md) (онбординг по разрешениям),
  [`docs/protocol.md`](docs/protocol.md) (протокол и каналы).
- **Тесты**: набор `core` (протокол, кодеки, синхронизация, буфер, медиа, SMS).

## Осталось по блоку 13 (за рамками текущего релиза)

- Аппаратное **декодирование** видео на ПК (MediaFoundation/DXVA) вместо чистого JVM-декодера.
- Аппаратное кодирование камеры на устройствах без поддержки Surface-кодирования камерного тракта.
- Восстановление соединения после перезапуска приложения (незавершённые передачи при этом уже восстанавливаются из очереди).
- Публикация в сторах; продакшен-сертификат для подписи Windows (сейчас — самоподписанный).
- Замена тестового release-ключа Android на защищённый (ключ не должен быть в репозитории).
