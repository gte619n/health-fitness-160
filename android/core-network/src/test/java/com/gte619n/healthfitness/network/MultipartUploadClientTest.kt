package com.gte619n.healthfitness.network

import com.gte619n.healthfitness.network.upload.MultipartUploadClient
import com.gte619n.healthfitness.network.upload.PendingUpload
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * MockWebServer round-trip checks for [MultipartUploadClient]. Verifies:
 *  - The request is POST with a `multipart/form-data` body that carries
 *    the right field name, filename, content-type, and body bytes.
 *  - 2xx responses are parsed and returned as [Result.success].
 *  - Non-2xx responses surface as [Result.failure] wrapping an
 *    `IOException` whose message includes the HTTP code.
 */
class MultipartUploadClientTest {

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
    fun `posts multipart body with expected field name and content type`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val uploader = MultipartUploadClient(client)
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG magic

        val result = uploader.upload(
            url = server.url("/api/me/gyms/loc_1/photo"),
            upload = PendingUpload(
                filename = "cover.png",
                mimeType = "image/png",
                source = { ByteArrayInputStream(bytes) },
            ),
            fieldName = "file",
            parse = { it.string() },
        )

        assertTrue(result.isSuccess)
        assertEquals("""{"ok":true}""", result.getOrNull())

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        val contentType = recorded.getHeader("Content-Type")
        assertNotNull(contentType)
        assertTrue(
            "expected multipart/form-data, got $contentType",
            contentType!!.startsWith("multipart/form-data"),
        )
        val rawBody = recorded.body.readByteArray()
        val bodyText = String(rawBody, Charsets.ISO_8859_1)
        assertTrue(
            "expected file field, got: $bodyText",
            bodyText.contains("name=\"file\""),
        )
        assertTrue(
            "expected filename, got: $bodyText",
            bodyText.contains("filename=\"cover.png\""),
        )
        assertTrue(
            "expected image/png content type, got: $bodyText",
            bodyText.contains("Content-Type: image/png"),
        )
        // PNG magic bytes must round-trip through the multipart body
        // untouched. Search the raw bytes; UTF-8 decoding mangles the
        // 0x89 byte and the substring search would always fail.
        val magic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        assertTrue(
            "expected raw PNG bytes in body",
            indexOfBytes(rawBody, magic) >= 0,
        )
    }

    @Test
    fun `returns Result success when parser succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("location-id-42"))
        val uploader = MultipartUploadClient(client)

        val result = uploader.upload(
            url = server.url("/upload"),
            upload = PendingUpload(
                filename = "x.jpg",
                mimeType = "image/jpeg",
                source = { ByteArrayInputStream(ByteArray(0)) },
            ),
        ) { it.string() }

        assertTrue(result.isSuccess)
        assertEquals("location-id-42", result.getOrNull())
    }

    @Test
    fun `returns Result failure on non-2xx with HTTP code in message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(413).setBody("too big"))
        val uploader = MultipartUploadClient(client)

        val result = uploader.upload(
            url = server.url("/fail"),
            upload = PendingUpload(
                filename = "x.jpg",
                mimeType = "image/jpeg",
                source = { ByteArrayInputStream(ByteArray(0)) },
            ),
        ) { it.string() }

        assertTrue(result.isFailure)
        val cause = result.exceptionOrNull()
        assertNotNull(cause)
        assertTrue(cause is IOException)
        assertTrue(
            "expected HTTP 413 in message, got: ${cause?.message}",
            cause?.message?.contains("HTTP 413") == true,
        )
    }

    @Test
    fun `uses custom field name when supplied`() = runTest {
        server.enqueue(MockResponse().setBody(""))
        val uploader = MultipartUploadClient(client)

        uploader.upload(
            url = server.url("/upload"),
            upload = PendingUpload(
                filename = "report.pdf",
                mimeType = "application/pdf",
                source = { ByteArrayInputStream(ByteArray(0)) },
            ),
            fieldName = "document",
        ) { it.string() }

        val recorded = server.takeRequest()
        val bodyText = recorded.body.readUtf8()
        assertTrue(
            "expected document field, got: $bodyText",
            bodyText.contains("name=\"document\""),
        )
        // The default 'file' field must NOT be present
        assertNull(
            "did not expect default 'file' field, body was: $bodyText",
            bodyText.lineSequence().firstOrNull { it.contains("name=\"file\"") },
        )
    }

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
