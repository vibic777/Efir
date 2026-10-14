package ru.efir.core.comms

// Роль default SMS app существует только на Android.

actual fun defaultSmsRoleSupported(): Boolean = false

actual fun isDefaultSmsApp(): Boolean = false

actual fun requestDefaultSmsRole(): Boolean = false
