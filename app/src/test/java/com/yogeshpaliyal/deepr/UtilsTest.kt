package com.yogeshpaliyal.deepr

import com.yogeshpaliyal.deepr.util.extractUrlFromSharedText
import com.yogeshpaliyal.deepr.util.getFallbackTitleFromLink
import com.yogeshpaliyal.deepr.util.isValidDeeplink
import com.yogeshpaliyal.deepr.util.normalizeLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test for utility functions, specifically link normalization
 */
class UtilsTest {
    @Test
    fun normalizeLink_addsHttpsToUrlWithoutScheme() {
        assertEquals("https://cnn.com", normalizeLink("cnn.com"))
        assertEquals("https://www.google.com", normalizeLink("www.google.com"))
        assertEquals("https://example.com/path", normalizeLink("example.com/path"))
    }

    @Test
    fun normalizeLink_preservesExistingHttpsScheme() {
        assertEquals("https://cnn.com", normalizeLink("https://cnn.com"))
        assertEquals("https://www.google.com", normalizeLink("https://www.google.com"))
    }

    @Test
    fun normalizeLink_preservesExistingHttpScheme() {
        assertEquals("http://example.com", normalizeLink("http://example.com"))
    }

    @Test
    fun normalizeLink_preservesCustomSchemes() {
        assertEquals("app://deeplink", normalizeLink("app://deeplink"))
        assertEquals("myapp://open", normalizeLink("myapp://open"))
        assertEquals("intent://action", normalizeLink("intent://action"))
    }

    @Test
    fun normalizeLink_handlesBlankInput() {
        assertEquals("", normalizeLink(""))
        assertEquals("", normalizeLink("   "))
    }

    @Test
    fun normalizeLink_trimsWhitespace() {
        assertEquals("https://cnn.com", normalizeLink("  cnn.com  "))
        assertEquals("https://example.com", normalizeLink(" example.com "))
    }

    @Test
    fun normalizeLink_handlesUrlsWithoutDots() {
        // For custom schemes without dots, return as-is
        assertEquals("localhost", normalizeLink("localhost"))
    }

    @Test
    fun normalizeLink_handlesComplexUrls() {
        assertEquals(
            "https://www.example.com/path?query=value",
            normalizeLink("www.example.com/path?query=value"),
        )
        assertEquals(
            "https://example.com:8080/path",
            normalizeLink("example.com:8080/path"),
        )
    }

    @Test
    fun extractUrlFromSharedText_plainUrl() {
        val (url, title) = extractUrlFromSharedText("https://example.com/page")
        assertEquals("https://example.com/page", url)
        assertNull(title)
    }

    @Test
    fun extractUrlFromSharedText_withPrecedingText() {
        val (url, title) = extractUrlFromSharedText("Check this out: https://youtu.be/dQw4w9WgXcQ")
        assertEquals("https://youtu.be/dQw4w9WgXcQ", url)
        assertEquals("Check this out", title)
    }

    @Test
    fun extractUrlFromSharedText_withNewlinesAndTrailingPunctuation() {
        val (url, title) = extractUrlFromSharedText("Breaking News Article\nhttps://example.com/news.")
        assertEquals("https://example.com/news", url)
        assertEquals("Breaking News Article", title)
    }

    @Test
    fun getFallbackTitleFromLink_extractsLastPathSegment() {
        val title = getFallbackTitleFromLink("https://en.wikipedia.org/wiki/Kotlin_(programming_language)")
        assertEquals("Kotlin (programming language)", title)
    }

    @Test
    fun getFallbackTitleFromLink_extractsHostWhenNoPath() {
        val title = getFallbackTitleFromLink("https://example.com")
        assertEquals("example.com", title)
    }

    @Test
    fun isValidDeeplink_acceptsHierarchicalLinks() {
        assertTrue(isValidDeeplink("https://example.com"))
        assertTrue(isValidDeeplink("example.com/path"))
        assertTrue(isValidDeeplink("app://deeplink"))
        assertTrue(isValidDeeplink("intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun isValidDeeplink_acceptsOpaqueLinks() {
        assertTrue(isValidDeeplink("about:config"))
        assertTrue(isValidDeeplink("about:config?filter=browser.tabs"))
        assertTrue(isValidDeeplink("mailto:someone@example.com"))
        assertTrue(isValidDeeplink("tel:+911234567890"))
        assertTrue(isValidDeeplink("tel:123"))
        assertTrue(isValidDeeplink("mailto:?subject=Hello"))
    }

    @Test
    fun normalizeLink_preservesOpaqueSchemes() {
        assertEquals("mailto:someone@example.com", normalizeLink("mailto:someone@example.com"))
        assertEquals("about:config?filter=browser.tabs", normalizeLink("about:config?filter=browser.tabs"))
        assertEquals("about:config", normalizeLink("about:config"))
    }

    @Test
    fun isValidDeeplink_rejectsInvalidLinks() {
        assertFalse(isValidDeeplink(""))
        assertFalse(isValidDeeplink("   "))
        assertFalse(isValidDeeplink("localhost"))
        assertFalse(isValidDeeplink("localhost:8080"))
        assertFalse(isValidDeeplink("localhost:8080?x=1"))
        assertFalse(isValidDeeplink("localhost:8080#section"))
        assertFalse(isValidDeeplink("hello world"))
        assertFalse(isValidDeeplink("https://"))
        assertFalse(isValidDeeplink("about:"))
        assertFalse(isValidDeeplink("1abc:foo"))
    }
}
