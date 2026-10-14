package ru.efir.core.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import ru.efir.core.ui.Button
import ru.efir.core.ui.OutlinedButton
import androidx.compose.material3.MaterialTheme
import ru.efir.core.ui.OutlinedTextField
import ru.efir.core.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.efir.core.app.ActiveConnection
import ru.efir.core.app.AppVisibility
import ru.efir.core.app.platformPermissions
import ru.efir.core.media.ScreenSharing
import ru.efir.core.app.EfirController
import ru.efir.core.input.FrameMapping
import ru.efir.core.input.GestureBuilder
import ru.efir.core.protocol.DeviceType
import ru.efir.core.protocol.KeyAction
import ru.efir.core.ui.AppIcons
import ru.efir.core.ui.ErrorText
import ru.efir.core.ui.HelpTopic
import ru.efir.core.ui.Hint
import ru.efir.core.ui.IconAction
import ru.efir.core.ui.IconChoice
import ru.efir.core.ui.LocalStrings
import ru.efir.core.ui.SectionCard
import kotlin.math.abs
import kotlin.math.sqrt

/** Экран «Экран»: трансляция экрана удалённого устройства и управление им. */
@Composable
fun RemoteScreenScreen(controller: EfirController, connection: ActiveConnection) {
    val s = LocalStrings.current
    val state by controller.screenClientState.collectAsState()
    val hostState by controller.screenHostState.collectAsState()
    val frame by controller.remoteScreen.collectAsState()
    val remoteIsPhone = connection.remote.type == DeviceType.ANDROID
    var fullScreen by remember { mutableStateOf(false) }

    ScreenColumn(HelpTopic.SCREEN) {
        SectionCard(
            title = s.screenOf(connection.remote.name),
            icon = AppIcons.ScreenRecord,
            actions = {
                if (frame != null) IconAction(AppIcons.Fullscreen, s.fullScreen, { fullScreen = true })
                if (state.active) {
                    IconAction(AppIcons.Stop, s.stopScreen, { controller.stopScreenStream() })
                } else {
                    IconAction(AppIcons.PlayArrow, s.startScreen, { controller.startScreenStream() })
                }
            },
        ) {
            val image = frame
            when {
                image != null && state.width > 0 && state.height > 0 -> {
                    RemoteScreenView(controller, image, state.width, state.height)
                    if (fullScreen) {
                        FullScreenView(controller, image, state.width, state.height, onExit = { fullScreen = false })
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (remoteIsPhone) {
                            IconAction(AppIcons.ArrowBack, s.keyBack, { controller.sendKey(KeyAction.BACK) })
                            IconAction(AppIcons.Home, s.keyHome, { controller.sendKey(KeyAction.HOME) })
                            IconAction(AppIcons.Apps, s.keyRecents, { controller.sendKey(KeyAction.RECENTS) })
                        }
                    }
                    var text by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(s.typeText) },
                        tip = s.tipTypeText,
                        leadingIcon = { androidx.compose.material3.Icon(AppIcons.Keyboard, null) },
                        trailingIcon = {
                            IconAction(AppIcons.Send, s.sendText, {
                                controller.sendText(text)
                                text = ""
                            }, enabled = text.isNotEmpty())
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint(s.screenStats(state.width, state.height, state.receivedFrames, state.droppedFrames))
                }

                state.active -> Hint(s.waitingFrame)

                else -> {
                    Hint(s.screenHint)
                    Button(onClick = { controller.startScreenStream() }, tip = s.tipStartScreen) { Text(s.startScreen) }
                }
            }
            state.error?.let { ErrorText(s.streamError(it)) }
        }

        // Экран этого устройства: смотрят ли его сейчас, как остановить показ и как его разрешить.
        val openSettings = LocalOpenSettings.current
        val visible by AppVisibility.visible.collectAsState()
        SectionCard(
            title = s.ownScreen,
            icon = deviceIcon(controller.selfType),
            topic = HelpTopic.SCREEN_SHARE,
            actions = {
                if (hostState.active) IconAction(AppIcons.Stop, s.stopSharing, { controller.stopOwnScreen() })
            },
        ) {
            Hint(
                if (hostState.active) s.ownScreenShared(hostState.width, hostState.height, hostState.sentFrames)
                else s.ownScreenIdle,
            )
            if (hostState.active) {
                Button(onClick = { controller.stopOwnScreen() }, tip = s.tipStopSharing) { Text(s.stopSharing) }
            }
            when {
                !controller.canCaptureScreen -> ErrorText(s.captureUnavailable)

                controller.selfType == DeviceType.ANDROID -> {
                    // Разрешение выдают в системном окне: перечитываем, пока карточка перед глазами.
                    var tick by remember { mutableStateOf(0) }
                    LaunchedEffect(visible) {
                        while (visible) {
                            delay(1_500)
                            tick++
                        }
                    }
                    val permission = remember(tick) { platformPermissions().firstOrNull { it.id == "screen" } }
                    when {
                        hostState.active -> Unit
                        permission?.granted == true -> Hint(s.screenShareReady)
                        else -> {
                            Hint(s.ownScreenPhoneHint)
                            if (permission != null) {
                                OutlinedButton(onClick = { permission.request() }, tip = s.tipAllowScreenShare) { Text(s.allowScreenShare) }
                            }
                        }
                    }
                }

                else -> {
                    Hint(if (ScreenSharing.showAllowed) s.ownScreenPcOn else s.ownScreenPcOff)
                    if (!hostState.active) {
                        OutlinedButton(onClick = { openSettings(SettingsSection.SCREEN) }) { Text(s.openScreenSettings) }
                    }
                }
            }
            hostState.error?.let { ErrorText(s.ownShareError(it)) }
        }
    }
}

