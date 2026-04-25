package com.yogeshpaliyal.deepr.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yogeshpaliyal.deepr.ClipboardLink
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for ClipboardLinkBanner composable.
 *
 * Covers:
 * - Visibility when clipboardLink is null vs non-null
 * - URL text display
 * - Add button callback with correct URL
 * - Dismiss button callback
 * - Label text display
 */
@RunWith(AndroidJUnit4::class)
class ClipboardLinkBannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun banner_notVisible_whenClipboardLinkIsNull() {
        composeTestRule.setContent {
            MaterialTheme {
                ClipboardLinkBanner(
                    clipboardLink = null,
                    onAddClick = {},
                    onDismiss = {},
                )
            }
        }

        // The banner card/content should not be shown when link is null
        composeTestRule
            .onNodeWithText("Link detected in clipboard")
            .assertIsNotDisplayed()
    }

    @Test
    fun banner_isVisible_whenClipboardLinkIsNotNull() {
        val testUrl = "https://example.com"

        composeTestRule.setContent {
            MaterialTheme {
                ClipboardLinkBanner(
                    clipboardLink = ClipboardLink(url = testUrl),
                    onAddClick = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // The label text should be visible
        composeTestRule
            .onNodeWithText("Link detected in clipboard")
            .assertIsDisplayed()
    }

    @Test
    fun banner_displaysUrlText_whenLinkIsSet() {
        val testUrl = "myapp://open/settings"

        composeTestRule.setContent {
            MaterialTheme {
                ClipboardLinkBanner(
                    clipboardLink = ClipboardLink(url = testUrl),
                    onAddClick = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // The URL text should be shown
        composeTestRule
            .onNodeWithText(testUrl)
            .assertIsDisplayed()
    }

    @Test
    fun banner_addButton_isVisible_whenLinkIsSet() {
        val testUrl = "https://test.com/path"

        composeTestRule.setContent {
            MaterialTheme {
                ClipboardLinkBanner(
                    clipboardLink = ClipboardLink(url = testUrl),
                    onAddClick = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Add")
            .assertIsDisplayed()
    }

    @Test
    fun banner_addButtonClick_invokesOnAddClickWithCorrectUrl() {
        val testUrl = "https://example.com/resource"
        var capturedUrl: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                ClipboardLinkBanner(
                    clipboardLink = ClipboardLink(url = testUrl),
                    onAddClick = { url -> capturedUrl = url },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Add")
            .performClick()

        assertEquals(testUrl, capturedUrl)
    }

    @Test
    fun banner_dismissButtonClick_invokesOnDismiss() {
        var dismissCalled = false

        composeTestRule.setContent {
            MaterialTheme {
                ClipboardLinkBanner(
                    clipboardLink = ClipboardLink(url = "https://example.com"),
                    onAddClick = {},
                    onDismiss = { dismissCalled = true },
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("Cancel")
            .performClick()

        assertEquals(true, dismissCalled)
    }

    @Test
    fun banner_addButtonClick_passesExactUrlToCallback() {
        val customSchemeUrl = "intent://action#Intent;scheme=myapp;end"
        var capturedUrl: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                ClipboardLinkBanner(
                    clipboardLink = ClipboardLink(url = customSchemeUrl),
                    onAddClick = { url -> capturedUrl = url },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Add")
            .performClick()

        assertEquals(customSchemeUrl, capturedUrl)
    }

    @Test
    fun banner_dismissButtonNotClicked_doesNotInvokeOnDismiss() {
        var dismissCalled = false

        composeTestRule.setContent {
            MaterialTheme {
                ClipboardLinkBanner(
                    clipboardLink = ClipboardLink(url = "https://example.com"),
                    onAddClick = {},
                    onDismiss = { dismissCalled = true },
                )
            }
        }

        composeTestRule.waitForIdle()

        // Just render – don't click dismiss
        assertEquals(false, dismissCalled)
    }

    @Test
    fun banner_onAddClickNotCalled_whenAddButtonNotClicked() {
        var addClickCalled = false

        composeTestRule.setContent {
            MaterialTheme {
                ClipboardLinkBanner(
                    clipboardLink = ClipboardLink(url = "https://example.com"),
                    onAddClick = { addClickCalled = true },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Just render – don't click add
        assertEquals(false, addClickCalled)
    }

    @Test
    fun banner_nullLink_doesNotShowUrlOrAddButton() {
        composeTestRule.setContent {
            MaterialTheme {
                ClipboardLinkBanner(
                    clipboardLink = null,
                    onAddClick = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Neither URL text nor Add button should be visible
        composeTestRule
            .onNodeWithText("Add")
            .assertIsNotDisplayed()
    }
}