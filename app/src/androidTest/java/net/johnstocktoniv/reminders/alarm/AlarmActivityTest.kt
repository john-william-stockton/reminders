package net.johnstocktoniv.reminders.alarm

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Activity-level regression tests exercising the real Activity (not just the extracted
// AlarmScreen composable), since both fixes below live in AlarmActivity itself.
@RunWith(AndroidJUnit4::class)
class AlarmActivityTest {
    // createEmptyComposeRule (rather than createAndroidComposeRule) so the test can launch the
    // Activity itself with a custom start Intent via ActivityScenario, while still getting Compose
    // node-finding/synchronization against whatever's currently on screen.
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private fun intentFor(reminderId: Long, title: String): Intent {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmScheduler.EXTRA_TITLE, title)
            putExtra(AlarmScheduler.EXTRA_DESCRIPTION, "")
        }
    }

    // Regression test for the "no plain dismiss" alarm-screen design: the system back button used
    // to bypass it entirely (see BUGS.md), since AlarmActivity never intercepted it and back would
    // just finish() the activity, silently stopping the alarm without going through
    // complete()/snooze(). Fixed via onBackPressedDispatcher.addCallback.
    @Test
    fun backButtonDoesNotFinishTheActivity() {
        ActivityScenario.launch<AlarmActivity>(intentFor(1L, "First")).use { scenario ->
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            scenario.onActivity {
                assertFalse("expected back press to be swallowed, not finish the activity", it.isFinishing)
            }
        }
    }

    // Regression test for a second alarm arriving while one is already showing (see BUGS.md):
    // AlarmReceiver launches AlarmActivity with SINGLE_TOP|CLEAR_TOP, so the running instance gets
    // the new alarm via onNewIntent() rather than onCreate(). AlarmActivity didn't previously
    // update its held intent/extras there, so the screen (and Mark Complete/Snooze) kept acting on
    // the first reminder. Fixed by having onNewIntent() refresh a Compose-observable extras state.
    @Test
    fun secondAlarmArrivingWhileShowingUpdatesTheScreenToTheNewReminder() {
        ActivityScenario.launch<AlarmActivity>(intentFor(1L, "First")).use { scenario ->
            composeTestRule.onNodeWithText("First").assertIsDisplayed()

            scenario.onActivity { it.onNewIntent(intentFor(2L, "Second")) }

            composeTestRule.onNodeWithText("Second").assertIsDisplayed()
            composeTestRule.onNodeWithText("First").assertDoesNotExist()
        }
    }
}
