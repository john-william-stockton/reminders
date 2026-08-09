package net.johnstocktoniv.reminders.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import net.johnstocktoniv.reminders.STABLE_FUTURE_CRON
import net.johnstocktoniv.reminders.alarm.nextOccurrence
import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderSchedule
import net.johnstocktoniv.reminders.database.ReminderStatus
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import net.johnstocktoniv.reminders.database.dateFormatter
import net.johnstocktoniv.reminders.database.timeFormatter
import net.johnstocktoniv.reminders.testReminder
import net.johnstocktoniv.reminders.testReminderWithSchedules
import net.johnstocktoniv.reminders.ui.theme.RemindersTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setDialog(
        isOpen: Boolean = true,
        reminderWithSchedules: ReminderWithSchedules? = null,
        onCancel: () -> Unit = {},
        onSave: (Reminder, List<String>) -> Unit = { _, _ -> },
        onEditOccurrence: (ReminderWithSchedules, Reminder) -> Unit = { _, _ -> },
        onDelete: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            RemindersTheme {
                ReminderDialog(
                    isOpen = isOpen,
                    onCancel = onCancel,
                    onSave = onSave,
                    reminderWithSchedules = reminderWithSchedules,
                    onEditOccurrence = onEditOccurrence,
                    onDelete = onDelete
                )
            }
        }
    }

    @Test
    fun dialogRendersNothingWhenClosed() {
        setDialog(isOpen = false)

        composeTestRule.onNodeWithText("Add Reminder").assertDoesNotExist()
    }

    @Test
    fun addModeShowsAddReminderTitleAndOneBlankScheduleRow() {
        setDialog()

        composeTestRule.onNodeWithText("Add Reminder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cronField:0").assertIsDisplayed()
    }

    @Test
    fun editModeShowsEditReminderTitleAndPrefillsFields() {
        val reminder = testReminder(title = "Water plants", description = "Ferns need extra")
        setDialog(
            reminderWithSchedules = testReminderWithSchedules(reminder = reminder, cronExpressions = listOf("0 9 * * 1-5"))
        )

        composeTestRule.onNodeWithText("Edit Reminder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("titleField").assertTextContains("Water plants")
        composeTestRule.onNodeWithTag("descriptionField").assertTextContains("Ferns need extra")
        composeTestRule.onNodeWithTag("cronField:0").assertTextContains("0 9 * * 1-5")
    }

    @Test
    fun blankTitleShowsErrorOnlyAfterSaveTapped() {
        setDialog()

        composeTestRule.onNodeWithText("Title is required").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.onNodeWithText("Title is required").assertIsDisplayed()
    }

    @Test
    fun savingWithBlankTitleDoesNotInvokeOnSave() {
        var saveCalled = false
        setDialog(onSave = { _, _ -> saveCalled = true })

        composeTestRule.onNodeWithTag("cronField:0").performTextInput(STABLE_FUTURE_CRON)
        composeTestRule.onNodeWithText("Save").performClick()

        assert(!saveCalled) { "expected onSave not to be invoked" }
    }

    @Test
    fun validSaveInvokesOnSaveWithTrimmedTitleAndDescriptionAndComputedDateTime() {
        var savedReminder: Reminder? = null
        var savedSchedules: List<String>? = null
        setDialog(onSave = { reminder, schedules -> savedReminder = reminder; savedSchedules = schedules })

        composeTestRule.onNodeWithTag("titleField").performTextInput(" Buy milk ")
        composeTestRule.onNodeWithTag("descriptionField").performTextInput(" 2% ")
        composeTestRule.onNodeWithTag("cronField:0").performTextInput(STABLE_FUTURE_CRON)

        val expected = nextOccurrence(
            listOf(ReminderSchedule(reminderId = 0, cronExpression = STABLE_FUTURE_CRON)),
            LocalDateTime.now()
        )!!

        composeTestRule.onNodeWithText("Save").performClick()

        assert(savedReminder?.title == "Buy milk") { "was ${savedReminder?.title}" }
        assert(savedReminder?.description == "2%") { "was ${savedReminder?.description}" }
        assert(savedReminder?.date == expected.toLocalDate())
        assert(savedReminder?.time == expected.toLocalTime())
        assert(savedSchedules == listOf(STABLE_FUTURE_CRON))
    }

    @Test
    fun addScheduleAppendsBlankCronRow() {
        setDialog()

        composeTestRule.onNodeWithText("Add schedule").performClick()

        // One blank row is already present by default, plus the one just added.
        composeTestRule.onAllNodesWithContentDescription("Remove schedule").assertCountEquals(2)
    }

    @Test
    fun removeScheduleDeletesRow() {
        setDialog()
        composeTestRule.onNodeWithTag("cronField:0").performTextInput("0 9 * * *")
        composeTestRule.onNodeWithText("Add schedule").performClick()
        composeTestRule.onNodeWithTag("cronField:1").performTextInput("0 18 * * *")

        composeTestRule.onNodeWithTag("removeCronButton:0").performClick()

        composeTestRule.onAllNodesWithContentDescription("Remove schedule").assertCountEquals(1)
        composeTestRule.onNodeWithTag("cronField:0").assertTextContains("0 18 * * *")
    }

    @Test
    fun invalidCronShowsPerRowError() {
        setDialog()
        composeTestRule.onNodeWithTag("titleField").performTextInput("Something")
        composeTestRule.onNodeWithTag("cronField:0").performTextInput("not a cron")

        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.onNodeWithText("Not a valid CRON expression").assertIsDisplayed()
    }

    @Test
    fun blankSchedulesOnSaveShowsAddAtLeastOneCronError() {
        setDialog()
        composeTestRule.onNodeWithTag("titleField").performTextInput("Something")

        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.onNodeWithText("Add at least one CRON schedule").assertIsDisplayed()
    }

    @Test
    fun emptyScheduleListPreviewShowsEmDash() {
        setDialog()

        composeTestRule.onNodeWithText("Next occurrence: —").assertIsDisplayed()
    }

    @Test
    fun validCronShowsNextOccurrencePreview() {
        setDialog()
        composeTestRule.onNodeWithTag("cronField:0").performTextInput(STABLE_FUTURE_CRON)

        val expected = nextOccurrence(
            listOf(ReminderSchedule(reminderId = 0, cronExpression = STABLE_FUTURE_CRON)),
            LocalDateTime.now()
        )!!
        val expectedText =
            "Next occurrence: ${expected.toLocalDate().format(dateFormatter)} at ${expected.toLocalTime().format(timeFormatter)}"

        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Test
    fun pinnedYearCronThatAlreadyPassedCannotBeSaved() {
        var saveCalled = false
        setDialog(onSave = { _, _ -> saveCalled = true })
        composeTestRule.onNodeWithTag("titleField").performTextInput("Something")
        composeTestRule.onNodeWithTag("cronField:0").performTextInput("0 0 1 1 * 1970")

        // A syntactically valid CRON with no remaining future match computes no next occurrence
        // (nextOccurrence always searches forward from now), which blocks the save outright rather
        // than creating a reminder in the past.
        composeTestRule.onNodeWithText("Next occurrence: —").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").performClick()

        assert(!saveCalled) { "expected onSave not to be invoked for a schedule with no future occurrence" }
    }

    @Test
    fun editModePrefillsRecurringScheduleRows() {
        val reminder = testReminder(title = "Standup", date = LocalDate.now(), time = LocalTime.of(9, 0))
        setDialog(
            reminderWithSchedules = testReminderWithSchedules(
                reminder = reminder,
                cronExpressions = listOf("0 9 * * 1-5", "0 9 * * 6")
            )
        )

        composeTestRule.onNodeWithTag("cronField:0").assertTextContains("0 9 * * 1-5")
        composeTestRule.onNodeWithTag("cronField:1").assertTextContains("0 9 * * 6")
    }

    @Test
    fun cancelInvokesOnCancelOnlyAndNeverOnSave() {
        var cancelCalled = false
        var saveCalled = false
        setDialog(onCancel = { cancelCalled = true }, onSave = { _, _ -> saveCalled = true })

        composeTestRule.onNodeWithText("Cancel").performClick()

        assert(cancelCalled) { "expected onCancel to be invoked" }
        assert(!saveCalled) { "expected onSave not to be invoked" }
    }

    @Test
    fun editingRecurringReminderShowsSeriesChoiceInsteadOfSavingDirectly() {
        var saveCalled = false
        val reminder = testReminder(title = "Standup", date = LocalDate.now(), time = LocalTime.of(9, 0))
        setDialog(
            reminderWithSchedules = testReminderWithSchedules(reminder = reminder, cronExpressions = listOf("0 9 * * *")),
            onSave = { _, _ -> saveCalled = true }
        )

        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.onNodeWithText("Apply changes to?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wholeSeriesButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("thisOccurrenceOnlyButton").assertIsDisplayed()
        assert(!saveCalled) { "expected onSave not to be invoked before a choice is made" }
    }

    @Test
    fun choosingWholeSeriesInvokesOnSave() {
        var savedReminder: Reminder? = null
        var editOccurrenceCalled = false
        val reminder = testReminder(title = "Standup", date = LocalDate.now(), time = LocalTime.of(9, 0))
        setDialog(
            reminderWithSchedules = testReminderWithSchedules(reminder = reminder, cronExpressions = listOf("0 9 * * *")),
            onSave = { edited, _ -> savedReminder = edited },
            onEditOccurrence = { _, _ -> editOccurrenceCalled = true }
        )
        composeTestRule.onNodeWithTag("titleField").performTextClearance()
        composeTestRule.onNodeWithTag("titleField").performTextInput("Renamed")

        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.onNodeWithTag("wholeSeriesButton").performClick()

        assert(savedReminder?.title == "Renamed") { "was ${savedReminder?.title}" }
        assert(!editOccurrenceCalled) { "expected onEditOccurrence not to be invoked" }
        composeTestRule.onNodeWithText("Apply changes to?").assertDoesNotExist()
    }

    @Test
    fun choosingThisOccurrenceOnlyInvokesOnEditOccurrenceWithOriginalAndEditedReminder() {
        var originalPassed: ReminderWithSchedules? = null
        var editedPassed: Reminder? = null
        var saveCalled = false
        val reminder = testReminder(id = 3, title = "Standup", date = LocalDate.now(), time = LocalTime.of(9, 0))
        val original = testReminderWithSchedules(reminder = reminder, cronExpressions = listOf("0 9 * * *"))
        setDialog(
            reminderWithSchedules = original,
            onSave = { _, _ -> saveCalled = true },
            onEditOccurrence = { orig, edited -> originalPassed = orig; editedPassed = edited }
        )
        composeTestRule.onNodeWithTag("titleField").performTextClearance()
        composeTestRule.onNodeWithTag("titleField").performTextInput("Renamed")

        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.onNodeWithTag("thisOccurrenceOnlyButton").performClick()

        assert(originalPassed == original) { "expected the pre-edit reminder, was $originalPassed" }
        assert(editedPassed?.id == 3L) { "was ${editedPassed?.id}" }
        assert(editedPassed?.title == "Renamed") { "was ${editedPassed?.title}" }
        assert(!saveCalled) { "expected onSave not to be invoked" }
        composeTestRule.onNodeWithText("Apply changes to?").assertDoesNotExist()
    }

    @Test
    fun cancelingSeriesChoiceReturnsToEditingWithoutSavingEitherWay() {
        var saveCalled = false
        var editOccurrenceCalled = false
        val reminder = testReminder(title = "Standup", date = LocalDate.now(), time = LocalTime.of(9, 0))
        setDialog(
            reminderWithSchedules = testReminderWithSchedules(reminder = reminder, cronExpressions = listOf("0 9 * * *")),
            onSave = { _, _ -> saveCalled = true },
            onEditOccurrence = { _, _ -> editOccurrenceCalled = true }
        )

        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("Apply changes to?").assertDoesNotExist()
        composeTestRule.onNodeWithText("Edit Reminder").assertIsDisplayed()
        assert(!saveCalled) { "expected onSave not to be invoked" }
        assert(!editOccurrenceCalled) { "expected onEditOccurrence not to be invoked" }
    }

    @Test
    fun editingAlreadyCompleteReminderSkipsSeriesChoiceEvenIfRecurring() {
        var saveCalled = false
        val reminder = testReminder(
            title = "Done",
            date = LocalDate.now(),
            time = LocalTime.of(9, 0),
            status = ReminderStatus.COMPLETE
        )
        setDialog(
            reminderWithSchedules = testReminderWithSchedules(reminder = reminder, cronExpressions = listOf("0 9 * * *")),
            onSave = { _, _ -> saveCalled = true }
        )

        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.onNodeWithText("Apply changes to?").assertDoesNotExist()
        assert(saveCalled) { "expected onSave to be invoked directly" }
    }

    @Test
    fun editingPinnedOneOffWithNoFutureOccurrenceSkipsSeriesChoice() {
        var saveCalled = false
        val ownDate = LocalDate.of(2026, 12, 25)
        val ownTime = LocalTime.of(9, 30)
        val reminder = testReminder(title = "One-off", date = ownDate, time = ownTime)
        setDialog(
            reminderWithSchedules = testReminderWithSchedules(reminder = reminder, cronExpressions = listOf("30 9 25 12 * 2026")),
            onSave = { _, _ -> saveCalled = true }
        )

        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.onNodeWithText("Apply changes to?").assertDoesNotExist()
        assert(saveCalled) { "expected onSave to be invoked directly" }
    }

    @Test
    fun addModeHasNoDeleteButton() {
        setDialog()

        composeTestRule.onNodeWithTag("deleteButton").assertDoesNotExist()
    }

    @Test
    fun editModeShowsDeleteButton() {
        setDialog(reminderWithSchedules = testReminderWithSchedules(testReminder(title = "Existing")))

        composeTestRule.onNodeWithTag("deleteButton").assertIsDisplayed()
    }

    @Test
    fun tappingDeleteShowsConfirmationBeforeInvokingOnDelete() {
        var deleteCalled = false
        setDialog(
            reminderWithSchedules = testReminderWithSchedules(testReminder(title = "Existing")),
            onDelete = { deleteCalled = true }
        )

        composeTestRule.onNodeWithTag("deleteButton").performClick()

        composeTestRule.onNodeWithText("Are you sure?").assertIsDisplayed()
        assert(!deleteCalled) { "expected onDelete not to be invoked before confirming" }
    }

    @Test
    fun confirmingDeleteInvokesOnDelete() {
        var deleteCalled = false
        setDialog(
            reminderWithSchedules = testReminderWithSchedules(testReminder(title = "Existing")),
            onDelete = { deleteCalled = true }
        )

        composeTestRule.onNodeWithTag("deleteButton").performClick()
        composeTestRule.onNodeWithTag("confirmDeleteButton").performClick()

        assert(deleteCalled) { "expected onDelete to be invoked after confirming" }
    }

    @Test
    fun cancelingDeleteConfirmationDoesNotInvokeOnDelete() {
        var deleteCalled = false
        setDialog(
            reminderWithSchedules = testReminderWithSchedules(testReminder(title = "Existing")),
            onDelete = { deleteCalled = true }
        )

        composeTestRule.onNodeWithTag("deleteButton").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("Are you sure?").assertDoesNotExist()
        composeTestRule.onNodeWithText("Edit Reminder").assertIsDisplayed()
        assert(!deleteCalled) { "expected onDelete not to be invoked" }
    }
}
