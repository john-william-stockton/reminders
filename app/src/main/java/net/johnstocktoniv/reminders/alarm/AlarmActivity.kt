package net.johnstocktoniv.reminders.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.johnstocktoniv.reminders.database.DatabaseProvider
import net.johnstocktoniv.reminders.database.ReminderWithSchedules
import net.johnstocktoniv.reminders.ui.theme.OnSnoozeContainer
import net.johnstocktoniv.reminders.ui.theme.OnSuccessContainer
import net.johnstocktoniv.reminders.ui.theme.RemindersTheme
import net.johnstocktoniv.reminders.ui.theme.SnoozeContainer
import net.johnstocktoniv.reminders.ui.theme.SuccessContainer
import java.time.LocalDateTime

class AlarmActivity : ComponentActivity() {
    private var vibrator: Vibrator? = null // gross lol

    // A Compose-observable var (not `by lazy` over `intent`, and not a plain `val`) so that
    // onNewIntent() below can actually update what's on screen and what Mark Complete/Snooze act
    // on — see onNewIntent() for why that matters.
    private var extras by mutableStateOf(AlarmExtras(reminderId = -1L, title = "", description = ""))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extras = AlarmScheduler.extrasFrom(intent)
        showOverLockScreen()
        startAlerting()
        enableEdgeToEdge()

        // No plain dismiss, by design (see FEATURES.md) — back would otherwise finish the
        // activity and silently stop the alarm via onDestroy()/stopAlerting() without going
        // through complete() or snooze(). Swallow it instead so Mark Complete/Snooze stay the
        // only ways to silence the alarm.
        onBackPressedDispatcher.addCallback(this) { }

        setContent {
            RemindersTheme {
                AlarmScreen(
                    title = extras.title.ifBlank { "Reminder" },
                    description = extras.description,
                    onComplete = { complete() },
                    onSnooze = { snooze() }
                )
            }
        }
    }

    // Public (widened from the framework's protected onNewIntent) so tests can invoke it directly
    // to simulate a second alarm arriving while this activity is already showing.
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // AlarmReceiver launches AlarmActivity with SINGLE_TOP|CLEAR_TOP, so a second alarm
        // arriving while this activity is already showing reuses this instance and delivers the
        // new alarm here instead of onCreate(). Without updating the held intent/extras, the
        // screen (and Mark Complete/Snooze) would keep referring to the first reminder, silently
        // losing whichever one just arrived.
        setIntent(intent)
        extras = AlarmScheduler.extrasFrom(intent)
    }

    override fun onDestroy() {
        stopAlerting()
        super.onDestroy()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
    }

    private fun startAlerting() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val pattern = longArrayOf(0, 500, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopAlerting() {
        vibrator?.cancel()
        vibrator = null
        if (extras.reminderId != -1L) {
            NotificationManagerCompat.from(this).cancel(extras.reminderId.toInt())
        }
    }

    private fun complete() = withReminder { current ->
        AlarmScheduler.completeAndAdvance(applicationContext, current)
    }

    private fun snooze() = withReminder { current ->
        val snoozeAt = LocalDateTime.now().plusMinutes(2)
        val updated = current.reminder.copy(date = snoozeAt.toLocalDate(), time = snoozeAt.toLocalTime())
        DatabaseProvider.dao(applicationContext).upsert(updated)
        AlarmScheduler.schedule(applicationContext, updated)
    }

    private fun withReminder(action: suspend (ReminderWithSchedules) -> Unit) {
        stopAlerting()
        if (extras.reminderId == -1L) {
            finish()
            return
        }
        lifecycleScope.launch {
            DatabaseProvider.dao(applicationContext).getById(extras.reminderId)?.let { action(it) }
            finish()
        }
    }
}

@Composable
internal fun AlarmScreen(
    title: String,
    description: String,
    onComplete: () -> Unit,
    onSnooze: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(),
                modifier = Modifier.fillMaxWidth()
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("alarmDescription")
                )
            }
            Spacer(Modifier.height(48.dp))
            // Double the Material 3 default button height (40.dp) and scale the label to match,
            // since these are the only two actions on the alarm screen and should be easy to hit
            // and read at a glance.
            val actionButtonModifier = Modifier.fillMaxWidth().height(80.dp)
            val actionButtonTextStyle = MaterialTheme.typography.labelLarge.let {
                it.copy(fontSize = it.fontSize * 1.5f, lineHeight = it.lineHeight * 1.5f)
            }
            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessContainer,
                    contentColor = OnSuccessContainer
                ),
                modifier = actionButtonModifier
            ) {
                Text("Mark Complete", style = actionButtonTextStyle)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSnooze,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SnoozeContainer,
                    contentColor = OnSnoozeContainer
                ),
                modifier = actionButtonModifier
            ) {
                Text("Snooze 2 minutes", style = actionButtonTextStyle)
            }
        }
    }
}
