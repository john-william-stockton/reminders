package net.johnstocktoniv.reminders.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import net.johnstocktoniv.reminders.STABLE_FUTURE_CRON
import net.johnstocktoniv.reminders.database.Reminder
import net.johnstocktoniv.reminders.database.ReminderStatus
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
        onSaveReminder: suspend (Reminder, List<String>) -> Unit = { _, _ -> },
        onEditOccurrence: suspend (ReminderWithSchedules, Reminder) -> Unit = { _, _ -> },
        onToggleComplete: (ReminderWithSchedules) -> Unit = {},
        onToggleMissed: (ReminderWithSchedules) -> Unit = {},
        onDeleteReminder: (ReminderWithSchedules) -> Unit = {},
        onClearAll: (List<ReminderWithSchedules>) -> Unit = {},
        onExportBackup: () -> Unit = {},
        onImportBackup: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            RemindersTheme {
                RemindersScreen(
                    reminders = reminders,
                    onSaveReminder = onSaveReminder,
                    onEditOccurrence = onEditOccurrence,
                    onToggleComplete = onToggleComplete,
                    onToggleMissed = onToggleMissed,
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
    fun todayTabIncludesIncompleteOverdueReminders() {
        val overdue = testReminder(id = 1, title = "Overdue Item", date = LocalDate.now().minusDays(3))
        setScreen(reminders = listOf(overdue).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Overdue Item").assertIsDisplayed()
    }

    @Test
    fun todayTabExcludesCompleteOverdueReminders() {
        val overdueComplete = testReminder(
            id = 1,
            title = "Overdue Complete Item",
            date = LocalDate.now().minusDays(3),
            status = ReminderStatus.COMPLETE
        )
        setScreen(reminders = listOf(overdueComplete).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Overdue Complete Item").assertDoesNotExist()
    }

    @Test
    fun todayTabSortsOverdueAboveTodayItems() {
        val today = testReminder(id = 1, title = "Today Item", date = LocalDate.now())
        val overdue = testReminder(id = 2, title = "Overdue Item", date = LocalDate.now().minusDays(1))
        setScreen(reminders = listOf(today, overdue).map { testReminderWithSchedules(it) })

        val overdueTop = composeTestRule.onNodeWithTag("reminderItem:2").fetchSemanticsNode().boundsInRoot.top
        val todayTop = composeTestRule.onNodeWithTag("reminderItem:1").fetchSemanticsNode().boundsInRoot.top

        assert(overdueTop < todayTop) { "expected the overdue item above today's item" }
    }

    @Test
    fun todayTabSortsIncompleteAboveComplete() {
        // Deliberately pass the complete item first so a naive "list order" read would get this
        // wrong if the stable sort broke.
        val completeReminder = testReminder(id = 1, title = "Complete Item", date = LocalDate.now(), status = ReminderStatus.COMPLETE)
        val incompleteReminder = testReminder(id = 2, title = "Incomplete Item", date = LocalDate.now(), status = ReminderStatus.OPEN)
        setScreen(reminders = listOf(completeReminder, incompleteReminder).map { testReminderWithSchedules(it) })

        val incompleteTop = composeTestRule.onNodeWithTag("reminderItem:2").fetchSemanticsNode().boundsInRoot.top
        val completeTop = composeTestRule.onNodeWithTag("reminderItem:1").fetchSemanticsNode().boundsInRoot.top

        assert(incompleteTop < completeTop) { "expected the incomplete item above the complete item" }
    }

    @Test
    fun todayTabCollapsesCompletedRemindersIntoOffsetStackByDefault() {
        val incomplete = testReminder(id = 1, title = "Incomplete", date = LocalDate.now())
        val completeOne = testReminder(id = 2, title = "Complete One", date = LocalDate.now(), status = ReminderStatus.COMPLETE)
        val completeTwo = testReminder(id = 3, title = "Complete Two", date = LocalDate.now(), status = ReminderStatus.COMPLETE)
        setScreen(reminders = listOf(incomplete, completeOne, completeTwo).map { testReminderWithSchedules(it) })

        val firstCompleteNode = composeTestRule.onNodeWithTag("reminderItem:2").fetchSemanticsNode().boundsInRoot
        val secondCompleteTop = composeTestRule.onNodeWithTag("reminderItem:3").fetchSemanticsNode().boundsInRoot.top
        val gap = secondCompleteTop - firstCompleteNode.top

        assert(gap in 1f..(firstCompleteNode.height * 0.5f)) {
            "expected a small peek offset between stacked completed items, was $gap (item height ${firstCompleteNode.height})"
        }
    }

    // Missed is a terminal status just like Complete — nothing left to do on it today — so it
    // shares the same collapsed stack rather than sitting in the prominent open list.
    @Test
    fun todayTabCollapsesMissedRemindersIntoTheSameStackAsComplete() {
        val missedOne = testReminder(id = 2, title = "Missed One", date = LocalDate.now(), status = ReminderStatus.MISSED)
        val completeOne = testReminder(id = 3, title = "Complete One", date = LocalDate.now(), status = ReminderStatus.COMPLETE)
        setScreen(reminders = listOf(missedOne, completeOne).map { testReminderWithSchedules(it) })

        val missedNode = composeTestRule.onNodeWithTag("reminderItem:2").fetchSemanticsNode().boundsInRoot
        val completeTop = composeTestRule.onNodeWithTag("reminderItem:3").fetchSemanticsNode().boundsInRoot.top
        val gap = completeTop - missedNode.top

        assert(gap in 1f..(missedNode.height * 0.5f)) {
            "expected Missed and Complete reminders to share the same collapsed stack, gap was $gap (item height ${missedNode.height})"
        }
    }

    @Test
    fun tappingCompletedReminderExpandsThenCollapsesTheStack() {
        val completeOne = testReminder(id = 2, title = "Complete One", date = LocalDate.now(), status = ReminderStatus.COMPLETE)
        val completeTwo = testReminder(id = 3, title = "Complete Two", date = LocalDate.now(), status = ReminderStatus.COMPLETE)
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
        val completeOne = testReminder(id = 2, title = "Complete One", date = LocalDate.now(), status = ReminderStatus.COMPLETE)
        val completeTwo = testReminder(id = 3, title = "Complete Two", date = LocalDate.now(), status = ReminderStatus.COMPLETE)
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
            status = ReminderStatus.COMPLETE
        )
        val completeTwo = testReminder(id = 3, title = "Complete Two", date = LocalDate.now(), status = ReminderStatus.COMPLETE)
        setScreen(reminders = listOf(completeOne, completeTwo).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Some description text").assertDoesNotExist()

        composeTestRule.onNodeWithTag("reminderItem:2").performTouchInput { click() }

        composeTestRule.onNodeWithText("Some description text").assertIsDisplayed()
    }

    @Test
    fun incompleteTabShowsAllIncompleteRegardlessOfDate() {
        val incompleteToday = testReminder(id = 1, title = "Incomplete Today", date = LocalDate.now())
        val incompleteFuture = testReminder(id = 2, title = "Incomplete Future", date = LocalDate.now().plusDays(5))
        val completeToday = testReminder(id = 3, title = "Complete Today", date = LocalDate.now(), status = ReminderStatus.COMPLETE)
        setScreen(reminders = listOf(incompleteToday, incompleteFuture, completeToday).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Incomplete").performClick()

        composeTestRule.onNodeWithText("Incomplete Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("Incomplete Future").assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete Today").assertDoesNotExist()
    }

    // Missed is a resolved/terminal status (see the Today tab's collapsed stack) — it doesn't
    // belong in "still need to do," even though it isn't Complete either.
    @Test
    fun incompleteTabExcludesMissedReminders() {
        val open = testReminder(id = 1, title = "Open One", date = LocalDate.now())
        val missed = testReminder(id = 2, title = "Missed One", date = LocalDate.now(), status = ReminderStatus.MISSED)
        setScreen(reminders = listOf(open, missed).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Incomplete").performClick()

        composeTestRule.onNodeWithText("Open One").assertIsDisplayed()
        composeTestRule.onNodeWithText("Missed One").assertDoesNotExist()
    }

    @Test
    fun completeTabShowsAllCompleteRegardlessOfDate() {
        val completeToday = testReminder(id = 1, title = "Complete Today", date = LocalDate.now(), status = ReminderStatus.COMPLETE)
        val completeFuture = testReminder(id = 2, title = "Complete Future", date = LocalDate.now().plusDays(5), status = ReminderStatus.COMPLETE)
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
        val completeOne = testReminder(id = 1, title = "Complete One", status = ReminderStatus.COMPLETE)
        val completeTwo = testReminder(id = 2, title = "Complete Two", status = ReminderStatus.COMPLETE)
        setScreen(reminders = listOf(completeOne, completeTwo).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Complete").performClick()
        composeTestRule.onNodeWithContentDescription("Clear All").performClick()

        composeTestRule.onNodeWithText(
            "This will permanently delete 2 completed reminder(s). This can't be undone."
        ).assertIsDisplayed()
    }

    @Test
    fun addItemFabReplacedByClearAllOnCompleteTab() {
        val completeOne = testReminder(id = 1, title = "Complete One", status = ReminderStatus.COMPLETE)
        setScreen(reminders = listOf(completeOne).map { testReminderWithSchedules(it) })

        composeTestRule.onNodeWithText("Complete").performClick()

        composeTestRule.onNodeWithContentDescription("Add Item").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Clear All").assertIsDisplayed()
    }

    @Test
    fun confirmingClearAllInvokesOnClearAllWithVisibleCompletedRemindersAndClosesDialog() {
        var cleared: List<ReminderWithSchedules>? = null
        val completeOne = testReminderWithSchedules(testReminder(id = 1, title = "Complete One", status = ReminderStatus.COMPLETE))
        val completeTwo = testReminderWithSchedules(testReminder(id = 2, title = "Complete Two", status = ReminderStatus.COMPLETE))
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
        val completeOne = testReminderWithSchedules(testReminder(id = 1, title = "Complete One", status = ReminderStatus.COMPLETE))
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
        composeTestRule.onNodeWithTag("cronField:0").performTextInput(STABLE_FUTURE_CRON)
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
                    onSaveReminder = { reminder, cronExpressions ->
                        // Stands in for the real save: DAO write + Flow re-emission.
                        reminders = listOf(testReminderWithSchedules(reminder, cronExpressions = cronExpressions))
                    },
                    onToggleComplete = {},
                    onDeleteReminder = {},
                    onClearAll = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("reminderItem:1").performTouchInput { longClick() }
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
    fun genuinelyRecurringReminderShowsRepeatIcon() {
        val reminder = testReminder(id = 21, title = "Standup", date = LocalDate.now())
        setScreen(reminders = listOf(testReminderWithSchedules(reminder, cronExpressions = listOf("0 9 * * *"))))

        composeTestRule.onNodeWithContentDescription("Recurring").assertIsDisplayed()
    }

    @Test
    fun pinnedOneOffReminderHidesRepeatIcon() {
        val date = LocalDate.of(2026, 12, 25)
        val time = LocalTime.of(9, 30)
        val reminder = testReminder(id = 22, title = "Christmas", date = date, time = time)
        setScreen(
            reminders = listOf(testReminderWithSchedules(reminder, cronExpressions = listOf("30 9 25 12 * 2026")))
        )

        composeTestRule.onNodeWithContentDescription("Recurring").assertDoesNotExist()
    }

    @Test
    fun recurringReminderWithPositiveStreakShowsPluralizedStreakText() {
        val reminder = testReminder(id = 23, title = "Standup", date = LocalDate.now(), streak = 5)
        setScreen(reminders = listOf(testReminderWithSchedules(reminder, cronExpressions = listOf("0 9 * * *"))))

        composeTestRule.onNodeWithText("5 days streak").assertIsDisplayed()
    }

    @Test
    fun recurringReminderWithSingularPositiveStreakUsesSingularDay() {
        val reminder = testReminder(id = 24, title = "Standup", date = LocalDate.now(), streak = 1)
        setScreen(reminders = listOf(testReminderWithSchedules(reminder, cronExpressions = listOf("0 9 * * *"))))

        composeTestRule.onNodeWithText("1 day streak").assertIsDisplayed()
    }

    @Test
    fun recurringReminderWithNegativeStreakShowsDaysMissedInstead() {
        val reminder = testReminder(id = 25, title = "Standup", date = LocalDate.now(), streak = -3)
        setScreen(reminders = listOf(testReminderWithSchedules(reminder, cronExpressions = listOf("0 9 * * *"))))

        composeTestRule.onNodeWithText("3 days missed").assertIsDisplayed()
        composeTestRule.onNodeWithText("-3 day streak").assertDoesNotExist()
    }

    @Test
    fun recurringReminderWithZeroStreakShowsNoStreakText() {
        val reminder = testReminder(id = 26, title = "Standup", date = LocalDate.now(), streak = 0)
        setScreen(reminders = listOf(testReminderWithSchedules(reminder, cronExpressions = listOf("0 9 * * *"))))

        composeTestRule.onNodeWithText("streak", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("missed", substring = true).assertDoesNotExist()
    }

    // The underlying counter still updates for a one-off reminder's own completion/miss (see
    // AlarmScheduler.nextStreak) — it's just never surfaced, since "streak" only means anything
    // for something that actually repeats.
    @Test
    fun nonRecurringReminderNeverShowsStreakEvenIfNonzero() {
        val date = LocalDate.of(2026, 12, 25)
        val time = LocalTime.of(9, 30)
        val reminder = testReminder(id = 27, title = "Christmas", date = date, time = time, streak = 4)
        setScreen(
            reminders = listOf(testReminderWithSchedules(reminder, cronExpressions = listOf("30 9 25 12 * 2026")))
        )

        // A future-dated one-off wouldn't render on the default Today tab at all regardless of
        // this assertion, so switch to Incomplete (shows everything regardless of date) to
        // actually exercise the "recurring icon hidden -> streak hidden too" logic.
        composeTestRule.onNodeWithText("Incomplete").performClick()
        composeTestRule.onNodeWithText("Christmas").assertIsDisplayed()
        composeTestRule.onNodeWithText("streak", substring = true).assertDoesNotExist()
    }

    @Test
    fun choosingThisOccurrenceOnlyInvokesOnEditOccurrenceAndClosesDialog() {
        var originalPassed: ReminderWithSchedules? = null
        var editedPassed: Reminder? = null
        val reminder = testReminder(id = 11, title = "Standup", date = LocalDate.now())
        val original = testReminderWithSchedules(reminder = reminder, cronExpressions = listOf("0 9 * * *"))
        setScreen(
            reminders = listOf(original),
            onEditOccurrence = { orig, edited -> originalPassed = orig; editedPassed = edited }
        )

        composeTestRule.onNodeWithTag("reminderItem:11").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.onNodeWithTag("thisOccurrenceOnlyButton").performClick()

        assert(originalPassed == original) { "expected the pre-edit reminder, was $originalPassed" }
        assert(editedPassed?.id == 11L) { "was ${editedPassed?.id}" }
        composeTestRule.onNodeWithText("Edit Reminder").assertDoesNotExist()
        composeTestRule.onNodeWithText("Apply changes to?").assertDoesNotExist()
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
    fun swipingStartToEndOnOverdueReminderInvokesOnToggleMissedForThatReminder() {
        var toggled: ReminderWithSchedules? = null
        val reminder = testReminderWithSchedules(
            testReminder(id = 9, title = "Swipe Me Too", date = LocalDate.now().minusDays(3))
        )
        setScreen(reminders = listOf(reminder), onToggleMissed = { toggled = it })

        composeTestRule.onNodeWithTag("reminderItem:9").performTouchInput {
            swipeRight(startX = width * 0.05f, endX = width * 0.9f)
        }
        composeTestRule.waitForIdle()

        assert(toggled?.reminder?.id == 9L) { "was $toggled" }
    }

    // Marking something missed only makes sense once it's actually overdue (or to un-miss an
    // already-Missed one) — a not-yet-due OPEN reminder has that swipe direction disabled outright.
    @Test
    fun swipingStartToEndOnNotYetDueReminderDoesNotInvokeOnToggleMissed() {
        var toggleMissedCalled = false
        val reminder = testReminderWithSchedules(
            testReminder(id = 10, title = "Not Due Yet", date = LocalDate.now().plusDays(3))
        )
        setScreen(reminders = listOf(reminder), onToggleMissed = { toggleMissedCalled = true })

        // A reminder 3 days out isn't due "Today" (the default tab); switch to Incomplete, which
        // shows it regardless of date.
        composeTestRule.onNodeWithText("Incomplete").performClick()
        composeTestRule.onNodeWithTag("reminderItem:10").performTouchInput {
            swipeRight(startX = width * 0.05f, endX = width * 0.9f)
        }
        composeTestRule.waitForIdle()

        assert(!toggleMissedCalled) { "expected onToggleMissed not to be invoked for a not-yet-due reminder" }
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
