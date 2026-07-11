package com.module.notelycompose.platform

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Abstraktion über eine HTTP-Antwort, damit der Downloader transparent zwischen dem
 * normalen HttpURLConnection-Pfad und dem DoH-Fallback-Pfad wechseln kann.
 */
internal interface HttpSource {
    val code: Int
    val contentLength: Long
    fun header(name: String): String?
    fun body(): InputStream
    fun close()
}

/** Normaler Pfad: dünner Wrapper um eine bereits geöffnete HttpURLConnection. */
internal class UrlConnectionSource(private val connection: HttpURLConnection) : HttpSource {
    override val code: Int get() = connection.responseCode
    override val contentLength: Long get() = connection.contentLengthLong
    override fun header(name: String): String? = connection.getHeaderField(name)
    override fun body(): InputStream = connection.inputStream
    override fun close() = connection.disconnect()
}

/**
 * Fallback-Pfad für defektes lokales DNS: HTTPS-GET über einen manuell zu einer
 * (per DoH aufgelösten) IP aufgebauten TLS-Socket. HttpURLConnection bietet keinen
 * DNS-Hook, deshalb dieser minimale eigene Client.
 *
 * Sicherheitsäquivalent zum normalen Pfad: Die Zertifikatskette prüft die Standard-
 * SSLSocketFactory beim Handshake, den Hostnamen der Standard-HostnameVerifier
 * (Android/OkHttp, strikt) gegen den ORIGINALEN Hostnamen — nicht gegen die IP.
 * SNI setzt Android aus dem host-Parameter von createSocket().
 *
 * Requests gehen als HTTP/1.0 raus: damit antwortet der Server nie mit
 * Transfer-Encoding: chunked (Content-Length oder Close-getrennt), was den
 * Body-Parser trivial hält. Accept-Encoding: identity verhindert gzip.
 */
internal object DirectHttpsClient {

    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 30_000
    /** 64 KB statt 8-KB-Default — TLS-Records (16 KB) werden nicht zerstückelt. */
    private const val HEADER_BUFFER_BYTES = 64 * 1024

    /** [rangeEnd] inklusiv, -1 = offenes Range-Ende. */
    fun get(url: URL, ip: String, resumeFrom: Long, rangeEnd: Long = -1L): HttpSource {
        if (url.protocol != "https") throw IOException("DoH-Fallback unterstützt nur https (${url.protocol})")
        val host = url.host
        val port = if (url.port == -1) 443 else url.port

        val raw = Socket()
        val ssl: SSLSocket
        try {
            raw.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
            raw.soTimeout = READ_TIMEOUT_MS
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            // host-Parameter steuert SNI + Session-Cache; autoClose schließt raw mit.
            ssl = factory.createSocket(raw, host, port, true) as SSLSocket
            ssl.startHandshake()
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ssl.session)) {
                ssl.close()
                throw SSLPeerUnverifiedException("Zertifikat von $ip passt nicht zu $host")
            }
        } catch (e: Exception) {
            runCatching { raw.close() }
            throw if (e is IOException) e else IOException("TLS-Verbindung zu $host ($ip) fehlgeschlagen: ${e.message}", e)
        }

        try {
            val path = url.file.ifEmpty { "/" } // Pfad inkl. Query
            val request = buildString {
                append("GET ").append(path).append(" HTTP/1.0\r\n")
                append("Host: ").append(host).append("\r\n")
                append("User-Agent: MolyEcho-Android\r\n")
                append("Accept-Encoding: identity\r\n")
                // Immer mit Range — bei resumeFrom=0 dient die 206/200-Antwort als Probe
                // für Range-Unterstützung (Weiche für den parallelen Segment-Download).
                append("Range: bytes=").append(resumeFrom).append("-")
                if (rangeEnd >= 0) append(rangeEnd)
                append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            ssl.outputStream.write(request.toByteArray(Charsets.ISO_8859_1))
            ssl.outputStream.flush()

            val input = BufferedInputStream(ssl.inputStream, HEADER_BUFFER_BYTES)
            val statusLine = readLine(input) ?: throw IOException("Leere Antwort von $host ($ip)")
            val code = statusLine.split(" ").getOrNull(1)?.toIntOrNull()
                ?: throw IOException("Ungültige Statuszeile: $statusLine")

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }

            return object : HttpSource {
                override val code: Int = code
                override val contentLength: Long = headers["content-length"]?.toLongOrNull() ?: -1L
                override fun header(name: String): String? = headers[name.lowercase()]
                override fun body(): InputStream = input
                override fun close() = runCatching { ssl.close() }.let { }
            }
        } catch (e: Exception) {
            runCatching { ssl.close() }
            throw if (e is IOException) e else IOException("HTTP-Fehler über $host ($ip): ${e.message}", e)
        }
    }

    /** Liest eine CRLF-terminierte Zeile byteweise (kein Reader — der Body bleibt unangetastet). */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().removeSuffix("\r")
            sb.append(b.toChar())
        }
    }
}
