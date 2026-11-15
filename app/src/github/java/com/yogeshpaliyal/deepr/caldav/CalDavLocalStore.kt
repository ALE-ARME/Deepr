package com.yogeshpaliyal.deepr.caldav

import com.yogeshpaliyal.deepr.DeeprQueries

data class CalDavLocalLink(
    val id: Long,
    val link: String,
    val name: String,
    val createdAt: String,
    val openedCount: Long,
    val isFavourite: Long,
    val notes: String,
    val thumbnail: String,
    val profileId: Long,
    val targetApp: String,
    val syncUid: String,
    val syncEtag: String,
    val updatedAt: Long,
    val lastSyncedAt: Long,
    val profileName: String,
    val profileIsPrivate: Long,
    val tags: List<String>,
    val tagDetails: List<CalDavTag> = emptyList(),
)

data class CalDavDeletedItem(
    val id: Long,
    val syncUid: String,
    val deletedAt: Long,
)

interface CalDavLocalStore {
    suspend fun getLinksForSync(includePrivate: Boolean): List<CalDavLocalLink>

    suspend fun getDeletedItems(): List<CalDavDeletedItem>

    suspend fun removeDeletedItemByUid(syncUid: String)

    suspend fun updateSyncState(
        linkId: Long,
        syncUid: String,
        syncEtag: String,
        updatedAt: Long,
        lastSyncedAt: Long,
    )

    suspend fun deleteLocalLinkFromRemote(linkId: Long)

    suspend fun upsertLinkFromRemote(
        existingLinkId: Long?,
        vtodo: CalDavVTodo,
        etag: String,
        updatedAt: Long,
        lastSyncedAt: Long,
    )
}

