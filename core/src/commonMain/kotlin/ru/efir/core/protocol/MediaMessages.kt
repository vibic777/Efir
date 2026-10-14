package ru.efir.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Экран
// ---------------------------------------------------------------------------

/** Запуск захвата экрана Android и трансляции на ПК. */
@Serializable
@SerialName("screenStart")
data class ScreenStart(
    val maxWidth: Int = 1280,
    val maxHeight: Int = 720,
    val bitrate: Int = 4_000_000,
    val fps: Int = 30,
    val codec: String = "h264",
    /** Качество JPEG-кадра (1..100). */
    val jpegQuality: Int = 70,
    /** Включить адаптивное снижение качества под доступную пропускную способность. */
    val adaptive: Boolean = true,
) : RpcRequest

/** Экран захвачен, поток пошёл. */
@Serializable
@SerialName("screenStarted")
data class ScreenStarted(
    val sessionId: String,
    val width: Int,
    val height: Int,
    val codec: String,
) : RpcResponse

/** Остановка захвата экрана. */
@Serializable
@SerialName("screenStop")
object ScreenStop : RpcRequest

/** Захват экрана остановлен. */
@Serializable
@SerialName("screenStopped")
data class ScreenStopped(
    val sessionId: String,
) : RpcResponse

/**
 * Показывающая сторона сама закончила показ: его остановили на устройстве, отозвали
 * разрешение или захват сорвался. [reason] — код причины (`screen_stopped`, `screen_failed`).
 */
@Serializable
@SerialName("screenEnded")
data class ScreenEnded(
    val sessionId: String,
    val reason: String = "",
) : Event

// ---------------------------------------------------------------------------
// Камера
// ---------------------------------------------------------------------------

/** Какая камера используется. */
@Serializable
enum class CameraFacing { BACK, FRONT }

/** Запуск трансляции камеры. */
@Serializable
@SerialName("cameraStart")
data class CameraStart(
    val facing: CameraFacing = CameraFacing.BACK,
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitrate: Int = 4_000_000,
    /** Кодек потока: `jpeg` (по умолчанию) или `h264` (аппаратное кодирование). */
    val codec: String = "jpeg",
    /** Качество JPEG-кадра (1..100). */
    val jpegQuality: Int = 70,
    /** Включить адаптивное снижение качества под доступную пропускную способность. */
    val adaptive: Boolean = true,
) : RpcRequest

/** Камера запущена. */
@Serializable
@SerialName("cameraStarted")
data class CameraStarted(
    val sessionId: String,
    val width: Int,
    val height: Int,
    val codec: String,
) : RpcResponse

/** Остановка камеры. */
@Serializable
@SerialName("cameraStop")
object CameraStop : RpcRequest

/** Камера остановлена. */
@Serializable
@SerialName("cameraStopped")
data class CameraStopped(
    val sessionId: String,
) : RpcResponse

// ---------------------------------------------------------------------------
// Звук
// ---------------------------------------------------------------------------

/** Источник звука. */
@Serializable
enum class AudioSource { MICROPHONE, SYSTEM }

/** Запуск передачи звука. */
@Serializable
@SerialName("audioStart")
data class AudioStart(
    val source: AudioSource = AudioSource.MICROPHONE,
    val sampleRate: Int = 48_000,
    val channels: Int = 1,
    val codec: String = "aac",
) : RpcRequest

/** Звук запущен. */
@Serializable
@SerialName("audioStarted")
data class AudioStarted(
    val sessionId: String,
    val sampleRate: Int,
    val channels: Int,
    val codec: String,
) : RpcResponse

/** Остановка звука. */
@Serializable
@SerialName("audioStop")
object AudioStop : RpcRequest

/** Звук остановлен. */
@Serializable
@SerialName("audioStopped")
data class AudioStopped(
    val sessionId: String,
) : RpcResponse
