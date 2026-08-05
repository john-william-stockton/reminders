package net.johnstocktoniv.reminders.alarm

import android.app.KeyguardManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import net.johnstocktoniv.reminders.ui.theme.RemindersTheme
import java.time.LocalDateTime

class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
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
                    onDismiss = { dismiss() },
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
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
            }
            play()
        }

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
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        if (extras.reminderId != -1L) {
            NotificationManagerCompat.from(this).cancel(extras.reminderId.toInt())
        }
    }

    private fun dismiss() {
        stopAlerting()
        finish()
    }

    private fun complete() = withReminder { current ->
        AlarmScheduler.completeAndAdvance(applicationContext, current)
    }

    private fun snooze() = withReminder { current ->
        val snoozeAt = LocalDateTime.now().plusMinutes(10)
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
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onSnooze: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Reminder", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(48.dp))
            Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                Text("Mark Complete")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
                Text("Snooze 10 minutes")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Dismiss")
            }
        }
    }
}