/**
 * Кадр удалённого экрана. Область сама принимает пропорции кадра (и не выше
 * окна), поэтому касания попадают точно; жесты переводятся в нормализованные
 * координаты (0..1): тап, свайп и щипок двумя пальцами.
 */
@Composable
private fun RemoteScreenView(controller: EfirController, image: ImageBitmap, width: Int, height: Int) {
    val s = LocalStrings.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = image,
            contentDescription = s.remoteScreen,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .heightIn(max = 640.dp)
                .aspectRatio(width.toFloat() / height.toFloat())
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .pointerInput(width, height) {
                    val mapping = FrameMapping(
                        frameWidth = width,
                        frameHeight = height,
                        viewWidth = size.width.toFloat(),
                        viewHeight = size.height.toFloat(),
                    )
                    detectRemoteGestures(
                        mapping = mapping,
                        onTap = { nx, ny -> controller.sendTap(nx, ny) },
                        onSwipe = { fromX, fromY, toX, toY -> controller.sendSwipe(fromX, fromY, toX, toY) },
                        onPinch = { centerX, centerY, startSpan, endSpan ->
                            controller.sendMultiTouch(
                                GestureBuilder.pinch(
                                    centerX = centerX,
                                    centerY = centerY,
                                    startSpan = startSpan,
                                    endSpan = endSpan,
                                    durationMs = 300,
                                ),
                            )
                        },
                    )
                },
        )
    }
}

/** Жесты по картинке удалённого экрана: нажатие, проведение, щипок — в координатах его кадра. */
private fun Modifier.remoteGestures(controller: EfirController, width: Int, height: Int): Modifier =
    pointerInput(width, height) {
        val mapping = FrameMapping(
            frameWidth = width,
            frameHeight = height,
            viewWidth = size.width.toFloat(),
            viewHeight = size.height.toFloat(),
        )
        detectRemoteGestures(
            mapping = mapping,
            onTap = { nx, ny -> controller.sendTap(nx, ny) },
            onSwipe = { fromX, fromY, toX, toY -> controller.sendSwipe(fromX, fromY, toX, toY) },
            onPinch = { centerX, centerY, startSpan, endSpan ->
                controller.sendMultiTouch(
                    GestureBuilder.pinch(centerX = centerX, centerY = centerY, startSpan = startSpan, endSpan = endSpan, durationMs = 300),
                )
            },
        )
    }

/**
 * Удалённый экран на всё окно (на телефоне — на весь экран): картинка вписана целиком,
 * её можно повернуть на четверть оборота — широкий экран компьютера на узком телефоне
 * удобнее смотреть боком. Жесты работают и в повёрнутом виде. Выход — кнопкой или «Назад».
 */
@Composable
private fun FullScreenView(controller: EfirController, image: ImageBitmap, width: Int, height: Int, onExit: () -> Unit) {
    val s = LocalStrings.current
    var turns by remember { mutableStateOf(0) }
    Dialog(onDismissRequest = onExit, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // После поворота на четверть ширина и высота места меняются местами.
                val sideways = turns % 2 == 1
                val roomW = if (sideways) maxHeight else maxWidth
                val roomH = if (sideways) maxWidth else maxHeight
                val aspect = width.toFloat() / height.toFloat()
                val w = if (roomW / aspect <= roomH) roomW else roomH * aspect
                val h = w / aspect
                Image(
                    bitmap = image,
                    contentDescription = s.remoteScreen,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .requiredSize(w, h)
                        .rotate(90f * turns)
                        .remoteGestures(controller, width, height),
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            ) {
                Row {
                    IconAction(AppIcons.RotateRight, s.rotateScreen, { turns = (turns + 1) % 4 })
                    IconAction(AppIcons.FullscreenExit, s.exitFullScreen, onExit)
                }
            }
        }
    }
}

/**
 * Различает одиночные и мультитач-жесты: один палец — тап/свайп, два пальца —
 * щипок. Координаты переводятся в нормализованные через [mapping].
 */
