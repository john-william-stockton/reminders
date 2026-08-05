package net.johnstocktoniv.reminders.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.johnstocktoniv.reminders.ui.theme.RemindersTheme
import org.junit.Rule
import org.junit.Test

class BackupDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setDialog(
        isOpen: Boolean = true,
        onCancel: () -> Unit = {},
        onExport: () -> Unit = {},
        onImport: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            RemindersTheme {
                BackupDialog(
                    isOpen = isOpen,
                    onCancel = onCancel,
                    onExport = onExport,
                    onImport = onImport
                )
            }
        }
    }

    @Test
    fun dialogRendersNothingWhenClosed() {
        setDialog(isOpen = false)

        composeTestRule.onNodeWithText("Backup").assertDoesNotExist()
    }

    @Test
    fun tappingExportInvokesOnExportAndClosesNothingElse() {
        var exportCalled = false
        setDialog(onExport = { exportCalled = true })

        composeTestRule.onNodeWithTag("exportButton").performClick()

        assert(exportCalled) { "expected onExport to be invoked" }
    }

    @Test
    fun tappingCancelInvokesOnCancelOnlyAndNeverOnExportOrOnImport() {
        var cancelCalled = false
        var exportCalled = false
        var importCalled = false
        setDialog(onCancel = { cancelCalled = true }, onExport = { exportCalled = true }, onImport = { importCalled = true })

        composeTestRule.onNodeWithText("Cancel").performClick()

        assert(cancelCalled) { "expected onCancel to be invoked" }
        assert(!exportCalled) { "expected onExport not to be invoked" }
        assert(!importCalled) { "expected onImport not to be invoked" }
    }

    @Test
    fun tappingRestoreShowsAreYouSureConfirmationBeforeInvokingOnImport() {
        var importCalled = false
        setDialog(onImport = { importCalled = true })

        composeTestRule.onNodeWithTag("importButton").performClick()

        composeTestRule.onNodeWithText("Are you sure?").assertIsDisplayed()
        assert(!importCalled) { "expected onImport not to be invoked before confirming" }
    }

    @Test
    fun confirmingRestoreInvokesOnImport() {
        var importCalled = false
        setDialog(onImport = { importCalled = true })

        composeTestRule.onNodeWithTag("importButton").performClick()
        composeTestRule.onNodeWithTag("confirmRestoreButton").performClick()

        assert(importCalled) { "expected onImport to be invoked after confirming" }
    }

    @Test
    fun cancelingRestoreConfirmationDoesNotInvokeOnImport() {
        var importCalled = false
        setDialog(onImport = { importCalled = true })

        composeTestRule.onNodeWithTag("importButton").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("Are you sure?").assertDoesNotExist()
        assert(!importCalled) { "expected onImport not to be invoked" }
    }
}
