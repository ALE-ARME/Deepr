package com.yogeshpaliyal.deepr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for the ClipboardLink data class introduced in this PR.
 */
class ClipboardLinkTest {

    @Test
    fun clipboardLink_createsWithUrl() {
        val link = ClipboardLink("https://example.com")
        assertEquals("https://example.com", link.url)
    }

    @Test
    fun clipboardLink_equality_sameUrl() {
        val link1 = ClipboardLink("https://example.com")
        val link2 = ClipboardLink("https://example.com")
        assertEquals(link1, link2)
    }

    @Test
    fun clipboardLink_equality_differentUrl() {
        val link1 = ClipboardLink("https://example.com")
        val link2 = ClipboardLink("https://other.com")
        assertNotEquals(link1, link2)
    }

    @Test
    fun clipboardLink_copy_changesUrl() {
        val original = ClipboardLink("https://example.com")
        val copied = original.copy(url = "https://other.com")
        assertEquals("https://other.com", copied.url)
        assertEquals("https://example.com", original.url)
    }

    @Test
    fun clipboardLink_copy_preservesUrl() {
        val original = ClipboardLink("app://deeplink/path?param=value")
        val copied = original.copy()
        assertEquals(original, copied)
        assertEquals(original.url, copied.url)
    }

    @Test
    fun clipboardLink_hashCode_equalObjectsHaveSameHashCode() {
        val link1 = ClipboardLink("https://example.com")
        val link2 = ClipboardLink("https://example.com")
        assertEquals(link1.hashCode(), link2.hashCode())
    }

    @Test
    fun clipboardLink_toString_containsUrl() {
        val url = "https://example.com/path"
        val link = ClipboardLink(url)
        val str = link.toString()
        assertNotNull(str)
        assert(str.contains(url)) { "toString() should contain the url, but was: $str" }
    }

    @Test
    fun clipboardLink_supportsCustomSchemes() {
        val url = "app://deeplink/action"
        val link = ClipboardLink(url)
        assertEquals(url, link.url)
    }

    @Test
    fun clipboardLink_supportsHttpScheme() {
        val url = "http://example.com"
        val link = ClipboardLink(url)
        assertEquals(url, link.url)
    }

    @Test
    fun clipboardLink_supportsNormalizedHttpsUrl() {
        val url = "https://www.example.com/path?query=value&foo=bar"
        val link = ClipboardLink(url)
        assertEquals(url, link.url)
    }

    @Test
    fun clipboardLink_notEqualToNull() {
        val link = ClipboardLink("https://example.com")
        assertNotEquals(link, null)
    }

    @Test
    fun clipboardLink_notEqualToDifferentType() {
        val link = ClipboardLink("https://example.com")
        assertNotEquals(link, "https://example.com")
    }

    // Regression test: ensure ClipboardLink and SharedLink are distinct types
    @Test
    fun clipboardLink_isDistinctFromSharedLink() {
        val clipboardLink = ClipboardLink("https://example.com")
        val sharedLink = SharedLink("https://example.com", null)
        // Both hold the same URL string but are different types
        assertEquals(clipboardLink.url, sharedLink.url)
        assertNotEquals(clipboardLink as Any, sharedLink as Any)
    }
}