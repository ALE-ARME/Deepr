package com.yogeshpaliyal.deepr.data

import com.yogeshpaliyal.deepr.util.normalizeLink
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

class NetworkRepository(
    val httpParser: HtmlParser,
    private val httpClient: HttpClient = HttpClient(CIO),
) {
    companion object {
        const val CRAWLER_USER_AGENT = "facebookexternalhit/1.1"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private suspend fun fetchHtml(
        url: String,
        userAgent: String,
    ): Result<String> =
        try {
            val response =
                httpClient.get(url) {
                    header(HttpHeaders.UserAgent, userAgent)
                    header(
                        HttpHeaders.Accept,
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    )
                    header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
                }
            if (!response.status.isSuccess()) {
                Result.failure(Exception("Failed to fetch data from $url, status code: ${response.status.value}"))
            } else {
                Result.success(response.bodyAsText())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun getLinkInfo(url: String): Result<LinkInfo> {
        val normalizedUrl = normalizeLink(url)

        // 1. First attempt with crawler user-agent (efficient for OpenGraph and social previews)
        val firstResult = fetchHtml(normalizedUrl, CRAWLER_USER_AGENT)
        if (firstResult.isSuccess) {
            val html = firstResult.getOrThrow()
            val linkInfo = httpParser.getTitleAndImageFromHtml(html, normalizedUrl)
            // If we successfully got a title, return it immediately
            if (!linkInfo.title.isNullOrBlank()) {
                return Result.success(linkInfo)
            }
        }

        // 2. Retry with browser user-agent if first attempt failed or yielded no title (handles anti-bot, Cloudflare, Wikipedia, Reddit)
        val retryResult = fetchHtml(normalizedUrl, BROWSER_USER_AGENT)
        if (retryResult.isSuccess) {
            val html = retryResult.getOrThrow()
            val linkInfo = httpParser.getTitleAndImageFromHtml(html, normalizedUrl)
            return Result.success(linkInfo)
        }

        // 3. If both failed, return failure with relevant error message
        val error = firstResult.exceptionOrNull() ?: retryResult.exceptionOrNull()
        return Result.failure(
            Exception(
                "Error fetching data from $normalizedUrl: ${error?.message}",
                error,
            ),
        )
    }
}
