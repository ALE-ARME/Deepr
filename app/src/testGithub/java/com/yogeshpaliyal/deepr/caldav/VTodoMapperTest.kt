package com.yogeshpaliyal.deepr.caldav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VTodoMapperTest {
    @Test
    fun `toIcs and parseIcs roundtrip preserves all metadata and tags`() {
        val original =
            CalDavVTodo(
                uid = "deepr-uid-1234",
                link = "https://example.com/articles/very-long-path?utm_source=deepr&ref=1234567890",
                summary = "Deep Dive; Kotlin, Coroutines & CalDAV",
                description = "Line 1\nLine 2; with semicolon, comma, and \\ backslash",
                categories = listOf("kotlin", "dev,ops", "sync;test"),
                profileName = "Work",
                isFavourite = true,
                isPrivate = true,
                openedCount = 7L,
                thumbnail = "https://cdn.example.com/thumb.png",
                targetApp = "org.mozilla.firefox",
                createdMillis = 1700000000000L,
                lastModifiedMillis = 1700050000000L,
            )

        val ics = VTodoMapper.toIcs(original)
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("BEGIN:VTODO"))
        assertTrue(ics.contains("END:VTODO"))
        assertTrue(ics.contains("END:VCALENDAR"))

        // Verify RFC 5545 max 75 octets per line
        ics.split("\r\n").filter { it.isNotEmpty() }.forEach { physicalLine ->
            assertTrue(
                "Line exceeds 75 octets: ${physicalLine.toByteArray(Charsets.UTF_8).size} bytes -> $physicalLine",
                physicalLine.toByteArray(Charsets.UTF_8).size <= 75,
            )
        }

        val parsed = VTodoMapper.parseIcs(ics)
        assertNotNull(parsed)
        assertEquals(original.uid, parsed!!.uid)
        assertEquals(original.link, parsed.link)
        assertEquals(original.summary, parsed.summary)
        assertEquals(original.description, parsed.description)
        assertEquals(original.categories, parsed.categories)
        assertEquals(original.profileName, parsed.profileName)
        assertEquals(original.isFavourite, parsed.isFavourite)
        assertEquals(original.isPrivate, parsed.isPrivate)
        assertEquals(original.openedCount, parsed.openedCount)
        assertEquals(original.thumbnail, parsed.thumbnail)
        assertEquals(original.targetApp, parsed.targetApp)
        assertEquals(1700000000000L, parsed.createdMillis)
        assertEquals(1700050000000L, parsed.lastModifiedMillis)
    }

    @Test
    fun `parseIcs extracts URL fallback from DESCRIPTION when URL property missing`() {
        val externalVTodo =
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//OtherClient//EN
            BEGIN:VTODO
            UID:ext-1
            DTSTAMP:20250101T120000Z
            SUMMARY:Read this later
            DESCRIPTION:Check out https://kotlinlang.org/docs/home.html for details.
            CATEGORIES:reading,tech
            END:VTODO
            END:VCALENDAR
            """.trimIndent()

        val parsed = VTodoMapper.parseIcs(externalVTodo)
        assertNotNull(parsed)
        assertEquals("ext-1", parsed!!.uid)
        assertEquals("https://kotlinlang.org/docs/home.html", parsed.link)
        assertEquals("Read this later", parsed.summary)
        assertEquals(listOf("reading", "tech"), parsed.categories)
        assertFalse(parsed.isPrivate)
        assertFalse(parsed.isFavourite)
    }

    @Test
    fun `parseIcs handles folded multibyte UTF-8 lines cleanly`() {
        val emojiSummary = "Bookmark 🚀✨🔥 molto lungo per testare il folding UTF-8 oltre i 75 byte senza corruzione"
        val item =
            CalDavVTodo(
                uid = "utf8-test",
                link = "https://example.org/utf8",
                summary = emojiSummary,
                description = "Note con caratteri speciali: àèéìòù € — test",
                lastModifiedMillis = 1710000000000L,
            )

        val ics = VTodoMapper.toIcs(item)
        val parsed = VTodoMapper.parseIcs(ics)
        assertNotNull(parsed)
        assertEquals(emojiSummary, parsed!!.summary)
        assertEquals(item.description, parsed.description)
    }

    @Test
    fun `parseIcs returns null when UID or URL is missing`() {
        val noUrl =
            """
            BEGIN:VCALENDAR
            BEGIN:VTODO
            UID:no-url
            SUMMARY:Just a task without link
            END:VTODO
            END:VCALENDAR
            """.trimIndent()
        assertNull(VTodoMapper.parseIcs(noUrl))

        val noUid =
            """
            BEGIN:VCALENDAR
            BEGIN:VTODO
            URL:https://example.com
            SUMMARY:Missing UID
            END:VTODO
            END:VCALENDAR
            """.trimIndent()
        assertNull(VTodoMapper.parseIcs(noUid))
    }

    @Test
    fun `toIcs and parseIcs preserve tagScopes with special characters and emojis`() {
        val scopes =
            listOf(
                CalDavTag(name = "kotlin", isGlobal = true),
                CalDavTag(
                    name = "dev,ops;team",
                    isGlobal = false,
                    profileNames = listOf("Work;Main", "Studio 🚀,Lab"),
                ),
            )
        val item =
            CalDavVTodo(
                uid = "tag-scope-1",
                link = "https://example.com/scoped",
                summary = "Scoped Tags",
                categories = listOf("kotlin", "dev,ops;team"),
                tagScopes = scopes,
                profileName = "Work;Main",
                lastModifiedMillis = 1715000000000L,
            )

        val ics = VTodoMapper.toIcs(item)
        assertTrue(ics.contains("X-DEEPR-TAG-SCOPES:"))
        ics.split("\r\n").filter { it.isNotEmpty() }.forEach { physicalLine ->
            assertTrue(
                "Line exceeds 75 octets: ${physicalLine.toByteArray(Charsets.UTF_8).size} bytes -> $physicalLine",
                physicalLine.toByteArray(Charsets.UTF_8).size <= 75,
            )
        }

        val parsed = VTodoMapper.parseIcs(ics)
        assertNotNull(parsed)
        assertEquals(listOf("kotlin", "dev,ops;team"), parsed!!.categories)
        assertEquals(scopes, parsed.tagScopes)
    }
}
