package ru.efir.core.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ru.efir.core.net.EfirNetwork
import ru.efir.core.pairing.InMemoryTrustStore
import ru.efir.core.protocol.DeviceType
import ru.efir.core.ui.HelpScreen
import ru.efir.core.ui.HelpTopic
import ru.efir.core.ui.LocalStrings
import ru.efir.core.ui.i18n.EnStrings
import ru.efir.core.ui.i18n.RuStrings
import ru.efir.core.ui.i18n.Strings
import ru.efir.core.ui.screens.ClipboardScreen
import ru.efir.core.ui.screens.FilesScreen
import ru.efir.core.ui.screens.MediaScreen
import ru.efir.core.ui.screens.RemoteScreenScreen
import ru.efir.core.ui.screens.SyncScreen
import ru.efir.core.ui.theme.EfirTheme
import ru.efir.core.ui.theme.ThemeMode
import java.io.File
import java.net.DatagramSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Сквозная проверка двух контроллеров в одном процессе: сопряжение по ключу,
 * отказ несопряжённому устройству, повторное подключение по токену, реакция
 * на разрыв с другой стороны и отзыв доверия. Заодно экраны отрисовываются в
 * `build/ui-shots` — видно, что интерфейс собирается и как он выглядит.
 */
class ControllerLinkTest {

    private fun freeUdpPort(): Int = DatagramSocket(0).use { it.localPort }

    private fun freeTcpPort(): Int = java.net.ServerSocket(0).use { it.localPort }

    private suspend fun <T> StateFlow<T>.await(what: String, condition: (T) -> Boolean): T =
        runCatching { withTimeout(15_000) { first(condition) } }
            .getOrElse { error("Не дождались: $what (сейчас: $value)") }

    private fun controller(scope: CoroutineScope, id: String, name: String, port: Int = EfirNetwork.DEFAULT_PORT) = EfirController(
        scope = scope,
        selfType = DeviceType.WINDOWS,
        selfDeviceId = id,
        selfName = name,
        selfHost = "127.0.0.1",
        trustStore = InMemoryTrustStore(),
        discoveryPort = freeUdpPort(),
        listenPort = port,
        listenOnStart = false,
    )

    @Test
    fun pairingTrustAndDisconnect() = runBlocking {
        val scopeA = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scopeB = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Свободные порты: тест не зависит от Эфира, запущенного на этой машине.
        val portA = freeTcpPort()
        val a = controller(scopeA, "win-a", "PC A", portA)
        val b = controller(scopeB, "win-b", "PC B", freeTcpPort())
        try {
            a.beginPairing()
            val key = assertNotNull(a.pairingInfo.value).accessKey
            delay(1_500) // сервер поднимается не мгновенно

            // 1. Без ключа и без доверия соединение не устанавливается.
            b.connectTo("127.0.0.1", port = portA)
            b.connecting.await("конец попытки без ключа") { !it }
            assertEquals(ClientHandshake.NOT_PAIRED, b.connectionError.value)
            assertNull(b.connection.value)
            assertNull(a.connection.value, "Несопряжённое устройство не должно получить сессию")

            // 2. Неверный ключ отклоняется, верный — сопрягает (токен из QR не нужен).
            b.connectTo("127.0.0.1", port = portA, accessKey = "000000000000")
            b.connecting.await("конец попытки с неверным ключом") { !it }
            assertNull(b.connection.value)

            b.connectTo("127.0.0.1", port = portA, accessKey = key)
            b.connection.await("соединение B после сопряжения") { it != null }
            a.connection.await("соединение A после сопряжения") { it != null }
            assertEquals("win-b", a.connection.value?.remote?.deviceId)
            assertEquals(LinkRole.HOST, a.connection.value?.linkRole)
            assertNull(a.pairingInfo.value, "Одноразовый ключ должен исчезнуть после сопряжения")
            a.trustedDevices.await("доверие на A") { list -> list.any { it.deviceId == "win-b" } }
            b.trustedDevices.await("доверие на B") { list -> list.any { it.deviceId == "win-a" } }

            // 3. Разрыв с одной стороны виден на другой.
            b.disconnect()
            b.connection.await("разрыв на B") { it == null }
            a.connection.await("разрыв на A") { it == null }

            // 4. Доверенное устройство подключается без ключа, даже если адрес введён вручную.
            b.connectTo("127.0.0.1", port = portA)
            b.connection.await("повторное соединение B") { it != null }
            a.connection.await("повторное соединение A") { it != null }

            b.fileBrowser.await("разделы файлов A") { it.roots.isNotEmpty() }
            renderScreens(b)

            // 4а. Показ экрана компьютера: B просит, A показывает, кадр доходит; остановка на A видна на B.
            if (ru.efir.core.media.screenCaptureSupported() && ru.efir.core.media.ScreenSharing.showAllowed) {
                b.startScreenStream()
                a.screenHostState.await("показ экрана на A") { it.active && it.width > 0 }
                b.remoteScreen.await("кадр экрана A на B") { it != null }
                assertTrue(b.screenClientState.value.width > 0, "У принятого кадра должен быть размер")
                println("screen sharing checked: frame ${b.screenClientState.value.width}x${b.screenClientState.value.height}")
                assertNull(b.screenClientState.value.error)

                a.stopOwnScreen()
                a.screenHostState.await("конец показа на A") { !it.active }
                b.screenClientState.await("конец показа виден на B") { it.error == ru.efir.core.media.StreamErrors.STOPPED }
                assertNull(b.remoteScreen.value, "После остановки картинка убирается")

                // Повторный показ и остановка со стороны смотрящего.
                b.startScreenStream()
                b.remoteScreen.await("кадр после повторного запуска") { it != null }
                b.stopScreenStream()
                a.screenHostState.await("конец показа по просьбе B") { !it.active }
            } else {
                println("screen sharing NOT checked: no screen in this session")
            }

            // 5. Отзыв доверия разрывает связь и закрывает вход.
            a.revokeTrust("win-b")
            a.connection.await("разрыв после отзыва") { it == null }
            b.connection.await("разрыв после отзыва на B") { it == null }
            b.connectTo("127.0.0.1", port = portA)
            b.connecting.await("конец попытки после отзыва") { !it }
            assertNull(b.connection.value)
            assertEquals(ClientHandshake.NOT_PAIRED, b.connectionError.value)
        } finally {
            a.stop()
            b.stop()
            delay(300)
            scopeA.cancel()
            scopeB.cancel()
        }
    }

