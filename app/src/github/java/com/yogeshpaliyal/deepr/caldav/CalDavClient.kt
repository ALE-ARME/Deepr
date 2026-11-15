package com.yogeshpaliyal.deepr.caldav

import java.net.URI

data class RemoteVTodoResource(
    val href: String,
    val etag: String,
    val vtodo: CalDavVTodo,
)

class CalDavAuthException(
    message: String,
) : Exception(message)

class CalDavRequestException(
    val statusCode: Int,
    message: String,
) : Exception(message)

class CalDavClient(
    private val transport: CalDavHttpTransport = KtorCalDavHttpTransport(),
) {
    suspend fun discoverCollections(
        serverUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): Pair<String, List<CalDavCollection>> {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        val candidates = buildDiscoveryCandidates(normalizedUrl)
        val discovered = linkedMapOf<String, CalDavCollection>()
        var lastError: Throwable? = null
        var resolvedBase = normalizedUrl

        for (candidate in candidates) {
            try {
                val found =
                    discoverFromEntrypoint(
                        entrypointUrl = candidate,
                        username = username,
                        password = password,
                        trustSelfSigned = trustSelfSigned,
                    )
                if (found.isNotEmpty()) {
                    resolvedBase = candidate
                    found.forEach { collection ->
                        discovered.putIfAbsent(collection.url, collection)
                    }
                    break
                }
            } catch (authError: CalDavAuthException) {
                throw authError
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (discovered.isEmpty() && lastError != null) {
            throw lastError
        }

        val sorted =
            discovered.values.sortedWith(
                compareByDescending<CalDavCollection> { it.supportsVTodo }
                    .thenBy { it.displayName.lowercase() },
            )
        return resolvedBase to sorted
    }

    private suspend fun discoverFromEntrypoint(
        entrypointUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): List<CalDavCollection> {
        val collections = linkedMapOf<String, CalDavCollection>()

        val propfind0 =
            propfind(
                url = entrypointUrl,
                depth = "0",
                body = PROPFIND_PRINCIPAL_BODY,
                username = username,
                password = password,
                trustSelfSigned = trustSelfSigned,
            )

        if (propfind0.statusCode in 200..299) {
            val responses0 = CalDavXmlParser.parseMultiStatus(propfind0.body)
            val baseForResolve = propfind0.finalUrl.ifBlank { entrypointUrl }

            // 1. Check if the entered URL itself is already a calendar collection supporting VTODO
            for (resp in responses0) {
                if (resp.isCalendarCollection && resp.supportsVTodo()) {
                    val colUrl = ensureTrailingSlash(resolveUrl(baseForResolve, resp.href))
                    val name = resp.displayName.ifBlank { extractLastSegment(colUrl) }
                    collections[colUrl] = CalDavCollection(colUrl, name, supportsVTodo = true)
                }
            }

            // 2. Follow calendar-home-set directly if provided
            val directHomes =
                responses0
                    .mapNotNull { it.calendarHomeSetHref }
                    .map { ensureTrailingSlash(resolveUrl(baseForResolve, it)) }
                    .distinct()

            for (homeUrl in directHomes) {
                listCollectionsAtHome(homeUrl, username, password, trustSelfSigned).forEach { col ->
                    collections.putIfAbsent(col.url, col)
                }
            }

            // 3. Follow current-user-principal -> calendar-home-set if still empty
            if (collections.isEmpty()) {
                val principals =
                    responses0
                        .mapNotNull { it.currentUserPrincipalHref }
                        .map { resolveUrl(baseForResolve, it) }
                        .distinct()

                for (principalUrl in principals) {
                    val principalResp =
                        propfind(
                            url = principalUrl,
                            depth = "0",
                            body = PROPFIND_HOME_SET_BODY,
                            username = username,
                            password = password,
                            trustSelfSigned = trustSelfSigned,
                        )
                    if (principalResp.statusCode in 200..299) {
                        val principalBase = principalResp.finalUrl.ifBlank { principalUrl }
                        val homeHrefs =
                            CalDavXmlParser
                                .parseMultiStatus(principalResp.body)
                                .mapNotNull { it.calendarHomeSetHref }
                                .map { ensureTrailingSlash(resolveUrl(principalBase, it)) }
                                .distinct()

                        for (homeUrl in homeHrefs) {
                            listCollectionsAtHome(homeUrl, username, password, trustSelfSigned).forEach { col ->
                                collections.putIfAbsent(col.url, col)
                            }
                        }
                    }
                }
            }
        }

        // 4. Direct Depth: 1 scan on the entrypoint itself (critical for Radicale / subpath setups)
        if (collections.isEmpty()) {
            listCollectionsAtHome(entrypointUrl, username, password, trustSelfSigned).forEach { col ->
                collections.putIfAbsent(col.url, col)
            }
        }

        return collections.values.toList()
    }

    private suspend fun listCollectionsAtHome(
        homeUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): List<CalDavCollection> {
        val response =
            propfind(
                url = homeUrl,
                depth = "1",
                body = PROPFIND_COLLECTIONS_BODY,
                username = username,
                password = password,
                trustSelfSigned = trustSelfSigned,
            )
        if (response.statusCode !in 200..299) {
            return emptyList()
        }
        val baseUrl = response.finalUrl.ifBlank { homeUrl }
        return CalDavXmlParser
            .parseMultiStatus(response.body)
            .filter { it.isCalendarCollection && it.supportsVTodo() }
            .map { resp ->
                val resolvedUrl = ensureTrailingSlash(resolveUrl(baseUrl, resp.href))
                val name = resp.displayName.ifBlank { extractLastSegment(resolvedUrl) }
                CalDavCollection(
                    url = resolvedUrl,
                    displayName = name,
                    supportsVTodo = true,
                )
            }
    }

    suspend fun fetchVTodos(
        collectionUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): List<RemoteVTodoResource> {
        val reportResponse =
            transport.execute(
                method = "REPORT",
                url = collectionUrl,
                username = username,
                password = password,
                headers = mapOf("Depth" to "1"),
                body = REPORT_VTODO_QUERY_BODY,
                contentTypeHeader = "application/xml; charset=utf-8",
                trustSelfSigned = trustSelfSigned,
            )
        checkAuthAndErrors(reportResponse, allowNotFound = false)

        if (reportResponse.statusCode in 200..299) {
            val entries = CalDavXmlParser.parseMultiStatus(reportResponse.body)
            val parsedFromReport = mutableListOf<RemoteVTodoResource>()
            val missingDataEntries = mutableListOf<WebDavResponse>()
            val baseUrl = reportResponse.finalUrl.ifBlank { collectionUrl }

            for (entry in entries) {
                val itemUrl = resolveUrl(baseUrl, entry.href)
                if (normalizeTrailingSlash(itemUrl) == normalizeTrailingSlash(collectionUrl)) {
                    continue
                }
                val calData = entry.calendarData
                if (!calData.isNullOrBlank()) {
                    val vtodo = VTodoMapper.parseIcs(calData)
                    if (vtodo != null) {
                        parsedFromReport.add(
                            RemoteVTodoResource(
                                href = itemUrl,
                                etag = entry.etag.orEmpty(),
                                vtodo = vtodo,
                            ),
                        )
                    }
                } else if (!entry.isCalendarCollection) {
                    missingDataEntries.add(entry)
                }
            }

            if (missingDataEntries.isEmpty()) {
                return parsedFromReport
            }

            // Server returned hrefs/etags without inline calendar-data; fetch missing .ics payloads
            for (missing in missingDataEntries) {
                val itemUrl = resolveUrl(baseUrl, missing.href)
                val vtodo = getVTodo(itemUrl, username, password, trustSelfSigned) ?: continue
                parsedFromReport.add(
                    RemoteVTodoResource(
                        href = itemUrl,
                        etag = missing.etag.orEmpty(),
                        vtodo = vtodo,
                    ),
                )
            }
            return parsedFromReport
        }

        // Fallback: PROPFIND Depth: 1 + individual GET requests
        val propfindResponse =
            propfind(
                url = collectionUrl,
                depth = "1",
                body = PROPFIND_ITEMS_BODY,
                username = username,
                password = password,
                trustSelfSigned = trustSelfSigned,
            )
        if (propfindResponse.statusCode !in 200..299) {
            throw CalDavRequestException(
                propfindResponse.statusCode,
                "Failed to fetch remote tasks (HTTP ${propfindResponse.statusCode})",
            )
        }

        val baseUrl = propfindResponse.finalUrl.ifBlank { collectionUrl }
        val entries = CalDavXmlParser.parseMultiStatus(propfindResponse.body)
        val result = mutableListOf<RemoteVTodoResource>()
        for (entry in entries) {
            if (entry.isCalendarCollection) continue
            val itemUrl = resolveUrl(baseUrl, entry.href)
            if (normalizeTrailingSlash(itemUrl) == normalizeTrailingSlash(collectionUrl)) continue
            val contentType = entry.contentType.orEmpty()
            val looksLikeCalendar =
                itemUrl.endsWith(".ics", ignoreCase = true) ||
                    contentType.contains("text/calendar", ignoreCase = true) ||
                    entry.etag != null
            if (!looksLikeCalendar) continue

            val vtodo = getVTodo(itemUrl, username, password, trustSelfSigned) ?: continue
            result.add(
                RemoteVTodoResource(
                    href = itemUrl,
                    etag = entry.etag.orEmpty(),
                    vtodo = vtodo,
                ),
            )
        }
        return result
    }

    suspend fun getVTodo(
        itemUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): CalDavVTodo? {
        val response =
            transport.execute(
                method = "GET",
                url = itemUrl,
                username = username,
                password = password,
                trustSelfSigned = trustSelfSigned,
            )
        checkAuthAndErrors(response, allowNotFound = true)
        if (response.statusCode !in 200..299) return null
        return VTodoMapper.parseIcs(response.body)
    }

    suspend fun putVTodo(
        collectionUrl: String,
        targetHref: String?,
        vtodo: CalDavVTodo,
        etag: String?,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): Pair<String, String> {
        val targetUrl =
            if (!targetHref.isNullOrBlank()) {
                targetHref
            } else {
                val safeUid = vtodo.uid.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                ensureTrailingSlash(collectionUrl) + "$safeUid.ics"
            }

        val headers = mutableMapOf<String, String>()
        if (!etag.isNullOrBlank()) {
            headers["If-Match"] = etag
        } else if (targetHref.isNullOrBlank()) {
            headers["If-None-Match"] = "*"
        }

        val icsBody = VTodoMapper.toIcs(vtodo)
        var response =
            transport.execute(
                method = "PUT",
                url = targetUrl,
                username = username,
                password = password,
                headers = headers,
                body = icsBody,
                contentTypeHeader = "text/calendar; charset=utf-8",
                trustSelfSigned = trustSelfSigned,
            )

        if (response.statusCode == 412 && etag == null && targetHref.isNullOrBlank()) {
            // Retry without If-None-Match if file already existed with same UID
            response =
                transport.execute(
                    method = "PUT",
                    url = targetUrl,
                    username = username,
                    password = password,
                    headers = emptyMap(),
                    body = icsBody,
                    contentTypeHeader = "text/calendar; charset=utf-8",
                    trustSelfSigned = trustSelfSigned,
                )
        }

        checkAuthAndErrors(response, allowNotFound = false)
        if (response.statusCode !in 200..299) {
            throw CalDavRequestException(
                response.statusCode,
                "Failed to upload item ${vtodo.uid} (HTTP ${response.statusCode})",
            )
        }

        var updatedEtag = response.headers["etag"].orEmpty()
        if (updatedEtag.isBlank()) {
            updatedEtag = fetchEtag(targetUrl, username, password, trustSelfSigned)
        }
        return targetUrl to updatedEtag
    }

    suspend fun deleteVTodo(
        itemUrl: String,
        etag: String?,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ) {
        val headers =
            if (!etag.isNullOrBlank()) {
                mapOf("If-Match" to etag)
            } else {
                emptyMap()
            }
        var response =
            transport.execute(
                method = "DELETE",
                url = itemUrl,
                username = username,
                password = password,
                headers = headers,
                trustSelfSigned = trustSelfSigned,
            )
        if (response.statusCode == 412 && !etag.isNullOrBlank()) {
            response =
                transport.execute(
                    method = "DELETE",
                    url = itemUrl,
                    username = username,
                    password = password,
                    trustSelfSigned = trustSelfSigned,
                )
        }
        checkAuthAndErrors(response, allowNotFound = true)
        if (response.statusCode != 404 && response.statusCode != 410 && response.statusCode !in 200..299) {
            throw CalDavRequestException(
                response.statusCode,
                "Failed to delete remote item (HTTP ${response.statusCode})",
            )
        }
    }

    private suspend fun fetchEtag(
        itemUrl: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): String {
        val resp =
            runCatching {
                propfind(
                    url = itemUrl,
                    depth = "0",
                    body = PROPFIND_ETAG_ONLY_BODY,
                    username = username,
                    password = password,
                    trustSelfSigned = trustSelfSigned,
                )
            }.getOrNull() ?: return ""
        if (resp.statusCode !in 200..299) return ""
        return CalDavXmlParser
            .parseMultiStatus(resp.body)
            .firstOrNull()
            ?.etag
            .orEmpty()
    }

    private suspend fun propfind(
        url: String,
        depth: String,
        body: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean,
    ): CalDavHttpResponse {
        val response =
            transport.execute(
                method = "PROPFIND",
                url = url,
                username = username,
                password = password,
                headers = mapOf("Depth" to depth),
                body = body,
                contentTypeHeader = "application/xml; charset=utf-8",
                trustSelfSigned = trustSelfSigned,
            )
        checkAuthAndErrors(response, allowNotFound = true)
        return response
    }

    private fun checkAuthAndErrors(
        response: CalDavHttpResponse,
        allowNotFound: Boolean,
    ) {
        if (response.statusCode == 401 || response.statusCode == 403) {
            throw CalDavAuthException("Authentication failed (HTTP ${response.statusCode})")
        }
        if (!allowNotFound && response.statusCode == 404) {
            throw CalDavRequestException(404, "CalDAV resource not found (HTTP 404)")
        }
    }

    companion object {
        internal fun normalizeServerUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.contains("://")) trimmed else "https://$trimmed"
        }

        internal fun buildDiscoveryCandidates(normalizedUrl: String): List<String> {
            val uri =
                runCatching { URI(normalizedUrl) }.getOrNull()
                    ?: return listOf(normalizedUrl)
            val scheme = uri.scheme ?: "https"
            val authority = uri.rawAuthority ?: return listOf(normalizedUrl)
            val origin = "$scheme://$authority"
            val rawPath = uri.rawPath.orEmpty()
            val trimmedNoSlash = normalizedUrl.trimEnd('/')
            val withSlash = ensureTrailingSlash(normalizedUrl)

            val candidates = linkedSetOf<String>()
            val hasCustomSubpath = rawPath.isNotBlank() && rawPath != "/"
            if (hasCustomSubpath) {
                // Probe explicit custom subpath (e.g. https://dav.example.com:8443/user/) first,
                // then .well-known / standard DAV fallbacks on subpath and origin.
                candidates.add(withSlash)
                candidates.add(trimmedNoSlash)
                candidates.add("$trimmedNoSlash/.well-known/caldav")
                candidates.add("$origin/.well-known/caldav")
                candidates.add("$trimmedNoSlash/remote.php/dav/")
                candidates.add("$origin/remote.php/dav/")
                candidates.add("$origin/dav.php/")
            } else {
                candidates.add("$origin/.well-known/caldav")
                candidates.add("$origin/")
                candidates.add("$origin/remote.php/dav/")
                candidates.add("$origin/dav.php/")
            }
            return candidates.toList()
        }

        internal fun resolveUrl(
            baseUrl: String,
            href: String,
        ): String {
            val trimmedHref = href.trim()
            if (trimmedHref.startsWith("http://", ignoreCase = true) ||
                trimmedHref.startsWith("https://", ignoreCase = true)
            ) {
                return trimmedHref
            }
            return try {
                URI(baseUrl).resolve(trimmedHref).toString()
            } catch (_: Exception) {
                trimmedHref
            }
        }

        private fun ensureTrailingSlash(url: String): String = if (url.endsWith("/")) url else "$url/"

        private fun normalizeTrailingSlash(url: String): String = url.trimEnd('/')

        private fun extractLastSegment(url: String): String {
            val clean = url.trimEnd('/')
            val segment = clean.substringAfterLast('/', clean)
            return segment.ifBlank { url }
        }

        private const val PROPFIND_PRINCIPAL_BODY =
            """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
  <D:prop>
    <D:resourcetype/>
    <D:displayname/>
    <D:current-user-principal/>
    <C:calendar-home-set/>
    <C:supported-calendar-component-set/>
  </D:prop>
</D:propfind>"""

        private const val PROPFIND_HOME_SET_BODY =
            """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
  <D:prop>
    <C:calendar-home-set/>
  </D:prop>
</D:propfind>"""

        private const val PROPFIND_COLLECTIONS_BODY =
            """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
  <D:prop>
    <D:resourcetype/>
    <D:displayname/>
    <C:supported-calendar-component-set/>
  </D:prop>
</D:propfind>"""

        private const val PROPFIND_ITEMS_BODY =
            """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:resourcetype/>
    <D:getetag/>
    <D:getcontenttype/>
  </D:prop>
</D:propfind>"""

        private const val PROPFIND_ETAG_ONLY_BODY =
            """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:getetag/>
  </D:prop>
</D:propfind>"""

        private const val REPORT_VTODO_QUERY_BODY =
            """<?xml version="1.0" encoding="utf-8"?>
<C:calendar-query xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
  <D:prop>
    <D:getetag/>
    <C:calendar-data/>
  </D:prop>
  <C:filter>
    <C:comp-filter name="VCALENDAR">
      <C:comp-filter name="VTODO"/>
    </C:comp-filter>
  </C:filter>
</C:calendar-query>"""
    }
}
