package net.johnstocktoniv.reminders.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import net.johnstocktoniv.reminders.ui.theme.RemindersTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class SettingsDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setDialog(
        isOpen: Boolean = true,
        defaultReminderTime: LocalTime = LocalTime.of(8, 0),
        onCancel: () -> Unit = {},
        onSave: (LocalTime) -> Unit = {},
    ) {
        composeTestRule.setContent {
            RemindersTheme {
                SettingsDialog(
                    isOpen = isOpen,
                    defaultReminderTime = defaultReminderTime,
                    onCancel = onCancel,
                    onSave = onSave
                )
            }
        }
    }

    @Test
    fun dialogRendersNothingWhenClosed() {
        setDialog(isOpen = false)

        composeTestRule.onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    fun dialogPrefillsTimeFieldFromDefaultReminderTime() {
        setDialog(defaultReminderTime = LocalTime.of(14, 30))

        composeTestRule.onNodeWithTag("defaultTimeField").assertTextContains("14:30")
    }

    @Test
    fun savingValidTimeInvokesOnSaveWithParsedTime() {
        var savedTime: LocalTime? = null
        setDialog(onSave = { savedTime = it })

        composeTestRule.onNodeWithTag("defaultTimeField").performTextClearance()
        composeTestRule.onNodeWithTag("defaultTimeField").performTextInput("09:15")
        composeTestRule.onNodeWithText("Save").performClick()

        assert(savedTime == LocalTime.of(9, 15)) { "was $savedTime" }
    }

    @Test
    fun savingWithoutEditingInvokesOnSaveWithOriginalDefaultTime() {
        var savedTime: LocalTime? = null
        val original = LocalTime.of(8, 0)
        setDialog(defaultReminderTime = original, onSave = { savedTime = it })

        composeTestRule.onNodeWithText("Save").performClick()

        assert(savedTime == original) { "was $savedTime" }
    }

    @Test
    fun invalidTimeShowsErrorOnlyAfterSaveTapped() {
        var saveCalled = false
        setDialog(onSave = { saveCalled = true })

        composeTestRule.onNodeWithTag("defaultTimeField").performTextClearance()
        composeTestRule.onNodeWithTag("defaultTimeField").performTextInput("not-a-time")

        composeTestRule.onNodeWithText("Use the format shown when the dialog opens").assertDoesNotExist()

        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.onNodeWithText("Use the format shown when the dialog opens").assertIsDisplayed()
        assert(!saveCalled) { "expected onSave not to be invoked" }
    }

    @Test
    fun blankTimeShowsErrorOnSaveAttempt() {
        var saveCalled = false
        setDialog(onSave = { saveCalled = true })

        composeTestRule.onNodeWithTag("defaultTimeField").performTextClearance()
        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.onNodeWithText("Use the format shown when the dialog opens").assertIsDisplayed()
        assert(!saveCalled) { "expected onSave not to be invoked" }
    }

    @Test
    fun cancelInvokesOnCancelOnlyAndNeverOnSave() {
        var cancelCalled = false
        var saveCalled = false
        setDialog(onCancel = { cancelCalled = true }, onSave = { saveCalled = true })

        composeTestRule.onNodeWithText("Cancel").performClick()

        assert(cancelCalled) { "expected onCancel to be invoked" }
        assert(!saveCalled) { "expected onSave not to be invoked" }
    }
}
