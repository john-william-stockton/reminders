package net.johnstocktoniv.reminders.alarm

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
    private val extras by lazy { AlarmScheduler.extrasFrom(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        startAlerting()
        enableEdgeToEdge()

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
private fun AlarmScreen(
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
            Text("Reminder", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.let {
                    it.copy(fontSize = it.fontSize * 1.5f, lineHeight = it.lineHeight * 1.5f)
                },
                textAlign = TextAlign.Center
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessContainer,
                    contentColor = OnSuccessContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mark Complete")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSnooze,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SnoozeContainer,
                    contentColor = OnSnoozeContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Snooze 2 minutes")
            }
        }
    }
}
