package ru.efir.core.media

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ru.efir.core.media.EfirAndroidEnvironment
import ru.efir.core.protocol.AudioSource
import ru.efir.core.protocol.nowMillis

actual fun audioCaptureSupported(): Boolean = true

/**
 * Android-источник захвата звука.
 *
 * - [AudioSource.MICROPHONE] — `AudioRecord` с микрофона (PCM16);
 * - [AudioSource.SYSTEM] — `AudioPlaybackCapture` (Android 10+), требует выданного
 *   разрешения MediaProjection; приложения с `ALLOW_CAPTURE_BY_NONE` не захватываются.
 */
private class AndroidAudioCaptureSource(
    private val context: Context,
) : AudioCaptureSource {

    private var record: AudioRecord? = null
    private var quality: AudioQuality = AudioQuality()
    private var projection: MediaProjection? = null

    @SuppressLint("MissingPermission")
    override suspend fun start(quality: AudioQuality): AudioQuality {
        this.quality = quality
        val channelMask = if (quality.channels == 2) {
            AudioFormat.CHANNEL_IN_STEREO
        } else {
            AudioFormat.CHANNEL_IN_MONO
        }
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(quality.sampleRate, channelMask, encoding)
            .coerceAtLeast(quality.sampleRate / 5) // ~200 мс

        val recorder = if (quality.source == AudioSource.SYSTEM) {
            createPlaybackCapture(minBuffer, quality.sampleRate, channelMask, encoding)
        } else {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                quality.sampleRate,
                channelMask,
                encoding,
                minBuffer,
            )
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { recorder.release() }
            throw IllegalStateException("Не удалось инициализировать захват звука")
        }
        recorder.startRecording()
        record = recorder
        return quality.copy(codec = "pcm16")
    }

    /** Настраивает захват системного звука через MediaProjection (Android 10+). */
    private fun createPlaybackCapture(
        bufferSize: Int,
        sampleRate: Int,
        channelMask: Int,
        encoding: Int,
    ): AudioRecord {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw UnsupportedOperationException("Захват системного звука доступен с Android 10")
        }
        val permission = EfirAndroidEnvironment.projectionPermission
            ?: throw IllegalStateException("Нужно разрешение MediaProjection для захвата системного звука")
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = manager.getMediaProjection(permission.resultCode, permission.data)
            ?: throw IllegalStateException("Не удалось создать MediaProjection")
        projection = mp

        val config = AudioPlaybackCaptureConfiguration.Builder(mp)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()
        return AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(config)
            .build()
    }

    override fun chunks(): Flow<CapturedAudioChunk> = flow {
        val recorder = record ?: throw IllegalStateException("Захват звука не запущен")
        val q = quality
        val bufferSize = recorder.bufferSizeInFrames.coerceAtLeast(q.sampleRate / 10) *
            q.channels * 2
        val buffer = ByteArray(bufferSize.coerceAtLeast(q.sampleRate / 10 * q.channels * 2))
        while (currentCoroutineContext().isActive) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read <= 0) continue
            emit(
                CapturedAudioChunk(
                    sampleRate = q.sampleRate,
                    channels = q.channels,
                    codec = "pcm16",
                    timestamp = nowMillis(),
                    payload = buffer.copyOfRange(0, read),
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            record = null
            runCatching { projection?.stop() }
            projection = null
        }
    }
}

/**
 * Проигрыватель на Android: используется только если Android-устройство выступает
 * клиентом (не типичный сценарий). Реализован минимально через `AudioTrack`-совместимый
 * контракт — здесь no-op, воспроизведение на телефоне не требуется.
 */
private class NoopAudioPlayer : AudioPlayer {
    override fun play(chunk: CapturedAudioChunk) = Unit
    override fun stop() = Unit
}

actual fun createAudioCaptureSource(): AudioCaptureSource {
    val context = EfirAndroidEnvironment.appContext
        ?: throw IllegalStateException("EfirAndroidEnvironment.appContext не инициализирован")
    return AndroidAudioCaptureSource(context)
}

actual fun createAudioPlayer(): AudioPlayer = NoopAudioPlayer()
