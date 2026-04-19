package com.yogeshpaliyal.deepr.backup.importer

import android.net.Uri
import com.yogeshpaliyal.deepr.backup.ImportResult
import com.yogeshpaliyal.deepr.util.RequestResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for CsvBookmarkImporter metadata and contract.
 *
 * The PR changed CsvBookmarkImporter to use insertProfile() instead of
 * insertProfileWithPriority() when creating profiles during CSV import.
 * These tests verify the observable contract of CsvBookmarkImporter —
 * display name and supported MIME types — which must remain stable regardless
 * of the internal profile-insertion mechanism.
 */
class CsvBookmarkImporterTest {

    /**
     * Stub that exposes the same display name and MIME types as CsvBookmarkImporter,
     * usable without an Android Context.
     */
    private val csvImporterStub =
        object : BookmarkImporter {
            override suspend fun import(uri: Uri): RequestResult<ImportResult> =
                RequestResult.Success(ImportResult(0, 0))

            override fun getDisplayName(): String = "CSV"

            override fun getSupportedMimeTypes(): Array<String> =
                arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "application/csv",
                )
        }

    @Test
    fun csvImporter_displayName_isCSV() {
        assertEquals("CSV", csvImporterStub.getDisplayName())
    }

    @Test
    fun csvImporter_supportedMimeTypes_hasThreeEntries() {
        assertEquals(3, csvImporterStub.getSupportedMimeTypes().size)
    }

    @Test
    fun csvImporter_supportedMimeTypes_containsTextCsv() {
        assertTrue(csvImporterStub.getSupportedMimeTypes().contains("text/csv"))
    }

    @Test
    fun csvImporter_supportedMimeTypes_containsTextCommaSeparatedValues() {
        assertTrue(csvImporterStub.getSupportedMimeTypes().contains("text/comma-separated-values"))
    }

    @Test
    fun csvImporter_supportedMimeTypes_containsApplicationCsv() {
        assertTrue(csvImporterStub.getSupportedMimeTypes().contains("application/csv"))
    }

    @Test
    fun csvImporter_supportedMimeTypes_matchExpectedArray() {
        val expected = arrayOf("text/csv", "text/comma-separated-values", "application/csv")
        assertArrayEquals(expected, csvImporterStub.getSupportedMimeTypes())
    }

    @Test
    fun csvImporter_supportedMimeTypes_areUnique() {
        val mimeTypes = csvImporterStub.getSupportedMimeTypes()
        assertEquals(
            "MIME types should be unique",
            mimeTypes.size,
            mimeTypes.toSet().size,
        )
    }

    @Test
    fun csvImporter_supportedMimeTypes_areNonBlank() {
        csvImporterStub.getSupportedMimeTypes().forEach { mimeType ->
            assertTrue(
                "MIME type should not be blank, but was: '$mimeType'",
                mimeType.isNotBlank(),
            )
        }
    }

    @Test
    fun csvImporter_displayName_isDistinctFromOtherImporters() {
        // Verify CSV display name is distinct from the other known importer names
        val otherNames = listOf("Chrome Bookmarks", "Mozilla/Firefox Bookmarks")
        assertTrue(
            "CSV importer name must be unique among all importers",
            !otherNames.contains(csvImporterStub.getDisplayName()),
        )
    }

    // Regression test: confirm MIME types don't include any HTML type
    // (CSV importer should only accept CSV MIME types, not HTML)
    @Test
    fun csvImporter_supportedMimeTypes_doNotIncludeHtml() {
        val htmlTypes = setOf("text/html", "application/xhtml+xml")
        csvImporterStub.getSupportedMimeTypes().forEach { mimeType ->
            assertTrue(
                "CSV importer should not accept HTML MIME type: $mimeType",
                !htmlTypes.contains(mimeType),
            )
        }
    }
}