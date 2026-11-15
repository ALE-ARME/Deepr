package com.yogeshpaliyal.deepr.caldav

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class CalDavSyncEngine(
    private val localStore: CalDavLocalStore,
    private val client: CalDavClient,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun sync(config: CalDavConfig): CalDavSyncResult {
        if (!config.enabled) {
            return CalDavSyncResult.Error("CalDAV sync is disabled")
        }
        if (config.serverUrl.isBlank()) {
            return CalDavSyncResult.Error("Server URL is empty")
        }

        val effectiveCollectionUrl =
            config.collectionUrl.ifBlank {
                val (_, discovered) =
                    client.discoverCollections(
                        serverUrl = config.serverUrl,
                        username = config.username,
                        password = config.password,
                        trustSelfSigned = config.trustSelfSigned,
                    )
                discovered.firstOrNull()?.url
                    ?: return CalDavSyncResult.Error("No compatible VTODO collection found on server")
            }

        val syncTime = clock()
        var pushedCount = 0
        var pulledCount = 0
        var deletedCount = 0

        // 1. Fetch remote items
        val remoteItems =
            client
                .fetchVTodos(
                    collectionUrl = effectiveCollectionUrl,
                    username = config.username,
                    password = config.password,
                    trustSelfSigned = config.trustSelfSigned,
                ).toMutableList()
        val remoteByUid = remoteItems.associateBy { it.vtodo.uid }.toMutableMap()

        // 2. Process local deletions (tombstones)
        val deletedItems = localStore.getDeletedItems()
        for (deleted in deletedItems) {
            val remoteMatch = remoteByUid[deleted.syncUid]
            if (remoteMatch != null) {
                client.deleteVTodo(
                    itemUrl = remoteMatch.href,
                    etag = remoteMatch.etag.ifBlank { null },
                    username = config.username,
                    password = config.password,
                    trustSelfSigned = config.trustSelfSigned,
                )
                remoteByUid.remove(deleted.syncUid)
                deletedCount++
            }
            localStore.removeDeletedItemByUid(deleted.syncUid)
        }

        // 3. Reconcile local items with remote state
        val localLinks = localStore.getLinksForSync(includePrivate = config.syncPrivate)
        val handledRemoteUids = mutableSetOf<String>()

        for (local in localLinks) {
            var uid = local.syncUid
            var matchedRemote = if (uid.isNotBlank()) remoteByUid[uid] else null

            // Initial connect heuristic: avoid duplicating identical bookmarks present on both sides
            if (uid.isBlank()) {
                val candidateRemote =
                    remoteByUid.values.firstOrNull { remote ->
                        remote.vtodo.uid !in handledRemoteUids &&
                            remote.vtodo.link.trim() == local.link.trim() &&
                            (
                                remote.vtodo.profileName.isBlank() ||
                                    remote.vtodo.profileName.equals(local.profileName, ignoreCase = true)
                            ) &&
                            remote.vtodo.isPrivate == (local.profileIsPrivate == 1L)
                    }
                if (candidateRemote != null) {
                    uid = candidateRemote.vtodo.uid
                    matchedRemote = candidateRemote
                } else {
                    uid = UUID.randomUUID().toString()
                }
                localStore.updateSyncState(
                    linkId = local.id,
                    syncUid = uid,
                    syncEtag = local.syncEtag,
                    updatedAt = if (local.updatedAt > 0L) local.updatedAt else syncTime,
                    lastSyncedAt = local.lastSyncedAt,
                )
            }

            if (matchedRemote != null) {
                handledRemoteUids.add(uid)
                val localChanged = local.lastSyncedAt == 0L || local.updatedAt > local.lastSyncedAt
                val remoteChanged =
                    local.syncEtag.isBlank() ||
                        (matchedRemote.etag.isNotBlank() && matchedRemote.etag != local.syncEtag)

                when {
                    !localChanged && !remoteChanged -> {
                        if (local.lastSyncedAt == 0L) {
                            localStore.updateSyncState(
                                linkId = local.id,
                                syncUid = uid,
                                syncEtag = matchedRemote.etag,
                                updatedAt = local.updatedAt,
                                lastSyncedAt = syncTime,
                            )
                        }
                    }
                    localChanged && !remoteChanged -> {
                        pushLocalLink(
                            collectionUrl = effectiveCollectionUrl,
                            targetHref = matchedRemote.href,
                            etag = matchedRemote.etag.ifBlank { null },
                            local = local.copy(syncUid = uid),
                            syncTime = syncTime,
                            config = config,
                        )
                        pushedCount++
                    }
                    !localChanged && remoteChanged -> {
                        val remoteModified =
                            matchedRemote.vtodo.lastModifiedMillis.takeIf { it > 0L } ?: syncTime
                        localStore.upsertLinkFromRemote(
                            existingLinkId = local.id,
                            vtodo = matchedRemote.vtodo,
                            etag = matchedRemote.etag,
                            updatedAt = remoteModified,
                            lastSyncedAt = syncTime,
                        )
                        pulledCount++
                    }
                    else -> {
                        // Conflict: Last-Write-Wins comparing local updatedAt vs remote LAST-MODIFIED
                        val remoteModified = matchedRemote.vtodo.lastModifiedMillis
                        if (local.updatedAt >= remoteModified) {
                            pushLocalLink(
                                collectionUrl = effectiveCollectionUrl,
                                targetHref = matchedRemote.href,
                                etag = matchedRemote.etag.ifBlank { null },
                                local = local.copy(syncUid = uid),
                                syncTime = syncTime,
                                config = config,
                            )
                            pushedCount++
                        } else {
                            localStore.upsertLinkFromRemote(
                                existingLinkId = local.id,
                                vtodo = matchedRemote.vtodo,
                                etag = matchedRemote.etag,
                                updatedAt = remoteModified.takeIf { it > 0L } ?: syncTime,
                                lastSyncedAt = syncTime,
                            )
                            pulledCount++
                        }
                    }
                }
            } else {
                // Absent on server: either deleted remotely (if previously synced) or new locally
                if (local.lastSyncedAt > 0L) {
                    localStore.deleteLocalLinkFromRemote(local.id)
                    deletedCount++
                } else {
                    pushLocalLink(
                        collectionUrl = effectiveCollectionUrl,
                        targetHref = null,
                        etag = null,
                        local = local.copy(syncUid = uid),
                        syncTime = syncTime,
                        config = config,
                    )
                    pushedCount++
                }
            }
        }

        // 4. Pull remaining remote items not present locally
        for ((uid, remote) in remoteByUid) {
            if (uid in handledRemoteUids) continue
            if (remote.vtodo.isPrivate && !config.syncPrivate) {
                // Do not import or touch private remote entries while syncPrivate is disabled
                continue
            }
            val remoteModified = remote.vtodo.lastModifiedMillis.takeIf { it > 0L } ?: syncTime
            localStore.upsertLinkFromRemote(
                existingLinkId = null,
                vtodo = remote.vtodo,
                etag = remote.etag,
                updatedAt = remoteModified,
                lastSyncedAt = syncTime,
            )
            pulledCount++
        }

        return CalDavSyncResult.Success(
            pushedCount = pushedCount,
            pulledCount = pulledCount,
            deletedCount = deletedCount,
        )
    }

    private suspend fun pushLocalLink(
        collectionUrl: String,
        targetHref: String?,
        etag: String?,
        local: CalDavLocalLink,
        syncTime: Long,
        config: CalDavConfig,
    ) {
        val updatedAt = if (local.updatedAt > 0L) local.updatedAt else syncTime
        val vtodo =
            CalDavVTodo(
                uid = local.syncUid,
                link = local.link,
                summary = local.name.ifBlank { local.link },
                description = local.notes,
                categories = local.tags,
                tagScopes = local.tagDetails.ifEmpty { local.tags.map { CalDavTag(name = it, isGlobal = true) } },
                profileName = local.profileName,
                isFavourite = local.isFavourite == 1L,
                isPrivate = local.profileIsPrivate == 1L,
                openedCount = local.openedCount,
                thumbnail = local.thumbnail,
                targetApp = local.targetApp,
                createdMillis = parseDbTimestamp(local.createdAt),
                lastModifiedMillis = updatedAt,
            )

        val (_, newEtag) =
            client.putVTodo(
                collectionUrl = collectionUrl,
                targetHref = targetHref,
                vtodo = vtodo,
                etag = etag,
                username = config.username,
                password = config.password,
                trustSelfSigned = config.trustSelfSigned,
            )

        localStore.updateSyncState(
            linkId = local.id,
            syncUid = local.syncUid,
            syncEtag = newEtag,
            updatedAt = updatedAt,
            lastSyncedAt = syncTime,
        )
    }

    private fun parseDbTimestamp(dbTimestamp: String): Long {
        if (dbTimestamp.isBlank()) return 0L
        val sdf =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        return runCatching { sdf.parse(dbTimestamp)?.time ?: 0L }.getOrDefault(0L)
    }
}
