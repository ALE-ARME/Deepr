package com.yogeshpaliyal.deepr

import com.yogeshpaliyal.deepr.util.isValidDeeplink
import com.yogeshpaliyal.deepr.util.normalizeLink
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests covering the clipboard link detection logic introduced in this PR.
 *
 * The Dashboard composable reads clipboard text, normalizes it with normalizeLink(),
 * then validates it with isValidDeeplink() before creating a ClipboardLink.
 * These tests verify that the validation pipeline correctly identifies valid vs invalid
 * deeplinks that users might have in their clipboard.
 */
class ClipboardDetectionTest {

    // --- Valid clipboard content that should trigger ClipboardLink creation ---

    @Test
    fun clipboardDetection_httpsUrl_isValid() {
        val text = "https://example.com"
        val normalized = normalizeLink(text)
        assertTrue(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_httpUrl_isValid() {
        val text = "http://example.com"
        val normalized = normalizeLink(text)
        assertTrue(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_urlWithoutScheme_isValidAfterNormalization() {
        // User copies "example.com" — normalizeLink adds https://
        val text = "example.com"
        val normalized = normalizeLink(text)
        assertTrue(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_urlWithPath_isValid() {
        val text = "https://example.com/some/path"
        val normalized = normalizeLink(text)
        assertTrue(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_urlWithQueryParams_isValid() {
        val text = "https://example.com/search?q=hello&lang=en"
        val normalized = normalizeLink(text)
        assertTrue(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_customAppScheme_isValid() {
        val text = "app://deeplink/home"
        val normalized = normalizeLink(text)
        assertTrue(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_intentScheme_isValid() {
        val text = "intent://example.com#Intent;scheme=https;end"
        val normalized = normalizeLink(text)
        assertTrue(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_leadingWhitespace_isValidAfterNormalization() {
        // User may accidentally copy whitespace with a URL
        val text = "  https://example.com  "
        val normalized = normalizeLink(text)
        assertTrue(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_subdomain_isValid() {
        val text = "https://sub.example.com/page"
        val normalized = normalizeLink(text)
        assertTrue(isValidDeeplink(normalized))
    }

    // --- Invalid clipboard content that should NOT trigger ClipboardLink creation ---

    @Test
    fun clipboardDetection_blankText_isNotValid() {
        val text = ""
        val normalized = normalizeLink(text)
        assertFalse(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_whitespaceOnly_isNotValid() {
        val text = "   "
        val normalized = normalizeLink(text)
        assertFalse(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_plainTextNoUrl_isNotValid() {
        // Random clipboard text like "hello world" should not be a deeplink
        val text = "hello world"
        val normalized = normalizeLink(text)
        // "hello world" has no dot and no scheme, so normalizeLink returns as-is
        // isValidDeeplink requires scheme AND authority
        assertFalse(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_singleWord_isNotValid() {
        val text = "clipboard"
        val normalized = normalizeLink(text)
        assertFalse(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_numberOnly_isNotValid() {
        val text = "12345"
        val normalized = normalizeLink(text)
        assertFalse(isValidDeeplink(normalized))
    }

    // --- Normalization preserves URLs correctly for clipboard use ---

    @Test
    fun clipboardDetection_normalizationPreservesHttps() {
        val text = "https://example.com/path"
        val normalized = normalizeLink(text)
        assertTrue(normalized.startsWith("https://"))
    }

    @Test
    fun clipboardDetection_normalizationAddsHttpsToBareDomain() {
        val text = "example.com"
        val normalized = normalizeLink(text)
        assertTrue(normalized.startsWith("https://"))
    }

    @Test
    fun clipboardDetection_normalizationPreservesCustomScheme() {
        val text = "myapp://open/screen"
        val normalized = normalizeLink(text)
        assertTrue(normalized.startsWith("myapp://"))
    }

    // --- Edge cases for clipboard detection robustness ---

    @Test
    fun clipboardDetection_urlWithPort_isValid() {
        val text = "http://localhost:8080/api"
        val normalized = normalizeLink(text)
        assertTrue(isValidDeeplink(normalized))
    }

    @Test
    fun clipboardDetection_urlWithFragment_isValid() {
        val text = "https://example.com/page#section"
        val normalized = normalizeLink(text)
        assertTrue(isValidDeeplink(normalized))
    }

    // Regression: pure text with spaces (no scheme, no dot) should never be a valid deeplink
    @Test
    fun clipboardDetection_textWithSpaces_isNotValidDeeplink() {
        // Multi-word text copied from clipboard (e.g. "open this link") is never a deeplink
        val text = "open this link please"
        val normalized = normalizeLink(text)
        assertFalse(
            "Text with spaces and no scheme/domain should not be a valid deeplink",
            isValidDeeplink(normalized),
        )
    }
}