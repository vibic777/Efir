package ru.efir.core.protocol

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RpcSessionTest {

    @Test
    fun requestResponseRoundTrip() = runTest {
        // Кольцевая проводка: sink клиента → onEnvelope сервера и наоборот.
        lateinit var client: RpcSession

        val server = RpcSession(
            sink = EnvelopeSink { envelope -> client.onEnvelope(envelope) },
            scope = this,
        )
        server.onRequest<RequestCapabilities> {
            CapabilitiesResult(setOf(Capability.CLIPBOARD))
        }

        client = RpcSession(
            sink = EnvelopeSink { envelope -> server.onEnvelope(envelope) },
            scope = this,
        )

        val response = client.call(RequestCapabilities, timeoutMs = 5_000)
        val result = assertIs<CapabilitiesResult>(response)
        assertEquals(setOf(Capability.CLIPBOARD), result.capabilities)
    }

    @Test
    fun eventDelivery() = runTest {
        val received = mutableListOf<String>()

        val session = RpcSession(sink = EnvelopeSink { }, scope = this)
        session.onEvent<ClipboardChanged> { received.add(it.text) }

        session.onEnvelope(
            Envelope(id = "e1", timestamp = 1L, body = ClipboardChanged(text = "hello")),
        )
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("hello"), received)
    }

    @Test
    fun unhandledRequestProducesError() = runTest {
        val errors = mutableListOf<String>()

        // Сессия без обработчиков: любой запрос должен привести к ErrorMessage на sink.
        val session = RpcSession(
            sink = EnvelopeSink { envelope ->
                val body = envelope.body
                if (body is ErrorMessage) errors.add(body.code)
            },
            scope = this,
        )

        session.onEnvelope(
            Envelope(id = "r1", timestamp = 1L, body = FsMkdir(path = "/x")),
        )
        testScheduler.advanceUntilIdle()

        assertTrue(errors.contains("unhandled_request"), "Ожидалась ошибка unhandled_request, получено: $errors")
    }

    @Test
    fun handlerErrorIsWrapped() = runTest {
        val errors = mutableListOf<String>()

        val session = RpcSession(
            sink = EnvelopeSink { envelope ->
                val body = envelope.body
                if (body is ErrorMessage) errors.add(body.code)
            },
            scope = this,
        )
        session.onRequest<FsMkdir> { error("boom") }

        session.onEnvelope(
            Envelope(id = "r2", timestamp = 1L, body = FsMkdir(path = "/x")),
        )
        testScheduler.advanceUntilIdle()

        assertTrue(errors.contains("handler_error"), "Ожидалась ошибка handler_error, получено: $errors")
    }

    @Test
    fun newerProtocolVersionIsRejected() = runTest {
        val sent = mutableListOf<Message>()

        val session = RpcSession(sink = EnvelopeSink { envelope -> sent.add(envelope.body) }, scope = this)
        session.onEnvelope(
            Envelope(
                protocolVersion = PROTOCOL_VERSION + 1,
                id = "future",
                timestamp = 1L,
                body = Ping(nonce = 1L),
            ),
        )
        testScheduler.advanceUntilIdle()

        val error = sent.filterIsInstance<ErrorMessage>().firstOrNull()
        assertTrue(error != null && error.code == "protocol_version_mismatch")
    }
}
