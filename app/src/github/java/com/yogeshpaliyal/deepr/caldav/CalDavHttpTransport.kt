package com.yogeshpaliyal.deepr.caldav

import android.annotation.SuppressLint
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.charset.Charset
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class CalDavHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String,
    val finalUrl: String,
)

interface CalDavHttpTransport {
    suspend fun execute(
        method: String,
        url: String,
        username: String,
        password: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        contentTypeHeader: String? = null,
        trustSelfSigned: Boolean = false,
    ): CalDavHttpResponse
}

class KtorCalDavHttpTransport(
    private val customEngine: HttpClientEngine? = null,
) : CalDavHttpTransport {
    private val standardClient: HttpClient by lazy {
        if (customEngine != null) {
            HttpClient(customEngine) {
                followRedirects = false
            }
        } else {
            HttpClient(CIO) {
                followRedirects = false
                engine {
                    requestTimeout = REQUEST_TIMEOUT_MS
                }
            }
        }
    }

    private val permissiveClient: HttpClient by lazy {
        if (customEngine != null) {
            HttpClient(customEngine) {
                followRedirects = false
            }
        } else {
            HttpClient(CIO) {
                followRedirects = false
                engine {
                    requestTimeout = REQUEST_TIMEOUT_MS
                    https {
                        trustManager = PermissiveX509TrustManager
                    }
                }
            }
        }
    }

    override suspend fun execute(
        method: String,
        url: String,
        username: String,
        password: String,
        headers: Map<String, String>,
        body: String?,
        contentTypeHeader: String?,
        trustSelfSigned: Boolean,
    ): CalDavHttpResponse {
        var currentUrl = url
        var redirectsRemaining = MAX_REDIRECTS

        while (true) {
            val response =
                executeSingleHop(
                    method = method,
                    url = currentUrl,
                    username = username,
                    password = password,
                    headers = headers,
                    body = body,
                    contentTypeHeader = contentTypeHeader,
                    trustSelfSigned = trustSelfSigned,
                )

            val location = response.headers["location"]
            if (response.statusCode in REDIRECT_CODES && !location.isNullOrBlank() && redirectsRemaining > 0) {
                currentUrl = resolveRedirectUrl(currentUrl, location)
                redirectsRemaining--
                continue
            }
            return response
        }
    }

    private suspend fun executeSingleHop(
        method: String,
        url: String,
        username: String,
        password: String,
        headers: Map<String, String>,
        body: String?,
        contentTypeHeader: String?,
        trustSelfSigned: Boolean,
    ): CalDavHttpResponse {
        val client = if (trustSelfSigned) permissiveClient else standardClient
        val authHeader = buildBasicAuthHeader(username, password)

        return try {
            val ktorResponse =
                client.request(url) {
                    this.method = HttpMethod.parse(method)
                    if (authHeader != null) {
                        header(HttpHeaders.Authorization, authHeader)
                    }
                    header(HttpHeaders.UserAgent, "Deepr-CalDAV/1.0")
                    headers.forEach { (key, value) ->
                        header(key, value)
                    }
                    if (body != null) {
                        contentTypeHeader?.let { rawContentType ->
                            runCatching { ContentType.parse(rawContentType) }.getOrNull()?.let { parsed ->
                                contentType(parsed)
                            } ?: header(HttpHeaders.ContentType, rawContentType)
                        }
                        setBody(body)
                    }
                }

            val lowerCaseHeaders = mutableMapOf<String, String>()
            ktorResponse.headers.forEach { name, values ->
                if (values.isNotEmpty()) {
                    lowerCaseHeaders[name.lowercase()] = values.last()
                }
            }
            CalDavHttpResponse(
                statusCode = ktorResponse.status.value,
                headers = lowerCaseHeaders,
                body = ktorResponse.bodyAsText(),
                finalUrl = url,
            )
        } catch (e: Exception) {
            // If self-signed certs are trusted and Ktor CIO rejects a missing IP/hostname SAN,
            // fall back to a raw TLS socket configured with the permissive TrustManager.
            if (trustSelfSigned && customEngine == null && isTlsFailure(e)) {
                RawTlsHttpFallback.execute(
                    method = method,
                    url = url,
                    authHeader = authHeader,
                    headers = headers,
                    body = body,
                    contentTypeHeader = contentTypeHeader,
                )
            } else {
                throw e
            }
        }
    }

    private fun isTlsFailure(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            val className = current::class.java.name
            val message = current.message.orEmpty()
            if (current is SSLException ||
                className.contains("TLSException") ||
                className.contains("CertificateException") ||
                message.contains("certificate", ignoreCase = true) ||
                message.contains("hostname", ignoreCase = true) ||
                message.contains("TLS", ignoreCase = false)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 25_000L
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        fun buildBasicAuthHeader(
            username: String,
            password: String,
        ): String? {
            if (username.isBlank() && password.isBlank()) return null
            val token =
                Base64
                    .getEncoder()
                    .encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
            return "Basic $token"
        }

        fun resolveRedirectUrl(
            baseUrl: String,
            location: String,
        ): String =
            try {
                URI(baseUrl).resolve(location).toString()
            } catch (_: Exception) {
                location
            }
    }
}

@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
object PermissiveX509TrustManager : X509TrustManager {
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private object RawTlsHttpFallback {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 25_000

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(PermissiveX509TrustManager), SecureRandom())
        }
    }

    suspend fun execute(
        method: String,
        url: String,
        authHeader: String?,
        headers: Map<String, String>,
        body: String?,
        contentTypeHeader: String?,
    ): CalDavHttpResponse =
        withContext(Dispatchers.IO) {
            val uri = URI(url)
            val isHttps = uri.scheme.equals("https", ignoreCase = true)
            val host = uri.host ?: throw IllegalArgumentException("Invalid URL host: $url")
            val port =
                when {
                    uri.port > 0 -> uri.port
                    isHttps -> 443
                    else -> 80
                }
            val rawPath =
                buildString {
                    append(uri.rawPath.orEmpty().ifEmpty { "/" })
                    if (!uri.rawQuery.isNullOrBlank()) {
                        append("?")
                        append(uri.rawQuery)
                    }
                }

            val socket: Socket =
                if (isHttps) {
                    val plainSocket = Socket()
                    plainSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    (sslContext.socketFactory.createSocket(plainSocket, host, port, true) as SSLSocket).apply {
                        soTimeout = READ_TIMEOUT_MS
                        startHandshake()
                    }
                } else {
                    Socket().apply {
                        connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                        soTimeout = READ_TIMEOUT_MS
                    }
                }

            socket.use { sock ->
                val bodyBytes = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                val defaultPort = if (isHttps) 443 else 80
                val hostHeader = if (port == defaultPort) host else "$host:$port"

                val requestHeader =
                    buildString {
                        append("$method $rawPath HTTP/1.1\r\n")
                        append("Host: $hostHeader\r\n")
                        append("User-Agent: Deepr-CalDAV/1.0\r\n")
                        append("Accept-Encoding: identity\r\n")
                        append("Connection: close\r\n")
                        if (authHeader != null) {
                            append("Authorization: $authHeader\r\n")
                        }
                        headers.forEach { (key, value) ->
                            append("$key: $value\r\n")
                        }
                        if (body != null) {
                            if (!contentTypeHeader.isNullOrBlank()) {
                                append("Content-Type: $contentTypeHeader\r\n")
                            }
                            append("Content-Length: ${bodyBytes.size}\r\n")
                        }
                        append("\r\n")
                    }

                val out = sock.getOutputStream()
                out.write(requestHeader.toByteArray(Charsets.US_ASCII))
                if (bodyBytes.isNotEmpty()) {
                    out.write(bodyBytes)
                }
                out.flush()

                val input = BufferedInputStream(sock.getInputStream())
                val statusLine = readAsciiLine(input) ?: throw IllegalStateException("Empty HTTP response from $url")
                val statusCode =
                    statusLine
                        .split(" ")
                        .getOrNull(1)
                        ?.toIntOrNull()
                        ?: throw IllegalStateException("Malformed HTTP status line: $statusLine")

                val responseHeaders = mutableMapOf<String, String>()
                while (true) {
                    val line = readAsciiLine(input) ?: break
                    if (line.isEmpty()) break
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val key = line.substring(0, colonIndex).trim().lowercase()
                        val value = line.substring(colonIndex + 1).trim()
                        responseHeaders[key] = value
                    }
                }

                val responseBytes = readResponseBody(input, statusCode, responseHeaders)
                val charset = parseCharset(responseHeaders["content-type"])
                CalDavHttpResponse(
                    statusCode = statusCode,
                    headers = responseHeaders,
                    body = String(responseBytes, charset),
                    finalUrl = url,
                )
            }
        }

    private fun readResponseBody(
        input: InputStream,
        statusCode: Int,
        headers: Map<String, String>,
    ): ByteArray {
        if (statusCode == 204 || statusCode == 304 || statusCode in 100..199) {
            return ByteArray(0)
        }
        val transferEncoding = headers["transfer-encoding"]?.lowercase().orEmpty()
        if (transferEncoding.contains("chunked")) {
            return readChunkedBody(input)
        }
        val contentLength = headers["content-length"]?.toIntOrNull()
        if (contentLength != null) {
            if (contentLength <= 0) return ByteArray(0)
            val buffer = ByteArray(contentLength)
            var offset = 0
            while (offset < contentLength) {
                val read = input.read(buffer, offset, contentLength - offset)
                if (read == -1) break
                offset += read
            }
            return if (offset == contentLength) buffer else buffer.copyOf(offset)
        }
        return input.readBytes()
    }

    private fun readChunkedBody(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readAsciiLine(input) ?: break
            val chunkSize = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (chunkSize == 0) {
                // Consume trailing headers
                while (true) {
                    val trailer = readAsciiLine(input)
                    if (trailer.isNullOrEmpty()) break
                }
                break
            }
            val chunkBuffer = ByteArray(chunkSize)
            var offset = 0
            while (offset < chunkSize) {
                val read = input.read(chunkBuffer, offset, chunkSize - offset)
                if (read == -1) break
                offset += read
            }
            output.write(chunkBuffer, 0, offset)
            readAsciiLine(input) // consume CRLF after chunk
        }
        return output.toByteArray()
    }

    private fun readAsciiLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        var b = input.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                bytes.write(b)
            }
            b = input.read()
        }
        return bytes.toString(Charsets.ISO_8859_1.name())
    }

    private fun parseCharset(contentType: String?): Charset {
        if (contentType == null) return Charsets.UTF_8
        val charsetPart =
            contentType
                .split(';')
                .map { it.trim() }
                .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                ?: return Charsets.UTF_8
        val name = charsetPart.substringAfter('=').trim().trim('"')
        return runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
    }
}