private suspend fun PointerInputScope.detectRemoteGestures(
    mapping: FrameMapping,
    onTap: (Float, Float) -> Unit,
    onSwipe: (Float, Float, Float, Float) -> Unit,
    onPinch: (centerX: Float, centerY: Float, startSpan: Float, endSpan: Float) -> Unit,
) = awaitEachGesture {
    val first = awaitFirstDown(requireUnconsumed = false)
    var pointerCount = 1
    var startFirst = first.position
    var startSecond: Offset? = null
    var pinched = false
    var swiped = false

    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        pointerCount = maxOf(pointerCount, pressed.size)

        if (pressed.size >= 2 && !swiped) {
            val a = pressed[0].position
            val b = pressed[1].position
            val second = startSecond ?: b.also {
                startFirst = a
                startSecond = it
            }
            val startSpan = abs(second.x - startFirst.x)
            val currentSpan = abs(b.x - a.x)
            if (abs(currentSpan - startSpan) > size.width * 0.04f) {
                pinched = true
                onPinch(
                    mapping.toNormalizedX((a.x + b.x) / 2f),
                    mapping.toNormalizedY((a.y + b.y) / 2f),
                    if (mapping.scaledWidth > 0f) startSpan / mapping.scaledWidth else 0f,
                    if (mapping.scaledWidth > 0f) currentSpan / mapping.scaledWidth else 0f,
                )
                // Сдвигаем базу, чтобы следующие движения тоже давали жест.
                startFirst = a
                startSecond = b
            }
            event.changes.forEach { it.consume() }
        } else if (pressed.size == 1 && pointerCount == 1) {
            val change = pressed[0]
            val dx = change.position.x - startFirst.x
            val dy = change.position.y - startFirst.y
            // Порог против дребезга: свайп только при заметном смещении.
            if (sqrt(dx * dx + dy * dy) > size.width * 0.08f) {
                swiped = true
                onSwipe(
                    mapping.toNormalizedX(startFirst.x),
                    mapping.toNormalizedY(startFirst.y),
                    mapping.toNormalizedX(change.position.x),
                    mapping.toNormalizedY(change.position.y),
                )
                startFirst = change.position
            }
            event.changes.forEach { it.consume() }
        }

        if (pressed.isEmpty()) break
    }

    if (!pinched && !swiped) {
        onTap(mapping.toNormalizedX(first.position.x), mapping.toNormalizedY(first.position.y))
    }
}

/** Экран «Камера и звук»: камера и микрофон/системный звук удалённого устройства. */
@Composable
fun MediaScreen(controller: EfirController, connection: ActiveConnection) {
    val s = LocalStrings.current
    val camera by controller.cameraClientState.collectAsState()
    val audio by controller.audioClientState.collectAsState()
    val frame by controller.remoteCamera.collectAsState()
    var front by remember { mutableStateOf(false) }
    var systemAudio by remember { mutableStateOf(false) }

    ScreenColumn(HelpTopic.MEDIA) {
        SectionCard(
            title = s.cameraOf(connection.remote.name),
            icon = AppIcons.Videocam,
            actions = {
                IconChoice(AppIcons.PhotoCamera, s.cameraBack, selected = !front, onClick = {
                    front = false
                    if (camera.active) controller.startCameraStream(front = false)
                })
                IconChoice(AppIcons.Smartphone, s.cameraFront, selected = front, onClick = {
                    front = true
                    if (camera.active) controller.startCameraStream(front = true)
                })
                if (camera.active) {
                    IconAction(AppIcons.Stop, s.stopCamera, { controller.stopCameraStream() })
                } else {
                    IconAction(AppIcons.PlayArrow, s.startCamera, { controller.startCameraStream(front) })
                }
            },
        ) {
            val image = frame
            if (image != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = image,
                        contentDescription = s.cameraOf(connection.remote.name),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .heightIn(max = 480.dp)
                            .aspectRatio(image.width.toFloat() / image.height.toFloat())
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                    )
                }
            }
            Hint(
                if (camera.active) s.cameraStats(camera.width, camera.height, camera.receivedFrames)
                else s.cameraIdle(if (front) s.cameraFront else s.cameraBack),
            )
            camera.error?.let { ErrorText(s.streamError(it)) }
        }

        SectionCard(
            title = s.soundOf(connection.remote.name),
            icon = AppIcons.VolumeUp,
            actions = {
                IconChoice(AppIcons.Mic, s.audioMic, selected = !systemAudio, onClick = {
                    systemAudio = false
                    if (audio.active) controller.startAudioStream(system = false)
                })
                IconChoice(AppIcons.VolumeUp, s.audioSystem, selected = systemAudio, onClick = {
                    systemAudio = true
                    if (audio.active) controller.startAudioStream(system = true)
                })
                if (audio.active) {
                    IconAction(AppIcons.Stop, s.stopAudio, { controller.stopAudioStream() })
                } else {
                    IconAction(AppIcons.PlayArrow, s.startAudio, { controller.startAudioStream(systemAudio) })
                }
            },
        ) {
            Hint(
                if (audio.active) s.audioStats(audio.sampleRate, audio.channels, audio.receivedChunks)
                else s.audioIdle(if (systemAudio) s.audioSystem else s.audioMic),
            )
            audio.error?.let { ErrorText(s.streamError(it)) }
            Text(s.mediaHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
