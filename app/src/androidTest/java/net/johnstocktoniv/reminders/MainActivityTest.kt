package net.johnstocktoniv.reminders

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.johnstocktoniv.reminders.database.DatabaseProvider
import net.johnstocktoniv.reminders.database.RemindersDatabase
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Launches the real Main Activity end-to-end (real Compose wiring, real AlarmScheduler calls,
// permission-request navigation avoided by pre-granting below) — RemindersScreenTest only covers
// the extracted composable in isolation, not Main's own Activity wiring.
//
// Safety: DatabaseProvider.overrideForTesting() swaps in an in-memory database before Main's
// onCreate() runs, so this never touches the real on-disk reminders.db — the same guarantee
// ReminderDaoTest gets from building its own in-memory Room instance directly, extended here to
// cover Main's Activity wiring too. Safe to run against a physical device with real saved data.
//
// Matches AlarmActivityTest's proven shape for launching a real Activity in this suite: explicit
// AndroidJUnit4 runner, and each ActivityScenario launched-and-closed within its own @Test via
// .use{} rather than split across @Before/@After.
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    // createEmptyComposeRule() (rather than createAndroidComposeRule<Main>()) doesn't auto-launch
    // the Activity, so setUp() below can finish its permission grants and DB override *before*
    // Main.onCreate() runs — a rule-driven launch would otherwise happen before @Before.
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var db: RemindersDatabase

    private fun runShellCommand(command: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        // Reading to EOF blocks until the shell command has actually finished, not just started.
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = context.packageName
        // Main.requestAlarmPermissionsIfNeeded() navigates away to system Settings for any of
        // these that are missing, backgrounding the Activity mid-test — pre-granting all three
        // keeps that real permission-request code path from ever firing here.
        runShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
        runShellCommand("appops set $packageName SCHEDULE_EXACT_ALARM allow")
        runShellCommand("appops set $packageName SYSTEM_ALERT_WINDOW allow")

        db = Room.inMemoryDatabaseBuilder<RemindersDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .build()
        DatabaseProvider.overrideForTesting(db)
    }

    @After
    fun tearDown() {
        DatabaseProvider.clearOverrideForTesting()
        db.close()
    }

    @Test
    fun launchesAndShowsRemindersScreen() {
        ActivityScenario.launch(Main::class.java).use {
            composeTestRule.onNodeWithText("Reminders").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Add Item").assertIsDisplayed()
        }
    }

    @Test
    fun addingReminderThroughRealActivityPersistsIt() {
        ActivityScenario.launch(Main::class.java).use {
            composeTestRule.onNodeWithContentDescription("Add Item").performClick()
            composeTestRule.onNodeWithTag("titleField").performTextInput("Real Stack Task")
            composeTestRule.onNodeWithTag("cronField:0").performTextInput("0 0 1 1 *")
            composeTestRule.onNodeWithText("Save").performClick()
            composeTestRule.waitForIdle()

            // This is the thing MainActivityTest exists to prove that RemindersScreenTest's
            // synchronous fakes can't: that Main's real onSaveReminder wiring — the actual Room
            // write via the in-memory db above — persists what the dialog collected. Asserting on
            // the DB directly (rather than waiting for the list to visually update, which needs
            // Main's own reminders Flow to re-collect and recompose) keeps this test scoped to
            // that persistence guarantee instead of re-testing recomposition timing, which is
            // already covered by RemindersScreenTest's synchronous-fake equivalents.
            val persisted = runBlocking { db.reminderDao().readAll().first() }
            assertTrue(
                "expected a persisted reminder titled 'Real Stack Task', got $persisted",
                persisted.any { it.reminder.title == "Real Stack Task" }
            )
        }
    }
}
