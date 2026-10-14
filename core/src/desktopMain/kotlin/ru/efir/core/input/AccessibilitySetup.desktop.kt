package ru.efir.core.input

/** На Desktop инъекция ввода не выполняется — служба недоступна. */
actual fun isAccessibilityServiceEnabled(): Boolean = false

/** На Desktop настроек службы доступности нет. */
actual fun openAccessibilitySettings(): Boolean = false
