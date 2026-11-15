package com.yogeshpaliyal.deepr.caldav

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalDavSyncEngineTest {
    private class FakeLocalStore(
        initialLinks: List<CalDavLocalLink> = emptyList(),
        initialDeleted: List<CalDavDeletedItem> = emptyList(),
    ) : CalDavLocalStore {
        val links = initialLinks.toMutableList()
        val deleted = initialDeleted.toMutableList()
        private var nextId = (initialLinks.maxOfOrNull { it.id } ?: 0L) + 1L

        override suspend fun getLinksForSync(includePrivate: Boolean): List<CalDavLocalLink> =
            if (includePrivate) {
                links.toList()
            } else {
                links.filter { it.profileIsPrivate == 0L }
            }

        override suspend fun getDeletedItems(): List<CalDavDeletedItem> = deleted.toList()

        override suspend fun removeDeletedItemByUid(syncUid: String) {
            deleted.removeAll { it.syncUid == syncUid }
        }

        override suspend fun updateSyncState(
            linkId: Long,
            syncUid: String,
            syncEtag: String,
            updatedAt: Long,
            lastSyncedAt: Long,
        ) {
            val idx = links.indexOfFirst { it.id == linkId }
            if (idx >= 0) {
                links[idx] =
                    links[idx].copy(
                        syncUid = syncUid,
                        syncEtag = syncEtag,
                        updatedAt = updatedAt,
                        lastSyncedAt = lastSyncedAt,
                    )
            }
        }

        override suspend fun deleteLocalLinkFromRemote(linkId: Long) {
            links.removeAll { it.id == linkId }
        }

        override suspend fun upsertLinkFromRemote(
            existingLinkId: Long?,
            vtodo: CalDavVTodo,
            etag: String,
            updatedAt: Long,
            lastSyncedAt: Long,
        ) {
            val scopesByName = vtodo.tagScopes.associateBy { it.name }
            val resolvedTagDetails =
                vtodo.categories.map { tagName ->
                    scopesByName[tagName] ?: CalDavTag(name = tagName, isGlobal = true)
                }
            if (existingLinkId != null) {
                val idx = links.indexOfFirst { it.id == existingLinkId }
                if (idx >= 0) {
                    links[idx] =
                        links[idx].copy(
                            link = vtodo.link,
                            name = vtodo.summary,
                            notes = vtodo.description,
                            tags = vtodo.categories,
                            tagDetails = resolvedTagDetails,
                            profileName = vtodo.profileName.ifBlank { "Default" },
                            profileIsPrivate = if (vtodo.isPrivate) 1L else 0L,
                            isFavourite = if (vtodo.isFavourite) 1L else 0L,
                            openedCount = vtodo.openedCount,
                            thumbnail = vtodo.thumbnail,
                            targetApp = vtodo.targetApp,
                            syncUid = vtodo.uid,
                            syncEtag = etag,
                            updatedAt = updatedAt,
                            lastSyncedAt = lastSyncedAt,
                        )
                    return
                }
            }
            links.add(
                CalDavLocalLink(
                    id = nextId++,
                    link = vtodo.link,
                    name = vtodo.summary,
                    createdAt = "2025-01-01 00:00:00",
                    openedCount = vtodo.openedCount,
                    isFavourite = if (vtodo.isFavourite) 1L else 0L,
                    notes = vtodo.description,
                    thumbnail = vtodo.thumbnail,
                    profileId = if (vtodo.isPrivate) 2L else 1L,
                    targetApp = vtodo.targetApp,
                    syncUid = vtodo.uid,
                    syncEtag = etag,
                    updatedAt = updatedAt,
                    lastSyncedAt = lastSyncedAt,
                    profileName = vtodo.profileName.ifBlank { "Default" },
                    profileIsPrivate = if (vtodo.isPrivate) 1L else 0L,
                    tags = vtodo.categories,
                    tagDetails = resolvedTagDetails,
                ),
            )
        }
    }

    private data class RemoteEntry(
        val href: String,
        var etag: String,
        var ics: String,
    )

    private fun createMockServerEngine(
        collectionUrl: String,
        remoteMap: MutableMap<String, RemoteEntry>,
    ): MockEngine =
        MockEngine { request ->
            val url = request.url.toString()
            when (request.method.value) {
                "REPORT" -> {
                    val xml =
                        buildString {
                            append("""<?xml version="1.0" encoding="utf-8"?>""")
                            append("""<D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">""")
                            remoteMap.values.forEach { entry ->
                                val escapedIcs =
                                    entry.ics
                                        .replace("&", "&amp;")
                                        .replace("<", "&lt;")
                                        .replace(">", "&gt;")
                                append(
                                    """
                                    <D:response>
                                      <D:href>${entry.href}</D:href>
                                      <D:propstat>
                                        <D:prop>
                                          <D:getetag>${entry.etag}</D:getetag>
                                          <C:calendar-data>$escapedIcs</C:calendar-data>
                                        </D:prop>
                                      </D:propstat>
                                    </D:response>
                                    """.trimIndent(),
                                )
                            }
                            append("</D:multistatus>")
                        }
                    respond(xml, HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, "application/xml"))
                }
                "PUT" -> {
                    val body = request.body.toByteArray().decodeToString()
                    val vtodo = VTodoMapper.parseIcs(body)!!
                    val newEtag = "\"etag-${remoteMap.size + 100}\""
                    remoteMap[url] = RemoteEntry(href = url, etag = newEtag, ics = body)
                    respond("", HttpStatusCode.Created, headersOf(HttpHeaders.ETag, newEtag))
                }
                "DELETE" -> {
                    val removed = remoteMap.remove(url)
                    if (removed != null) {
                        respond("", HttpStatusCode.NoContent)
                    } else {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

    private fun baseConfig(syncPrivate: Boolean = false) =
        CalDavConfig(
            enabled = true,
            serverUrl = "https://dav.example.com:8443/user/",
            username = "user",
            password = "pw",
            collectionUrl = "https://dav.example.com:8443/user/deepr/",
            collectionName = "Deepr",
            trustSelfSigned = true,
            syncPrivate = syncPrivate,
        )

    @Test
    fun `bidirectional sync pushes local links, pulls remote links, and respects syncPrivate`() =
        runTest {
            val collectionUrl = "https://dav.example.com:8443/user/deepr/"
            val localStore =
                FakeLocalStore(
                    initialLinks =
                        listOf(
                            CalDavLocalLink(
                                id = 1L,
                                link = "https://public.local",
                                name = "Public Local",
                                createdAt = "2025-01-01 10:00:00",
                                openedCount = 0L,
                                isFavourite = 1L,
                                notes = "Public notes",
                                thumbnail = "",
                                profileId = 1L,
                                targetApp = "",
                                syncUid = "",
                                syncEtag = "",
                                updatedAt = 1700000000000L,
                                lastSyncedAt = 0L,
                                profileName = "Default",
                                profileIsPrivate = 0L,
                                tags = listOf("news"),
                            ),
                            CalDavLocalLink(
                                id = 2L,
                                link = "https://secret.local",
                                name = "Private Local",
                                createdAt = "2025-01-01 10:00:00",
                                openedCount = 0L,
                                isFavourite = 0L,
                                notes = "",
                                thumbnail = "",
                                profileId = 2L,
                                targetApp = "",
                                syncUid = "",
                                syncEtag = "",
                                updatedAt = 1700000000000L,
                                lastSyncedAt = 0L,
                                profileName = "Secret",
                                profileIsPrivate = 1L,
                                tags = emptyList(),
                            ),
                        ),
                )

            val remoteMap =
                mutableMapOf(
                    "${collectionUrl}remote-pub.ics" to
                        RemoteEntry(
                            href = "${collectionUrl}remote-pub.ics",
                            etag = "\"r1\"",
                            ics =
                                VTodoMapper.toIcs(
                                    CalDavVTodo(
                                        uid = "remote-pub",
                                        link = "https://public.remote",
                                        summary = "Remote Public",
                                        categories = listOf("work"),
                                        isPrivate = false,
                                        lastModifiedMillis = 1700000010000L,
                                    ),
                                ),
                        ),
                    "${collectionUrl}remote-priv.ics" to
                        RemoteEntry(
                            href = "${collectionUrl}remote-priv.ics",
                            etag = "\"r2\"",
                            ics =
                                VTodoMapper.toIcs(
                                    CalDavVTodo(
                                        uid = "remote-priv",
                                        link = "https://secret.remote",
                                        summary = "Remote Private",
                                        profileName = "Vault",
                                        isPrivate = true,
                                        lastModifiedMillis = 1700000020000L,
                                    ),
                                ),
                        ),
                )

            val engine =
                CalDavSyncEngine(
                    localStore = localStore,
                    client = CalDavClient(KtorCalDavHttpTransport(createMockServerEngine(collectionUrl, remoteMap))),
                    clock = { 1700000100000L },
                )

            // Sync with syncPrivate = false
            val res1 = engine.sync(baseConfig(syncPrivate = false))
            assertTrue(res1 is CalDavSyncResult.Success)
            res1 as CalDavSyncResult.Success
            assertEquals(1, res1.pushedCount)
            assertEquals(1, res1.pulledCount)
            assertTrue(remoteMap.values.none { it.ics.contains("https://secret.local") })
            assertTrue(localStore.links.none { it.link == "https://secret.remote" })
            assertTrue(localStore.links.any { it.link == "https://public.remote" && it.tags == listOf("work") })

            // Sync with syncPrivate = true
            val res2 = engine.sync(baseConfig(syncPrivate = true))
            assertTrue(res2 is CalDavSyncResult.Success)
            res2 as CalDavSyncResult.Success
            assertEquals(1, res2.pushedCount)
            assertEquals(1, res2.pulledCount)
            assertTrue(remoteMap.values.any { it.ics.contains("https://secret.local") && it.ics.contains("X-DEEPR-PRIVATE:1") })
            val pulledPrivate = localStore.links.find { it.link == "https://secret.remote" }
            assertNotNull(pulledPrivate)
            assertEquals(1L, pulledPrivate!!.profileIsPrivate)
            assertEquals("Vault", pulledPrivate.profileName)
        }

    @Test
    fun `LWW conflict resolution picks newer timestamp and handles tombstones plus remote deletes`() =
        runTest {
            val collectionUrl = "https://dav.example.com:8443/user/deepr/"
            val localStore =
                FakeLocalStore(
                    initialLinks =
                        listOf(
                            // Local newer -> should win over remote
                            CalDavLocalLink(
                                id = 1L,
                                link = "https://conflict-local-wins.com",
                                name = "Local Newer Title",
                                createdAt = "2025-01-01 00:00:00",
                                openedCount = 2L,
                                isFavourite = 1L,
                                notes = "",
                                thumbnail = "",
                                profileId = 1L,
                                targetApp = "",
                                syncUid = "uid-local-wins",
                                syncEtag = "\"old-etag\"",
                                updatedAt = 2000L,
                                lastSyncedAt = 1000L,
                                profileName = "Default",
                                profileIsPrivate = 0L,
                                tags = emptyList(),
                            ),
                            // Remote newer -> should overwrite local
                            CalDavLocalLink(
                                id = 2L,
                                link = "https://conflict-remote-wins.com",
                                name = "Old Local Title",
                                createdAt = "2025-01-01 00:00:00",
                                openedCount = 0L,
                                isFavourite = 0L,
                                notes = "",
                                thumbnail = "",
                                profileId = 1L,
                                targetApp = "",
                                syncUid = "uid-remote-wins",
                                syncEtag = "\"etag-1\"",
                                updatedAt = 1500L,
                                lastSyncedAt = 1000L,
                                profileName = "Default",
                                profileIsPrivate = 0L,
                                tags = emptyList(),
                            ),
                            // Deleted on server -> should be deleted locally
                            CalDavLocalLink(
                                id = 3L,
                                link = "https://deleted-remotely.com",
                                name = "Gone Remote",
                                createdAt = "2025-01-01 00:00:00",
                                openedCount = 0L,
                                isFavourite = 0L,
                                notes = "",
                                thumbnail = "",
                                profileId = 1L,
                                targetApp = "",
                                syncUid = "uid-deleted-remote",
                                syncEtag = "\"etag-del\"",
                                updatedAt = 1000L,
                                lastSyncedAt = 1000L,
                                profileName = "Default",
                                profileIsPrivate = 0L,
                                tags = emptyList(),
                            ),
                        ),
                    initialDeleted =
                        listOf(
                            CalDavDeletedItem(
                                id = 10L,
                                syncUid = "uid-deleted-local",
                                deletedAt = 1800L,
                            ),
                        ),
                )

            val remoteMap =
                mutableMapOf(
                    "${collectionUrl}uid-local-wins.ics" to
                        RemoteEntry(
                            href = "${collectionUrl}uid-local-wins.ics",
                            etag = "\"changed-remote-etag\"",
                            ics =
                                VTodoMapper.toIcs(
                                    CalDavVTodo(
                                        uid = "uid-local-wins",
                                        link = "https://conflict-local-wins.com",
                                        summary = "Stale Remote Title",
                                        lastModifiedMillis = 1200L,
                                    ),
                                ),
                        ),
                    "${collectionUrl}uid-remote-wins.ics" to
                        RemoteEntry(
                            href = "${collectionUrl}uid-remote-wins.ics",
                            etag = "\"newer-remote-etag\"",
                            ics =
                                VTodoMapper.toIcs(
                                    CalDavVTodo(
                                        uid = "uid-remote-wins",
                                        link = "https://conflict-remote-wins.com",
                                        summary = "Fresh Remote Title",
                                        lastModifiedMillis = 3000L,
                                    ),
                                ),
                        ),
                    "${collectionUrl}uid-deleted-local.ics" to
                        RemoteEntry(
                            href = "${collectionUrl}uid-deleted-local.ics",
                            etag = "\"del-etag\"",
                            ics =
                                VTodoMapper.toIcs(
                                    CalDavVTodo(
                                        uid = "uid-deleted-local",
                                        link = "https://deleted-locally.com",
                                        summary = "To Delete",
                                        lastModifiedMillis = 1200L,
                                    ),
                                ),
                        ),
                )

            val engine =
                CalDavSyncEngine(
                    localStore = localStore,
                    client = CalDavClient(KtorCalDavHttpTransport(createMockServerEngine(collectionUrl, remoteMap))),
                    clock = { 5000L },
                )

            val result = engine.sync(baseConfig())
            assertTrue(result is CalDavSyncResult.Success)
            result as CalDavSyncResult.Success
            assertEquals(1, result.pushedCount)
            assertEquals(1, result.pulledCount)
            assertEquals(2, result.deletedCount) // 1 remote delete (tombstone) + 1 local delete

            assertFalse(remoteMap.containsKey("${collectionUrl}uid-deleted-local.ics"))
            assertTrue(localStore.deleted.isEmpty())
            assertTrue(localStore.links.none { it.syncUid == "uid-deleted-remote" })
            assertEquals(
                "Local Newer Title",
                VTodoMapper.parseIcs(remoteMap["${collectionUrl}uid-local-wins.ics"]!!.ics)!!.summary,
            )
            assertEquals(
                "Fresh Remote Title",
                localStore.links.first { it.syncUid == "uid-remote-wins" }.name,
            )
        }

    @Test
    fun `sync pushes and restores tag specificity across devices`() =
        runTest {
            val collectionUrl = "https://dav.example.com:8443/user/deepr/"
            val remoteMap = mutableMapOf<String, RemoteEntry>()
            val expectedTagDetails =
                listOf(
                    CalDavTag(name = "shared", isGlobal = true),
                    CalDavTag(
                        name = "sprint",
                        isGlobal = false,
                        profileNames = listOf("Work", "Engineering"),
                    ),
                )

            val deviceAStore =
                FakeLocalStore(
                    initialLinks =
                        listOf(
                            CalDavLocalLink(
                                id = 1L,
                                link = "https://scoped.example.com",
                                name = "Scoped Bookmark",
                                createdAt = "2025-01-01 10:00:00",
                                openedCount = 0L,
                                isFavourite = 0L,
                                notes = "",
                                thumbnail = "",
                                profileId = 1L,
                                targetApp = "",
                                syncUid = "",
                                syncEtag = "",
                                updatedAt = 1700000000000L,
                                lastSyncedAt = 0L,
                                profileName = "Work",
                                profileIsPrivate = 0L,
                                tags = listOf("shared", "sprint"),
                                tagDetails = expectedTagDetails,
                            ),
                        ),
                )

            val deviceBStore = FakeLocalStore()
            val mockHttp = KtorCalDavHttpTransport(createMockServerEngine(collectionUrl, remoteMap))

            val engineA =
                CalDavSyncEngine(
                    localStore = deviceAStore,
                    client = CalDavClient(mockHttp),
                    clock = { 1700000100000L },
                )
            val pushRes = engineA.sync(baseConfig())
            assertTrue(pushRes is CalDavSyncResult.Success)
            assertEquals(1, (pushRes as CalDavSyncResult.Success).pushedCount)

            val engineB =
                CalDavSyncEngine(
                    localStore = deviceBStore,
                    client = CalDavClient(mockHttp),
                    clock = { 1700000200000L },
                )
            val pullRes = engineB.sync(baseConfig())
            assertTrue(pullRes is CalDavSyncResult.Success)
            assertEquals(1, (pullRes as CalDavSyncResult.Success).pulledCount)

            val imported = deviceBStore.links.single()
            assertEquals(listOf("shared", "sprint"), imported.tags)
            assertEquals(expectedTagDetails, imported.tagDetails)
            assertEquals("Work", imported.profileName)
        }
}
