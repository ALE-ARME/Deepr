package com.yogeshpaliyal.deepr.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests verifying the LinkRepository interface method signatures changed in this PR.
 *
 * The PR removed the `searchQuery4` parameter from `getLinksAndTags`, reducing it from
 * 13 to 12 parameters. These tests verify that the interface has the expected parameter count
 * and that the method exists with the correct name.
 */
class LinkRepositorySignatureTest {

    @Test
    fun linkRepository_getLinksAndTags_methodExists() {
        val methods = LinkRepository::class.java.methods
        val method = methods.firstOrNull { it.name == "getLinksAndTags" }
        assertNotNull("getLinksAndTags method should exist on LinkRepository", method)
    }

    @Test
    fun linkRepository_getLinksAndTags_hasCorrectParameterCount() {
        // After the PR, getLinksAndTags takes 12 parameters (searchQuery4 was removed).
        // The Kotlin interface method has: profileId, searchQuery1, searchQuery2, searchQuery3,
        // favouriteFilter1, favouriteFilter2, tagIdsString1, tagIdsString2,
        // sortType1, sortField1, sortType2, sortField2 = 12 params
        val methods = LinkRepository::class.java.methods
        val method = methods.firstOrNull { it.name == "getLinksAndTags" }
        assertNotNull("getLinksAndTags method should exist", method)
        assertEquals(
            "getLinksAndTags should have exactly 12 parameters (searchQuery4 was removed)",
            12,
            method!!.parameterCount,
        )
    }

    @Test
    fun linkRepository_getLinksAndTags_doesNotHaveThirteenParameters() {
        // Regression: verify searchQuery4 was not re-added
        val methods = LinkRepository::class.java.methods
        val method = methods.firstOrNull { it.name == "getLinksAndTags" }
        assertNotNull("getLinksAndTags method should exist", method)
        assertTrue(
            "getLinksAndTags must not have 13 parameters (searchQuery4 should have been removed)",
            method!!.parameterCount != 13,
        )
    }

    @Test
    fun linkRepository_insertProfile_methodExists() {
        // The PR changed CsvBookmarkImporter to call insertProfile (no priority parameter).
        // Verify the method exists on the repository interface.
        val methods = LinkRepository::class.java.methods
        val insertProfileMethod = methods.firstOrNull { it.name == "insertProfile" }
        assertNotNull("insertProfile method should exist on LinkRepository", insertProfileMethod)
    }

    @Test
    fun linkRepository_hasExpectedCrudMethods() {
        val methodNames = LinkRepository::class.java.methods.map { it.name }.toSet()

        // Core CRUD operations that must remain stable
        assertTrue("insertDeepr should exist", "insertDeepr" in methodNames)
        assertTrue("deleteDeeprById should exist", "deleteDeeprById" in methodNames)
        assertTrue("updateDeeplink should exist", "updateDeeplink" in methodNames)
        assertTrue("getLinksAndTags should exist", "getLinksAndTags" in methodNames)
        assertTrue("insertProfile should exist", "insertProfile" in methodNames)
        assertTrue("deleteProfile should exist", "deleteProfile" in methodNames)
    }
}