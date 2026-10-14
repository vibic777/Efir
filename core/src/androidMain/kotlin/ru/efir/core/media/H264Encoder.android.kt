package ru.efir.core.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ru.efir.core.protocol.nowMillis

/**
 * Аппаратный H.264-энкодер на базе `MediaCodec` (блок 13).
 *
 * Кодирует кадры, приходящие на входную [Surface] (её потребителем выступает
 * `VirtualDisplay`/`Camera`), и отдаёт готовые Annex-B кадры потоком
 * [CapturedScreenFrame] с MIME [`MIME_H264`]. Кадр конфигурации (CSD: SPS/PPS)
 * помечается [CapturedScreenFrame.config], ключевые — [CapturedScreenFrame.keyframe].
 *
 * Класс не потокобезопасен: методы `start`/`drain`/`stop` вызываются из одного
 * контекста (хост-стрим).
 */
internal class AndroidH264Encoder(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int,
    private val frameRate: Int,
    private val iFrameIntervalSec: Int = 2,
) {

    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null

    /** Входная поверхность кодера. Создаётся после [start]. */
    val surface: Surface get() = inputSurface ?: error("Энкодер не запущен")

    /** Настраивает и запускает `MediaCodec`. Возвращает успех. */
    fun start(): Boolean = runCatching {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate.coerceAtLeast(100_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate.coerceIn(1, 120))
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameIntervalSec)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = encoder.createInputSurface()
        encoder.start()
        codec = encoder
        true
    }.getOrDefault(false)

    /** Меняет целевой битрейт на лету (адаптивный битрейт). */
    fun setBitrate(bps: Int) {
        runCatching {
            val params = android.os.Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bps.coerceAtLeast(100_000))
            }
            codec?.setParameters(params)
        }
    }

    /** Запрашивает внеплановый ключевой кадр. */
    fun requestKeyFrame() {
        runCatching {
            val params = android.os.Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            codec?.setParameters(params)
        }
    }

    /**
     * Поток закодированных кадров. Читает выходной буфер `MediaCodec` до закрытия
     * потока. Каждый кадр копируется в отдельный массив и освобождается буфер.
     */
    fun drain(): Flow<CapturedScreenFrame> = callbackFlow {
        val encoder = codec ?: error("Энкодер не запущен")
        val info = MediaCodec.BufferInfo()
        var running = true
        while (running) {
            val index = try {
                encoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            } catch (e: IllegalStateException) {
                break
            }
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> continue
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> continue
                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val isKey = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        val payload = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.get(payload)
                        trySend(
                            CapturedScreenFrame(
                                size = CaptureSize(width, height),
                                mimeType = MIME_H264,
                                timestamp = nowMillis(),
                                payload = payload,
                                keyframe = isKey || isConfig,
                                config = isConfig,
                            ),
                        )
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        running = false
                    }
                }
            }
        }
        awaitClose { /* кодек останавливается в stop() */ }
    }

    /** Останавливает и освобождает `MediaCodec` и входную поверхность. */
    fun stop() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { inputSurface?.release() }
        inputSurface = null
    }

    private companion object {
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}

/** Поддерживает ли устройство аппаратное кодирование H.264 (Surface). */
internal fun h264EncodingSupported(): Boolean = runCatching {
    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    val caps = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
    val ok = caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
    codec.release()
    ok
}.getOrDefault(false)

