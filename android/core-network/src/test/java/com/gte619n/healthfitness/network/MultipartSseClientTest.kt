package com.gte619n.healthfitness.network

import app.cash.turbine.test
import com.gte619n.healthfitness.network.sse.MultipartSseClient
import com.gte619n.healthfitness.network.sse.MultipartSseEvent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class MultipartSseClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `posts multipart body with file part and Accept event-stream header`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: ok\n\n"),
        )

        val bytes = byteArrayOf(0x25, 0x50, 0x44, 0x46) // "%PDF" magic
        val sse = MultipartSseClient(client)

        sse.stream(
            url = server.url("/api/me/blood/reports"),
            parts = listOf(
                MultipartSseClient.Part(
                    name = "file",
                    fileName = "report.pdf",
                    contentType = "application/pdf".toMediaType(),
                    body = bytes,
                ),
            ),
        ).test(timeout = 5.seconds) {
            val event = awaitItem()
            assertEquals("ok", event.data)
            awaitComplete()
        }

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("text/event-stream", recorded.getHeader("Accept"))
        val contentType = recorded.getHeader("Content-Type")
        assertNotNull(contentType)
        assertTrue(
            "expected multipart/form-data, got $contentType",
            contentType!!.startsWith("multipart/form-data"),
        )
        // The boundary appears both in the Content-Type and the body. The
        // PDF magic bytes must round-trip into the body untouched.
        val bodyText = recorded.body.readUtf8()
        assertTrue(bodyText.contains("name=\"file\""))
        assertTrue(bodyText.contains("filename=\"report.pdf\""))
        assertTrue(bodyText.contains("Content-Type: application/pdf"))
        assertTrue(bodyText.contains("%PDF"))
    }

    @Test
    fun `decodes two SSE events separated by blank line in order`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    buildString {
                        append("data: {\"phase\":\"uploading\"}\n\n")
                        append("event: phase\n")
                        append("data: {\"phase\":\"extracting\"}\n\n")
                    },
                ),
        )

        val sse = MultipartSseClient(client)
        sse.stream(
            url = server.url("/upload"),
            parts = listOf(
                MultipartSseClient.Part(
                    name = "file",
                    fileName = "x.pdf",
                    contentType = "application/pdf".toMediaType(),
                    body = ByteArray(0),
                ),
            ),
        ).test(timeout = 5.seconds) {
            val first = awaitItem()
            assertNull(first.event)
            assertEquals("{\"phase\":\"uploading\"}", first.data)

            val second = awaitItem()
            assertEquals("phase", second.event)
            assertEquals("{\"phase\":\"extracting\"}", second.data)

            awaitComplete()
        }
    }

    @Test
    fun `joins multiple data lines with newline`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: line1\ndata: line2\n\n"),
        )

        val sse = MultipartSseClient(client)
        sse.stream(
            url = server.url("/multi"),
            parts = listOf(
                MultipartSseClient.Part(
                    name = "file",
                    fileName = "x.pdf",
                    contentType = "application/pdf".toMediaType(),
                    body = ByteArray(0),
                ),
            ),
        ).test(timeout = 5.seconds) {
            val event = awaitItem()
            assertEquals("line1\nline2", event.data)
            awaitComplete()
        }
    }

    @Test
    fun `non-2xx response throws IOException`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody("boom"),
        )
        val sse = MultipartSseClient(client)
        sse.stream(
            url = server.url("/fail"),
            parts = listOf(
                MultipartSseClient.Part(
                    name = "file",
                    fileName = "x.pdf",
                    contentType = "application/pdf".toMediaType(),
                    body = ByteArray(0),
                ),
            ),
        ).test(timeout = 5.seconds) {
            val error = awaitError()
            assertTrue(error.message?.contains("HTTP 500") == true)
        }
    }

    @Test
    fun `flushes final event without trailing blank line`() = runTest {
        // Server emits an event but never sends the blank-line terminator
        // before closing the stream. The parser should still flush.
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: trailing\n")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END),
        )
        val sse = MultipartSseClient(client)
        var received: MultipartSseEvent? = null
        sse.stream(
            url = server.url("/trailing"),
            parts = listOf(
                MultipartSseClient.Part(
                    name = "file",
                    fileName = "x.pdf",
                    contentType = "application/pdf".toMediaType(),
                    body = ByteArray(0),
                ),
            ),
        ).test(timeout = 5.seconds) {
            received = awaitItem()
            // Either complete or terminal error — both are acceptable.
            cancelAndConsumeRemainingEvents()
        }
        assertEquals("trailing", received?.data)
    }
}
