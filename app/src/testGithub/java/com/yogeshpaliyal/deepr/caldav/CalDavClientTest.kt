package com.yogeshpaliyal.deepr.caldav

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalDavClientTest {
    @Test
    fun `discoverCollections supports custom port and subpath directly`() =
        runTest {
            val requestedUrls = mutableListOf<String>()
            val mockEngine =
                MockEngine { request ->
                    val url = request.url.toString()
                    requestedUrls.add("${request.method.value} $url (Depth=${request.headers["Depth"]})")
                    when {
                        url == "https://dav.example.com:8443/user/" && request.headers["Depth"] == "0" ->
                            respond(
                                content =
                                    """<?xml version="1.0" encoding="utf-8"?>
                                    <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                                      <D:response>
                                        <D:href>/user/</D:href>
                                        <D:propstat>
                                          <D:prop>
                                            <D:current-user-principal><D:href>/user/</D:href></D:current-user-principal>
                                            <C:calendar-home-set><D:href>/user/</D:href></C:calendar-home-set>
                                          </D:prop>
                                          <D:status>HTTP/1.1 200 OK</D:status>
                                        </D:propstat>
                                      </D:response>
                                    </D:multistatus>""",
                                status = HttpStatusCode.MultiStatus,
                                headers = headersOf(HttpHeaders.ContentType, "application/xml"),
                            )
                        url == "https://dav.example.com:8443/user/" && request.headers["Depth"] == "1" ->
                            respond(
                                content =
                                    """<?xml version="1.0" encoding="utf-8"?>
                                    <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                                      <D:response>
                                        <D:href>/user/</D:href>
                                        <D:propstat>
                                          <D:prop>
                                            <D:resourcetype><D:collection/></D:resourcetype>
                                          </D:prop>
                                          <D:status>HTTP/1.1 200 OK</D:status>
                                        </D:propstat>
                                      </D:response>
                                      <D:response>
                                        <D:href>/user/deepr-tasks/</D:href>
                                        <D:propstat>
                                          <D:prop>
                                            <D:resourcetype><D:collection/><C:calendar/></D:resourcetype>
                                            <D:displayname>Deepr Bookmarks</D:displayname>
                                            <C:supported-calendar-component-set>
                                              <C:comp name="VTODO"/>
                                            </C:supported-calendar-component-set>
                                          </D:prop>
                                          <D:status>HTTP/1.1 200 OK</D:status>
                                        </D:propstat>
                                      </D:response>
                                    </D:multistatus>""",
                                status = HttpStatusCode.MultiStatus,
                                headers = headersOf(HttpHeaders.ContentType, "application/xml"),
                            )
                        else -> respond("", HttpStatusCode.NotFound)
                    }
                }

            val client = CalDavClient(KtorCalDavHttpTransport(mockEngine))
            val (resolvedBase, collections) =
                client.discoverCollections(
                    serverUrl = "https://dav.example.com:8443/user/",
                    username = "user",
                    password = "secret",
                    trustSelfSigned = true,
                )

            assertEquals("https://dav.example.com:8443/user/", resolvedBase)
            assertEquals(1, collections.size)
            assertEquals("https://dav.example.com:8443/user/deepr-tasks/", collections[0].url)
            assertEquals("Deepr Bookmarks", collections[0].displayName)
            assertTrue(requestedUrls.first().startsWith("PROPFIND https://dav.example.com:8443/user/"))
        }

    @Test(expected = CalDavAuthException::class)
    fun `discoverCollections throws CalDavAuthException on HTTP 401`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond("Unauthorized", HttpStatusCode.Unauthorized)
                }
            val client = CalDavClient(KtorCalDavHttpTransport(mockEngine))
            client.discoverCollections(
                serverUrl = "https://cloud.example.com",
                username = "bad",
                password = "bad",
                trustSelfSigned = false,
            )
        }

    @Test
    fun `fetchVTodos parses REPORT response and resolves missing calendar-data via GET`() =
        runTest {
            val icsOne =
                VTodoMapper.toIcs(
                    CalDavVTodo(
                        uid = "uid-1",
                        link = "https://deepr.dev/1",
                        summary = "Item 1",
                        lastModifiedMillis = 1700000000000L,
                    ),
                )
            val icsTwo =
                VTodoMapper.toIcs(
                    CalDavVTodo(
                        uid = "uid-2",
                        link = "https://deepr.dev/2",
                        summary = "Item 2",
                        lastModifiedMillis = 1700000005000L,
                    ),
                )

            val mockEngine =
                MockEngine { request ->
                    val url = request.url.toString()
                    when (request.method.value) {
                        "REPORT" ->
                            respond(
                                content =
                                    """<?xml version="1.0" encoding="utf-8"?>
                                    <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                                      <D:response>
                                        <D:href>/dav/tasks/uid-1.ics</D:href>
                                        <D:propstat>
                                          <D:prop>
                                            <D:getetag>"etag-1"</D:getetag>
                                            <C:calendar-data>$icsOne</C:calendar-data>
                                          </D:prop>
                                        </D:propstat>
                                      </D:response>
                                      <D:response>
                                        <D:href>/dav/tasks/uid-2.ics</D:href>
                                        <D:propstat>
                                          <D:prop>
                                            <D:getetag>"etag-2"</D:getetag>
                                          </D:prop>
                                        </D:propstat>
                                      </D:response>
                                    </D:multistatus>""",
                                status = HttpStatusCode.MultiStatus,
                                headers = headersOf(HttpHeaders.ContentType, "application/xml"),
                            )
                        "GET" ->
                            if (url.endsWith("uid-2.ics")) {
                                respond(icsTwo, HttpStatusCode.OK)
                            } else {
                                respond("", HttpStatusCode.NotFound)
                            }
                        else -> respond("", HttpStatusCode.NotFound)
                    }
                }

            val client = CalDavClient(KtorCalDavHttpTransport(mockEngine))
            val items =
                client.fetchVTodos(
                    collectionUrl = "https://dav.example.com:8443/dav/tasks/",
                    username = "u",
                    password = "p",
                    trustSelfSigned = false,
                )

            assertEquals(2, items.size)
            assertEquals("https://dav.example.com:8443/dav/tasks/uid-1.ics", items[0].href)
            assertEquals("\"etag-1\"", items[0].etag)
            assertEquals("https://deepr.dev/1", items[0].vtodo.link)
            assertEquals("https://dav.example.com:8443/dav/tasks/uid-2.ics", items[1].href)
            assertEquals("\"etag-2\"", items[1].etag)
            assertEquals("https://deepr.dev/2", items[1].vtodo.link)
        }

    @Test
    fun `putVTodo sends If-None-Match and falls back cleanly on 412`() =
        runTest {
            var putAttempts = 0
            val capturedIfNoneMatch = mutableListOf<String?>()
            var uploadedBody = ""

            val mockEngine =
                MockEngine { request ->
                    if (request.method.value == "PUT") {
                        putAttempts++
                        capturedIfNoneMatch.add(request.headers["If-None-Match"])
                        uploadedBody = request.body.toByteArray().decodeToString()
                        if (putAttempts == 1) {
                            respond("Precondition Failed", HttpStatusCode.PreconditionFailed)
                        } else {
                            respond(
                                content = "",
                                status = HttpStatusCode.Created,
                                headers = headersOf(HttpHeaders.ETag, "\"etag-new\""),
                            )
                        }
                    } else {
                        respond("", HttpStatusCode.NotFound)
                    }
                }

            val client = CalDavClient(KtorCalDavHttpTransport(mockEngine))
            val (url, etag) =
                client.putVTodo(
                    collectionUrl = "https://dav.example.com/col/",
                    targetHref = null,
                    vtodo =
                        CalDavVTodo(
                            uid = "new-uid",
                            link = "https://example.org",
                            summary = "Test",
                            lastModifiedMillis = 1700000000000L,
                        ),
                    etag = null,
                    username = "u",
                    password = "p",
                    trustSelfSigned = false,
                )

            assertEquals(2, putAttempts)
            assertEquals("*", capturedIfNoneMatch[0])
            assertEquals(null, capturedIfNoneMatch[1])
            assertEquals("https://dav.example.com/col/new-uid.ics", url)
            assertEquals("\"etag-new\"", etag)
            assertTrue(uploadedBody.contains("BEGIN:VTODO"))
        }
}
