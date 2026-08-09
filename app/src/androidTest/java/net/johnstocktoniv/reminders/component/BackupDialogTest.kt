package net.johnstocktoniv.reminders.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        scheduledBackupEnabled: Boolean = false,
        scheduledBackupCron: String = "0 */8 * * *",
        scheduledBackupDestinationSet: Boolean = false,
        onToggleScheduledBackup: (Boolean) -> Unit = {},
        onScheduledBackupCronChange: (String) -> Unit = {},
        onChooseScheduledBackupFolder: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            RemindersTheme {
                BackupDialog(
                    isOpen = isOpen,
                    onCancel = onCancel,
                    onExport = onExport,
                    onImport = onImport,
                    scheduledBackupEnabled = scheduledBackupEnabled,
                    scheduledBackupCron = scheduledBackupCron,
                    scheduledBackupDestinationSet = scheduledBackupDestinationSet,
                    onToggleScheduledBackup = onToggleScheduledBackup,
                    onScheduledBackupCronChange = onScheduledBackupCronChange,
                    onChooseScheduledBackupFolder = onChooseScheduledBackupFolder
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

    @Test
    fun scheduledBackupSwitchReflectsEnabledState() {
        setDialog(scheduledBackupEnabled = true)

        composeTestRule.onNodeWithTag("scheduledBackupSwitch").assertIsOn()
    }

    @Test
    fun togglingSwitchOnWithNoDestinationOpensFolderPickerInsteadOfEnabling() {
        var toggleCalled = false
        var chooseFolderCalled = false
        setDialog(
            scheduledBackupDestinationSet = false,
            onToggleScheduledBackup = { toggleCalled = true },
            onChooseScheduledBackupFolder = { chooseFolderCalled = true }
        )

        composeTestRule.onNodeWithTag("scheduledBackupSwitch").performClick()

        assert(chooseFolderCalled) { "expected onChooseScheduledBackupFolder to be invoked" }
        assert(!toggleCalled) { "expected onToggleScheduledBackup not to be invoked before a folder is chosen" }
        composeTestRule.onNodeWithTag("scheduledBackupSwitch").assertIsOff()
    }

    @Test
    fun togglingSwitchWithDestinationAlreadySetInvokesToggleDirectly() {
        var toggledTo: Boolean? = null
        var chooseFolderCalled = false
        setDialog(
            scheduledBackupDestinationSet = true,
            onToggleScheduledBackup = { toggledTo = it },
            onChooseScheduledBackupFolder = { chooseFolderCalled = true }
        )

        composeTestRule.onNodeWithTag("scheduledBackupSwitch").performClick()

        assert(toggledTo == true) { "expected onToggleScheduledBackup(true) to be invoked" }
        assert(!chooseFolderCalled) { "expected onChooseScheduledBackupFolder not to be invoked" }
    }

    @Test
    fun togglingEnabledSwitchOffInvokesToggleWithFalse() {
        var toggledTo: Boolean? = null
        setDialog(
            scheduledBackupEnabled = true,
            scheduledBackupDestinationSet = true,
            onToggleScheduledBackup = { toggledTo = it }
        )

        composeTestRule.onNodeWithTag("scheduledBackupSwitch").performClick()

        assert(toggledTo == false) { "expected onToggleScheduledBackup(false) to be invoked" }
    }

    @Test
    fun validCronEditInvokesOnScheduledBackupCronChange() {
        var latestCron: String? = null
        setDialog(scheduledBackupCron = "", onScheduledBackupCronChange = { latestCron = it })

        composeTestRule.onNodeWithTag("scheduledBackupCronField").performTextInput("0 */8 * * *")

        assert(latestCron == "0 */8 * * *") { "expected the valid CRON expression to be committed, got $latestCron" }
    }

    @Test
    fun invalidCronEditShowsErrorAndDoesNotInvokeOnScheduledBackupCronChange() {
        var changeCalled = false
        setDialog(scheduledBackupCron = "", onScheduledBackupCronChange = { changeCalled = true })

        composeTestRule.onNodeWithTag("scheduledBackupCronField").performTextInput("not a cron")

        composeTestRule.onNodeWithText("Not a valid CRON expression").assertIsDisplayed()
        assert(!changeCalled) { "expected onScheduledBackupCronChange not to be invoked for an invalid expression" }
    }

    @Test
    fun chooseFolderButtonLabelReflectsWhetherADestinationIsAlreadySet() {
        setDialog(scheduledBackupDestinationSet = false)
        composeTestRule.onNodeWithText("Choose Backup Folder…").assertIsDisplayed()
    }

    @Test
    fun changeFolderButtonShownWhenDestinationAlreadySet() {
        setDialog(scheduledBackupDestinationSet = true)
        composeTestRule.onNodeWithText("Change Backup Folder…").assertIsDisplayed()
    }

    @Test
    fun tappingChooseFolderButtonInvokesCallback() {
        var chooseFolderCalled = false
        setDialog(onChooseScheduledBackupFolder = { chooseFolderCalled = true })

        composeTestRule.onNodeWithTag("chooseScheduledBackupFolderButton").performClick()

        assert(chooseFolderCalled) { "expected onChooseScheduledBackupFolder to be invoked" }
    }
}
