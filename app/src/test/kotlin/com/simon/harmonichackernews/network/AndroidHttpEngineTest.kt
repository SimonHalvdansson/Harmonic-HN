package com.simon.harmonichackernews.network

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.Collections
import kotlin.concurrent.thread

class AndroidHttpEngineTest {
    @Test
    fun enginePreservesKtorRedirectPolicyAndDoesNotReplayFailedWrites() = runBlocking {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val serving = thread(isDaemon = true) {
            try {
                while (!server.isClosed) {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val reader = socket.getInputStream().bufferedReader()
                        val request = reader.readLine() ?: return@use
                        requests += request
                        var contentLength = 0
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = header.substringAfter(':').trim().toInt()
                            }
                        }
                        repeat(contentLength) { reader.read() }
                        // Simulate the server consuming a write before its connection fails.
                        if (request.contains("/disconnect ")) return@use
                        val redirect = request.contains("/redirect ")
                        val response = if (redirect) {
                            "HTTP/1.1 302 Found\r\nLocation: /target\r\nContent-Length: 0\r\n"
                        } else {
                            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n"
                        }
                        socket.getOutputStream().apply {
                            val body = if (redirect) "" else "ok"
                            write((response + "Connection: close\r\n\r\n" + body).toByteArray())
                            flush()
                        }
                    }
                }
            } catch (_: SocketException) {
                check(server.isClosed)
            }
        }
        val client = createHarmonicHttpClient(createAndroidHttpEngine(), "test")
        val base = "http://127.0.0.1:${server.localPort}"
        try {
            assertEquals("ok", client.get("$base/redirect").bodyAsText())
            assertEquals(302, client.post("$base/redirect") { setBody("id=42") }.status.value)
            val failedWrite = runCatching { client.post("$base/disconnect") { setBody("id=42") } }
            assertTrue(failedWrite.exceptionOrNull() is IOException)
            assertEquals(
                listOf(
                    "GET /redirect HTTP/1.1",
                    "GET /target HTTP/1.1",
                    "POST /redirect HTTP/1.1",
                    "POST /disconnect HTTP/1.1",
                ),
                requests.toList(),
            )
        } finally {
            client.close()
            server.close()
            serving.join(5_000)
        }
    }
}
