package com.yogeshpaliyal.deepr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for ClipboardLink data class behavior.
 *
 * The ClipboardLink data class represents a URL found in the device clipboard.
 * Tests cover equality, copy semantics, and URL storage correctness.
 */
class ClipboardLinkTest {

    @Test
    fun clipboardLink_storesUrlCorrectly() {
        val url = "https://example.com"
        val link = ClipboardLink(url = url)
        assertEquals(url, link.url)
    }

    @Test
    fun clipboardLink_storesCustomSchemeUrl() {
        val url = "myapp://open/resource"
        val link = ClipboardLink(url = url)
        assertEquals("myapp://open/resource", link.url)
    }

    @Test
    fun clipboardLink_equality_sameUrl_areEqual() {
        val url = "https://example.com/path?q=1"
        val link1 = ClipboardLink(url = url)
        val link2 = ClipboardLink(url = url)
        assertEquals(link1, link2)
    }

    @Test
    fun clipboardLink_equality_differentUrls_areNotEqual() {
        val link1 = ClipboardLink(url = "https://example.com")
        val link2 = ClipboardLink(url = "https://other.com")
        assertNotEquals(link1, link2)
    }

    @Test
    fun clipboardLink_copy_preservesUrl() {
        val original = ClipboardLink(url = "https://original.com")
        val copy = original.copy()
        assertEquals(original.url, copy.url)
        assertEquals(original, copy)
    }

    @Test
    fun clipboardLink_copy_withNewUrl_updatesUrl() {
        val original = ClipboardLink(url = "https://original.com")
        val updated = original.copy(url = "https://updated.com")
        assertEquals("https://updated.com", updated.url)
        assertNotEquals(original, updated)
    }

    @Test
    fun clipboardLink_hashCode_sameUrls_sameHashCode() {
        val url = "https://example.com"
        val link1 = ClipboardLink(url = url)
        val link2 = ClipboardLink(url = url)
        assertEquals(link1.hashCode(), link2.hashCode())
    }

    @Test
    fun clipboardLink_storesEmptyUrl() {
        val link = ClipboardLink(url = "")
        assertEquals("", link.url)
    }

    @Test
    fun clipboardLink_storesUrlWithQueryParams() {
        val url = "https://example.com/search?query=test&page=1"
        val link = ClipboardLink(url = url)
        assertEquals(url, link.url)
    }

    @Test
    fun clipboardLink_storesIntentScheme() {
        val url = "intent://scan#Intent;scheme=zxing;package=com.google.zxing.client.android;end"
        val link = ClipboardLink(url = url)
        assertEquals(url, link.url)
    }
}
