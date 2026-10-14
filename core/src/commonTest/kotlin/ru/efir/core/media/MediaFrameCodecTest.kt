package ru.efir.core.media

import ru.efir.core.media.MediaFrameCodec.MediaFrameFormatException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaFrameCodecTest {

    @Test
    fun imageFrameRoundTrip() {
        val frame = MediaFrame.Image(
            kind = MediaFrameKind.SCREEN,
            sessionId = "session-42",
            sequence = 7L,
            timestamp = 1_700_000_000_123L,
            keyframe = true,
            width = 1080,
            height = 2400,
            mimeType = "image/jpeg",
            payload = byteArrayOf(10, 20, 30, 40, 50),
        )

        val decoded = MediaFrameCodec.decode(MediaFrameCodec.encode(frame)) as MediaFrame.Image

        assertEquals(MediaFrameKind.SCREEN, decoded.kind)
        assertEquals("session-42", decoded.sessionId)
        assertEquals(7L, decoded.sequence)
        assertEquals(1_700_000_000_123L, decoded.timestamp)
        assertTrue(decoded.keyframe)
        assertEquals(1080, decoded.width)
        assertEquals(2400, decoded.height)
        assertEquals("image/jpeg", decoded.mimeType)
        assertContentEquals(frame.payload, decoded.payload)
    }

    @Test
    fun cameraNonKeyframeRoundTrip() {
        val frame = MediaFrame.Image(
            kind = MediaFrameKind.CAMERA,
            sessionId = "cam",
            sequence = 0L,
            timestamp = 1L,
            keyframe = false,
            width = 640,
            height = 480,
            mimeType = "image/jpeg",
            payload = ByteArray(0),
        )

        val decoded = MediaFrameCodec.decode(MediaFrameCodec.encode(frame)) as MediaFrame.Image
        assertEquals(MediaFrameKind.CAMERA, decoded.kind)
        assertFalse(decoded.keyframe)
        assertEquals(0, decoded.payloadSize)
    }

    @Test
    fun audioFrameRoundTrip() {
        val frame = MediaFrame.Audio(
            sessionId = "mic",
            sequence = 3L,
            timestamp = 99L,
            sampleRate = 48_000,
            channels = 1,
            codec = "pcm16",
            payload = ByteArray(1024) { it.toByte() },
        )

        val decoded = MediaFrameCodec.decode(MediaFrameCodec.encode(frame)) as MediaFrame.Audio
        assertEquals(MediaFrameKind.AUDIO, decoded.kind)
        assertEquals(48_000, decoded.sampleRate)
        assertEquals(1, decoded.channels)
        assertEquals("pcm16", decoded.codec)
        assertEquals(1024, decoded.payloadSize)
        assertContentEquals(frame.payload, decoded.payload)
    }

    @Test
    fun invalidMagicThrows() {
        val bytes = MediaFrameCodec.encode(
            MediaFrame.Image(
                kind = MediaFrameKind.SCREEN,
                sessionId = "s",
                sequence = 0,
                timestamp = 0,
                keyframe = true,
                width = 1,
                height = 1,
                mimeType = "image/jpeg",
                payload = ByteArray(0),
            ),
        )
        bytes[0] = 0x00
        assertFailsWith<MediaFrameFormatException> { MediaFrameCodec.decode(bytes) }
    }

    @Test
    fun truncatedFrameThrows() {
        assertFailsWith<IllegalArgumentException> { MediaFrameCodec.decode(byteArrayOf(1, 2, 3)) }
    }
}
