package ru.efir.core.app

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Видно ли приложение человеку: окно на экране, а не свёрнуто, не в трее и не в фоне.
 *
 * Всё, что нужно только глазам, — поиск устройств в сети, перечитывание
 * разрешений, секундные такты экранов — работает, только пока здесь `true`
 * (см. `docs/BATTERY_RU.md`). Значение выставляют точки входа платформ.
 */
object AppVisibility {
    val visible = MutableStateFlow(true)
}

/**
 * Просьба открыть раздел приложения извне окна (меню значка в трее): имя раздела —
 * `DEVICES`, `SCREEN`, `FILES`, `SYNC`… Окно выполняет её и сбрасывает.
 */
object AppNavigation {
    val request = MutableStateFlow<String?>(null)
}
