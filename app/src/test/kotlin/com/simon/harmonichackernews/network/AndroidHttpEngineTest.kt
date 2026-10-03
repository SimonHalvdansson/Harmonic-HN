package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.http.URLProtocol
import okhttp3.Dns
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class AndroidHttpEngineTest {
    @Test
    fun feedReadsRecoverWhenIdleConnectionsExhaustRequestRetries() = runBlocking {
        // Exercise the real API, including its three request attempts. Multiple idle sockets
        // can each fail once after backgrounding; request retries alone can exhaust their budget.
        for (readOnly in listOf(false, true)) {
            val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            val warmed = CountDownLatch(4)
            val sockets = Collections.synchronizedList(mutableListOf<Socket>())
            val workers = Collections.synchronizedList(mutableListOf<Thread>())
            val serving = thread(isDaemon = true) {
                try {
                    var accepted = 0
                    while (!server.isClosed) {
                        val socket = server.accept()
                        sockets += socket
                        val stale = accepted++ < 4
                        workers += thread(isDaemon = true) {
                            try {
                                socket.use {
                                    socket.soTimeout = 10_000
                                    val reader = socket.getInputStream().bufferedReader()
                                    while (!reader.readLine().isNullOrEmpty()) { }
                                    if (stale) {
                                        warmed.countDown()
                                        check(warmed.await(5, TimeUnit.SECONDS))
                                    }
                                    socket.getOutputStream().apply {
                                        val connection = if (stale) "" else "Connection: close\r\n"
                                        write(("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n" +
                                            connection + "\r\n[42]").toByteArray())
                                        flush()
                                    }
                                    // Wait until reuse before dropping the response, so this is
                                    // a connection failure and not an already-detected idle EOF.
                                    if (stale) while (!reader.readLine().isNullOrEmpty()) { }
                                }
                            } catch (_: SocketException) {
                                check(server.isClosed)
                            }
                        }
                    }
                } catch (_: SocketException) {
                    check(server.isClosed)
                }
            }
            val client = createHarmonicHttpClient(createAndroidHttpEngine(readOnly), "test")
            client.plugin(HttpSend).intercept { request ->
                request.url.protocol = URLProtocol.HTTP
                request.url.host = "127.0.0.1"
                request.url.port = server.localPort
                execute(request)
            }
            try {
                List(4) { async { client.get("http://127.0.0.1:${server.localPort}/warm").bodyAsText() } }.awaitAll()
                val result = runCatching { KtorHackerNewsApi(client).getStoryIds(StoryType.TOP_STORIES) }
                if (readOnly) assertEquals(listOf(42), result.getOrThrow())
                else assertTrue(result.exceptionOrNull() is IOException)
            } finally {
                client.close()
                server.close()
                serving.join(5_000)
                sockets.forEach { it.close() }
                workers.forEach { it.join(5_000) }
            }
        }
    }

    @Test
    fun contentReadsRecoverWhenTheFirstServerAddressIsUnreachable() = runBlocking {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serving = thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                        flush()
                    }
                }
            } catch (_: SocketException) {
                check(server.isClosed)
            }
        }
        val engine = createAndroidHttpEngine(readOnly = true)
        (engine.config as OkHttpConfig).apply {
            dns = Dns { listOf(InetAddress.getByName("127.0.0.2"), InetAddress.getByName("127.0.0.1")) }
            config { fastFallback(false) }
        }
        val client = createHarmonicHttpClient(engine, "test")
        try {
            assertEquals("ok", client.get("http://feed.test:${server.localPort}/topstories.json").bodyAsText())
        } finally {
            client.close()
            server.close()
            serving.join(5_000)
        }
    }

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
            // HN also mutates state through GET endpoints such as /vote and /fave.
            val failedGetAction = runCatching { client.get("$base/disconnect") }
            assertTrue(failedGetAction.exceptionOrNull() is IOException)
            assertEquals(
                listOf(
                    "GET /redirect HTTP/1.1",
                    "GET /target HTTP/1.1",
                    "POST /redirect HTTP/1.1",
                    "POST /disconnect HTTP/1.1",
                    "GET /disconnect HTTP/1.1",
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
