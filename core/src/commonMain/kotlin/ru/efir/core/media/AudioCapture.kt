package ru.efir.core.media

import kotlinx.coroutines.flow.Flow
import ru.efir.core.protocol.AudioSource
import ru.efir.core.protocol.AudioStart

/** Настройки захвата звука. */
data class AudioQuality(
    val source: AudioSource = AudioSource.MICROPHONE,
    val sampleRate: Int = 48_000,
    val channels: Int = 1,
    val bitrate: Int = 128_000,
    val codec: String = "pcm16",
) {
    init {
        require(sampleRate in 8_000..192_000) { "sampleRate вне диапазона: $sampleRate" }
        require(channels in 1..2) { "channels вне диапазона: $channels" }
    }

    companion object {
        fun from(start: AudioStart): AudioQuality = AudioQuality(
            source = start.source,
            sampleRate = start.sampleRate,
            channels = start.channels,
            codec = start.codec,
        )
    }
}

/** Один блок (чанк) захваченного звука. */
data class CapturedAudioChunk(
    val sampleRate: Int,
    val channels: Int,
    val codec: String,
    val timestamp: Long,
    val payload: ByteArray,
)

/**
 * Источник захвата звука (платформенный).
 * Android — `AudioRecord` (микрофон) или `AudioPlaybackCapture` (системный звук).
 */
interface AudioCaptureSource {
    suspend fun start(quality: AudioQuality): AudioQuality
    fun chunks(): Flow<CapturedAudioChunk>
    suspend fun stop()
}

/** Воспроизведение принятого звука на принимающей стороне. */
interface AudioPlayer {
    fun play(chunk: CapturedAudioChunk)
    fun stop()
}

/** Поддерживает ли платформа захват звука. */
expect fun audioCaptureSupported(): Boolean

/** Создаёт платформенный источник захвата звука. */
expect fun createAudioCaptureSource(): AudioCaptureSource

/** Создаёт платформенный проигрыватель звука (Windows — `javax.sound`). */
expect fun createAudioPlayer(): AudioPlayer
