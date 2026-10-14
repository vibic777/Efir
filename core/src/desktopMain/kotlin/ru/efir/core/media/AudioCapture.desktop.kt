package ru.efir.core.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/** Источник-заглушка: захват звука на Desktop не поддерживается. */
private class UnsupportedAudioCapture : AudioCaptureSource {
    override suspend fun start(quality: AudioQuality): AudioQuality =
        throw UnsupportedOperationException("Захват звука не поддерживается на Desktop")

    override fun chunks(): Flow<CapturedAudioChunk> = flow { }

    override suspend fun stop() = Unit
}

/**
 * Проигрыватель звука на Desktop через `javax.sound.sampled`.
 * Поддерживает PCM16LE (формат захвата микрофона); иные кодеки игнорируются.
 */
private class JavaSoundAudioPlayer : AudioPlayer {

    private var line: SourceDataLine? = null
    private var format: AudioFormat? = null

    override fun play(chunk: CapturedAudioChunk) {
        if (chunk.codec != "pcm16") return
        runCatching {
            val fmt = AudioFormat(
                chunk.sampleRate.toFloat(),
                16,
                chunk.channels,
                true,
                false, // little-endian
            )
            if (line == null || format != fmt) {
                line?.close()
                val info = DataLine.Info(SourceDataLine::class.java, fmt)
                val newLine = AudioSystem.getLine(info) as SourceDataLine
                newLine.open(fmt)
                newLine.start()
                line = newLine
                format = fmt
            }
            line?.write(chunk.payload, 0, chunk.payload.size)
        }
    }

    override fun stop() {
        runCatching {
            line?.stop()
            line?.close()
        }
        line = null
        format = null
    }
}

actual fun audioCaptureSupported(): Boolean = false

actual fun createAudioCaptureSource(): AudioCaptureSource = UnsupportedAudioCapture()

actual fun createAudioPlayer(): AudioPlayer = JavaSoundAudioPlayer()