class SqlDelightCalDavLocalStore(
    private val deeprQueries: DeeprQueries,
) : CalDavLocalStore {
    override suspend fun getLinksForSync(includePrivate: Boolean): List<CalDavLocalLink> {
        val includePrivParam = if (includePrivate) 1L else 0L
        val allowedProfiles =
            deeprQueries
                .getAllProfiles()
                .executeAsList()
                .filter { includePrivate || it.isPrivate == 0L }
                .associate { it.id to it.name }
        return deeprQueries
            .getLinksForCalDavSync(includePrivate = includePrivParam)
            .executeAsList()
            .map { row ->
                val tagRows = deeprQueries.getTagsForLink(row.id).executeAsList()
                val tags = tagRows.map { it.name }
                val tagDetails =
                    tagRows.map { tag ->
                        val isGlobal = tag.isGlobal == 1L
                        val profileNames =
                            if (isGlobal) {
                                emptyList()
                            } else {
                                deeprQueries
                                    .getProfilesForTag(tag.id)
                                    .executeAsList()
                                    .mapNotNull { allowedProfiles[it] }
                                    .distinct()
                            }
                        CalDavTag(
                            name = tag.name,
                            isGlobal = isGlobal,
                            profileNames = profileNames,
                        )
                    }
                CalDavLocalLink(
                    id = row.id,
                    link = row.link,
                    name = row.name,
                    createdAt = row.createdAt,
                    openedCount = row.openedCount,
                    isFavourite = row.isFavourite,
                    notes = row.notes,
                    thumbnail = row.thumbnail,
                    profileId = row.profileId,
                    targetApp = row.targetApp,
                    syncUid = row.syncUid,
                    syncEtag = row.syncEtag,
                    updatedAt = row.updatedAt,
                    lastSyncedAt = row.lastSyncedAt,
                    profileName = row.profileName,
                    profileIsPrivate = row.profileIsPrivate,
                    tags = tags,
                    tagDetails = tagDetails,
                )
            }
    }

    override suspend fun getDeletedItems(): List<CalDavDeletedItem> =
        deeprQueries.getAllDeletedSyncItems().executeAsList().map { row ->
            CalDavDeletedItem(
                id = row.id,
                syncUid = row.syncUid,
                deletedAt = row.deletedAt,
            )
        }

    override suspend fun removeDeletedItemByUid(syncUid: String) {
        deeprQueries.deleteDeletedSyncItemByUid(syncUid)
    }

    override suspend fun updateSyncState(
        linkId: Long,
        syncUid: String,
        syncEtag: String,
        updatedAt: Long,
        lastSyncedAt: Long,
    ) {
        deeprQueries.updateDeeprSyncState(
            syncUid = syncUid,
            syncEtag = syncEtag,
            updatedAt = updatedAt,
            lastSyncedAt = lastSyncedAt,
            id = linkId,
        )
    }

    override suspend fun deleteLocalLinkFromRemote(linkId: Long) {
        deeprQueries.transaction {
            deeprQueries.clearSyncUidBeforeDelete(linkId)
            deeprQueries.deleteLinkRelations(linkId)
            deeprQueries.deleteDeeprById(linkId)
        }
    }

    override suspend fun upsertLinkFromRemote(
        existingLinkId: Long?,
        vtodo: CalDavVTodo,
        etag: String,
        updatedAt: Long,
        lastSyncedAt: Long,
    ) {
        deeprQueries.transaction {
            val targetPrivateFlag = if (vtodo.isPrivate) 1L else 0L
            val profileId = resolveOrCreateProfile(vtodo.profileName, targetPrivateFlag)
            val resolvedName = vtodo.summary.ifBlank { vtodo.link }
            val favFlag = if (vtodo.isFavourite) 1L else 0L

            val linkId =
                if (existingLinkId != null) {
                    deeprQueries.updateDeeprFromSync(
                        link = vtodo.link,
                        name = resolvedName,
                        notes = vtodo.description,
                        thumbnail = vtodo.thumbnail,
                        isFavourite = favFlag,
                        profileId = profileId,
                        targetApp = vtodo.targetApp,
                        openedCount = vtodo.openedCount,
                        id = existingLinkId,
                    )
                    existingLinkId
                } else {
                    val createdText =
                        if (vtodo.createdMillis > 0L) {
                            VTodoMapper.formatDbTimestamp(vtodo.createdMillis)
                        } else {
                            VTodoMapper.formatDbTimestamp(lastSyncedAt)
                        }
                    deeprQueries.insertDeeprFromSync(
                        link = vtodo.link,
                        name = resolvedName,
                        openedCount = vtodo.openedCount,
                        notes = vtodo.description,
                        thumbnail = vtodo.thumbnail,
                        isFavourite = favFlag,
                        createdAt = createdText,
                        profileId = profileId,
                        targetApp = vtodo.targetApp,
                        syncUid = vtodo.uid,
                        syncEtag = etag,
                        updatedAt = updatedAt,
                        lastSyncedAt = lastSyncedAt,
                    )
                    deeprQueries.lastInsertRowId().executeAsOne()
                }

            val tagScopesByName =
                vtodo.tagScopes
                    .filter { it.name.isNotBlank() }
                    .associateBy { it.name.trim() }

            deeprQueries.deleteLinkRelations(linkId)
            vtodo.categories.forEach { rawTagName ->
                val tagName = rawTagName.trim()
                if (tagName.isNotEmpty()) {
                    val scope = tagScopesByName[tagName]
                    if (scope != null) {
                        val isGlobalFlag = if (scope.isGlobal) 1L else 0L
                        deeprQueries.insertTag(tagName, targetPrivateFlag, isGlobalFlag)
                        val tag = deeprQueries.getTagByName(tagName, targetPrivateFlag).executeAsOneOrNull()
                        if (tag != null) {
                            if (tag.isGlobal != isGlobalFlag) {
                                deeprQueries.updateTag(tag.name, isGlobalFlag, tag.id)
                            }
                            if (scope.isGlobal) {
                                val existingBindings = deeprQueries.getProfilesForTag(tag.id).executeAsList()
                                if (existingBindings.isNotEmpty()) {
                                    deeprQueries.deleteTagProfiles(tag.id)
                                }
                            } else {
                                val resolvedProfileIds =
                                    scope.profileNames
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .distinct()
                                        .map { pName -> resolveOrCreateProfile(pName, targetPrivateFlag) }
                                        .toSet()
                                        .ifEmpty { setOf(profileId) }
                                val existingProfileIds =
                                    deeprQueries.getProfilesForTag(tag.id).executeAsList().toSet()
                                if (existingProfileIds != resolvedProfileIds) {
                                    deeprQueries.deleteTagProfiles(tag.id)
                                    resolvedProfileIds.forEach { pId ->
                                        deeprQueries.insertTagProfile(tag.id, pId)
                                    }
                                }
                            }
                            deeprQueries.addTagToLink(linkId, tag.id)
                        }
                    } else {
                        deeprQueries.insertTag(tagName, targetPrivateFlag, 1L)
                        val tag = deeprQueries.getTagByName(tagName, targetPrivateFlag).executeAsOneOrNull()
                        if (tag != null) {
                            deeprQueries.addTagToLink(linkId, tag.id)
                        }
                    }
                }
            }

            // Ensure exact remote sync metadata & updatedAt are preserved after tag/update triggers
            deeprQueries.updateDeeprSyncState(
                syncUid = vtodo.uid,
                syncEtag = etag,
                updatedAt = updatedAt,
                lastSyncedAt = lastSyncedAt,
                id = linkId,
            )
        }
    }

    private fun resolveOrCreateProfile(
        profileName: String,
        isPrivateFlag: Long,
    ): Long {
        val cleanName = profileName.trim()
        if (cleanName.isNotEmpty()) {
            val existingExact = deeprQueries.getProfileByName(cleanName, isPrivateFlag).executeAsOneOrNull()
            if (existingExact != null) {
                return existingExact.id
            }
            deeprQueries.insertProfileAutoPriority(cleanName, isPrivateFlag)
            val created = deeprQueries.getProfileByName(cleanName, isPrivateFlag).executeAsOneOrNull()
            if (created != null) {
                return created.id
            }
        }
        val allProfiles = deeprQueries.getAllProfiles().executeAsList()
        val matchingVisibility = allProfiles.firstOrNull { it.isPrivate == isPrivateFlag }
        if (matchingVisibility != null) {
            return matchingVisibility.id
        }
        val fallbackName = if (isPrivateFlag == 1L) "Private" else "Default"
        deeprQueries.insertProfileAutoPriority(fallbackName, isPrivateFlag)
        return deeprQueries
            .getProfileByName(fallbackName, isPrivateFlag)
            .executeAsOneOrNull()
            ?.id
            ?: allProfiles.first().id
    }
}