    /** Рисует экраны в PNG: широкое и узкое окно, светлая и тёмная тема, оба языка. */
    private fun renderScreens(controller: EfirController) {
        val link = assertNotNull(controller.connection.value)
        val out = File("build/ui-shots").apply { mkdirs() }

        fun shot(name: String, width: Int, height: Int, content: @Composable () -> Unit) {
            ImageComposeScene(width, height, Density(1f), content = content).use { scene ->
                var image = scene.render(0)
                // Несколько кадров: подписки на состояние и эффекты успевают отработать.
                // Короткая пауза между кадрами: чтение папок и файлов идёт в другом потоке.
                repeat(5) {
                    Thread.sleep(40)
                    // Состояние, изменённое из другого потока, сцена теста сама не подхватывает.
                    androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                    image = scene.render((it + 1) * 100_000_000L)
                }
                val bytes = assertNotNull(image.encodeToData()).bytes
                assertTrue(bytes.size > 1_000, "Экран $name отрисовался пустым")
                File(out, "$name.png").writeBytes(bytes)
            }
        }

        @Composable
        fun framed(strings: Strings, theme: ThemeMode, content: @Composable () -> Unit) {
            CompositionLocalProvider(LocalStrings provides strings) {
                EfirTheme(theme) { Surface(color = MaterialTheme.colorScheme.background, content = content) }
            }
        }

        shot("logo", 256, 256) { ru.efir.core.ui.theme.EfirLogo(androidx.compose.ui.Modifier.fillMaxSize()) }
        shot("app-wide", 1100, 760) { EfirAppScreen(controller) }
        shot("app-narrow", 400, 800) { EfirAppScreen(controller) }
        shot("files-ru", 1000, 760) { framed(RuStrings, ThemeMode.LIGHT) { FilesScreen(controller, link) } }
        shot("files-en-dark", 1000, 760) { framed(EnStrings, ThemeMode.DARK) { FilesScreen(controller, link) } }
        shot("sync-ru", 1000, 500) { framed(RuStrings, ThemeMode.LIGHT) { SyncScreen(controller, link) } }
        shot("screen-ru", 1000, 500) { framed(RuStrings, ThemeMode.LIGHT) { RemoteScreenScreen(controller, link) } }
        shot("media-en", 1000, 500) { framed(EnStrings, ThemeMode.LIGHT) { MediaScreen(controller, link) } }
        shot("clipboard-ru-dark", 1000, 500) { framed(RuStrings, ThemeMode.DARK) { ClipboardScreen(controller, link) } }
        shot("help-ru", 1000, 900) { framed(RuStrings, ThemeMode.LIGHT) { HelpScreen(HelpTopic.DEVICES) } }
        shot("help-en", 1000, 900) { framed(EnStrings, ThemeMode.DARK) { HelpScreen(HelpTopic.DOWNLOADS) } }
        val settings: @Composable (ru.efir.core.ui.screens.SettingsSection?) -> Unit = { section ->
            ru.efir.core.ui.screens.SettingsLink.section = section
            ru.efir.core.ui.screens.SettingsScreen(controller, ru.efir.core.ui.i18n.Lang.RU, {}, ThemeMode.LIGHT, {})
        }
        shot("settings-menu-ru", 1000, 760) { framed(RuStrings, ThemeMode.LIGHT) { settings(null) } }
        shot("settings-folders-ru", 1000, 900) { framed(RuStrings, ThemeMode.LIGHT) { settings(ru.efir.core.ui.screens.SettingsSection.FOLDERS) } }
        shot("settings-screen-ru-dark", 1000, 500) { framed(RuStrings, ThemeMode.DARK) { settings(ru.efir.core.ui.screens.SettingsSection.SCREEN) } }
        shot("settings-about-en", 1000, 600) { framed(EnStrings, ThemeMode.LIGHT) { settings(ru.efir.core.ui.screens.SettingsSection.ABOUT) } }
        ru.efir.core.ui.screens.SettingsLink.section = null

        // Справка поверх окна (как её открывает нажатая подсказка) и поиск в ней; окно выбора папки.
        ru.efir.core.ui.HelpLink.open(HelpTopic.SCREEN)
        shot("help-overlay", 1100, 760) { EfirAppScreen(controller) }
        ru.efir.core.ui.HelpLink.close()
        shot("folder-picker-ru", 700, 700) {
            framed(RuStrings, ThemeMode.LIGHT) {
                ru.efir.core.ui.screens.FolderPickerDialog(
                    title = RuStrings.downloadsFolder,
                    start = null,
                    // Готовый список вместо настоящих дисков: снимок не зависит от машины.
                    source = ru.efir.core.ui.screens.FolderSource(
                        roots = { listOf("C:/Users/user", "C:/", "D:/").map { ru.efir.core.ui.screens.FolderItem(it, it) } },
                        list = { emptyList() },
                        make = { true },
                        remote = false,
                    ),
                    onPick = {},
                    onDismiss = {},
                )
            }
        }
    }
}
