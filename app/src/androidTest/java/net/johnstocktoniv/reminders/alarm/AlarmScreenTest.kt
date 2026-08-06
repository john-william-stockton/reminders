package net.johnstocktoniv.reminders.alarm

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.johnstocktoniv.reminders.ui.theme.RemindersTheme
import org.junit.Rule
import org.junit.Test

class AlarmScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setScreen(
        title: String = "Take medicine",
        description: String = "",
        onComplete: () -> Unit = {},
        onSnooze: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            RemindersTheme {
                AlarmScreen(title = title, description = description, onComplete = onComplete, onSnooze = onSnooze)
            }
        }
    }

    @Test
    fun titleIsDisplayed() {
        setScreen(title = "Take medicine")

        composeTestRule.onNodeWithText("Take medicine").assertIsDisplayed()
    }

    @Test
    fun descriptionDisplayedWhenNonBlank() {
        setScreen(description = "With food")

        composeTestRule.onNodeWithTag("alarmDescription").assertIsDisplayed()
    }

    @Test
    fun descriptionNotRenderedWhenBlank() {
        setScreen(description = "")

        composeTestRule.onNodeWithTag("alarmDescription").assertDoesNotExist()
    }

    @Test
    fun tappingMarkCompleteInvokesOnCompleteOnly() {
        var completeCalled = false
        var snoozeCalled = false
        setScreen(onComplete = { completeCalled = true }, onSnooze = { snoozeCalled = true })

        composeTestRule.onNodeWithText("Mark Complete").performClick()

        assert(completeCalled) { "expected onComplete to be invoked" }
        assert(!snoozeCalled) { "expected onSnooze not to be invoked" }
    }

    @Test
    fun tappingSnoozeInvokesOnSnoozeOnly() {
        var completeCalled = false
        var snoozeCalled = false
        setScreen(onComplete = { completeCalled = true }, onSnooze = { snoozeCalled = true })

        composeTestRule.onNodeWithText("Snooze 2 minutes").performClick()

        assert(snoozeCalled) { "expected onSnooze to be invoked" }
        assert(!completeCalled) { "expected onComplete not to be invoked" }
    }

    @Test
    fun exactlyTwoClickableActionsExist() {
        setScreen()

        // Regression guard for the "no plain dismiss" alarm-screen design: Mark Complete and
        // Snooze should be the only two clickable actions. (The system back button is a separate
        // concern, covered by AlarmActivityTest since it's intercepted at the Activity level.)
        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun veryLongTitleRendersWithoutCrashing() {
        // Smoke test only: Compose UI test's semantics tree confirms the full string is present,
        // not that TextAutoSize/ellipsis actually kept it on one visible line — that would need a
        // screenshot-diff tool this project doesn't have.
        val longTitle = "A".repeat(200)
        setScreen(title = longTitle)

        composeTestRule.onNodeWithText(longTitle).assertIsDisplayed()
    }
}
