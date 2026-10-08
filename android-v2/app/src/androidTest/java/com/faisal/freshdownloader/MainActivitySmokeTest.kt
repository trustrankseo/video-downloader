package com.faisal.freshdownloader

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchShowsPrivacyGate() {
        // fetchSemanticsNode() throws when the node is absent, so these are
        // stable existence assertions across Compose UI-test versions.
        composeRule.onNodeWithText("Privacy Policy & User Consent").fetchSemanticsNode()
        composeRule.onNodeWithText("Agree & Continue").fetchSemanticsNode()
    }
}
