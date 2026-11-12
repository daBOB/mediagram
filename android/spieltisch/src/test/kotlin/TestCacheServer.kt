package com.mediagram.spieltisch

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A home cache server on loopback that serves [files] by path and remembers every path it was asked for. */
class TestCacheServer : AutoCloseable {
    val files = ConcurrentHashMap<String, ByteArray>()
    val asked = CopyOnWriteArrayList<String>()

    /** How long each answer waits, as a server across a slow network would. */
    @Volatile var delayMs = 0L

    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                asked += exchange.requestURI.path
                Thread.sleep(delayMs)
                val body = files[exchange.requestURI.path]
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            start()
        }

    val url get() = "http://127.0.0.1:${server.address.port}"

    override fun close() = server.stop(0)
}
