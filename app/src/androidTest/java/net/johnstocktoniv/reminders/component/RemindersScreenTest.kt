package net.johnstocktoniv.reminders.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import net.johnstocktoniv.reminders.testReminder
import net.johnstocktoniv.reminders.testReminderWithSchedules
import net.johnstocktoniv.reminders.ui.theme.RemindersTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RemindersScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setScreen(
        reminders: List<ReminderWithSchedules> = emptyList(),
        defaultReminderTime: LocalTime = LocalTime.of(8, 0),
        onSaveReminder: suspend (Reminder, List<String>) -> Unit = { _, _ -> },
        onSaveSettings: (LocalTime) -> Unit = {},
        onToggleComplete: (ReminderWithSchedules) -> Unit = {},
        onDeleteReminder: (ReminderWithSchedules) -> Unit = {},
        onClearAll: (List<ReminderWithSchedules>) -> Unit = {},
        onExportBackup: () -> Unit = {},
        onImportBackup: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            RemindersTheme {
                RemindersScreen(
                    reminders = reminders,
                    defaultReminderTime = defaultReminderTime,
                    onSaveReminder = onSaveReminder,
                    onSaveSettings = onSaveSettings,
                    onToggleComplete = onToggleComplete,
                    onDeleteReminder = onDeleteReminder,
                    onClearAll = onClearAll,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup
                )
            }
        }
    }

    @Test
    fun todayTabShowsOnlyRemindersDueToday() {
        val today = testReminder(id = 1, title = "Today Item", date = LocalDate.now())
        val tomorrow = testReminder(id = 2, title = "Tomorrow Item", date = LocalDate.now().plusDays(1))
        setScreen(reminders = listOf(today, tomorrow).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Today Item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tomorrow Item").assertDoesNotExist()
    }

    @Test
    fun todayTabSortsIncompleteAboveComplete() {
        // Deliberately pass the complete item first so a naive "list order" read would get this
        // wrong if the stable sort broke.
        val completeReminder = testReminder(id = 1, title = "Complete Item", date = LocalDate.now(), complete = true)
        val incompleteReminder = testReminder(id = 2, title = "Incomplete Item", date = LocalDate.now(), complete = false)
        setScreen(reminders = listOf(completeReminder, incompleteReminder).map { testReminderWithSchedules(it) })

        val incompleteTop = composeTestRule.onNodeWithTag("reminderItem:2").fetchSemanticsNode().boundsInRoot.top
        val completeTop = composeTestRule.onNodeWithTag("reminderItem:1").fetchSemanticsNode().boundsInRoot.top

        assert(incompleteTop < completeTop) { "expected the incomplete item above the complete item" }
    }

    @Test
    fun todayTabCollapsesCompletedRemindersIntoOffsetStackByDefault() {
        val incomplete = testReminder(id = 1, title = "Incomplete", date = LocalDate.now())
        val completeOne = testReminder(id = 2, title = "Complete One", date = LocalDate.now(), complete = true)
        val completeTwo = testReminder(id = 3, title = "Complete Two", date = LocalDate.now(), complete = true)
        setScreen(reminders = listOf(incomplete, completeOne, completeTwo).map { testReminderWithSchedules(it) })

        val firstCompleteNode = composeTestRule.onNodeWithTag("reminderItem:2").fetchSemanticsNode().boundsInRoot
        val secondCompleteTop = composeTestRule.onNodeWithTag("reminderItem:3").fetchSemanticsNode().boundsInRoot.top
        val gap = secondCompleteTop - firstCompleteNode.top

        assert(gap in 1f..(firstCompleteNode.height * 0.5f)) {
            "expected a small peek offset between stacked completed items, was $gap (item height ${firstCompleteNode.height})"
        }
    }

    @Test
    fun tappingCompletedReminderExpandsThenCollapsesTheStack() {
        val completeOne = testReminder(id = 2, title = "Complete One", date = LocalDate.now(), complete = true)
        val completeTwo = testReminder(id = 3, title = "Complete Two", date = LocalDate.now(), complete = true)
        setScreen(reminders = listOf(completeOne, completeTwo).map { testReminderWithSchedules(it) })

        val firstCompleteNode = composeTestRule.onNodeWithTag("reminderItem:2").fetchSemanticsNode().boundsInRoot
        val collapsedSecondTop = composeTestRule.onNodeWithTag("reminderItem:3").fetchSemanticsNode().boundsInRoot.top
        val collapsedGap = collapsedSecondTop - firstCompleteNode.top

        composeTestRule.onNodeWithTag("reminderItem:2").performTouchInput { click() }

        val expandedSecondTop = composeTestRule.onNodeWithTag("reminderItem:3").fetchSemanticsNode().boundsInRoot.top
        val expandedGap = expandedSecondTop - firstCompleteNode.top
        assert(expandedGap > firstCompleteNode.height * 0.8f) {
            "expected the stack to expand to roughly full item height apart, was $expandedGap (item height ${firstCompleteNode.height})"
        }
        assert(expandedGap > collapsedGap) { "expected expanded gap ($expandedGap) to exceed collapsed gap ($collapsedGap)" }

        composeTestRule.onNodeWithTag("reminderItem:2").performTouchInput { click() }

        val recollapsedSecondTop = composeTestRule.onNodeWithTag("reminderItem:3").fetchSemanticsNode().boundsInRoot.top
        val recollapsedGap = recollapsedSecondTop - firstCompleteNode.top
        assert(recollapsedGap < expandedGap) { "expected re-collapsing to shrink the gap back down, was $recollapsedGap" }
    }

    @Test
    fun completeTabDoesNotStackCompletedReminders() {
        val completeOne = testReminder(id = 2, title = "Complete One", date = LocalDate.now(), complete = true)
        val completeTwo = testReminder(id = 3, title = "Complete Two", date = LocalDate.now(), complete = true)
        setScreen(reminders = listOf(completeOne, completeTwo).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Complete").performClick()

        val firstCompleteNode = composeTestRule.onNodeWithTag("reminderItem:2").fetchSemanticsNode().boundsInRoot
        val secondCompleteTop = composeTestRule.onNodeWithTag("reminderItem:3").fetchSemanticsNode().boundsInRoot.top
        val gap = secondCompleteTop - firstCompleteNode.top

        assert(gap > firstCompleteNode.height * 0.8f) {
            "expected full-height spacing outside the Today tab, was $gap (item height ${firstCompleteNode.height})"
        }
    }

    @Test
    fun collapsedStackHidesDescriptionUntilExpanded() {
        val completeOne = testReminder(
            id = 2,
            title = "Complete One",
            description = "Some description text",
            date = LocalDate.now(),
            complete = true
        )
        val completeTwo = testReminder(id = 3, title = "Complete Two", date = LocalDate.now(), complete = true)
        setScreen(reminders = listOf(completeOne, completeTwo).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Some description text").assertDoesNotExist()

        composeTestRule.onNodeWithTag("reminderItem:2").performTouchInput { click() }

        composeTestRule.onNodeWithText("Some description text").assertIsDisplayed()
    }

    @Test
    fun incompleteTabShowsAllIncompleteRegardlessOfDate() {
        val incompleteToday = testReminder(id = 1, title = "Incomplete Today", date = LocalDate.now())
        val incompleteFuture = testReminder(id = 2, title = "Incomplete Future", date = LocalDate.now().plusDays(5))
        val completeToday = testReminder(id = 3, title = "Complete Today", date = LocalDate.now(), complete = true)
        setScreen(reminders = listOf(incompleteToday, incompleteFuture, completeToday).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Incomplete").performClick()

        composeTestRule.onNodeWithText("Incomplete Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("Incomplete Future").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete Today").assertDoesNotExist()
    }

    @Test
    fun completeTabShowsAllCompleteRegardlessOfDate() {
        val completeToday = testReminder(id = 1, title = "Complete Today", date = LocalDate.now(), complete = true)
        val completeFuture = testReminder(id = 2, title = "Complete Future", date = LocalDate.now().plusDays(5), complete = true)
        val incompleteToday = testReminder(id = 3, title = "Incomplete Today", date = LocalDate.now())
        setScreen(reminders = listOf(completeToday, completeFuture, incompleteToday).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Complete").performClick()

        composeTestRule.onNodeWithText("Complete Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete Future").assertIsDisplayed()
        composeTestRule.onNodeWithText("Incomplete Today").assertDoesNotExist()
    }

    @Test
    fun clearAllFabHiddenWhenCompleteTabEmpty() {
        setScreen(reminders = emptyList())

        composeTestRule.onNodeWithText("Complete").performClick()

        composeTestRule.onNodeWithContentDescription("Clear All").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Add Item").assertDoesNotExist()
    }

    @Test
    fun clearAllFabShownAndTappingItShowsConfirmationDialogWithCount() {
        val completeOne = testReminder(id = 1, title = "Complete One", complete = true)
        val completeTwo = testReminder(id = 2, title = "Complete Two", complete = true)
        setScreen(reminders = listOf(completeOne, completeTwo).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Complete").performClick()
        composeTestRule.onNodeWithContentDescription("Clear All").performClick()

        composeTestRule.onNodeWithText(
            "This will permanently delete 2 completed reminder(s). This can't be undone."
        ).assertIsDisplayed()
    }

    @Test
    fun addItemFabReplacedByClearAllOnCompleteTab() {
        val completeOne = testReminder(id = 1, title = "Complete One", complete = true)
        setScreen(reminders = listOf(completeOne).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Complete").performClick()

        composeTestRule.onNodeWithContentDescription("Add Item").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Clear All").assertIsDisplayed()
    }

    @Test
    fun confirmingClearAllInvokesOnClearAllWithVisibleCompletedRemindersAndClosesDialog() {
        var cleared: List<ReminderWithSchedules>? = null
        val completeOne = testReminderWithSchedules(testReminder(id = 1, title = "Complete One", complete = true))
        val completeTwo = testReminderWithSchedules(testReminder(id = 2, title = "Complete Two", complete = true))
        setScreen(reminders = listOf(completeOne, completeTwo), onClearAll = { cleared = it })

        composeTestRule.onNodeWithText("Complete").performClick()
        composeTestRule.onNodeWithContentDescription("Clear All").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()

        assert(cleared?.map { it.reminder.id }?.toSet() == setOf(1L, 2L)) { "was $cleared" }
        composeTestRule.onNodeWithText("Delete completed reminders?").assertDoesNotExist()
    }

    @Test
    fun cancelingClearAllConfirmationClosesDialogWithoutInvokingCallback() {
        var clearAllCalled = false
        val completeOne = testReminderWithSchedules(testReminder(id = 1, title = "Complete One", complete = true))
        setScreen(reminders = listOf(completeOne), onClearAll = { clearAllCalled = true })

        composeTestRule.onNodeWithText("Complete").performClick()
        composeTestRule.onNodeWithContentDescription("Clear All").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assert(!clearAllCalled) { "expected onClearAll not to be invoked" }
        composeTestRule.onNodeWithText("Delete completed reminders?").assertDoesNotExist()
    }

    @Test
    fun tappingFabOpensAddReminderDialog() {
        setScreen(reminders = emptyList())

        composeTestRule.onNodeWithContentDescription("Add Item").performClick()

        composeTestRule.onNodeWithText("Add Reminder").assertIsDisplayed()
    }

    @Test
    fun savingNewReminderFromFabInvokesOnSaveReminderAndClosesDialog() {
        var savedReminder: Reminder? = null
        setScreen(reminders = emptyList(), onSaveReminder = { reminder, _ -> savedReminder = reminder })

        composeTestRule.onNodeWithContentDescription("Add Item").performClick()
        composeTestRule.onNodeWithTag("titleField").performTextInput("New Task")
        composeTestRule.onNodeWithText("Save").performClick()

        assert(savedReminder?.title == "New Task") { "was ${savedReminder?.title}" }
        composeTestRule.onNodeWithText("Add Reminder").assertDoesNotExist()
    }

    // Regression test for a bug where reopening a reminder right after adding a CRON schedule to
    // it showed stale (pre-save) data: the dialog closed as soon as onSaveReminder was invoked,
    // without waiting for the (asynchronous) DB write it kicks off to actually complete, so a fast
    // reopen could race it and read the reminder list before the new schedule landed.
    @Test
    fun reopeningEditDialogAfterAddingCronReflectsTheNewSchedule() {
        var reminders by mutableStateOf(listOf(testReminderWithSchedules(testReminder(id = 1, title = "Water plants"))))

        composeTestRule.setContent {
            RemindersTheme {
                RemindersScreen(
                    reminders = reminders,
                    defaultReminderTime = LocalTime.of(8, 0),
                    onSaveReminder = { reminder, cronExpressions ->
                        // Stands in for the real save: DAO write + Flow re-emission.
                        reminders = listOf(testReminderWithSchedules(reminder, cronExpressions = cronExpressions))
                    },
                    onSaveSettings = {},
                    onToggleComplete = {},
                    onDeleteReminder = {},
                    onClearAll = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("reminderItem:1").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Recurring (CRON)").performClick()
        composeTestRule.onNodeWithText("Add schedule").performClick()
        composeTestRule.onNodeWithTag("cronField:0").performTextInput("0 9 * * *")
        composeTestRule.onNodeWithText("Save").performClick()

        // Saving a recurring reminder recomputes its date to the CRON's next occurrence, which
        // may no longer be today (e.g. if it's already past 9am) — switch to the Incomplete tab,
        // which (per incompleteTabShowsAllIncompleteRegardlessOfDate below) shows it regardless.
        composeTestRule.onNodeWithText("Incomplete").performClick()
        composeTestRule.onNodeWithTag("reminderItem:1").performTouchInput { longClick() }

        composeTestRule.onNodeWithTag("cronField:0").assertTextContains("0 9 * * *")
    }

    @Test
    fun longPressingReminderOpensEditDialogPrefilled() {
        val reminder = testReminder(id = 5, title = "Existing Task", date = LocalDate.now())
        setScreen(reminders = listOf(testReminderWithSchedules(reminder)))

        composeTestRule.onNodeWithTag("reminderItem:5").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Edit Reminder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("titleField").assertTextContains("Existing Task")
    }

    @Test
    fun swipingEndToStartInvokesOnToggleCompleteForThatReminder() {
        var toggled: ReminderWithSchedules? = null
        val reminder = testReminderWithSchedules(testReminder(id = 7, title = "Swipe Me", date = LocalDate.now()))
        setScreen(reminders = listOf(reminder), onToggleComplete = { toggled = it })

        composeTestRule.onNodeWithTag("reminderItem:7").performTouchInput {
            swipeLeft(startX = width * 0.9f, endX = width * 0.05f)
        }
        composeTestRule.waitForIdle()

        assert(toggled?.reminder?.id == 7L) { "was $toggled" }
    }

    @Test
    fun swipingStartToEndInvokesOnDeleteReminderForThatReminder() {
        var deleted: ReminderWithSchedules? = null
        val reminder = testReminderWithSchedules(testReminder(id = 9, title = "Swipe Me Too", date = LocalDate.now()))
        setScreen(reminders = listOf(reminder), onDeleteReminder = { deleted = it })

        composeTestRule.onNodeWithTag("reminderItem:9").performTouchInput {
            swipeRight(startX = width * 0.05f, endX = width * 0.9f)
        }
        composeTestRule.waitForIdle()

        assert(deleted?.reminder?.id == 9L) { "was $deleted" }
    }

    @Test
    fun tappingSettingsIconOpensSettingsDialogWithDefaultTimePrefilled() {
        setScreen(reminders = emptyList(), defaultReminderTime = LocalTime.of(7, 45))

        composeTestRule.onNodeWithContentDescription("Settings").performClick()

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithTag("defaultTimeField").assertTextContains("07:45")
    }

    @Test
    fun savingSettingsInvokesOnSaveSettingsAndClosesDialog() {
        var savedTime: LocalTime? = null
        setScreen(reminders = emptyList(), defaultReminderTime = LocalTime.of(8, 0), onSaveSettings = { savedTime = it })

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithTag("defaultTimeField").performTextClearance()
        composeTestRule.onNodeWithTag("defaultTimeField").performTextInput("10:00")
        composeTestRule.onNodeWithText("Save").performClick()

        assert(savedTime == LocalTime.of(10, 0)) { "was $savedTime" }
        composeTestRule.onNodeWithTag("defaultTimeField").assertDoesNotExist()
    }

    @Test
    fun tappingBackupIconOpensBackupDialog() {
        setScreen(reminders = emptyList())

        composeTestRule.onNodeWithContentDescription("Backup").performClick()

        composeTestRule.onNodeWithText("Backup").assertIsDisplayed()
    }

    @Test
    fun tappingExportInBackupDialogInvokesOnExportBackupAndClosesDialog() {
        var exportCalled = false
        setScreen(reminders = emptyList(), onExportBackup = { exportCalled = true })

        composeTestRule.onNodeWithContentDescription("Backup").performClick()
        composeTestRule.onNodeWithTag("exportButton").performClick()

        assert(exportCalled) { "expected onExportBackup to be invoked" }
        composeTestRule.onNodeWithTag("exportButton").assertDoesNotExist()
    }

    @Test
    fun confirmingRestoreInBackupDialogInvokesOnImportBackupAndClosesDialog() {
        var importCalled = false
        setScreen(reminders = emptyList(), onImportBackup = { importCalled = true })

        composeTestRule.onNodeWithContentDescription("Backup").performClick()
        composeTestRule.onNodeWithTag("importButton").performClick()
        composeTestRule.onNodeWithTag("confirmRestoreButton").performClick()

        assert(importCalled) { "expected onImportBackup to be invoked" }
        composeTestRule.onNodeWithText("Are you sure?").assertDoesNotExist()
    }

    @Test
    fun screenRendersWithEmptyReminderListWithoutCrashing() {
        setScreen(reminders = emptyList())

        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
    }
}
