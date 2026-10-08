package com.faisal.freshdownloader

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchShowsPrivacyGate() {
        val title = composeRule.onNodeWithText("Privacy Policy & User Consent").fetchSemanticsNode()
        val agree = composeRule.onNodeWithText("Agree & Continue").fetchSemanticsNode()

        assertEquals("Privacy Policy & User Consent", title.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.firstOrNull()?.text)
        assertEquals("Agree & Continue", agree.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.firstOrNull()?.text)
    }
}
