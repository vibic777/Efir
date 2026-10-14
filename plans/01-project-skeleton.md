# План 01 — Каркас KMP-проекта

## Цель

Создать скелет проекта **Эфир** на Kotlin Multiplatform + Compose Multiplatform с тремя модулями и настроенной сборкой для Android и Windows.

## Зависимости

Нет (базовый блок).

## Структура репозитория

```
Efir/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradle/libs.versions.toml
  core/                 # KMP-модуль (общий код)
    build.gradle.kts
    src/commonMain/kotlin/...
    src/androidMain/kotlin/...
    src/desktopMain/kotlin/...
  androidApp/           # Android-приложение
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/kotlin/...
  desktopApp/           # Windows-приложение (Compose Desktop)
    build.gradle.kts
    src/main/kotlin/...
  plans/
```

## Ключевые технологии

- **Gradle 8.x**, Kotlin 2.x, плагин `kotlin("multiplatform")`.
- **Compose Multiplatform** (org.jetbrains.compose) для desktop, Jetpack Compose для Android.
- **Ktor** — сети (клиент/сервер, WebSocket) для всех платформ.
- **kotlinx.serialization** — сериализация протокола.
- **kotlinx.coroutines** — асинхронность.
- **kotlinx-datetime** — даты/расписания синхронизации.
- **SQLDelight** — локальное хранилище (настройки, доверенные устройства, журналы).
- **Koin** — DI (опционально).

## Целевые платформы

- Android: `minSdk 26`, `targetSdk 35`.
- Windows: JVM 17+, упаковка через `packageMsi`/`packageExe` (jpackage).

## Шаги реализации

1. Инициализировать `settings.gradle.kts` с модулями `core`, `androidApp`, `desktopApp`.
2. Настроить `gradle/libs.versions.toml` (каталог версий).
3. Настроить `core` как KMP с таргетами `androidTarget()` и `jvm("desktop")`, добавить `commonMain` с общими зависимостями.
4. Настроить `androidApp` (Application + Activity с Compose).
5. Настроить `desktopApp` (main-функция `application { Window { ... } }`).
6. Добавить базовые `expect/actual` заготовки (например, `DeviceInfo`, пути к папкам, крипто).
7. Настроить `.gitignore` для Gradle/IntelliJ/Android.
8. Проверить: `./gradlew :androidApp:assembleDebug` и `./gradlew :desktopApp:run`.

## Краевые случаи

- Рассинхрон версий Kotlin/Compose/Ktor — фиксировать в version catalog.
- Разные JDK для Android и Desktop — задать toolchain.
- Сетевые разрешения Android (`INTERNET`, `ACCESS_NETWORK_STATE`) с первого шага.

## Критерии готовности

- [ ] Все три модуля собираются.
- [ ] Пустые приложения запускаются на Android и Windows.
- [ ] Общий код из `core` виден и импортируется в оба приложения.
